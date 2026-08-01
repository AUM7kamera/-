package com.example.weatherlocalapp.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class VersionInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String
)

class AppUpdater {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Checks if there's a new version available by parsing version.json.
     * Returns null if current is up-to-date or newer.
     */
    suspend fun checkForUpdates(currentVersionCode: Int, versionJsonUrl: String): Result<VersionInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(versionJsonUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("Failed to check update: HTTP ${response.code}")
            }
            
            val json = response.body?.string() ?: throw Exception("Empty JSON response")
            val remoteInfo = gson.fromJson(json, VersionInfo::class.java)
            
            if (remoteInfo.versionCode > currentVersionCode) {
                remoteInfo
            } else {
                null
            }
        }
    }

    /**
     * Downloads APK from apkUrl to application's external cache dir.
     */
    suspend fun downloadApk(
        context: Context, 
        apkUrl: String, 
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(apkUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("Failed to download APK: HTTP ${response.code}")
            }

            val body = response.body ?: throw Exception("Empty body in APK download response")
            val totalBytes = body.contentLength()
            val apkFile = File(context.externalCacheDir, "update.apk")
            
            body.byteStream().use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            val progress = totalRead.toFloat() / totalBytes.toFloat()
                            onProgress(progress)
                        }
                    }
                }
            }
            apkFile
        }
    }

    /**
     * Checks whether Android OS permits this app to request package installations.
     */
    fun canInstallPackages(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Directs the user to the Android settings screen for installing unknown apps.
     */
    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Triggers the APK installer interface.
     */
    fun installApk(context: Context, apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "com.example.weatherlocalapp.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
