package com.miappvideos.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log

object UpdateInstaller {
    private const val TAG = "UpdateInstaller"

    fun installUpdate(context: Context, apkUrl: String, apkSha256: String?) {
        try {
            // Usar android DownloadManager
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val uri = Uri.parse(apkUrl)

            val request = android.app.DownloadManager.Request(uri)
                .setTitle("MY-TUBE Update")
                .setDescription("Descargando actualización de MY-TUBE")
                .setAllowedNetworkTypes(android.app.DownloadManager.Request.NETWORK_WIFI)
                .setVisibleInDownloadsUi(false)
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                    "mytube.apk"
                )

            downloadManager.enqueue(request)

            // Nota: Para una implementación real, se requiere un BroadcastReceiver que escuche
            // DownloadManager.ACTION_DOWNLOAD_COMPLETE para verificar el SHA-256
            // y disparar la instalación. Por ahora, el flujo queda preparado para la validación.
        } catch (e: Exception) {
            Log.e(TAG, "Error installing update", e)
        }
    }

    private fun verifySha256(file: java.io.File, expected: String): Boolean {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read: Int
                while (input.read(buffer).also { read = it } != -1) digest.update(buffer, 0, read)
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            actual.equals(expected, ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying SHA-256", e)
            false
        }
    }

    private fun intentInstall(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            `package` = "com.android.packageinstaller"
            action = android.content.Intent.ACTION_INSTALL_PACKAGE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}