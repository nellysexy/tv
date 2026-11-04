package com.nexotv.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexotv.app.data.Channel
import com.nexotv.app.data.EpgIndex
import com.nexotv.app.data.Programme
import com.nexotv.app.data.Repository

private val DP_PER_MIN = 4.dp
private const val HOURS = 24
private const val SLOT_MIN = 30

@Composable
fun GuideScreen(
    repo: Repository,
    wide: Boolean,
    openPlayer: (Channel, List<Channel>) -> Unit,
    onGoToSettings: () -> Unit,
) {
    val channels by repo.channels.collectAsStateWithLifecycle()
    val favorites by repo.favorites.collectAsStateWithLifecycle()
    val recents by repo.recents.collectAsStateWithLifecycle()
    val epg by repo.epg.collectAsStateWithLifecycle()
    val epgSources by repo.epgSources.collectAsStateWithLifecycle()
    var group by rememberSaveable { mutableStateOf(GROUP_ALL) }
    var onlyWithEpg by rememberSaveable { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Pair<Channel, Programme>?>(null) }
    val now = rememberNow()

    if (channels.isEmpty()) {
        EmptyState("Sin canales", "Añade primero una lista de canales.", "Ir a Ajustes", onGoToSettings)
        return
    }
    if (epgSources.isEmpty()) {
        EmptyState(
            "Sin guía de programación",
            "Añade una o varias fuentes EPG (XMLTV) en Ajustes. Si tu lista incluye url-tvg o es Xtream Codes, se añade sola.",
            "Ir a Ajustes",
            onGoToSettings,
        )
        return
    }

    val groups = remember(channels, recents.isNotEmpty(), favorites.isNotEmpty()) {
        buildGroups(channels, recents.isNotEmpty(), favorites.isNotEmpty())
    }
    val currentGroup = if (groups.any { it.first == group }) group else GROUP_ALL
    val rows = remember(channels, currentGroup, favorites, recents, epg, onlyWithEpg) {
        filterChannels(channels, currentGroup, "", favorites, recents)
            .let { list -> if (onlyWithEpg) list.filter { epg.hasData(it.id) } else list }
    }

    // Inicio de la ventana: media hora antes de la franja actual.
    val windowStart = remember { (System.currentTimeMillis() / (SLOT_MIN * 60_000L)) * (SLOT_MIN * 60_000L) - SLOT_MIN * 60_000L }
    val windowEnd = windowStart + HOURS * 3_600_000L
    val totalWidth = DP_PER_MIN * (HOURS * 60f)
    val hScroll = rememberScrollState()
    val density = LocalDensity.current
    val channelColWidth = if (wide) 220.dp else 120.dp

    LaunchedEffect(Unit) {
        val px = with(density) { (DP_PER_MIN * (SLOT_MIN - 10f)).toPx() }
        hScroll.scrollTo(px.toInt())
    }

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "__epg_only") {
                FilterChip(
                    selected = onlyWithEpg,
                    onClick = { onlyWithEpg = !onlyWithEpg },
                    label = { Text("Solo con guía") },
                )
            }
            items(groups, key = { it.first }) { (key, label) ->
                FilterChip(
                    selected = key == currentGroup,
                    onClick = { group = key },
                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                )
            }
        }

        // Cabecera con las horas
        Row(Modifier.fillMaxWidth().height(28.dp)) {
            Box(Modifier.width(channelColWidth).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                Text(
                    formatDay(now),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                Box(Modifier.width(totalWidth).fillMaxHeight()) {
                    for (i in 0 until HOURS * 60 / SLOT_MIN) {
                        val t = windowStart + i * SLOT_MIN * 60_000L
                        Text(
                            formatTime(t),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.offset(x = DP_PER_MIN * (i * SLOT_MIN).toFloat()).padding(start = 4.dp, top = 6.dp),
                        )
                    }
                }
            }
        }

        if (rows.isEmpty()) {
            EmptyState(
                "Sin programación",
                "Ningún canal de este grupo tiene guía. Revisa las fuentes EPG en Ajustes o asigna el canal de la guía manualmente desde el menú ⋮ de cada canal.",
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows, key = { it.id }) { ch ->
                    GuideRow(
                        channel = ch,
                        epg = epg,
                        now = now,
                        windowStart = windowStart,
                        windowEnd = windowEnd,
                        totalWidth = totalWidth,
                        channelColWidth = channelColWidth,
                        hScroll = hScroll,
                        onChannelClick = { openPlayer(ch, rows) },
                        onProgrammeClick = { p -> selected = ch to p },
                    )
                }
            }
        }
    }

    selected?.let { (ch, p) ->
        ProgrammeDialog(
            channel = ch,
            programme = p,
            now = now,
            onDismiss = { selected = null },
            onWatch = {
                selected = null
                openPlayer(ch, rows)
            },
        )
    }
}

@Composable
private fun GuideRow(
    channel: Channel,
    epg: EpgIndex,
    now: Long,
    windowStart: Long,
    windowEnd: Long,
    totalWidth: Dp,
    channelColWidth: Dp,
    hScroll: ScrollState,
    onChannelClick: () -> Unit,
    onProgrammeClick: (Programme) -> Unit,
) {
    val programmes = remember(epg, channel.id) {
        epg.programmes(channel.id).filter { it.stop > windowStart && it.start < windowEnd }
    }
    Row(Modifier.fillMaxWidth().height(64.dp).padding(vertical = 2.dp)) {
        Row(
            Modifier
                .width(channelColWidth)
                .fillMaxHeight()
                .padding(horizontal = 4.dp)
                .focusBorder()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onChannelClick)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(channel, 40.dp)
            Text(
                channel.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(hScroll)) {
            Box(Modifier.width(totalWidth).fillMaxHeight()) {
                if (programmes.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize().padding(2.dp).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            "Sin información de guía",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
                programmes.forEach { p ->
                    val startMin = ((maxOf(p.start, windowStart) - windowStart) / 60_000f)
                    val endMin = ((minOf(p.stop, windowEnd) - windowStart) / 60_000f)
                    val isNow = now in p.start until p.stop
                    val bg = if (isNow) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                    Box(
                        Modifier
                            .offset(x = DP_PER_MIN * startMin)
                            .width(DP_PER_MIN * (endMin - startMin).coerceAtLeast(1f))
                            .fillMaxHeight()
                            .padding(2.dp)
                            .focusBorder(RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(bg)
                            .clickable { onProgrammeClick(p) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Column {
                            Text(
                                p.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isNow) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${formatTime(p.start)} – ${formatTime(p.stop)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
                if (now in windowStart until windowEnd) {
                    Box(
                        Modifier
                            .offset(x = DP_PER_MIN * ((now - windowStart) / 60_000f))
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.secondary)
                    )
                }
            }
        }
    }
}

@Composable
fun ProgrammeDialog(
    channel: Channel,
    programme: Programme,
    now: Long,
    onDismiss: () -> Unit,
    onWatch: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(programme.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "${channel.name} · ${formatDay(programme.start)} · ${formatTime(programme.start)} – ${formatTime(programme.stop)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (now in programme.start until programme.stop) {
                    Text("En emisión ahora", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                }
                programme.category?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
                Text(programme.desc ?: "Sin descripción.", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onWatch, modifier = Modifier.focusBorder()) { Text("Ver canal") } },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.focusBorder()) { Text("Cerrar") } },
    )
}
