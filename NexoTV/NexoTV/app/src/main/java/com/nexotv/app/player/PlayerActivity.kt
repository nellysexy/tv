package com.nexotv.app.player

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Subtitles
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
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
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
import java.util.Locale
import kotlin.math.abs

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
    private var videoInfo by mutableStateOf<String?>(null)
    private var tracksVersion by mutableIntStateOf(0)
    private var trackDialog by mutableStateOf<Int?>(null)
    private var resizeMode by mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)

    private var retries = 0
    private var triedHls = false
    private var forceHls = false
    private val retryRunnable = Runnable { startChannel(index, isRetry = true) }

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
        player.trackSelectionParameters = params.build()

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    errorText = null
                    retries = 0
                }
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) = updateVideoInfo()

            override fun onTracksChanged(tracks: Tracks) {
                tracksVersion++
                updateVideoInfo()
            }

            override fun onPlayerError(error: PlaybackException) = handleError(error)
        })
    }

    private fun startChannel(i: Int, isRetry: Boolean = false) {
        if (queue.isEmpty()) return
        handler.removeCallbacks(retryRunnable)
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
        val source = DefaultMediaSourceFactory(dataSource).createMediaSource(item)
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
        // Muchos servidores sirven HLS sin la extensión .m3u8: lo intentamos como HLS.
        if (parsingError && !triedHls && !lower.contains(".m3u8")) {
            triedHls = true
            forceHls = true
            startChannel(index, isRetry = true)
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
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "Este dispositivo no puede decodificar el formato del canal."
        else -> "No se pudo reproducir el canal (${e.errorCodeName})."
    } + if (retries < 3) " Reintentando…" else ""

    private fun updateVideoInfo() {
        val format = player.videoFormat
        val size = player.videoSize
        val w = if (size.width > 0) size.width else format?.width ?: 0
        val h = if (size.height > 0) size.height else format?.height ?: 0
        if (w <= 0 || h <= 0) {
            videoInfo = null
            return
        }
        val label = when {
            h >= 2000 -> "4K"
            h >= 1000 -> "FHD"
            h >= 700 -> "HD"
            else -> "SD"
        }
        val fps = format?.frameRate?.takeIf { it > 0 }?.let { " · ${Math.round(it)} fps" } ?: ""
        val br = format?.bitrate?.takeIf { it > 0 }?.let { String.format(Locale.US, " · %.1f Mbps", it / 1_000_000f) } ?: ""
        videoInfo = "$label ${h}p ($w×$h)$fps$br"
    }

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
            KeyEvent.KEYCODE_MEDIA_PLAY -> { player.play(); return true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { player.pause(); return true }
            KeyEvent.KEYCODE_MEDIA_NEXT -> { zap(1); return true }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { zap(-1); return true }
        }
        if (!overlayVisible && !listVisible && trackDialog == null) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> { zap(-1); return true }
                KeyEvent.KEYCODE_DPAD_DOWN -> { zap(1); return true }
                KeyEvent.KEYCODE_DPAD_LEFT -> { listVisible = true; return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO -> {
                    showOverlay(true); return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun togglePlay() {
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
        if (::player.isInitialized && player.playbackState != Player.STATE_IDLE) {
            if (player.isCurrentMediaItemLive) player.seekToDefaultPosition()
            player.play()
        }
    }

    override fun onStop() {
        super.onStop()
        if (::player.isInitialized) player.pause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::player.isInitialized) player.release()
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
                modifier = Modifier.fillMaxSize(),
            )

            if (buffering && errorText == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
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
        val playFocus = remember { FocusRequester() }

        LaunchedEffect(requestFocus, index) {
            if (requestFocus) {
                delay(50)
                runCatching { playFocus.requestFocus() }
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
                OverlayButton(Icons.AutoMirrored.Filled.List, "Lista de canales") { listVisible = true }
                OverlayButton(Icons.Filled.SkipPrevious, "Canal anterior") { zap(-1) }
                OverlayButton(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (isPlaying) "Pausa" else "Reproducir",
                    Modifier.focusRequester(playFocus),
                ) { togglePlay() }
                OverlayButton(Icons.Filled.SkipNext, "Canal siguiente") { zap(1) }
                OverlayButton(if (fav) Icons.Filled.Star else Icons.Filled.StarBorder, "Favorito") { repo.toggleFavorite(ch.id) }
                OverlayButton(Icons.Filled.HighQuality, "Calidad de vídeo") { trackDialog = C.TRACK_TYPE_VIDEO }
                OverlayButton(Icons.Filled.Audiotrack, "Audio") { trackDialog = C.TRACK_TYPE_AUDIO }
                OverlayButton(Icons.Filled.Subtitles, "Subtítulos") { trackDialog = C.TRACK_TYPE_TEXT }
                OverlayButton(Icons.Filled.AspectRatio, "Formato de imagen") { cycleResize() }
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
        val groups = player.currentTracks.groups.filter { it.type == type }
        val title = when (type) {
            C.TRACK_TYPE_VIDEO -> "Calidad de vídeo"
            C.TRACK_TYPE_AUDIO -> "Pista de audio"
            else -> "Subtítulos"
        }
        val params = player.trackSelectionParameters
        val hasOverride = params.overrides.keys.any { it.type == type }
        val disabled = type in params.disabledTrackTypes

        AlertDialog(
            onDismissRequest = { trackDialog = null },
            title = { Text(title) },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    item {
                        TrackOption(
                            label = if (type == C.TRACK_TYPE_VIDEO) "Automática (máx. ${maxLabel()})" else "Automático",
                            selected = !hasOverride && !disabled,
                        ) {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .clearOverridesOfType(type)
                                .setTrackTypeDisabled(type, false)
                                .build()
                            trackDialog = null
                        }
                    }
                    if (type == C.TRACK_TYPE_TEXT) {
                        item {
                            TrackOption("Desactivados", disabled) {
                                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                    .clearOverridesOfType(type)
                                    .setTrackTypeDisabled(type, true)
                                    .build()
                                trackDialog = null
                            }
                        }
                    }
                    groups.forEach { group ->
                        for (i in 0 until group.length) {
                            if (!group.isTrackSupported(i)) continue
                            val f = group.getTrackFormat(i)
                            val label = when (type) {
                                C.TRACK_TYPE_VIDEO -> buildString {
                                    append(if (f.height > 0) "${f.height}p" else "Vídeo")
                                    if (f.width > 0 && f.height > 0) append(" (${f.width}×${f.height})")
                                    if (f.bitrate > 0) append(String.format(Locale.US, " · %.1f Mbps", f.bitrate / 1_000_000f))
                                }
                                else -> listOfNotNull(
                                    f.label,
                                    f.language?.let { Locale(it).displayLanguage.ifBlank { it } },
                                    f.sampleMimeType?.substringAfter('/')?.uppercase(Locale.ROOT),
                                    f.channelCount.takeIf { it > 0 }?.let { "$it canales" },
                                ).distinct().joinToString(" · ").ifBlank { "Pista ${i + 1}" }
                            }
                            item {
                                TrackOption(label, hasOverride && group.isTrackSelected(i)) {
                                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                        .setTrackTypeDisabled(type, false)
                                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                        .build()
                                    trackDialog = null
                                }
                            }
                        }
                    }
                    if (groups.isEmpty()) {
                        item { Text("No hay pistas disponibles para este canal.") }
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
    }
}
