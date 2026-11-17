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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.model.BankLoan
import com.example.data.model.BusinessExpense
import com.example.data.model.RiceCatalog
import com.example.data.preferences.LanguageMode
import com.example.data.preferences.bi
import com.example.data.preferences.formatBdt
import com.example.data.preferences.formatEditableNumber
import com.example.data.preferences.formatShortDate
import com.example.data.preferences.parseLocalizedDouble
import com.example.ui.theme.CrimsonDue
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.HarvestAmber
import com.example.ui.theme.ShouravColors
import com.example.ui.viewmodel.BusinessUiState

@Composable
fun ExpenseLoanScreen(
    state: BusinessUiState.Ready,
    mode: LanguageMode,
    isBusy: Boolean,
    onAddExpense: (frequency: String, category: String, amount: Double, notes: String) -> Unit,
    onDeleteExpense: (expenseId: String) -> Unit,
    onAddLoan: (lenderName: String, principal: Double, rate: Double, notes: String) -> Unit,
    onRecordInstallment: (loan: BankLoan, principalPaid: Double) -> Unit,
    onDeleteLoan: (loanId: String) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var installmentLoan by remember { mutableStateOf<BankLoan?>(null) }

    if (installmentLoan != null) {
        LoanInstallmentDialog(
            loan = installmentLoan!!,
            mode = mode,
            onDismiss = { installmentLoan = null },
            onConfirm = { principalPaid ->
                val target = installmentLoan!!
                installmentLoan = null
                onRecordInstallment(target, principalPaid)
            },
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = ShouravColors.emeraldIconTint,
            modifier = Modifier.widthIn(max = 760.dp),
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(bi("Daily", "দৈনিক খরচ", mode), maxLines = 1) },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(bi("Monthly", "মাসিক খরচ", mode), maxLines = 1) },
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text(bi("Bank Loan", "ব্যাংক লোন", mode), maxLines = 1) },
            )
        }

        when (selectedTab) {
            0 -> ExpenseTabContent(
                frequency = BusinessExpense.FREQ_DAILY,
                categories = RiceCatalog.dailyExpenseCategories,
                expenses = state.expenses.filter { it.frequency == BusinessExpense.FREQ_DAILY },
                totalAmount = state.summary.dailyExpensesTotal,
                mode = mode,
                isBusy = isBusy,
                onAddExpense = onAddExpense,
                onDeleteExpense = onDeleteExpense,
            )

            1 -> ExpenseTabContent(
                frequency = BusinessExpense.FREQ_MONTHLY,
                categories = RiceCatalog.monthlyExpenseCategories,
                expenses = state.expenses.filter { it.frequency == BusinessExpense.FREQ_MONTHLY },
                totalAmount = state.summary.monthlyExpensesTotal,
                mode = mode,
                isBusy = isBusy,
                onAddExpense = onAddExpense,
                onDeleteExpense = onDeleteExpense,
            )

            else -> BankLoanTabContent(
                loans = state.loans,
                monthlyInterestTotal = state.summary.monthlyLoanInterest,
                mode = mode,
                isBusy = isBusy,
                onAddLoan = onAddLoan,
                onLogInstallment = { installmentLoan = it },
                onDeleteLoan = onDeleteLoan,
            )
        }
    }
}

