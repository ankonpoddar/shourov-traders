package com.example.ui.viewmodel

import android.util.Log
import com.example.data.backup.DriveBackupPreferences
import com.example.data.backup.DriveBackupUiState
import com.example.data.backup.DriveBackupWorkStatus
import com.example.data.backup.GoogleDriveBackupClient
import com.example.data.backup.LedgerBackupSerializer
import com.example.data.backup.PendingDriveRestore
import com.example.data.backup.StoredDriveBackupInfo
import com.example.data.model.BankLoan
import com.example.data.model.BusinessAggregator
import com.example.data.model.BusinessExpense
import com.example.data.model.BusinessSummary
import com.example.data.model.Customer
import com.example.data.model.CustomerSummary
import com.example.data.model.LedgerTransaction
import com.example.data.preferences.LanguageMode
import com.example.data.preferences.LanguagePreferences
import com.example.data.repository.LedgerRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

sealed interface BusinessUiState {
    data object Loading : BusinessUiState
    data class Ready(
        val customers: List<Customer>,
        val customerSummaries: List<CustomerSummary>,
        val transactions: List<LedgerTransaction>,
        val expenses: List<BusinessExpense>,
        val loans: List<BankLoan>,
        val summary: BusinessSummary,
        val isFromCache: Boolean = false,
        val hasPendingWrites: Boolean = false,
    ) : BusinessUiState

    data class Error(val message: String) : BusinessUiState
}

