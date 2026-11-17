package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.Customer
import com.example.data.model.LedgerTransaction
import com.example.data.model.RiceCatalog
import com.example.data.preferences.LanguageMode
import com.example.data.preferences.bi
import com.example.data.preferences.formatBdt
import com.example.data.preferences.formatEditableNumber
import com.example.data.preferences.parseLocalizedDouble
import com.example.ui.theme.CrimsonDue
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ShouravColors

@Composable
fun DailySaleScreen(
    customers: List<Customer>,
    preselectedCustomerId: String?,
    mode: LanguageMode,
    isBusy: Boolean,
    onSaveSale: (
        customerId: String,
        customerName: String,
        riceVariety: String,
        unit: String,
        quantity: Double,
        costPricePerUnit: Double,
        sellingPricePerUnit: Double,
        paidAmount: Double,
        notes: String,
    ) -> Unit,
    onAddCustomerClick: () -> Unit,
    customerDueMap: Map<String, Double> = emptyMap(),
) {
    var selectedCustomerId by remember { mutableStateOf(preselectedCustomerId ?: "") }
    var isWalkIn by remember { mutableStateOf(preselectedCustomerId == null) }
    var customerMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(preselectedCustomerId) {
        if (!preselectedCustomerId.isNullOrBlank()) {
            selectedCustomerId = preselectedCustomerId
            isWalkIn = false
        }
    }

    var riceVariety by remember { mutableStateOf("Miniket") }
    var unit by remember { mutableStateOf(LedgerTransaction.UNIT_BAG) }
    var qtyText by remember { mutableStateOf("10") }
    var costText by remember { mutableStateOf("3350") }
    var sellText by remember { mutableStateOf("3600") }
    var paidText by remember { mutableStateOf("36000") }
    var notes by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val sortedCustomers = remember(customers) {
        customers.sortedBy { it.name.trim().lowercase() }
    }
    val selectedCustomer = sortedCustomers.firstOrNull { it.id == selectedCustomerId }
    val previousDue = if (!isWalkIn && selectedCustomer != null) {
        customerDueMap[selectedCustomer.id] ?: selectedCustomer.openingBalance
    } else {
        0.0
    }
    val qty = parseLocalizedDouble(qtyText)?.coerceAtLeast(0.0) ?: 0.0
    val cost = parseLocalizedDouble(costText)?.coerceAtLeast(0.0) ?: 0.0
    val sell = parseLocalizedDouble(sellText)?.coerceAtLeast(0.0) ?: 0.0
    val totalBill = qty * sell

    LaunchedEffect(isWalkIn, totalBill) {
        if (isWalkIn) {
            paidText = formatEditableNumber(totalBill)
        }
    }

    val paid = (parseLocalizedDouble(paidText) ?: 0.0).coerceIn(0.0, totalBill)
    val remainingDue = (totalBill - paid).coerceAtLeast(0.0)
    val newTotalCustomerDue = previousDue + remainingDue
    val grossProfit = (sell - cost) * qty

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp)
                .testTag("daily_sale_list"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = bi("Daily Rice Sale Entry", "দৈনিক চাল বিক্রি এন্ট্রি", mode),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = bi(
                        "Auto-calculates gross profit and updates customer Baki Khata.",
                        "বিক্রি থেকে লাভ ও ক্রেতার বাকী স্বয়ংক্রিয়ভাবে আপডেট হয়।",
                        mode,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Customer Selection
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = bi("Buyer / Customer", "ক্রেতা নির্বাচন", mode),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            TextButton(onClick = onAddCustomerClick) {
                                Text(bi("+ New Customer", "+ নতুন ক্রেতা", mode))
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = isWalkIn,
                                onClick = {
                                    isWalkIn = true
                                    selectedCustomerId = ""
                                    paidText = formatEditableNumber(totalBill)
                                },
                                label = { Text(bi("Walk-in Cash", "নগদ ক্রেতা", mode)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = ShouravColors.emeraldInk,
                                ),
                            )
                            FilterChip(
                                selected = !isWalkIn,
                                onClick = {
                                    isWalkIn = false
                                    if (selectedCustomerId.isBlank() && sortedCustomers.isNotEmpty()) {
                                        selectedCustomerId = sortedCustomers.first().id
                                    }
                                },
                                label = { Text(bi("Khata Customer", "খাতার ক্রেতা", mode)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = ShouravColors.emeraldInk,
                                ),
                            )
                        }

                        if (!isWalkIn) {
                            Box {
                                OutlinedButton(
                                    onClick = { customerMenuExpanded = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("sale_customer_dropdown"),
                                ) {
                                    Text(
                                        text = selectedCustomer?.name
                                            ?: bi("Select a customer...", "ক্রেতা বাছাই করুন...", mode),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Icon(imageVector = Icons.Filled.ArrowDropDown, contentDescription = null)
                                }
                                DropdownMenu(
                                    expanded = customerMenuExpanded,
                                    onDismissRequest = { customerMenuExpanded = false },
                                ) {
                                    if (sortedCustomers.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text(bi("No customers yet — add one", "কোনো ক্রেতা নেই", mode)) },
                                            onClick = {
                                                customerMenuExpanded = false
                                                onAddCustomerClick()
                                            },
                                        )
                                    } else {
                                        sortedCustomers.forEachIndexed { index, customer ->
                                            val due = customerDueMap[customer.id] ?: customer.openingBalance
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        Text("${index + 1}. ${customer.name}")
                                                        if (due > 0.5) {
                                                            Text(
                                                                text = formatBdt(due),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = CrimsonDue,
                                                                modifier = Modifier.padding(start = 12.dp),
                                                            )
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    selectedCustomerId = customer.id
                                                    customerMenuExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Rice Variety & Quantities
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = bi("Rice Variety & Pricing", "চালের জাত ও দর", mode),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RiceCatalog.varieties.forEach { (en, bn) ->
                                FilterChip(
                                    selected = riceVariety.equals(en, ignoreCase = true),
                                    onClick = { riceVariety = en },
                                    label = { Text(bi(en, bn, mode)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = ShouravColors.emeraldInk,
                                    ),
                                )
                            }
                        }
                        OutlinedTextField(
                            value = riceVariety,
                            onValueChange = { riceVariety = it },
                            label = { Text(bi("Rice Variety Name", "চালের নাম", mode)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = bi("Unit:", "একক:", mode),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FilterChip(
                                selected = unit == LedgerTransaction.UNIT_BAG,
                                onClick = { unit = LedgerTransaction.UNIT_BAG },
                                label = { Text(bi("Bag", "বস্তা", mode)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = ShouravColors.emeraldInk,
                                ),
                            )
                            FilterChip(
                                selected = unit == LedgerTransaction.UNIT_KG,
                                onClick = { unit = LedgerTransaction.UNIT_KG },
                                label = { Text(bi("Kg", "কেজি", mode)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = ShouravColors.emeraldInk,
                                ),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedTextField(
                                value = qtyText,
                                onValueChange = {
                                    qtyText = it
                                    validationError = null
                                },
                                label = { Text(bi("Quantity", "পরিমাণ", mode)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sale_qty_input"),
                            )
                            OutlinedTextField(
                                value = costText,
                                onValueChange = { costText = it },
                                label = { Text(bi("Cost / Unit", "কেনা দর", mode)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedTextField(
                                value = sellText,
                                onValueChange = {
                                    sellText = it
                                    validationError = null
                                },
                                label = { Text(bi("Sell Price / Unit", "বেচা দর", mode)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sale_sell_price_input"),
                            )
                            OutlinedTextField(
                                value = paidText,
                                onValueChange = { paidText = it },
                                label = { Text(bi("Paid Now (৳)", "নগদ জমা (৳)", mode)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sale_paid_input"),
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { paidText = formatEditableNumber(totalBill) }) {
                                Text(bi("Full Paid", "পুরো নগদ", mode))
                            }
                            if (!isWalkIn) {
                                TextButton(onClick = { paidText = "0" }) {
                                    Text(bi("Full Baki (Due)", "পুরো বাকী", mode), color = CrimsonDue)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text(bi("Notes (Optional)", "মন্তব্য (ঐচ্ছিক)", mode)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // Live Calculation Preview Card
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, ShouravColors.emeraldBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(bi("Total Bill", "মোট বিল", mode), color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                text = formatBdt(totalBill),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(bi("Gross Profit", "এই বিক্রিতে লাভ", mode), color = ShouravColors.emeraldInk)
                            Text(
                                text = formatBdt(grossProfit, showPositiveSign = true),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (grossProfit >= 0.0) ShouravColors.emeraldInk else CrimsonDue,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(bi("This Sale Due (Baki)", "এই বিক্রির বাকী", mode), color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                text = formatBdt(remainingDue),
                                style = MaterialTheme.typography.titleSmall,
                                color = if (remainingDue > 0.5) CrimsonDue else ShouravColors.emeraldInk,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (!isWalkIn && selectedCustomer != null) {
                            HorizontalDivider(color = ShouravColors.emeraldBorder)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(bi("Previous Due", "আগের বাকী", mode), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = formatBdt(previousDue),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(bi("+ New Sale Due", "+ নতুন বাকী যোগ", mode), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "+${formatBdt(remainingDue)}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (remainingDue > 0.5) CrimsonDue else ShouravColors.emeraldInk,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = bi("New Total Due", "সর্বমোট বাকী হবে", mode),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = formatBdt(newTotalCustomerDue),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (newTotalCustomerDue > 0.5) CrimsonDue else ShouravColors.emeraldInk,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("sale_new_total_due"),
                                )
                            }
                        }
                    }
                }
            }

            if (validationError != null) {
                item {
                    Text(
                        text = validationError.orEmpty(),
                        color = CrimsonDue,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        when {
                            !isWalkIn && selectedCustomer == null -> {
                                validationError = bi("Select a Khata customer or choose Walk-in Cash.", "ক্রেতা নির্বাচন করুন অথবা নগদ ক্রেতা দিন।", mode)
                            }
                            qty <= 0.0 || sell <= 0.0 -> {
                                validationError = bi("Quantity and selling price must be greater than zero.", "পরিমাণ ও বিক্রয় মূল্য ০-এর বেশি হতে হবে।", mode)
                            }
                            riceVariety.isBlank() -> {
                                validationError = bi("Enter a rice variety name.", "চালের নাম লিখুন।", mode)
                            }
                            else -> {
                                val targetId = if (isWalkIn) "" else selectedCustomer!!.id
                                val targetName = if (isWalkIn) "Walk-in Cash Customer" else selectedCustomer!!.name
                                val effectivePaid = if (isWalkIn) totalBill else paid
                                onSaveSale(
                                    targetId,
                                    targetName,
                                    riceVariety.trim(),
                                    unit,
                                    qty,
                                    cost,
                                    sell,
                                    effectivePaid,
                                    notes.trim(),
                                )
                            }
                        }
                    },
                    enabled = !isBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_sale_button"),
                ) {
                    Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = bi("Save Sale Entry", "বিক্রির হিসাব সংরক্ষণ করুন", mode),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
