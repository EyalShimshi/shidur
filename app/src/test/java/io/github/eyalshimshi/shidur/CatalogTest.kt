package io.github.eyalshimshi.shidur

import io.github.eyalshimshi.shidur.model.CatalogDto
import io.github.eyalshimshi.shidur.model.SUPPORTED_SCHEMA_VERSION
import io.github.eyalshimshi.shidur.model.toCatalog
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Validates the station catalog bundled in the APK, so a typo in catalog.json fails CI. */
class CatalogTest {

    // Unit tests run with the module directory (app/) as the working directory.
    private val dto: CatalogDto = Json { ignoreUnknownKeys = true }
        .decodeFromString(CatalogDto.serializer(), File("src/main/assets/catalog.json").readText(Charsets.UTF_8))

    @Test
    fun catalogUsesSupportedSchemaAndHasStations() {
        assertEquals(SUPPORTED_SCHEMA_VERSION, dto.schemaVersion)
        assertTrue("expected at least 20 active stations", dto.toCatalog().stations.size >= 20)
    }

    @Test
    fun stationIdsAreUniqueAndUrlSafe() {
        val ids = dto.stations.map { it.id }
        assertEquals("duplicate station ids", ids.size, ids.toSet().size)
        val idPattern = Regex("^[a-z0-9-]+$")
        ids.forEach { id -> assertTrue("invalid station id: $id", idPattern.matches(id)) }
    }

    @Test
    fun everyStationIsComplete() {
        val collectionIds = dto.collections.map { it.id }.toSet()
        dto.stations.forEach { station ->
            assertTrue("${station.id}: missing names", station.nameHe.isNotBlank() && station.nameEn.isNotBlank())
            assertTrue("${station.id}: blank short name", station.shortName.isNotBlank())
            assertTrue("${station.id}: no streams", station.streams.isNotEmpty())
            station.streams.forEach { stream ->
                assertTrue(
                    "${station.id}: bad stream url ${stream.url}",
                    stream.url.startsWith("https://") || stream.url.startsWith("http://"),
                )
            }
            station.collections.forEach { collection ->
                assertTrue("${station.id}: unknown collection $collection", collection in collectionIds)
            }
            station.fm.forEach { mhz -> assertTrue("${station.id}: odd frequency $mhz", mhz in 87.5..108.0) }
            station.logo?.let { logo ->
                // Android can't decode SVG files; Wikimedia's PNG thumbnails of them are fine.
                assertTrue("${station.id}: logo must be https", logo.startsWith("https://"))
                assertTrue("${station.id}: logo must not be an SVG file", !logo.lowercase().endsWith(".svg"))
            }
        }
    }
}