class BusinessViewModel(
    private val repository: LedgerRepository,
    private val languagePreferences: LanguagePreferences,
    private val currentUserId: String,
    isOnlineFlow: Flow<Boolean> = flowOf(true),
    private val driveBackupClient: GoogleDriveBackupClient = GoogleDriveBackupClient(),
    private val driveBackupPreferences: DriveBackupPreferences? = null,
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = isOnlineFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = true,
        )

    val languageMode: StateFlow<LanguageMode> = languagePreferences.languageMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = LanguageMode.BILINGUAL,
        )

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _driveWorkStatus = MutableStateFlow(DriveBackupWorkStatus.IDLE)
    private val _pendingDriveRestore = MutableStateFlow<PendingDriveRestore?>(null)
    private val _sessionDriveInfo = MutableStateFlow(StoredDriveBackupInfo())

    private val storedDriveInfoFlow: Flow<StoredDriveBackupInfo> =
        if (driveBackupPreferences != null) {
            combine(
                driveBackupPreferences.observeBackupInfo(currentUserId),
                _sessionDriveInfo,
            ) { persisted, session ->
                val useSessionBackup = session.lastBackupAtMillis != null &&
                    session.lastBackupAtMillis >= (persisted.lastBackupAtMillis ?: 0L)
                val useSessionRestore = session.lastRestoreAtMillis != null &&
                    session.lastRestoreAtMillis >= (persisted.lastRestoreAtMillis ?: 0L)
                StoredDriveBackupInfo(
                    lastBackupAtMillis = if (useSessionBackup) session.lastBackupAtMillis else persisted.lastBackupAtMillis,
                    lastRestoreAtMillis = if (useSessionRestore) session.lastRestoreAtMillis else persisted.lastRestoreAtMillis,
                    lastRemoteFileId = session.lastRemoteFileId ?: persisted.lastRemoteFileId,
                    lastRemoteFileName = session.lastRemoteFileName ?: persisted.lastRemoteFileName,
                )
            }
        } else {
            _sessionDriveInfo
        }

    val driveBackupState: StateFlow<DriveBackupUiState> = combine(
        _driveWorkStatus,
        _pendingDriveRestore,
        storedDriveInfoFlow,
    ) { workStatus, pendingRestore, storedInfo ->
        DriveBackupUiState(
            workStatus = workStatus,
            lastBackupAtMillis = storedInfo.lastBackupAtMillis,
            lastRestoreAtMillis = storedInfo.lastRestoreAtMillis,
            lastRemoteFileId = storedInfo.lastRemoteFileId,
            lastRemoteFileName = storedInfo.lastRemoteFileName,
            pendingRestore = pendingRestore,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DriveBackupUiState(),
    )

    val uiState: StateFlow<BusinessUiState> = combine(
        repository.observeCustomersWithSync(currentUserId),
        repository.observeTransactionsWithSync(currentUserId),
        repository.observeExpensesWithSync(currentUserId),
        repository.observeLoansWithSync(currentUserId),
    ) { customersSync, transactionsSync, expensesSync, loansSync ->
        val customers = customersSync.items
        val transactions = transactionsSync.items
        val expenses = expensesSync.items
        val loans = loansSync.items

        val aggregated = BusinessAggregator.aggregate(
            customers = customers,
            transactions = transactions,
            expenses = expenses,
            loans = loans,
        )
        BusinessUiState.Ready(
            customers = customers,
            customerSummaries = aggregated.customerSummaries,
            transactions = transactions,
            expenses = expenses,
            loans = loans,
            summary = aggregated.businessSummary,
            isFromCache = customersSync.isFromCache ||
                transactionsSync.isFromCache ||
                expensesSync.isFromCache ||
                loansSync.isFromCache,
            hasPendingWrites = customersSync.hasPendingWrites ||
                transactionsSync.hasPendingWrites ||
                expensesSync.hasPendingWrites ||
                loansSync.hasPendingWrites,
        ) as BusinessUiState
    }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing business data", rootError)
            emit(BusinessUiState.Error(rootError.message ?: "Failed to sync ledger data."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = BusinessUiState.Loading,
        )

    fun setLanguageMode(mode: LanguageMode) {
        viewModelScope.launch {
            languagePreferences.setLanguageMode(mode)
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun addCustomer(
        name: String,
        phone: String,
        shopAddress: String,
        openingBalance: Double,
        onSuccess: () -> Unit = {},
    ) {
        runMutation("Customer added to Baki Khata") {
            repository.addCustomer(currentUserId, name, phone, shopAddress, openingBalance)
                .also { if (it.isSuccess) onSuccess() }
        }
    }

    fun recordSale(
        customerId: String,
        customerName: String,
        riceVariety: String,
        unit: String,
        quantity: Double,
        costPricePerUnit: Double,
        sellingPricePerUnit: Double,
        paidAmount: Double,
        notes: String,
        dateMillis: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit = {},
    ) {
        val total = quantity * sellingPricePerUnit
        val clampedPaid = paidAmount.coerceIn(0.0, total)
        val dueAdded = (total - clampedPaid).coerceAtLeast(0.0)
        val grossProfit = (sellingPricePerUnit - costPricePerUnit) * quantity

        val tx = LedgerTransaction(
            userId = currentUserId,
            customerId = customerId,
            customerName = customerName,
            type = LedgerTransaction.TYPE_SALE,
            riceVariety = riceVariety,
            unit = unit,
            quantity = quantity,
            costPricePerUnit = costPricePerUnit,
            sellingPricePerUnit = sellingPricePerUnit,
            totalAmount = total,
            paidAmount = clampedPaid,
            dueAdded = dueAdded,
            grossProfit = grossProfit,
            transactionDateMillis = dateMillis,
            notes = notes,
        )
        runMutation("Sale saved & ledger updated") {
            repository.recordTransaction(currentUserId, tx)
                .also { if (it.isSuccess) onSuccess() }
        }
    }

    fun recordPayment(
        customerId: String,
        customerName: String,
        amount: Double,
        notes: String,
        dateMillis: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit = {},
    ) {
        val tx = LedgerTransaction(
            userId = currentUserId,
            customerId = customerId,
            customerName = customerName,
            type = LedgerTransaction.TYPE_PAYMENT,
            riceVariety = "",
            unit = LedgerTransaction.UNIT_BAG,
            quantity = 0.0,
            costPricePerUnit = 0.0,
            sellingPricePerUnit = 0.0,
            totalAmount = 0.0,
            paidAmount = amount,
            dueAdded = -amount,
            grossProfit = 0.0,
            transactionDateMillis = dateMillis,
            notes = notes.ifBlank { "Payment received" },
        )
        runMutation("Payment recorded in Khata") {
            repository.recordTransaction(currentUserId, tx)
                .also { if (it.isSuccess) onSuccess() }
        }
    }

    fun addExpense(
        frequency: String,
        category: String,
        amount: Double,
        notes: String,
        dateMillis: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit = {},
    ) {
        runMutation("Expense recorded") {
            repository.addExpense(currentUserId, frequency, category, amount, notes, dateMillis)
                .also { if (it.isSuccess) onSuccess() }
        }
    }

    fun deleteExpense(expenseId: String) {
        runMutation("Expense removed") {
            repository.deleteExpense(currentUserId, expenseId)
        }
    }

    fun addLoan(
        lenderName: String,
        principalAmount: Double,
        annualInterestRate: Double,
        notes: String,
        startDateMillis: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit = {},
    ) {
        runMutation("Bank loan added") {
            repository.addLoan(
                userId = currentUserId,
                lenderName = lenderName,
                principalAmount = principalAmount,
                annualInterestRate = annualInterestRate,
                startDateMillis = startDateMillis,
                notes = notes,
            ).also { if (it.isSuccess) onSuccess() }
        }
    }

    fun recordLoanInstallment(loan: BankLoan, principalPaid: Double, onSuccess: () -> Unit = {}) {
        runMutation("Installment recorded") {
            repository.recordLoanInstallment(currentUserId, loan, principalPaid)
                .also { if (it.isSuccess) onSuccess() }
        }
    }

    fun deleteLoan(loanId: String) {
        runMutation("Loan removed") {
            repository.deleteLoan(currentUserId, loanId)
        }
    }

    fun loadSampleData() {
        runMutation("Sample Bangladeshi rice business data loaded") {
            repository.seedSampleData(currentUserId)
        }
    }

    fun clearAllData() {
        runMutation("All business records cleared") {
            repository.clearAllUserData(currentUserId)
        }
    }

    fun setDriveAuthorizing(active: Boolean) {
        _driveWorkStatus.value = if (active) DriveBackupWorkStatus.AUTHORIZING else DriveBackupWorkStatus.IDLE
    }

    fun reportDriveError(message: String) {
        _driveWorkStatus.value = DriveBackupWorkStatus.IDLE
        _feedbackMessage.value = message
    }

    fun dismissPendingRestore() {
        _pendingDriveRestore.value = null
    }

    fun backupToGoogleDrive(accessToken: String, userEmail: String) {
        if (_driveWorkStatus.value == DriveBackupWorkStatus.BACKING_UP) return
        viewModelScope.launch {
            _driveWorkStatus.value = DriveBackupWorkStatus.BACKING_UP
            try {
                val payload = repository.fetchAllUserDataForBackup(currentUserId, userEmail)
                    .getOrElse { err ->
                        _feedbackMessage.value = err.localizedMessage ?: "Failed to export ledger data."
                        return@launch
                    }
                val jsonContent = LedgerBackupSerializer.toJson(payload)
                val cachedFileId = _sessionDriveInfo.value.lastRemoteFileId
                    ?: storedDriveInfoFlow.firstOrNull()?.lastRemoteFileId
                val uploadedMeta = driveBackupClient.uploadBackupJson(
                    accessToken = accessToken,
                    jsonContent = jsonContent,
                    existingFileId = cachedFileId,
                ).getOrElse { err ->
                    _feedbackMessage.value = err.localizedMessage ?: "Google Drive backup failed."
                    return@launch
                }

                val nowMillis = System.currentTimeMillis()
                // Always update in-memory cache immediately after every upload (fresh POST or PATCH)
                _sessionDriveInfo.value = _sessionDriveInfo.value.copy(
                    lastBackupAtMillis = nowMillis,
                    lastRemoteFileId = uploadedMeta.fileId,
                    lastRemoteFileName = uploadedMeta.fileName,
                )
                driveBackupPreferences?.recordBackupSuccess(
                    userId = currentUserId,
                    timestampMillis = nowMillis,
                    fileId = uploadedMeta.fileId,
                    fileName = uploadedMeta.fileName,
                )

                _feedbackMessage.value =
                    "Drive backup saved (${payload.customers.size} customers, ${payload.transactions.size} entries)"
            } finally {
                _driveWorkStatus.value = DriveBackupWorkStatus.IDLE
            }
        }
    }

    fun prepareRestoreFromGoogleDrive(accessToken: String) {
        if (_driveWorkStatus.value == DriveBackupWorkStatus.FETCHING_BACKUP ||
            _driveWorkStatus.value == DriveBackupWorkStatus.RESTORING
        ) return
        viewModelScope.launch {
            _driveWorkStatus.value = DriveBackupWorkStatus.FETCHING_BACKUP
            try {
                val fileMeta = driveBackupClient.findLatestBackupFile(accessToken)
                    .getOrElse { err ->
                        _feedbackMessage.value = err.localizedMessage ?: "Failed to check Google Drive backup."
                        return@launch
                    }
                if (fileMeta == null) {
                    _feedbackMessage.value = "No Shourav_Traders_Backup.json found in Google Drive."
                    return@launch
                }
                _sessionDriveInfo.value = _sessionDriveInfo.value.copy(
                    lastRemoteFileId = fileMeta.fileId,
                    lastRemoteFileName = fileMeta.fileName,
                )
                val rawJson = driveBackupClient.downloadBackupJson(accessToken, fileMeta.fileId)
                    .getOrElse { err ->
                        _feedbackMessage.value = err.localizedMessage ?: "Failed to download Drive backup."
                        return@launch
                    }
                val parsed = LedgerBackupSerializer.fromJson(rawJson)
                    .getOrElse { err ->
                        _feedbackMessage.value = err.localizedMessage ?: "Invalid backup JSON file."
                        return@launch
                    }
                _pendingDriveRestore.value = PendingDriveRestore(
                    fileMetadata = fileMeta,
                    payload = parsed,
                )
            } finally {
                _driveWorkStatus.value = DriveBackupWorkStatus.IDLE
            }
        }
    }

    fun confirmRestoreFromDrive(replaceExisting: Boolean) {
        val pending = _pendingDriveRestore.value ?: return
        viewModelScope.launch {
            _driveWorkStatus.value = DriveBackupWorkStatus.RESTORING
            try {
                val summary = repository.restoreFromBackup(
                    userId = currentUserId,
                    payload = pending.payload,
                    replaceExisting = replaceExisting,
                ).getOrElse { err ->
                    _feedbackMessage.value = err.localizedMessage ?: "Failed to restore backup into ledger."
                    return@launch
                }
                _pendingDriveRestore.value = null
                val nowMillis = System.currentTimeMillis()
                _sessionDriveInfo.value = _sessionDriveInfo.value.copy(
                    lastRestoreAtMillis = nowMillis,
                    lastRemoteFileId = pending.fileMetadata.fileId,
                    lastRemoteFileName = pending.fileMetadata.fileName,
                )
                driveBackupPreferences?.recordRestoreSuccess(
                    userId = currentUserId,
                    timestampMillis = nowMillis,
                    fileId = pending.fileMetadata.fileId,
                    fileName = pending.fileMetadata.fileName,
                )
                _feedbackMessage.value =
                    "Restored ${summary.customersRestored} customers, ${summary.transactionsRestored} entries, ${summary.expensesRestored} expenses"
            } finally {
                _driveWorkStatus.value = DriveBackupWorkStatus.IDLE
            }
        }
    }

    private fun runMutation(successMessage: String, block: suspend () -> Result<*>) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val result = block()
                result.fold(
                    onSuccess = { _feedbackMessage.value = successMessage },
                    onFailure = { err -> _feedbackMessage.value = err.localizedMessage ?: "Operation failed" },
                )
            } finally {
                _isBusy.value = false
            }
        }
    }

    private companion object {
        const val TAG = "BusinessViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
