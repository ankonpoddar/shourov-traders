package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.example.data.model.LedgerTransaction
import com.example.data.preferences.LanguageMode
import com.example.data.preferences.bi
import com.example.data.preferences.formatBdt
import com.example.data.preferences.formatShortDate
import com.example.data.report.TransactionSlipData
import com.example.data.report.TransactionSlipPdfExporter
import com.example.ui.theme.CrimsonDue
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ShouravColors
import kotlinx.coroutines.launch

@Composable
fun TransactionSlipDialog(
    slipData: TransactionSlipData,
    mode: LanguageMode,
    onDismiss: () -> Unit,
    onFeedback: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isWorking by remember { mutableStateOf(false) }

    val entry = slipData.entry
    val tx = entry.transaction
    val isPayment = tx.type == LedgerTransaction.TYPE_PAYMENT

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    isWorking = true
                    val res = TransactionSlipPdfExporter.saveSlipToUri(context, uri, slipData)
                    isWorking = false
                    res.fold(
                        onSuccess = {
                            onFeedback(
                                bi(
                                    "Slip PDF saved for ${tx.customerName}",
                                    "${tx.customerName}-এর রসিদ PDF সেভ হয়েছে",
                                    mode,
                                )
                            )
                            onDismiss()
                        },
                        onFailure = { err ->
                            onFeedback(err.localizedMessage ?: "Failed to save slip PDF")
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
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = if (isPayment) {
                        bi("Payment Receipt PDF", "জমা রসিদ PDF", mode)
                    } else {
                        bi("Sale Memo Slip PDF", "বিক্রয় মেমো রসিদ PDF", mode)
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
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
                        Text(
                            text = tx.customerName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = formatShortDate(tx.transactionDateMillis),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        if (isPayment) {
                            SlipPreviewRow(
                                label = bi("Joma / Payment Paid", "জমা প্রাপ্তি", mode),
                                value = formatBdt(tx.paidAmount),
                                valueColor = ShouravColors.emeraldInk,
                                bold = true,
                            )
                        } else {
                            SlipPreviewRow(
                                label = bi("Item", "বিবরণ", mode),
                                value = "${tx.riceVariety} (${tx.quantity.toInt()} ${tx.unit})",
                                valueColor = MaterialTheme.colorScheme.onSurface,
                            )
                            SlipPreviewRow(
                                label = bi("Total Bill", "মোট বিল", mode),
                                value = formatBdt(tx.totalAmount),
                                valueColor = MaterialTheme.colorScheme.onSurface,
                                bold = true,
                            )
                            SlipPreviewRow(
                                label = bi("Paid Now (Nagad)", "নগদ জমা", mode),
                                value = formatBdt(tx.paidAmount),
                                valueColor = ShouravColors.emeraldInk,
                            )
                        }
                    }
                }

                // Khata Balance Summary Box
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (ShouravColors.isDark) 0.7f else 0.45f),
                    border = BorderStroke(1.dp, ShouravColors.emeraldBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = bi("Khata Balance Summary", "খাতার হিসাব সারসংক্ষেপ", mode),
                            style = MaterialTheme.typography.labelMedium,
                            color = ShouravColors.emeraldInk,
                            fontWeight = FontWeight.Bold,
                        )
                        SlipPreviewRow(
                            label = bi("Previous Due", "আগের বাকী", mode),
                            value = formatBdt(entry.previousDue),
                            valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (isPayment) {
                            SlipPreviewRow(
                                label = bi("Less: Joma Paid", "বাদ: জমা", mode),
                                value = "-${formatBdt(tx.paidAmount)}",
                                valueColor = ShouravColors.emeraldInk,
                                bold = true,
                            )
                        } else {
                            val saleDue = (tx.totalAmount - tx.paidAmount).coerceAtLeast(0.0)
                            SlipPreviewRow(
                                label = bi("Plus: Sale Due", "যোগ: নতুন বাকী", mode),
                                value = "+${formatBdt(saleDue)}",
                                valueColor = if (saleDue > 0.5) CrimsonDue else ShouravColors.emeraldInk,
                                bold = true,
                            )
                        }
                        HorizontalDivider(color = ShouravColors.emeraldBorder)
                        SlipPreviewRow(
                            label = bi("Total Due Balance", "সর্বমোট বাকী", mode),
                            value = if (entry.runningDueAfter > 0.5) formatBdt(entry.runningDueAfter) else "৳ 0",
                            valueColor = if (entry.runningDueAfter > 0.5) CrimsonDue else ShouravColors.emeraldInk,
                            bold = true,
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
                            text = bi("Creating slip PDF...", "রসিদ PDF তৈরি হচ্ছে...", mode),
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
                                val res = TransactionSlipPdfExporter.shareSlipPdf(context, slipData)
                                isWorking = false
                                res.fold(
                                    onSuccess = { onDismiss() },
                                    onFailure = { err ->
                                        onFeedback(err.localizedMessage ?: "Unable to share slip PDF")
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
                            .testTag("slip_share_btn"),
                    ) {
                        Icon(imageVector = Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = bi("Share Slip PDF", "রসিদ PDF শেয়ার করুন", mode),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                savePdfLauncher.launch(TransactionSlipPdfExporter.suggestedFileName(slipData))
                            },
                            enabled = !isWorking,
                            border = BorderStroke(1.dp, ShouravColors.emeraldBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("slip_save_btn"),
                        ) {
                            Icon(imageVector = Icons.Filled.SaveAlt, contentDescription = null, tint = ShouravColors.emeraldInk, modifier = Modifier.size(17.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(bi("Save", "সেভ", mode), color = ShouravColors.emeraldInk, maxLines = 1)
                        }

                        OutlinedButton(
                            onClick = {
                                val res = TransactionSlipPdfExporter.printSlipPdf(context, slipData)
                                res.fold(
                                    onSuccess = { onDismiss() },
                                    onFailure = { err ->
                                        onFeedback(err.localizedMessage ?: "Print failed")
                                    },
                                )
                            },
                            enabled = !isWorking,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("slip_print_btn"),
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
            TextButton(onClick = onDismiss, enabled = !isWorking) {
                Text(bi("Close", "বন্ধ করুন", mode))
            }
        },
    )
}

@Composable
private fun SlipPreviewRow(
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
