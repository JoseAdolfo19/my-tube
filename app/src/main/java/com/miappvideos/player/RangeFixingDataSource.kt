package com.miappvideos.player

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener

/**
 * Envuelve otro HttpDataSource y corrige las peticiones con rango abierto.
 *
 * 1) ExoPlayer pide el primer segmento con `Range: bytes=0-` (longitud desconocida)
 *    y googlevideo responde 403 a los rangos abiertos, asi que usamos el tamano
 *    real del parametro `clen` (o un limite generoso si no existe).
 * 2) Algunos URLs (con restriccion geografica `gcr`) rechazan rangos mayores
 *    a ~1 MB (403), por eso cada peticion se limita a 1 MB como maximo;
 *    ExoPlayer continua con la siguiente peticion al terminar el rango.
 */
class RangeFixingDataSource(private val upstream: HttpDataSource) : HttpDataSource {

    companion object {
        private const val MAX_CHUNK = 1024L * 1024L
    }

    override fun open(dataSpec: DataSpec): Long {
        val hasGeoRestriction = dataSpec.uri.getQueryParameter("gcr") != null
        val chunkLimit = if (hasGeoRestriction) MAX_CHUNK else Long.MAX_VALUE

        val fixed = if (dataSpec.length < 0L) {
            val clen = parseClen(dataSpec.uri)
            val available = if (clen != null) (clen - dataSpec.position).coerceAtLeast(1L) else MAX_CHUNK
            dataSpec.buildUpon()
                .setLength(available.coerceAtMost(chunkLimit))
                .build()
        } else {
            dataSpec.buildUpon()
                .setLength(dataSpec.length.coerceAtMost(chunkLimit))
                .build()
        }
        return upstream.open(fixed)
    }

    private fun parseClen(uri: Uri): Long? {
        return try {
            uri.getQueryParameter("clen")?.toLong()
        } catch (e: Exception) {
            null
        }
    }

    override fun close() {
        upstream.close()
    }

    override fun getUri(): Uri? = upstream.uri

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int =
        upstream.read(buffer, offset, readLength)

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun setRequestProperty(name: String, value: String) {
        upstream.setRequestProperty(name, value)
    }

    override fun clearRequestProperty(name: String) {
        upstream.clearRequestProperty(name)
    }

    override fun clearAllRequestProperties() {
        upstream.clearAllRequestProperties()
    }

    override fun getResponseCode(): Int = upstream.responseCode

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }
}
