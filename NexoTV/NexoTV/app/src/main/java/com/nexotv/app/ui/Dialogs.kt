package com.nexotv.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexotv.app.data.Channel
import com.nexotv.app.data.EpgMatcher
import com.nexotv.app.data.PlaylistSource
import com.nexotv.app.data.Repository
import com.nexotv.app.data.SourceType

@Composable
private fun rememberFilePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            onPicked(uri)
        }
    }
    return { launcher.launch(arrayOf("*/*")) }
}

/** Diálogo para añadir o editar una lista (M3U por URL, archivo M3U o Xtream Codes). */
@Composable
fun SourceDialog(initial: PlaylistSource?, onDismiss: () -> Unit, onSave: (PlaylistSource) -> Unit) {
    var type by remember { mutableStateOf(initial?.type ?: SourceType.M3U_URL) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var server by remember { mutableStateOf(initial?.server ?: "") }
    var user by remember { mutableStateOf(initial?.username ?: "") }
    var pass by remember { mutableStateOf(initial?.password ?: "") }
    val pick = rememberFilePicker { url = it.toString() }

    val valid = when (type) {
        SourceType.M3U_URL -> url.isNotBlank()
        SourceType.M3U_FILE -> url.isNotBlank()
        SourceType.XTREAM -> server.isNotBlank() && user.isNotBlank() && pass.isNotBlank()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Añadir lista" else "Editar lista") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (initial == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(type == SourceType.M3U_URL, { type = SourceType.M3U_URL }, { Text("M3U (URL)") })
                        FilterChip(type == SourceType.M3U_FILE, { type = SourceType.M3U_FILE }, { Text("Archivo") })
                        FilterChip(type == SourceType.XTREAM, { type = SourceType.XTREAM }, { Text("Xtream") })
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Nombre (opcional)") }, modifier = Modifier.fillMaxWidth().focusBorder(),
                )
                when (type) {
                    SourceType.M3U_URL -> OutlinedTextField(
                        value = url, onValueChange = { url = it }, singleLine = true,
                        label = { Text("URL de la lista M3U / M3U8") },
                        placeholder = { Text("http://…/lista.m3u") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth().focusBorder(),
                    )
                    SourceType.M3U_FILE -> {
                        OutlinedButton(onClick = pick, modifier = Modifier.focusBorder()) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null)
                            Spacer(Modifier.padding(4.dp))
                            Text(if (url.isBlank()) "Elegir archivo .m3u" else "Cambiar archivo")
                        }
                        if (url.isNotBlank()) {
                            Text(
                                Uri.decode(url).substringAfterLast('/'),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    SourceType.XTREAM -> {
                        OutlinedTextField(
                            value = server, onValueChange = { server = it }, singleLine = true,
                            label = { Text("Servidor") }, placeholder = { Text("http://servidor.com:8080") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth().focusBorder(),
                        )
                        OutlinedTextField(
                            value = user, onValueChange = { user = it }, singleLine = true,
                            label = { Text("Usuario") }, modifier = Modifier.fillMaxWidth().focusBorder(),
                        )
                        OutlinedTextField(
                            value = pass, onValueChange = { pass = it }, singleLine = true,
                            label = { Text("Contraseña") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth().focusBorder(),
                        )
                    }
                }
                Text(
                    when (type) {
                        SourceType.XTREAM -> "Se cargarán los canales en directo y la guía EPG del servidor automáticamente."
                        else -> "Si la lista incluye url-tvg, su guía EPG se añadirá automáticamente."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                modifier = Modifier.focusBorder(),
                onClick = {
                    val finalName = name.trim().ifBlank {
                        when (type) {
                            SourceType.XTREAM -> server.trim().removePrefix("http://").removePrefix("https://").substringBefore('/')
                            SourceType.M3U_URL -> Uri.parse(url.trim()).host ?: "Mi lista"
                            SourceType.M3U_FILE -> Uri.decode(url).substringAfterLast('/').substringAfterLast(':').ifBlank { "Mi lista" }
                        }
                    }
                    onSave(
                        PlaylistSource(
                            id = initial?.id ?: "",
                            name = finalName,
                            type = type,
                            url = url.trim(),
                            server = server.trim(),
                            username = user.trim(),
                            password = pass.trim(),
                            lastUpdated = initial?.lastUpdated ?: 0L,
                            channelCount = initial?.channelCount ?: 0,
                        )
                    )
                },
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.focusBorder()) { Text("Cancelar") } },
    )
}

@Composable
fun EpgDialog(onDismiss: () -> Unit, onSave: (name: String, url: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val pick = rememberFilePicker { url = it.toString() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir fuente EPG") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Nombre (opcional)") }, modifier = Modifier.fillMaxWidth().focusBorder(),
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it }, singleLine = true,
                    label = { Text("URL XMLTV (.xml o .xml.gz)") },
                    placeholder = { Text("https://…/guide.xml.gz") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth().focusBorder(),
                )
                OutlinedButton(onClick = pick, modifier = Modifier.focusBorder()) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text("O elegir un archivo")
                }
                Text(
                    "Puedes añadir tantas fuentes como quieras. Si un canal aparece en varias, se usa la que esté más arriba en la lista.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank(),
                modifier = Modifier.focusBorder(),
                onClick = {
                    val n = name.trim().ifBlank { Uri.parse(url.trim()).host ?: "EPG" }
                    onSave(n, url.trim())
                },
            ) { Text("Añadir") }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.focusBorder()) { Text("Cancelar") } },
    )
}

/** Permite elegir a mano qué canal de la guía corresponde a un canal de la lista. */
@Composable
fun EpgAssignDialog(repo: Repository, channel: Channel, onDismiss: () -> Unit) {
    val epgChannels by repo.epgChannels.collectAsStateWithLifecycle()
    val epgSources by repo.epgSources.collectAsStateWithLifecycle()
    val overrides by repo.overrides.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf(channel.tvgName ?: channel.name) }
    val sourceNames = remember(epgSources) { epgSources.associate { it.id to it.name } }
    val results = remember(epgChannels, query) {
        val q = EpgMatcher.normalizeName(query)
        if (q.isEmpty()) epgChannels.take(200)
        else epgChannels.filter {
            EpgMatcher.normalizeName(it.name).contains(q) || it.id.lowercase().contains(query.trim().lowercase())
        }.take(200)
    }
    val current = overrides[channel.id]

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Guía para «${channel.name}»") },
        text = {
            Column {
                Text(
                    "Actual: " + (current?.let { "$it (manual)" } ?: channel.tvgId?.let { "$it (automático)" } ?: "por nombre (automático)"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Buscar en la guía") }, modifier = Modifier.fillMaxWidth().focusBorder(),
                )
                Spacer(Modifier.height(8.dp))
                if (epgChannels.isEmpty()) {
                    Text("Todavía no hay ninguna guía cargada.", style = MaterialTheme.typography.bodyMedium)
                }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(results, key = { it.sourceId + "|" + it.id }) { e ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .focusBorder()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    repo.setEpgOverride(channel.id, e.id)
                                    onDismiss()
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Text(e.name, fontWeight = if (e.id == current) FontWeight.Bold else FontWeight.Normal)
                            Text(
                                "${e.id} · ${sourceNames[e.sourceId] ?: ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    repo.setEpgOverride(channel.id, null)
                    onDismiss()
                },
                modifier = Modifier.focusBorder(),
            ) { Text("Automático") }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.focusBorder()) { Text("Cerrar") } },
    )
}
