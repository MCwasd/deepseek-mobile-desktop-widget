package com.tiramisu.deepseekwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.RemoteViews
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Main widget provider for DeepSeek Dashboard.
 * Displays balance, daily token usage, cost, and cache hit rate.
 */
class DeepSeekWidget : AppWidgetProvider() {

    companion object {
        const val PREFS_NAME = "deepseek_widget_prefs"
        const val KEY_API_KEY = "api_key"
        const val ACTION_REFRESH = "com.tiramisu.deepseekwidget.ACTION_REFRESH"
        const val UPDATE_INTERVAL_MINUTES = 30L

        // Plain SharedPreferences for caching widget display text
        private const val DISPLAY_PREFS = "deepseek_widget_display"
        const val KEY_HAS_DATA = "has_cached_data"
        private const val KEY_CACHED_BALANCE = "cached_balance"
        private const val KEY_CACHED_TODAY = "cached_today"
        private const val KEY_CACHED_TIME = "cached_time"
        private const val KEY_CACHED_INPUT = "cached_input"
        private const val KEY_CACHED_OUTPUT = "cached_output"
        private const val KEY_CACHED_CACHE = "cached_cache"

        private fun getDisplayPrefs(context: Context): SharedPreferences {
            return context.getSharedPreferences(DISPLAY_PREFS, Context.MODE_PRIVATE)
        }

        /**
         * Read the stored API key.
         */
        fun getApiKey(context: Context): String? {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val prefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                prefs.getString(KEY_API_KEY, null)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Store the API key securely.
         */
        fun setApiKey(context: Context, apiKey: String) {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            prefs.edit().putString(KEY_API_KEY, apiKey).apply()
        }

        /**
         * Set up click-to-refresh on the widget container.
         */
        fun setupClickRefresh(context: Context, views: RemoteViews) {
            val refreshIntent = Intent(context, DeepSeekWidget::class.java).apply {
                action = ACTION_REFRESH
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
        }

        /**
         * Trigger an immediate one-time worker run.
         */
        fun triggerImmediateUpdate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }

        /**
         * Check if the widget has ever loaded data successfully.
         */
        fun hasCachedData(context: Context): Boolean {
            return getDisplayPrefs(context).getBoolean(KEY_HAS_DATA, false)
        }

        /**
         * Update all widget instances with fresh data and cache it.
         */
        fun updateWidgets(context: Context, data: WidgetDisplayData) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, DeepSeekWidget::class.java)
            )

            // Save to display cache
            val editor = getDisplayPrefs(context).edit()
            if (data.error != null) {
                editor.putString(KEY_CACHED_BALANCE, "⚠️ " + (data.error ?: "未知错误"))
                editor.putString(KEY_CACHED_TODAY, "")
                editor.putString(KEY_CACHED_TIME, "")
                editor.putString(KEY_CACHED_INPUT, "")
                editor.putString(KEY_CACHED_OUTPUT, "")
                editor.putString(KEY_CACHED_CACHE, "")
            } else {
                editor.putString(KEY_CACHED_BALANCE, data.formattedBalance)
                editor.putString(KEY_CACHED_TODAY, "📊 今日 " + data.todayCost)
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(Date(data.updatedAt))
                editor.putString(KEY_CACHED_TIME, "🕐 " + timeStr)
                editor.putString(KEY_CACHED_INPUT, "📝 输入 " + data.formattedInputTokens)
                editor.putString(KEY_CACHED_OUTPUT, "· 输出 " + data.formattedOutputTokens)
                editor.putString(KEY_CACHED_CACHE, "💾 缓存 " + data.formattedCacheHitRate)
            }
            editor.putBoolean(KEY_HAS_DATA, true)
            editor.apply()

            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_layout)
                applyCachedData(context, views)
                setupClickRefresh(context, views)
                manager.updateAppWidget(id, views)
            }
        }

        /**
         * Apply cached display data to the RemoteViews.
         */
        private fun applyCachedData(context: Context, views: RemoteViews) {
            val prefs = getDisplayPrefs(context)
            views.setTextViewText(R.id.tv_balance, prefs.getString(KEY_CACHED_BALANCE, "¥0.00") ?: "¥0.00")

            val todayText = prefs.getString(KEY_CACHED_TODAY, "")
            if (!todayText.isNullOrBlank()) {
                views.setTextViewText(R.id.tv_today_cost, todayText)
            }

            val timeText = prefs.getString(KEY_CACHED_TIME, "")
            if (!timeText.isNullOrBlank()) {
                views.setTextViewText(R.id.tv_updated, timeText)
            }

            val inputText = prefs.getString(KEY_CACHED_INPUT, "")
            if (!inputText.isNullOrBlank()) {
                views.setTextViewText(R.id.tv_input_tokens, inputText)
            }

            val outputText = prefs.getString(KEY_CACHED_OUTPUT, "")
            if (!outputText.isNullOrBlank()) {
                views.setTextViewText(R.id.tv_output_tokens, outputText)
            }

            val cacheText = prefs.getString(KEY_CACHED_CACHE, "")
            if (!cacheText.isNullOrBlank()) {
                views.setTextViewText(R.id.tv_cache_rate, cacheText)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        schedulePeriodicUpdate(context)
        val apiKey = getApiKey(context)

        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            if (apiKey.isNullOrBlank()) {
                // No API key configured — guide user
                views.setTextViewText(R.id.tv_balance, "Hello Widget!")
            } else if (hasCachedData(context)) {
                // Data was loaded before — restore cache immediately, no flicker
                applyCachedData(context, views)
            } else {
                // First time, no data yet — show loading
                views.setTextViewText(R.id.tv_balance, "⟳ 刷新中...")
            }

            setupClickRefresh(context, views)
            appWidgetManager.updateAppWidget(id, views)
        }

        // Always trigger worker for fresh data
        if (!apiKey.isNullOrBlank()) {
            triggerImmediateUpdate(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val apiKey = getApiKey(context)
            if (!apiKey.isNullOrBlank()) {
                // Show loading immediately, then trigger worker
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(
                    ComponentName(context, DeepSeekWidget::class.java)
                )
                for (id in ids) {
                    val views = RemoteViews(context.packageName, R.layout.widget_layout)
                    views.setTextViewText(R.id.tv_balance, "⟳ 刷新中...")
                    setupClickRefresh(context, views)
                    manager.updateAppWidget(id, views)
                }
                triggerImmediateUpdate(context)
            }
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        schedulePeriodicUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManager.getInstance(context).cancelUniqueWork(WIDGET_UPDATE_WORK_NAME)
    }

    private fun schedulePeriodicUpdate(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            UPDATE_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10000L,
                TimeUnit.MILLISECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WIDGET_UPDATE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

// WorkManager work name — needs to be accessible from the worker
const val WIDGET_UPDATE_WORK_NAME = "deepseek_widget_update"
