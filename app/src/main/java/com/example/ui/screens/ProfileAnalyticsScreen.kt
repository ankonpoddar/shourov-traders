package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BankLoan
import com.example.data.model.BusinessAggregator
import com.example.data.model.BusinessExpense
import com.example.data.model.CustomerSummary
import com.example.data.model.LedgerTransaction
import com.example.data.preferences.LanguageMode
import com.example.data.preferences.bi
import com.example.data.preferences.formatBdt
import com.example.ui.components.KpiMetricCard
import com.example.ui.components.SalesProfitTrendChart
import com.example.ui.theme.CrimsonDue
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.HarvestAmber
import com.example.ui.theme.PlumBadge
import com.example.ui.theme.ShouravColors
import com.example.ui.theme.SkyChart
import kotlin.math.roundToInt

@Composable
fun ProfileAnalyticsScreen(
    userEmail: String,
    transactions: List<LedgerTransaction>,
    expenses: List<BusinessExpense>,
    loans: List<BankLoan>,
    mode: LanguageMode,
    onBack: () -> Unit,
    onExportMonthlyPdf: () -> Unit,
    onSignOut: () -> Unit,
    referenceTimeMillis: Long = System.currentTimeMillis(),
    customerSummaries: List<CustomerSummary> = emptyList(),
    showBackButton: Boolean = true,
) {
    BackHandler(enabled = showBackButton, onBack = onBack)
    var selectedDays by remember { mutableIntStateOf(7) }

    val analytics = remember(selectedDays, transactions, expenses, loans, referenceTimeMillis) {
        BusinessAggregator.buildPeriodAnalytics(
            daysCount = selectedDays,
            transactions = transactions,
            expenses = expenses,
            loans = loans,
            referenceTimeMillis = referenceTimeMillis,
        )
    }

    val topDueCustomers = remember(customerSummaries) {
        customerSummaries
            .filter { it.currentDue > 0.5 }
            .sortedByDescending { it.currentDue }
            .take(4)
    }
    val maxCustomerDue = remember(topDueCustomers) {
        (topDueCustomers.maxOfOrNull { it.currentDue } ?: 1.0).coerceAtLeast(1.0)
    }

    val topExpenseCategories = remember(expenses) {
        expenses
            .groupBy { it.category.ifBlank { "Misc" } }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .entries
            .sortedByDescending { it.value }
            .take(4)
    }
    val maxExpenseCategory = remember(topExpenseCategories) {
        (topExpenseCategories.maxOfOrNull { it.value } ?: 1.0).coerceAtLeast(1.0)
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .testTag("profile_analytics_screen"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showBackButton) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("profile_back_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = bi("Back", "পেছনে যান", mode),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bi("Analytics & Report", "বিশ্লেষণ ও রিপোর্ট", mode),
                            style = MaterialTheme.typography.headlineSmall,
                            color = ShouravColors.emeraldInk,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = bi(
                                "Review 7-day & 30-day sales, Nagad, due & net profit",
                                "৭ দিন ও ৩০ দিনের বিক্রি, নগদ, বাকী ও নিট লাভ দেখুন",
                                mode,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 7-Day vs 30-Day Period Switcher (Screenshot 2 pill)
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PeriodToggleTab(
                            label = bi("Last 7 Days", "গত ৭ দিন", mode),
                            selected = selectedDays == 7,
                            onClick = { selectedDays = 7 },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("analytics_7d_btn"),
                        )
                        PeriodToggleTab(
                            label = bi("Last 30 Days", "গত ৩০ দিন", mode),
                            selected = selectedDays == 30,
                            onClick = { selectedDays = 30 },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("analytics_30d_btn"),
                        )
                    }
                }
            }

            // Period 2x2 KPI Grid (Screenshot 2 circular pastel icon grid)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        KpiMetricCard(
                            title = if (selectedDays == 7) {
                                bi("7D Total Sale", "৭ দিনের বিক্রি", mode)
                            } else {
                                bi("30D Total Sale", "৩০ দিনের বিক্রি", mode)
                            },
                            value = formatBdt(analytics.totalSales),
                            subtitle = "${analytics.saleCount} ${bi("sales", "টি বিক্রি", mode)}",
                            icon = Icons.Filled.ShoppingBag,
                            accentColor = SkyChart,
                            containerColor = ShouravColors.skyContainer,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("analytics_sales_card"),
                        )
                        KpiMetricCard(
                            title = if (selectedDays == 7) {
                                bi("7D Nagad", "৭ দিনের নগদ", mode)
                            } else {
                                bi("30D Nagad", "৩০ দিনের নগদ", mode)
                            },
                            value = formatBdt(analytics.totalNagad),
                            subtitle = bi("Cash & Joma", "নগদ ও জমা আদায়", mode),
                            icon = Icons.Filled.Payments,
                            accentColor = ShouravColors.emeraldIconTint,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            valueColor = ShouravColors.emeraldInk,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("analytics_nagad_card"),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        KpiMetricCard(
                            title = if (selectedDays == 7) {
                                bi("7D Due Added", "৭ দিনের বাকী", mode)
                            } else {
                                bi("30D Due Added", "৩০ দিনের বাকী", mode)
                            },
                            value = formatBdt(analytics.totalDueAdded),
                            subtitle = bi("New credit sales", "নতুন বাকী বিক্রি", mode),
                            icon = Icons.Filled.AccountBalanceWallet,
                            accentColor = if (analytics.totalDueAdded > 0.5) CrimsonDue else ShouravColors.emeraldIconTint,
                            containerColor = if (analytics.totalDueAdded > 0.5) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            valueColor = if (analytics.totalDueAdded > 0.5) CrimsonDue else ShouravColors.emeraldInk,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("analytics_due_card"),
                        )
                        KpiMetricCard(
                            title = if (selectedDays == 7) {
                                bi("7D Expenses", "৭ দিনের খরচ", mode)
                            } else {
                                bi("30D Expenses", "৩০ দিনের খরচ", mode)
                            },
                            value = formatBdt(analytics.totalExpenses),
                            subtitle = "${bi("Daily", "দৈনিক", mode)}: ${formatBdt(analytics.dailyExpenses)}",
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            accentColor = HarvestAmber,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("analytics_expenses_card"),
                        )
                    }
                }
            }

            // Cash vs Due / Expenses Ratio Pill Bar (Screenshot 2: আয় বনাম ব্যয় style)
            item {
                val cashPart = analytics.totalNagad.coerceAtLeast(0.0)
                val dueOrExpPart = (analytics.totalDueAdded + analytics.totalExpenses).coerceAtLeast(0.0)
                val totalFlow = cashPart + dueOrExpPart
                val cashRatio = if (totalFlow > 0.0) (cashPart / totalFlow).toFloat().coerceIn(0f, 1f) else 0.5f
                val cashPct = (cashRatio * 100f).roundToInt()
                val outPct = 100 - cashPct

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = bi("Nagad vs. Due & Expenses", "নগদ বনাম বাকী ও খরচ", mode),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            if (cashRatio > 0.02f) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(cashRatio.coerceAtLeast(0.05f))
                                        .fillMaxHeight()
                                        .background(EmeraldPrimary),
                                ) {
                                    Text(
                                        text = "$cashPct%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            if ((1f - cashRatio) > 0.02f) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight((1f - cashRatio).coerceAtLeast(0.05f))
                                        .fillMaxHeight()
                                        .background(CrimsonDue),
                                ) {
                                    Text(
                                        text = "$outPct%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            RatioLegendDot(
                                color = ShouravColors.emeraldIconTint,
                                text = "${bi("Nagad", "নগদ জমা", mode)} ($cashPct%)",
                            )
                            RatioLegendDot(
                                color = CrimsonDue,
                                text = "${bi("Due + Exp", "বাকী ও খরচ", mode)} ($outPct%)",
                            )
                        }
                    }
                }
            }

            // Top Customer Dues Progress Bars (matching Screenshot 2 category progress rows)
            if (topDueCustomers.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = bi("Top Customer Dues", "শীর্ষ কাস্টমার বাকী", mode),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                            val palette = listOf(
                                ShouravColors.emeraldIconTint to MaterialTheme.colorScheme.primaryContainer,
                                PlumBadge to ShouravColors.plumContainer,
                                HarvestAmber to MaterialTheme.colorScheme.tertiaryContainer,
                                SkyChart to ShouravColors.skyContainer,
                            )
                            topDueCustomers.forEachIndexed { idx, cust ->
                                val (tint, bg) = palette[idx % palette.size]
                                ProgressBreakdownRow(
                                    icon = Icons.Filled.Person,
                                    tint = tint,
                                    iconBg = bg,
                                    label = cust.customer.name,
                                    amountText = formatBdt(cust.currentDue),
                                    fraction = (cust.currentDue / maxCustomerDue).toFloat().coerceIn(0.06f, 1f),
                                )
                            }
                        }
                    }
                }
            }

            // Expense Breakdown Bars (matching Screenshot 2)
            if (topExpenseCategories.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = bi("Category-wise Expenses", "খাত ভিত্তিক খরচ", mode),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                            val palette = listOf(
                                HarvestAmber to MaterialTheme.colorScheme.tertiaryContainer,
                                PlumBadge to ShouravColors.plumContainer,
                                SkyChart to ShouravColors.skyContainer,
                                CrimsonDue to MaterialTheme.colorScheme.errorContainer,
                            )
                            topExpenseCategories.forEachIndexed { idx, (cat, totalAmt) ->
                                val (tint, bg) = palette[idx % palette.size]
                                ProgressBreakdownRow(
                                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                                    tint = tint,
                                    iconBg = bg,
                                    label = cat,
                                    amountText = formatBdt(totalAmt),
                                    fraction = (totalAmt / maxExpenseCategory).toFloat().coerceIn(0.06f, 1f),
                                )
                            }
                        }
                    }
                }
            }

            // Period Net Profit Breakdown Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.testTag("analytics_net_profit_card"),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = ShouravColors.emeraldIconTint,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = if (selectedDays == 7) {
                                    bi("7-Day Net Profit Breakdown", "৭ দিনের নিট লাভ হিসাব", mode)
                                } else {
                                    bi("30-Day Net Profit Breakdown", "৩০ দিনের নিট লাভ হিসাব", mode)
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = bi("Gross Sale Profit", "বিক্রি থেকে মোট লাভ", mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = formatBdt(analytics.totalGrossProfit, showPositiveSign = true),
                                style = MaterialTheme.typography.labelMedium,
                                color = ShouravColors.emeraldInk,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = bi("Daily Expenses (${analytics.daysCount}d)", "দৈনিক খরচ", mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "-${formatBdt(analytics.dailyExpenses)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = HarvestAmber,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = bi("Fixed Bills + Loan Interest Share", "মাসিক বিল ও সুদের অংশ", mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "-${formatBdt(analytics.proratedFixedAndInterest)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = CrimsonDue,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = bi("Period Net Profit", "নিট লাভ", mode),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = formatBdt(analytics.netProfit, showPositiveSign = analytics.netProfit > 0.0),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (analytics.netProfit >= 0.0) ShouravColors.emeraldInk else CrimsonDue,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // Period Trend Chart
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = if (selectedDays == 7) {
                                bi("7-Day Sales vs. Net Profit", "৭ দিনের বিক্রি ও নিট লাভ", mode)
                            } else {
                                bi("30-Day Sales vs. Net Profit", "৩০ দিনের বিক্রি ও নিট লাভ", mode)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        SalesProfitTrendChart(
                            points = analytics.trendPoints,
                            mode = mode,
                        )
                    }
                }
            }

            // Account Profile & Report Export Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, ShouravColors.emeraldBorder),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AccountCircle,
                                    contentDescription = null,
                                    tint = ShouravColors.emeraldIconTint,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Shourav Traders",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = userEmail.ifBlank { bi("Cloud Ledger Account", "ক্লাউড খাতা অ্যাকাউন্ট", mode) },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = onExportMonthlyPdf,
                                border = BorderStroke(1.dp, ShouravColors.emeraldBorder),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("profile_export_pdf_btn"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PictureAsPdf,
                                    contentDescription = null,
                                    tint = ShouravColors.emeraldIconTint,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = bi("Monthly PDF", "মাসিক PDF", mode),
                                    color = ShouravColors.emeraldInk,
                                    maxLines = 1,
                                )
                            }
                            OutlinedButton(
                                onClick = onSignOut,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("profile_sign_out_btn"),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = null,
                                    tint = CrimsonDue,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = bi("Sign Out", "লগ আউট", mode),
                                    color = CrimsonDue,
                                    maxLines = 1,
                                )
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
private fun RatioLegendDot(
    color: Color,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ProgressBreakdownRow(
    icon: ImageVector,
    tint: Color,
    iconBg: Color,
    label: String,
    amountText: String,
    fraction: Float,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(tint),
                )
            }
        }
    }
}

@Composable
private fun PeriodToggleTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) EmeraldPrimary else Color.Transparent,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
