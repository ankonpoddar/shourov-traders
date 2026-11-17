package com.example.data.report

import com.example.data.model.BankLoan
import com.example.data.model.BusinessExpense
import com.example.data.model.Customer
import com.example.data.model.LedgerTransaction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ReportMonthOption(
    val year: Int,
    val month: Int, // 0-based Calendar.MONTH
    val displayLabel: String,
    val fileSlug: String,
) {
    fun monthStartMillis(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun monthEndMillis(): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = monthStartMillis()
            add(Calendar.MONTH, 1)
        }
        return cal.timeInMillis - 1L
    }
}

data class CustomerMonthlyLedgerRow(
    val customerName: String,
    val phone: String,
    val shopAddress: String,
    val priorDue: Double,
    val monthSales: Double,
    val monthPaid: Double,
    val closingDue: Double,
)

data class MonthlyReportData(
    val period: ReportMonthOption,
    val generatedAtMillis: Long,
    val monthlySalesTotal: Double,
    val monthlySaleCount: Int,
    val monthlyPaymentsCollected: Double,
    val monthlyGrossProfit: Double,
    val monthlyDailyExpenses: Double,
    val monthlyFixedExpenses: Double,
    val monthlyLoanInterest: Double,
    val monthlyTotalExpenses: Double,
    val monthlyNetProfit: Double,
    val totalReceivableDue: Double,
    val activeDueCustomersCount: Int,
    val customerLedgerRows: List<CustomerMonthlyLedgerRow>,
    val transactionsInMonth: List<LedgerTransaction>,
    val expensesInMonth: List<BusinessExpense>,
    val activeLoans: List<BankLoan>,
)

object MonthlyReportBuilder {

    fun availableMonths(
        transactions: List<LedgerTransaction>,
        expenses: List<BusinessExpense>,
        referenceMillis: Long = System.currentTimeMillis(),
    ): List<ReportMonthOption> {
        val pairs = linkedSetOf<Pair<Int, Int>>()
        val cal = Calendar.getInstance().apply { timeInMillis = referenceMillis }
        repeat(6) {
            pairs.add(cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH))
            cal.add(Calendar.MONTH, -1)
        }

        transactions.forEach { tx ->
            if (tx.transactionDateMillis > 0L) {
                cal.timeInMillis = tx.transactionDateMillis
                pairs.add(cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH))
            }
        }
        expenses.forEach { exp ->
            if (exp.expenseDateMillis > 0L) {
                cal.timeInMillis = exp.expenseDateMillis
                pairs.add(cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH))
            }
        }

        return pairs
            .sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenByDescending { it.second })
            .map { (year, month) -> createMonthOption(year, month) }
    }

    fun createMonthOption(year: Int, month: Int): ReportMonthOption {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val label = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(cal.time)
        val slug = String.format(Locale.ENGLISH, "%04d-%02d", year, month + 1)
        return ReportMonthOption(
            year = year,
            month = month,
            displayLabel = label,
            fileSlug = slug,
        )
    }

    fun buildReport(
        period: ReportMonthOption,
        customers: List<Customer>,
        transactions: List<LedgerTransaction>,
        expenses: List<BusinessExpense>,
        loans: List<BankLoan>,
        generatedAtMillis: Long = System.currentTimeMillis(),
    ): MonthlyReportData {
        val startMillis = period.monthStartMillis()
        val endMillis = period.monthEndMillis()

        val transactionsInMonth = transactions
            .filter { it.transactionDateMillis in startMillis..endMillis }
            .sortedByDescending { it.transactionDateMillis }

        val salesInMonth = transactionsInMonth.filter { it.type == LedgerTransaction.TYPE_SALE }
        val monthlySalesTotal = salesInMonth.sumOf { it.totalAmount }
        val monthlyGrossProfit = salesInMonth.sumOf { it.grossProfit }
        val monthlyPaymentsCollected = transactionsInMonth.sumOf { it.paidAmount }

        val expensesInMonth = expenses
            .filter { it.expenseDateMillis in startMillis..endMillis }
            .sortedByDescending { it.expenseDateMillis }

        val monthlyDailyExpenses = expensesInMonth
            .filter { it.frequency == BusinessExpense.FREQ_DAILY }
            .sumOf { it.amount }
        val monthlyFixedExpenses = expensesInMonth
            .filter { it.frequency == BusinessExpense.FREQ_MONTHLY }
            .sumOf { it.amount }

        val activeLoans = loans.filter { loan ->
            loan.remainingPrincipal > 0.5 &&
                (loan.startDateMillis <= 0L || loan.startDateMillis <= endMillis)
        }
        val monthlyLoanInterest = activeLoans.sumOf { it.monthlyInterest }

        val monthlyTotalExpenses = monthlyDailyExpenses + monthlyFixedExpenses + monthlyLoanInterest
        val monthlyNetProfit = monthlyGrossProfit - monthlyTotalExpenses

        val txUpToEndByCustomer = transactions
            .filter { it.customerId.isNotBlank() && it.transactionDateMillis <= endMillis }
            .groupBy { it.customerId }

        val customerLedgerRows = customers.map { customer ->
            val customerTxs = txUpToEndByCustomer[customer.id].orEmpty()
            val priorTxs = customerTxs.filter { it.transactionDateMillis < startMillis }
            val monthTxs = customerTxs.filter { it.transactionDateMillis in startMillis..endMillis }

            val priorSales = priorTxs
                .filter { it.type == LedgerTransaction.TYPE_SALE }
                .sumOf { it.totalAmount }
            val priorPaid = priorTxs.sumOf { it.paidAmount }
            val priorDue = customer.openingBalance + priorSales - priorPaid

            val monthSales = monthTxs
                .filter { it.type == LedgerTransaction.TYPE_SALE }
                .sumOf { it.totalAmount }
            val monthPaid = monthTxs.sumOf { it.paidAmount }
            val closingDue = priorDue + monthSales - monthPaid

            CustomerMonthlyLedgerRow(
                customerName = customer.name,
                phone = customer.phone,
                shopAddress = customer.shopAddress,
                priorDue = priorDue,
                monthSales = monthSales,
                monthPaid = monthPaid,
                closingDue = closingDue,
            )
        }.sortedByDescending { it.closingDue }

        val totalReceivableDue = customerLedgerRows.sumOf { it.closingDue.coerceAtLeast(0.0) }
        val activeDueCustomersCount = customerLedgerRows.count { it.closingDue > 0.5 }

        return MonthlyReportData(
            period = period,
            generatedAtMillis = generatedAtMillis,
            monthlySalesTotal = monthlySalesTotal,
            monthlySaleCount = salesInMonth.size,
            monthlyPaymentsCollected = monthlyPaymentsCollected,
            monthlyGrossProfit = monthlyGrossProfit,
            monthlyDailyExpenses = monthlyDailyExpenses,
            monthlyFixedExpenses = monthlyFixedExpenses,
            monthlyLoanInterest = monthlyLoanInterest,
            monthlyTotalExpenses = monthlyTotalExpenses,
            monthlyNetProfit = monthlyNetProfit,
            totalReceivableDue = totalReceivableDue,
            activeDueCustomersCount = activeDueCustomersCount,
            customerLedgerRows = customerLedgerRows,
            transactionsInMonth = transactionsInMonth,
            expensesInMonth = expensesInMonth,
            activeLoans = activeLoans,
        )
    }

    fun formatReportTimestamp(millis: Long): String {
        return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date(millis))
    }
}
