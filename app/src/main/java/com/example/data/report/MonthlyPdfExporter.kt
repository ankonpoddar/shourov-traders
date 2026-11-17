package com.example.data.report

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import com.example.data.model.LedgerTransaction
import com.example.data.preferences.formatBdt
import com.example.data.preferences.formatShortDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object MonthlyPdfExporter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_X = 36f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN_X * 2f)
    private const val BOTTOM_SAFE_Y = PAGE_HEIGHT - 44f

    private const val COLOR_EMERALD = 0xFF059669.toInt()
    private const val COLOR_EMERALD_DARK = 0xFF047857.toInt()
    private const val COLOR_EMERALD_BG = 0xFFECFDF5.toInt()
    private const val COLOR_SLATE_INK = 0xFF0F172A.toInt()
    private const val COLOR_SLATE_MUTED = 0xFF475569.toInt()
    private const val COLOR_SLATE_BG = 0xFFF8FAFC.toInt()
    private const val COLOR_BORDER = 0xFFE2E8F0.toInt()
    private const val COLOR_CRIMSON = 0xFFDC2626.toInt()
    private const val COLOR_CRIMSON_BG = 0xFFFEF2F2.toInt()
    private const val COLOR_AMBER = 0xFFD97706.toInt()
    private const val COLOR_SKY = 0xFF0284C7.toInt()

    internal interface PdfSink {
        fun startPage(width: Int, height: Int, pageNumber: Int): Canvas
        fun finishPage()
        fun writeTo(outputStream: OutputStream)
        fun close()
    }

    private class AndroidPdfDocumentSink : PdfSink {
        private val document = PdfDocument()
        private var activePage: PdfDocument.Page? = null

        override fun startPage(width: Int, height: Int, pageNumber: Int): Canvas {
            val info = PdfDocument.PageInfo.Builder(width, height, pageNumber).create()
            val page = document.startPage(info)
            activePage = page
            return page.canvas
        }

        override fun finishPage() {
            val page = activePage ?: return
            document.finishPage(page)
            activePage = null
        }

        override fun writeTo(outputStream: OutputStream) {
            document.writeTo(outputStream)
            outputStream.flush()
        }

        override fun close() {
            document.close()
        }
    }

    fun suggestedFileName(period: ReportMonthOption): String =
        "Shourav_Traders_Report_${period.fileSlug}.pdf"

    internal fun renderToSink(report: MonthlyReportData, sink: PdfSink) {
        val session = PdfPageSession(sink, report)
        session.drawCoverHeader()
        session.drawKpiCards()
        session.drawProfitAndLossSection()
        session.drawCustomerLedgerSection()
        session.drawExpensesAndLoansSection()
        session.drawTransactionsSection()
        session.finishCurrentPage()
    }

    fun writePdfToStream(report: MonthlyReportData, outputStream: OutputStream) {
        val sink = AndroidPdfDocumentSink()
        try {
            renderToSink(report, sink)
            sink.writeTo(outputStream)
        } finally {
            sink.close()
        }
    }

    suspend fun exportToCacheFile(
        context: Context,
        report: MonthlyReportData,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "pdf_reports").apply { mkdirs() }
        val file = File(dir, suggestedFileName(report.period))
        FileOutputStream(file).use { out ->
            writePdfToStream(report, out)
        }
        file
    }

    suspend fun sharePdf(
        context: Context,
        report: MonthlyReportData,
    ): Result<Unit> = runCatching {
        val pdfFile = exportToCacheFile(context, report)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile,
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Shourav Traders - ${report.period.displayLabel} Report")
            putExtra(
                Intent.EXTRA_TEXT,
                "Shourav Traders Monthly Credit Ledger (Baki Khata) & Profit Report for ${report.period.displayLabel}.",
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Monthly PDF Report").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    suspend fun savePdfToUri(
        context: Context,
        uri: Uri,
        report: MonthlyReportData,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openOutputStream(uri)
                ?: error("Unable to open destination file for writing.")
            stream.use { out ->
                writePdfToStream(report, out)
            }
        }
    }

    fun printPdf(
        context: Context,
        report: MonthlyReportData,
    ): Result<Unit> = runCatching {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: error("Android Print service is unavailable on this device.")
        val jobName = "Shourav Traders - ${report.period.displayLabel}"
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?,
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(suggestedFileName(report.period))
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback,
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onWriteCancelled()
                    return
                }
                try {
                    FileOutputStream(destination.fileDescriptor).use { out ->
                        writePdfToStream(report, out)
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.localizedMessage ?: "Failed to render PDF for printing.")
                }
            }
        }
        printManager.print(
            jobName,
            adapter,
            PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .build(),
        )
    }

    private class PdfPageSession(
        private val sink: PdfSink,
        private val report: MonthlyReportData,
    ) {
        private var pageNumber = 1
        private var hasOpenPage = false
        private var canvas: Canvas = Canvas()
        private var y = 0f

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = COLOR_BORDER
        }
        private val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            color = COLOR_SLATE_INK
        }
        private val regularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            color = COLOR_SLATE_INK
        }

        init {
            startNewPage(drawCompactContinuationHeader = false)
        }

        private fun startNewPage(drawCompactContinuationHeader: Boolean) {
            if (hasOpenPage) {
                finishCurrentPage()
                pageNumber += 1
            } else {
                pageNumber = 1
            }
            canvas = sink.startPage(PAGE_WIDTH, PAGE_HEIGHT, pageNumber)
            hasOpenPage = true
            regularPaint.textAlign = Paint.Align.LEFT
            boldPaint.textAlign = Paint.Align.LEFT
            y = 32f
            if (drawCompactContinuationHeader) {
                regularPaint.textSize = 8.5f
                regularPaint.color = COLOR_SLATE_MUTED
                canvas.drawText(
                    "Shourav Traders · ${report.period.displayLabel} (Continued)",
                    MARGIN_X,
                    y,
                    regularPaint,
                )
                y += 8f
                strokePaint.color = COLOR_BORDER
                canvas.drawLine(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y, strokePaint)
                y += 16f
            }
        }

        fun finishCurrentPage() {
            if (!hasOpenPage) return
            regularPaint.textSize = 8f
            regularPaint.color = COLOR_SLATE_MUTED
            regularPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(
                "Generated offline · Shourav Traders Digital Ledger",
                MARGIN_X,
                PAGE_HEIGHT - 20f,
                regularPaint,
            )
            regularPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(
                "Page $pageNumber",
                PAGE_WIDTH - MARGIN_X,
                PAGE_HEIGHT - 20f,
                regularPaint,
            )
            regularPaint.textAlign = Paint.Align.LEFT
            sink.finishPage()
            hasOpenPage = false
        }

        private fun ensureSpace(neededHeight: Float): Boolean {
            if (y + neededHeight > BOTTOM_SAFE_Y) {
                startNewPage(drawCompactContinuationHeader = true)
                return true
            }
            return false
        }

        fun drawCoverHeader() {
            val headerRect = RectF(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 72f)
            fillPaint.color = COLOR_EMERALD
            canvas.drawRoundRect(headerRect, 10f, 10f, fillPaint)

            boldPaint.color = Color.WHITE
            boldPaint.textSize = 18f
            canvas.drawText("SHOURAV TRADERS", MARGIN_X + 16f, y + 26f, boldPaint)

            regularPaint.color = COLOR_EMERALD_BG
            regularPaint.textSize = 10.5f
            canvas.drawText(
                "Monthly Credit Ledger (Baki Khata) & Profit Report",
                MARGIN_X + 16f,
                y + 44f,
                regularPaint,
            )
            regularPaint.textSize = 9f
            canvas.drawText(
                "Period: ${report.period.displayLabel}   ·   Generated: ${MonthlyReportBuilder.formatReportTimestamp(report.generatedAtMillis)}",
                MARGIN_X + 16f,
                y + 59f,
                regularPaint,
            )
            y += 88f
        }

        fun drawKpiCards() {
            ensureSpace(70f)
            val gap = 10f
            val cardWidth = (CONTENT_WIDTH - gap * 3f) / 4f
            val cardHeight = 58f

            val items = listOf(
                Triple("TOTAL SALES (${report.monthlySaleCount})", formatBdt(report.monthlySalesTotal), COLOR_SKY),
                Triple("RECEIVABLE DUE", formatBdt(report.totalReceivableDue), if (report.totalReceivableDue > 0.5) COLOR_CRIMSON else COLOR_EMERALD_DARK),
                Triple("TOTAL EXPENSES", formatBdt(report.monthlyTotalExpenses), COLOR_AMBER),
                Triple("NET PROFIT", formatBdt(report.monthlyNetProfit), if (report.monthlyNetProfit >= 0.0) COLOR_EMERALD_DARK else COLOR_CRIMSON),
            )

            items.forEachIndexed { idx, (label, amount, accent) ->
                val left = MARGIN_X + idx * (cardWidth + gap)
                val rect = RectF(left, y, left + cardWidth, y + cardHeight)
                fillPaint.color = COLOR_SLATE_BG
                canvas.drawRoundRect(rect, 8f, 8f, fillPaint)
                strokePaint.color = COLOR_BORDER
                canvas.drawRoundRect(rect, 8f, 8f, strokePaint)

                // Top accent bar
                fillPaint.color = accent
                canvas.drawRoundRect(RectF(left, y, left + cardWidth, y + 4f), 4f, 4f, fillPaint)

                boldPaint.color = COLOR_SLATE_MUTED
                boldPaint.textSize = 7.5f
                canvas.drawText(ellipsize(label, boldPaint, cardWidth - 14f), left + 8f, y + 20f, boldPaint)

                boldPaint.color = accent
                boldPaint.textSize = 11.5f
                canvas.drawText(ellipsize(amount, boldPaint, cardWidth - 14f), left + 8f, y + 42f, boldPaint)
            }
            y += cardHeight + 18f
        }

        fun drawProfitAndLossSection() {
            ensureSpace(136f)
            drawSectionTitle("1. Profit & Loss Summary (${report.period.displayLabel})")

            val boxTop = y
            val rowHeight = 20f
            val rows = listOf(
                Triple("Gross Sale Profit (Sales Margin)", formatBdt(report.monthlyGrossProfit, showPositiveSign = true), COLOR_EMERALD_DARK),
                Triple("Daily Operational Expenses", "-${formatBdt(report.monthlyDailyExpenses)}", COLOR_AMBER),
                Triple("Monthly Fixed Expenses (Salary, Electricity, etc.)", "-${formatBdt(report.monthlyFixedExpenses)}", COLOR_AMBER),
                Triple("Monthly Bank Loan Interest Burden", "-${formatBdt(report.monthlyLoanInterest)}", COLOR_CRIMSON),
                Triple("Cash / Bank Payments Collected in Month", formatBdt(report.monthlyPaymentsCollected), COLOR_SLATE_INK),
            )
            val totalHeight = rows.size * rowHeight + 28f
            val boxRect = RectF(MARGIN_X, boxTop, PAGE_WIDTH - MARGIN_X, boxTop + totalHeight)
            fillPaint.color = Color.WHITE
            canvas.drawRoundRect(boxRect, 8f, 8f, fillPaint)
            strokePaint.color = COLOR_BORDER
            canvas.drawRoundRect(boxRect, 8f, 8f, strokePaint)

            var rowY = boxTop + 15f
            rows.forEach { (label, value, color) ->
                regularPaint.textSize = 9.5f
                regularPaint.color = COLOR_SLATE_MUTED
                regularPaint.textAlign = Paint.Align.LEFT
                canvas.drawText(label, MARGIN_X + 12f, rowY, regularPaint)

                boldPaint.textSize = 9.5f
                boldPaint.color = color
                boldPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(value, PAGE_WIDTH - MARGIN_X - 12f, rowY, boldPaint)
                boldPaint.textAlign = Paint.Align.LEFT
                rowY += rowHeight
            }

            // Net Profit Highlight Row
            val netBg = if (report.monthlyNetProfit >= 0.0) COLOR_EMERALD_BG else COLOR_CRIMSON_BG
            val netColor = if (report.monthlyNetProfit >= 0.0) COLOR_EMERALD_DARK else COLOR_CRIMSON
            fillPaint.color = netBg
            canvas.drawRect(MARGIN_X + 1f, rowY - 11f, PAGE_WIDTH - MARGIN_X - 1f, boxTop + totalHeight - 1f, fillPaint)

            boldPaint.textSize = 10.5f
            boldPaint.color = COLOR_SLATE_INK
            canvas.drawText("Net Business Profit (Formula: Gross - Expenses - Loan Interest)", MARGIN_X + 12f, rowY + 4f, boldPaint)

            boldPaint.color = netColor
            boldPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(
                formatBdt(report.monthlyNetProfit, showPositiveSign = report.monthlyNetProfit > 0.0),
                PAGE_WIDTH - MARGIN_X - 12f,
                rowY + 4f,
                boldPaint,
            )
            boldPaint.textAlign = Paint.Align.LEFT

            y = boxTop + totalHeight + 18f
        }

        fun drawCustomerLedgerSection() {
            ensureSpace(58f)
            drawSectionTitle("2. Customer Credit Ledger / Baki Khata (${report.customerLedgerRows.size} Parties)")

            if (report.customerLedgerRows.isEmpty()) {
                drawEmptyNote("No customer ledger accounts recorded for this period.")
                return
            }

            val colWidths = floatArrayOf(173f, 85f, 85f, 85f, 95f)
            val headers = listOf("Customer", "Prior Due", "Month Sales", "Month Paid", "Closing Due")
            drawTableHeader(headers, colWidths)

            report.customerLedgerRows.forEachIndexed { index, row ->
                if (ensureSpace(22f)) {
                    drawTableHeader(headers, colWidths)
                }
                val rowTop = y
                if (index % 2 == 1) {
                    fillPaint.color = COLOR_SLATE_BG
                    canvas.drawRect(MARGIN_X, rowTop, PAGE_WIDTH - MARGIN_X, rowTop + 20f, fillPaint)
                }
                val nameText = if (row.phone.isNotBlank()) "${row.customerName} (${row.phone})" else row.customerName
                drawTableCell(nameText, MARGIN_X, colWidths[0], rowTop, COLOR_SLATE_INK, bold = false, alignRight = false)
                drawTableCell(formatBdt(row.priorDue), MARGIN_X + colWidths[0], colWidths[1], rowTop, COLOR_SLATE_MUTED, bold = false, alignRight = true)
                drawTableCell(formatBdt(row.monthSales), MARGIN_X + colWidths[0] + colWidths[1], colWidths[2], rowTop, COLOR_SLATE_INK, bold = false, alignRight = true)
                drawTableCell(formatBdt(row.monthPaid), MARGIN_X + colWidths[0] + colWidths[1] + colWidths[2], colWidths[3], rowTop, COLOR_EMERALD_DARK, bold = false, alignRight = true)
                drawTableCell(
                    text = if (row.closingDue > 0.5) formatBdt(row.closingDue) else "Cleared",
                    x = MARGIN_X + colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3],
                    width = colWidths[4],
                    topY = rowTop,
                    color = if (row.closingDue > 0.5) COLOR_CRIMSON else COLOR_EMERALD_DARK,
                    bold = true,
                    alignRight = true,
                )
                strokePaint.color = COLOR_BORDER
                canvas.drawLine(MARGIN_X, rowTop + 20f, PAGE_WIDTH - MARGIN_X, rowTop + 20f, strokePaint)
                y += 20f
            }
            y += 16f
        }

        fun drawExpensesAndLoansSection() {
            if (report.expensesInMonth.isEmpty() && report.activeLoans.isEmpty()) return
            ensureSpace(58f)
            drawSectionTitle("3. Expenses & Active Bank Loans (${report.period.displayLabel})")

            if (report.expensesInMonth.isNotEmpty()) {
                val colWidths = floatArrayOf(75f, 80f, 150f, 128f, 90f)
                val headers = listOf("Date", "Type", "Category", "Notes", "Amount")
                drawTableHeader(headers, colWidths)

                report.expensesInMonth.forEachIndexed { idx, exp ->
                    if (ensureSpace(22f)) drawTableHeader(headers, colWidths)
                    val rowTop = y
                    if (idx % 2 == 1) {
                        fillPaint.color = COLOR_SLATE_BG
                        canvas.drawRect(MARGIN_X, rowTop, PAGE_WIDTH - MARGIN_X, rowTop + 20f, fillPaint)
                    }
                    drawTableCell(formatShortDate(exp.expenseDateMillis), MARGIN_X, colWidths[0], rowTop, COLOR_SLATE_MUTED, false, false)
                    drawTableCell(exp.frequency, MARGIN_X + colWidths[0], colWidths[1], rowTop, COLOR_SLATE_INK, false, false)
                    drawTableCell(exp.category, MARGIN_X + colWidths[0] + colWidths[1], colWidths[2], rowTop, COLOR_SLATE_INK, false, false)
                    drawTableCell(exp.notes.ifBlank { "-" }, MARGIN_X + colWidths[0] + colWidths[1] + colWidths[2], colWidths[3], rowTop, COLOR_SLATE_MUTED, false, false)
                    drawTableCell(formatBdt(exp.amount), MARGIN_X + colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3], colWidths[4], rowTop, COLOR_AMBER, true, true)
                    strokePaint.color = COLOR_BORDER
                    canvas.drawLine(MARGIN_X, rowTop + 20f, PAGE_WIDTH - MARGIN_X, rowTop + 20f, strokePaint)
                    y += 20f
                }
                y += 10f
            }

            if (report.activeLoans.isNotEmpty()) {
                val colWidths = floatArrayOf(183f, 110f, 70f, 160f)
                val headers = listOf("Lender / Bank", "Remaining Principal", "Rate", "Monthly Interest")
                ensureSpace(44f)
                drawTableHeader(headers, colWidths)

                report.activeLoans.forEachIndexed { idx, loan ->
                    if (ensureSpace(22f)) drawTableHeader(headers, colWidths)
                    val rowTop = y
                    if (idx % 2 == 1) {
                        fillPaint.color = COLOR_SLATE_BG
                        canvas.drawRect(MARGIN_X, rowTop, PAGE_WIDTH - MARGIN_X, rowTop + 20f, fillPaint)
                    }
                    drawTableCell(loan.lenderName, MARGIN_X, colWidths[0], rowTop, COLOR_SLATE_INK, false, false)
                    drawTableCell(formatBdt(loan.remainingPrincipal), MARGIN_X + colWidths[0], colWidths[1], rowTop, COLOR_SLATE_INK, false, true)
                    drawTableCell("${loan.annualInterestRate}%", MARGIN_X + colWidths[0] + colWidths[1], colWidths[2], rowTop, COLOR_SLATE_MUTED, false, true)
                    drawTableCell(formatBdt(loan.monthlyInterest), MARGIN_X + colWidths[0] + colWidths[1] + colWidths[2], colWidths[3], rowTop, COLOR_CRIMSON, true, true)
                    strokePaint.color = COLOR_BORDER
                    canvas.drawLine(MARGIN_X, rowTop + 20f, PAGE_WIDTH - MARGIN_X, rowTop + 20f, strokePaint)
                    y += 20f
                }
                y += 16f
            }
        }

        fun drawTransactionsSection() {
            ensureSpace(58f)
            val sectionNum = if (report.expensesInMonth.isEmpty() && report.activeLoans.isEmpty()) 3 else 4
            drawSectionTitle("$sectionNum. Monthly Sales & Payments Log (${report.transactionsInMonth.size} Entries)")

            if (report.transactionsInMonth.isEmpty()) {
                drawEmptyNote("No sales or payment transactions logged in ${report.period.displayLabel}.")
                return
            }

            val colWidths = floatArrayOf(70f, 145f, 118f, 65f, 65f, 60f)
            val headers = listOf("Date", "Customer", "Item / Type", "Total", "Paid", "Due")
            drawTableHeader(headers, colWidths)

            report.transactionsInMonth.forEachIndexed { idx, tx ->
                if (ensureSpace(22f)) drawTableHeader(headers, colWidths)
                val rowTop = y
                if (idx % 2 == 1) {
                    fillPaint.color = COLOR_SLATE_BG
                    canvas.drawRect(MARGIN_X, rowTop, PAGE_WIDTH - MARGIN_X, rowTop + 20f, fillPaint)
                }
                val itemLabel = if (tx.type == LedgerTransaction.TYPE_SALE) {
                    "${tx.riceVariety} (${tx.quantity.toInt()} ${tx.unit})"
                } else {
                    "Payment Received"
                }
                val totalLabel = if (tx.type == LedgerTransaction.TYPE_SALE) formatBdt(tx.totalAmount) else "-"
                val dueLabel = if (tx.type == LedgerTransaction.TYPE_SALE && tx.dueAdded > 0.5) {
                    formatBdt(tx.dueAdded)
                } else {
                    "-"
                }

                drawTableCell(formatShortDate(tx.transactionDateMillis), MARGIN_X, colWidths[0], rowTop, COLOR_SLATE_MUTED, false, false)
                drawTableCell(tx.customerName, MARGIN_X + colWidths[0], colWidths[1], rowTop, COLOR_SLATE_INK, false, false)
                drawTableCell(itemLabel, MARGIN_X + colWidths[0] + colWidths[1], colWidths[2], rowTop, COLOR_SLATE_MUTED, false, false)
                drawTableCell(totalLabel, MARGIN_X + colWidths[0] + colWidths[1] + colWidths[2], colWidths[3], rowTop, COLOR_SLATE_INK, false, true)
                drawTableCell(formatBdt(tx.paidAmount), MARGIN_X + colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3], colWidths[4], rowTop, COLOR_EMERALD_DARK, false, true)
                drawTableCell(
                    text = dueLabel,
                    x = MARGIN_X + colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3] + colWidths[4],
                    width = colWidths[5],
                    topY = rowTop,
                    color = if (dueLabel == "-") COLOR_SLATE_MUTED else COLOR_CRIMSON,
                    bold = dueLabel != "-",
                    alignRight = true,
                )
                strokePaint.color = COLOR_BORDER
                canvas.drawLine(MARGIN_X, rowTop + 20f, PAGE_WIDTH - MARGIN_X, rowTop + 20f, strokePaint)
                y += 20f
            }
            y += 14f
        }

        private fun drawSectionTitle(title: String) {
            boldPaint.color = COLOR_EMERALD_DARK
            boldPaint.textSize = 11.5f
            canvas.drawText(title, MARGIN_X, y + 10f, boldPaint)
            y += 18f
        }

        private fun drawEmptyNote(message: String) {
            regularPaint.color = COLOR_SLATE_MUTED
            regularPaint.textSize = 9.5f
            canvas.drawText(message, MARGIN_X, y + 12f, regularPaint)
            y += 26f
        }

        private fun drawTableHeader(headers: List<String>, colWidths: FloatArray) {
            fillPaint.color = COLOR_EMERALD_BG
            canvas.drawRect(MARGIN_X, y, PAGE_WIDTH - MARGIN_X, y + 20f, fillPaint)
            strokePaint.color = COLOR_EMERALD
            canvas.drawLine(MARGIN_X, y + 20f, PAGE_WIDTH - MARGIN_X, y + 20f, strokePaint)

            var curX = MARGIN_X
            headers.forEachIndexed { i, title ->
                val alignRight = i >= 1 && title in setOf(
                    "Prior Due", "Month Sales", "Month Paid", "Closing Due",
                    "Amount", "Remaining Principal", "Rate", "Monthly Interest",
                    "Total", "Paid", "Due",
                )
                drawTableCell(title, curX, colWidths[i], y, COLOR_EMERALD_DARK, bold = true, alignRight = alignRight)
                curX += colWidths[i]
            }
            y += 20f
        }

        private fun drawTableCell(
            text: String,
            x: Float,
            width: Float,
            topY: Float,
            color: Int,
            bold: Boolean,
            alignRight: Boolean,
        ) {
            val paint = if (bold) boldPaint else regularPaint
            paint.color = color
            paint.textSize = 8.5f
            val paddedWidth = (width - 10f).coerceAtLeast(10f)
            val clipped = ellipsize(text, paint, paddedWidth)
            if (alignRight) {
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(clipped, x + width - 5f, topY + 13.5f, paint)
                paint.textAlign = Paint.Align.LEFT
            } else {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(clipped, x + 5f, topY + 13.5f, paint)
            }
        }

        private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
            if (paint.measureText(text) <= maxWidth) return text
            val ellipsis = "..."
            val ellipsisWidth = paint.measureText(ellipsis)
            val count = paint.breakText(text, true, (maxWidth - ellipsisWidth).coerceAtLeast(0f), null)
            return if (count <= 0) ellipsis else text.substring(0, count).trimEnd() + ellipsis
        }
    }
}
