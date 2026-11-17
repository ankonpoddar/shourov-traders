package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BankLoan
import com.example.data.model.BusinessAggregator
import com.example.data.model.BusinessExpense
import com.example.data.model.BusinessSummary
import com.example.data.model.Customer
import com.example.data.model.CustomerSummary
import com.example.data.model.CustomerTransactionEntry
import com.example.data.model.LedgerTransaction
import com.example.data.preferences.LanguageMode
import com.example.data.preferences.bi
import com.example.data.preferences.formatBdt
import com.example.data.preferences.formatShortDate
import com.example.data.report.TransactionSlipData
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.SalesProfitTrendChart
import com.example.ui.components.TransactionSlipDialog
import com.example.ui.theme.CrimsonDue
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ForestHeroEnd
import com.example.ui.theme.ForestHeroStart
import com.example.ui.theme.HarvestAmber
import com.example.ui.theme.ShouravColors
import com.example.ui.theme.SkyChart
import com.example.ui.viewmodel.BusinessUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    state: BusinessUiState.Ready,
    mode: LanguageMode,
    isBusy: Boolean,
    onQuickNewSale: () -> Unit,
    onQuickAddCustomer: () -> Unit,
    onQuickAddExpense: () -> Unit,
    onQuickScanKhata: () -> Unit,
    onLoadSampleData: () -> Unit,
    onOpenDueCustomers: () -> Unit = {},
    onOpenCustomerTransactions: () -> Unit = onOpenDueCustomers,
    onOpenExpenses: () -> Unit = {},
    onExportMonthlyPdf: (() -> Unit)? = null,
    onOpenProfileAnalytics: (() -> Unit)? = null,
    onSlipFeedback: (String) -> Unit = {},
) {
    MainBusinessDashboard(
        customers = state.customers,
        transactions = state.transactions,
        expenses = state.expenses,
        loans = state.loans,
        mode = mode,
        isBusy = isBusy,
        onQuickNewSale = onQuickNewSale,
        onQuickAddCustomer = onQuickAddCustomer,
        onQuickAddExpense = onQuickAddExpense,
        onQuickScanKhata = onQuickScanKhata,
        onLoadSampleData = onLoadSampleData,
        onOpenDueCustomers = onOpenDueCustomers,
        onOpenCustomerTransactions = onOpenCustomerTransactions,
        onOpenExpenses = onOpenExpenses,
        onExportMonthlyPdf = onExportMonthlyPdf,
        onOpenProfileAnalytics = onOpenProfileAnalytics,
        onSlipFeedback = onSlipFeedback,
        precomputedSummary = state.summary,
        precomputedCustomerSummaries = state.customerSummaries,
    )
}

