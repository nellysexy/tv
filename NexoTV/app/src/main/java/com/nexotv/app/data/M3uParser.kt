package com.nexotv.app.data

import java.io.InputStream

object M3uParser {

    class Result(val channels: List<Channel>, val epgUrls: List<String>)

    private val attrRegex = Regex("""([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"""")

    fun parse(input: InputStream, sourceId: String): Result {
        val channels = ArrayList<Channel>()
        val epgUrls = LinkedHashSet<String>()
        val usedIds = HashSet<String>()

        var info: String? = null
        var extGroup: String? = null
        var userAgent: String? = null
        var referrer: String? = null

        input.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (raw in lines) {
                val line = raw.trim().removePrefix("﻿")
                when {
                    line.isEmpty() -> Unit
                    line.startsWith("#EXTM3U", ignoreCase = true) -> {
                        val attrs = parseAttrs(line)
                        listOf("url-tvg", "x-tvg-url", "tvg-url").forEach { key ->
                            attrs[key]?.split(',')
                                ?.map { it.trim() }
                                ?.filter { it.startsWith("http", ignoreCase = true) }
                                ?.forEach { epgUrls.add(it) }
                        }
                    }
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        info = line
                        extGroup = null
                        userAgent = null
                        referrer = null
                    }
                    line.startsWith("#EXTGRP:", ignoreCase = true) -> extGroup = line.substring(8).trim()
                    line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                        val opt = line.substring(11).trim()
                        val key = opt.substringBefore('=').lowercase()
                        val value = opt.substringAfter('=', "").trim()
                        when (key) {
                            "http-user-agent" -> userAgent = value
                            "http-referrer", "http-referer" -> referrer = value
                        }
                    }
                    line.startsWith("#") -> Unit
                    else -> {
                        val channel = buildChannel(
                            sourceId, info, line, extGroup, userAgent, referrer, channels.size + 1, usedIds
                        )
                        channels.add(channel)
                        info = null
                        extGroup = null
                        userAgent = null
                        referrer = null
                    }
                }
            }
        }
        return Result(channels, epgUrls.toList())
    }

    private fun parseAttrs(text: String): Map<String, String> {
        val map = HashMap<String, String>()
        attrRegex.findAll(text).forEach { m -> map[m.groupValues[1].lowercase()] = m.groupValues[2].trim() }
        return map
    }

    private fun buildChannel(
        sourceId: String,
        info: String?,
        urlLine: String,
        extGroup: String?,
        vlcUserAgent: String?,
        vlcReferrer: String?,
        position: Int,
        usedIds: MutableSet<String>,
    ): Channel {
        var attrsPart = ""
        var name = ""
        if (info != null) {
            val body = info.substringAfter(':', "")
            var inQuotes = false
            var split = -1
            for (i in body.indices) {
                val c = body[i]
                if (c == '"') inQuotes = !inQuotes
                else if (c == ',' && !inQuotes) {
                    split = i
                    break
                }
            }
            if (split >= 0) {
                attrsPart = body.substring(0, split)
                name = body.substring(split + 1).trim()
            } else {
                attrsPart = body
            }
        }
        val attrs = parseAttrs(attrsPart)

        // Cabeceras estilo Kodi: http://url|User-Agent=xxx&Referer=yyy
        var url = urlLine
        var userAgent = vlcUserAgent ?: attrs["http-user-agent"] ?: attrs["user-agent"]
        var referrer = vlcReferrer ?: attrs["http-referrer"] ?: attrs["referrer"]
        val pipe = urlLine.indexOf('|')
        if (pipe > 0) {
            url = urlLine.substring(0, pipe).trim()
            urlLine.substring(pipe + 1).split('&').forEach { kv ->
                val k = kv.substringBefore('=').trim().lowercase()
                val v = kv.substringAfter('=', "").trim()
                when (k) {
                    "user-agent" -> userAgent = v
                    "referer", "referrer" -> referrer = v
                }
            }
        }

        val tvgName = attrs["tvg-name"]?.takeIf { it.isNotBlank() }
        if (name.isBlank()) name = tvgName ?: url.substringAfterLast('/').ifBlank { "Canal $position" }
        val group = (attrs["group-title"] ?: extGroup)?.takeIf { it.isNotBlank() } ?: NO_GROUP
        val number = (attrs["tvg-chno"] ?: attrs["channel-number"])?.toIntOrNull() ?: position

        val baseId = "$sourceId:" + Integer.toHexString((name + "|" + url).hashCode())
        var id = baseId
        var n = 1
        while (!usedIds.add(id)) {
            n++
            id = "$baseId#$n"
        }

        return Channel(
            id = id,
            sourceId = sourceId,
            name = name,
            url = url,
            logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() } ?: attrs["logo"]?.takeIf { it.isNotBlank() },
            group = group,
            tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() },
            tvgName = tvgName,
            userAgent = userAgent?.takeIf { it.isNotBlank() },
            referrer = referrer?.takeIf { it.isNotBlank() },
            number = number,
        )
    }
}
