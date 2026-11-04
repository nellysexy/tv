package com.nexotv.app.data

import kotlinx.serialization.Serializable

const val NO_GROUP = "Sin grupo"

@Serializable
enum class SourceType { M3U_URL, M3U_FILE, XTREAM }

@Serializable
data class PlaylistSource(
    val id: String,
    val name: String,
    val type: SourceType,
    val url: String = "",
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val enabled: Boolean = true,
    val lastUpdated: Long = 0L,
    val lastStatus: String? = null,
    val channelCount: Int = 0,
)

@Serializable
data class Channel(
    val id: String,
    val sourceId: String,
    val name: String,
    val url: String,
    val logo: String? = null,
    val group: String = NO_GROUP,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    val number: Int = 0,
)

@Serializable
data class EpgSource(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    /** Si se añadió automáticamente desde una lista (url-tvg o Xtream), id de esa lista. */
    val autoFromSourceId: String? = null,
    val lastUpdated: Long = 0L,
    val lastStatus: String? = null,
)

@Serializable
data class Programme(
    val start: Long,
    val stop: Long,
    val title: String,
    val desc: String? = null,
    val category: String? = null,
)

@Serializable
data class EpgChannelInfo(
    val id: String,
    val name: String,
    val sourceId: String,
)

@Serializable
data class AppSettings(
    /** Altura máxima de vídeo en píxeles. 0 = sin límite. */
    val maxVideoHeight: Int = 1080,
    /** Ignora el ancho de banda estimado y el tamaño de pantalla: siempre la mejor calidad permitida. */
    val forceHighestQuality: Boolean = false,
    val bufferSeconds: Int = 15,
    val userAgent: String = Net.DEFAULT_UA,
    val epgOffsetMinutes: Int = 0,
    val epgRefreshHours: Int = 12,
    val epgDaysAhead: Int = 3,
    /** "ts" o "m3u8" para las listas Xtream Codes. */
    val xtreamFormat: String = "ts",
    /** 0 = ajustar, 1 = rellenar, 2 = zoom */
    val resizeMode: Int = 0,
)