@Composable
fun MainBusinessDashboard(
    customers: List<Customer>,
    transactions: List<LedgerTransaction>,
    expenses: List<BusinessExpense>,
    loans: List<BankLoan>,
    mode: LanguageMode,
    isBusy: Boolean,
    onQuickNewSale: () -> Unit,
    onQuickAddCustomer: () -> Unit,
    onQuickAddExpense: () -> Unit,
    onQuickScanKhata: () -> Unit,
    onLoadSampleData: () -> Unit,
    onOpenDueCustomers: () -> Unit = {},
    onOpenCustomerTransactions: () -> Unit = onOpenDueCustomers,
    onOpenExpenses: () -> Unit = {},
    onExportMonthlyPdf: (() -> Unit)? = null,
    onOpenProfileAnalytics: (() -> Unit)? = null,
    onSlipFeedback: (String) -> Unit = {},
    precomputedSummary: BusinessSummary? = null,
    precomputedCustomerSummaries: List<CustomerSummary>? = null,
) {
    var showMonthlyChart by remember { mutableStateOf(false) }
    var activeSlipData by remember { mutableStateOf<TransactionSlipData?>(null) }

    if (activeSlipData != null) {
        TransactionSlipDialog(
            slipData = activeSlipData!!,
            mode = mode,
            onDismiss = { activeSlipData = null },
            onFeedback = onSlipFeedback,
        )
    }

    val aggregated = remember(customers, transactions, expenses, loans, precomputedSummary, precomputedCustomerSummaries) {
        if (precomputedSummary != null && precomputedCustomerSummaries != null) {
            null
        } else {
            BusinessAggregator.aggregate(
                customers = customers,
                transactions = transactions,
                expenses = expenses,
                loans = loans,
            )
        }
    }
    val summary = precomputedSummary ?: aggregated!!.businessSummary
    val customerSummaries = precomputedCustomerSummaries ?: aggregated!!.customerSummaries

    val isEmptyBusiness = customers.isEmpty() &&
        transactions.isEmpty() &&
        expenses.isEmpty() &&
        loans.isEmpty()

    val salesCount = remember(transactions) {
        transactions.count { it.type == LedgerTransaction.TYPE_SALE }
    }
    val dueCustomersCount = remember(customerSummaries) {
        customerSummaries.count { it.currentDue > 0.5 }
    }
    val todayDateLabel = remember {
        SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.ENGLISH).format(Date())
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .testTag("main_business_dashboard"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Greeting & Date Header (Screenshot 5 style)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bi("Hisab Khata", "হিসাব খাতা", mode),
                            style = MaterialTheme.typography.headlineMedium,
                            color = ShouravColors.emeraldInk,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = todayDateLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (onExportMonthlyPdf != null) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shadowElevation = 1.dp,
                            modifier = Modifier
                                .defaultMinSize(minHeight = 42.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .clickable(onClick = onExportMonthlyPdf)
                                .testTag("dashboard_export_pdf_btn"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PictureAsPdf,
                                    contentDescription = bi("Export PDF Report", "PDF রিপোর্ট", mode),
                                    tint = ShouravColors.emeraldIconTint,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = bi("PDF", "PDF", mode),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ShouravColors.emeraldInk,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            // Signature Dark Forest Green Hero Balance Card (Screenshot 5)
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    shadowElevation = 4.dp,
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(onClick = onOpenDueCustomers),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(ForestHeroStart, ForestHeroEnd)
                                )
                            )
                            .padding(16.dp),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top,
                            ) {
                                Column {
                                    Text(
                                        text = bi("Total Receivable (Baki)", "মোট পাবো / বাকী ব্যালেন্স", mode),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White.copy(alpha = 0.85f),
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = formatBdt(summary.totalReceivable),
                                        style = MaterialTheme.typography.displaySmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = Color.White.copy(alpha = 0.16f),
                                ) {
                                    Text(
                                        text = "${customers.size} ${bi("Customers", "ক্রেতা", mode)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                HeroStatItem(
                                    icon = Icons.Filled.ArrowDownward,
                                    label = bi("Net Profit", "মোট নিট লাভ", mode),
                                    value = formatBdt(summary.netProfit, showPositiveSign = summary.netProfit > 0.0),
                                    modifier = Modifier.weight(1f),
                                )
                                HeroStatItem(
                                    icon = Icons.Filled.ArrowUpward,
                                    label = bi("Due Customers", "বাকী ক্রেতা", mode),
                                    value = "$dueCustomersCount / $salesCount ${bi("Sales", "বিক্রি", mode)}",
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            // 4 Quick Action Icon Tiles Row (matching Screenshot 5's 4 square tiles)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DashboardSquareActionTile(
                        label = bi("New Sale", "নতুন বিক্রি", mode),
                        icon = Icons.Filled.AddShoppingCart,
                        tint = ShouravColors.emeraldIconTint,
                        iconBg = MaterialTheme.colorScheme.primaryContainer,
                        onClick = onQuickNewSale,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_new_sale_btn"),
                    )
                    DashboardSquareActionTile(
                        label = bi("Customer", "ক্রেতা যোগ", mode),
                        icon = Icons.Filled.PersonAdd,
                        tint = SkyChart,
                        iconBg = ShouravColors.skyContainer,
                        onClick = onQuickAddCustomer,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_add_customer_btn"),
                    )
                    DashboardSquareActionTile(
                        label = bi("Expense", "খরচ যোগ", mode),
                        icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        tint = HarvestAmber,
                        iconBg = MaterialTheme.colorScheme.tertiaryContainer,
                        onClick = onQuickAddExpense,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_add_expense_btn"),
                    )
                    DashboardSquareActionTile(
                        label = bi("AI Scan", "খাতা স্ক্যান", mode),
                        icon = Icons.Filled.DocumentScanner,
                        tint = ShouravColors.emeraldInk,
                        iconBg = MaterialTheme.colorScheme.primaryContainer,
                        onClick = onQuickScanKhata,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_scan_khata_btn"),
                    )
                }
            }

            if (isEmptyBusiness) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        border = BorderStroke(1.dp, ShouravColors.emeraldBorder),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = ShouravColors.emeraldInk,
                                )
                                Text(
                                    text = bi(
                                        "Ready to explore Shourav Traders?",
                                        "সৌরভ ট্রেডার্স শুরু করতে প্রস্তুত?",
                                        mode,
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ShouravColors.emeraldInk,
                                )
                            }
                            Text(
                                text = bi(
                                    "Load realistic Bangladeshi rice business sample data (5 customers, Miniket/Nazirshail sales, staff & electricity bills, and a 13% bank loan) or start adding your own records.",
                                    "বাস্তবসম্মত চালের ব্যবসার নমুনা ডাটা (৫ জন ক্রেতা, মিনিকেট/নাজিরশাইল বিক্রি, স্টাফ বেতন ও ১৩% ব্যাংক লোন) লোড করুন অথবা সরাসরি নতুন এন্ট্রি দিন।",
                                    mode,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Button(
                                onClick = onLoadSampleData,
                                enabled = !isBusy,
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("load_sample_data_button"),
                            ) {
                                Text(bi("Load Sample Rice Data", "নমুনা ডাটা লোড করুন", mode))
                            }
                        }
                    }
                }
            }

            // Today's 2x2 KPI Cards (Today's Sale, Due, Nagad, Expenses)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = bi("Today's Business Hisab", "আজকের হিসাব", mode),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                        )
                        if (onOpenProfileAnalytics != null) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(1.dp, ShouravColors.emeraldBorder),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable(onClick = onOpenProfileAnalytics)
                                    .testTag("dashboard_7d_30d_chip"),
                            ) {
                                Text(
                                    text = bi("7D / 30D Report →", "৭ / ৩০ দিন →", mode),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ShouravColors.emeraldInk,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        KpiMetricCard(
                            title = bi("Today's Sale", "আজকের বিক্রি", mode),
                            value = formatBdt(summary.today.todaySales),
                            subtitle = "${summary.today.todaySaleCount} ${bi("sales today", "টি বিক্রি", mode)}",
                            icon = Icons.Filled.ShoppingBag,
                            accentColor = SkyChart,
                            containerColor = ShouravColors.skyContainer,
                            onClick = onQuickNewSale,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("kpi_today_sales"),
                        )
                        KpiMetricCard(
                            title = bi("Today's Due", "আজকের বাকী", mode),
                            value = formatBdt(summary.today.todayDueAdded),
                            subtitle = bi("Added today", "আজ নতুন বাকী", mode),
                            icon = Icons.Filled.AccountBalanceWallet,
                            accentColor = if (summary.today.todayDueAdded > 0.5) CrimsonDue else ShouravColors.emeraldIconTint,
                            containerColor = if (summary.today.todayDueAdded > 0.5) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            valueColor = if (summary.today.todayDueAdded > 0.5) CrimsonDue else ShouravColors.emeraldInk,
                            onClick = onOpenDueCustomers,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("kpi_today_due"),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        KpiMetricCard(
                            title = bi("Today's Nagad", "আজকের নগদ", mode),
                            value = formatBdt(summary.today.todayNagad),
                            subtitle = bi("Cash & Joma today", "আজকের নগদ জমা", mode),
                            icon = Icons.Filled.Payments,
                            accentColor = ShouravColors.emeraldIconTint,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            valueColor = ShouravColors.emeraldInk,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("kpi_today_nagad"),
                        )
                        KpiMetricCard(
                            title = bi("Today's Expenses", "আজকের খরচ", mode),
                            value = formatBdt(summary.today.todayExpenses),
                            subtitle = "${bi("Total Exp", "মোট খরচ", mode)}: ${formatBdt(summary.totalExpenses)}",
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            accentColor = HarvestAmber,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            onClick = onOpenExpenses,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("kpi_today_expenses"),
                        )
                    }
                }
            }

            // Overall Khata & Net Profit Card (with circular + sale shortcut matching Screenshot 5)
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                    modifier = Modifier.testTag("overall_summary_card"),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bi("Overall Khata & Profit", "মোট খাতা ও লাভের হিসাব", mode),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "${bi("Total Sales", "মোট বিক্রি", mode)}: ${formatBdt(summary.totalSales)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = EmeraldPrimary,
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onQuickNewSale),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = bi("New Sale", "নতুন বিক্রি", mode),
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SummaryRow(
                            label = bi("Total Rice Sales", "সর্বমোট চাল বিক্রি", mode),
                            value = formatBdt(summary.totalSales),
                            valueColor = MaterialTheme.colorScheme.onSurface,
                        )
                        SummaryRow(
                            label = bi("Gross Sale Profit", "বিক্রি থেকে মোট লাভ", mode),
                            value = formatBdt(summary.totalGrossProfit, showPositiveSign = true),
                            valueColor = ShouravColors.emeraldInk,
                        )
                        SummaryRow(
                            label = bi("Daily + Monthly Bills", "দৈনিক + মাসিক খরচ", mode),
                            value = "-${formatBdt(summary.dailyExpensesTotal + summary.monthlyExpensesTotal)}",
                            valueColor = HarvestAmber,
                        )
                        SummaryRow(
                            label = bi("Monthly Bank Loan Interest", "মাসিক ব্যাংক লোন সুদ", mode),
                            value = "-${formatBdt(summary.monthlyLoanInterest)}",
                            valueColor = CrimsonDue,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = bi("Net Business Profit", "সর্বমোট নিট লাভ", mode),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = formatBdt(summary.netProfit, showPositiveSign = summary.netProfit > 0.0),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (summary.netProfit >= 0.0) ShouravColors.emeraldInk else CrimsonDue,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // Sales vs Net Profit Chart Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = bi("Sales vs. Net Profit", "বিক্রি বনাম নিট লাভ", mode),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Row(modifier = Modifier.padding(3.dp)) {
                                    ChartPeriodChip(
                                        label = bi("7 Days", "৭ দিন", mode),
                                        selected = !showMonthlyChart,
                                        onClick = { showMonthlyChart = false },
                                        modifier = Modifier.testTag("chart_period_daily"),
                                    )
                                    ChartPeriodChip(
                                        label = bi("Monthly", "মাসিক", mode),
                                        selected = showMonthlyChart,
                                        onClick = { showMonthlyChart = true },
                                        modifier = Modifier.testTag("chart_period_monthly"),
                                    )
                                }
                            }
                        }
                        SalesProfitTrendChart(
                            points = if (showMonthlyChart) summary.monthlyTrend else summary.dailyTrend,
                            mode = mode,
                        )
                    }
                }
            }

            // Recent Customer Lenden (matching Screenshot 5 bottom section)
            if (transactions.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = bi("Recent Customer Lenden", "সাম্প্রতিক কাস্টমার লেনদেন", mode),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = bi("See All", "সব দেখুন", mode),
                            style = MaterialTheme.typography.labelLarge,
                            color = ShouravColors.emeraldIconTint,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onOpenCustomerTransactions)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                items(transactions.take(5), key = { it.id }) { tx ->
                    val isPayment = tx.type == LedgerTransaction.TYPE_PAYMENT
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (isPayment) MaterialTheme.colorScheme.primaryContainer else ShouravColors.skyContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (isPayment) Icons.Filled.Payments else Icons.Filled.ShoppingBag,
                                    contentDescription = null,
                                    tint = if (isPayment) ShouravColors.emeraldIconTint else SkyChart,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tx.customerName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val detail = if (!isPayment) {
                                    "${tx.riceVariety} · ${tx.quantity.toInt()} ${tx.unit} · ${formatShortDate(tx.transactionDateMillis)}"
                                } else {
                                    "${bi("Joma Received", "নগদ জমা", mode)} · ${formatShortDate(tx.transactionDateMillis)}"
                                }
                                Text(
                                    text = detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (!isPayment) {
                                        formatBdt(tx.totalAmount)
                                    } else {
                                        formatBdt(tx.paidAmount, showPositiveSign = true)
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isPayment) ShouravColors.emeraldInk else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (!isPayment && tx.dueAdded > 0.5) {
                                    Text(
                                        text = "${bi("Due", "বাকী", mode)} ${formatBdt(tx.dueAdded)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CrimsonDue,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        val customer = customers.firstOrNull { it.id == tx.customerId }
                                        val entry = if (customer != null) {
                                            BusinessAggregator
                                                .buildCustomerLedgerTimeline(customer, transactions)
                                                .firstOrNull { it.transaction.id == tx.id }
                                        } else {
                                            null
                                        } ?: CustomerTransactionEntry(
                                            transaction = tx,
                                            previousDue = 0.0,
                                            deltaDue = if (isPayment) -tx.paidAmount else tx.dueAdded,
                                            runningDueAfter = if (isPayment) 0.0 else tx.dueAdded,
                                        )
                                        activeSlipData = TransactionSlipData(customer = customer, entry = entry)
                                    }
                                    .testTag("recent_tx_slip_${tx.id}"),
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(10.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PictureAsPdf,
                                        contentDescription = bi("Slip PDF", "রসিদ PDF", mode),
                                        tint = ShouravColors.emeraldInk,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun HeroStatItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DashboardSquareActionTile(
    label: String,
    icon: ImageVector,
    tint: Color,
    iconBg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 1.dp,
            modifier = Modifier.size(56.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(iconBg.copy(alpha = if (ShouravColors.isDark) 0.85f else 0.45f)),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    valueColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ChartPeriodChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) EmeraldPrimary else Color.Transparent,
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}
