package com.miappvideos.ui.update

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {
    private const val TAG = "UpdateManager"
    const val VERSION_URL = "https://mytubemusic.vercel.app/version.json"

    private const val CONNECT_TIMEOUT_MS = 5000
    private const val READ_TIMEOUT_MS = 5000

    // Debe coincidir con build.gradle.kts
    const val CURRENT_VERSION_CODE = 21
    const val CURRENT_VERSION_NAME = "2.1.0"

    data class RemoteVersion(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val releaseNotes: List<String>,
        val mandatory: Boolean,
        val sha256: String
    )

    fun interface OnUpdateAvailable {
        fun onUpdateAvailable(versionCode: Int, versionName: String, apkUrl: String, sha256: String, releaseNotes: List<String>)
    }

    fun interface OnNoUpdate {
        fun onNoUpdate()
    }

    fun checkUpdate(context: Context, onUpdateAvailable: OnUpdateAvailable, onNoUpdate: OnNoUpdate) {
        CoroutineScope(Dispatchers.IO).launch {
            val currentVersion = getCurrentVersion(context)
            val remote = fetchRemoteVersion()

            if (remote == null) {
                onNoUpdate.onNoUpdate()
                return@launch
            }

            if (remote.versionCode > currentVersion) {
                onUpdateAvailable.onUpdateAvailable(
                    remote.versionCode,
                    remote.versionName,
                    remote.apkUrl,
                    remote.sha256,
                    remote.releaseNotes
                )
            } else {
                onNoUpdate.onNoUpdate()
            }
        }
    }

    private fun getCurrentVersion(context: Context): Int {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.longVersionCode.toInt()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current version", e)
            CURRENT_VERSION_CODE
        }
    }

    private fun fetchRemoteVersion(): RemoteVersion? {
        return try {
            val url = URL(VERSION_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setDoOutput(false)
                setDoInput(true)
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP ${connection.responseCode} fetching version.json")
                return null
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val stringBuilder = StringBuilder()
            while (true) {
                val line = reader.readLine() ?: break
                stringBuilder.append(line)
            }
            reader.close()

            parseRemoteVersion(stringBuilder.toString())

        } catch (e: Exception) {
            Log.e(TAG, "Error fetching remote version", e)
            null
        }
    }

    private fun parseRemoteVersion(jsonString: String): RemoteVersion? {
        return try {
            val obj = JSONObject(jsonString)
            val code = obj.getInt("versionCode")
            val name = obj.getString("versionName")
            val apkUrl = obj.getString("apkUrl")
            val sha256 = obj.getString("sha256")
            val isMandatory = obj.optBoolean("mandatory", false)

            val notesArray = obj.getJSONArray("releaseNotes")
            val notes = mutableListOf<String>()
            for (i in 0 until notesArray.length()) {
                notes.add(notesArray.getString(i))
            }

            RemoteVersion(
                versionCode = code,
                versionName = name,
                apkUrl = apkUrl,
                releaseNotes = notes,
                mandatory = isMandatory,
                sha256 = sha256
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing version JSON", e)
            null
        }
    }
}
