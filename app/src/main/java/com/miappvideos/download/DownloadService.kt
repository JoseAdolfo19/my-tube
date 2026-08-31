package com.miappvideos.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.miappvideos.MainActivity
import com.miappvideos.R
import com.miappvideos.api.innertube.RotatingHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.OutputStream

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = RotatingHttpClient.client()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val videoId = intent?.getStringExtra(EXTRA_VIDEO_ID) ?: return START_NOT_STICKY
        val title = intent.getStringExtra(EXTRA_TITLE) ?: videoId
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_AUDIO
        val directUrl = intent.getStringExtra(EXTRA_URL)
        val directMime = intent.getStringExtra(EXTRA_MIME)
        val directExt = intent.getStringExtra(EXTRA_EXT)
        val directLabel = intent.getStringExtra(EXTRA_LABEL)
        val notificationId = videoId.hashCode()

        startForeground(notificationId, buildProgressNotification(title, mode, 0))

        scope.launch {
            try {
                download(videoId, title, mode, notificationId, directUrl, directMime, directExt, directLabel)
            } catch (e: Exception) {
                Log.e(TAG, "descarga fallida $videoId", e)
                notifyDone(notificationId, "Error al descargar: ${e.message}", success = false)
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun download(
        videoId: String,
        title: String,
        mode: String,
        notificationId: Int,
        directUrl: String? = null,
        directMime: String? = null,
        directExt: String? = null,
        directLabel: String? = null,
    ) {
        val url = directUrl
        if (url == null) {
            notifyDone(notificationId, "URL de descarga no disponible", success = false)
            return
        }
        val mime = directMime?.ifBlank { null } ?: "application/octet-stream"
        val ext = directExt?.ifBlank { null } ?: "mp4"

        val fileName = "${sanitizeFileName(title)}.$ext"

        val written = withContext(Dispatchers.IO) {
            writeToStorage(fileName, mime, url) { bytesTotal, bytesDone ->
                notifyProgress(notificationId, title, directLabel ?: mode, bytesTotal, bytesDone)
            }
        }

        if (written == null) {
            notifyDone(notificationId, "Error al guardar la descarga", success = false)
        } else {
            notifyDone(notificationId, "Descarga completada: $written", success = true)
        }
    }

    private fun writeToStorage(
        fileName: String,
        mime: String,
        url: String,
        onProgress: (Long, Long) -> Unit,
    ): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeToMediaStore(fileName, mime, url, onProgress)
        } else {
            writeToLegacyFile(fileName, url, onProgress)
        }
    }

    private fun writeToMediaStore(
        fileName: String,
        mime: String,
        url: String,
        onProgress: (Long, Long) -> Unit,
    ): String? {
        val resolver = contentResolver
        val uniqueFileName = uniqueFileNameInMediaStore(fileName)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, uniqueFileName)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MyTube")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { out ->
                downloadRanges(url, out, onProgress)
            } ?: return null
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return "MyTube/$uniqueFileName"
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
    }

    private fun writeToLegacyFile(
        fileName: String,
        url: String,
        onProgress: (Long, Long) -> Unit,
    ): String? {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "MyTube"
        )
        if (!dir.exists() && !dir.mkdirs()) return null
        val uniqueName = uniqueFileNameInDirectory(dir, fileName)
        val file = File(dir, uniqueName)
        file.outputStream().use { out ->
            downloadRanges(url, out, onProgress)
        }
        MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), null, null)
        return "MyTube/$uniqueName"
    }

    private fun downloadRanges(url: String, out: OutputStream, onProgress: (Long, Long) -> Unit) {
        var total: Long = -1
        var done: Long = 0
        var start: Long = 0
        while (true) {
            val startBefore = start
            val end = start + CHUNK - 1
            val request = Request.Builder()
                .url(url)
                .header("Range", "bytes=$start-$end")
                .build()
            var shouldStop = false
            val response = httpClient.newCall(request).execute()
            try {
                if (!response.isSuccessful && response.code != 416 && response.code != 206) {
                    throw IOException("HTTP ${response.code}")
                }
                if (total < 0) {
                    val clen = response.header("Content-Length")?.toLongOrNull() ?: -1L
                    val contentRange = response.header("Content-Range")
                    total = if (contentRange != null) {
                        contentRange.substringAfter("/").toLongOrNull() ?: -1L
                    } else if (clen > 0 && response.code == 206) {
                        clen + start
                    } else {
                        -1L
                    }
                    if (total > 0) onProgress(total, done)
                }
                val body = response.body
                if (body == null) {
                    shouldStop = true
                } else {
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            done += read
                            if (total > 0 && done % (2 * 1024 * 1024) == 0L) {
                                onProgress(total, done)
                            }
                        }
                    }
                }
                shouldStop = shouldStop || response.code == 200 || response.code == 416 || response.code != 206
            } finally {
                response.close()
            }
            if (shouldStop) break
            if (total > 0 && done >= total) break
            if (total < 0 && done == 0L) break
            // Salvaguarda anti-bucle infinito: si una iteración completa no
            // avanzó (p. ej. servidor que responde 206 con body vacío en la
            // misma posición), abandonar en lugar de quedar colgado.
            if (done <= startBefore) break
            start = done
        }
        onProgress(total.coerceAtLeast(done), done)
    }

    private fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.take(80).ifBlank { "descarga" }
    }

    /**
     * Genera un nombre de archivo único dentro de Descargas/MyTube consultando
     * los nombres ya existentes en MediaStore para el mismo directorio relativo.
     * Evita sobrescribir descargas previas del mismo título.
     */
    private fun uniqueFileNameInMediaStore(baseName: String): String {
        val resolver = contentResolver
        val usedNames = hashSetOf<String>()
        val projection = arrayOf(MediaStore.Downloads.DISPLAY_NAME)
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(Environment.DIRECTORY_DOWNLOADS + "/MyTube/")
        runCatching {
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val col = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    cursor.getString(col)?.let { usedNames.add(it) }
                }
            }
        }
        return buildUniqueName(baseName, usedNames)
    }

    /**
     * Genera un nombre de archivo único dentro de un directorio del sistema de
     * archivos (Android 9-). Evita sobrescribir descargas previas del mismo título.
     */
    private fun uniqueFileNameInDirectory(dir: File, baseName: String): String {
        val usedNames = dir.list()?.toHashSet() ?: hashSetOf()
        return buildUniqueName(baseName, usedNames)
    }

    /**
     * Si [baseName] ya existe en [usedNames], anade un sufijo numerico:
     * "cancion (1).mp3", "cancion (2).mp3", etc.
     */
    private fun buildUniqueName(baseName: String, usedNames: Set<String>): String {
        if (baseName !in usedNames) return baseName
        val dot = baseName.lastIndexOf('.')
        val stem = if (dot > 0) baseName.substring(0, dot) else baseName
        val ext = if (dot > 0) baseName.substring(dot) else ""
        var i = 1
        var candidate: String
        do {
            candidate = "$stem ($i)$ext"
            i++
        } while (candidate in usedNames)
        return candidate
    }

    private fun buildProgressNotification(title: String, mode: String, progress: Int): android.app.Notification {
        val label = if (mode == MODE_VIDEO) "Descargando video" else "Descargando audio"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(label)
            .setContentText(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, progress == 0)
            .build()
    }

    private fun notifyProgress(notificationId: Int, title: String, modeOrLabel: String, total: Long, done: Long) {
        val percent = if (total > 0) ((done * 100) / total).toInt() else 0
        val heading = when {
            modeOrLabel == MODE_VIDEO -> "Descargando video"
            modeOrLabel == MODE_AUDIO -> "Descargando audio"
            else -> "Descargando $modeOrLabel"
        }
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(heading)
            .setContentText(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (total > 0) {
            builder.setProgress(100, percent, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        getSystemService(NotificationManager::class.java).notify(notificationId, builder.build())
    }

    private fun notifyDone(notificationId: Int, text: String, success: Boolean) {
        val contentIntent = PendingIntent.getActivity(
            this,
            notificationId,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(if (success) "Descarga completada" else "Descarga fallida")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
        getSystemService(NotificationManager::class.java).notify(notificationId, builder.build())
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Descargas",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Progreso de descargas de MyTube" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DownloadService"
        private const val CHANNEL_ID = "downloads"
        private const val CHUNK = 1024 * 1024L

        const val EXTRA_VIDEO_ID = "video_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MODE = "mode"
        const val EXTRA_URL = "url"
        const val EXTRA_MIME = "mime"
        const val EXTRA_EXT = "ext"
        const val EXTRA_LABEL = "label"
        const val MODE_AUDIO = "audio"
        const val MODE_VIDEO = "video"
    }
}
