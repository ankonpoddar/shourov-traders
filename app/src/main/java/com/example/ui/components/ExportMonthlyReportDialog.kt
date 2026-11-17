package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BankLoan
import com.example.data.model.BusinessExpense
import com.example.data.model.Customer
import com.example.data.model.LedgerTransaction
import com.example.data.preferences.LanguageMode
import com.example.data.preferences.bi
import com.example.data.preferences.formatBdt
import com.example.data.report.MonthlyPdfExporter
import com.example.data.report.MonthlyReportBuilder
import com.example.ui.theme.CrimsonDue
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ShouravColors
import kotlinx.coroutines.launch

@Composable
fun ExportMonthlyReportDialog(
    customers: List<Customer>,
    transactions: List<LedgerTransaction>,
    expenses: List<BusinessExpense>,
    loans: List<BankLoan>,
    mode: LanguageMode,
    onDismiss: () -> Unit,
    onExportFeedback: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val monthOptions = remember(transactions, expenses) {
        MonthlyReportBuilder.availableMonths(transactions, expenses)
    }
    var selectedMonth by remember(monthOptions) {
        mutableStateOf(monthOptions.first())
    }
    val reportData = remember(selectedMonth, customers, transactions, expenses, loans) {
        MonthlyReportBuilder.buildReport(
            period = selectedMonth,
            customers = customers,
            transactions = transactions,
            expenses = expenses,
            loans = loans,
        )
    }

    var isWorking by remember { mutableStateOf(false) }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    isWorking = true
                    val res = MonthlyPdfExporter.savePdfToUri(context, uri, reportData)
                    isWorking = false
                    res.fold(
                        onSuccess = {
                            onExportFeedback(
                                bi(
                                    "PDF report saved for ${selectedMonth.displayLabel}",
                                    "${selectedMonth.displayLabel}-এর PDF রিপোর্ট সেভ হয়েছে",
                                    mode,
                                )
                            )
                            onDismiss()
                        },
                        onFailure = { err ->
                            onExportFeedback(err.localizedMessage ?: "Failed to save PDF")
                        },
                    )
                }
            }
        },
    )

    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PictureAsPdf,
                    contentDescription = null,
                    tint = ShouravColors.emeraldIconTint,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = bi("Export Monthly PDF Report", "মাসিক PDF রিপোর্ট", mode),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = bi(
                        "Select month to export Baki Khata credit ledger & profit report offline:",
                        "বাকী খাতা ও নিট লাভের PDF রিপোর্টের মাস নির্বাচন করুন:",
                        mode,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    monthOptions.forEach { option ->
                        val selected = option.fileSlug == selectedMonth.fileSlug
                        FilterChip(
                            selected = selected,
                            onClick = { selectedMonth = option },
                            label = { Text(option.displayLabel, maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = ShouravColors.emeraldInk,
                            ),
                            modifier = Modifier.testTag("export_month_chip_${option.fileSlug}"),
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PreviewStatRow(
                            label = bi("Month Sales", "মাসের বিক্রি", mode),
                            value = "${formatBdt(reportData.monthlySalesTotal)} (${reportData.monthlySaleCount})",
                            valueColor = MaterialTheme.colorScheme.onSurface,
                        )
                        PreviewStatRow(
                            label = bi("Total Baki Due", "মোট পাবো / বাকী", mode),
                            value = formatBdt(reportData.totalReceivableDue),
                            valueColor = if (reportData.totalReceivableDue > 0.5) CrimsonDue else ShouravColors.emeraldInk,
                        )
                        PreviewStatRow(
                            label = bi("Month Expenses + Interest", "মাসের খরচ ও সুদ", mode),
                            value = formatBdt(reportData.monthlyTotalExpenses),
                            valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        PreviewStatRow(
                            label = bi("Month Net Profit", "মাসের নিট লাভ", mode),
                            value = formatBdt(reportData.monthlyNetProfit, showPositiveSign = reportData.monthlyNetProfit > 0.0),
                            valueColor = if (reportData.monthlyNetProfit >= 0.0) ShouravColors.emeraldInk else CrimsonDue,
                            bold = true,
                        )
                        Text(
                            text = "${reportData.customerLedgerRows.size} ${bi("customers", "জন ক্রেতা", mode)} · ${reportData.transactionsInMonth.size} ${bi("entries", "টি এন্ট্রি", mode)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (isWorking) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = ShouravColors.emeraldIconTint,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = bi("Generating PDF...", "PDF তৈরি হচ্ছে...", mode),
                            style = MaterialTheme.typography.bodySmall,
                            color = ShouravColors.emeraldInk,
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                isWorking = true
                                val res = MonthlyPdfExporter.sharePdf(context, reportData)
                                isWorking = false
                                res.fold(
                                    onSuccess = { onDismiss() },
                                    onFailure = { err ->
                                        onExportFeedback(err.localizedMessage ?: "Unable to share PDF")
                                    },
                                )
                            }
                        },
                        enabled = !isWorking,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("export_pdf_share_btn"),
                    ) {
                        Icon(imageVector = Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(bi("Share PDF (Offline Ready)", "PDF শেয়ার করুন", mode), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                createPdfLauncher.launch(MonthlyPdfExporter.suggestedFileName(selectedMonth))
                            },
                            enabled = !isWorking,
                            border = BorderStroke(1.dp, ShouravColors.emeraldBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("export_pdf_save_btn"),
                        ) {
                            Icon(imageVector = Icons.Filled.SaveAlt, contentDescription = null, tint = ShouravColors.emeraldInk, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(bi("Save", "সেভ", mode), color = ShouravColors.emeraldInk, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                val res = MonthlyPdfExporter.printPdf(context, reportData)
                                res.fold(
                                    onSuccess = { onDismiss() },
                                    onFailure = { err ->
                                        onExportFeedback(err.localizedMessage ?: "Print failed")
                                    },
                                )
                            },
                            enabled = !isWorking,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("export_pdf_print_btn"),
                        ) {
                            Icon(imageVector = Icons.Filled.Print, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(bi("Print", "প্রিন্ট", mode), color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isWorking,
            ) {
                Text(bi("Close", "বন্ধ করুন", mode))
            }
        },
    )
}

@Composable
private fun PreviewStatRow(
    label: String,
    value: String,
    valueColor: Color,
    bold: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = if (bold) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
            color = valueColor,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}
