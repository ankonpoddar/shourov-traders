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
import com.example.data.model.Customer
import com.example.data.model.CustomerTransactionEntry
import com.example.data.model.LedgerTransaction
import com.example.data.preferences.formatBdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TransactionSlipData(
    val customer: Customer?,
    val entry: CustomerTransactionEntry,
)

object TransactionSlipPdfExporter {

    private const val PAGE_WIDTH = 420 // A5-like receipt width for clean print/share
    private const val PAGE_HEIGHT = 595
    private const val MARGIN_X = 28f

    private const val COLOR_EMERALD = 0xFF059669.toInt()
    private const val COLOR_EMERALD_DARK = 0xFF047857.toInt()
    private const val COLOR_EMERALD_BG = 0xFFECFDF5.toInt()
    private const val COLOR_SLATE_INK = 0xFF0F172A.toInt()
    private const val COLOR_SLATE_MUTED = 0xFF475569.toInt()
    private const val COLOR_SLATE_BG = 0xFFF8FAFC.toInt()
    private const val COLOR_BORDER = 0xFFE2E8F0.toInt()
    private const val COLOR_CRIMSON = 0xFFDC2626.toInt()
    private const val COLOR_CRIMSON_BG = 0xFFFEF2F2.toInt()

    internal interface SlipPdfSink {
        fun startPage(width: Int, height: Int): Canvas
        fun finishPage()
        fun writeTo(outputStream: OutputStream)
        fun close()
    }

    private class AndroidSlipPdfSink : SlipPdfSink {
        private val document = PdfDocument()
        private var activePage: PdfDocument.Page? = null

        override fun startPage(width: Int, height: Int): Canvas {
            val info = PdfDocument.PageInfo.Builder(width, height, 1).create()
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

    fun suggestedFileName(data: TransactionSlipData): String {
        val tx = data.entry.transaction
        val kind = if (tx.type == LedgerTransaction.TYPE_PAYMENT) "Joma_Slip" else "Sale_Slip"
        val safeCustomer = tx.customerName
            .replace(Regex("[^A-Za-z0-9]+"), "_")
            .trim('_')
            .take(24)
            .ifEmpty { "Customer" }
        val dateSlug = SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).format(Date(tx.transactionDateMillis))
        return "Shourav_${kind}_${safeCustomer}_$dateSlug.pdf"
    }

