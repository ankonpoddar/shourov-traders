package com.example.data.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class AggregatedLedger(
    val customerSummaries: List<CustomerSummary>,
    val businessSummary: BusinessSummary,
)

object BusinessAggregator {

    fun aggregate(
        customers: List<Customer>,
        transactions: List<LedgerTransaction>,
        expenses: List<BusinessExpense>,
        loans: List<BankLoan>,
        referenceTimeMillis: Long = System.currentTimeMillis(),
    ): AggregatedLedger {
        val customerSummaries = buildCustomerSummaries(customers, transactions)
        val businessSummary = buildBusinessSummary(
            customerSummaries = customerSummaries,
            transactions = transactions,
            expenses = expenses,
            loans = loans,
            referenceTimeMillis = referenceTimeMillis,
        )
        return AggregatedLedger(
            customerSummaries = customerSummaries,
            businessSummary = businessSummary,
        )
    }

    /**
     * Builds per-customer credit balances sorted alphabetically A-Z by customer name.
     */
    fun buildCustomerSummaries(
        customers: List<Customer>,
        transactions: List<LedgerTransaction>,
    ): List<CustomerSummary> {
        val txByCustomer = transactions
            .filter { it.customerId.isNotBlank() }
            .groupBy { it.customerId }
        return customers.map { customer ->
            val txs = txByCustomer[customer.id].orEmpty()
            val salesTotal = txs
                .filter { it.type == LedgerTransaction.TYPE_SALE }
                .sumOf { it.totalAmount }
            val totalSold = customer.openingBalance + salesTotal
            val totalPaid = txs.sumOf { it.paidAmount }
            val currentDue = totalSold - totalPaid
            CustomerSummary(
                customer = customer,
                totalSold = totalSold,
                totalPaid = totalPaid,
                currentDue = currentDue,
                transactionCount = txs.size,
            )
        }.sortedBy { it.customer.name.trim().lowercase(Locale.ENGLISH) }
    }

    /**
     * Computes chronological running Khata balance (Previous Due -> Entry Delta -> Remaining Total Due)
     * starting from [customer.openingBalance], returned newest-first for ledger display.
     */
    fun buildCustomerLedgerTimeline(
        customer: Customer,
        transactions: List<LedgerTransaction>,
    ): List<CustomerTransactionEntry> {
        val ascending = transactions
            .filter { it.customerId == customer.id }
            .sortedWith(
                compareBy<LedgerTransaction> { it.transactionDateMillis }
                    .thenBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
                    .thenBy { it.id }
            )

        var runningDue = customer.openingBalance
        val entries = ascending.map { tx ->
            val previousDue = runningDue
            val delta = if (tx.type == LedgerTransaction.TYPE_PAYMENT) {
                -tx.paidAmount
            } else {
                tx.totalAmount - tx.paidAmount
            }
            val afterDue = previousDue + delta
            runningDue = afterDue
            CustomerTransactionEntry(
                transaction = tx,
                previousDue = previousDue,
                deltaDue = delta,
                runningDueAfter = afterDue,
            )
        }
        return entries.asReversed()
    }

    fun buildBusinessSummary(
        customerSummaries: List<CustomerSummary>,
        transactions: List<LedgerTransaction>,
        expenses: List<BusinessExpense>,
        loans: List<BankLoan>,
        referenceTimeMillis: Long = System.currentTimeMillis(),
    ): BusinessSummary {
        val sales = transactions.filter { it.type == LedgerTransaction.TYPE_SALE }
        val totalSales = sales.sumOf { it.totalAmount }
        val totalGrossProfit = sales.sumOf { it.grossProfit }
        val totalReceivable = customerSummaries.sumOf { it.currentDue.coerceAtLeast(0.0) }

        val dailyExpensesTotal = expenses
            .filter { it.frequency == BusinessExpense.FREQ_DAILY }
            .sumOf { it.amount }
        val monthlyExpensesTotal = expenses
            .filter { it.frequency == BusinessExpense.FREQ_MONTHLY }
            .sumOf { it.amount }
        val monthlyLoanInterest = loans.sumOf { it.monthlyInterest }

        val totalExpenses = dailyExpensesTotal + monthlyExpensesTotal + monthlyLoanInterest
        val netProfit = totalGrossProfit - totalExpenses

        val todaySummary = buildTodaySummary(
            transactions = transactions,
            expenses = expenses,
            referenceTimeMillis = referenceTimeMillis,
        )

        val dailyTrend = buildDailyTrend(
            sales = sales,
            expenses = expenses,
            monthlyBurden = monthlyExpensesTotal + monthlyLoanInterest,
            monthlyLoanInterest = monthlyLoanInterest,
            referenceTimeMillis = referenceTimeMillis,
        )
        val monthlyTrend = buildMonthlyTrend(
            sales = sales,
            expenses = expenses,
            monthlyLoanInterest = monthlyLoanInterest,
            referenceTimeMillis = referenceTimeMillis,
        )

        return BusinessSummary(
            totalSales = totalSales,
            totalReceivable = totalReceivable,
            totalGrossProfit = totalGrossProfit,
            dailyExpensesTotal = dailyExpensesTotal,
            monthlyExpensesTotal = monthlyExpensesTotal,
            monthlyLoanInterest = monthlyLoanInterest,
            totalExpenses = totalExpenses,
            netProfit = netProfit,
            today = todaySummary,
            dailyTrend = dailyTrend,
            monthlyTrend = monthlyTrend,
        )
    }

