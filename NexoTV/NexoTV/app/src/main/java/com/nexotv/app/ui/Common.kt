package com.nexotv.app.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nexotv.app.data.Channel
import com.nexotv.app.data.EpgIndex
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun isTv(context: Context): Boolean =
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

private val hourFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dayFmt = SimpleDateFormat("EEE d MMM", Locale.getDefault())

fun formatTime(ms: Long): String = synchronized(hourFmt) { hourFmt.format(Date(ms)) }
fun formatDay(ms: Long): String = synchronized(dayFmt) { dayFmt.format(Date(ms)) }

fun formatAgo(ms: Long): String {
    if (ms <= 0) return "nunca"
    val diff = (System.currentTimeMillis() - ms) / 60_000
    return when {
        diff < 1 -> "ahora mismo"
        diff < 60 -> "hace $diff min"
        diff < 60 * 24 -> "hace ${diff / 60} h"
        else -> "hace ${diff / (60 * 24)} días"
    }
}

/** Hora actual que se actualiza cada 30 segundos. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** Borde visible al recibir el foco (imprescindible para mandos de Android TV). */
@Composable
fun Modifier.focusBorder(shape: Shape = RoundedCornerShape(10.dp), width: Dp = 2.dp): Modifier {
    var focused by remember { mutableStateOf(false) }
    val color = if (focused) MaterialTheme.colorScheme.primary else Color.Transparent
    return this
        .border(width, color, shape)
        .onFocusChanged { focused = it.isFocused }
}

@Composable
fun ChannelLogo(channel: Channel, size: Dp, modifier: Modifier = Modifier) {
    var loaded by remember(channel.logo) { mutableStateOf(false) }
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                text = initials(channel.name),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = (size.value / 3.2f).sp,
            )
        }
        if (!channel.logo.isNullOrBlank()) {
            AsyncImage(
                model = channel.logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(3.dp),
                onSuccess = { loaded = true },
            )
        }
    }
}

private fun initials(name: String): String =
    name.split(' ', '-', '_').filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "TV" }

@Composable
fun EmptyState(title: String, message: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun ChannelList(
    channels: List<Channel>,
    epg: EpgIndex,
    favorites: Set<String>,
    now: Long,
    onPlay: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onAssignEpg: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    LazyColumn(modifier, state = state) {
        items(channels, key = { it.id }) { ch ->
            ChannelRow(
                channel = ch,
                epg = epg,
                now = now,
                favorite = ch.id in favorites,
                onClick = { onPlay(ch) },
                onToggleFavorite = { onToggleFavorite(ch) },
                onAssignEpg = { onAssignEpg(ch) },
            )
        }
    }
}

@Composable
fun ChannelRow(
    channel: Channel,
    epg: EpgIndex,
    now: Long,
    favorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAssignEpg: () -> Unit,
) {
    val current = epg.current(channel.id, now)
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .focusBorder()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                channel.number.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.Center,
            )
            ChannelLogo(channel, 48.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (current != null) {
                    Text(
                        "${formatTime(current.start)}  ${current.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val progress = ((now - current.start).toFloat() / (current.stop - current.start).coerceAtLeast(1))
                        .coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(3.dp).clip(RoundedCornerShape(2.dp)),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                } else {
                    Text(
                        channel.group,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.focusBorder(RoundedCornerShape(50))) {
            Icon(
                if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (favorite) "Quitar de favoritos" else "Añadir a favoritos",
                tint = if (favorite) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.focusBorder(RoundedCornerShape(50))) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Reproducir") }, onClick = { menu = false; onClick() })
                DropdownMenuItem(
                    text = { Text(if (favorite) "Quitar de favoritos" else "Añadir a favoritos") },
                    onClick = { menu = false; onToggleFavorite() },
                )
                DropdownMenuItem(text = { Text("Asignar canal de la guía (EPG)…") }, onClick = { menu = false; onAssignEpg() })
            }
        }
    }
}

@Composable
fun GroupList(
    groups: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier) {
        items(groups, key = { it.first }) { (key, label) ->
            val isSel = key == selected
            Text(
                label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .focusBorder()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onSelect(key) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}
