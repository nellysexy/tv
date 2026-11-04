package com.nexotv.app.data

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class Storage(context: Context) {
    private val dir: File = context.filesDir
    private val epgDir: File = File(context.filesDir, "epg").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun loadSources(): List<PlaylistSource> = read<List<PlaylistSource>>("sources.json") ?: emptyList()
    fun saveSources(v: List<PlaylistSource>) = write("sources.json", v)
    fun loadChannels(): List<Channel> = read<List<Channel>>("channels.json") ?: emptyList()
    fun saveChannels(v: List<Channel>) = write("channels.json", v)
    fun loadEpgSources(): List<EpgSource> = read<List<EpgSource>>("epg_sources.json") ?: emptyList()
    fun saveEpgSources(v: List<EpgSource>) = write("epg_sources.json", v)
    fun loadFavorites(): List<String> = read<List<String>>("favorites.json") ?: emptyList()
    fun saveFavorites(v: List<String>) = write("favorites.json", v)
    fun loadRecents(): List<String> = read<List<String>>("recents.json") ?: emptyList()
    fun saveRecents(v: List<String>) = write("recents.json", v)
    fun loadOverrides(): Map<String, String> = read<Map<String, String>>("epg_overrides.json") ?: emptyMap()
    fun saveOverrides(v: Map<String, String>) = write("epg_overrides.json", v)
    fun loadSettings(): AppSettings = read<AppSettings>("settings.json") ?: AppSettings()
    fun saveSettings(v: AppSettings) = write("settings.json", v)

    fun epgRawFile(epgId: String) = File(epgDir, "$epgId.xml")

    private inline fun <reified T> read(name: String): T? = try {
        val f = File(dir, name)
        if (f.exists()) json.decodeFromString<T>(f.readText()) else null
    } catch (e: Exception) {
        null
    }

    private inline fun <reified T> write(name: String, value: T) {
        synchronized(this) {
            try {
                val tmp = File(dir, "$name.tmp")
                tmp.writeText(json.encodeToString(value))
                tmp.renameTo(File(dir, name))
            } catch (_: Exception) {
            }
        }
    }

    // ---- Caché binaria de la guía (mucho más rápida que JSON para cientos de miles de programas) ----

    class EpgCache(val programmes: Map<String, List<Programme>>, val channels: List<EpgChannelInfo>)

    private val epgCacheFile get() = File(dir, "epg_cache.bin")

    fun saveEpgCache(programmes: Map<String, List<Programme>>, channels: List<EpgChannelInfo>) {
        synchronized(this) {
            try {
                val tmp = File(dir, "epg_cache.bin.tmp")
                DataOutputStream(BufferedOutputStream(FileOutputStream(tmp), 1 shl 16)).use { out ->
                    out.writeInt(EPG_CACHE_VERSION)
                    out.writeInt(channels.size)
                    for (c in channels) {
                        out.writeUTF(c.id.take(500))
                        out.writeUTF(c.name.take(500))
                        out.writeUTF(c.sourceId)
                    }
                    out.writeInt(programmes.size)
                    for ((key, list) in programmes) {
                        out.writeUTF(key)
                        out.writeInt(list.size)
                        for (p in list) {
                            out.writeLong(p.start)
                            out.writeLong(p.stop)
                            out.writeUTF(p.title.take(300))
                            out.writeUTF(p.desc?.take(1000) ?: "")
                            out.writeUTF(p.category?.take(100) ?: "")
                        }
                    }
                }
                tmp.renameTo(epgCacheFile)
            } catch (_: Exception) {
            }
        }
    }

    fun loadEpgCache(): EpgCache? = try {
        val f = epgCacheFile
        if (!f.exists()) null else DataInputStream(BufferedInputStream(FileInputStream(f), 1 shl 16)).use { input ->
            if (input.readInt() != EPG_CACHE_VERSION) return null
            val chCount = input.readInt()
            val channels = ArrayList<EpgChannelInfo>(chCount)
            repeat(chCount) {
                channels.add(EpgChannelInfo(input.readUTF(), input.readUTF(), input.readUTF()))
            }
            val mapCount = input.readInt()
            val map = HashMap<String, List<Programme>>(mapCount * 2)
            repeat(mapCount) {
                val key = input.readUTF()
                val n = input.readInt()
                val list = ArrayList<Programme>(n)
                repeat(n) {
                    val start = input.readLong()
                    val stop = input.readLong()
                    val title = input.readUTF()
                    val desc = input.readUTF().ifEmpty { null }
                    val cat = input.readUTF().ifEmpty { null }
                    list.add(Programme(start, stop, title, desc, cat))
                }
                map[key] = list
            }
            EpgCache(map, channels)
        }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val EPG_CACHE_VERSION = 1
    }
}