    fun buildTodaySummary(
        transactions: List<LedgerTransaction>,
        expenses: List<BusinessExpense>,
        referenceTimeMillis: Long = System.currentTimeMillis(),
    ): TodaySummary {
        val (todayStart, todayEnd) = dayRange(referenceTimeMillis, daysBack = 0)
        val todayTxs = transactions.filter { it.transactionDateMillis in todayStart..todayEnd }
        val todaySalesList = todayTxs.filter { it.type == LedgerTransaction.TYPE_SALE }
        val todaySales = todaySalesList.sumOf { it.totalAmount }
        val todayDueAdded = todaySalesList.sumOf { (it.totalAmount - it.paidAmount).coerceAtLeast(0.0) }
        val todayNagad = todayTxs.sumOf { it.paidAmount }
        val todayExpenses = expenses
            .filter { it.expenseDateMillis in todayStart..todayEnd }
            .sumOf { it.amount }

        return TodaySummary(
            todaySales = todaySales,
            todaySaleCount = todaySalesList.size,
            todayDueAdded = todayDueAdded,
            todayNagad = todayNagad,
            todayExpenses = todayExpenses,
        )
    }

    fun buildPeriodAnalytics(
        daysCount: Int,
        transactions: List<LedgerTransaction>,
        expenses: List<BusinessExpense>,
        loans: List<BankLoan>,
        referenceTimeMillis: Long = System.currentTimeMillis(),
    ): PeriodAnalyticsSummary {
        val safeDays = daysCount.coerceAtLeast(1)
        val (windowStart, _) = dayRange(referenceTimeMillis, daysBack = safeDays - 1)
        val (_, windowEnd) = dayRange(referenceTimeMillis, daysBack = 0)

        val windowTxs = transactions.filter { it.transactionDateMillis in windowStart..windowEnd }
        val windowSalesList = windowTxs.filter { it.type == LedgerTransaction.TYPE_SALE }

        val totalSales = windowSalesList.sumOf { it.totalAmount }
        val totalGrossProfit = windowSalesList.sumOf { it.grossProfit }
        val totalDueAdded = windowSalesList.sumOf { (it.totalAmount - it.paidAmount).coerceAtLeast(0.0) }
        val totalNagad = windowTxs.sumOf { it.paidAmount }

        val dailyExpenses = expenses
            .filter { it.frequency == BusinessExpense.FREQ_DAILY && it.expenseDateMillis in windowStart..windowEnd }
            .sumOf { it.amount }

        val monthlyFixed = expenses
            .filter { it.frequency == BusinessExpense.FREQ_MONTHLY }
            .sumOf { it.amount }
        val monthlyInterest = loans.sumOf { it.monthlyInterest }
        val proratedFixedAndInterest = (monthlyFixed + monthlyInterest) * (safeDays / 30.0)
        val totalExpenses = dailyExpenses + proratedFixedAndInterest
        val netProfit = totalGrossProfit - totalExpenses

        val trendPoints = if (safeDays <= 7) {
            buildDailyTrend(
                sales = windowSalesList,
                expenses = expenses,
                monthlyBurden = monthlyFixed + monthlyInterest,
                monthlyLoanInterest = monthlyInterest,
                referenceTimeMillis = referenceTimeMillis,
            )
        } else {
            buildThirtyDayBucketTrend(
                sales = windowSalesList,
                expenses = expenses,
                monthlyBurden = monthlyFixed + monthlyInterest,
                monthlyLoanInterest = monthlyInterest,
                referenceTimeMillis = referenceTimeMillis,
            )
        }

        return PeriodAnalyticsSummary(
            daysCount = safeDays,
            totalSales = totalSales,
            saleCount = windowSalesList.size,
            totalNagad = totalNagad,
            totalDueAdded = totalDueAdded,
            totalGrossProfit = totalGrossProfit,
            dailyExpenses = dailyExpenses,
            proratedFixedAndInterest = proratedFixedAndInterest,
            totalExpenses = totalExpenses,
            netProfit = netProfit,
            trendPoints = trendPoints,
        )
    }

    private fun dayRange(referenceTimeMillis: Long, daysBack: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = referenceTimeMillis
            add(Calendar.DAY_OF_YEAR, -daysBack)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return start to cal.timeInMillis
    }

