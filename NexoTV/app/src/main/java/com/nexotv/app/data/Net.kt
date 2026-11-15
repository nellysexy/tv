package com.nexotv.app.data

import android.content.Context
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

object Net {
    const val DEFAULT_UA = "NexoTV/1.0 (Linux; Android)"

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /** Abre una URL http(s) o un archivo local (content:// o file://) sin descomprimir. */
    fun openRaw(context: Context, url: String, userAgent: String): InputStream {
        val trimmed = url.trim()
        val uri = Uri.parse(trimmed)
        if (uri.scheme == "content" || uri.scheme == "file") {
            return context.contentResolver.openInputStream(uri)
                ?: throw IOException("No se pudo abrir el archivo")
        }
        val request = Request.Builder()
            .url(trimmed)
            .header("User-Agent", userAgent)
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw IOException("HTTP $code")
        }
        val body = response.body ?: run {
            response.close()
            throw IOException("Respuesta vacía")
        }
        return body.byteStream()
    }

    fun httpGetString(url: String, userAgent: String): String {
        val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body?.string() ?: ""
        }
    }

    /** Detecta gzip por sus bytes mágicos y lo descomprime de forma transparente. */
    fun maybeGunzip(input: InputStream): InputStream {
        val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input, 1 shl 16)
        buffered.mark(4)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()
        return if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(buffered, 1 shl 16) else buffered
    }
}
