package com.example.data.report

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.example.data.model.BankLoan
import com.example.data.model.BusinessExpense
import com.example.data.model.Customer
import com.example.data.model.LedgerTransaction
import com.example.data.preferences.LanguageMode
import com.example.data.preferences.formatBdt
import com.example.ui.components.ExportMonthlyReportDialog
import com.example.ui.theme.ShouravTradersTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.OutputStream
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MonthlyPdfExportTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun millisFor(year: Int, monthZeroBased: Int, day: Int): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, monthZeroBased)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun buildReport_scopesMonthSalesExpensesAndRollsPriorCustomerDue() {
        val oct10 = millisFor(2026, Calendar.OCTOBER, 10)
        val nov05 = millisFor(2026, Calendar.NOVEMBER, 5)
        val nov12 = millisFor(2026, Calendar.NOVEMBER, 12)

        val customers = listOf(
            Customer(id = "c1", name = "Rahim Store", phone = "01711", openingBalance = 5000.0),
            Customer(id = "c2", name = "Padma Traders", phone = "01819", openingBalance = 0.0),
        )
        val transactions = listOf(
            LedgerTransaction(
                id = "t_oct",
                customerId = "c1",
                customerName = "Rahim Store",
                type = LedgerTransaction.TYPE_SALE,
                riceVariety = "Miniket",
                quantity = 5.0,
                totalAmount = 10000.0,
                paidAmount = 4000.0,
                dueAdded = 6000.0,
                grossProfit = 1000.0,
                transactionDateMillis = oct10,
            ),
            LedgerTransaction(
                id = "t_nov_sale",
                customerId = "c1",
                customerName = "Rahim Store",
                type = LedgerTransaction.TYPE_SALE,
                riceVariety = "Nazirshail",
                quantity = 10.0,
                totalAmount = 20000.0,
                paidAmount = 5000.0,
                dueAdded = 15000.0,
                grossProfit = 3000.0,
                transactionDateMillis = nov05,
            ),
            LedgerTransaction(
                id = "t_nov_pay",
                customerId = "c1",
                customerName = "Rahim Store",
                type = LedgerTransaction.TYPE_PAYMENT,
                paidAmount = 2000.0,
                dueAdded = -2000.0,
                transactionDateMillis = nov12,
            ),
        )
        val expenses = listOf(
            BusinessExpense(
                id = "e_oct",
                frequency = BusinessExpense.FREQ_DAILY,
                category = "Transport",
                amount = 500.0,
                expenseDateMillis = oct10,
            ),
            BusinessExpense(
                id = "e_nov_daily",
                frequency = BusinessExpense.FREQ_DAILY,
                category = "Transport",
                amount = 800.0,
                expenseDateMillis = nov05,
            ),
            BusinessExpense(
                id = "e_nov_monthly",
                frequency = BusinessExpense.FREQ_MONTHLY,
                category = "Staff Salary",
                amount = 1000.0,
                expenseDateMillis = nov12,
            ),
        )
        val loans = listOf(
            BankLoan(
                id = "l1",
                lenderName = "Islami Bank",
                principalAmount = 120000.0,
                annualInterestRate = 12.0,
                startDateMillis = oct10,
                totalPrincipalRepaid = 0.0,
            )
        )

        val novPeriod = MonthlyReportBuilder.createMonthOption(2026, Calendar.NOVEMBER)
        val report = MonthlyReportBuilder.buildReport(
            period = novPeriod,
            customers = customers,
            transactions = transactions,
            expenses = expenses,
            loans = loans,
            generatedAtMillis = nov12,
        )

        assertEquals(20000.0, report.monthlySalesTotal, 0.01)
        assertEquals(1, report.monthlySaleCount)
        assertEquals(7000.0, report.monthlyPaymentsCollected, 0.01)
        assertEquals(3000.0, report.monthlyGrossProfit, 0.01)
        assertEquals(3000.0, report.monthlyTotalExpenses, 0.01)
        assertEquals(0.0, report.monthlyNetProfit, 0.01)

        val rahimRow = report.customerLedgerRows.first { it.customerName == "Rahim Store" }
        assertEquals(11000.0, rahimRow.priorDue, 0.01)
        assertEquals(20000.0, rahimRow.monthSales, 0.01)
        assertEquals(7000.0, rahimRow.monthPaid, 0.01)
        assertEquals(24000.0, rahimRow.closingDue, 0.01)
        assertEquals(24000.0, report.totalReceivableDue, 0.01)
    }

    @Test
    fun renderToSink_paginatesMultiPageReportAcrossA4Pages() {
        val nov12 = millisFor(2026, Calendar.NOVEMBER, 12)
        val customers = (1..42).map { idx ->
            Customer(
                id = "c$idx",
                name = "Customer $idx (রহিম স্টোর $idx)",
                phone = "01711-0000$idx",
                openingBalance = 1500.0 * idx,
            )
        }
        val transactions = (1..42).map { idx ->
            LedgerTransaction(
                id = "t$idx",
                customerId = "c$idx",
                customerName = "Customer $idx",
                type = LedgerTransaction.TYPE_SALE,
                riceVariety = "Miniket",
                unit = LedgerTransaction.UNIT_BAG,
                quantity = 5.0,
                totalAmount = 18000.0,
                paidAmount = 10000.0,
                dueAdded = 8000.0,
                grossProfit = 1500.0,
                transactionDateMillis = nov12,
            )
        }
        val expenses = listOf(
            BusinessExpense(
                id = "e1",
                frequency = BusinessExpense.FREQ_MONTHLY,
                category = "Staff Salary",
                amount = 12000.0,
                expenseDateMillis = nov12,
            )
        )
        val loans = listOf(
            BankLoan(
                id = "l1",
                lenderName = "Islami Bank",
                principalAmount = 300000.0,
                annualInterestRate = 13.0,
                startDateMillis = nov12,
            )
        )

        val period = MonthlyReportBuilder.createMonthOption(2026, Calendar.NOVEMBER)
        val report = MonthlyReportBuilder.buildReport(
            period = period,
            customers = customers,
            transactions = transactions,
            expenses = expenses,
            loans = loans,
            generatedAtMillis = nov12,
        )

        val sink = object : MonthlyPdfExporter.PdfSink {
            val startedPages = mutableListOf<Triple<Int, Int, Int>>()
            var finishedCount = 0
            private var openBitmap: Bitmap? = null

            override fun startPage(width: Int, height: Int, pageNumber: Int): Canvas {
                startedPages.add(Triple(width, height, pageNumber))
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                openBitmap = bmp
                return Canvas(bmp)
            }

            override fun finishPage() {
                finishedCount++
                openBitmap?.recycle()
                openBitmap = null
            }

            override fun writeTo(outputStream: OutputStream) {}
            override fun close() {}
        }

        MonthlyPdfExporter.renderToSink(report, sink)

        assertTrue("Expected multi-page PDF (>= 3 pages), got ${sink.startedPages.size}", sink.startedPages.size >= 3)
        assertEquals(sink.startedPages.size, sink.finishedCount)
        sink.startedPages.forEachIndexed { idx, (w, h, pageNum) ->
            assertEquals(595, w)
            assertEquals(842, h)
            assertEquals(idx + 1, pageNum)
        }
        assertEquals("Shourav_Traders_Report_2026-11.pdf", MonthlyPdfExporter.suggestedFileName(period))
    }

    @Test
    fun exportDialog_displaysPreviewAndOfflineExportActions() {
        val now = System.currentTimeMillis()
        val customers = listOf(Customer(id = "c1", name = "Rahim Store", openingBalance = 5000.0))
        val transactions = listOf(
            LedgerTransaction(
                id = "t1",
                customerId = "c1",
                customerName = "Rahim Store",
                type = LedgerTransaction.TYPE_SALE,
                totalAmount = 15000.0,
                paidAmount = 10000.0,
                dueAdded = 5000.0,
                grossProfit = 2000.0,
                transactionDateMillis = now,
            )
        )

        composeRule.setContent {
            ShouravTradersTheme {
                ExportMonthlyReportDialog(
                    customers = customers,
                    transactions = transactions,
                    expenses = emptyList(),
                    loans = emptyList(),
                    mode = LanguageMode.ENGLISH,
                    onDismiss = {},
                    onExportFeedback = {},
                )
            }
        }

        composeRule.onNodeWithText("Export Monthly PDF Report").assertIsDisplayed()
        // Due = 5,000 opening + 15,000 sale - 10,000 paid = 10,000
        composeRule.onNodeWithText(formatBdt(10000.0)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("export_pdf_share_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("export_pdf_save_btn").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("export_pdf_print_btn").performScrollTo().assertIsDisplayed()
    }
}
