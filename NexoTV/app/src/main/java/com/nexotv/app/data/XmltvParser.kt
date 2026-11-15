package com.nexotv.app.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/** Lector XMLTV en streaming: no carga el archivo completo en memoria. */
object XmltvParser {

    interface Handler {
        fun onChannel(id: String, displayNames: List<String>)
        fun onProgramme(channelId: String, programme: Programme)
    }

    fun parse(input: InputStream, handler: Handler) {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false
        val parser = factory.newPullParser()
        parser.setInput(input, null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> readChannel(parser, handler)
                    "programme" -> readProgramme(parser, handler)
                }
            }
            event = parser.next()
        }
    }

    private fun readChannel(p: XmlPullParser, handler: Handler) {
        val id = p.getAttributeValue(null, "id")
        val depth = p.depth
        val names = ArrayList<String>(2)
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_DOCUMENT) break
            if (e == XmlPullParser.END_TAG && p.depth == depth) break
            if (e == XmlPullParser.START_TAG && p.name == "display-name") {
                val t = p.nextText().trim()
                if (t.isNotEmpty()) names.add(t)
            }
        }
        if (!id.isNullOrBlank()) handler.onChannel(id.trim(), names)
    }

    private fun readProgramme(p: XmlPullParser, handler: Handler) {
        val channel = p.getAttributeValue(null, "channel")?.trim()
        val start = parseTime(p.getAttributeValue(null, "start"))
        var stop = parseTime(p.getAttributeValue(null, "stop"))
        val depth = p.depth
        var title: String? = null
        var desc: String? = null
        var category: String? = null
        var subTitle: String? = null
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_DOCUMENT) break
            if (e == XmlPullParser.END_TAG && p.depth == depth) break
            if (e == XmlPullParser.START_TAG) {
                when (p.name) {
                    "title" -> { val t = p.nextText().trim(); if (title == null && t.isNotEmpty()) title = t }
                    "sub-title" -> { val t = p.nextText().trim(); if (subTitle == null && t.isNotEmpty()) subTitle = t }
                    "desc" -> { val t = p.nextText().trim(); if (desc == null && t.isNotEmpty()) desc = t }
                    "category" -> { val t = p.nextText().trim(); if (category == null && t.isNotEmpty()) category = t }
                }
            }
        }
        if (channel.isNullOrEmpty() || start < 0) return
        if (stop <= start) stop = start + 30 * 60_000L
        val fullDesc = listOfNotNull(subTitle, desc).joinToString(" — ").ifBlank { null }
        handler.onProgramme(
            channel,
            Programme(
                start = start,
                stop = stop,
                title = (title ?: "Sin título").take(300),
                desc = fullDesc?.take(1000),
                category = category?.take(100),
            )
        )
    }

    /** Formato XMLTV: "yyyyMMddHHmmss +HHMM" (la zona y los segundos son opcionales). */
    fun parseTime(value: String?): Long {
        if (value == null) return -1
        val t = value.trim()
        if (t.length < 12) return -1
        fun num(a: Int, b: Int): Int? = if (b <= t.length) t.substring(a, b).toIntOrNull() else null
        val y = num(0, 4) ?: return -1
        val mo = num(4, 6) ?: return -1
        val d = num(6, 8) ?: return -1
        val h = num(8, 10) ?: return -1
        val mi = num(10, 12) ?: return -1
        val s = if (t.length >= 14 && t[12].isDigit() && t[13].isDigit()) num(12, 14) ?: 0 else 0
        var offsetMinutes = 0
        val signIdx = t.indexOfAny(charArrayOf('+', '-'), 12)
        if (signIdx >= 0) {
            val tz = t.substring(signIdx + 1).replace(":", "")
            if (tz.length >= 4) {
                val oh = tz.substring(0, 2).toIntOrNull() ?: 0
                val om = tz.substring(2, 4).toIntOrNull() ?: 0
                offsetMinutes = (oh * 60 + om) * (if (t[signIdx] == '-') -1 else 1)
            }
        }
        val days = daysFromCivil(y, mo, d)
        val seconds = days * 86400L + h * 3600L + mi * 60L + s - offsetMinutes * 60L
        return seconds * 1000L
    }

    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = if (month > 2) month - 3 else month + 9
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }
}
