package com.tiramisu.deepseekwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
         * Update all widget instances with fresh data and set up click-to-refresh.
         */
        fun updateWidgets(context: Context, data: WidgetDisplayData) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, DeepSeekWidget::class.java)
            )

            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_layout)
                populateViews(context, views, data)

                // Set up click-to-refresh
                val refreshIntent = Intent(context, DeepSeekWidget::class.java).apply {
                    action = ACTION_REFRESH
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                }
                val refreshPI = PendingIntent.getBroadcast(
                    context, id, refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_container, refreshPI)

                manager.updateAppWidget(id, views)
            }
        }

        /**
         * Populate RemoteViews with widget data.
         */
        private fun populateViews(context: Context, views: RemoteViews, data: WidgetDisplayData) {
            if (data.error != null) {
                // Show error state
                views.setTextViewText(R.id.tv_balance, "⚠️")
                views.setTextViewText(R.id.tv_today_cost, "刷新失败")
                views.setTextViewText(R.id.tv_input_tokens, "")
                views.setTextViewText(R.id.tv_output_tokens, "")
                views.setTextViewText(R.id.tv_cache_rate, data.error.take(30))
                views.setTextViewText(R.id.tv_updated, "")
                views.setViewVisibility(R.id.widget_divider, android.view.View.INVISIBLE)
                return
            }

            // Balance
            views.setTextViewText(R.id.tv_balance, data.formattedBalance)

            // Today cost
            views.setTextViewText(R.id.tv_today_cost, "📊 今日 ${data.formattedTodayCost}")
            views.setViewVisibility(R.id.widget_divider, android.view.View.VISIBLE)

            // Token counts
            if (data.todayInputTokens > 0 || data.todayOutputTokens > 0) {
                views.setTextViewText(
                    R.id.tv_input_tokens,
                    "📝 输入 ${data.formattedInputTokens}"
                )
                views.setTextViewText(
                    R.id.tv_output_tokens,
                    "  ·  输出 ${data.formattedOutputTokens}"
                )
            } else {
                if (data.monthlyTokens > 0) {
                    views.setTextViewText(
                        R.id.tv_input_tokens,
                        "📝 本月 ${WidgetDisplayData.formatTokenCount(data.monthlyTokens)}"
                    )
                } else {
                    views.setTextViewText(R.id.tv_input_tokens, "📝 暂无用量数据")
                }
                views.setTextViewText(R.id.tv_output_tokens, "")
            }

            // Cache hit rate
            views.setTextViewText(
                R.id.tv_cache_rate,
                "💾 缓存命中 ${data.formattedCacheHitRate}"
            )

            // Update time
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            val now = System.currentTimeMillis()
            val updated = data.updatedAt
            val timeStr = if (now - updated < 3600_000L) {
                timeFormat.format(Date(updated))
            } else {
                dateFormat.format(Date(updated))
            }
            views.setTextViewText(R.id.tv_updated, "🕐 $timeStr")
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Schedule periodic updates
        schedulePeriodicUpdate(context)

        // Show a simple message on the widget
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            views.setTextViewText(R.id.tv_balance, "Hello Widget!")
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            // Manual refresh triggered by tapping the widget
            onUpdate(
                context,
                AppWidgetManager.getInstance(context),
                AppWidgetManager.getInstance(context).getAppWidgetIds(
                    ComponentName(context, DeepSeekWidget::class.java)
                )
            )
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        schedulePeriodicUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Cancel periodic updates when last widget is removed
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
