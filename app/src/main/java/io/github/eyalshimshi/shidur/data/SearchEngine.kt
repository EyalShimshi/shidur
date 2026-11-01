package io.github.eyalshimshi.shidur.data

import io.github.eyalshimshi.shidur.model.Station
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/** Station search tolerant of Hebrew spelling variants, transliterations and frequencies. */
object SearchEngine {

    private val frequencyRegex = Regex("""^(\d{2,3})(?:[.,](\d))?\s*(?:fm|mhz)?$""", RegexOption.IGNORE_CASE)

    /**
     * Lowercases, strips niqqud, punctuation, quotes (צה"ל → צהל) and spaces, and maps Hebrew final
     * letters to their regular forms.
     */
    fun normalize(input: String): String {
        val decomposed = Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFKD)
        val out = StringBuilder(decomposed.length)
        for (ch in decomposed) {
            when {
                ch in '\u0591'..'\u05C7' -> Unit
                ch == 'ך' -> out.append('כ')
                ch == 'ם' -> out.append('מ')
                ch == 'ן' -> out.append('נ')
                ch == 'ף' -> out.append('פ')
                ch == 'ץ' -> out.append('צ')
                Character.getType(ch) == Character.NON_SPACING_MARK.toInt() -> Unit
                ch.isLetterOrDigit() -> out.append(ch)
                else -> Unit
            }
        }
        return out.toString()
    }

    /** Parses "88", "88.0", "107,5" or "103FM" into a frequency in the FM band. */
    fun parseFrequency(query: String): Double? {
        val match = frequencyRegex.matchEntire(query.trim()) ?: return null
        val whole = match.groupValues[1]
        val fraction = match.groupValues[2]
        val value = (if (fraction.isEmpty()) whole else "$whole.$fraction").toDoubleOrNull() ?: return null
        return value.takeIf { it in 87.0..108.9 }
    }

    fun search(query: String, stations: List<Station>): List<Station> {
        val normalized = normalize(query)
        if (normalized.isEmpty()) return emptyList()
        val frequency = parseFrequency(query)
        val wholeNumber = frequency != null && !query.contains('.') && !query.contains(',')
        return stations
            .map { it to score(it, normalized, frequency, wholeNumber) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Station, Int>> { it.second }.thenBy { it.first.rank })
            .map { it.first }
    }

    private fun score(station: Station, query: String, frequency: Double?, wholeNumber: Boolean): Int {
        var best = 0
        if (frequency != null) {
            if (station.frequencies.any { abs(it - frequency) < 0.05 }) {
                best = 100
            } else if (wholeNumber && station.frequencies.any { floor(it) == frequency }) {
                best = 90
            }
        }
        val names = listOf(station.nameHe, station.nameEn, station.shortName).map(::normalize)
        val aliases = station.aliases.map(::normalize)
        val textScore = when {
            names.any { it == query } -> 95
            aliases.any { it == query } -> 85
            names.any { it.startsWith(query) } -> 80
            aliases.any { it.startsWith(query) } -> 70
            names.any { it.contains(query) } -> 50
            aliases.any { it.contains(query) } -> 40
            else -> 0
        }
        return maxOf(best, textScore)
    }
}
