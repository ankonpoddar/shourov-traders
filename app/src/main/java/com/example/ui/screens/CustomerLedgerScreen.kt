package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BusinessAggregator
import com.example.data.model.CustomerSummary
import com.example.data.model.CustomerTransactionEntry
import com.example.data.model.LedgerTransaction
import com.example.data.preferences.LanguageMode
import com.example.data.preferences.bi
import com.example.data.preferences.formatBdt
import com.example.data.preferences.formatShortDate
import com.example.data.report.TransactionSlipData
import com.example.ui.components.BakiBadge
import com.example.ui.components.RecordPaymentDialog
import com.example.ui.components.TransactionSlipDialog
import com.example.ui.theme.CrimsonDue
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ShouravColors
import com.example.ui.theme.SkyChart
import com.example.ui.viewmodel.BusinessUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private enum class KhataFilter { ALL, DUE_ONLY, CLEARED_ONLY }
private enum class LendenTypeFilter { ALL, SALES, PAYMENTS }
private enum class CustomersViewMode { KHATA_AZ, CUSTOMER_LENDEN }

@Composable
fun CustomerLedgerScreen(
    state: BusinessUiState.Ready,
    mode: LanguageMode,
    onOpenCustomer: (customerId: String) -> Unit,
    onAddCustomerClick: () -> Unit,
    initialShowTransactions: Boolean = false,
    onConsumeInitialTab: () -> Unit = {},
    onSlipFeedback: (String) -> Unit = {},
) {
    var viewMode by remember {
        mutableStateOf(
            if (initialShowTransactions) CustomersViewMode.CUSTOMER_LENDEN else CustomersViewMode.KHATA_AZ
        )
    }
    LaunchedEffect(initialShowTransactions) {
        if (initialShowTransactions) {
            viewMode = CustomersViewMode.CUSTOMER_LENDEN
            onConsumeInitialTab()
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(KhataFilter.ALL) }
    var lendenFilter by remember { mutableStateOf(LendenTypeFilter.ALL) }
    // monthOffset: null = All time, 0 = Current Month, -1 = Previous Month, etc.
    var monthOffset by remember { mutableStateOf<Int?>(null) }
    var activeSlipData by remember { mutableStateOf<TransactionSlipData?>(null) }

    if (activeSlipData != null) {
        TransactionSlipDialog(
            slipData = activeSlipData!!,
            mode = mode,
            onDismiss = { activeSlipData = null },
            onFeedback = onSlipFeedback,
        )
    }

    // Preserve A-Z master serial numbers (#1..#N) even while searching or filtering
    val indexedSummaries = remember(state.customerSummaries) {
        state.customerSummaries
            .sortedBy { it.customer.name.trim().lowercase() }
            .mapIndexed { index, summary -> (index + 1) to summary }
    }

    val filteredCustomers = remember(indexedSummaries, searchQuery, filter) {
        val q = searchQuery.trim().lowercase()
        indexedSummaries.filter { (_, summary) ->
            val matchesQuery = q.isEmpty() ||
                summary.customer.name.lowercase().contains(q) ||
                summary.customer.phone.lowercase().contains(q) ||
                summary.customer.shopAddress.lowercase().contains(q)
            val matchesFilter = when (filter) {
                KhataFilter.ALL -> true
                KhataFilter.DUE_ONLY -> summary.currentDue > 0.5
                KhataFilter.CLEARED_ONLY -> summary.currentDue <= 0.5
            }
            matchesQuery && matchesFilter
        }
    }

    val monthRangeInfo = remember(monthOffset) {
        val offset = monthOffset
        if (offset == null) {
            null
        } else {
            val cal = Calendar.getInstance().apply {
                add(Calendar.MONTH, offset)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            val label = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).format(cal.time)
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            Triple(label, start, cal.timeInMillis)
        }
    }

    val filteredTransactions = remember(state.transactions, searchQuery, lendenFilter, monthRangeInfo) {
        val q = searchQuery.trim().lowercase()
        state.transactions.filter { tx ->
            val matchesQuery = q.isEmpty() ||
                tx.customerName.lowercase().contains(q) ||
                tx.riceVariety.lowercase().contains(q) ||
                tx.notes.lowercase().contains(q)
            val matchesType = when (lendenFilter) {
                LendenTypeFilter.ALL -> true
                LendenTypeFilter.SALES -> tx.type == LedgerTransaction.TYPE_SALE
                LendenTypeFilter.PAYMENTS -> tx.type == LedgerTransaction.TYPE_PAYMENT
            }
            val matchesMonth = monthRangeInfo == null ||
                tx.transactionDateMillis in monthRangeInfo.second..monthRangeInfo.third
            matchesQuery && matchesType && matchesMonth
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .testTag("customer_ledger_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (viewMode == CustomersViewMode.CUSTOMER_LENDEN) {
                                bi("Customer Lenden", "কাস্টমার লেনদেন", mode)
                            } else {
                                bi("Customer Baki Khata (A–Z)", "ক্রেতার বাকী খাতা (A–Z)", mode)
                            },
                            style = MaterialTheme.typography.headlineSmall,
                            color = ShouravColors.emeraldInk,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${state.customerSummaries.size} ${bi("customers", "জন ক্রেতা", mode)} · ${bi("Receivable", "মোট পাবো", mode)}: ${formatBdt(state.summary.totalReceivable)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = onAddCustomerClick,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("khata_add_customer_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(bi("Add", "যোগ", mode))
                    }
                }
            }

            // Segmented Switcher: Khata A-Z vs Customer Lenden (Screenshot 3)
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SegmentToggleChip(
                            label = bi("Khata List (A–Z)", "ক্রেতা তালিকা (A–Z)", mode),
                            selected = viewMode == CustomersViewMode.KHATA_AZ,
                            onClick = { viewMode = CustomersViewMode.KHATA_AZ },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tab_khata_az"),
                        )
                        SegmentToggleChip(
                            label = bi("Customer Lenden", "কাস্টমার লেনদেন", mode),
                            selected = viewMode == CustomersViewMode.CUSTOMER_LENDEN,
                            onClick = { viewMode = CustomersViewMode.CUSTOMER_LENDEN },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tab_customer_lenden"),
                        )
                    }
                }
            }

            // Month Switcher Pill (shown in Customer Lenden view matching Screenshot 3)
            if (viewMode == CustomersViewMode.CUSTOMER_LENDEN) {
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
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = {
                                    monthOffset = (monthOffset ?: 0) - 1
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = bi("Previous month", "আগের মাস", mode),
                                    tint = ShouravColors.emeraldInk,
                                )
                            }
                            Text(
                                text = monthRangeInfo?.first ?: bi("All Months", "সকল মাস", mode),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        monthOffset = if (monthOffset == null) 0 else null
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                            IconButton(
                                onClick = {
                                    val cur = monthOffset
                                    monthOffset = when {
                                        cur == null -> 0
                                        cur >= 0 -> null
                                        else -> cur + 1
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = bi("Next month", "পরের মাস", mode),
                                    tint = ShouravColors.emeraldInk,
                                )
                            }
                        }
                    }
                }
            }

            // Pill Search Bar (matching Screenshot 3)
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = if (viewMode == CustomersViewMode.CUSTOMER_LENDEN) {
                                bi("Search customer transactions...", "কাস্টমারের লেনদেন খুঁজুন...", mode)
                            } else {
                                bi("Search customer A–Z, phone, shop...", "ক্রেতার নাম বা ফোন খুঁজুন...", mode)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.testTag("customer_search_clear_btn"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = bi("Clear search", "খোঁজ মুছুন", mode),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(26.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = EmeraldPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_search_input"),
                )
            }

            if (viewMode == CustomersViewMode.KHATA_AZ) {
                // Dark Forest Green Filter Pills for Customer Khata
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        PillFilterChip(
                            label = bi("All", "সব", mode),
                            selected = filter == KhataFilter.ALL,
                            onClick = { filter = KhataFilter.ALL },
                        )
                        PillFilterChip(
                            label = bi("Due Only", "বাকী আছে", mode),
                            selected = filter == KhataFilter.DUE_ONLY,
                            selectedColor = CrimsonDue,
                            onClick = { filter = KhataFilter.DUE_ONLY },
                        )
                        PillFilterChip(
                            label = bi("Cleared", "পরিশোধিত", mode),
                            selected = filter == KhataFilter.CLEARED_ONLY,
                            onClick = { filter = KhataFilter.CLEARED_ONLY },
                        )
                    }
                }

                if (filteredCustomers.isEmpty()) {
                    item {
                        EmptyLedgerStateCard(
                            title = bi("No matching customers found", "কোনো ক্রেতা পাওয়া যায়নি", mode),
                            subtitle = bi("Tap Add to create a customer ledger.", "নতুন ক্রেতা যোগ করতে Add বাটনে চাপ দিন।", mode),
                        )
                    }
                } else {
                    items(filteredCustomers, key = { (_, summary) -> summary.customer.id }) { (serialNumber, summary) ->
                        CustomerDirectoryRow(
                            serialNumber = serialNumber,
                            summary = summary,
                            mode = mode,
                            onClick = { onOpenCustomer(summary.customer.id) },
                        )
                    }
                }
            } else {
                // Filter Pills for Customer Lenden matching Screenshot 3 (সব | বিক্রি | জমা)
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        PillFilterChip(
                            label = bi("All", "সব", mode),
                            selected = lendenFilter == LendenTypeFilter.ALL,
                            onClick = { lendenFilter = LendenTypeFilter.ALL },
                        )
                        PillFilterChip(
                            label = bi("Sales / Due", "বিক্রি / বাকী", mode),
                            selected = lendenFilter == LendenTypeFilter.SALES,
                            onClick = { lendenFilter = LendenTypeFilter.SALES },
                        )
                        PillFilterChip(
                            label = bi("Joma / Cash", "জমা / নগদ", mode),
                            selected = lendenFilter == LendenTypeFilter.PAYMENTS,
                            onClick = { lendenFilter = LendenTypeFilter.PAYMENTS },
                        )
                    }
                }

                if (filteredTransactions.isEmpty()) {
                    item {
                        EmptyLedgerStateCard(
                            title = bi("No customer transactions found", "কোনো কাস্টমার লেনদেন পাওয়া যায়নি", mode),
                            subtitle = bi("Record a rice sale or payment to view entries here.", "নতুন বিক্রি বা জমা এন্ট্রি দিলে এখানে দেখা যাবে।", mode),
                        )
                    }
                } else {
                    items(filteredTransactions, key = { it.id }) { tx ->
                        CustomerLendenTransactionCard(
                            tx = tx,
                            mode = mode,
                            onOpenCustomer = {
                                if (tx.customerId.isNotBlank()) {
                                    onOpenCustomer(tx.customerId)
                                }
                            },
                            onOpenSlip = {
                                val customer = state.customers.firstOrNull { it.id == tx.customerId }
                                val entry = if (customer != null) {
                                    BusinessAggregator
                                        .buildCustomerLedgerTimeline(customer, state.transactions)
                                        .firstOrNull { it.transaction.id == tx.id }
                                } else {
                                    null
                                } ?: CustomerTransactionEntry(
                                    transaction = tx,
                                    previousDue = 0.0,
                                    deltaDue = if (tx.type == LedgerTransaction.TYPE_PAYMENT) -tx.paidAmount else tx.dueAdded,
                                    runningDueAfter = if (tx.type == LedgerTransaction.TYPE_PAYMENT) 0.0 else tx.dueAdded,
                                )
                                activeSlipData = TransactionSlipData(customer = customer, entry = entry)
                            },
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun SegmentToggleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) EmeraldPrimary else Color.Transparent,
        modifier = modifier
            .defaultMinSize(minHeight = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
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

@Composable
private fun PillFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    selectedColor: Color = EmeraldPrimary,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (selected) selectedColor else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) selectedColor else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .defaultMinSize(minHeight = 38.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun EmptyLedgerStateCard(
    title: String,
    subtitle: String,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Storefront,
                contentDescription = null,
                tint = ShouravColors.subtleText,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CustomerLendenTransactionCard(
    tx: LedgerTransaction,
    mode: LanguageMode,
    onOpenCustomer: () -> Unit,
    onOpenSlip: () -> Unit,
) {
    val isPayment = tx.type == LedgerTransaction.TYPE_PAYMENT
    val hasDue = !isPayment && tx.dueAdded > 0.5
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = tx.customerId.isNotBlank(), onClick = onOpenCustomer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isPayment -> MaterialTheme.colorScheme.primaryContainer
                            hasDue -> MaterialTheme.colorScheme.errorContainer
                            else -> ShouravColors.skyContainer
                        }
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPayment) Icons.Filled.Payments else Icons.Filled.ShoppingBag,
                    contentDescription = null,
                    tint = when {
                        isPayment -> ShouravColors.emeraldIconTint
                        hasDue -> CrimsonDue
                        else -> SkyChart
                    },
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.customerName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = if (isPayment) {
                    "${bi("Joma Received", "বাকী জমা", mode)} · ${formatShortDate(tx.transactionDateMillis)}"
                } else {
                    "${tx.riceVariety} (${tx.quantity.toInt()} ${tx.unit}) · ${formatShortDate(tx.transactionDateMillis)}"
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isPayment) {
                        formatBdt(tx.paidAmount, showPositiveSign = true)
                    } else {
                        formatBdt(tx.totalAmount)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isPayment) ShouravColors.emeraldInk else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                if (hasDue) {
                    Text(
                        text = "-${formatBdt(tx.dueAdded)} ${bi("Due", "বাকী", mode)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CrimsonDue,
                        fontWeight = FontWeight.Medium,
                    )
                } else if (!isPayment) {
                    Text(
                        text = bi("Paid", "নগদ", mode),
                        style = MaterialTheme.typography.labelSmall,
                        color = ShouravColors.emeraldInk,
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onOpenSlip,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("lenden_slip_btn_${tx.id}"),
            ) {
                Icon(
                    imageVector = Icons.Filled.PictureAsPdf,
                    contentDescription = bi("Slip PDF", "রসিদ PDF", mode),
                    tint = ShouravColors.emeraldIconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun CustomerDirectoryRow(
    serialNumber: Int,
    summary: CustomerSummary,
    mode: LanguageMode,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("customer_card_${summary.customer.id}"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(1.dp, ShouravColors.emeraldBorder, CircleShape)
                            .testTag("customer_serial_$serialNumber"),
                    ) {
                        Text(
                            text = "#$serialNumber",
                            style = MaterialTheme.typography.labelLarge,
                            color = ShouravColors.emeraldInk,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Column {
                        Text(
                            text = summary.customer.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val meta = listOfNotNull(
                            summary.customer.shopAddress.takeIf { it.isNotBlank() },
                            summary.customer.phone.takeIf { it.isNotBlank() },
                        ).joinToString(" · ")
                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                BakiBadge(dueAmount = summary.currentDue, mode = mode)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = bi("Total Bought", "মোট ক্রয়", mode),
                        style = MaterialTheme.typography.labelSmall,
                        color = ShouravColors.subtleText,
                    )
                    Text(
                        text = formatBdt(summary.totalSold),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = bi("Total Paid", "মোট জমা", mode),
                        style = MaterialTheme.typography.labelSmall,
                        color = ShouravColors.subtleText,
                    )
                    Text(
                        text = formatBdt(summary.totalPaid),
                        style = MaterialTheme.typography.labelLarge,
                        color = ShouravColors.emeraldInk,
                    )
                }
            }
        }
    }
}

@Composable
fun CustomerDetailScreen(
    summary: CustomerSummary?,
    transactions: List<LedgerTransaction>,
    mode: LanguageMode,
    onBack: () -> Unit,
    onRecordPayment: (amount: Double, notes: String) -> Unit,
    onNewSaleForCustomer: (customerId: String) -> Unit,
    onSlipFeedback: (String) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    var showPaymentDialog by remember { mutableStateOf(false) }
    var activeSlipEntry by remember { mutableStateOf<CustomerTransactionEntry?>(null) }

    if (summary == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(bi("Customer not found", "ক্রেতা পাওয়া যায়নি", mode))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onBack) {
                    Text(bi("Go Back", "ফিরে যান", mode))
                }
            }
        }
        return
    }

    val timelineEntries = remember(transactions, summary.customer) {
        BusinessAggregator.buildCustomerLedgerTimeline(summary.customer, transactions)
    }

    if (showPaymentDialog) {
        RecordPaymentDialog(
            customerName = summary.customer.name,
            currentDue = summary.currentDue,
            mode = mode,
            onDismiss = { showPaymentDialog = false },
            onConfirm = { amt, note ->
                showPaymentDialog = false
                onRecordPayment(amt, note)
            },
        )
    }

    if (activeSlipEntry != null) {
        TransactionSlipDialog(
            slipData = TransactionSlipData(
                customer = summary.customer,
                entry = activeSlipEntry!!,
            ),
            mode = mode,
            onDismiss = { activeSlipEntry = null },
            onFeedback = onSlipFeedback,
        )
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .testTag("customer_detail_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("customer_detail_back_button"),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = summary.customer.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (summary.customer.phone.isNotBlank()) {
                                Icon(
                                    imageVector = Icons.Filled.Phone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                                Text(
                                    text = summary.customer.phone,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (summary.customer.shopAddress.isNotBlank()) {
                                Text(
                                    text = summary.customer.shopAddress,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // 3 Customer Summary Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CustomerStatBox(
                        title = bi("Total Sold", "মোট Sell", mode),
                        value = formatBdt(summary.totalSold),
                        color = SkyChart,
                        modifier = Modifier.weight(1f),
                    )
                    CustomerStatBox(
                        title = bi("Total Paid", "সে দিয়েছে", mode),
                        value = formatBdt(summary.totalPaid),
                        color = ShouravColors.emeraldInk,
                        modifier = Modifier.weight(1f),
                    )
                    CustomerStatBox(
                        title = bi("Net Due", "কত পাবো", mode),
                        value = formatBdt(summary.currentDue),
                        color = if (summary.currentDue > 0.5) CrimsonDue else ShouravColors.emeraldInk,
                        highlightBg = if (summary.currentDue > 0.5) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Direct Actions: Record Payment & New Sale
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { showPaymentDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("customer_record_payment_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Payments,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(bi("Record Payment", "জমা নিন", mode), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { onNewSaleForCustomer(summary.customer.id) },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ShouravColors.emeraldIconTint),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("customer_new_sale_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddShoppingCart,
                            contentDescription = null,
                            tint = ShouravColors.emeraldIconTint,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = bi("New Sale", "নতুন বিক্রি", mode),
                            color = ShouravColors.emeraldInk,
                            maxLines = 1,
                        )
                    }
                }
            }

            // Clear Khata Total Calculation Equation Card
            item {
                val periodSalesOnly = (summary.totalSold - summary.customer.openingBalance).coerceAtLeast(0.0)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, ShouravColors.emeraldBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_khata_equation_card"),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = bi("Khata Balance Calculation", "খাতার মোট হিসাব", mode),
                            style = MaterialTheme.typography.titleSmall,
                            color = ShouravColors.emeraldInk,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = bi("Previous Opening Due", "আগের জের / বাকী", mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = formatBdt(summary.customer.openingBalance),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = bi("+ New Sales Total", "+ নতুন মোট বিক্রি", mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "+${formatBdt(periodSalesOnly)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = bi("− Total Paid (Joma)", "− মোট জমা বাদ", mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "-${formatBdt(summary.totalPaid)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = ShouravColors.emeraldInk,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = bi("= Current Total Due", "= সর্বমোট বাকী", mode),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = formatBdt(summary.currentDue),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (summary.currentDue > 0.5) CrimsonDue else ShouravColors.emeraldInk,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = bi("Date-wise Khata History", "তারিখ অনুযায়ী খাতার বিবরণ", mode),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (timelineEntries.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Text(
                            text = bi("No sales or payments recorded yet.", "এখনো কোনো বিক্রি বা জমার হিসাব নেই।", mode),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                }
            } else {
                items(timelineEntries, key = { it.transaction.id }) { entry ->
                    CustomerTransactionRow(
                        entry = entry,
                        mode = mode,
                        onGenerateSlipPdf = { activeSlipEntry = entry },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun CustomerStatBox(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    highlightBg: Color = MaterialTheme.colorScheme.surface,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = highlightBg),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = color,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CustomerTransactionRow(
    entry: CustomerTransactionEntry,
    mode: LanguageMode,
    onGenerateSlipPdf: () -> Unit,
) {
    val tx = entry.transaction
    val isPayment = tx.type == LedgerTransaction.TYPE_PAYMENT
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isPayment) {
                        bi("Cash / Bank Payment (Joma)", "নগদ / ব্যাংক জমা", mode)
                    } else {
                        "${tx.riceVariety} (${tx.quantity.toInt()} ${tx.unit})"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isPayment) ShouravColors.emeraldInk else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatShortDate(tx.transactionDateMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = ShouravColors.subtleText,
                )
            }

            if (!isPayment) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${bi("Rate", "দর", mode)}: ${formatBdt(tx.sellingPricePerUnit)}/${tx.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "${bi("Bill", "বিল", mode)}: ${formatBdt(tx.totalAmount)} · ${bi("Paid", "নগদ", mode)}: ${formatBdt(tx.paidAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = bi("Joma Received", "জমা প্রাপ্তি", mode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatBdt(tx.paidAmount, showPositiveSign = true),
                        style = MaterialTheme.typography.titleSmall,
                        color = ShouravColors.emeraldInk,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Running Balance Strip (Previous Due +/- Entry = Total Due After)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = bi("Prev Due", "আগের বাকী", mode),
                            style = MaterialTheme.typography.labelSmall,
                            color = ShouravColors.subtleText,
                        )
                        Text(
                            text = formatBdt(entry.previousDue),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isPayment) bi("− Joma", "− জমা", mode) else bi("+ Sale Due", "+ নতুন বাকী", mode),
                            style = MaterialTheme.typography.labelSmall,
                            color = ShouravColors.subtleText,
                        )
                        val deltaDisplay = if (isPayment) {
                            "-${formatBdt(tx.paidAmount)}"
                        } else {
                            val saleDue = (tx.totalAmount - tx.paidAmount).coerceAtLeast(0.0)
                            "+${formatBdt(saleDue)}"
                        }
                        Text(
                            text = deltaDisplay,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isPayment || entry.deltaDue <= 0.5) ShouravColors.emeraldInk else CrimsonDue,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = bi("= Total Due", "= মোট বাকী", mode),
                            style = MaterialTheme.typography.labelSmall,
                            color = ShouravColors.subtleText,
                        )
                        Text(
                            text = formatBdt(entry.runningDueAfter),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (entry.runningDueAfter > 0.5) CrimsonDue else ShouravColors.emeraldInk,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            if (tx.notes.isNotBlank()) {
                Text(
                    text = tx.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = ShouravColors.subtleText,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = onGenerateSlipPdf,
                    border = BorderStroke(1.dp, ShouravColors.emeraldBorder),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("tx_slip_pdf_btn_${tx.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PictureAsPdf,
                        contentDescription = null,
                        tint = ShouravColors.emeraldIconTint,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isPayment) {
                            bi("Joma Slip PDF", "জমা রসিদ PDF", mode)
                        } else {
                            bi("Sale Slip PDF", "বিক্রয় মেমো PDF", mode)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = ShouravColors.emeraldInk,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