    internal fun renderSlip(data: TransactionSlipData, sink: SlipPdfSink) {
        val canvas = sink.startPage(PAGE_WIDTH, PAGE_HEIGHT)
        val tx = data.entry.transaction
        val isPayment = tx.type == LedgerTransaction.TYPE_PAYMENT
        val rightX = PAGE_WIDTH - MARGIN_X

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = COLOR_BORDER
        }
        val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            color = COLOR_SLATE_INK
        }
        val regularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            color = COLOR_SLATE_INK
        }

        var y = 26f

        // Header Banner
        val bannerRect = RectF(MARGIN_X, y, rightX, y + 66f)
        fillPaint.color = COLOR_EMERALD
        canvas.drawRoundRect(bannerRect, 10f, 10f, fillPaint)

        boldPaint.color = Color.WHITE
        boldPaint.textSize = 15f
        canvas.drawText("SHOURAV TRADERS", MARGIN_X + 14f, y + 24f, boldPaint)

        regularPaint.color = COLOR_EMERALD_BG
        regularPaint.textSize = 9.5f
        canvas.drawText(
            if (isPayment) "CASH / DUE PAYMENT RECEIPT (জমা রসিদ)" else "RICE SALE MEMO / CREDIT SLIP (বিক্রয় মেমো)",
            MARGIN_X + 14f,
            y + 41f,
            regularPaint,
        )

        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH).format(Date(tx.transactionDateMillis))
        regularPaint.textSize = 8.5f
        canvas.drawText("Date: $dateStr  ·  Ref: #${tx.id.take(8).uppercase()}", MARGIN_X + 14f, y + 56f, regularPaint)
        y += 80f

        // Customer Card
        val custRect = RectF(MARGIN_X, y, rightX, y + 54f)
        fillPaint.color = COLOR_SLATE_BG
        canvas.drawRoundRect(custRect, 8f, 8f, fillPaint)
        strokePaint.color = COLOR_BORDER
        canvas.drawRoundRect(custRect, 8f, 8f, strokePaint)

        boldPaint.color = COLOR_SLATE_MUTED
        boldPaint.textSize = 8f
        canvas.drawText("CUSTOMER / PARTY", MARGIN_X + 12f, y + 16f, boldPaint)

        val maxCustTextWidth = rightX - MARGIN_X - 24f

        boldPaint.color = COLOR_SLATE_INK
        boldPaint.textSize = 11.5f
        canvas.drawText(ellipsize(tx.customerName, boldPaint, maxCustTextWidth), MARGIN_X + 12f, y + 32f, boldPaint)

        val meta = listOfNotNull(
            data.customer?.phone?.takeIf { it.isNotBlank() },
            data.customer?.shopAddress?.takeIf { it.isNotBlank() },
        ).joinToString(" · ").ifEmpty { "Baki Khata Account" }
        regularPaint.color = COLOR_SLATE_MUTED
        regularPaint.textSize = 8.5f
        canvas.drawText(ellipsize(meta, regularPaint, maxCustTextWidth), MARGIN_X + 12f, y + 46f, regularPaint)
        y += 68f

        // Entry Details Box
        val detailHeight = if (isPayment) 76f else 118f
        val detailRect = RectF(MARGIN_X, y, rightX, y + detailHeight)
        fillPaint.color = Color.WHITE
        canvas.drawRoundRect(detailRect, 8f, 8f, fillPaint)
        strokePaint.color = COLOR_BORDER
        canvas.drawRoundRect(detailRect, 8f, 8f, strokePaint)

        boldPaint.color = COLOR_EMERALD_DARK
        boldPaint.textSize = 9.5f
        canvas.drawText(
            if (isPayment) "PAYMENT DETAILS" else "SALE DETAILS",
            MARGIN_X + 12f,
            y + 18f,
            boldPaint,
        )

        var lineY = y + 36f
        fun drawPair(label: String, value: String, valueColor: Int = COLOR_SLATE_INK, boldVal: Boolean = false) {
            val maxPairValueWidth = (rightX - MARGIN_X) * 0.55f
            regularPaint.color = COLOR_SLATE_MUTED
            regularPaint.textSize = 9.5f
            regularPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(label, MARGIN_X + 12f, lineY, regularPaint)

            val vp = if (boldVal) boldPaint else regularPaint
            vp.color = valueColor
            vp.textSize = 9.5f
            vp.textAlign = Paint.Align.RIGHT
            canvas.drawText(ellipsize(value, vp, maxPairValueWidth), rightX - 12f, lineY, vp)
            vp.textAlign = Paint.Align.LEFT
            lineY += 18f
        }

        if (isPayment) {
            drawPair("Joma / Payment Received", formatBdt(tx.paidAmount), COLOR_EMERALD_DARK, boldVal = true)
            drawPair("Notes", tx.notes.ifBlank { "Cash / Bank Payment" }, COLOR_SLATE_MUTED)
        } else {
            drawPair("Rice Variety", "${tx.riceVariety} (${tx.quantity.toInt()} ${tx.unit})")
            drawPair("Rate per ${tx.unit}", formatBdt(tx.sellingPricePerUnit))
            drawPair("Total Bill Amount", formatBdt(tx.totalAmount), COLOR_SLATE_INK, boldVal = true)
            drawPair("Paid Now (Nagad / Joma)", formatBdt(tx.paidAmount), COLOR_EMERALD_DARK, boldVal = true)
        }
        y += detailHeight + 14f

        // Khata Running Balance Box (Previous Due +/- Entry = New Total Due)
        val balanceBoxHeight = 96f
        val balanceRect = RectF(MARGIN_X, y, rightX, y + balanceBoxHeight)
        fillPaint.color = COLOR_SLATE_BG
        canvas.drawRoundRect(balanceRect, 8f, 8f, fillPaint)
        strokePaint.color = COLOR_EMERALD
        canvas.drawRoundRect(balanceRect, 8f, 8f, strokePaint)

        boldPaint.color = COLOR_EMERALD_DARK
        boldPaint.textSize = 9.5f
        canvas.drawText("BAKI KHATA BALANCE SUMMARY (খাতার হিসাব)", MARGIN_X + 12f, y + 18f, boldPaint)

        lineY = y + 36f
        drawPair("Previous Due (আগের বাকী)", formatBdt(data.entry.previousDue), COLOR_SLATE_MUTED)
        if (isPayment) {
            drawPair("Less: Joma / Paid (জমা বাদ)", "-${formatBdt(tx.paidAmount)}", COLOR_EMERALD_DARK, boldVal = true)
        } else {
            val saleDue = (tx.totalAmount - tx.paidAmount).coerceAtLeast(0.0)
            drawPair("Plus: New Sale Due (নতুন বাকী)", "+${formatBdt(saleDue)}", COLOR_CRIMSON, boldVal = true)
        }

        val finalDue = data.entry.runningDueAfter
        val finalBg = if (finalDue > 0.5) COLOR_CRIMSON_BG else COLOR_EMERALD_BG
        val finalFg = if (finalDue > 0.5) COLOR_CRIMSON else COLOR_EMERALD_DARK
        fillPaint.color = finalBg
        canvas.drawRect(MARGIN_X + 1f, lineY - 10f, rightX - 1f, y + balanceBoxHeight - 1f, fillPaint)

        boldPaint.color = COLOR_SLATE_INK
        boldPaint.textSize = 10f
        canvas.drawText("Total Due Balance (মোট বাকী)", MARGIN_X + 12f, lineY + 6f, boldPaint)
        boldPaint.color = finalFg
        boldPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            if (finalDue > 0.5) formatBdt(finalDue) else "৳ 0 (Cleared)",
            rightX - 12f,
            lineY + 6f,
            boldPaint,
        )
        boldPaint.textAlign = Paint.Align.LEFT

        if (!isPayment && tx.notes.isNotBlank()) {
            y += balanceBoxHeight + 14f
            regularPaint.color = COLOR_SLATE_MUTED
            regularPaint.textSize = 8.5f
            regularPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(ellipsize("Note: ${tx.notes}", regularPaint, rightX - MARGIN_X), MARGIN_X, y, regularPaint)
        }

        // Signature / Footer
        regularPaint.color = COLOR_SLATE_MUTED
        regularPaint.textSize = 8f
        regularPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Authorized Signatory · Shourav Traders", MARGIN_X, PAGE_HEIGHT - 26f, regularPaint)
        regularPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Generated Offline", rightX, PAGE_HEIGHT - 26f, regularPaint)
        regularPaint.textAlign = Paint.Align.LEFT

        sink.finishPage()
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "..."
        val ellipsisWidth = paint.measureText(ellipsis)
        val count = paint.breakText(text, true, (maxWidth - ellipsisWidth).coerceAtLeast(0f), null)
        return if (count <= 0) ellipsis else text.substring(0, count).trimEnd() + ellipsis
    }

    fun writeSlipToStream(data: TransactionSlipData, outputStream: OutputStream) {
        val sink = AndroidSlipPdfSink()
        try {
            renderSlip(data, sink)
            sink.writeTo(outputStream)
        } finally {
            sink.close()
        }
    }

    suspend fun exportSlipToCacheFile(
        context: Context,
        data: TransactionSlipData,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "pdf_reports").apply { mkdirs() }
        val file = File(dir, suggestedFileName(data))
        FileOutputStream(file).use { out ->
            writeSlipToStream(data, out)
        }
        file
    }

    suspend fun shareSlipPdf(
        context: Context,
        data: TransactionSlipData,
    ): Result<Unit> = runCatching {
        val pdfFile = exportSlipToCacheFile(context, data)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile,
        )
        val tx = data.entry.transaction
        val subject = if (tx.type == LedgerTransaction.TYPE_PAYMENT) {
            "Shourav Traders - Payment Receipt (${tx.customerName})"
        } else {
            "Shourav Traders - Sale Memo (${tx.customerName})"
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Transaction Slip PDF").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    suspend fun saveSlipToUri(
        context: Context,
        uri: Uri,
        data: TransactionSlipData,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openOutputStream(uri)
                ?: error("Unable to open destination file.")
            stream.use { out ->
                writeSlipToStream(data, out)
            }
        }
    }

    fun printSlipPdf(
        context: Context,
        data: TransactionSlipData,
    ): Result<Unit> = runCatching {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: error("Print service unavailable.")
        val jobName = suggestedFileName(data).removeSuffix(".pdf")
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
                val info = PrintDocumentInfo.Builder(suggestedFileName(data))
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(1)
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
                        writeSlipToStream(data, out)
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.localizedMessage ?: "Failed to print slip PDF.")
                }
            }
        }
        printManager.print(
            jobName,
            adapter,
            PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A5)
                .build(),
        )
    }
}
