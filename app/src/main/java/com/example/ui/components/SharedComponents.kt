package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.TrendPoint
import com.example.data.preferences.LanguageMode
import com.example.data.preferences.bi
import com.example.data.preferences.formatBdt
import com.example.data.preferences.formatEditableNumber
import com.example.data.preferences.parseLocalizedDouble
import com.example.ui.theme.CrimsonDue
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ShouravColors
import com.example.ui.theme.SkyChart
import kotlin.math.max

@Composable
fun LanguageSwitcherPill(
    currentMode: LanguageMode,
    onSelectMode: (LanguageMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LanguageMode.entries.forEach { mode ->
                val selected = mode == currentMode
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) EmeraldPrimary else Color.Transparent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onSelectMode(mode) }
                        .testTag("lang_toggle_${mode.name.lowercase()}"),
                ) {
                    Text(
                        text = mode.shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun KpiMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
) {
    val cardModifier = if (onClick != null) {
        modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    } else {
        modifier
    }
    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(containerColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = valueColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = ShouravColors.subtleText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun BakiBadge(
    dueAmount: Double,
    mode: LanguageMode,
) {
    val hasDue = dueAmount > 0.5
    val bg = if (hasDue) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val border = if (hasDue) ShouravColors.crimsonBorder else ShouravColors.emeraldBorder
    val fg = if (hasDue) CrimsonDue else ShouravColors.emeraldInk
    val label = if (hasDue) {
        "${bi("Due", "বাকী", mode)}: ${formatBdt(dueAmount)}"
    } else {
        bi("Cleared", "পরিশোধিত", mode)
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun SalesProfitTrendChart(
    points: List<TrendPoint>,
    mode: LanguageMode,
    modifier: Modifier = Modifier,
) {
    val hasAnyValue = points.any { it.sales > 0.0 || it.netProfit != 0.0 }
    if (!hasAnyValue) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = bi(
                    "Log sales or load sample data to view trends",
                    "বিক্রির হিসাব দেখতে সেল যোগ করুন বা নমুনা ডাটা লোড করুন",
                    mode,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }

    val maxVal = max(
        points.maxOfOrNull { max(it.sales, it.netProfit) } ?: 1.0,
        1.0,
    )
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val dotHaloColor = MaterialTheme.colorScheme.surface
    val profitLineColor = ShouravColors.emeraldIconTint

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendDot(color = SkyChart, label = bi("Sales", "বিক্রি", mode))
            LegendDot(color = profitLineColor, label = bi("Net Profit", "নিট লাভ", mode))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp)
        ) {
            val count = points.size.coerceAtLeast(1)
            val chartHeight = size.height - 8.dp.toPx()
            val slotWidth = size.width / count
            val barWidth = (slotWidth * 0.34f).coerceIn(8.dp.toPx(), 26.dp.toPx())

            // Subtle horizontal grid lines
            for (step in 0..3) {
                val y = chartHeight * (step / 3f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            val profitPath = Path()
            points.forEachIndexed { index, pt ->
                val centerX = slotWidth * index + slotWidth / 2f
                val salesRatio = (pt.sales / maxVal).toFloat().coerceIn(0f, 1f)
                val salesHeight = salesRatio * (chartHeight - 10.dp.toPx())
                if (salesHeight > 0f) {
                    drawRoundRect(
                        color = SkyChart.copy(alpha = 0.85f),
                        topLeft = Offset(centerX - barWidth / 2f, chartHeight - salesHeight),
                        size = Size(barWidth, salesHeight),
                        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    )
                }

                val profitRatio = (pt.netProfit.coerceAtLeast(0.0) / maxVal).toFloat().coerceIn(0f, 1f)
                val profitY = chartHeight - profitRatio * (chartHeight - 10.dp.toPx())
                if (index == 0) {
                    profitPath.moveTo(centerX, profitY)
                } else {
                    profitPath.lineTo(centerX, profitY)
                }
            }

            drawPath(
                path = profitPath,
                color = profitLineColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
            )

            points.forEachIndexed { index, pt ->
                val centerX = slotWidth * index + slotWidth / 2f
                val profitRatio = (pt.netProfit.coerceAtLeast(0.0) / maxVal).toFloat().coerceIn(0f, 1f)
                val profitY = chartHeight - profitRatio * (chartHeight - 10.dp.toPx())
                drawCircle(
                    color = dotHaloColor,
                    radius = 5.dp.toPx(),
                    center = Offset(centerX, profitY),
                )
                drawCircle(
                    color = profitLineColor,
                    radius = 3.5.dp.toPx(),
                    center = Offset(centerX, profitY),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            points.forEach { pt ->
                Text(
                    text = pt.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = ShouravColors.subtleText,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
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
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun AddCustomerDialog(
    mode: LanguageMode,
    onDismiss: () -> Unit,
    onConfirm: (name: String, phone: String, shop: String, openingBalance: Double) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var shop by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = bi("Add Customer", "নতুন ক্রেতা যোগ করুন", mode),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text(bi("Customer Name *", "ক্রেতার নাম *", mode)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_customer_name_input"),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(bi("Phone Number", "মোবাইল নম্বর", mode)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = shop,
                    onValueChange = { shop = it },
                    label = { Text(bi("Shop / Address", "দোকান / ঠিকানা", mode)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = opening,
                    onValueChange = { opening = it },
                    label = { Text(bi("Opening Due Balance (৳)", "পূর্বের বাকী (৳)", mode)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        color = CrimsonDue,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        error = bi("Customer name is required", "ক্রেতার নাম আবশ্যক", mode)
                    } else {
                        val openingVal = parseLocalizedDouble(opening)?.coerceAtLeast(0.0) ?: 0.0
                        onConfirm(trimmed, phone.trim(), shop.trim(), openingVal)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier.testTag("save_customer_button"),
            ) {
                Text(bi("Save", "সংরক্ষণ", mode))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(bi("Cancel", "বাতিল", mode))
            }
        },
    )
}

@Composable
fun RecordPaymentDialog(
    customerName: String,
    currentDue: Double,
    mode: LanguageMode,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, notes: String) -> Unit,
) {
    var amountText by remember {
        mutableStateOf(if (currentDue > 0.0) formatEditableNumber(currentDue) else "")
    }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val parsedAmount = parseLocalizedDouble(amountText)?.coerceAtLeast(0.0) ?: 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = bi("Record Payment", "বাকী জমা নিন", mode),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = customerName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ShouravColors.emeraldInk,
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PaymentKhataPreviewCard(
                    currentDue = currentDue,
                    paidAmount = parsedAmount,
                    mode = mode,
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        error = null
                    },
                    label = { Text(bi("Amount Paid (৳)", "জমার পরিমাণ (৳)", mode)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("record_payment_amount_input"),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(bi("Payment Note (Cash/Bank)", "মন্তব্য (নগদ/ব্যাংক)", mode)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        color = CrimsonDue,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = parseLocalizedDouble(amountText)
                    if (amount == null || amount <= 0.0) {
                        error = bi("Enter a valid positive amount", "সঠিক পরিমাণ লিখুন", mode)
                    } else {
                        onConfirm(amount, notes.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                modifier = Modifier.testTag("confirm_payment_button"),
            ) {
                Text(bi("Record Paid", "জমা লিখুন", mode))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(bi("Cancel", "বাতিল", mode))
            }
        },
    )
}

@Composable
fun PaymentKhataPreviewCard(
    currentDue: Double,
    paidAmount: Double,
    mode: LanguageMode,
    modifier: Modifier = Modifier,
) {
    val remainingAfterPayment = currentDue - paidAmount
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("payment_khata_preview"),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = bi("Current Total Due", "বর্তমান মোট বাকী", mode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatBdt(currentDue),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (currentDue > 0.5) CrimsonDue else ShouravColors.emeraldInk,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = bi("Less: Joma Paid", "বাদ: জমা টাকা", mode),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "-${formatBdt(paidAmount)}",
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
                    text = if (remainingAfterPayment < -0.5) {
                        bi("Advance Credit", "অগ্রিম জমা থাকবে", mode)
                    } else {
                        bi("Remaining Due", "জমা বাদে বাকী", mode)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (remainingAfterPayment < -0.5) {
                        formatBdt(-remainingAfterPayment)
                    } else {
                        formatBdt(remainingAfterPayment.coerceAtLeast(0.0))
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (remainingAfterPayment > 0.5) CrimsonDue else ShouravColors.emeraldInk,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("payment_remaining_due_value"),
                )
            }
        }
    }
}
