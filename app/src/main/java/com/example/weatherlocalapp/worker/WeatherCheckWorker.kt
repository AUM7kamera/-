package com.example.weatherlocalapp.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.weatherlocalapp.data.WeatherRepository

class WeatherCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repository = WeatherRepository()

    override suspend fun doWork(): Result {
        // Read area code from SharedPreferences, default to "130000" (Tokyo)
        val prefs = applicationContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val areaCode = prefs.getString("selected_area_code", "130000") ?: "130000"

        val warningResult = repository.getWarning(areaCode)
        
        warningResult.fold(
            onSuccess = { response ->
                val headline = response.headline
                if (!headline.isNullOrBlank()) {
                    showWarningNotification(headline)
                }
            },
            onFailure = {
                // If network is offline, retry later or fail gracefully.
                // We return Result.retry() to let WorkManager run it again based on backoff policy.
                return Result.retry()
            }
        )

        return Result.success()
    }

    private fun showWarningNotification(headlineText: String) {
        val channelId = "disaster_warning_channel"
        val channelName = "気象警報・防災情報"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O (API 26) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "気象庁から警報や注意報が発表された際に通知します。"
            }
            manager.createNotificationChannel(channel)
        }

        // Build notification using built-in alert icon
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("【気象・防災警報】")
            .setContentText(headlineText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(headlineText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(1001, notification)
    }
}
