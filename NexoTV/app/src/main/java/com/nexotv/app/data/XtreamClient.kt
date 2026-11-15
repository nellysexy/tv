package com.nexotv.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException
import java.net.URLEncoder

/** Cliente mínimo de la API "player_api.php" de Xtream Codes (solo TV en directo). */
object XtreamClient {

    fun normalizeServer(server: String): String {
        var s = server.trim().trimEnd('/')
        if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
        return s
    }

    private fun query(src: PlaylistSource): String =
        "username=" + URLEncoder.encode(src.username.trim(), "UTF-8") +
            "&password=" + URLEncoder.encode(src.password.trim(), "UTF-8")

    fun epgUrl(src: PlaylistSource): String = "${normalizeServer(src.server)}/xmltv.php?${query(src)}"

    fun loadLive(src: PlaylistSource, format: String, userAgent: String): List<Channel> {
        val base = normalizeServer(src.server)
        val api = "$base/player_api.php?${query(src)}"

        val auth = parse(Net.httpGetString(api, userAgent))
        val userInfo = (auth as? JsonObject)?.get("user_info") as? JsonObject
        if (userInfo != null && userInfo.str("auth") == "0") throw IOException("Usuario o contraseña incorrectos")

        val categories = parse(Net.httpGetString("$api&action=get_live_categories", userAgent)) as? JsonArray
        val catNames = HashMap<String, String>()
        categories?.forEach { el ->
            val o = el as? JsonObject ?: return@forEach
            val id = o.str("category_id") ?: return@forEach
            catNames[id] = o.str("category_name") ?: NO_GROUP
        }

        val streams = parse(Net.httpGetString("$api&action=get_live_streams", userAgent)) as? JsonArray
            ?: throw IOException("Respuesta no válida del servidor (¿credenciales incorrectas?)")

        val ext = if (format == "m3u8") "m3u8" else "ts"
        val user = src.username.trim()
        val pass = src.password.trim()
        val result = ArrayList<Channel>(streams.size)
        val used = HashSet<String>()
        streams.forEachIndexed { index, el ->
            val o = el as? JsonObject ?: return@forEachIndexed
            val streamId = o.str("stream_id") ?: return@forEachIndexed
            val id = "${src.id}:$streamId"
            if (!used.add(id)) return@forEachIndexed
            val catId = o.str("category_id")
            result.add(
                Channel(
                    id = id,
                    sourceId = src.id,
                    name = o.str("name")?.trim().orEmpty().ifBlank { "Canal $streamId" },
                    url = "$base/live/$user/$pass/$streamId.$ext",
                    logo = o.str("stream_icon")?.takeIf { it.isNotBlank() },
                    group = catId?.let { catNames[it] } ?: NO_GROUP,
                    tvgId = o.str("epg_channel_id")?.takeIf { it.isNotBlank() },
                    number = o.str("num")?.toIntOrNull() ?: (index + 1),
                )
            )
        }
        return result
    }

    private fun parse(text: String): JsonElement? =
        try { Json.parseToJsonElement(text) } catch (e: Exception) { null }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
