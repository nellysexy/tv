package com.nexotv.app.data

/** Guía ya combinada: id de canal de la lista -> programas ordenados por hora de inicio. */
class EpgIndex(val raw: Map<String, List<Programme>>, private val offsetMs: Long) {

    fun withOffset(offsetMs: Long) = EpgIndex(raw, offsetMs)

    fun hasData(channelId: String) = raw[channelId]?.isNotEmpty() == true

    fun programmes(channelId: String): List<Programme> {
        val list = raw[channelId] ?: return emptyList()
        return if (offsetMs == 0L) list else list.map { shift(it) }
    }

    fun current(channelId: String, time: Long): Programme? {
        val list = raw[channelId] ?: return null
        val t = time - offsetMs
        val i = indexAt(list, t)
        return if (i >= 0 && list[i].stop > t) shift(list[i]) else null
    }

    fun next(channelId: String, time: Long): Programme? {
        val list = raw[channelId] ?: return null
        val t = time - offsetMs
        val i = indexAt(list, t)
        return list.getOrNull(i + 1)?.let { shift(it) }
    }

    private fun shift(p: Programme) =
        if (offsetMs == 0L) p else p.copy(start = p.start + offsetMs, stop = p.stop + offsetMs)

    /** Último programa cuyo inicio es <= t, o -1. */
    private fun indexAt(list: List<Programme>, t: Long): Int {
        var lo = 0
        var hi = list.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (list[mid].start <= t) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }

    companion object {
        val EMPTY = EpgIndex(emptyMap(), 0L)
    }
}
