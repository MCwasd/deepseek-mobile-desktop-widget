package com.tiramisu.deepseekwidget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * WorkManager worker for periodic widget updates (every 30 min).
 */
class WidgetUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val apiKey = DeepSeekWidget.getApiKey(applicationContext)
        if (apiKey.isNullOrBlank()) {
            return Result.success() // No API key configured yet
        }

        return try {
            val client = DeepSeekApiClient(apiKey)
            val data = client.fetchAll()
            DeepSeekWidget.updateWidgets(applicationContext, data)
            Result.success()
        } catch (e: Exception) {
            DeepSeekWidget.updateWidgets(
                applicationContext,
                WidgetDisplayData(error = e.message)
            )
            Result.retry()
        }
    }
}
