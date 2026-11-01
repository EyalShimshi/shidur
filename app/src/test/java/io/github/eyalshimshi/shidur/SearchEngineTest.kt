package io.github.eyalshimshi.shidur

import io.github.eyalshimshi.shidur.data.SearchEngine
import io.github.eyalshimshi.shidur.model.Station
import io.github.eyalshimshi.shidur.model.StationStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {

    private fun station(
        id: String,
        nameHe: String,
        nameEn: String,
        shortName: String,
        fm: List<Double> = emptyList(),
        aliases: List<String> = emptyList(),
        rank: Int = 0,
    ) = Station(
        id = id,
        nameHe = nameHe,
        nameEn = nameEn,
        shortName = shortName,
        color = 0,
        network = "test",
        collections = emptyList(),
        frequencies = fm,
        primaryFrequency = fm.firstOrNull(),
        aliases = aliases,
        website = null,
        streams = listOf(StationStream("https://example.com/stream")),
        icyCharset = null,
        rank = rank,
    )

    private val stations = listOf(
        station("kan-bet", "כאן ב׳", "Kan Bet", "ב׳", listOf(95.5, 88.5), listOf("רשת ב", "reshet bet"), rank = 1),
        station("kan-88", "כאן 88", "Kan 88", "88", listOf(88.0), listOf("88fm"), rank = 2),
        station("glglz", "גלגלצ", "Galgalatz", "גלגלצ", listOf(91.8), listOf("גלגל״צ", "galgalatz", "glglz"), rank = 3),
        station("glz", "גלי צה״ל", "Galei Tzahal", "גל״צ", listOf(104.0), listOf("גלצ", "galatz"), rank = 4),
        station("radio-haifa", "רדיו חיפה 107.5", "Radio Haifa 107.5", "107.5", listOf(107.5), listOf("חיפה"), rank = 5),
    )

    @Test
    fun wholeFrequencyPrefersExactMatch() {
        assertEquals("kan-88", SearchEngine.search("88", stations).first().id)
    }

    @Test
    fun decimalFrequencyWithCommaMatches() {
        assertEquals("radio-haifa", SearchEngine.search("107,5", stations).first().id)
    }

    @Test
    fun hebrewQuotesAreIgnored() {
        assertEquals("glz", SearchEngine.search("צהל", stations).first().id)
        assertEquals("glglz", SearchEngine.search("גלגל\"צ", stations).first().id)
    }

    @Test
    fun transliterationsMatch() {
        assertEquals("glglz", SearchEngine.search("glglz", stations).first().id)
        assertEquals("glglz", SearchEngine.search("Galgalatz", stations).first().id)
        assertEquals("kan-bet", SearchEngine.search("reshet bet", stations).first().id)
    }

    @Test
    fun finalLettersAreNormalized() {
        assertEquals(SearchEngine.normalize("כאן"), SearchEngine.normalize("כאנ"))
    }

    @Test
    fun parseFrequencyRejectsOutOfBandNumbers() {
        assertNull(SearchEngine.parseFrequency("12"))
        assertEquals(103.0, SearchEngine.parseFrequency("103FM")!!, 0.001)
    }

    @Test
    fun blankQueryReturnsNothing() {
        assertTrue(SearchEngine.search("  ", stations).isEmpty())
    }
}
