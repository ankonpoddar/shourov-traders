package com.example.data.backup

import com.example.data.model.BankLoan
import com.example.data.model.BusinessExpense
import com.example.data.model.Customer
import com.example.data.model.LedgerTransaction

object DriveBackupContract {
    const val BACKUP_FILE_NAME = "Shourav_Traders_Backup.json"
    const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    const val SCHEMA_VERSION = 1
    const val APP_IDENTIFIER = "Shourav Traders"
}

data class ShouravBackupPayload(
    val schemaVersion: Int = DriveBackupContract.SCHEMA_VERSION,
    val appName: String = DriveBackupContract.APP_IDENTIFIER,
    val userId: String = "",
    val userEmail: String = "",
    val exportedAtMillis: Long = 0L,
    val customers: List<Customer> = emptyList(),
    val transactions: List<LedgerTransaction> = emptyList(),
    val expenses: List<BusinessExpense> = emptyList(),
    val loans: List<BankLoan> = emptyList(),
) {
    val totalRecordCount: Int
        get() = customers.size + transactions.size + expenses.size + loans.size
}

data class DriveBackupFileMetadata(
    val fileId: String,
    val fileName: String,
    val modifiedTimeIso: String = "",
    val sizeBytes: Long = 0L,
)

data class RestoreSummary(
    val customersRestored: Int,
    val transactionsRestored: Int,
    val expensesRestored: Int,
    val loansRestored: Int,
) {
    val totalRestored: Int
        get() = customersRestored + transactionsRestored + expensesRestored + loansRestored
}

data class PendingDriveRestore(
    val fileMetadata: DriveBackupFileMetadata,
    val payload: ShouravBackupPayload,
)

enum class DriveBackupWorkStatus {
    IDLE,
    AUTHORIZING,
    BACKING_UP,
    FETCHING_BACKUP,
    RESTORING,
}

data class DriveBackupUiState(
    val workStatus: DriveBackupWorkStatus = DriveBackupWorkStatus.IDLE,
    val lastBackupAtMillis: Long? = null,
    val lastRestoreAtMillis: Long? = null,
    val lastRemoteFileId: String? = null,
    val lastRemoteFileName: String? = null,
    val pendingRestore: PendingDriveRestore? = null,
) {
    val isWorking: Boolean
        get() = workStatus != DriveBackupWorkStatus.IDLE
}

class DriveAuthException(
    val statusCode: Int,
    message: String,
) : Exception(message)
