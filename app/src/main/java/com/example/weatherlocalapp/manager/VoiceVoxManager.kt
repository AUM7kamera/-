package com.example.weatherlocalapp.manager

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class VoiceVoxSpeaker(val name: String, val id: Int)

class VoiceVoxManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val speakers = listOf(
        VoiceVoxSpeaker("ずんだもん (ノーマル)", 3),
        VoiceVoxSpeaker("ずんだもん (あまあま)", 1),
        VoiceVoxSpeaker("ずんだもん (ツンツン)", 7),
        VoiceVoxSpeaker("ずんだもん (ささやき)", 5),
        VoiceVoxSpeaker("四国めたん (ノーマル)", 2),
        VoiceVoxSpeaker("四国めたん (あまあま)", 0),
        VoiceVoxSpeaker("四国めたん (ツンツン)", 6),
        VoiceVoxSpeaker("四国めたん (ささやき)", 4),
        VoiceVoxSpeaker("春日部つむぎ (ノーマル)", 8),
        VoiceVoxSpeaker("雨晴はう (ノーマル)", 10),
        VoiceVoxSpeaker("波音リツ (ノーマル)", 9)
    )

    suspend fun synthesizeVoice(text: String, speakerId: Int, hostIp: String = "127.0.0.1"): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val queryUrl = HttpUrl.Builder()
                .scheme("http")
                .host(hostIp)
                .port(50021)
                .addPathSegment("audio_query")
                .addQueryParameter("text", text)
                .addQueryParameter("speaker", speakerId.toString())
                .build()

            val queryRequest = Request.Builder()
                .url(queryUrl)
                .post("".toRequestBody("application/json".toMediaType()))
                .build()

            val queryResponse = client.newCall(queryRequest).execute()
            if (!queryResponse.isSuccessful) {
                throw Exception("Failed to create audio query: HTTP ${queryResponse.code}")
            }
            val queryJson = queryResponse.body?.string() ?: throw Exception("Empty response for audio query")

            val synthesisUrl = HttpUrl.Builder()
                .scheme("http")
                .host(hostIp)
                .port(50021)
                .addPathSegment("synthesis")
                .addQueryParameter("speaker", speakerId.toString())
                .build()

            val synthesisRequest = Request.Builder()
                .url(synthesisUrl)
                .post(queryJson.toRequestBody("application/json".toMediaType()))
                .build()

            val synthesisResponse = client.newCall(synthesisRequest).execute()
            if (!synthesisResponse.isSuccessful) {
                throw Exception("Failed to synthesize audio: HTTP ${synthesisResponse.code}")
            }

            synthesisResponse.body?.bytes() ?: throw Exception("Empty voice synthesis result")
        }
    }

    suspend fun saveWavFile(context: Context, bytes: ByteArray, fileName: String): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/wav")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/VOICEVOX")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val uri = resolver.insert(collection, contentValues) ?: throw Exception("Failed to insert MediaStore record")

                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        out.write(bytes)
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    uri
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val voiceVoxDir = File(downloadsDir, "VOICEVOX")
                if (!voiceVoxDir.exists()) {
                    voiceVoxDir.mkdirs()
                }
                val destFile = File(voiceVoxDir, fileName)
                FileOutputStream(destFile).use { out ->
                    out.write(bytes)
                }
                Uri.fromFile(destFile)
            }
        }
    }

    fun shareWavFile(context: Context, uri: Uri) {
        val shareableUri = if (uri.scheme == "file") {
            val file = File(uri.path ?: throw Exception("Invalid file URI"))
            FileProvider.getUriForFile(context, "com.example.weatherlocalapp.fileprovider", file)
        } else {
            uri
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, shareableUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val chooser = Intent.createChooser(intent, "WAV音声を他アプリに共有")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