@Composable
private fun ExpenseTabContent(
    frequency: String,
    categories: List<Pair<String, String>>,
    expenses: List<BusinessExpense>,
    totalAmount: Double,
    mode: LanguageMode,
    isBusy: Boolean,
    onAddExpense: (frequency: String, category: String, amount: Double, notes: String) -> Unit,
    onDeleteExpense: (expenseId: String) -> Unit,
) {
    var selectedCategory by remember(frequency) { mutableStateOf(categories.first().first) }
    var amountText by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 760.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                border = BorderStroke(1.dp, ShouravColors.amberBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (frequency == BusinessExpense.FREQ_DAILY) {
                            bi("Total Daily Expenses", "মোট দৈনিক খরচ", mode)
                        } else {
                            bi("Total Monthly Fixed Bills", "মোট মাসিক খরচ", mode)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = formatBdt(totalAmount),
                        style = MaterialTheme.typography.titleMedium,
                        color = HarvestAmber,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

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
                    Text(
                        text = bi("Log Expense", "নতুন খরচ লিখুন", mode),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        categories.forEach { (en, bn) ->
                            FilterChip(
                                selected = selectedCategory == en,
                                onClick = { selectedCategory = en },
                                label = { Text(bi(en, bn, mode)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = ShouravColors.emeraldInk,
                                ),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = { selectedCategory = it },
                        label = { Text(bi("Category", "খরচের খাত", mode)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = {
                            amountText = it
                            error = null
                        },
                        label = { Text(bi("Amount (৳)", "পরিমাণ (৳)", mode)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("expense_amount_input"),
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(bi("Notes / Receipt Detail", "বিবরণ", mode)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (error != null) {
                        Text(text = error.orEmpty(), color = CrimsonDue, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = {
                            val amt = parseLocalizedDouble(amountText)
                            if (amt == null || amt <= 0.0) {
                                error = bi("Enter a valid positive amount", "সঠিক টাকার পরিমাণ দিন", mode)
                            } else {
                                onAddExpense(frequency, selectedCategory, amt, notes)
                                amountText = ""
                                notes = ""
                            }
                        },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_expense_button"),
                    ) {
                        Text(bi("Save Expense", "খরচ সংরক্ষণ", mode))
                    }
                }
            }
        }

        items(expenses, key = { it.id }) { exp ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = exp.category,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val sub = listOfNotNull(
                            formatShortDate(exp.expenseDateMillis),
                            exp.notes.takeIf { it.isNotBlank() },
                        ).joinToString(" · ")
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = formatBdt(exp.amount),
                        style = MaterialTheme.typography.titleSmall,
                        color = HarvestAmber,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = { onDeleteExpense(exp.id) }) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete expense",
                            tint = ShouravColors.subtleText,
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun BankLoanTabContent(
    loans: List<BankLoan>,
    monthlyInterestTotal: Double,
    mode: LanguageMode,
    isBusy: Boolean,
    onAddLoan: (lenderName: String, principal: Double, rate: Double, notes: String) -> Unit,
    onLogInstallment: (BankLoan) -> Unit,
    onDeleteLoan: (String) -> Unit,
) {
    var bankName by remember { mutableStateOf("") }
    var principalText by remember { mutableStateOf("") }
    var rateText by remember { mutableStateOf("13.0") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val previewPrincipal = parseLocalizedDouble(principalText)?.coerceAtLeast(0.0) ?: 0.0
    val previewRate = parseLocalizedDouble(rateText)?.coerceIn(0.0, 100.0) ?: 13.0
    val previewAnnualInt = previewPrincipal * (previewRate / 100.0)
    val previewMonthlyInt = previewAnnualInt / 12.0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 760.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                border = BorderStroke(1.dp, ShouravColors.amberBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bi("Monthly Interest Burden", "মাসিক ব্যাংক সুদ", mode),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = bi("Automatically deducted from Dashboard Net Profit", "ড্যাশবোর্ড নিট লাভ থেকে স্বয়ংক্রিয়ভাবে বাদ দেওয়া হয়", mode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatBdt(monthlyInterestTotal),
                        style = MaterialTheme.typography.titleMedium,
                        color = CrimsonDue,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountBalance,
                            contentDescription = null,
                            tint = ShouravColors.emeraldIconTint,
                        )
                        Text(
                            text = bi("Add Bank Loan & 13% Calculator", "ব্যাংক লোন ও ১৩% সুদ হিসাব", mode),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    OutlinedTextField(
                        value = bankName,
                        onValueChange = {
                            bankName = it
                            error = null
                        },
                        label = { Text(bi("Bank / Lender Name", "ব্যাংক / প্রতিষ্ঠানের নাম", mode)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("loan_bank_input"),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedTextField(
                            value = principalText,
                            onValueChange = {
                                principalText = it
                                error = null
                            },
                            label = { Text(bi("Principal (৳)", "আসল টাকা (৳)", mode)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.4f)
                                .testTag("loan_principal_input"),
                        )
                        OutlinedTextField(
                            value = rateText,
                            onValueChange = { rateText = it },
                            label = { Text(bi("Rate (% p.a.)", "সুদ (%)", mode)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    if (previewPrincipal > 0.0) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "${bi("Annual", "বার্ষিক", mode)}: ${formatBdt(previewAnnualInt)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ShouravColors.emeraldInk,
                                )
                                Text(
                                    text = "${bi("Monthly", "মাসিক", mode)}: ${formatBdt(previewMonthlyInt)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ShouravColors.emeraldInk,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(bi("Notes (Optional)", "মন্তব্য", mode)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (error != null) {
                        Text(text = error.orEmpty(), color = CrimsonDue, style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = {
                            val principal = parseLocalizedDouble(principalText)
                            val rate = parseLocalizedDouble(rateText) ?: 13.0
                            when {
                                bankName.isBlank() -> {
                                    error = bi("Enter bank or lender name", "ব্যাংকের নাম দিন", mode)
                                }
                                principal == null || principal <= 0.0 -> {
                                    error = bi("Enter valid principal amount", "সঠিক আসল পরিমাণ দিন", mode)
                                }
                                else -> {
                                    onAddLoan(bankName, principal, rate, notes)
                                    bankName = ""
                                    principalText = ""
                                    rateText = "13.0"
                                    notes = ""
                                }
                            }
                        },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_loan_button"),
                    ) {
                        Text(bi("Save Bank Loan", "লোন যোগ করুন", mode))
                    }
                }
            }
        }

        items(loans, key = { it.id }) { loan ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = loan.lenderName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${loan.annualInterestRate}% p.a. · ${bi("Started", "শুরু", mode)} ${formatShortDate(loan.startDateMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onDeleteLoan(loan.id) }) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete loan",
                                tint = ShouravColors.subtleText,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Text(bi("Remaining Principal", "বাকি আসল", mode), style = MaterialTheme.typography.labelSmall, color = ShouravColors.subtleText)
                            Text(formatBdt(loan.remainingPrincipal), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(bi("Annual Int.", "বার্ষিক সুদ", mode), style = MaterialTheme.typography.labelSmall, color = ShouravColors.subtleText)
                            Text(formatBdt(loan.annualInterest), style = MaterialTheme.typography.titleSmall, color = HarvestAmber)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(bi("Monthly Int.", "মাসিক সুদ", mode), style = MaterialTheme.typography.labelSmall, color = ShouravColors.subtleText)
                            Text(formatBdt(loan.monthlyInterest), style = MaterialTheme.typography.titleSmall, color = CrimsonDue, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${bi("Installments Paid", "পরিশোধিত কিস্তি", mode)}: ${loan.paidInstallmentsCount} (${formatBdt(loan.totalPrincipalRepaid)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = ShouravColors.emeraldInk,
                        )
                        Button(
                            onClick = { onLogInstallment(loan) },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(imageVector = Icons.Filled.Payments, contentDescription = null, modifier = Modifier.width(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(bi("Pay Installment", "কিস্তি দিন", mode), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun LoanInstallmentDialog(
    loan: BankLoan,
    mode: LanguageMode,
    onDismiss: () -> Unit,
    onConfirm: (principalPaid: Double) -> Unit,
) {
    var principalPaidText by remember(loan.id) {
        val suggested = if (loan.remainingPrincipal > 0.0) {
            minOf(25000.0, loan.remainingPrincipal)
        } else {
            0.0
        }
        mutableStateOf(if (suggested > 0.0) formatEditableNumber(suggested) else "")
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(bi("Log Loan Installment", "লোনের কিস্তি পরিশোধ", mode)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${loan.lenderName}\n${bi("Remaining Principal", "বাকি আসল", mode)}: ${formatBdt(loan.remainingPrincipal)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = principalPaidText,
                    onValueChange = {
                        principalPaidText = it
                        error = null
                    },
                    label = { Text(bi("Principal Repaid (৳)", "আসল পরিশোধ (৳)", mode)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(text = error.orEmpty(), color = CrimsonDue, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val paid = parseLocalizedDouble(principalPaidText)
                    if (paid == null || paid <= 0.0) {
                        error = bi("Enter a valid positive amount", "সঠিক পরিমাণ লিখুন", mode)
                    } else {
                        onConfirm(paid)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
            ) {
                Text(bi("Confirm", "নিশ্চিত করুন", mode))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(bi("Cancel", "বাতিল", mode))
            }
        },
    )
}
