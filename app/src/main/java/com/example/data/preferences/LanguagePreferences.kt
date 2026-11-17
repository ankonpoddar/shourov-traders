package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

enum class LanguageMode(val shortLabel: String) {
    BILINGUAL("EN+বাং"),
    BENGALI("বাংলা"),
    ENGLISH("EN"),
}

private val Context.languageDataStore: DataStore<Preferences> by preferencesDataStore(name = "shourav_prefs")

class LanguagePreferences(private val context: Context) {
    private val keyMode = stringPreferencesKey("language_mode")

    val languageMode: Flow<LanguageMode> = context.languageDataStore.data.map { prefs ->
        val raw = prefs[keyMode]
        LanguageMode.entries.firstOrNull { it.name == raw } ?: LanguageMode.BILINGUAL
    }

    suspend fun setLanguageMode(mode: LanguageMode) {
        context.languageDataStore.edit { prefs ->
            prefs[keyMode] = mode.name
        }
    }
}

fun bi(en: String, bn: String, mode: LanguageMode): String = when (mode) {
    LanguageMode.BILINGUAL -> "$en · $bn"
    LanguageMode.BENGALI -> bn
    LanguageMode.ENGLISH -> en
}

/**
 * Formats BDT amounts using Bangladeshi/South Asian grouping (e.g., ৳ 1,25,400).
 */
fun formatBdt(amount: Double, showPositiveSign: Boolean = false): String {
    val isNegative = amount < -0.005
    val rounded = abs(amount).roundToLong()
    val digits = rounded.toString()
    val grouped = if (digits.length <= 3) {
        digits
    } else {
        val lastThree = digits.takeLast(3)
        val remaining = digits.dropLast(3)
        val chunks = mutableListOf<String>()
        var i = remaining.length
        while (i > 0) {
            val start = (i - 2).coerceAtLeast(0)
            chunks.add(remaining.substring(start, i))
            i = start
        }
        chunks.reverse()
        chunks.joinToString(",") + "," + lastThree
    }
    val prefix = when {
        isNegative -> "-৳ "
        showPositiveSign && rounded > 0L -> "+৳ "
        else -> "৳ "
    }
    return "$prefix$grouped"
}

fun formatShortDate(millis: Long): String {
    if (millis <= 0L) return "Today"
    return SimpleDateFormat("dd MMM yy", Locale.ENGLISH).format(Date(millis))
}

fun formatDayMonth(millis: Long): String {
    if (millis <= 0L) return ""
    return SimpleDateFormat("dd MMM", Locale.ENGLISH).format(Date(millis))
}

/**
 * Parses numeric input accepting English digits, Bengali numerals (০-৯), commas, currency symbol, and spaces.
 */
fun parseLocalizedDouble(raw: String): Double? {
    val normalized = buildString(raw.length) {
        for (ch in raw.trim()) {
            when (ch) {
                in '০'..'৯' -> append(('0'.code + (ch.code - '০'.code)).toChar())
                ',', ' ', '\u00A0', '৳' -> Unit
                else -> append(ch)
            }
        }
    }
    if (normalized.isEmpty()) return null
    val value = normalized.toDoubleOrNull() ?: return null
    return value.takeIf { it.isFinite() }
}

/**
 * Formats numbers for editable decimal text fields without truncating fractional paisa values.
 */
fun formatEditableNumber(value: Double): String {
    if (!value.isFinite() || value <= 0.0) return "0"
    val rounded = kotlin.math.round(value * 100.0) / 100.0
    return if (rounded % 1.0 == 0.0) {
        rounded.toLong().toString()
    } else {
        String.format(Locale.ENGLISH, "%.2f", rounded).trimEnd('0').trimEnd('.')
    }
}

