package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Customer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val phone: String = "",
    val shopAddress: String = "",
    val openingBalance: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class LedgerTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val customerId: String = "", // "" for Walk-in Cash Customer
    val customerName: String = "",
    val type: String = TYPE_SALE, // "SALE" or "PAYMENT"
    val riceVariety: String = "",
    val unit: String = UNIT_BAG, // "BAG" or "KG"
    val quantity: Double = 0.0,
    val costPricePerUnit: Double = 0.0,
    val sellingPricePerUnit: Double = 0.0,
    val totalAmount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val dueAdded: Double = 0.0,
    val grossProfit: Double = 0.0,
    val transactionDateMillis: Long = 0L,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    companion object {
        const val TYPE_SALE = "SALE"
        const val TYPE_PAYMENT = "PAYMENT"
        const val UNIT_BAG = "BAG"
        const val UNIT_KG = "KG"
    }
}

@IgnoreExtraProperties
data class BusinessExpense(
    @DocumentId val id: String = "",
    val userId: String = "",
    val frequency: String = FREQ_DAILY, // "DAILY" or "MONTHLY"
    val category: String = "",
    val amount: Double = 0.0,
    val notes: String = "",
    val expenseDateMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    companion object {
        const val FREQ_DAILY = "DAILY"
        const val FREQ_MONTHLY = "MONTHLY"
    }
}

@IgnoreExtraProperties
data class BankLoan(
    @DocumentId val id: String = "",
    val userId: String = "",
    val lenderName: String = "",
    val principalAmount: Double = 0.0,
    val annualInterestRate: Double = 13.0,
    val startDateMillis: Long = 0L,
    val paidInstallmentsCount: Long = 0L,
    val totalPrincipalRepaid: Double = 0.0,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val remainingPrincipal: Double
        get() = (principalAmount - totalPrincipalRepaid).coerceAtLeast(0.0)

    val annualInterest: Double
        get() = remainingPrincipal * (annualInterestRate / 100.0)

    val monthlyInterest: Double
        get() = annualInterest / 12.0
}

data class CustomerSummary(
    val customer: Customer,
    val totalSold: Double,
    val totalPaid: Double,
    val currentDue: Double,
    val transactionCount: Int,
)

data class CustomerTransactionEntry(
    val transaction: LedgerTransaction,
    val previousDue: Double,
    val deltaDue: Double,
    val runningDueAfter: Double,
)

data class TodaySummary(
    val todaySales: Double = 0.0,
    val todaySaleCount: Int = 0,
    val todayDueAdded: Double = 0.0,
    val todayNagad: Double = 0.0,
    val todayExpenses: Double = 0.0,
)

data class PeriodAnalyticsSummary(
    val daysCount: Int,
    val totalSales: Double = 0.0,
    val saleCount: Int = 0,
    val totalNagad: Double = 0.0,
    val totalDueAdded: Double = 0.0,
    val totalGrossProfit: Double = 0.0,
    val dailyExpenses: Double = 0.0,
    val proratedFixedAndInterest: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val netProfit: Double = 0.0,
    val trendPoints: List<TrendPoint> = emptyList(),
)

data class TrendPoint(
    val label: String,
    val sales: Double,
    val netProfit: Double,
)

data class BusinessSummary(
    val totalSales: Double = 0.0,
    val totalReceivable: Double = 0.0,
    val totalGrossProfit: Double = 0.0,
    val dailyExpensesTotal: Double = 0.0,
    val monthlyExpensesTotal: Double = 0.0,
    val monthlyLoanInterest: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val netProfit: Double = 0.0,
    val today: TodaySummary = TodaySummary(),
    val dailyTrend: List<TrendPoint> = emptyList(),
    val monthlyTrend: List<TrendPoint> = emptyList(),
)

data class ScannedLedgerRow(
    val localId: String,
    val customerName: String,
    val dateText: String,
    val totalAmount: String,
    val paidAmount: String,
    val dueAmount: String,
    val notes: String = "",
)

object RiceCatalog {
    val varieties = listOf(
        "Miniket" to "মিনিকেট",
        "Nazirshail" to "নাজিরশাইল",
        "BR-28" to "বিআর-২৮",
        "Kataribhog" to "কাটারিভোগ",
        "Chinigura" to "চিনিগুঁড়া",
        "Swarna" to "স্বর্ণা",
    )

    val dailyExpenseCategories = listOf(
        "Transport / Van" to "গাড়ি / ভ্যান ভাড়া",
        "Labor / Loading" to "লেবার / লোডিং",
        "Packaging / Sacks" to "বস্তা ও প্যাকিং",
        "Misc" to "অন্যান্য খরচ",
    )

    val monthlyExpenseCategories = listOf(
        "Staff Salary" to "স্টাফ বেতন",
        "Electricity Bill" to "বিদ্যুৎ বিল",
        "Godown / Shop Rent" to "দোকান / গোডাউন ভাড়া",
        "Other Fixed Cost" to "অন্যান্য মাসিক খরচ",
    )
}
