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
                .setDescription("Descargando MY-TUBE 2.1.0")
                .setAllowedNetworkTypes(android.app.DownloadManager.Request.NETWORK_WIFI)
                .setVisibleInDownloadsUi(false)
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                    "mytube.apk"
                )

            val downloadId = downloadManager.enqueue(request)

            // En un escenario real, verificaríamos SHA-256 y luego instalamos
            // Por ahora, simulamos abriendo la app de instalación

            // Abrir el archivo descargado (este es el flujo nativo de Android)
            val file = java.io.File(context.getExternalFilesDir("downloads"), "mytube.apk")
            if (file.exists()) {
                intentInstall(context, uri)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error installing update", e)
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