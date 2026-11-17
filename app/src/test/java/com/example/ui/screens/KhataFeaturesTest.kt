package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.data.model.BusinessAggregator
import com.example.data.model.BusinessExpense
import com.example.data.model.Customer
import com.example.data.model.LedgerTransaction
import com.example.data.preferences.LanguageMode
import com.example.data.preferences.formatBdt
import com.example.data.report.TransactionSlipData
import com.example.data.report.TransactionSlipPdfExporter
import com.example.ui.components.PaymentKhataPreviewCard
import com.example.ui.theme.ShouravTradersTheme
import com.example.ui.viewmodel.BusinessUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.OutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class KhataFeaturesTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = System.currentTimeMillis()
    private val twelveDaysAgo = now - 12L * 24L * 60L * 60L * 1000L

    private val customers = listOf(
        Customer(id = "c_z", name = "Zahir Rice Agency", phone = "01711", openingBalance = 5000.0),
        Customer(id = "c_a", name = "Alim Store", phone = "01811", openingBalance = 2000.0),
    )

    private val transactions = listOf(
        LedgerTransaction(
            id = "tx_sale",
            customerId = "c_a",
            customerName = "Alim Store",
            type = LedgerTransaction.TYPE_SALE,
            riceVariety = "Miniket",
            unit = LedgerTransaction.UNIT_BAG,
            quantity = 5.0,
            costPricePerUnit = 3200.0,
            sellingPricePerUnit = 3600.0,
            totalAmount = 18000.0,
            paidAmount = 10000.0,
            dueAdded = 8000.0,
            grossProfit = 2000.0,
            transactionDateMillis = now - 2000L,
        ),
        LedgerTransaction(
            id = "tx_joma",
            customerId = "c_a",
            customerName = "Alim Store",
            type = LedgerTransaction.TYPE_PAYMENT,
            paidAmount = 3000.0,
            dueAdded = -3000.0,
            transactionDateMillis = now,
        ),
        LedgerTransaction(
            id = "tx_old",
            customerId = "c_z",
            customerName = "Zahir Rice Agency",
            type = LedgerTransaction.TYPE_SALE,
            riceVariety = "BR-28",
            unit = LedgerTransaction.UNIT_BAG,
            quantity = 10.0,
            costPricePerUnit = 2800.0,
            sellingPricePerUnit = 3000.0,
            totalAmount = 30000.0,
            paidAmount = 30000.0,
            dueAdded = 0.0,
            grossProfit = 2000.0,
            transactionDateMillis = twelveDaysAgo,
        ),
    )

    @Test
    fun customerLedgerScreen_showsAzSerialsAndFiltersViaSearchBar() {
        val aggregated = BusinessAggregator.aggregate(
            customers = customers,
            transactions = transactions,
            expenses = emptyList(),
            loans = emptyList(),
            referenceTimeMillis = now,
        )
        val ready = BusinessUiState.Ready(
            customers = customers,
            customerSummaries = aggregated.customerSummaries,
            transactions = transactions,
            expenses = emptyList(),
            loans = emptyList(),
            summary = aggregated.businessSummary,
        )

        composeRule.setContent {
            ShouravTradersTheme {
                CustomerLedgerScreen(
                    state = ready,
                    mode = LanguageMode.ENGLISH,
                    onOpenCustomer = {},
                    onAddCustomerClick = {},
                )
            }
        }

        // Alim Store is A-Z #1, Zahir Rice Agency is A-Z #2
        composeRule.onNodeWithTag("customer_serial_1", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("#1", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Alim Store", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("customer_serial_2", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("#2", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Zahir Rice Agency", useUnmergedTree = true).assertIsDisplayed()

        // Search for "Zahir" -> Zahir stays #2 and Alim disappears
        composeRule.onNodeWithTag("customer_search_input").performTextInput("Zahir")
        composeRule.onNodeWithText("Zahir Rice Agency", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Alim Store", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("customer_serial_2", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun customerDetailScreen_displaysRunningBalanceAndOpensSlipPdfDialog() {
        val aggregated = BusinessAggregator.aggregate(
            customers = customers,
            transactions = transactions,
            expenses = emptyList(),
            loans = emptyList(),
            referenceTimeMillis = now,
        )
        val alimSummary = aggregated.customerSummaries.first { it.customer.id == "c_a" }

        composeRule.setContent {
            ShouravTradersTheme {
                CustomerDetailScreen(
                    summary = alimSummary,
                    transactions = transactions,
                    mode = LanguageMode.ENGLISH,
                    onBack = {},
                    onRecordPayment = { _, _ -> },
                    onNewSaleForCustomer = {},
                )
            }
        }

        // Action buttons & Khata equation card
        composeRule.onNodeWithTag("customer_record_payment_button").assertIsDisplayed()
        composeRule.onNodeWithTag("customer_new_sale_button").assertIsDisplayed()
        val detailList = composeRule.onNodeWithTag("customer_detail_list")
        detailList.performScrollToNode(hasTestTag("customer_khata_equation_card"))
        composeRule.onNodeWithTag("customer_khata_equation_card").assertIsDisplayed()

        // Both sale & joma slip PDF buttons exist; click joma slip PDF button to open dialog
        detailList.performScrollToNode(hasTestTag("tx_slip_pdf_btn_tx_joma"))
        composeRule.onNodeWithTag("tx_slip_pdf_btn_tx_joma").performClick()

        composeRule.onNodeWithText("Payment Receipt PDF").assertIsDisplayed()
        composeRule.onNodeWithTag("slip_share_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("slip_save_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("slip_print_btn").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun paymentKhataPreviewCard_showsSubtractionOfJomaFromCurrentDue() {
        composeRule.setContent {
            ShouravTradersTheme {
                PaymentKhataPreviewCard(
                    currentDue = 7000.0,
                    paidAmount = 2500.0,
                    mode = LanguageMode.ENGLISH,
                )
            }
        }

        composeRule.onNodeWithText(formatBdt(7000.0)).assertIsDisplayed()
        composeRule.onNodeWithText("-${formatBdt(2500.0)}").assertIsDisplayed()
        // 7,000 - 2,500 = 4,500 remaining due
        composeRule.onNodeWithTag("payment_remaining_due_value").assertIsDisplayed()
        composeRule.onNodeWithText(formatBdt(4500.0)).assertIsDisplayed()
    }

    @Test
    fun profileAnalyticsScreen_switchesBetween7DaysAnd30Days() {
        composeRule.setContent {
            ShouravTradersTheme {
                ProfileAnalyticsScreen(
                    userEmail = "shourav@example.com",
                    transactions = transactions,
                    expenses = listOf(
                        BusinessExpense(
                            id = "e1",
                            frequency = BusinessExpense.FREQ_DAILY,
                            category = "Transport / Van",
                            amount = 500.0,
                            expenseDateMillis = now,
                        )
                    ),
                    loans = emptyList(),
                    mode = LanguageMode.ENGLISH,
                    onBack = {},
                    onExportMonthlyPdf = {},
                    onSignOut = {},
                    referenceTimeMillis = now,
                )
            }
        }

        // Default 7 Days shows 18,000 sales
        composeRule.onNodeWithText(formatBdt(18000.0)).assertIsDisplayed()

        // Tap 30 Days -> shows 18,000 + 30,000 = 48,000 sales
        composeRule.onNodeWithTag("analytics_30d_btn").performClick()
        composeRule.onNodeWithText(formatBdt(48000.0)).assertIsDisplayed()
    }

    @Test
    fun transactionSlipPdfExporter_rendersBothSaleAndPaymentSlips() {
        val timeline = BusinessAggregator.buildCustomerLedgerTimeline(customers[1], transactions)
        val fakeSink = object : TransactionSlipPdfExporter.SlipPdfSink {
            var startedPages = 0
            var finishedPages = 0
            private var bmp: Bitmap? = null

            override fun startPage(width: Int, height: Int): Canvas {
                startedPages++
                bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                return Canvas(bmp!!)
            }

            override fun finishPage() {
                finishedPages++
                bmp?.recycle()
                bmp = null
            }

            override fun writeTo(outputStream: OutputStream) {}
            override fun close() {}
        }

        timeline.forEach { entry ->
            TransactionSlipPdfExporter.renderSlip(
                TransactionSlipData(customer = customers[1], entry = entry),
                fakeSink,
            )
        }

        assertEquals(2, fakeSink.startedPages)
        assertEquals(2, fakeSink.finishedPages)
    }

    @Test
    fun customerLedgerScreen_customerLendenTabShowsCustomerTransactionsAndFilters() {
        val aggregated = BusinessAggregator.aggregate(
            customers = customers,
            transactions = transactions,
            expenses = emptyList(),
            loans = emptyList(),
            referenceTimeMillis = now,
        )
        val ready = BusinessUiState.Ready(
            customers = customers,
            customerSummaries = aggregated.customerSummaries,
            transactions = transactions,
            expenses = emptyList(),
            loans = emptyList(),
            summary = aggregated.businessSummary,
        )

        composeRule.setContent {
            ShouravTradersTheme {
                CustomerLedgerScreen(
                    state = ready,
                    mode = LanguageMode.ENGLISH,
                    onOpenCustomer = {},
                    onAddCustomerClick = {},
                    initialShowTransactions = true,
                )
            }
        }

        // Customer Lenden shows customer transactions instead of generic income/expense
        composeRule.onNodeWithTag("tab_customer_lenden").assertIsDisplayed()
        composeRule.onNodeWithTag("lenden_slip_btn_tx_sale").assertIsDisplayed()
        composeRule.onNodeWithTag("lenden_slip_btn_tx_joma").assertIsDisplayed()

        // Filter to Joma / Cash -> only payment tx remains
        composeRule.onNodeWithText("Joma / Cash").performClick()
        composeRule.onNodeWithTag("lenden_slip_btn_tx_joma").assertIsDisplayed()
        composeRule.onNodeWithTag("lenden_slip_btn_tx_sale").assertDoesNotExist()
    }

    @Test
    fun settingsScreen_rendersThemeSectionsAndTriggersLanguageAndPdfActions() {
        var selectedMode: LanguageMode? = null
        var pdfExportClicked = false

        composeRule.setContent {
            ShouravTradersTheme {
                SettingsScreen(
                    userEmail = "shourav@example.com",
                    languageMode = LanguageMode.BILINGUAL,
                    isOnline = true,
                    hasPendingWrites = false,
                    customerCount = 5,
                    onSelectLanguageMode = { selectedMode = it },
                    onOpenAnalytics = {},
                    onOpenScanner = {},
                    onExportMonthlyPdf = { pdfExportClicked = true },
                    onLoadSampleData = {},
                    onRequestResetData = {},
                    onSignOut = {},
                )
            }
        }

        composeRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_lang_bengali").performClick()
        assertEquals(LanguageMode.BENGALI, selectedMode)

        composeRule.onNodeWithTag("settings_screen")
            .performScrollToNode(hasTestTag("settings_export_pdf_row"))
        composeRule.onNodeWithTag("settings_export_pdf_row").performClick()
        assertEquals(true, pdfExportClicked)
    }
}
