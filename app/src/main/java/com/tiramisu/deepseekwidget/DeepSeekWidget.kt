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
 * Displays API account balance with click-to-refresh.
 */
class DeepSeekWidget : AppWidgetProvider() {

    companion object {
        const val PREFS_NAME = "deepseek_widget_prefs"
        const val KEY_API_KEY = "***"
        const val ACTION_REFRESH = "com.tiramisu.deepseekwidget.ACTION_REFRESH"
        const val UPDATE_INTERVAL_MINUTES = 30L

        private const val DISPLAY_PREFS = "deepseek_widget_display"
        private const val KEY_CACHED = "cached_text"
        private const val KEY_HAS_CACHE = "has_cache"

        private fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(DISPLAY_PREFS, Context.MODE_PRIVATE)

        // ─── API Key storage (legacy, kept for backward compat) ───

        fun getApiKey(context: Context): String? {
            return try {
                val mk = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
                val p = EncryptedSharedPreferences.create(context, PREFS_NAME, mk,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
                p.getString(KEY_API_KEY, null)
            } catch (_: Exception) { null }
        }

        fun setApiKey(context: Context, apiKey: String) {
            val mk = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val p = EncryptedSharedPreferences.create(context, PREFS_NAME, mk,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
            p.edit().putString(KEY_API_KEY, apiKey).apply()
        }

        // ─── Account-based auth (new) ───

        /**
         * Check whether account credentials (email+password) are configured.
         * If true, use getTokenFromAccount() instead of getApiKey().
         */
        fun hasAccountCredentials(context: Context): Boolean {
            return DeepSeekAccountManager(context).hasCredentials()
        }

        /**
         * Get a valid Bearer Token from stored account credentials.
         * Auto re-logs-in if token is missing/expired.
         * Returns null if no credentials are configured.
         */
        fun getTokenFromAccount(context: Context): String? {
            return DeepSeekAccountManager(context).getValidToken()
        }

        /**
         * Determine which auth method to use:
         * 1. Account token (preferred)
         * 2. Legacy API Key (fallback)
         * Returns null if neither is configured.
         */
        fun getAuthToken(context: Context): String? {
            return getTokenFromAccount(context) ?: getApiKey(context)
        }

        fun setupClickRefresh(context: Context, views: RemoteViews) {
            val intent = Intent(context, DeepSeekWidget::class.java).apply { action = ACTION_REFRESH }
            val pi = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_container, pi)
        }

        fun triggerImmediateUpdate(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            )
        }

        fun updateWidgets(context: Context, data: WidgetDisplayData) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, DeepSeekWidget::class.java))

            // Cache the error/status text for onUpdate restore
            val statusText = if (data.error != null) "⚠️ ${data.error}" else data.formattedTodayCost
            prefs(context).edit().putString(KEY_CACHED, statusText).putBoolean(KEY_HAS_CACHE, true).apply()

            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_layout)

                if (data.error != null) {
                    // Error state: show error on balance, hide details
                    views.setTextViewText(R.id.tv_title, "DeepSeek ⚠️")
                    views.setTextViewText(R.id.tv_balance, data.error ?: "未知错误")
                    views.setTextViewText(R.id.tv_today_cost, "")
                    views.setTextViewText(R.id.tv_requests, "")
                    views.setTextViewText(R.id.tv_updated, "")
                    views.setTextViewText(R.id.tv_input_tokens, "")
                    views.setTextViewText(R.id.tv_output_tokens, "")
                    views.setTextViewText(R.id.tv_cache_rate, "")
                } else {
                    views.setTextViewText(R.id.tv_title, "DeepSeek 📊")
                    views.setTextViewText(R.id.tv_balance, data.formattedTodayCost)
                    views.setTextViewText(R.id.tv_today_cost, "今日 ¥${data.todayCost}")
                    views.setTextViewText(R.id.tv_requests, "· 请求 ${data.todayRequests}")
                    views.setTextViewText(R.id.tv_updated, "· 🕐 ${data.formattedUpdatedTime}")
                    views.setTextViewText(R.id.tv_input_tokens, "📝 ${data.formattedInputTokens}")
                    views.setTextViewText(R.id.tv_output_tokens, "· 输出 ${data.formattedOutputTokens}")
                    views.setTextViewText(R.id.tv_cache_rate, "· 💾 ${data.formattedCacheHitRate}")
                }

                setupClickRefresh(context, views)
                mgr.updateAppWidget(id, views)
            }
        }
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, appWidgetIds: IntArray) {
        schedulePeriodicUpdate(context)
        val hasAuth = DeepSeekWidget.getAuthToken(context) != null

        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            views.setTextViewText(R.id.tv_title, if (hasAuth) "DeepSeek 📊" else "DeepSeek ⚙️")
            views.setTextViewText(R.id.tv_balance, "⟳ 刷新中...")

            if (prefs(context).getBoolean(KEY_HAS_CACHE, false)) {
                views.setTextViewText(R.id.tv_balance,
                    prefs(context).getString(KEY_CACHED, "⟳ 刷新中...") ?: "⟳ 刷新中...")
            }

            setupClickRefresh(context, views)
            mgr.updateAppWidget(id, views)
        }

        if (hasAuth) triggerImmediateUpdate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_REFRESH) return
        if (getAuthToken(context).isNullOrBlank()) return

        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, DeepSeekWidget::class.java))
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            views.setTextViewText(R.id.tv_title, "DeepSeek 📊")
            views.setTextViewText(R.id.tv_balance, "⟳ 刷新中...")
            setupClickRefresh(context, views)
            mgr.updateAppWidget(id, views)
        }
        triggerImmediateUpdate(context)
    }

    override fun onEnabled(context: Context) { super.onEnabled(context); schedulePeriodicUpdate(context) }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManager.getInstance(context).cancelUniqueWork(WIDGET_UPDATE_WORK_NAME)
    }

    private fun schedulePeriodicUpdate(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WIDGET_UPDATE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetUpdateWorker>(UPDATE_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10000L, TimeUnit.MILLISECONDS)
                .build()
        )
    }
}

const val WIDGET_UPDATE_WORK_NAME = "deepseek_widget_update"
