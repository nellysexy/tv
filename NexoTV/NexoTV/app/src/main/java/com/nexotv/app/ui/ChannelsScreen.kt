package com.nexotv.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexotv.app.data.Channel
import com.nexotv.app.data.Repository

const val GROUP_ALL = "__all__"
const val GROUP_RECENT = "__recent__"
const val GROUP_FAV = "__fav__"

/** Grupos para el selector: (clave, etiqueta). */
fun buildGroups(channels: List<Channel>, hasRecents: Boolean, hasFavs: Boolean): List<Pair<String, String>> {
    val list = ArrayList<Pair<String, String>>()
    list.add(GROUP_ALL to "Todos (${channels.size})")
    if (hasFavs) list.add(GROUP_FAV to "★ Favoritos")
    if (hasRecents) list.add(GROUP_RECENT to "Recientes")
    val counts = LinkedHashMap<String, Int>()
    channels.forEach { counts[it.group] = (counts[it.group] ?: 0) + 1 }
    counts.forEach { (g, n) -> list.add(g to "$g ($n)") }
    return list
}

fun filterChannels(
    channels: List<Channel>,
    group: String,
    query: String,
    favorites: List<String>,
    recents: List<String>,
): List<Channel> {
    val base = when (group) {
        GROUP_ALL -> channels
        GROUP_FAV -> {
            val byId = channels.associateBy { it.id }
            favorites.mapNotNull { byId[it] }
        }
        GROUP_RECENT -> {
            val byId = channels.associateBy { it.id }
            recents.mapNotNull { byId[it] }
        }
        else -> channels.filter { it.group == group }
    }
    val q = query.trim()
    if (q.isEmpty()) return base
    return base.filter { it.name.contains(q, ignoreCase = true) || it.group.contains(q, ignoreCase = true) }
}

@Composable
fun ChannelsScreen(
    repo: Repository,
    wide: Boolean,
    openPlayer: (Channel, List<Channel>) -> Unit,
    onGoToSettings: () -> Unit,
) {
    val channels by repo.channels.collectAsStateWithLifecycle()
    val favorites by repo.favorites.collectAsStateWithLifecycle()
    val recents by repo.recents.collectAsStateWithLifecycle()
    val epg by repo.epg.collectAsStateWithLifecycle()
    val loaded by repo.loaded.collectAsStateWithLifecycle()
    val busy by repo.busy.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf(GROUP_ALL) }
    var assignFor by remember { mutableStateOf<Channel?>(null) }
    val now = rememberNow()

    if (loaded && channels.isEmpty()) {
        if (busy == null) {
            EmptyState(
                title = "Aún no tienes canales",
                message = "Añade tu lista M3U o tu cuenta Xtream Codes en Ajustes. También puedes añadir una o varias guías EPG (XMLTV).",
                action = "Añadir lista",
                onAction = onGoToSettings,
            )
        }
        return
    }

    val groups = remember(channels, recents.isNotEmpty(), favorites.isNotEmpty()) {
        buildGroups(channels, recents.isNotEmpty(), favorites.isNotEmpty())
    }
    val currentGroup = if (groups.any { it.first == group }) group else GROUP_ALL
    val favSet = remember(favorites) { favorites.toHashSet() }
    val filtered = remember(channels, currentGroup, query, favorites, recents) {
        filterChannels(channels, currentGroup, query, favorites, recents)
    }

    val search: @Composable (Modifier) -> Unit = { m ->
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = m.focusBorder(),
            singleLine = true,
            placeholder = { Text("Buscar canal o grupo…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, contentDescription = "Borrar") }
                }
            },
        )
    }

    val list: @Composable (Modifier) -> Unit = { m ->
        if (filtered.isEmpty()) {
            EmptyState("Sin resultados", "No hay canales que coincidan con la búsqueda.")
        } else {
            ChannelList(
                channels = filtered,
                epg = epg,
                favorites = favSet,
                now = now,
                onPlay = { openPlayer(it, filtered) },
                onToggleFavorite = { repo.toggleFavorite(it.id) },
                onAssignEpg = { assignFor = it },
                modifier = m,
            )
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(280.dp).fillMaxHeight().padding(top = 12.dp)) {
                Text(
                    "Grupos",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                GroupList(groups, currentGroup, { group = it }, Modifier.fillMaxSize())
            }
            VerticalDivider()
            Column(Modifier.weight(1f).fillMaxHeight()) {
                search(Modifier.fillMaxWidth().padding(12.dp))
                list(Modifier.fillMaxSize())
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            search(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(groups, key = { it.first }) { (key, label) ->
                    FilterChip(
                        selected = key == currentGroup,
                        onClick = { group = key },
                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
            list(Modifier.fillMaxSize())
        }
    }

    assignFor?.let { ch ->
        EpgAssignDialog(repo, ch, onDismiss = { assignFor = null })
    }
}

@Composable
fun FavoritesScreen(repo: Repository, openPlayer: (Channel, List<Channel>) -> Unit) {
    val channels by repo.channels.collectAsStateWithLifecycle()
    val favorites by repo.favorites.collectAsStateWithLifecycle()
    val epg by repo.epg.collectAsStateWithLifecycle()
    var assignFor by remember { mutableStateOf<Channel?>(null) }
    val now = rememberNow()
    val list = remember(channels, favorites) { filterChannels(channels, GROUP_FAV, "", favorites, emptyList()) }
    val favSet = remember(favorites) { favorites.toHashSet() }

    if (list.isEmpty()) {
        EmptyState("Sin favoritos", "Pulsa la estrella junto a un canal para añadirlo aquí.")
        return
    }
    Column(Modifier.fillMaxSize()) {
        Text(
            "Favoritos",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        )
        ChannelList(
            channels = list,
            epg = epg,
            favorites = favSet,
            now = now,
            onPlay = { openPlayer(it, list) },
            onToggleFavorite = { repo.toggleFavorite(it.id) },
            onAssignEpg = { assignFor = it },
            modifier = Modifier.fillMaxSize(),
        )
    }
    assignFor?.let { ch -> EpgAssignDialog(repo, ch, onDismiss = { assignFor = null }) }
}
