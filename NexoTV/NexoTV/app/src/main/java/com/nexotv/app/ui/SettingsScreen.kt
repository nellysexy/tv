package com.nexotv.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexotv.app.data.AppSettings
import com.nexotv.app.data.Net
import com.nexotv.app.data.PlaylistSource
import com.nexotv.app.data.Repository
import com.nexotv.app.data.SourceType

@Composable
fun SettingsScreen(repo: Repository, wide: Boolean) {
    val sources by repo.sources.collectAsStateWithLifecycle()
    val epgSources by repo.epgSources.collectAsStateWithLifecycle()
    val settings by repo.settings.collectAsStateWithLifecycle()
    val busy by repo.busy.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PlaylistSource?>(null) }
    var adding by remember { mutableStateOf(false) }
    var addingEpg by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    val hPad = if (wide) 32.dp else 16.dp

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = hPad, vertical = 16.dp)) {
        item { Text("Ajustes", style = MaterialTheme.typography.headlineSmall) }

        // ---------------------------------------------------------------- Listas
        item {
            SectionHeader("Listas de canales") {
                OutlinedButton(onClick = { repo.refreshAll() }, enabled = busy == null && sources.isNotEmpty(), modifier = Modifier.focusBorder()) {
                    Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Actualizar todo")
                }
                Button(onClick = { adding = true }, modifier = Modifier.focusBorder()) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Añadir lista")
                }
            }
        }
        if (sources.isEmpty()) {
            item { Hint("Añade una lista M3U (URL o archivo) o una cuenta Xtream Codes de tu proveedor.") }
        }
        items(sources, key = { it.id }) { s ->
            ItemCard(
                title = s.name,
                subtitle = buildString {
                    append(
                        when (s.type) {
                            SourceType.M3U_URL -> "M3U"
                            SourceType.M3U_FILE -> "Archivo M3U"
                            SourceType.XTREAM -> "Xtream Codes"
                        }
                    )
                    append(" · ${s.channelCount} canales · ${formatAgo(s.lastUpdated)}")
                },
                status = s.lastStatus,
            ) {
                IconButton(onClick = { repo.refreshSource(s.id) }, enabled = busy == null, modifier = Modifier.focusBorder(RoundedCornerShape(50))) {
                    Icon(Icons.Filled.Refresh, "Actualizar")
                }
                IconButton(onClick = { editing = s }, modifier = Modifier.focusBorder(RoundedCornerShape(50))) {
                    Icon(Icons.Filled.Edit, "Editar")
                }
                IconButton(
                    onClick = { confirmDelete = "¿Eliminar la lista «${s.name}» y sus canales?" to { repo.removeSource(s.id) } },
                    modifier = Modifier.focusBorder(RoundedCornerShape(50)),
                ) { Icon(Icons.Filled.Delete, "Eliminar") }
            }
        }

        // ---------------------------------------------------------------- EPG
        item {
            SectionHeader("Guía de programación (EPG)") {
                OutlinedButton(onClick = { repo.refreshEpg() }, enabled = busy == null && epgSources.isNotEmpty(), modifier = Modifier.focusBorder()) {
                    Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Actualizar guía")
                }
                Button(onClick = { addingEpg = true }, modifier = Modifier.focusBorder()) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Añadir EPG")
                }
            }
        }
        item {
            Hint("Puedes combinar varias fuentes XMLTV. Se asocian por tvg-id y, si no coincide, por nombre del canal. Si un canal está en varias fuentes, gana la que esté más arriba: usa las flechas para ordenar.")
        }
        items(epgSources, key = { it.id }) { e ->
            val index = epgSources.indexOf(e)
            ItemCard(
                title = "${index + 1}. ${e.name}" + if (e.autoFromSourceId != null) "  (automática)" else "",
                subtitle = "Descargada ${formatAgo(e.lastUpdated)}",
                status = e.lastStatus,
                extra = e.url.takeIf { !it.contains("password=") },
            ) {
                Switch(checked = e.enabled, onCheckedChange = { repo.setEpgEnabled(e.id, it) }, modifier = Modifier.focusBorder(RoundedCornerShape(50)))
                IconButton(onClick = { repo.moveEpgSource(e.id, -1) }, enabled = index > 0, modifier = Modifier.focusBorder(RoundedCornerShape(50))) {
                    Icon(Icons.Filled.ArrowUpward, "Subir prioridad")
                }
                IconButton(onClick = { repo.moveEpgSource(e.id, 1) }, enabled = index < epgSources.size - 1, modifier = Modifier.focusBorder(RoundedCornerShape(50))) {
                    Icon(Icons.Filled.ArrowDownward, "Bajar prioridad")
                }
                IconButton(
                    onClick = { confirmDelete = "¿Eliminar la fuente EPG «${e.name}»?" to { repo.removeEpgSource(e.id) } },
                    modifier = Modifier.focusBorder(RoundedCornerShape(50)),
                ) { Icon(Icons.Filled.Delete, "Eliminar") }
            }
        }
        item {
            Column(Modifier.padding(top = 8.dp)) {
                OptionRow("Días de guía a cargar", listOf(1, 2, 3, 5, 7), settings.epgDaysAhead, { "$it" }) { v ->
                    repo.updateSettings { it.copy(epgDaysAhead = v) }
                }
                OptionRow("Actualizar automáticamente cada", listOf(6, 12, 24, 48), settings.epgRefreshHours, { "$it h" }) { v ->
                    repo.updateSettings { it.copy(epgRefreshHours = v) }
                }
                OptionRow(
                    "Ajuste horario de la guía",
                    listOf(-180, -120, -60, 0, 60, 120, 180),
                    settings.epgOffsetMinutes,
                    { if (it == 0) "0" else (if (it > 0) "+" else "") + "${it / 60} h" },
                ) { v -> repo.updateSettings { it.copy(epgOffsetMinutes = v) } }
            }
        }

        // ---------------------------------------------------------------- Reproducción
        item { SectionHeader("Reproducción") {} }
        item {
            Column {
                OptionRow(
                    "Resolución máxima",
                    listOf(1080, 720, 480, 2160, 0),
                    settings.maxVideoHeight,
                    {
                        when (it) {
                            0 -> "Sin límite"
                            2160 -> "4K"
                            else -> "${it}p"
                        }
                    },
                ) { v -> repo.updateSettings { it.copy(maxVideoHeight = v) } }
                SwitchRow(
                    "Forzar máxima calidad",
                    "Elige siempre la mejor calidad disponible (hasta la resolución máxima) sin adaptarse a la conexión ni a la pantalla.",
                    settings.forceHighestQuality,
                ) { v -> repo.updateSettings { it.copy(forceHighestQuality = v) } }
                OptionRow("Búfer", listOf(5, 10, 15, 30, 60), settings.bufferSeconds, { "$it s" }) { v ->
                    repo.updateSettings { it.copy(bufferSeconds = v) }
                }
                OptionRow("Formato de emisión Xtream", listOf("ts", "m3u8"), settings.xtreamFormat, { if (it == "ts") "MPEG-TS" else "HLS" }) { v ->
                    repo.updateSettings { it.copy(xtreamFormat = v) }
                }
                Hint("El formato Xtream se aplica al volver a actualizar la lista.")
                UserAgentField(settings) { v -> repo.updateSettings { it.copy(userAgent = v) } }
            }
        }

        item { SectionHeader("Historial") {} }
        item {
            OutlinedButton(onClick = { repo.clearRecents() }, modifier = Modifier.focusBorder()) { Text("Borrar canales recientes") }
        }

        item {
            Text(
                "NexoTV 1.0 · Reproductor IPTV. La app no incluye canales: usa las listas de tu proveedor.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 32.dp, bottom = 16.dp),
            )
        }
    }

    if (adding) {
        SourceDialog(null, onDismiss = { adding = false }) {
            adding = false
            repo.addSource(it)
        }
    }
    editing?.let { s ->
        SourceDialog(s, onDismiss = { editing = null }) {
            editing = null
            repo.updateSource(it.copy(id = s.id, enabled = s.enabled))
        }
    }
    if (addingEpg) {
        EpgDialog(onDismiss = { addingEpg = false }) { name, url ->
            addingEpg = false
            repo.addEpgSource(name, url)
        }
    }
    confirmDelete?.let { (msg, action) ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Confirmar") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { action(); confirmDelete = null }, modifier = Modifier.focusBorder()) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }, modifier = Modifier.focusBorder()) { Text("Cancelar") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SectionHeader(title: String, actions: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) { actions() }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun ItemCard(
    title: String,
    subtitle: String,
    status: String?,
    extra: String? = null,
    actions: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (extra != null) {
                    Text(extra, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!status.isNullOrBlank()) {
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status.startsWith("Error") || status.startsWith("Archivo dañado")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> OptionRow(label: String, options: List<T>, selected: T, text: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o ->
                FilterChip(
                    selected = o == selected,
                    onClick = { onSelect(o) },
                    label = { Text(text(o)) },
                    modifier = Modifier.focusBorder(RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.focusBorder(RoundedCornerShape(50)))
    }
}

@Composable
private fun UserAgentField(settings: AppSettings, onSave: (String) -> Unit) {
    var ua by remember(settings.userAgent) { mutableStateOf(settings.userAgent) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text("User-Agent", style = MaterialTheme.typography.bodyLarge)
        Hint("Algunos proveedores solo funcionan con un User-Agent concreto.")
        OutlinedTextField(value = ua, onValueChange = { ua = it }, singleLine = true, modifier = Modifier.fillMaxWidth().focusBorder())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            OutlinedButton(onClick = { onSave(ua.trim().ifBlank { Net.DEFAULT_UA }) }, enabled = ua != settings.userAgent, modifier = Modifier.focusBorder()) {
                Text("Guardar")
            }
            TextButton(onClick = { ua = Net.DEFAULT_UA; onSave(Net.DEFAULT_UA) }, modifier = Modifier.focusBorder()) { Text("Restablecer") }
        }
    }
}
