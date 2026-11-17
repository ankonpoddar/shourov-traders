package com.example.data.backup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.data.model.BankLoan
import com.example.data.model.BusinessExpense
import com.example.data.model.Customer
import com.example.data.model.LedgerTransaction
import com.example.data.preferences.LanguageMode
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ShouravTradersTheme
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class GoogleDriveBackupTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun ledgerBackupSerializer_roundTripPreservesAllRecordsAndIds() {
        val original = ShouravBackupPayload(
            schemaVersion = 1,
            appName = DriveBackupContract.APP_IDENTIFIER,
            userId = "uid_123",
            userEmail = "shourav@example.com",
            exportedAtMillis = 1_760_000_000_000L,
            customers = listOf(
                Customer(
                    id = "cust_1",
                    name = "Rahim Store (রহিম স্টোর)",
                    phone = "01711-000111",
                    shopAddress = "Badamtoli",
                    openingBalance = 12000.0,
                )
            ),
            transactions = listOf(
                LedgerTransaction(
                    id = "tx_1",
                    customerId = "cust_1",
                    customerName = "Rahim Store (রহিম স্টোর)",
                    type = LedgerTransaction.TYPE_SALE,
                    riceVariety = "Miniket",
                    unit = LedgerTransaction.UNIT_BAG,
                    quantity = 10.0,
                    costPricePerUnit = 3300.0,
                    sellingPricePerUnit = 3600.0,
                    totalAmount = 36000.0,
                    paidAmount = 20000.0,
                    dueAdded = 16000.0,
                    grossProfit = 3000.0,
                    transactionDateMillis = 1_760_000_000_000L,
                    notes = "50kg sacks",
                ),
                LedgerTransaction(
                    id = "tx_2",
                    customerId = "cust_1",
                    customerName = "Rahim Store (রহিম স্টোর)",
                    type = LedgerTransaction.TYPE_PAYMENT,
                    paidAmount = 5000.0,
                    dueAdded = -5000.0,
                    transactionDateMillis = 1_760_050_000_000L,
                    notes = "Cash Joma",
                ),
            ),
            expenses = listOf(
                BusinessExpense(
                    id = "exp_1",
                    frequency = BusinessExpense.FREQ_DAILY,
                    category = "Transport / Van",
                    amount = 1200.0,
                    notes = "Van delivery",
                    expenseDateMillis = 1_760_000_000_000L,
                )
            ),
            loans = listOf(
                BankLoan(
                    id = "loan_1",
                    lenderName = "Islami Bank SME",
                    principalAmount = 500000.0,
                    annualInterestRate = 13.0,
                    startDateMillis = 1_750_000_000_000L,
                    paidInstallmentsCount = 2L,
                    totalPrincipalRepaid = 50000.0,
                    notes = "Working capital",
                )
            ),
        )

        val json = LedgerBackupSerializer.toJson(original)
        val parsed = LedgerBackupSerializer.fromJson(json).getOrThrow()

        assertEquals(original.appName, parsed.appName)
        assertEquals(original.userEmail, parsed.userEmail)
        assertEquals(1, parsed.customers.size)
        assertEquals("cust_1", parsed.customers[0].id)
        assertEquals("Rahim Store (রহিম স্টোর)", parsed.customers[0].name)
        assertEquals(12000.0, parsed.customers[0].openingBalance, 0.001)

        assertEquals(2, parsed.transactions.size)
        assertEquals("cust_1", parsed.transactions[0].customerId)
        assertEquals(-5000.0, parsed.transactions[1].dueAdded, 0.001)

        assertEquals(1, parsed.expenses.size)
        assertEquals("exp_1", parsed.expenses[0].id)
        assertEquals(1200.0, parsed.expenses[0].amount, 0.001)

        assertEquals(1, parsed.loans.size)
        assertEquals(13.0, parsed.loans[0].annualInterestRate, 0.001)
    }

    @Test
    fun ledgerBackupSerializer_rejectsInvalidOrForeignJson() {
        assertTrue(LedgerBackupSerializer.fromJson("").isFailure)
        assertTrue(LedgerBackupSerializer.fromJson("{not valid json").isFailure)
        assertTrue(
            LedgerBackupSerializer.fromJson("""{"appName":"Other App","customers":[]}""").isFailure
        )
    }

    @Test
    fun googleDriveBackupClient_createsUpdatesAndDownloadsBackupJson() = runTest {
        val recordedRequests = mutableListOf<String>()
        var remoteFileExists = false
        var savedJsonContent = ""

        val fakeClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val auth = req.header("Authorization")
                assertEquals("Bearer test_token_123", auth)

                val method = req.method
                val path = req.url.encodedPath
                val query = req.url.query.orEmpty()
                recordedRequests.add("$method $path?$query")

                val bodyBuffer = Buffer()
                req.body?.writeTo(bodyBuffer)
                val reqBodyText = bodyBuffer.readUtf8()

                val responseJson: String
                val code: Int
                when {
                    method == "GET" && path == "/drive/v3/files" -> {
                        code = 200
                        responseJson = if (remoteFileExists) {
                            """{"files":[{"id":"drive_file_1","name":"Shourav_Traders_Backup.json","modifiedTime":"2026-11-17T05:00:00Z","size":"${savedJsonContent.length}"}]}"""
                        } else {
                            """{"files":[]}"""
                        }
                    }
                    method == "POST" && path == "/upload/drive/v3/files" -> {
                        assertTrue(reqBodyText.contains("Shourav_Traders_Backup.json"))
                        assertTrue(reqBodyText.contains("Rahim Store"))
                        remoteFileExists = true
                        savedJsonContent = """{"appName":"Shourav Traders","customers":[{"id":"c1","name":"Rahim Store"}]}"""
                        code = 200
                        responseJson = """{"id":"drive_file_1","name":"Shourav_Traders_Backup.json","modifiedTime":"2026-11-17T05:01:00Z","size":"${savedJsonContent.length}"}"""
                    }
                    method == "PATCH" && path == "/upload/drive/v3/files/drive_file_1" -> {
                        savedJsonContent = reqBodyText
                        code = 200
                        responseJson = """{"id":"drive_file_1","name":"Shourav_Traders_Backup.json","modifiedTime":"2026-11-17T05:02:00Z","size":"${savedJsonContent.length}"}"""
                    }
                    method == "GET" && path == "/drive/v3/files/drive_file_1" && query.contains("alt=media") -> {
                        code = 200
                        responseJson = savedJsonContent
                    }
                    else -> {
                        code = 404
                        responseJson = """{"error":{"message":"Not Found"}}"""
                    }
                }

                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("OK")
                    .body(responseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val client = GoogleDriveBackupClient(
            httpClient = fakeClient,
            driveApiBaseUrl = "https://mock.googleapis.com/drive/v3",
            driveUploadBaseUrl = "https://mock.googleapis.com/upload/drive/v3",
        )

        val sampleJson = """{"appName":"Shourav Traders","customers":[{"id":"c1","name":"Rahim Store"}]}"""
        val created = client.uploadBackupJson("test_token_123", sampleJson).getOrThrow()
        assertEquals("drive_file_1", created.fileId)
        assertEquals(DriveBackupContract.BACKUP_FILE_NAME, created.fileName)

        val updatedJson = """{"appName":"Shourav Traders","customers":[{"id":"c1","name":"Rahim Store Updated"}]}"""
        val updated = client.uploadBackupJson("test_token_123", updatedJson, existingFileId = created.fileId).getOrThrow()
        assertEquals("drive_file_1", updated.fileId)

        val found = client.findLatestBackupFile("test_token_123").getOrThrow()
        assertNotNull(found)
        assertEquals("drive_file_1", found?.fileId)

        val downloaded = client.downloadBackupJson("test_token_123", "drive_file_1").getOrThrow()
        assertTrue(downloaded.contains("Rahim Store Updated"))
        assertTrue(recordedRequests.any { it.startsWith("POST /upload/drive/v3/files") })
        assertTrue(recordedRequests.any { it.startsWith("PATCH /upload/drive/v3/files/drive_file_1") })
    }

    @Test
    fun googleDriveBackupClient_returnsDriveAuthExceptionOn401() = runTest {
        val unauthorizedClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body("""{"error":{"message":"Invalid Credentials"}}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val client = GoogleDriveBackupClient(
            httpClient = unauthorizedClient,
            driveApiBaseUrl = "https://mock.googleapis.com/drive/v3",
            driveUploadBaseUrl = "https://mock.googleapis.com/upload/drive/v3",
        )

        val result = client.findLatestBackupFile("expired_token")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DriveAuthException)
    }

    @Test
    fun settingsScreen_rendersDriveBackupAndRestoreRowsAndTriggersCallbacks() {
        var backupClicked = false
        var restoreClicked = false

        composeRule.setContent {
            ShouravTradersTheme {
                SettingsScreen(
                    userEmail = "shourav@example.com",
                    languageMode = LanguageMode.ENGLISH,
                    isOnline = true,
                    hasPendingWrites = false,
                    customerCount = 4,
                    onSelectLanguageMode = {},
                    onOpenAnalytics = {},
                    onOpenScanner = {},
                    onExportMonthlyPdf = {},
                    onLoadSampleData = {},
                    onRequestResetData = {},
                    onSignOut = {},
                    driveBackupState = DriveBackupUiState(
                        lastBackupAtMillis = 1_760_000_000_000L,
                        lastRemoteFileName = DriveBackupContract.BACKUP_FILE_NAME,
                    ),
                    onBackupToGoogleDrive = { backupClicked = true },
                    onRestoreFromGoogleDrive = { restoreClicked = true },
                )
            }
        }

        val list = composeRule.onNodeWithTag("settings_screen")
        list.performScrollToNode(hasTestTag("settings_drive_backup_row"))
        composeRule.onNodeWithTag("settings_drive_backup_row").assertIsDisplayed().performClick()
        assertTrue(backupClicked)

        composeRule.onNodeWithText("Backup Data to Google Drive").assertIsDisplayed()

        list.performScrollToNode(hasTestTag("settings_drive_restore_row"))
        composeRule.onNodeWithTag("settings_drive_restore_row").assertIsDisplayed().performClick()
        assertTrue(restoreClicked)
    }
}
