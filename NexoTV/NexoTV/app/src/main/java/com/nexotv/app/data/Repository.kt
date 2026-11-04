package com.nexotv.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID

class Repository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val storage = Storage(context)
    private val mutex = Mutex()

    private val _sources = MutableStateFlow<List<PlaylistSource>>(emptyList())
    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    private val _epgSources = MutableStateFlow<List<EpgSource>>(emptyList())
    private val _favorites = MutableStateFlow<List<String>>(emptyList())
    private val _recents = MutableStateFlow<List<String>>(emptyList())
    private val _settings = MutableStateFlow(AppSettings())
    private val _epg = MutableStateFlow(EpgIndex.EMPTY)
    private val _epgChannels = MutableStateFlow<List<EpgChannelInfo>>(emptyList())
    private val _overrides = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _busy = MutableStateFlow<String?>(null)
    private val _loaded = MutableStateFlow(false)
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)

    val sources: StateFlow<List<PlaylistSource>> = _sources
    val channels: StateFlow<List<Channel>> = _channels
    val epgSources: StateFlow<List<EpgSource>> = _epgSources
    val favorites: StateFlow<List<String>> = _favorites
    val recents: StateFlow<List<String>> = _recents
    val settings: StateFlow<AppSettings> = _settings
    val epg: StateFlow<EpgIndex> = _epg
    val epgChannels: StateFlow<List<EpgChannelInfo>> = _epgChannels
    val overrides: StateFlow<Map<String, String>> = _overrides
    /** Texto de la tarea en curso (null = inactivo). */
    val busy: StateFlow<String?> = _busy
    val loaded: StateFlow<Boolean> = _loaded
    val events: SharedFlow<String> = _events

    /** Lista de canales por la que se hace zapping en el reproductor. */
    @Volatile
    var playQueue: List<Channel> = emptyList()

    fun start() {
        scope.launch {
            _settings.value = storage.loadSettings()
            _sources.value = storage.loadSources()
            _channels.value = storage.loadChannels()
            _epgSources.value = storage.loadEpgSources()
            _favorites.value = storage.loadFavorites()
            _recents.value = storage.loadRecents()
            _overrides.value = storage.loadOverrides()
            storage.loadEpgCache()?.let { cache ->
                _epg.value = EpgIndex(cache.programmes, offsetMs())
                _epgChannels.value = cache.channels
            }
            _loaded.value = true

            val enabledSources = _sources.value.filter { it.enabled }
            if (enabledSources.isNotEmpty() && _channels.value.isEmpty()) {
                refreshAllInternal()
            } else if (epgNeedsRefresh()) {
                mutex.withLock {
                    downloadEpg(_epgSources.value.filter { it.enabled })
                    buildEpg()
                }
            }
        }
    }

    private fun offsetMs() = _settings.value.epgOffsetMinutes * 60_000L

    private fun epgNeedsRefresh(): Boolean {
        val maxAge = _settings.value.epgRefreshHours * 3_600_000L
        val now = System.currentTimeMillis()
        return _epgSources.value.any { it.enabled && now - it.lastUpdated > maxAge }
    }

    fun channelById(id: String): Channel? = _channels.value.firstOrNull { it.id == id }

    // ------------------------------------------------------------------ Listas

    fun addSource(source: PlaylistSource): Job {
        val src = source.copy(id = UUID.randomUUID().toString())
        _sources.value = _sources.value + src
        storage.saveSources(_sources.value)
        return scope.launch {
            mutex.withLock {
                refreshSourcesInternal(listOf(src))
                downloadEpg(_epgSources.value.filter { it.enabled && it.lastUpdated == 0L })
                buildEpg()
            }
        }
    }

    fun updateSource(source: PlaylistSource) {
        _sources.value = _sources.value.map { if (it.id == source.id) source else it }
        storage.saveSources(_sources.value)
        refreshSource(source.id)
    }

    fun removeSource(id: String) {
        scope.launch {
            mutex.withLock {
                _sources.value = _sources.value.filter { it.id != id }
                _channels.value = _channels.value.filter { it.sourceId != id }
                val autoEpg = _epgSources.value.filter { it.autoFromSourceId == id }
                autoEpg.forEach { storage.epgRawFile(it.id).delete() }
                _epgSources.value = _epgSources.value - autoEpg.toSet()
                storage.saveSources(_sources.value)
                storage.saveChannels(_channels.value)
                storage.saveEpgSources(_epgSources.value)
                buildEpg()
            }
        }
    }

    fun refreshSource(id: String) {
        val src = _sources.value.firstOrNull { it.id == id } ?: return
        scope.launch {
            mutex.withLock {
                refreshSourcesInternal(listOf(src))
                downloadEpg(_epgSources.value.filter { it.enabled && it.lastUpdated == 0L })
                buildEpg()
            }
        }
    }

    fun refreshAll() {
        scope.launch { refreshAllInternal() }
    }

    private suspend fun refreshAllInternal() {
        mutex.withLock {
            refreshSourcesInternal(_sources.value.filter { it.enabled })
            downloadEpg(_epgSources.value.filter { it.enabled })
            buildEpg()
        }
    }

    private fun refreshSourcesInternal(list: List<PlaylistSource>) {
        val ua = _settings.value.userAgent
        for (src in list) {
            _busy.value = "Cargando lista «${src.name}»…"
            try {
                val (loaded, epgUrls) = when (src.type) {
                    SourceType.M3U_URL, SourceType.M3U_FILE ->
                        Net.openRaw(context, src.url, ua).use { raw ->
                            Net.maybeGunzip(raw).use { M3uParser.parse(it, src.id) }
                        }.let { it.channels to it.epgUrls }
                    SourceType.XTREAM ->
                        XtreamClient.loadLive(src, _settings.value.xtreamFormat, ua) to listOf(XtreamClient.epgUrl(src))
                }
                if (loaded.isEmpty()) throw IOException("La lista no contiene canales")
                val order = _sources.value.mapIndexed { i, s -> s.id to i }.toMap()
                _channels.value = (_channels.value.filter { it.sourceId != src.id } + loaded)
                    .sortedBy { order[it.sourceId] ?: Int.MAX_VALUE }
                editSource(src.id) {
                    it.copy(lastUpdated = System.currentTimeMillis(), lastStatus = null, channelCount = loaded.size)
                }
                epgUrls.forEach { addAutoEpg(it, src) }
                _events.tryEmit("«${src.name}»: ${loaded.size} canales cargados")
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                editSource(src.id) { it.copy(lastStatus = "Error: $msg") }
                _events.tryEmit("Error al cargar «${src.name}»: $msg")
            }
        }
        storage.saveChannels(_channels.value)
        storage.saveSources(_sources.value)
        _busy.value = null
    }

    private fun editSource(id: String, block: (PlaylistSource) -> PlaylistSource) {
        _sources.value = _sources.value.map { if (it.id == id) block(it) else it }
    }

    private fun addAutoEpg(url: String, src: PlaylistSource) {
        if (_epgSources.value.any { it.url.equals(url, ignoreCase = true) }) return
        _epgSources.value = _epgSources.value + EpgSource(
            id = UUID.randomUUID().toString(),
            name = "Guía de «${src.name}»",
            url = url,
            autoFromSourceId = src.id,
        )
        storage.saveEpgSources(_epgSources.value)
    }

    // ------------------------------------------------------------------ EPG

    fun addEpgSource(name: String, url: String) {
        val e = EpgSource(id = UUID.randomUUID().toString(), name = name.ifBlank { "EPG" }, url = url.trim())
        _epgSources.value = _epgSources.value + e
        storage.saveEpgSources(_epgSources.value)
        scope.launch {
            mutex.withLock {
                downloadEpg(listOf(e))
                buildEpg()
            }
        }
    }

    fun removeEpgSource(id: String) {
        storage.epgRawFile(id).delete()
        _epgSources.value = _epgSources.value.filter { it.id != id }
        storage.saveEpgSources(_epgSources.value)
        rebuildEpg()
    }

    fun setEpgEnabled(id: String, enabled: Boolean) {
        _epgSources.value = _epgSources.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
        storage.saveEpgSources(_epgSources.value)
        scope.launch {
            mutex.withLock {
                if (enabled) downloadEpg(_epgSources.value.filter { it.id == id && !storage.epgRawFile(id).exists() })
                buildEpg()
            }
        }
    }

    /** Cambia la prioridad de una fuente EPG (dir = -1 sube, +1 baja). */
    fun moveEpgSource(id: String, dir: Int) {
        val list = _epgSources.value.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + dir
        if (i < 0 || j < 0 || j >= list.size) return
        val tmp = list[i]
        list[i] = list[j]
        list[j] = tmp
        _epgSources.value = list
        storage.saveEpgSources(list)
        rebuildEpg()
    }

    fun refreshEpg() {
        scope.launch {
            mutex.withLock {
                downloadEpg(_epgSources.value.filter { it.enabled })
                buildEpg()
            }
        }
    }

    fun rebuildEpg() {
        scope.launch { mutex.withLock { buildEpg() } }
    }

    fun setEpgOverride(channelId: String, epgChannelId: String?) {
        val map = _overrides.value.toMutableMap()
        if (epgChannelId == null) map.remove(channelId) else map[channelId] = epgChannelId
        _overrides.value = map
        storage.saveOverrides(map)
        rebuildEpg()
    }

    private fun downloadEpg(list: List<EpgSource>) {
        val ua = _settings.value.userAgent
        for (e in list) {
            _busy.value = "Descargando guía «${e.name}»…"
            try {
                val target = storage.epgRawFile(e.id)
                val tmp = File(target.path + ".tmp")
                Net.openRaw(context, e.url, ua).use { input ->
                    tmp.outputStream().use { out -> input.copyTo(out, 1 shl 16) }
                }
                if (tmp.length() == 0L) throw IOException("Archivo vacío")
                if (!tmp.renameTo(target)) throw IOException("No se pudo guardar la guía")
                editEpg(e.id) { it.copy(lastUpdated = System.currentTimeMillis(), lastStatus = "Descargada") }
            } catch (ex: Exception) {
                val msg = ex.message ?: ex.javaClass.simpleName
                editEpg(e.id) { it.copy(lastStatus = "Error: $msg") }
                _events.tryEmit("Error en la guía «${e.name}»: $msg")
            }
        }
        storage.saveEpgSources(_epgSources.value)
        _busy.value = null
    }

    private fun buildEpg() {
        val sources = _epgSources.value.filter { it.enabled }
        _busy.value = "Procesando guía…"
        val now = System.currentTimeMillis()
        val days = _settings.value.epgDaysAhead.coerceIn(1, 14)
        val matcher = EpgMatcher(
            channels = _channels.value,
            overrides = _overrides.value,
            windowStart = now - 6 * 3_600_000L,
            windowEnd = now + days * 86_400_000L,
        )
        for (e in sources) {
            val file = storage.epgRawFile(e.id)
            if (!file.exists()) continue
            val status = try {
                Net.maybeGunzip(BufferedInputStream(FileInputStream(file), 1 shl 16)).use { input ->
                    val s = matcher.addSource(e.id, input)
                    "${s.matchedChannels} canales con guía · ${s.programmes} programas"
                }
            } catch (ex: Exception) {
                val s = matcher.lastStats
                "Archivo dañado o incompleto (${ex.message ?: "error"})" +
                    (s?.let { " · ${it.matchedChannels} canales leídos" } ?: "")
            }
            editEpg(e.id) { it.copy(lastStatus = status) }
        }
        _epg.value = EpgIndex(HashMap(matcher.result), offsetMs())
        _epgChannels.value = matcher.epgChannels.distinctBy { it.sourceId + "|" + it.id }
        storage.saveEpgSources(_epgSources.value)
        storage.saveEpgCache(_epg.value.raw, _epgChannels.value)
        _busy.value = null
        if (sources.isNotEmpty()) {
            _events.tryEmit("Guía actualizada: ${matcher.result.size} de ${_channels.value.size} canales con programación")
        }
    }

    private fun editEpg(id: String, block: (EpgSource) -> EpgSource) {
        _epgSources.value = _epgSources.value.map { if (it.id == id) block(it) else it }
    }

    // ------------------------------------------------------------------ Favoritos / recientes / ajustes

    fun isFavorite(id: String) = id in _favorites.value

    fun toggleFavorite(id: String) {
        val list = _favorites.value
        _favorites.value = if (id in list) list - id else list + id
        scope.launch { storage.saveFavorites(_favorites.value) }
    }

    fun addRecent(id: String) {
        _recents.value = (listOf(id) + _recents.value.filter { it != id }).take(30)
        scope.launch { storage.saveRecents(_recents.value) }
    }

    fun clearRecents() {
        _recents.value = emptyList()
        scope.launch { storage.saveRecents(emptyList()) }
    }

    fun updateSettings(block: (AppSettings) -> AppSettings) {
        val old = _settings.value
        val new = block(old)
        _settings.value = new
        scope.launch { storage.saveSettings(new) }
        if (old.epgOffsetMinutes != new.epgOffsetMinutes) {
            _epg.value = _epg.value.withOffset(offsetMs())
        }
        if (old.epgDaysAhead != new.epgDaysAhead) rebuildEpg()
    }
}
