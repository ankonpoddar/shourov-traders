package com.example

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.DriveBackupContract
import com.example.data.backup.DriveBackupPreferences
import com.example.data.backup.DriveBackupUiState
import com.example.data.backup.GoogleDriveBackupClient
import com.example.data.firebase.configureAppCheck
import com.example.data.model.BankLoan
import com.example.data.model.BusinessAggregator
import com.example.data.model.BusinessExpense
import com.example.data.model.Customer
import com.example.data.model.LedgerTransaction
import com.example.data.preferences.LanguageMode
import com.example.data.preferences.LanguagePreferences
import com.example.data.report.MonthlyPdfExporter
import com.example.data.report.MonthlyReportBuilder
import com.example.data.repository.KhataAiRepository
import com.example.data.repository.LedgerRepository
import com.example.ui.screens.MainBusinessDashboard
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkDivider
import com.example.ui.theme.DarkOnBackground
import com.example.ui.theme.DarkOnSurface
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ShouravTradersTheme
import com.example.ui.viewmodel.BusinessViewModel
import com.example.ui.viewmodel.KhataScanUiState
import com.example.ui.viewmodel.KhataScannerViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowLooper
import java.io.OutputStream
import java.util.Calendar
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class BugFixesVerificationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ShadowLog.clear()
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun waitUntilCondition(
        timeoutMillis: Long = 8_000L,
        failureMessage: () -> String = { "Condition not met within ${timeoutMillis}ms" },
        predicate: () -> Boolean,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!predicate()) {
            if (System.currentTimeMillis() > deadline) {
                throw AssertionError(failureMessage())
            }
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
            Thread.sleep(15L)
        }
    }

    private fun ensureTestFirebaseApp(): FirebaseApp {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.shouravtraders.krbnvx")
                    .setProjectId("shourav-traders-test")
                    .setApiKey("test-api-key")
                    .build(),
            )
        }
        return FirebaseApp.getInstance()
    }

    @Test
    fun fix1_appCheckSkipsWarningLogWhenNoDebugTokenExtraPresent() {
        val app = ensureTestFirebaseApp()

        // Normal launch without FIREBASE_APPCHECK_DEBUG_TOKEN intent extra
        configureAppCheck(context, Intent())

        val warnLogs = ShadowLog.getLogsForTag("AppCheckInit")
            .filter { it.type == Log.WARN }
        assertTrue(
            "Expected no AppCheckInit warning log on normal launch, got: ${warnLogs.map { it.msg }}",
            warnLogs.none { it.msg.contains("No debug token found", ignoreCase = true) },
        )

        // Launch with debug token extra writes secret synchronously
        val debugIntent = Intent().putExtra("FIREBASE_APPCHECK_DEBUG_TOKEN", "test-debug-token-999")
        configureAppCheck(context, debugIntent)
        val prefs = context.getSharedPreferences(
            "com.google.firebase.appcheck.debug.store.${app.persistenceKey}",
            Context.MODE_PRIVATE,
        )
        assertEquals(
            "test-debug-token-999",
            prefs.getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null),
        )
    }

    @Test
    fun fix2_khataScannerShowsErrorStateInsteadOfBlankRowWhenAiReturnsEmpty() {
        val app = ensureTestFirebaseApp()
        val fakeFirestore = FirebaseFirestore.getInstance(app, "test-scanner-db").apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
        val aiRepo = KhataAiRepository(
            customScanner = { _, _ -> Result.success(emptyList()) },
        )
        val ledgerRepo = LedgerRepository(db = fakeFirestore, isNetworkAvailable = { false })
        val vm = KhataScannerViewModel(
            aiRepository = aiRepo,
            ledgerRepository = ledgerRepo,
            currentUserId = "test_user",
        )

        val testUri = Uri.parse("content://media/external/images/123")
        vm.analyzeKhataImage(context, testUri)

        waitUntilCondition {
            val s = vm.scanState.value
            s !is KhataScanUiState.Idle && s !is KhataScanUiState.Scanning
        }

        val state = vm.scanState.value
        assertTrue("Expected Error state instead of Review, got $state", state is KhataScanUiState.Error)
        val errorState = state as KhataScanUiState.Error
        assertEquals(
            "No ledger rows detected. Try a clearer photo or use manual entry.",
            errorState.message,
        )
        assertEquals(testUri, errorState.imageUri)
    }

    @Test
    fun fix3a_dailyTrendsSubtractLoanInterestFromMonthlyBurdenBeforeDividingBy30() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 16, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Fixed monthly expense = 3,000 -> daily slice should be 3,000 / 30 = 100
        // Loan: 600,000 at 12% annual -> 6,000/month (should NOT be double-counted in dailyTrend)
        val expenses = listOf(
            BusinessExpense(
                id = "e_monthly",
                frequency = BusinessExpense.FREQ_MONTHLY,
                category = "Rent",
                amount = 3000.0,
                expenseDateMillis = now,
            ),
        )
        val loans = listOf(
            BankLoan(
                id = "l1",
                lenderName = "Bank",
                principalAmount = 600000.0,
                annualInterestRate = 12.0,
                startDateMillis = now,
                totalPrincipalRepaid = 0.0,
            ),
        )
        val transactions = listOf(
            LedgerTransaction(
                id = "sale_today",
                customerId = "c1",
                customerName = "Rahim",
                type = LedgerTransaction.TYPE_SALE,
                totalAmount = 5000.0,
                paidAmount = 5000.0,
                dueAdded = 0.0,
                grossProfit = 500.0,
                transactionDateMillis = now,
            ),
        )

        val aggregated = BusinessAggregator.aggregate(
            customers = listOf(Customer(id = "c1", name = "Rahim")),
            transactions = transactions,
            expenses = expenses,
            loans = loans,
            referenceTimeMillis = now,
        )

        // Daily trend has 7 days ending today. Today netProfit = 500 - (3000 / 30) = 400
        val todayPoint7d = aggregated.businessSummary.dailyTrend.last()
        assertEquals(5000.0, todayPoint7d.sales, 0.01)
        assertEquals(400.0, todayPoint7d.netProfit, 0.01)

        // 30-day period analytics uses 5-day buckets -> bucket overhead = (3000 / 30) * 5 = 500
        // Today bucket netProfit = 500 - 500 = 0 (not -1000 from double-counted loan interest)
        val p30 = BusinessAggregator.buildPeriodAnalytics(
            daysCount = 30,
            transactions = transactions,
            expenses = expenses,
            loans = loans,
            referenceTimeMillis = now,
        )
        val lastBucket30d = p30.trendPoints.last()
        assertEquals(0.0, lastBucket30d.netProfit, 0.01)
    }

    @Test
    fun fix3b_monthlyPdfPageNumbersStartAt1AndIncrementAcrossPages() {
        val nov12 = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 12, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val customers = (1..48).map { idx ->
            Customer(
                id = "c$idx",
                name = "Customer $idx",
                phone = "01711-0000$idx",
                openingBalance = 1000.0 * idx,
            )
        }
        val transactions = (1..48).map { idx ->
            LedgerTransaction(
                id = "t$idx",
                customerId = "c$idx",
                customerName = "Customer $idx",
                type = LedgerTransaction.TYPE_SALE,
                riceVariety = "Miniket",
                totalAmount = 15000.0,
                paidAmount = 10000.0,
                dueAdded = 5000.0,
                grossProfit = 1200.0,
                transactionDateMillis = nov12,
            )
        }

        val period = MonthlyReportBuilder.createMonthOption(2026, Calendar.NOVEMBER)
        val report = MonthlyReportBuilder.buildReport(
            period = period,
            customers = customers,
            transactions = transactions,
            expenses = emptyList(),
            loans = emptyList(),
            generatedAtMillis = nov12,
        )

        val drawnTextsPerPage = mutableListOf<List<String>>()
        val startedPageNumbers = mutableListOf<Int>()

        val sink = object : MonthlyPdfExporter.PdfSink {
            private var openBitmap: Bitmap? = null
            private val currentPageTexts = mutableListOf<String>()

            override fun startPage(width: Int, height: Int, pageNumber: Int): Canvas {
                startedPageNumbers.add(pageNumber)
                currentPageTexts.clear()
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                openBitmap = bmp
                return object : Canvas(bmp) {
                    override fun drawText(text: String, x: Float, y: Float, paint: Paint) {
                        currentPageTexts.add(text)
                        super.drawText(text, x, y, paint)
                    }
                }
            }

            override fun finishPage() {
                drawnTextsPerPage.add(currentPageTexts.toList())
                openBitmap?.recycle()
                openBitmap = null
            }

            override fun writeTo(outputStream: OutputStream) {}
            override fun close() {}
        }

        MonthlyPdfExporter.renderToSink(report, sink)

        assertTrue("Expected >= 3 pages, got ${startedPageNumbers.size}", startedPageNumbers.size >= 3)
        assertEquals((1..startedPageNumbers.size).toList(), startedPageNumbers)
        drawnTextsPerPage.forEachIndexed { index, texts ->
            val expectedFooter = "Page ${index + 1}"
            assertTrue(
                "Expected footer '$expectedFooter' on page ${index + 1}, got $texts",
                texts.contains(expectedFooter),
            )
        }
    }

    @Test
    fun fix4_googleDriveBackupUpdatesLocalCacheOnFreshPostAndSubsequentPatch() {
        val app = ensureTestFirebaseApp()
        val offlineDb = FirebaseFirestore.getInstance(app, "test-drive-cache-${UUID.randomUUID()}").apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }

        val repo = LedgerRepository(db = offlineDb, isNetworkAvailable = { false })
        val recordedMethods = mutableListOf<String>()
        var remoteDeletedOnce = false

        val fakeHttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val method = req.method
                val path = req.url.encodedPath
                recordedMethods.add("$method $path")
                val (code, json) = when {
                    method == "GET" && path == "/drive/v3/files" -> {
                        200 to """{"files":[]}"""
                    }
                    method == "POST" && path == "/upload/drive/v3/files" -> {
                        val id = if (recordedMethods.count { it.startsWith("POST") } == 1) "fresh_file_1" else "fresh_file_2"
                        200 to """{"id":"$id","name":"${DriveBackupContract.BACKUP_FILE_NAME}","modifiedTime":"2026-11-17T06:00:00Z","size":"256"}"""
                    }
                    method == "PATCH" && path == "/upload/drive/v3/files/fresh_file_1" && !remoteDeletedOnce -> {
                        remoteDeletedOnce = true
                        404 to """{"error":{"message":"File not found"}}"""
                    }
                    method == "PATCH" && path.startsWith("/upload/drive/v3/files/") -> {
                        val fileId = path.substringAfterLast("/")
                        200 to """{"id":"$fileId","name":"${DriveBackupContract.BACKUP_FILE_NAME}","modifiedTime":"2026-11-17T06:05:00Z","size":"300"}"""
                    }
                    else -> 404 to """{"error":{"message":"Not found"}}"""
                }
                Response.Builder()
                    .request(req)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("OK")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val client = GoogleDriveBackupClient(
            httpClient = fakeHttp,
            driveApiBaseUrl = "https://mock.googleapis.com/drive/v3",
            driveUploadBaseUrl = "https://mock.googleapis.com/upload/drive/v3",
        )

        val vm = BusinessViewModel(
            repository = repo,
            languagePreferences = LanguagePreferences(context),
            currentUserId = "drive_test_user_${UUID.randomUUID()}",
            driveBackupClient = client,
            driveBackupPreferences = DriveBackupPreferences(context),
        )

        var latestDriveState = DriveBackupUiState()
        val collectorJob = CoroutineScope(Dispatchers.Main.immediate).launch {
            vm.driveBackupState.collect { latestDriveState = it }
        }
        try {
            // 1) Fresh POST backup when no remote file exists
            vm.backupToGoogleDrive(accessToken = "token_abc", userEmail = "owner@shourav.com")
            waitUntilCondition(
                failureMessage = { "First backup did not update state: state=$latestDriveState feedback=${vm.feedbackMessage.value} methods=$recordedMethods" },
            ) {
                latestDriveState.lastRemoteFileId == "fresh_file_1" && latestDriveState.lastBackupAtMillis != null
            }
            val afterFirstBackup = latestDriveState
            assertEquals(DriveBackupContract.BACKUP_FILE_NAME, afterFirstBackup.lastRemoteFileName)
            assertTrue((afterFirstBackup.lastBackupAtMillis ?: 0L) > 0L)

            // 2) Second backup where cached file returns 404 on PATCH -> falls back to fresh POST -> updates fileId to fresh_file_2
            vm.backupToGoogleDrive(accessToken = "token_abc", userEmail = "owner@shourav.com")
            waitUntilCondition(
                failureMessage = { "Second backup did not update state: state=$latestDriveState feedback=${vm.feedbackMessage.value} methods=$recordedMethods" },
            ) {
                latestDriveState.lastRemoteFileId == "fresh_file_2"
            }
            assertTrue(
                (latestDriveState.lastBackupAtMillis ?: 0L) >= (afterFirstBackup.lastBackupAtMillis ?: 0L),
            )
        } finally {
            collectorJob.cancel()
        }
    }

    @Test
    fun fix5_darkThemeUsesExactRequestedPaletteAndRendersDashboard() {
        var capturedBg: Color? = null
        var capturedSurface: Color? = null
        var capturedOnBg: Color? = null
        var capturedOnSurface: Color? = null
        var capturedOutline: Color? = null
        var capturedOutlineVariant: Color? = null
        var capturedPrimary: Color? = null

        composeRule.setContent {
            ShouravTradersTheme(darkTheme = true) {
                capturedBg = MaterialTheme.colorScheme.background
                capturedSurface = MaterialTheme.colorScheme.surface
                capturedOnBg = MaterialTheme.colorScheme.onBackground
                capturedOnSurface = MaterialTheme.colorScheme.onSurface
                capturedOutline = MaterialTheme.colorScheme.outline
                capturedOutlineVariant = MaterialTheme.colorScheme.outlineVariant
                capturedPrimary = MaterialTheme.colorScheme.primary

                MainBusinessDashboard(
                    customers = listOf(Customer(id = "c1", name = "Rahim Store", openingBalance = 5000.0)),
                    transactions = emptyList(),
                    expenses = emptyList(),
                    loans = emptyList(),
                    mode = LanguageMode.ENGLISH,
                    isBusy = false,
                    onQuickNewSale = {},
                    onQuickAddCustomer = {},
                    onQuickAddExpense = {},
                    onQuickScanKhata = {},
                    onLoadSampleData = {},
                    onOpenProfileAnalytics = {},
                )
            }
        }

        composeRule.waitForIdle()
        assertEquals(Color(0xFF0B1117), capturedBg)
        assertEquals(DarkBackground, capturedBg)
        assertEquals(Color(0xFF111922), capturedSurface)
        assertEquals(DarkSurface, capturedSurface)
        assertEquals(Color(0xFFE2E8F0), capturedOnBg)
        assertEquals(DarkOnBackground, capturedOnBg)
        assertEquals(Color(0xFFFAFAFA), capturedOnSurface)
        assertEquals(DarkOnSurface, capturedOnSurface)
        assertEquals(Color(0xFF37474F), capturedOutline)
        assertEquals(DarkDivider, capturedOutline)
        assertEquals(Color(0xFF37474F), capturedOutlineVariant)
        assertEquals(Color(0xFF1B5E20), capturedPrimary)
        assertEquals(EmeraldPrimary, capturedPrimary)

        composeRule.onNodeWithTag("main_business_dashboard").assertIsDisplayed()
    }
}
