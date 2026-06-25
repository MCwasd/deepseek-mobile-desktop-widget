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

        // 缓存上次显示文本，避免 onUpdate 写 Hello Widget 覆盖
        private const val DISPLAY_PREFS = "deepseek_widget_display"
        private const val KEY_BALANCE_CACHE = "cached_balance"
        private const val KEY_HAS_CACHE = "has_cache"

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
                context, 0, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
        }

        /**
         * Trigger an immediate one-time worker run.
         */
        fun triggerImmediateUpdate(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        /**
         * Update all widget instances with fresh data and set up click-to-refresh.
         */
        fun updateWidgets(context: Context, data: WidgetDisplayData) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, DeepSeekWidget::class.java)
            )

            // 保存到缓存
            val editor = getDisplayPrefs(context).edit()
            val displayText = if (data.error != null) {
                "⚠️ " + (data.error ?: "未知错误")
            } else {
                data.formattedBalance
            }
            editor.putString(KEY_BALANCE_CACHE, displayText)
            editor.putBoolean(KEY_HAS_CACHE, true)
            editor.apply()

            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_layout)
                views.setTextViewText(R.id.tv_balance, displayText)
                setupClickRefresh(context, views)
                manager.updateAppWidget(id, views)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        schedulePeriodicUpdate(context)

        val prefs = getDisplayPrefs(context)
        val hasCache = prefs.getBoolean(KEY_HAS_CACHE, false)
        val cachedText = prefs.getString(KEY_BALANCE_CACHE, null)
        val apiKey = getApiKey(context)

        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            if (apiKey.isNullOrBlank()) {
                // 无API Key → 引导配置
                views.setTextViewText(R.id.tv_balance, "Hello Widget!")
            } else if (hasCache && cachedText != null) {
                // 有缓存 → 恢复上次显示（不闪Hello）
                views.setTextViewText(R.id.tv_balance, cachedText)
            } else {
                // 首次启动/无缓存 → 显示刷新中
                views.setTextViewText(R.id.tv_balance, "⟳ 刷新中...")
            }

            setupClickRefresh(context, views)
            appWidgetManager.updateAppWidget(id, views)
        }

        // 有API Key时始终触发Worker刷新
        if (!apiKey.isNullOrBlank()) {
            triggerImmediateUpdate(context)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val apiKey = getApiKey(context)
            if (!apiKey.isNullOrBlank()) {
                // Show loading state immediately
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
