package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.data.model.ScannedLedgerRow
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

class KhataAiRepository(
    private val customScanner: (suspend (Context, Uri) -> Result<List<ScannedLedgerRow>>)? = null,
) {

    private val ledgerSchema = Schema.obj(
        properties = mapOf(
            "rows" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "customerName" to Schema.string(description = "Customer or shop name from the ledger row (Bengali or English)"),
                        "dateText" to Schema.string(description = "Date text on the row if visible, else empty string"),
                        "totalAmount" to Schema.double(description = "Total bill or credit amount in BDT as a number"),
                        "paidAmount" to Schema.double(description = "Amount paid/received in BDT as a number"),
                        "dueAmount" to Schema.double(description = "Remaining due (baki) in BDT as a number"),
                        "notes" to Schema.string(description = "Optional rice variety, bag count, or note from the row"),
                    ),
                    optionalProperties = listOf("dateText", "notes")
                ),
                description = "List of extracted customer credit ledger rows"
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = ledgerSchema
            }
        )
    }

    suspend fun scanLedgerImage(context: Context, imageUri: Uri): Result<List<ScannedLedgerRow>> {
        customScanner?.invoke(context, imageUri)?.let { return it }
        return try {
            val bitmap = withContext(Dispatchers.IO) {
                decodeScaledBitmap(context, imageUri, maxDimension = 1600)
            } ?: return Result.failure(IllegalArgumentException("Unable to read the selected image."))

            val prompt = """
                You are an expert Bangladeshi rice wholesale accounting assistant reading a handwritten or printed Baki Khata (বাকী খাতা) ledger page.
                Extract every customer transaction row visible in the image.
                - Preserve customer names accurately in English or Bengali (or transliterate cleanly if helpful).
                - Extract numeric BDT amounts for totalAmount, paidAmount, and dueAmount. If only due is written, set totalAmount = dueAmount and paidAmount = 0. If total and paid are written, compute dueAmount = totalAmount - paidAmount.
                - Convert any Bengali numerals (০১২৩৪৫৬৭৮৯) into standard Western numbers.
                - Include rice variety, bag quantity, or remarks in notes if visible.
            """.trimIndent()

            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )
            }

            val jsonText = response.text
            if (jsonText.isNullOrBlank()) {
                return Result.failure(IllegalStateException("AI returned an empty response for this image."))
            }

            val root = JSONObject(jsonText)
            val rowsArray = root.optJSONArray("rows")
            val parsed = mutableListOf<ScannedLedgerRow>()
            if (rowsArray != null) {
                for (i in 0 until rowsArray.length()) {
                    val obj = rowsArray.optJSONObject(i) ?: continue
                    val name = obj.optString("customerName", "").trim()
                    if (name.isEmpty()) continue
                    val total = obj.optDouble("totalAmount", 0.0)
                    val paid = obj.optDouble("paidAmount", 0.0)
                    val due = obj.optDouble("dueAmount", total - paid)
                    parsed.add(
                        ScannedLedgerRow(
                            localId = UUID.randomUUID().toString(),
                            customerName = name,
                            dateText = obj.optString("dateText", ""),
                            totalAmount = formatNumericInput(total),
                            paidAmount = formatNumericInput(paid),
                            dueAmount = formatNumericInput(due),
                            notes = obj.optString("notes", ""),
                        )
                    )
                }
            }
            Result.success(parsed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Khata scan timed out", e)
            Result.failure(IllegalStateException("Ledger scan timed out after 60 seconds. Please try a clearer photo."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Khata scan failed", e)
            Result.failure(e)
        }
    }

    private fun formatNumericInput(value: Double): String {
        return if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.ENGLISH, "%.2f", value)
    }

    private fun decodeScaledBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        val resolver = context.contentResolver
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight
        if (origW <= 0 || origH <= 0) return null

        var sampleSize = 1
        while (origW / sampleSize > maxDimension || origH / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }

    private companion object {
        const val TAG = "KhataAiRepository"
    }
}
