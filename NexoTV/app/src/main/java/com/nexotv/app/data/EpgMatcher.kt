package com.nexotv.app.data

import java.io.InputStream
import java.text.Normalizer
import java.util.Locale

/**
 * Combina varias fuentes XMLTV con la lista de canales.
 *
 * Reglas:
 *  1. Las fuentes se procesan en orden de prioridad (la primera de la lista gana).
 *  2. Un canal se asocia por: asignación manual > tvg-id exacto > nombre normalizado.
 *  3. Un canal que ya recibió guía de una fuente con más prioridad no se sobrescribe.
 */
class EpgMatcher(
    channels: List<Channel>,
    overrides: Map<String, String>,
    private val windowStart: Long,
    private val windowEnd: Long,
) {
    private val byId = HashMap<String, MutableList<String>>()
    private val byName = HashMap<String, MutableList<String>>()

    val result = HashMap<String, List<Programme>>()
    val epgChannels = ArrayList<EpgChannelInfo>()

    init {
        for (ch in channels) {
            val key = overrides[ch.id] ?: ch.tvgId
            if (!key.isNullOrBlank()) byId.getOrPut(key.lowercase(Locale.ROOT)) { ArrayList(1) }.add(ch.id)
            // La asociación por nombre solo se usa si el canal no tiene asignación manual.
            if (overrides[ch.id] == null) {
                val names = listOfNotNull(ch.name, ch.tvgName).map { normalizeName(it) }.filter { it.isNotEmpty() }.distinct()
                names.forEach { byName.getOrPut(it) { ArrayList(1) }.add(ch.id) }
            }
        }
    }

    class SourceStats(val matchedChannels: Int, val programmes: Int, val xmlChannels: Int)

    fun addSource(sourceId: String, input: InputStream): SourceStats {
        // canal de la lista -> (id XMLTV, asociado por id)
        val binding = HashMap<String, Pair<String, Boolean>>()
        val programmes = HashMap<String, ArrayList<Programme>>()
        var reverse: Map<String, List<String>>? = null
        var xmlChannels = 0
        var kept = 0

        val handler = object : XmltvParser.Handler {
            override fun onChannel(id: String, displayNames: List<String>) {
                xmlChannels++
                epgChannels.add(EpgChannelInfo(id, displayNames.firstOrNull() ?: id, sourceId))
                reverse = null
                byId[id.lowercase(Locale.ROOT)]?.forEach { target ->
                    if (target !in result) binding[target] = id to true
                }
                (displayNames + id.substringBeforeLast('.')).forEach { n ->
                    byName[normalizeName(n)]?.forEach { target ->
                        if (target !in result && binding[target] == null) binding[target] = id to false
                    }
                }
            }

            override fun onProgramme(channelId: String, programme: Programme) {
                if (programme.stop < windowStart || programme.start > windowEnd) return
                val rev = reverse ?: binding.entries
                    .groupBy({ it.value.first }, { it.key })
                    .also { reverse = it }
                val bound = rev[channelId]
                val hasTargets = bound != null ||
                    byId[channelId.lowercase(Locale.ROOT)]?.any { it !in result && it !in binding } == true
                if (!hasTargets) return
                programmes.getOrPut(channelId) { ArrayList() }.add(programme)
                kept++
            }
        }

        try {
            XmltvParser.parse(input, handler)
        } finally {
            // Aunque el XML esté cortado, aprovechamos lo que se haya leído.
            val rev = binding.entries.groupBy({ it.value.first }, { it.key })
            var matched = 0
            for ((xmlId, list) in programmes) {
                val sorted = list.sortedBy { it.start }.distinctBy { it.start }
                val targets = rev[xmlId]
                    ?: byId[xmlId.lowercase(Locale.ROOT)]?.filter { it !in binding }
                    ?: continue
                for (t in targets) {
                    if (t !in result) {
                        result[t] = sorted
                        matched++
                    }
                }
            }
            lastStats = SourceStats(matched, kept, xmlChannels)
        }
        return lastStats!!
    }

    var lastStats: SourceStats? = null
        private set

    companion object {
        private val prefix = Regex("^[a-z]{2,3}\\s*[:|]\\s*")
        private val marks = Regex("\\p{Mn}+")
        private val quality = Regex("\\b(uhd|fhd|hd|sd|4k|hevc|h265|1080p?|720p?)\\b")
        private val nonAlnum = Regex("[^a-z0-9]")

        fun normalizeName(s: String): String {
            var t = s.lowercase(Locale.ROOT).trim()
            t = prefix.replace(t, "")
            t = marks.replace(Normalizer.normalize(t, Normalizer.Form.NFD), "")
            t = quality.replace(t, "")
            return nonAlnum.replace(t, "")
        }
    }
}