    private fun buildDailyTrend(
        sales: List<LedgerTransaction>,
        expenses: List<BusinessExpense>,
        monthlyBurden: Double,
        monthlyLoanInterest: Double,
        referenceTimeMillis: Long,
    ): List<TrendPoint> {
        val dayFmt = SimpleDateFormat("dd MMM", Locale.ENGLISH)
        val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val salesByDay = sales.groupBy { keyFmt.format(Date(it.transactionDateMillis)) }
        val dailyExpByDay = expenses
            .filter { it.frequency == BusinessExpense.FREQ_DAILY }
            .groupBy { keyFmt.format(Date(it.expenseDateMillis)) }
        val dailyOverheadSlice = (monthlyBurden - monthlyLoanInterest).coerceAtLeast(0.0) / 30.0

        val cal = Calendar.getInstance().apply { timeInMillis = referenceTimeMillis }
        cal.add(Calendar.DAY_OF_YEAR, -6)
        return (0 until 7).map {
            val d = cal.time
            val key = keyFmt.format(d)
            val label = dayFmt.format(d)
            val daySales = salesByDay[key]?.sumOf { it.totalAmount } ?: 0.0
            val dayGross = salesByDay[key]?.sumOf { it.grossProfit } ?: 0.0
            val dayExp = dailyExpByDay[key]?.sumOf { it.amount } ?: 0.0
            val dayNet = if (daySales > 0.0 || dayExp > 0.0) dayGross - dayExp - dailyOverheadSlice else 0.0
            cal.add(Calendar.DAY_OF_YEAR, 1)
            TrendPoint(label = label, sales = daySales, netProfit = dayNet)
        }
    }

    private fun buildThirtyDayBucketTrend(
        sales: List<LedgerTransaction>,
        expenses: List<BusinessExpense>,
        monthlyBurden: Double,
        monthlyLoanInterest: Double,
        referenceTimeMillis: Long,
    ): List<TrendPoint> {
        val fmt = SimpleDateFormat("dd/MM", Locale.ENGLISH)
        val dailyExpenses = expenses.filter { it.frequency == BusinessExpense.FREQ_DAILY }
        val bucketOverhead = ((monthlyBurden - monthlyLoanInterest).coerceAtLeast(0.0) / 30.0) * 5.0

        return (0 until 6).map { bucketIdx ->
            val startDaysBack = 29 - (bucketIdx * 5)
            val endDaysBack = (startDaysBack - 4).coerceAtLeast(0)
            val (startMs, _) = dayRange(referenceTimeMillis, startDaysBack)
            val (_, endMs) = dayRange(referenceTimeMillis, endDaysBack)

            val bucketSalesList = sales.filter { it.transactionDateMillis in startMs..endMs }
            val bSales = bucketSalesList.sumOf { it.totalAmount }
            val bGross = bucketSalesList.sumOf { it.grossProfit }
            val bExp = dailyExpenses.filter { it.expenseDateMillis in startMs..endMs }.sumOf { it.amount }
            val bNet = if (bSales > 0.0 || bExp > 0.0) bGross - bExp - bucketOverhead else 0.0

            val label = fmt.format(Date(endMs))
            TrendPoint(label = label, sales = bSales, netProfit = bNet)
        }
    }

    private fun buildMonthlyTrend(
        sales: List<LedgerTransaction>,
        expenses: List<BusinessExpense>,
        monthlyLoanInterest: Double,
        referenceTimeMillis: Long,
    ): List<TrendPoint> {
        val monthLabelFmt = SimpleDateFormat("MMM", Locale.ENGLISH)
        val monthKeyFmt = SimpleDateFormat("yyyy-MM", Locale.ENGLISH)
        val salesByMonth = sales.groupBy { monthKeyFmt.format(Date(it.transactionDateMillis)) }
        val expByMonth = expenses.groupBy { monthKeyFmt.format(Date(it.expenseDateMillis)) }

        val cal = Calendar.getInstance().apply { timeInMillis = referenceTimeMillis }
        cal.add(Calendar.MONTH, -3)
        return (0 until 4).map {
            val d = cal.time
            val key = monthKeyFmt.format(d)
            val label = monthLabelFmt.format(d)
            val mSales = salesByMonth[key]?.sumOf { it.totalAmount } ?: 0.0
            val mGross = salesByMonth[key]?.sumOf { it.grossProfit } ?: 0.0
            val mExp = expByMonth[key]?.sumOf { it.amount } ?: 0.0
            val mNet = if (mSales > 0.0 || mExp > 0.0) mGross - mExp - monthlyLoanInterest else 0.0
            cal.add(Calendar.MONTH, 1)
            TrendPoint(label = label, sales = mSales, netProfit = mNet)
        }
    }
}
