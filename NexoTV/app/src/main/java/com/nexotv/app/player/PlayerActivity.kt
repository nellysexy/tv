package com.nexotv.app.player

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nexotv.app.NexoApp
import com.nexotv.app.data.Channel
import com.nexotv.app.ui.ChannelLogo
import com.nexotv.app.ui.NexoTheme
import com.nexotv.app.ui.focusBorder
import com.nexotv.app.ui.formatTime
import com.nexotv.app.ui.isTv
import com.nexotv.app.ui.rememberNow
import kotlinx.coroutines.delay
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.Locale
import kotlin.math.abs
import org.videolan.libvlc.MediaPlayer as VlcPlayer

/** Motor que está reproduciendo el canal actual. */
enum class Engine { EXO, VLC }

/** Una opción del diálogo de pistas (audio, vídeo o subtítulos). */
internal class TrackItem(
    val label: String,
    val selected: Boolean,
    val language: String? = null,
    val onClick: () -> Unit,
)

@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private val repo get() = NexoApp.repo
    private lateinit var player: ExoPlayer
    private val handler = Handler(Looper.getMainLooper())

    private var queue: List<Channel> = emptyList()
    private var index by mutableIntStateOf(0)
    private var overlayVisible by mutableStateOf(true)
    private var listVisible by mutableStateOf(false)
    private var overlayTick by mutableIntStateOf(0)
    private var focusOverlay by mutableStateOf(false)
    private var isPlaying by mutableStateOf(false)
    private var buffering by mutableStateOf(true)
    private var errorText by mutableStateOf<String?>(null)
    private var notice by mutableStateOf<String?>(null)
    private var videoInfo by mutableStateOf<String?>(null)
    private var tracksVersion by mutableIntStateOf(0)
    private var trackDialog by mutableStateOf<Int?>(null)
    private var resizeMode by mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)
    private var engine by mutableStateOf(Engine.EXO)

    private var retries = 0
    private var triedHls = false
    private var forceHls = false
    private val retryRunnable = Runnable { startChannel(index, isRetry = true) }

    // ExoPlayer: detección de "hay sonido pero no imagen".
    private var firstFrameRendered = false
    private val noVideoCheck = Runnable { checkNoVideo() }
    private val clearNotice = Runnable { notice = null }

    // VLC (motor de respaldo con decodificadores por software).
    private var libVlc: LibVLC? = null
    private var vlc: VlcPlayer? = null
    private var vlcLayoutAttached = false
    private var vlcPending: Media? = null
    private var vlcFailed = false
    private var stoppedInBackground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        queue = repo.playQueue.ifEmpty { repo.channels.value }
        val id = intent.getStringExtra(EXTRA_CHANNEL_ID)
        if (queue.isEmpty()) {
            finish()
            return
        }
        index = queue.indexOfFirst { it.id == id }.coerceAtLeast(0)
        resizeMode = when (repo.settings.value.resizeMode) {
            1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }

        buildPlayer()
        startChannel(index)

        setContent { NexoTheme { PlayerScreen() } }
    }

    private fun buildPlayer() {
        val s = repo.settings.value
        val bufferMs = s.bufferSeconds.coerceIn(5, 120) * 1000
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(bufferMs, maxOf(bufferMs, 50_000), 2_500, 5_000)
            .build()
        val bandwidthMeter = DefaultBandwidthMeter.Builder(this)
            .setInitialBitrateEstimate(10_000_000L)
            .build()
        val renderers = DefaultRenderersFactory(this).setEnableDecoderFallback(true)

        player = ExoPlayer.Builder(this, renderers)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .build()

        val params = player.trackSelectionParameters.buildUpon()
        if (s.maxVideoHeight > 0) params.setMaxVideoSize(Int.MAX_VALUE, s.maxVideoHeight)
        if (s.forceHighestQuality) {
            params.setForceHighestSupportedBitrate(true)
            params.clearViewportSizeConstraints()
        }
        if (s.preferredAudioLanguage.isNotEmpty()) params.setPreferredAudioLanguages(s.preferredAudioLanguage)
        player.trackSelectionParameters = params.build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (engine != Engine.EXO) return
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    errorText = null
                    retries = 0
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                if (engine != Engine.EXO) return
                isPlaying = playing
                if (playing && !firstFrameRendered) {
                    handler.removeCallbacks(noVideoCheck)
                    handler.postDelayed(noVideoCheck, NO_VIDEO_TIMEOUT_MS)
                }
            }

            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
                handler.removeCallbacks(noVideoCheck)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (engine == Engine.EXO) updateVideoInfo()
            }

            override fun onTracksChanged(tracks: Tracks) {
                if (engine != Engine.EXO) return
                tracksVersion++
                updateVideoInfo()
                checkUnsupportedTracks(tracks)
            }

            override fun onPlayerError(error: PlaybackException) {
                if (engine == Engine.EXO) handleError(error)
            }
        })
    }

    /** Elige el motor para el canal: el que el usuario fijó, el que hizo falta antes o el de los ajustes. */
    private fun engineFor(ch: Channel): Engine {
        engineOverrides[ch.id]?.let { return it }
        return if (repo.settings.value.playerEngine == 2 && !vlcFailed) Engine.VLC else Engine.EXO
    }

    /** El cambio automático a VLC solo se hace en modo automático y si el usuario no fijó el motor del canal. */
    private fun autoSwitchAllowed(): Boolean {
        val ch = queue.getOrNull(index) ?: return false
        return repo.settings.value.playerEngine == 0 && !vlcFailed && engineOverrides[ch.id] == null
    }

    private fun startChannel(i: Int, isRetry: Boolean = false, forceEngine: Engine? = null) {
        if (queue.isEmpty()) return
        handler.removeCallbacks(retryRunnable)
        handler.removeCallbacks(noVideoCheck)
        val newIndex = ((i % queue.size) + queue.size) % queue.size
        if (!isRetry) {
            retries = 0
            triedHls = false
            forceHls = false
            videoInfo = null
            repo.addRecent(queue[newIndex].id)
        }
        index = newIndex
        errorText = null
        val ch = queue[newIndex]
        val target = forceEngine ?: engineFor(ch)
        if (target == Engine.VLC && startVlc(ch)) return
        startExo(ch)
    }

    private fun startExo(ch: Channel) {
        stopVlc()
        engine = Engine.EXO
        firstFrameRendered = false
        buffering = true
        val s = repo.settings.value

        val headers = HashMap<String, String>()
        ch.referrer?.let { headers["Referer"] = it }
        val dataSource = OkHttpDataSource.Factory(com.nexotv.app.data.Net.client)
            .setUserAgent(ch.userAgent ?: s.userAgent)
            .setDefaultRequestProperties(headers)

        val lower = ch.url.lowercase(Locale.ROOT)
        val mime = when {
            forceHls || lower.contains(".m3u8") -> MimeTypes.APPLICATION_M3U8
            lower.contains(".mpd") -> MimeTypes.APPLICATION_MPD
            else -> null
        }
        val item = MediaItem.Builder().setUri(ch.url).apply { if (mime != null) setMimeType(mime) }.build()
        // Muchos canales SD (720×480) no marcan sus fotogramas clave como IDR: sin estas
        // opciones ExoPlayer no llega a mostrar ninguna imagen aunque el sonido sí suene.
        val source = if (mime == MimeTypes.APPLICATION_M3U8) {
            HlsMediaSource.Factory(dataSource)
                .setExtractorFactory(DefaultHlsExtractorFactory(TS_FLAGS, true))
                .createMediaSource(item)
        } else {
            val extractors = DefaultExtractorsFactory().setTsExtractorFlags(TS_FLAGS)
            DefaultMediaSourceFactory(dataSource, extractors).createMediaSource(item)
        }
        player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
    }

    private fun handleError(e: PlaybackException) {
        if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player.seekToDefaultPosition()
            player.prepare()
            return
        }
        val lower = queue.getOrNull(index)?.url?.lowercase(Locale.ROOT).orEmpty()
        val parsingError = e.errorCode in listOf(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        )
        val decoderError = e.errorCode in listOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        )
        // Muchos servidores sirven HLS sin la extensión .m3u8: lo intentamos como HLS.
        if (parsingError && !triedHls && !lower.contains(".m3u8")) {
            triedHls = true
            forceHls = true
            startChannel(index, isRetry = true)
            return
        }
        // Formato que ExoPlayer no sabe leer o decodificar en este dispositivo: probamos con VLC.
        if ((decoderError || parsingError) && autoSwitchAllowed()) {
            switchToVlc("formato no compatible con ExoPlayer")
            return
        }
        errorText = describe(e)
        if (retries < 3) {
            retries++
            handler.postDelayed(retryRunnable, 3_000L * retries)
        }
    }

    private fun describe(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "El servidor rechazó la conexión (HTTP). Revisa tu suscripción."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Sin conexión con el servidor."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "Este dispositivo no puede decodificar el formato del canal. Prueba el motor VLC."
        else -> "No se pudo reproducir el canal (${e.errorCodeName})."
    } + if (retries < 3) " Reintentando…" else ""

    /** Hay pista de vídeo (o audio) pero el dispositivo no tiene decodificador: cambiamos a VLC. */
    private fun checkUnsupportedTracks(tracks: Tracks) {
        if (!autoSwitchAllowed() || tracks.groups.isEmpty()) return
        val video = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
        val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        when {
            video.isNotEmpty() && video.none { it.isSupported } -> switchToVlc("vídeo no compatible con ExoPlayer")
            audio.isNotEmpty() && audio.none { it.isSupported } -> switchToVlc("audio no compatible con ExoPlayer")
        }
    }

    /** Suena pero no ha salido ningún fotograma: el caso típico de los canales 720×480 en negro. */
    private fun checkNoVideo() {
        if (engine != Engine.EXO || firstFrameRendered || !player.isPlaying) return
        val hasVideo = player.currentTracks.groups.any { it.type == C.TRACK_TYPE_VIDEO }
        if (hasVideo && autoSwitchAllowed()) switchToVlc("ExoPlayer no mostraba imagen")
    }

    private fun exoVideoHeight(): Int {
        player.videoFormat?.height?.takeIf { it > 0 }?.let { return it }
        for (g in player.currentTracks.groups) {
            if (g.type != C.TRACK_TYPE_VIDEO) continue
            for (i in 0 until g.length) {
                val h = g.getTrackFormat(i).height
                if (h > 0) return h
            }
        }
        return 0
    }

    private fun switchToVlc(reason: String) {
        val ch = queue.getOrNull(index) ?: return
        // Para SD usamos decodificación por software: es la que lee cualquier MPEG-2/H.264 entrelazado.
        val h = exoVideoHeight()
        if (h in 1..576) softwareChannels.add(ch.id)
        engineOverrides[ch.id] = Engine.VLC
        showNotice("Cambiando a VLC: $reason")
        startChannel(index, isRetry = true, forceEngine = Engine.VLC)
    }

    /** Botón «Motor»: alterna ExoPlayer/VLC para este canal. */
    private fun toggleEngine() {
        val ch = queue.getOrNull(index) ?: return
        val next = if (engine == Engine.EXO) Engine.VLC else Engine.EXO
        engineOverrides[ch.id] = next
        showNotice(if (next == Engine.VLC) "Motor VLC" else "Motor ExoPlayer")
        startChannel(index, isRetry = true, forceEngine = next)
    }

    private fun showNotice(text: String) {
        notice = text
        handler.removeCallbacks(clearNotice)
        handler.postDelayed(clearNotice, 4_000)
    }

    private fun updateVideoInfo() {
        if (engine == Engine.VLC) {
            updateVlcVideoInfo()
            return
        }
        val format = player.videoFormat
        val size = player.videoSize
        val w = if (size.width > 0) size.width else format?.width ?: 0
        val h = if (size.height > 0) size.height else format?.height ?: 0
        if (w <= 0 || h <= 0) {
            videoInfo = null
            return
        }
        val fps = format?.frameRate?.takeIf { it > 0 }?.let { " · ${Math.round(it)} fps" } ?: ""
        val br = format?.bitrate?.takeIf { it > 0 }?.let { String.format(Locale.US, " · %.1f Mbps", it / 1_000_000f) } ?: ""
        videoInfo = "${qualityLabel(h)} ${h}p ($w×$h)$fps$br"
    }

    private fun qualityLabel(h: Int) = when {
        h >= 2000 -> "4K"
        h >= 1000 -> "FHD"
        h >= 700 -> "HD"
        else -> "SD"
    }

    // ------------------------------------------------------------------ VLC

    private fun ensureVlc(): VlcPlayer? {
        vlc?.let { return it }
        if (vlcFailed) return null
        return try {
            val s = repo.settings.value
            val caching = (s.bufferSeconds * 1000).coerceIn(1_500, 10_000)
            val lib = LibVLC(
                this,
                arrayListOf(
                    "--network-caching=$caching",
                    "--live-caching=$caching",
                    "--http-reconnect",
                    "--deinterlace=-1",
                    "--deinterlace-mode=blend",
                    "--audio-time-stretch",
                    "--no-stats",
                ),
            )
            libVlc = lib
            VlcPlayer(lib).also { p ->
                p.setEventListener(object : VlcPlayer.EventListener {
                    override fun onEvent(event: VlcPlayer.Event) = onVlcEvent(event)
                })
                vlc = p
            }
        } catch (t: Throwable) {
            vlcFailed = true
            showNotice("El motor VLC no está disponible en este dispositivo")
            null
        }
    }

    /** Prepara el canal en VLC. Devuelve false si VLC no se puede usar (y entonces se usa ExoPlayer). */
    private fun startVlc(ch: Channel): Boolean {
        val lib = ensureVlc()?.let { libVlc } ?: return false
        handler.removeCallbacks(noVideoCheck)
        engine = Engine.VLC
        player.stop()
        isPlaying = false
        buffering = true
        val s = repo.settings.value
        val media = try {
            Media(lib, Uri.parse(ch.url))
        } catch (t: Throwable) {
            return false
        }
        val caching = (s.bufferSeconds * 1000).coerceIn(1_500, 10_000)
        media.setHWDecoderEnabled(ch.id !in softwareChannels, false)
        media.addOption(":network-caching=$caching")
        media.addOption(":http-user-agent=${ch.userAgent ?: s.userAgent}")
        ch.referrer?.let { media.addOption(":http-referrer=$it") }
        if (s.preferredAudioLanguage.isNotEmpty()) media.addOption(":audio-language=${s.preferredAudioLanguage}")
        vlcPending?.release()
        vlcPending = media
        if (vlcLayoutAttached) playPendingVlc()
        return true
    }

    private fun playPendingVlc() {
        val p = vlc ?: return
        val media = vlcPending ?: return
        vlcPending = null
        p.media = media
        media.release()
        applyVlcScale()
        p.play()
    }

    private fun stopVlc() {
        vlcPending?.release()
        vlcPending = null
        vlc?.let { if (it.isPlaying || engine == Engine.VLC) it.stop() }
    }

    private fun onVlcEvent(event: VlcPlayer.Event) {
        if (engine != Engine.VLC) return
        when (event.type) {
            VlcPlayer.Event.Buffering -> buffering = event.buffering < 100f
            VlcPlayer.Event.Playing -> {
                isPlaying = true
                buffering = false
                errorText = null
                retries = 0
                updateVlcVideoInfo()
                tracksVersion++
            }
            VlcPlayer.Event.Paused, VlcPlayer.Event.Stopped -> isPlaying = false
            VlcPlayer.Event.Vout, VlcPlayer.Event.ESAdded, VlcPlayer.Event.ESDeleted, VlcPlayer.Event.ESSelected -> {
                updateVlcVideoInfo()
                tracksVersion++
            }
            VlcPlayer.Event.EncounteredError, VlcPlayer.Event.EndReached -> {
                isPlaying = false
                buffering = false
                errorText = "No se pudo reproducir el canal con VLC." + if (retries < 3) " Reintentando…" else ""
                if (retries < 3) {
                    retries++
                    handler.postDelayed(retryRunnable, 3_000L * retries)
                }
            }
        }
    }

    private fun updateVlcVideoInfo() {
        val t = vlc?.currentVideoTrack
        if (t == null || t.width <= 0 || t.height <= 0) {
            videoInfo = "VLC"
            return
        }
        val fps = if (t.frameRateDen > 0 && t.frameRateNum > 0) " · ${Math.round(t.frameRateNum.toFloat() / t.frameRateDen)} fps" else ""
        videoInfo = "${qualityLabel(t.height)} ${t.height}p (${t.width}×${t.height})$fps · VLC"
    }

    private fun applyVlcScale() {
        vlc?.setVideoScale(
            when (resizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> VlcPlayer.ScaleType.SURFACE_FILL
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> VlcPlayer.ScaleType.SURFACE_FIT_SCREEN
                else -> VlcPlayer.ScaleType.SURFACE_BEST_FIT
            }
        )
    }

    /** Idioma de cada pista de audio de VLC, por id. */
    private fun vlcTrackLanguages(): Map<Int, String> {
        val media = vlc?.media ?: return emptyMap()
        return try {
            val map = HashMap<Int, String>()
            for (i in 0 until media.trackCount) {
                val t = media.getTrack(i) ?: continue
                if (t.type == IMedia.Track.Type.Audio && !t.language.isNullOrBlank()) map[t.id] = t.language
            }
            map
        } catch (_: Throwable) {
            emptyMap()
        } finally {
            media.release()
        }
    }

    // ------------------------------------------------------------------ Pistas (ambos motores)

    private fun languageName(code: String?): String? {
        if (code.isNullOrBlank()) return null
        val c = code.lowercase(Locale.ROOT)
        if (c == "und" || c == "mul" || c == "zxx" || c == "mis") return null
        val name = runCatching { Locale.forLanguageTag(c).getDisplayLanguage(SPANISH) }.getOrNull()
        return name?.takeIf { it.isNotBlank() && !it.equals(c, ignoreCase = true) }
            ?.replaceFirstChar { it.titlecase(SPANISH) }
            ?: code
    }

    /** Pasa "spa", "es-419", "Español"… a un código corto ("es"), para recordarlo como preferido. */
    private fun normalizeLanguage(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val l = raw.lowercase(Locale.ROOT)
        return when {
            l.startsWith("es") || l.startsWith("spa") || "españ" in l || "spanish" in l || "castellano" in l || "latino" in l -> "es"
            l.startsWith("en") || "english" in l || "inglés" in l || "ingles" in l -> "en"
            l.length in 2..3 && l.all { it.isLetter() } -> l
            else -> null
        }
    }

    private fun rememberLanguage(raw: String?) {
        val code = normalizeLanguage(raw) ?: return
        if (repo.settings.value.preferredAudioLanguage != code) {
            repo.updateSettings { it.copy(preferredAudioLanguage = code) }
        }
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguages(code)
            .build()
    }

    private fun exoAudioLabel(f: Format, i: Int): String = listOfNotNull(
        languageName(f.language),
        f.label,
        f.sampleMimeType?.substringAfter('/')?.uppercase(Locale.ROOT),
        f.channelCount.takeIf { it > 0 }?.let { if (it > 2) "${it - 1}.1" else if (it == 2) "Estéreo" else "Mono" },
    ).distinct().joinToString(" · ").ifBlank { "Pista ${i + 1}" }

    private fun trackItems(type: Int): List<TrackItem> =
        if (engine == Engine.VLC) vlcTrackItems(type) else exoTrackItems(type)

    private fun exoTrackItems(type: Int): List<TrackItem> {
        val out = ArrayList<TrackItem>()
        val groups = player.currentTracks.groups.filter { it.type == type }
        val params = player.trackSelectionParameters
        val hasOverride = params.overrides.keys.any { it.type == type }
        val disabled = type in params.disabledTrackTypes

        out += TrackItem(
            label = when (type) {
                C.TRACK_TYPE_VIDEO -> "Automática (máx. ${maxLabel()})"
                C.TRACK_TYPE_AUDIO -> "Automático" + (languageName(repo.settings.value.preferredAudioLanguage)?.let { " (prefiere $it)" } ?: "")
                else -> "Automático"
            },
            selected = !hasOverride && !disabled,
        ) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(type)
                .setTrackTypeDisabled(type, false)
                .build()
        }
        if (type == C.TRACK_TYPE_TEXT) {
            out += TrackItem("Desactivados", disabled) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(type)
                    .setTrackTypeDisabled(type, true)
                    .build()
            }
        }
        var n = 0
        groups.forEach { group ->
            for (i in 0 until group.length) {
                val f = group.getTrackFormat(i)
                val supported = group.isTrackSupported(i)
                n++
                if (!supported && type != C.TRACK_TYPE_AUDIO) continue
                val label = when (type) {
                    C.TRACK_TYPE_VIDEO -> buildString {
                        append(if (f.height > 0) "${f.height}p" else "Vídeo")
                        if (f.width > 0 && f.height > 0) append(" (${f.width}×${f.height})")
                        if (f.bitrate > 0) append(String.format(Locale.US, " · %.1f Mbps", f.bitrate / 1_000_000f))
                    }
                    C.TRACK_TYPE_AUDIO -> exoAudioLabel(f, n - 1)
                    else -> listOfNotNull(f.label, languageName(f.language)).distinct().joinToString(" · ").ifBlank { "Pista $n" }
                }
                if (!supported) {
                    // Pista que este dispositivo no decodifica (p. ej. AC-3): VLC sí puede.
                    out += TrackItem("$label  (se abrirá con VLC)", false, f.language) {
                        rememberLanguage(f.language ?: f.label)
                        val ch = queue.getOrNull(index)
                        if (ch != null) engineOverrides[ch.id] = Engine.VLC
                        startChannel(index, isRetry = true, forceEngine = Engine.VLC)
                    }
                    continue
                }
                out += TrackItem(label, group.isTrackSelected(i) && (hasOverride || type == C.TRACK_TYPE_AUDIO), f.language) {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(type, false)
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                        .build()
                    if (type == C.TRACK_TYPE_AUDIO) rememberLanguage(f.language ?: f.label)
                }
            }
        }
        // En audio, "Automático" solo se marca si no hay ninguna pista marcada.
        if (type == C.TRACK_TYPE_AUDIO && out.drop(1).any { it.selected }) {
            val auto = out[0]
            out[0] = TrackItem(auto.label, false, onClick = auto.onClick)
        }
        return out
    }

    private fun vlcTrackItems(type: Int): List<TrackItem> {
        val p = vlc ?: return emptyList()
        return when (type) {
            C.TRACK_TYPE_AUDIO -> {
                val langs = vlcTrackLanguages()
                val current = p.audioTrack
                (p.audioTracks ?: emptyArray()).filter { it.id >= 0 }.mapIndexed { i, t ->
                    val lang = langs[t.id]
                    val label = listOfNotNull(languageName(lang), t.name?.takeIf { it.isNotBlank() })
                        .distinct().joinToString(" · ").ifBlank { "Pista ${i + 1}" }
                    TrackItem(label, t.id == current, lang) {
                        p.setAudioTrack(t.id)
                        rememberLanguage(lang ?: t.name)
                        tracksVersion++
                    }
                }
            }
            C.TRACK_TYPE_TEXT -> {
                val current = p.spuTrack
                val tracks = (p.spuTracks ?: emptyArray()).filter { it.id >= 0 }
                listOf(TrackItem("Desactivados", current < 0) { p.setSpuTrack(-1); tracksVersion++ }) +
                    tracks.mapIndexed { i, t ->
                        TrackItem(t.name?.takeIf { it.isNotBlank() } ?: "Pista ${i + 1}", t.id == current) {
                            p.setSpuTrack(t.id)
                            tracksVersion++
                        }
                    }
            }
            else -> listOf(TrackItem("Automática (VLC usa la calidad que envía el canal)", true) {})
        }
    }

    /** Texto corto del idioma que suena ahora, para el botón «Idioma». */
    private fun currentAudioLabel(): String? {
        val items = trackItems(C.TRACK_TYPE_AUDIO).filter { !it.label.startsWith("Automático") }
        val sel = items.firstOrNull { it.selected } ?: return null
        return languageName(sel.language) ?: sel.label.substringBefore(" · ").take(18)
    }

    private fun audioTrackCount(): Int = trackItems(C.TRACK_TYPE_AUDIO).count { !it.label.startsWith("Automático") }

    /** Tecla de idioma del mando: pasa a la siguiente pista de audio. */
    private fun cycleAudio() {
        val items = trackItems(C.TRACK_TYPE_AUDIO).filter { !it.label.startsWith("Automático") && !it.label.endsWith("(se abrirá con VLC)") }
        if (items.size < 2) {
            showNotice("Este canal solo tiene un idioma")
            return
        }
        val cur = items.indexOfFirst { it.selected }
        val next = items[(cur + 1) % items.size]
        next.onClick()
        showNotice("Idioma: ${languageName(next.language) ?: next.label}")
    }

    // ------------------------------------------------------------------ Mando y ciclo de vida

    private fun showOverlay(fromKey: Boolean) {
        focusOverlay = fromKey
        overlayVisible = true
        overlayTick++
    }

    private fun zap(delta: Int) {
        startChannel(index + delta)
        showOverlay(false)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) overlayTick++
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP -> { zap(1); return true }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(-1); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { togglePlay(); return true }
            KeyEvent.KEYCODE_MEDIA_PLAY -> { if (!isPlaying) togglePlay(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { if (isPlaying) togglePlay(); return true }
            KeyEvent.KEYCODE_MEDIA_NEXT -> { zap(1); return true }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { zap(-1); return true }
            KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK, KeyEvent.KEYCODE_LANGUAGE_SWITCH -> { cycleAudio(); return true }
            KeyEvent.KEYCODE_CAPTIONS -> { trackDialog = C.TRACK_TYPE_TEXT; return true }
        }
        if (!overlayVisible && !listVisible && trackDialog == null) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> { zap(-1); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { zap(1); return true }
                // OK y ◄ abren directamente la lista de canales.
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_DPAD_LEFT -> { listVisible = true; return true }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> {
                    showOverlay(true); return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun togglePlay() {
        if (engine == Engine.VLC) {
            val p = vlc ?: return
            if (p.isPlaying) p.pause() else p.play()
            return
        }
        if (player.isPlaying) player.pause() else {
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) startChannel(index, true)
            else {
                // En directo, al reanudar volvemos al punto en vivo.
                if (player.isCurrentMediaItemLive) player.seekToDefaultPosition()
                player.play()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!::player.isInitialized) return
        if (stoppedInBackground) {
            stoppedInBackground = false
            if (engine == Engine.VLC) {
                startChannel(index, isRetry = true, forceEngine = Engine.VLC)
            } else if (player.playbackState != Player.STATE_IDLE) {
                if (player.isCurrentMediaItemLive) player.seekToDefaultPosition()
                player.play()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!::player.isInitialized) return
        stoppedInBackground = true
        player.pause()
        if (engine == Engine.VLC) vlc?.stop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::player.isInitialized) player.release()
        vlcPending?.release()
        vlcPending = null
        vlc?.let {
            it.setEventListener(null)
            it.stop()
            it.detachViews()
            it.release()
        }
        vlc = null
        libVlc?.release()
        libVlc = null
        super.onDestroy()
    }

    // ------------------------------------------------------------------ UI

    @Composable
    private fun PlayerScreen() {
        val focusManager = LocalFocusManager.current
        val tv = remember { isTv(this) }

        BackHandler(enabled = listVisible || overlayVisible || trackDialog != null) {
            when {
                trackDialog != null -> trackDialog = null
                listVisible -> listVisible = false
                else -> overlayVisible = false
            }
        }

        LaunchedEffect(overlayVisible, overlayTick, listVisible, trackDialog) {
            if (overlayVisible && !listVisible && trackDialog == null) {
                delay(6_000)
                overlayVisible = false
            }
        }
        LaunchedEffect(overlayVisible, listVisible) {
            if (!overlayVisible && !listVisible) focusManager.clearFocus(force = true)
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        if (listVisible) listVisible = false
                        else if (overlayVisible) overlayVisible = false
                        else showOverlay(false)
                    })
                }
                .pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (abs(total) > 150f) zap(if (total < 0) 1 else -1)
                        },
                        onVerticalDrag = { _, dy -> total += dy },
                    )
                },
        ) {
            if (engine == Engine.EXO) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            setKeepContentOnPlayerReset(true)
                            isFocusable = false
                            player = this@PlayerActivity.player
                        }
                    },
                    update = { view -> view.resizeMode = resizeMode },
                    onRelease = { view -> view.player = null },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                AndroidView(
                    factory = { ctx ->
                        VLCVideoLayout(ctx).also { layout ->
                            layout.isFocusable = false
                            vlc?.let { p ->
                                p.attachViews(layout, null, true, false)
                                vlcLayoutAttached = true
                                playPendingVlc()
                            }
                        }
                    },
                    update = { applyVlcScale() },
                    onRelease = {
                        vlc?.detachViews()
                        vlcLayoutAttached = false
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (buffering && errorText == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            notice?.let {
                Text(
                    it,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xCC000000))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            errorText?.let {
                Text(
                    it,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xCC000000))
                        .padding(16.dp),
                )
            }

            AnimatedVisibility(
                visible = overlayVisible && !listVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                InfoOverlay(requestFocus = focusOverlay || tv)
            }

            AnimatedVisibility(
                visible = listVisible,
                enter = slideInHorizontally { -it } + fadeIn(),
                exit = slideOutHorizontally { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                ChannelPanel()
            }

            trackDialog?.let { type ->
                key(tracksVersion) { TrackDialog(type) }
            }
        }
    }

    @Composable
    private fun InfoOverlay(requestFocus: Boolean) {
        val ch = queue.getOrNull(index) ?: return
        val epg by repo.epg.collectAsStateWithLifecycle()
        val favorites by repo.favorites.collectAsStateWithLifecycle()
        val now = rememberNow()
        val current = epg.current(ch.id, now)
        val next = epg.next(ch.id, now)
        val fav = ch.id in favorites
        // Con el mando, el foco empieza en «Lista de canales» (no en pausa/play).
        val listFocus = remember { FocusRequester() }
        val audioLabel = remember(tracksVersion, engine, index) { currentAudioLabel() }
        val audioCount = remember(tracksVersion, engine, index) { audioTrackCount() }

        LaunchedEffect(requestFocus, index) {
            if (requestFocus) {
                delay(50)
                runCatching { listFocus.requestFocus() }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(horizontal = 32.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(ch, 64.dp)
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${ch.number}  ${ch.name}",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        videoInfo?.let {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Black,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(ch.group, style = MaterialTheme.typography.labelMedium, color = Color(0xFFB0B8C4))
                }
                Text(formatTime(now), style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
            Spacer(Modifier.height(10.dp))
            if (current != null) {
                Text(
                    "Ahora: ${formatTime(current.start)} – ${formatTime(current.stop)}  ${current.title}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(
                    progress = { ((now - current.start).toFloat() / (current.stop - current.start).coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                )
                current.desc?.let {
                    Text(it, color = Color(0xFFCCD3DC), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text("Sin información de guía", color = Color(0xFFB0B8C4), style = MaterialTheme.typography.bodyMedium)
            }
            next?.let {
                Text(
                    "Después: ${formatTime(it.start)}  ${it.title}",
                    color = Color(0xFFB0B8C4),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                OverlayButton(
                    Icons.AutoMirrored.Filled.List,
                    "Lista de canales",
                    Modifier.focusRequester(listFocus),
                ) { listVisible = true }
                OverlayButton(Icons.Filled.SkipPrevious, "Canal anterior") { zap(-1) }
                OverlayButton(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (isPlaying) "Pausa" else "Reproducir",
                ) { togglePlay() }
                OverlayButton(Icons.Filled.SkipNext, "Canal siguiente") { zap(1) }
                OverlayButton(if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, "Favorito") { repo.toggleFavorite(ch.id) }
                OverlayTextButton(
                    Icons.Filled.Language,
                    "Idioma" + (audioLabel?.let { ": $it" } ?: "") + if (audioCount > 1) " ($audioCount)" else "",
                ) { trackDialog = C.TRACK_TYPE_AUDIO }
                OverlayButton(Icons.Filled.Subtitles, "Subtítulos") { trackDialog = C.TRACK_TYPE_TEXT }
                OverlayButton(Icons.Filled.HighQuality, "Calidad de vídeo") { trackDialog = C.TRACK_TYPE_VIDEO }
                OverlayButton(Icons.Filled.AspectRatio, "Formato de imagen") { cycleResize() }
                OverlayTextButton(Icons.Filled.SwapHoriz, if (engine == Engine.VLC) "VLC" else "ExoPlayer") { toggleEngine() }
                Spacer(Modifier.width(12.dp))
                Text(resizeLabel(), color = Color(0xFFB0B8C4), style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    private fun resizeLabel() = when (resizeMode) {
        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Estirar"
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
        else -> "Ajustar"
    }

    private fun cycleResize() {
        val (mode, setting) = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM to 2
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL to 1
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT to 0
        }
        resizeMode = mode
        applyVlcScale()
        repo.updateSettings { it.copy(resizeMode = setting) }
    }

    @Composable
    private fun OverlayButton(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        description: String,
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
    ) {
        IconButton(
            onClick = { overlayTick++; onClick() },
            modifier = modifier.focusBorder(RoundedCornerShape(50)),
        ) {
            Icon(icon, contentDescription = description, tint = Color.White)
        }
    }

    @Composable
    private fun OverlayTextButton(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        text: String,
        onClick: () -> Unit,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .focusBorder(RoundedCornerShape(50))
                .clip(RoundedCornerShape(50))
                .clickable { overlayTick++; onClick() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }

    @Composable
    private fun ChannelPanel() {
        val epg by repo.epg.collectAsStateWithLifecycle()
        val now = rememberNow()
        val listState = rememberLazyListState()
        val currentFocus = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            listState.scrollToItem((index - 3).coerceAtLeast(0))
            delay(80)
            runCatching { currentFocus.requestFocus() }
        }

        Column(
            Modifier
                .width(380.dp)
                .fillMaxHeight()
                .background(Color(0xF0101418))
                .padding(vertical = 12.dp)
        ) {
            Text(
                "Canales (${queue.size})",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn(state = listState) {
                itemsIndexed(queue, key = { _, c -> c.id }) { i, c ->
                    val cur = epg.current(c.id, now)
                    val selected = i == index
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .then(if (selected) Modifier.focusRequester(currentFocus) else Modifier)
                            .focusBorder()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                            .clickable {
                                listVisible = false
                                if (i != index) startChannel(i)
                                showOverlay(false)
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${c.number}",
                            color = Color(0xFFB0B8C4),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(36.dp),
                        )
                        ChannelLogo(c, 40.dp)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(c.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            cur?.let {
                                Text(it.title, color = Color(0xFFB0B8C4), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TrackDialog(type: Int) {
        val title = when (type) {
            C.TRACK_TYPE_VIDEO -> "Calidad de vídeo"
            C.TRACK_TYPE_AUDIO -> "Idioma / pista de audio"
            else -> "Subtítulos"
        }
        val items = trackItems(type)
        val realTracks = items.count { !it.label.startsWith("Automático") && it.label != "Desactivados" }

        AlertDialog(
            onDismissRequest = { trackDialog = null },
            title = { Text(title) },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items.forEach { t ->
                        item {
                            TrackOption(t.label, t.selected) {
                                t.onClick()
                                trackDialog = null
                            }
                        }
                    }
                    if (realTracks == 0 && type != C.TRACK_TYPE_VIDEO) {
                        item {
                            Text(
                                if (type == C.TRACK_TYPE_AUDIO) "Este canal solo trae un idioma (o todavía se está cargando)."
                                else "No hay subtítulos en este canal.",
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { trackDialog = null }, modifier = Modifier.focusBorder()) { Text("Cerrar") }
            },
        )
    }

    private fun maxLabel(): String = repo.settings.value.maxVideoHeight.let {
        when (it) {
            0 -> "sin límite"
            2160 -> "4K"
            else -> "${it}p"
        }
    }

    @Composable
    private fun TrackOption(label: String, selected: Boolean, onClick: () -> Unit) {
        Column {
            Text(
                (if (selected) "✓  " else "     ") + label,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusBorder()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 10.dp, vertical = 12.dp),
            )
            HorizontalDivider()
        }
    }

    companion object {
        const val EXTRA_CHANNEL_ID = "channel_id"
        private const val NO_VIDEO_TIMEOUT_MS = 5_000L
        private val TS_FLAGS = DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
        private val SPANISH = Locale("es")

        /** Motor elegido para cada canal durante esta sesión (automático o a mano). */
        private val engineOverrides = HashMap<String, Engine>()
        /** Canales SD que necesitaron VLC: se decodifican por software. */
        private val softwareChannels = HashSet<String>()
    }
}
