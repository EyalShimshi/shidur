package io.github.eyalshimshi.shidur.model

import java.util.Locale

data class StationStream(
    val url: String,
    val format: String? = null,
    val bitrate: Int? = null,
)

data class Station(
    val id: String,
    val nameHe: String,
    val nameEn: String,
    /** Text drawn on the station's monogram artwork, e.g. "88" or "גלגלצ". */
    val shortName: String,
    /** ARGB color used for the monogram and the station-tinted player. */
    val color: Int,
    val network: String,
    val collections: List<String>,
    val frequencies: List<Double>,
    val primaryFrequency: Double?,
    val aliases: List<String>,
    val website: String?,
    val streams: List<StationStream>,
    /** Charset of in-stream ICY titles: "utf-8", "windows-1255" or null to auto-detect. */
    val icyCharset: String?,
    val rank: Int,
    val isCustom: Boolean = false,
    /** Address of the station's logo image, or null to always use the text artwork. */
    val logo: String? = null,
) {
    fun name(hebrew: Boolean): String = if (hebrew) nameHe else nameEn
}

data class StationCollection(
    val id: String,
    val nameHe: String,
    val nameEn: String,
    val order: Int,
) {
    fun name(hebrew: Boolean): String = if (hebrew) nameHe else nameEn
}

data class Catalog(
    val version: Long,
    val collections: List<StationCollection>,
    val stations: List<Station>,
)

fun formatFrequency(mhz: Double): String = String.format(Locale.US, "%.1f FM", mhz)
