package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.BankLoan
import com.example.data.model.BusinessAggregator
import com.example.data.model.BusinessExpense
import com.example.data.model.Customer
import com.example.data.model.CustomerSummary
import com.example.data.model.LedgerTransaction
import com.example.data.model.ScannedLedgerRow
import com.example.data.preferences.LanguageMode
import com.example.data.preferences.formatBdt
import com.example.data.preferences.formatEditableNumber
import com.example.data.preferences.parseLocalizedDouble
import com.example.data.report.MonthlyPdfExporter
import com.example.data.report.MonthlyReportBuilder
import com.example.data.report.TransactionSlipData
import com.example.data.report.TransactionSlipPdfExporter
import com.example.ui.components.RecordPaymentDialog
import com.example.ui.screens.CustomerLedgerScreen
import com.example.ui.screens.DailySaleScreen
import com.example.ui.screens.ExpenseLoanScreen
import com.example.ui.screens.KhataScannerScreen
import com.example.ui.theme.ShouravTradersTheme
import com.example.ui.viewmodel.BusinessUiState
import com.example.ui.viewmodel.KhataScanUiState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ReleaseAuditVerificationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.NOVEMBER, 16, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun localizedNumericParsing_supportsBengaliDigitsCommasAndNoTruncation() {
        assertEquals(12345.5, parseLocalizedDouble("১২,৩৪৫.৫০") ?: -1.0, 0.0001)
        assertEquals(1500.75, parseLocalizedDouble("৳ 1,500.75") ?: -1.0, 0.0001)
        assertEquals(0.0, parseLocalizedDouble("০") ?: -1.0, 0.0001)
        assertNull(parseLocalizedDouble(""))
        assertNull(parseLocalizedDouble("abc"))
        assertNull(parseLocalizedDouble("NaN"))
        assertNull(parseLocalizedDouble("Infinity"))

        assertEquals("1250.5", formatEditableNumber(1250.50))
        assertEquals("1250.75", formatEditableNumber(1250.75))
        assertEquals("36000", formatEditableNumber(36000.0))
        assertEquals("0", formatEditableNumber(0.0))
    }

    @Test
    fun businessAggregator_handlesAdvanceCreditLoanPayoffAndAzOrdering() {
        val customers = listOf(
            Customer(id = "c2", name = "Zahir Rice", openingBalance = 1000.0),
            Customer(id = "c1", name = "Alim Store", openingBalance = 2000.0),
        )
        val transactions = listOf(
            LedgerTransaction(
                id = "t1",
                customerId = "c1",
                customerName = "Alim Store",
                type = LedgerTransaction.TYPE_PAYMENT,
                paidAmount = 2500.0,
                dueAdded = -2500.0,
                transactionDateMillis = now - 1000L,
            ),
            LedgerTransaction(
                id = "t_walkin",
                customerId = "",
                customerName = "Walk-in Cash Customer",
                type = LedgerTransaction.TYPE_SALE,
                totalAmount = 5000.0,
                paidAmount = 5000.0,
                dueAdded = 0.0,
                grossProfit = 600.0,
                transactionDateMillis = now,
            ),
        )
        val paidOffLoan = BankLoan(
            id = "l1",
            lenderName = "Agrani Bank",
            principalAmount = 100000.0,
            annualInterestRate = 13.0,
            totalPrincipalRepaid = 100000.0,
        )

        val result = BusinessAggregator.aggregate(
            customers = customers,
            transactions = transactions,
            expenses = emptyList(),
            loans = listOf(paidOffLoan),
            referenceTimeMillis = now,
        )

        // Sorted A-Z: Alim Store first, Zahir Rice second
        assertEquals(listOf("Alim Store", "Zahir Rice"), result.customerSummaries.map { it.customer.name })

        // Alim overpaid 2500 against 2000 -> currentDue = -500 (advance credit)
        val alim = result.customerSummaries.first()
        assertEquals(-500.0, alim.currentDue, 0.01)

        // Paid-off loan has 0 remaining principal and 0 monthly interest
        assertEquals(0.0, paidOffLoan.remainingPrincipal, 0.01)
        assertEquals(0.0, result.businessSummary.monthlyLoanInterest, 0.01)

        // Walk-in cash + joma collection both counted in todayNagad
        assertEquals(7500.0, result.businessSummary.today.todayNagad, 0.01)
        assertEquals(5000.0, result.businessSummary.today.todaySales, 0.01)
        assertEquals(600.0, result.businessSummary.netProfit, 0.01)
    }

    @Test
    fun dailySaleScreen_picksSortedFirstCustomerSupportsBengaliDigitsAndPreservesDecimals() {
        val customers = listOf(
            Customer(id = "c_z", name = "Zahir Agency", openingBalance = 1000.0),
            Customer(id = "c_a", name = "Alim Store", openingBalance = 500.0),
        )
        var savedCustomerId: String? = null
        var savedQty = 0.0
        var savedSell = 0.0
        var savedPaid = 0.0

        composeRule.setContent {
            ShouravTradersTheme {
                DailySaleScreen(
                    customers = customers,
                    preselectedCustomerId = null,
                    mode = LanguageMode.ENGLISH,
                    isBusy = false,
                    onSaveSale = { cid, _, _, _, qty, _, sell, paid, _ ->
                        savedCustomerId = cid
                        savedQty = qty
                        savedSell = sell
                        savedPaid = paid
                    },
                    onAddCustomerClick = {},
                    customerDueMap = mapOf("c_z" to 1000.0, "c_a" to 500.0),
                )
            }
        }

        // Switch to Khata Customer -> auto-selects A-Z first ("Alim Store", not "Zahir Agency")
        composeRule.onNodeWithText("Khata Customer").performClick()
        composeRule.onNodeWithText("Alim Store").assertIsDisplayed()

        // Enter Bengali digits with decimals: qty = ২ (2), sell price = ১০০.২৫ (100.25) -> bill 200.5
        composeRule.onNodeWithTag("sale_qty_input").performTextClearance()
        composeRule.onNodeWithTag("sale_qty_input").performTextInput("২")
        composeRule.onNodeWithTag("sale_sell_price_input").performTextClearance()
        composeRule.onNodeWithTag("sale_sell_price_input").performTextInput("১০০.২৫")

        // Tap Full Paid -> must preserve 200.5 instead of truncating to 200
        composeRule.onNodeWithText("Full Paid").performClick()
        composeRule.onNodeWithTag("daily_sale_list").performScrollToNode(hasTestTag("save_sale_button"))
        composeRule.onNodeWithTag("save_sale_button").performClick()

        assertEquals("c_a", savedCustomerId)
        assertEquals(2.0, savedQty, 0.01)
        assertEquals(100.25, savedSell, 0.01)
        assertEquals(200.5, savedPaid, 0.01)
    }

    @Test
    fun recordPaymentDialog_prefillsFractionalDueAndParsesBengaliAmount() {
        var recordedAmount: Double? = null

        composeRule.setContent {
            ShouravTradersTheme {
                var showDialog by remember { mutableStateOf(false) }
                androidx.compose.material3.Surface {
                    androidx.compose.material3.Button(onClick = { showDialog = true }) {
                        androidx.compose.material3.Text("Open Payment Dialog")
                    }
                    if (showDialog) {
                        RecordPaymentDialog(
                            customerName = "Alim Store",
                            currentDue = 1500.75,
                            mode = LanguageMode.ENGLISH,
                            onDismiss = { showDialog = false },
                            onConfirm = { amt, _ ->
                                recordedAmount = amt
                                showDialog = false
                            },
                        )
                    }
                }
            }
        }

        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Open Payment Dialog").performClick()
        composeRule.mainClock.advanceTimeBy(300L)
        composeRule.onNodeWithText("1500.75").assertIsDisplayed()
        composeRule.onNodeWithTag("record_payment_amount_input")
            .performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("৫০০.২৫")) }
        composeRule.mainClock.advanceTimeBy(100L)
        composeRule.onNodeWithTag("confirm_payment_button").performClick()
        composeRule.mainClock.advanceTimeBy(100L)

        assertNotNull(recordedAmount)
        assertEquals(500.25, recordedAmount ?: 0.0, 0.001)
    }

    @Test
    fun expenseLoanScreen_acceptsBengaliNumbersAndSwitchesTabsCleanly() {
        val ready = BusinessUiState.Ready(
            customers = emptyList(),
            customerSummaries = emptyList(),
            transactions = emptyList(),
            expenses = emptyList(),
            loans = listOf(
                BankLoan(
                    id = "loan1",
                    lenderName = "Sonali Bank",
                    principalAmount = 100000.0,
                    annualInterestRate = 13.0,
                    totalPrincipalRepaid = 90000.0,
                )
            ),
            summary = BusinessAggregator.aggregate(emptyList(), emptyList(), emptyList(), emptyList(), now).businessSummary,
        )
        var addedExpenseAmount = 0.0
        var addedLoanPrincipal = 0.0
        var addedLoanRate = 0.0

        composeRule.setContent {
            ShouravTradersTheme {
                ExpenseLoanScreen(
                    state = ready,
                    mode = LanguageMode.ENGLISH,
                    isBusy = false,
                    onAddExpense = { _, _, amt, _ -> addedExpenseAmount = amt },
                    onDeleteExpense = {},
                    onAddLoan = { _, p, r, _ ->
                        addedLoanPrincipal = p
                        addedLoanRate = r
                    },
                    onRecordInstallment = { _, _ -> },
                    onDeleteLoan = {},
                )
            }
        }

        composeRule.onNodeWithTag("expense_amount_input").performTextInput("১,২৫০")
        composeRule.onNodeWithTag("save_expense_button").performClick()
        assertEquals(1250.0, addedExpenseAmount, 0.01)

        // Switch to Bank Loan tab
        composeRule.onNodeWithText("Bank Loan").performClick()
        composeRule.onNodeWithTag("loan_bank_input").performTextInput("BRAC Bank")
        composeRule.onNodeWithTag("loan_principal_input").performTextInput("২০০০০০")
        composeRule.onNodeWithTag("save_loan_button").performClick()

        assertEquals(200000.0, addedLoanPrincipal, 0.01)
        assertEquals(13.0, addedLoanRate, 0.01)
    }

    @Test
    fun khataScannerScreen_supportsErrorAndReviewStates() {
        var manualStarted = false
        var importedClicked = false
        val rows = listOf(
            ScannedLedgerRow(localId = "r1", customerName = "Rahim Store", dateText = "16 Nov", totalAmount = "5000", paidAmount = "2000", dueAmount = "3000"),
        )

        composeRule.setContent {
            ShouravTradersTheme {
                KhataScannerScreen(
                    scanState = KhataScanUiState.Review(imageUri = null, rows = rows),
                    existingCustomers = emptyList(),
                    mode = LanguageMode.ENGLISH,
                    isOnline = false,
                    onAnalyzeImage = { _, _ -> },
                    onStartManualTable = { manualStarted = true },
                    onUpdateRow = {},
                    onAddRow = {},
                    onRemoveRow = {},
                    onImportAll = { importedClicked = true },
                    onReset = {},
                    onGoToKhata = {},
                )
            }
        }

        composeRule.onNodeWithText("Editable Review Table (1)").assertIsDisplayed()
        composeRule.onNodeWithTag("khata_scanner_list").performScrollToNode(hasTestTag("import_all_khata_button"))
        composeRule.onNodeWithTag("import_all_khata_button").performClick()
        assertTrue(importedClicked)
    }

    @Test
    fun customerLedgerScreen_consumesInitialShowTransactionsCallbackOnce() {
        var consumed = false
        val ready = BusinessUiState.Ready(
            customers = listOf(Customer(id = "c1", name = "Alim Store")),
            customerSummaries = listOf(
                CustomerSummary(
                    customer = Customer(id = "c1", name = "Alim Store"),
                    totalSold = 0.0,
                    totalPaid = 0.0,
                    currentDue = 0.0,
                    transactionCount = 0,
                )
            ),
            transactions = emptyList(),
            expenses = emptyList(),
            loans = emptyList(),
            summary = BusinessAggregator.aggregate(emptyList(), emptyList(), emptyList(), emptyList(), now).businessSummary,
        )

        composeRule.setContent {
            ShouravTradersTheme {
                CustomerLedgerScreen(
                    state = ready,
                    mode = LanguageMode.ENGLISH,
                    onOpenCustomer = {},
                    onAddCustomerClick = {},
                    initialShowTransactions = true,
                    onConsumeInitialTab = { consumed = true },
                )
            }
        }

        composeRule.waitForIdle()
        assertTrue("Expected onConsumeInitialTab to be invoked", consumed)
    }

    @Test
    fun pdfExporters_generateValidNonEmptyPdfFilesForLongCustomerNamesAndNotes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val longCustomer = Customer(
            id = "c_long",
            name = "Haji Mohammad Abdul Rahman & Sons Wholesale Rice Traders",
            phone = "01711-000000",
            shopAddress = "Badamtoli Wholesale Market, Building 4, Dhaka-1100",
            openingBalance = 12000.0,
        )
        val longTx = LedgerTransaction(
            id = "tx_long",
            customerId = "c_long",
            customerName = longCustomer.name,
            type = LedgerTransaction.TYPE_SALE,
            riceVariety = "Premium Dinajpur Miniket 50kg",
            unit = LedgerTransaction.UNIT_BAG,
            quantity = 25.0,
            costPricePerUnit = 3300.0,
            sellingPricePerUnit = 3600.0,
            totalAmount = 90000.0,
            paidAmount = 45000.0,
            dueAdded = 45000.0,
            grossProfit = 7500.0,
            transactionDateMillis = now,
            notes = "Delivered by truck with 25 extra bags reserved for next week settlement at Badamtoli",
        )
        val timeline = BusinessAggregator.buildCustomerLedgerTimeline(longCustomer, listOf(longTx))
        val slipFile = TransactionSlipPdfExporter.exportSlipToCacheFile(
            context,
            TransactionSlipData(customer = longCustomer, entry = timeline.first()),
        )
        assertTrue(slipFile.exists())
        assertTrue(slipFile.length() > 500L)
        assertTrue(slipFile.readText(Charsets.ISO_8859_1).startsWith("%PDF-"))

        val period = MonthlyReportBuilder.createMonthOption(2026, Calendar.NOVEMBER)
        val report = MonthlyReportBuilder.buildReport(
            period = period,
            customers = listOf(longCustomer),
            transactions = listOf(longTx),
            expenses = listOf(
                BusinessExpense(
                    id = "e1",
                    frequency = BusinessExpense.FREQ_DAILY,
                    category = "Transport",
                    amount = 1200.0,
                    expenseDateMillis = now,
                )
            ),
            loans = emptyList(),
            generatedAtMillis = now,
        )
        val monthlyFile = MonthlyPdfExporter.exportToCacheFile(context, report)
        assertTrue(monthlyFile.exists())
        assertTrue(monthlyFile.length() > 500L)
        assertTrue(monthlyFile.readText(Charsets.ISO_8859_1).startsWith("%PDF-"))
    }
}
