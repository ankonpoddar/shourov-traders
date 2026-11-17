package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.data.model.BankLoan
import com.example.data.model.BusinessAggregator
import com.example.data.model.BusinessExpense
import com.example.data.model.Customer
import com.example.data.model.LedgerTransaction
import com.example.data.preferences.LanguageMode
import com.example.data.preferences.formatBdt
import com.example.ui.theme.ShouravTradersTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class MainBusinessDashboardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = System.currentTimeMillis()
    private val tenDaysAgo = now - 10L * 24L * 60L * 60L * 1000L

    private val sampleCustomers = listOf(
        Customer(id = "c1", name = "Rahim Store", openingBalance = 10000.0),
        Customer(id = "c2", name = "Padma Traders", openingBalance = 0.0),
    )

    private val sampleTransactions = listOf(
        LedgerTransaction(
            id = "t1",
            customerId = "c1",
            customerName = "Rahim Store",
            type = LedgerTransaction.TYPE_SALE,
            riceVariety = "Miniket",
            unit = LedgerTransaction.UNIT_BAG,
            quantity = 10.0,
            costPricePerUnit = 3200.0,
            sellingPricePerUnit = 3600.0,
            totalAmount = 36000.0,
            paidAmount = 20000.0,
            dueAdded = 16000.0,
            grossProfit = 4000.0,
            transactionDateMillis = now,
        ),
        LedgerTransaction(
            id = "t2",
            customerId = "c1",
            customerName = "Rahim Store",
            type = LedgerTransaction.TYPE_PAYMENT,
            paidAmount = 5000.0,
            dueAdded = -5000.0,
            transactionDateMillis = now + 1000L,
        ),
        LedgerTransaction(
            id = "t3",
            customerId = "c2",
            customerName = "Padma Traders",
            type = LedgerTransaction.TYPE_SALE,
            riceVariety = "Nazirshail",
            unit = LedgerTransaction.UNIT_BAG,
            quantity = 5.0,
            costPricePerUnit = 3500.0,
            sellingPricePerUnit = 4000.0,
            totalAmount = 20000.0,
            paidAmount = 20000.0,
            dueAdded = 0.0,
            grossProfit = 2500.0,
            transactionDateMillis = tenDaysAgo,
        ),
    )

    private val sampleExpenses = listOf(
        BusinessExpense(
            id = "e1",
            frequency = BusinessExpense.FREQ_DAILY,
            category = "Transport / Van",
            amount = 1000.0,
            expenseDateMillis = now,
        ),
        BusinessExpense(
            id = "e2",
            frequency = BusinessExpense.FREQ_MONTHLY,
            category = "Staff Salary",
            amount = 2000.0,
            expenseDateMillis = now,
        ),
    )

    // Remaining principal = 120,000 * 12% = 14,400/yr => 1,200/mo
    private val sampleLoans = listOf(
        BankLoan(
            id = "l1",
            lenderName = "Islami Bank",
            principalAmount = 150000.0,
            annualInterestRate = 12.0,
            startDateMillis = now,
            totalPrincipalRepaid = 30000.0,
        )
    )

    @Test
    fun aggregator_computesTodayOverallAzSerialTimelineAndPeriods() {
        val result = BusinessAggregator.aggregate(
            customers = sampleCustomers,
            transactions = sampleTransactions,
            expenses = sampleExpenses,
            loans = sampleLoans,
            referenceTimeMillis = now,
        )

        // Overall checks
        assertEquals(56000.0, result.businessSummary.totalSales, 0.01)
        assertEquals(21000.0, result.businessSummary.totalReceivable, 0.01)
        assertEquals(6500.0, result.businessSummary.totalGrossProfit, 0.01)
        assertEquals(4200.0, result.businessSummary.totalExpenses, 0.01)
        assertEquals(2300.0, result.businessSummary.netProfit, 0.01)

        // Today checks (t1 sale = 36,000; dueAdded = 16,000; nagad = 20,000 + 5,000 = 25,000; expenses = 1,000 + 2,000 = 3,000)
        assertEquals(36000.0, result.businessSummary.today.todaySales, 0.01)
        assertEquals(1, result.businessSummary.today.todaySaleCount)
        assertEquals(16000.0, result.businessSummary.today.todayDueAdded, 0.01)
        assertEquals(25000.0, result.businessSummary.today.todayNagad, 0.01)
        assertEquals(3000.0, result.businessSummary.today.todayExpenses, 0.01)

        // A-Z Customer sorting: Padma Traders first, Rahim Store second
        assertEquals(listOf("Padma Traders", "Rahim Store"), result.customerSummaries.map { it.customer.name })

        // Rahim timeline (newest first): t2 payment (26,000 - 5,000 = 21,000), then t1 sale (10,000 + 16,000 = 26,000)
        val rahimTimeline = BusinessAggregator.buildCustomerLedgerTimeline(sampleCustomers[0], sampleTransactions)
        assertEquals(2, rahimTimeline.size)
        assertEquals("t2", rahimTimeline[0].transaction.id)
        assertEquals(26000.0, rahimTimeline[0].previousDue, 0.01)
        assertEquals(21000.0, rahimTimeline[0].runningDueAfter, 0.01)
        assertEquals("t1", rahimTimeline[1].transaction.id)
        assertEquals(10000.0, rahimTimeline[1].previousDue, 0.01)
        assertEquals(26000.0, rahimTimeline[1].runningDueAfter, 0.01)

        // Period analytics: 7d includes only t1 (36k), 30d includes t1 + t3 (56k)
        val p7 = BusinessAggregator.buildPeriodAnalytics(7, sampleTransactions, sampleExpenses, sampleLoans, now)
        val p30 = BusinessAggregator.buildPeriodAnalytics(30, sampleTransactions, sampleExpenses, sampleLoans, now)
        assertEquals(36000.0, p7.totalSales, 0.01)
        assertEquals(56000.0, p30.totalSales, 0.01)
    }

    @Test
    fun mainBusinessDashboard_displaysTodayKpisOverallSummaryAndQuickActions() {
        var newSaleClicked = false
        var addCustomerClicked = false
        var addExpenseClicked = false
        var scanKhataClicked = false

        composeRule.setContent {
            ShouravTradersTheme {
                MainBusinessDashboard(
                    customers = sampleCustomers,
                    transactions = sampleTransactions,
                    expenses = sampleExpenses,
                    loans = sampleLoans,
                    mode = LanguageMode.ENGLISH,
                    isBusy = false,
                    onQuickNewSale = { newSaleClicked = true },
                    onQuickAddCustomer = { addCustomerClicked = true },
                    onQuickAddExpense = { addExpenseClicked = true },
                    onQuickScanKhata = { scanKhataClicked = true },
                    onLoadSampleData = {},
                    onOpenProfileAnalytics = {},
                )
            }
        }

        val dashboardList = composeRule.onNodeWithTag("main_business_dashboard")

        // Verify 4 Today KPI cards
        dashboardList.performScrollToNode(hasTestTag("kpi_today_sales"))
        composeRule.onNodeWithTag("kpi_today_sales").assertIsDisplayed()
        composeRule.onNodeWithTag("kpi_today_due").assertIsDisplayed()
        composeRule.onNodeWithTag("kpi_today_nagad").assertIsDisplayed()
        composeRule.onNodeWithTag("kpi_today_expenses").assertIsDisplayed()

        composeRule.onNodeWithText(formatBdt(36000.0)).assertIsDisplayed()
        composeRule.onNodeWithText(formatBdt(16000.0)).assertIsDisplayed()
        composeRule.onNodeWithText(formatBdt(25000.0)).assertIsDisplayed()
        composeRule.onNodeWithText(formatBdt(3000.0)).assertIsDisplayed()

        // Verify Overall Summary Card
        dashboardList.performScrollToNode(hasTestTag("overall_summary_card"))
        composeRule.onNodeWithTag("overall_summary_card").assertIsDisplayed()
        composeRule.onNodeWithText(formatBdt(56000.0)).assertIsDisplayed()

        // Verify all 4 Quick Action buttons scroll into view inside LazyColumn and fire callbacks
        dashboardList.performScrollToNode(hasTestTag("quick_new_sale_btn"))
        composeRule.onNodeWithTag("quick_new_sale_btn").performClick()

        dashboardList.performScrollToNode(hasTestTag("quick_add_customer_btn"))
        composeRule.onNodeWithTag("quick_add_customer_btn").performClick()

        dashboardList.performScrollToNode(hasTestTag("quick_add_expense_btn"))
        composeRule.onNodeWithTag("quick_add_expense_btn").performClick()

        dashboardList.performScrollToNode(hasTestTag("quick_scan_khata_btn"))
        composeRule.onNodeWithTag("quick_scan_khata_btn").performClick()

        assertTrue(newSaleClicked)
        assertTrue(addCustomerClicked)
        assertTrue(addExpenseClicked)
        assertTrue(scanKhataClicked)
    }
}
