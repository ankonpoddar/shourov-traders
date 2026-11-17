package com.example.data.repository

import android.content.Context
import com.example.data.backup.DriveBackupContract
import com.example.data.backup.RestoreSummary
import com.example.data.backup.ShouravBackupPayload
import com.example.data.firebase.FirestoreProvider
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.data.model.BankLoan
import com.example.data.model.BusinessExpense
import com.example.data.model.Customer
import com.example.data.model.LedgerTransaction
import com.example.data.model.ScannedLedgerRow
import com.example.data.network.NetworkMonitor
import com.example.data.preferences.parseLocalizedDouble
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar

data class SyncedList<T>(
    val items: List<T>,
    val isFromCache: Boolean,
    val hasPendingWrites: Boolean,
)

class LedgerRepository(
    private val db: FirebaseFirestore,
    private val isNetworkAvailable: () -> Boolean = { true },
) {
    constructor(context: Context) : this(
        db = FirestoreProvider.getInstance(context),
        isNetworkAvailable = { NetworkMonitor.isCurrentlyOnline(context.applicationContext) },
    )

    private fun userRoot(userId: String) = db.collection("users").document(userId)

    private fun customersCollection(userId: String) = userRoot(userId).collection("customers")
    private fun transactionsCollection(userId: String) = userRoot(userId).collection("transactions")
    private fun expensesCollection(userId: String) = userRoot(userId).collection("expenses")
    private fun loansCollection(userId: String) = userRoot(userId).collection("loans")

    private fun <T> Flow<SyncedList<T>>.withFirestoreLogging(
        userId: String,
        path: String,
    ): Flow<SyncedList<T>> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        val signedInUid = try {
            Firebase.auth.currentUser?.uid
        } catch (_: Exception) {
            null
        }
        if (exceptionToLog != null && (signedInUid == null || signedInUid == userId)) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
        throw error
    }

    fun observeCustomersWithSync(userId: String): Flow<SyncedList<Customer>> {
        val path = "users/$userId/customers"
        return customersCollection(userId)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                SyncedList(
                    items = snapshot.toObjects(Customer::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.name.lowercase() },
                    isFromCache = snapshot.metadata.isFromCache,
                    hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                )
            }
            .withFirestoreLogging(userId, path)
    }

    fun observeCustomers(userId: String): Flow<List<Customer>> =
        observeCustomersWithSync(userId).map { it.items }

    fun observeTransactionsWithSync(userId: String): Flow<SyncedList<LedgerTransaction>> {
        val path = "users/$userId/transactions"
        return transactionsCollection(userId)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                SyncedList(
                    items = snapshot.toObjects(LedgerTransaction::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.transactionDateMillis },
                    isFromCache = snapshot.metadata.isFromCache,
                    hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                )
            }
            .withFirestoreLogging(userId, path)
    }

    fun observeTransactions(userId: String): Flow<List<LedgerTransaction>> =
        observeTransactionsWithSync(userId).map { it.items }

    fun observeExpensesWithSync(userId: String): Flow<SyncedList<BusinessExpense>> {
        val path = "users/$userId/expenses"
        return expensesCollection(userId)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                SyncedList(
                    items = snapshot.toObjects(BusinessExpense::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.expenseDateMillis },
                    isFromCache = snapshot.metadata.isFromCache,
                    hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                )
            }
            .withFirestoreLogging(userId, path)
    }

    fun observeExpenses(userId: String): Flow<List<BusinessExpense>> =
        observeExpensesWithSync(userId).map { it.items }

    fun observeLoansWithSync(userId: String): Flow<SyncedList<BankLoan>> {
        val path = "users/$userId/loans"
        return loansCollection(userId)
            .snapshots(MetadataChanges.INCLUDE)
            .map { snapshot ->
                SyncedList(
                    items = snapshot.toObjects(BankLoan::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.startDateMillis },
                    isFromCache = snapshot.metadata.isFromCache,
                    hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                )
            }
            .withFirestoreLogging(userId, path)
    }

    fun observeLoans(userId: String): Flow<List<BankLoan>> =
        observeLoansWithSync(userId).map { it.items }

    /**
     * Executes a Firestore mutation with offline-first persistence safety:
     * - Mutations always persist immediately to Firestore's local disk cache and update listeners.
     * - When offline, returns immediately without hanging on server ACK.
     * - When online, awaits server ACK up to [SERVER_ACK_TIMEOUT_MS] so rule violations surface immediately,
     *   while silent packet drops fall back gracefully to the persistent local sync queue.
     */
    private suspend fun <R> executeOfflineSafeWrite(
        task: Task<*>,
        op: OperationType,
        path: String,
        resultValue: R,
    ): Result<R> {
        task.addOnFailureListener { error ->
            handleFirestoreError(error, op, path)
        }
        if (!isNetworkAvailable()) {
            return Result.success(resultValue)
        }
        return try {
            withTimeoutOrNull(SERVER_ACK_TIMEOUT_MS) {
                task.await()
            }
            Result.success(resultValue)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, op, path)
            Result.failure(e)
        }
    }

    suspend fun addCustomer(
        userId: String,
        name: String,
        phone: String,
        shopAddress: String,
        openingBalance: Double,
    ): Result<String> {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Customer name is required." }
        val docRef = customersCollection(userId).document()
        val payload = mapOf(
            "userId" to userId,
            "name" to trimmedName.take(100),
            "phone" to phone.trim().take(30),
            "shopAddress" to shopAddress.trim().take(200),
            "openingBalance" to openingBalance.coerceAtLeast(0.0),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return executeOfflineSafeWrite(
            task = docRef.set(payload),
            op = OperationType.CREATE,
            path = docRef.path,
            resultValue = docRef.id,
        )
    }

    suspend fun deleteCustomer(userId: String, customerId: String): Result<Unit> {
        val docRef = customersCollection(userId).document(customerId)
        return executeOfflineSafeWrite(
            task = docRef.delete(),
            op = OperationType.DELETE,
            path = docRef.path,
            resultValue = Unit,
        )
    }

    suspend fun recordTransaction(
        userId: String,
        transaction: LedgerTransaction,
    ): Result<String> {
        val docRef = transactionsCollection(userId).document()
        val payload = mapOf(
            "userId" to userId,
            "customerId" to transaction.customerId.take(128),
            "customerName" to transaction.customerName.trim().ifEmpty { "Walk-in Cash Customer" }.take(100),
            "type" to transaction.type,
            "riceVariety" to transaction.riceVariety.trim().take(80),
            "unit" to transaction.unit,
            "quantity" to transaction.quantity.coerceAtLeast(0.0),
            "costPricePerUnit" to transaction.costPricePerUnit.coerceAtLeast(0.0),
            "sellingPricePerUnit" to transaction.sellingPricePerUnit.coerceAtLeast(0.0),
            "totalAmount" to transaction.totalAmount.coerceAtLeast(0.0),
            "paidAmount" to transaction.paidAmount.coerceAtLeast(0.0),
            "dueAdded" to transaction.dueAdded,
            "grossProfit" to transaction.grossProfit,
            "transactionDateMillis" to transaction.transactionDateMillis.coerceAtLeast(0L),
            "notes" to transaction.notes.trim().take(300),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return executeOfflineSafeWrite(
            task = docRef.set(payload),
            op = OperationType.CREATE,
            path = docRef.path,
            resultValue = docRef.id,
        )
    }

    suspend fun addExpense(
        userId: String,
        frequency: String,
        category: String,
        amount: Double,
        notes: String,
        expenseDateMillis: Long,
    ): Result<String> {
        require(amount > 0.0) { "Expense amount must be greater than zero." }
        val docRef = expensesCollection(userId).document()
        val payload = mapOf(
            "userId" to userId,
            "frequency" to frequency,
            "category" to category.trim().ifEmpty { "Misc" }.take(60),
            "amount" to amount,
            "notes" to notes.trim().take(300),
            "expenseDateMillis" to expenseDateMillis.coerceAtLeast(0L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return executeOfflineSafeWrite(
            task = docRef.set(payload),
            op = OperationType.CREATE,
            path = docRef.path,
            resultValue = docRef.id,
        )
    }

    suspend fun deleteExpense(userId: String, expenseId: String): Result<Unit> {
        val docRef = expensesCollection(userId).document(expenseId)
        return executeOfflineSafeWrite(
            task = docRef.delete(),
            op = OperationType.DELETE,
            path = docRef.path,
            resultValue = Unit,
        )
    }

    suspend fun addLoan(
        userId: String,
        lenderName: String,
        principalAmount: Double,
        annualInterestRate: Double,
        startDateMillis: Long,
        notes: String,
    ): Result<String> {
        require(principalAmount > 0.0) { "Principal loan amount must be positive." }
        val docRef = loansCollection(userId).document()
        val payload = mapOf(
            "userId" to userId,
            "lenderName" to lenderName.trim().ifEmpty { "Bank Loan" }.take(100),
            "principalAmount" to principalAmount,
            "annualInterestRate" to annualInterestRate.coerceIn(0.0, 100.0),
            "startDateMillis" to startDateMillis.coerceAtLeast(0L),
            "paidInstallmentsCount" to 0L,
            "totalPrincipalRepaid" to 0.0,
            "notes" to notes.trim().take(300),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return executeOfflineSafeWrite(
            task = docRef.set(payload),
            op = OperationType.CREATE,
            path = docRef.path,
            resultValue = docRef.id,
        )
    }

    suspend fun recordLoanInstallment(
        userId: String,
        loan: BankLoan,
        principalPaid: Double,
    ): Result<Unit> {
        require(loan.id.isNotBlank()) { "Loan ID is required." }
        require(principalPaid >= 0.0) { "Repayment amount cannot be negative." }
        val docRef = loansCollection(userId).document(loan.id)
        val updatedRepaid = (loan.totalPrincipalRepaid + principalPaid).coerceAtMost(loan.principalAmount)
        val updates = mapOf(
            "paidInstallmentsCount" to (loan.paidInstallmentsCount + 1L),
            "totalPrincipalRepaid" to updatedRepaid,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return executeOfflineSafeWrite(
            task = docRef.update(updates),
            op = OperationType.UPDATE,
            path = docRef.path,
            resultValue = Unit,
        )
    }

    suspend fun deleteLoan(userId: String, loanId: String): Result<Unit> {
        val docRef = loansCollection(userId).document(loanId)
        return executeOfflineSafeWrite(
            task = docRef.delete(),
            op = OperationType.DELETE,
            path = docRef.path,
            resultValue = Unit,
        )
    }

    suspend fun importScannedRows(
        userId: String,
        existingCustomers: List<Customer>,
        rows: List<ScannedLedgerRow>,
    ): Result<Int> {
        if (rows.isEmpty()) return Result.success(0)
        val batch = db.batch()
        val customerByNormalizedName = existingCustomers.associateBy { it.name.trim().lowercase() }.toMutableMap()
        val nowMillis = System.currentTimeMillis()
        var importedCount = 0

        for (row in rows) {
            val cleanName = row.customerName.trim().take(100)
            if (cleanName.isEmpty()) continue
            val total = parseLocalizedDouble(row.totalAmount)?.coerceAtLeast(0.0) ?: 0.0
            val paid = parseLocalizedDouble(row.paidAmount)?.coerceAtLeast(0.0) ?: 0.0
            val due = parseLocalizedDouble(row.dueAmount) ?: (total - paid)
            if (total == 0.0 && paid == 0.0 && due == 0.0) continue

            val key = cleanName.lowercase()
            val customerId = customerByNormalizedName[key]?.id ?: run {
                val newCustomerRef = customersCollection(userId).document()
                batch.set(
                    newCustomerRef,
                    mapOf(
                        "userId" to userId,
                        "name" to cleanName,
                        "phone" to "",
                        "shopAddress" to "Imported from Old Khata",
                        "openingBalance" to 0.0,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                customerByNormalizedName[key] = Customer(id = newCustomerRef.id, userId = userId, name = cleanName)
                newCustomerRef.id
            }

            val txRef = transactionsCollection(userId).document()
            val noteText = listOfNotNull(
                row.dateText.takeIf { it.isNotBlank() }?.let { "Khata Date: $it" },
                row.notes.takeIf { it.isNotBlank() },
            ).joinToString(" · ").ifEmpty { "Imported from Old Khata" }.take(300)

            val estimatedGrossProfit = if (total > 0.0) total * 0.08 else 0.0
            batch.set(
                txRef,
                mapOf(
                    "userId" to userId,
                    "customerId" to customerId,
                    "customerName" to cleanName,
                    "type" to if (total > 0.0) LedgerTransaction.TYPE_SALE else LedgerTransaction.TYPE_PAYMENT,
                    "riceVariety" to if (total > 0.0) "Khata Entry" else "",
                    "unit" to LedgerTransaction.UNIT_BAG,
                    "quantity" to if (total > 0.0) 1.0 else 0.0,
                    "costPricePerUnit" to if (total > 0.0) (total - estimatedGrossProfit) else 0.0,
                    "sellingPricePerUnit" to total,
                    "totalAmount" to total,
                    "paidAmount" to paid,
                    "dueAdded" to due,
                    "grossProfit" to estimatedGrossProfit,
                    "transactionDateMillis" to nowMillis,
                    "notes" to noteText,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            importedCount++
        }

        if (importedCount == 0) return Result.success(0)
        return executeOfflineSafeWrite(
            task = batch.commit(),
            op = OperationType.WRITE,
            path = "users/$userId/transactions",
            resultValue = importedCount,
        )
    }

    suspend fun seedSampleData(userId: String): Result<Unit> {
        val batch = db.batch()
        val cal = Calendar.getInstance()
        fun daysAgo(days: Int): Long {
            val c = cal.clone() as Calendar
            c.add(Calendar.DAY_OF_YEAR, -days)
            return c.timeInMillis
        }

        val customerSpecs = listOf(
            Triple("Rahim Store (রহিম স্টোর)", "01711-348921", "Badamtoli Bazar, Dhaka" to 12000.0),
            Triple("Bismillah Rice Agency", "01819-554102", "Babubazar Arat, Dhaka" to 0.0),
            Triple("Haji Enterprise (হাজী এন্টারপ্রাইজ)", "01912-887430", "Chawk Bazar, Chattogram" to 25000.0),
            Triple("Padma Traders (পদ্মা ট্রেডার্স)", "01678-112290", "Jatrabari Wholesale" to 0.0),
            Triple("Karima Grocery (করিমা গ্রোসারি)", "01755-662018", "Mirpur 10 Market" to 5000.0),
        )

        val customerRefs = customerSpecs.map { spec ->
            val ref = customersCollection(userId).document()
            batch.set(
                ref,
                mapOf(
                    "userId" to userId,
                    "name" to spec.first,
                    "phone" to spec.second,
                    "shopAddress" to spec.third.first,
                    "openingBalance" to spec.third.second,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            ref.id to spec.first
        }

        val sampleTransactions = listOf(
            mapOf(
                "customerId" to customerRefs[0].first,
                "customerName" to customerRefs[0].second,
                "type" to LedgerTransaction.TYPE_SALE,
                "riceVariety" to "Miniket",
                "unit" to LedgerTransaction.UNIT_BAG,
                "quantity" to 20.0,
                "costPricePerUnit" to 3350.0,
                "sellingPricePerUnit" to 3600.0,
                "totalAmount" to 72000.0,
                "paidAmount" to 40000.0,
                "dueAdded" to 32000.0,
                "grossProfit" to 5000.0,
                "transactionDateMillis" to daysAgo(5),
                "notes" to "50kg premium Dinajpur Miniket",
            ),
            mapOf(
                "customerId" to customerRefs[1].first,
                "customerName" to customerRefs[1].second,
                "type" to LedgerTransaction.TYPE_SALE,
                "riceVariety" to "Nazirshail",
                "unit" to LedgerTransaction.UNIT_BAG,
                "quantity" to 15.0,
                "costPricePerUnit" to 3600.0,
                "sellingPricePerUnit" to 3900.0,
                "totalAmount" to 58500.0,
                "paidAmount" to 58500.0,
                "dueAdded" to 0.0,
                "grossProfit" to 4500.0,
                "transactionDateMillis" to daysAgo(4),
                "notes" to "Full cash paid via bKash/bank",
            ),
            mapOf(
                "customerId" to customerRefs[2].first,
                "customerName" to customerRefs[2].second,
                "type" to LedgerTransaction.TYPE_SALE,
                "riceVariety" to "BR-28",
                "unit" to LedgerTransaction.UNIT_BAG,
                "quantity" to 30.0,
                "costPricePerUnit" to 2750.0,
                "sellingPricePerUnit" to 2980.0,
                "totalAmount" to 89400.0,
                "paidAmount" to 50000.0,
                "dueAdded" to 39400.0,
                "grossProfit" to 6900.0,
                "transactionDateMillis" to daysAgo(3),
                "notes" to "Wholesale lot - 30 bags",
            ),
            mapOf(
                "customerId" to customerRefs[0].first,
                "customerName" to customerRefs[0].second,
                "type" to LedgerTransaction.TYPE_PAYMENT,
                "riceVariety" to "",
                "unit" to LedgerTransaction.UNIT_BAG,
                "quantity" to 0.0,
                "costPricePerUnit" to 0.0,
                "sellingPricePerUnit" to 0.0,
                "totalAmount" to 0.0,
                "paidAmount" to 20000.0,
                "dueAdded" to -20000.0,
                "grossProfit" to 0.0,
                "transactionDateMillis" to daysAgo(2),
                "notes" to "Cash collection at Badamtoli",
            ),
            mapOf(
                "customerId" to customerRefs[3].first,
                "customerName" to customerRefs[3].second,
                "type" to LedgerTransaction.TYPE_SALE,
                "riceVariety" to "Kataribhog",
                "unit" to LedgerTransaction.UNIT_BAG,
                "quantity" to 12.0,
                "costPricePerUnit" to 3900.0,
                "sellingPricePerUnit" to 4250.0,
                "totalAmount" to 51000.0,
                "paidAmount" to 35000.0,
                "dueAdded" to 16000.0,
                "grossProfit" to 4200.0,
                "transactionDateMillis" to daysAgo(1),
                "notes" to "Aromatic Kataribhog",
            ),
            mapOf(
                "customerId" to "",
                "customerName" to "Walk-in Cash Customer",
                "type" to LedgerTransaction.TYPE_SALE,
                "riceVariety" to "Chinigura",
                "unit" to LedgerTransaction.UNIT_KG,
                "quantity" to 80.0,
                "costPricePerUnit" to 118.0,
                "sellingPricePerUnit" to 135.0,
                "totalAmount" to 10800.0,
                "paidAmount" to 10800.0,
                "dueAdded" to 0.0,
                "grossProfit" to 1360.0,
                "transactionDateMillis" to daysAgo(0),
                "notes" to "Counter retail cash sale",
            ),
        )

        for (tx in sampleTransactions) {
            val ref = transactionsCollection(userId).document()
            batch.set(
                ref,
                tx + mapOf(
                    "userId" to userId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
        }

        val sampleExpenses = listOf(
            mapOf(
                "frequency" to BusinessExpense.FREQ_DAILY,
                "category" to "Transport / Van",
                "amount" to 1200.0,
                "notes" to "Pickup van delivery to Badamtoli",
                "expenseDateMillis" to daysAgo(3),
            ),
            mapOf(
                "frequency" to BusinessExpense.FREQ_DAILY,
                "category" to "Labor / Loading",
                "amount" to 850.0,
                "notes" to "Godown sack unloading labor",
                "expenseDateMillis" to daysAgo(1),
            ),
            mapOf(
                "frequency" to BusinessExpense.FREQ_MONTHLY,
                "category" to "Staff Salary",
                "amount" to 12000.0,
                "notes" to "Monthly counter & godown assistant salary",
                "expenseDateMillis" to daysAgo(5),
            ),
            mapOf(
                "frequency" to BusinessExpense.FREQ_MONTHLY,
                "category" to "Electricity Bill",
                "amount" to 2400.0,
                "notes" to "Shop & godown DESCO electricity bill",
                "expenseDateMillis" to daysAgo(2),
            ),
        )

        for (exp in sampleExpenses) {
            val ref = expensesCollection(userId).document()
            batch.set(
                ref,
                exp + mapOf(
                    "userId" to userId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
        }

        val loanRef = loansCollection(userId).document()
        batch.set(
            loanRef,
            mapOf(
                "userId" to userId,
                "lenderName" to "Islami Bank SME Working Capital",
                "principalAmount" to 500000.0,
                "annualInterestRate" to 13.0,
                "startDateMillis" to daysAgo(60),
                "paidInstallmentsCount" to 2L,
                "totalPrincipalRepaid" to 50000.0,
                "notes" to "13% p.a. rice stock CC limit",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            )
        )

        return executeOfflineSafeWrite(
            task = batch.commit(),
            op = OperationType.WRITE,
            path = "users/$userId",
            resultValue = Unit,
        )
    }

    suspend fun clearAllUserData(userId: String): Result<Unit> {
        return try {
            val source = if (isNetworkAvailable()) Source.DEFAULT else Source.CACHE
            val collections = listOf(
                customersCollection(userId),
                transactionsCollection(userId),
                expensesCollection(userId),
                loansCollection(userId),
            )
            val allRefs = mutableListOf<com.google.firebase.firestore.DocumentReference>()
            for (col in collections) {
                val snap = withTimeoutOrNull(SERVER_ACK_TIMEOUT_MS) {
                    col.get(source).await()
                } ?: col.get(Source.CACHE).await()
                allRefs.addAll(snap.documents.map { it.reference })
            }
            for (chunk in allRefs.chunked(400)) {
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                val deleteResult = executeOfflineSafeWrite(
                    task = batch.commit(),
                    op = OperationType.DELETE,
                    path = "users/$userId",
                    resultValue = Unit,
                )
                if (deleteResult.isFailure) {
                    return deleteResult
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$userId")
            Result.failure(e)
        }
    }

    suspend fun fetchAllUserDataForBackup(
        userId: String,
        userEmail: String = "",
    ): Result<ShouravBackupPayload> {
        return try {
            val source = if (isNetworkAvailable()) Source.DEFAULT else Source.CACHE

            suspend fun fetchCollection(col: com.google.firebase.firestore.CollectionReference) =
                withTimeoutOrNull(SERVER_ACK_TIMEOUT_MS) {
                    col.get(source).await()
                } ?: col.get(Source.CACHE).await()

            val customers = fetchCollection(customersCollection(userId))
                .toObjects(Customer::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedBy { it.name.lowercase() }
            val transactions = fetchCollection(transactionsCollection(userId))
                .toObjects(LedgerTransaction::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.transactionDateMillis }
            val expenses = fetchCollection(expensesCollection(userId))
                .toObjects(BusinessExpense::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.expenseDateMillis }
            val loans = fetchCollection(loansCollection(userId))
                .toObjects(BankLoan::class.java, ServerTimestampBehavior.ESTIMATE)
                .sortedByDescending { it.startDateMillis }

            Result.success(
                ShouravBackupPayload(
                    schemaVersion = DriveBackupContract.SCHEMA_VERSION,
                    appName = DriveBackupContract.APP_IDENTIFIER,
                    userId = userId,
                    userEmail = userEmail,
                    exportedAtMillis = System.currentTimeMillis(),
                    customers = customers,
                    transactions = transactions,
                    expenses = expenses,
                    loans = loans,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users/$userId")
            Result.failure(e)
        }
    }

    suspend fun restoreFromBackup(
        userId: String,
        payload: ShouravBackupPayload,
        replaceExisting: Boolean = true,
    ): Result<RestoreSummary> {
        return try {
            val source = if (isNetworkAvailable()) Source.DEFAULT else Source.CACHE

            suspend fun fetchSnapshot(col: com.google.firebase.firestore.CollectionReference) =
                withTimeoutOrNull(SERVER_ACK_TIMEOUT_MS) {
                    col.get(source).await()
                } ?: col.get(Source.CACHE).await()

            val existingCustomerSnap = fetchSnapshot(customersCollection(userId))
            val existingTxSnap = fetchSnapshot(transactionsCollection(userId))
            val existingExpSnap = fetchSnapshot(expensesCollection(userId))
            val existingLoanSnap = fetchSnapshot(loansCollection(userId))

            val existingCustomerIds: Set<String>
            val existingTxIds: Set<String>
            val existingExpIds: Set<String>
            val existingLoanIds: Set<String>

            if (replaceExisting) {
                val allDeleteRefs = buildList {
                    addAll(existingCustomerSnap.documents.map { it.reference })
                    addAll(existingTxSnap.documents.map { it.reference })
                    addAll(existingExpSnap.documents.map { it.reference })
                    addAll(existingLoanSnap.documents.map { it.reference })
                }
                for (chunk in allDeleteRefs.chunked(400)) {
                    val deleteBatch = db.batch()
                    chunk.forEach { deleteBatch.delete(it) }
                    val deleteResult = executeOfflineSafeWrite(
                        task = deleteBatch.commit(),
                        op = OperationType.DELETE,
                        path = "users/$userId",
                        resultValue = Unit,
                    )
                    if (deleteResult.isFailure) {
                        return Result.failure(
                            deleteResult.exceptionOrNull() ?: IllegalStateException("Failed to clear previous records.")
                        )
                    }
                }
                existingCustomerIds = emptySet()
                existingTxIds = emptySet()
                existingExpIds = emptySet()
                existingLoanIds = emptySet()
            } else {
                existingCustomerIds = existingCustomerSnap.documents.map { it.id }.toSet()
                existingTxIds = existingTxSnap.documents.map { it.id }.toSet()
                existingExpIds = existingExpSnap.documents.map { it.id }.toSet()
                existingLoanIds = existingLoanSnap.documents.map { it.id }.toSet()
            }

            data class WriteOp(
                val ref: com.google.firebase.firestore.DocumentReference,
                val data: Map<String, Any>,
                val isUpdate: Boolean,
            )

            val writeOps = mutableListOf<WriteOp>()
            var customersRestored = 0
            var transactionsRestored = 0
            var expensesRestored = 0
            var loansRestored = 0

            val customerIdMap = mutableMapOf<String, String>()
            for (c in payload.customers) {
                val cleanName = c.name.trim().take(100)
                if (cleanName.isEmpty()) continue
                val ref = resolveDocRef(customersCollection(userId), c.id)
                if (c.id.isNotBlank() && c.id != ref.id) {
                    customerIdMap[c.id] = ref.id
                }
                val isUpdate = ref.id in existingCustomerIds
                val fields = mutableMapOf<String, Any>(
                    "name" to cleanName,
                    "phone" to c.phone.trim().take(30),
                    "shopAddress" to c.shopAddress.trim().take(200),
                    "openingBalance" to c.openingBalance.coerceAtLeast(0.0),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (!isUpdate) {
                    fields["userId"] = userId
                    fields["createdAt"] = FieldValue.serverTimestamp()
                }
                writeOps.add(WriteOp(ref, fields, isUpdate))
                customersRestored++
            }

            for (tx in payload.transactions) {
                val ref = resolveDocRef(transactionsCollection(userId), tx.id)
                val isUpdate = ref.id in existingTxIds
                val mappedCustomerId = (customerIdMap[tx.customerId] ?: tx.customerId).take(128)
                val cleanCustomerName = tx.customerName.trim().ifEmpty { "Walk-in Cash Customer" }.take(100)
                val cleanType = if (tx.type == LedgerTransaction.TYPE_PAYMENT) {
                    LedgerTransaction.TYPE_PAYMENT
                } else {
                    LedgerTransaction.TYPE_SALE
                }
                val cleanUnit = if (tx.unit == LedgerTransaction.UNIT_KG) {
                    LedgerTransaction.UNIT_KG
                } else {
                    LedgerTransaction.UNIT_BAG
                }
                val fields = mutableMapOf<String, Any>(
                    "customerId" to mappedCustomerId,
                    "customerName" to cleanCustomerName,
                    "type" to cleanType,
                    "riceVariety" to tx.riceVariety.trim().take(80),
                    "unit" to cleanUnit,
                    "quantity" to tx.quantity.coerceAtLeast(0.0),
                    "costPricePerUnit" to tx.costPricePerUnit.coerceAtLeast(0.0),
                    "sellingPricePerUnit" to tx.sellingPricePerUnit.coerceAtLeast(0.0),
                    "totalAmount" to tx.totalAmount.coerceAtLeast(0.0),
                    "paidAmount" to tx.paidAmount.coerceAtLeast(0.0),
                    "dueAdded" to tx.dueAdded,
                    "grossProfit" to tx.grossProfit,
                    "transactionDateMillis" to tx.transactionDateMillis.coerceAtLeast(0L),
                    "notes" to tx.notes.trim().take(300),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (!isUpdate) {
                    fields["userId"] = userId
                    fields["createdAt"] = FieldValue.serverTimestamp()
                }
                writeOps.add(WriteOp(ref, fields, isUpdate))
                transactionsRestored++
            }

            for (exp in payload.expenses) {
                if (exp.amount <= 0.0) continue
                val ref = resolveDocRef(expensesCollection(userId), exp.id)
                val isUpdate = ref.id in existingExpIds
                val cleanFreq = if (exp.frequency == BusinessExpense.FREQ_MONTHLY) {
                    BusinessExpense.FREQ_MONTHLY
                } else {
                    BusinessExpense.FREQ_DAILY
                }
                val fields = mutableMapOf<String, Any>(
                    "frequency" to cleanFreq,
                    "category" to exp.category.trim().ifEmpty { "Misc" }.take(60),
                    "amount" to exp.amount,
                    "notes" to exp.notes.trim().take(300),
                    "expenseDateMillis" to exp.expenseDateMillis.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (!isUpdate) {
                    fields["userId"] = userId
                    fields["createdAt"] = FieldValue.serverTimestamp()
                }
                writeOps.add(WriteOp(ref, fields, isUpdate))
                expensesRestored++
            }

            for (loan in payload.loans) {
                if (loan.principalAmount <= 0.0) continue
                val ref = resolveDocRef(loansCollection(userId), loan.id)
                val isUpdate = ref.id in existingLoanIds
                val fields = mutableMapOf<String, Any>(
                    "lenderName" to loan.lenderName.trim().ifEmpty { "Bank Loan" }.take(100),
                    "principalAmount" to loan.principalAmount,
                    "annualInterestRate" to loan.annualInterestRate.coerceIn(0.0, 100.0),
                    "startDateMillis" to loan.startDateMillis.coerceAtLeast(0L),
                    "paidInstallmentsCount" to loan.paidInstallmentsCount.coerceAtLeast(0L),
                    "totalPrincipalRepaid" to loan.totalPrincipalRepaid.coerceAtLeast(0.0),
                    "notes" to loan.notes.trim().take(300),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                if (!isUpdate) {
                    fields["userId"] = userId
                    fields["createdAt"] = FieldValue.serverTimestamp()
                }
                writeOps.add(WriteOp(ref, fields, isUpdate))
                loansRestored++
            }

            for (chunk in writeOps.chunked(400)) {
                val batch = db.batch()
                chunk.forEach { op ->
                    if (op.isUpdate) {
                        batch.update(op.ref, op.data)
                    } else {
                        batch.set(op.ref, op.data)
                    }
                }
                val writeResult = executeOfflineSafeWrite(
                    task = batch.commit(),
                    op = OperationType.WRITE,
                    path = "users/$userId",
                    resultValue = Unit,
                )
                if (writeResult.isFailure) {
                    return Result.failure(
                        writeResult.exceptionOrNull() ?: IllegalStateException("Failed to write restored records.")
                    )
                }
            }

            Result.success(
                RestoreSummary(
                    customersRestored = customersRestored,
                    transactionsRestored = transactionsRestored,
                    expensesRestored = expensesRestored,
                    loansRestored = loansRestored,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$userId")
            Result.failure(e)
        }
    }

    private fun resolveDocRef(
        collection: com.google.firebase.firestore.CollectionReference,
        rawId: String,
    ): com.google.firebase.firestore.DocumentReference {
        val cleanId = rawId.trim()
        return if (cleanId.length in 1..128 && FIRESTORE_ID_REGEX.matches(cleanId)) {
            collection.document(cleanId)
        } else {
            collection.document()
        }
    }

    private companion object {
        const val SERVER_ACK_TIMEOUT_MS = 3500L
        val FIRESTORE_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")
    }
}
