package io.github.eyalshimshi.shidur.model

import kotlinx.serialization.Serializable

const val SUPPORTED_SCHEMA_VERSION = 1

@Serializable
data class CatalogDto(
    val schemaVersion: Int = SUPPORTED_SCHEMA_VERSION,
    val catalogVersion: Long = 0,
    val collections: List<CollectionDto> = emptyList(),
    val stations: List<StationDto> = emptyList(),
)

@Serializable
data class CollectionDto(
    val id: String,
    val nameHe: String,
    val nameEn: String,
    val order: Int = 0,
)

@Serializable
data class StreamDto(
    val url: String,
    val format: String? = null,
    val bitrate: Int? = null,
)

@Serializable
data class StationDto(
    val id: String,
    val nameHe: String,
    val nameEn: String,
    val shortName: String,
    val color: String = "#6750A4",
    val network: String = "other",
    val collections: List<String> = emptyList(),
    val fm: List<Double> = emptyList(),
    val primaryFm: Double? = null,
    val aliases: List<String> = emptyList(),
    val website: String? = null,
    /** Address of the station's logo image (PNG, JPEG or WebP); downloaded by the app at runtime. */
    val logo: String? = null,
    val icyCharset: String? = null,
    val rank: Int = 1000,
    val status: String = "active",
    val streams: List<StreamDto> = emptyList(),
)

/** A station the user added by URL. Stored only on the device. */
@Serializable
data class CustomStationDto(
    val id: String,
    val name: String,
    val url: String,
)

fun CatalogDto.toCatalog(): Catalog = Catalog(
    version = catalogVersion,
    collections = collections
        .sortedBy { it.order }
        .map { StationCollection(it.id, it.nameHe, it.nameEn, it.order) },
    stations = stations
        .filter { it.status == "active" && it.streams.isNotEmpty() }
        .sortedBy { it.rank }
        .map { it.toStation() },
)

fun StationDto.toStation(): Station = Station(
    id = id,
    nameHe = nameHe,
    nameEn = nameEn,
    shortName = shortName,
    color = parseColor(color),
    network = network,
    collections = collections,
    frequencies = fm,
    primaryFrequency = primaryFm,
    aliases = aliases,
    website = website,
    streams = streams.map { StationStream(it.url, it.format, it.bitrate) },
    icyCharset = icyCharset,
    rank = rank,
    logo = logo,
)

fun CustomStationDto.toStation(): Station = Station(
    id = id,
    nameHe = name,
    nameEn = name,
    shortName = name.trim().take(2).ifEmpty { "FM" },
    color = colorForId(id),
    network = "custom",
    collections = emptyList(),
    frequencies = emptyList(),
    primaryFrequency = null,
    aliases = emptyList(),
    website = null,
    streams = listOf(StationStream(url)),
    icyCharset = null,
    rank = Int.MAX_VALUE,
    isCustom = true,
)

private val DEFAULT_COLOR: Int = 0xFF6750A4.toInt()

private val CUSTOM_PALETTE = intArrayOf(
    0xFF6750A4.toInt(),
    0xFF00897B.toInt(),
    0xFFF4511E.toInt(),
    0xFF3949AB.toInt(),
    0xFFD81B60.toInt(),
    0xFF7CB342.toInt(),
)

fun colorForId(id: String): Int = CUSTOM_PALETTE[(id.hashCode() and 0x7fffffff) % CUSTOM_PALETTE.size]

private fun parseColor(hex: String): Int = runCatching {
    val clean = hex.removePrefix("#")
    when (clean.length) {
        6 -> (0xFF000000L or clean.toLong(16)).toInt()
        8 -> clean.toLong(16).toInt()
        else -> DEFAULT_COLOR
    }
}.getOrDefault(DEFAULT_COLOR)
