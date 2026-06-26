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
 *
 * Two view pages, tap to cycle:
 *   Page 0: Overview — balance, today/month cost, tokens
 *   Page 1: Token detail — V4 Flash prompt/output/cache breakdown
 *
 * Tap anywhere on the widget to switch pages.
 * Data auto-refreshes every 30 min via WorkManager.
 */
class DeepSeekWidget : AppWidgetProvider() {

    companion object {
        const val PREFS_NAME = "deepseek_widget_prefs"
        const val KEY_API_KEY = "***"
        const val ACTION_REFRESH = "com.tiramisu.deepseekwidget.ACTION_REFRESH"
        const val ACTION_CYCLE_VIEW = "com.tiramisu.deepseekwidget.ACTION_CYCLE_VIEW"
        const val UPDATE_INTERVAL_MINUTES = 30L

        private const val DISPLAY_PREFS = "deepseek_widget_display"
        private const val KEY_CACHED = "cached_text"
        private const val KEY_HAS_CACHE = "has_cache"
        private const val KEY_CURRENT_VIEW = "current_view"
        private const val KEY_CACHED_DATA = "cached_data_json"

        private fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(DISPLAY_PREFS, Context.MODE_PRIVATE)

        // ─── View page state ───────────────────────────────────

        fun getCurrentView(context: Context): Int =
            prefs(context).getInt(KEY_CURRENT_VIEW, 0)

        fun setCurrentView(context: Context, view: Int) {
            prefs(context).edit().putInt(KEY_CURRENT_VIEW, view).apply()
        }

        // ─── API Key storage (legacy) ──────────────────────────

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

        // ─── Account-based auth ────────────────────────────────

        fun hasAccountCredentials(context: Context): Boolean =
            DeepSeekAccountManager(context).hasCredentials()

        fun getTokenFromAccount(context: Context): String? =
            DeepSeekAccountManager(context).getValidToken()

        fun getAuthToken(context: Context): String? =
            getTokenFromAccount(context) ?: getApiKey(context)

        // ─── Click handlers ────────────────────────────────────

        fun setupClickCycle(context: Context, views: RemoteViews) {
            val cycleIntent = Intent(context, DeepSeekWidget::class.java).apply { action = ACTION_CYCLE_VIEW }
            val pi = PendingIntent.getBroadcast(context, 1, cycleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_container, pi)
        }

        fun triggerRefresh(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            )
        }

        // ─── Render widget ─────────────────────────────────────

        fun updateWidgets(context: Context, data: WidgetDisplayData) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, DeepSeekWidget::class.java))

            val cacheText = if (data.error != null) "⚠️ ${data.error}" else data.formattedBalance
            prefs(context).edit().putString(KEY_CACHED, cacheText).putBoolean(KEY_HAS_CACHE, true).apply()

            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_layout)

                if (data.error != null) {
                    renderError(views, data.error ?: "未知错误")
                } else {
                    val viewPage = getCurrentView(context)
                    if (viewPage == 0) renderOverview(views, data)
                    else renderTokenDetail(views, data)
                }

                setupClickCycle(context, views)
                mgr.updateAppWidget(id, views)
            }
        }

        private fun renderError(views: RemoteViews, error: String) {
            views.setTextViewText(R.id.tv_title, "DeepSeek ⚠️")
            views.setTextViewText(R.id.tv_balance, error)
            views.setTextViewText(R.id.tv_line2, "")
            views.setTextViewText(R.id.tv_line3, "")
            views.setTextViewText(R.id.tv_line4, "")
            views.setTextViewText(R.id.tv_page_dot, "●   ○")
        }

        /** Page 0: Overview — balance, costs, token summary */
        private fun renderOverview(views: RemoteViews, data: WidgetDisplayData) {
            views.setTextViewText(R.id.tv_title, "DeepSeek 📊  ·  🕐${data.formattedUpdatedTime}")
            views.setTextViewText(R.id.tv_balance, data.formattedBalance)
            views.setTextViewText(R.id.tv_line2, "今日 ¥${data.todayCost}    本月 ¥${data.monthlyCost}")
            views.setTextViewText(R.id.tv_line3, "Token 已用 ${data.formattedMonthTokens} · 可用 ${data.formattedAvailableTokens}")
            views.setTextViewText(R.id.tv_line4, "V4 Flash 💾 ${data.formattedCacheHitRate}")
            views.setTextViewText(R.id.tv_page_dot, "●   ○")
        }

        /** Page 1: Token detail — V4 Flash prompt/output/cache */
        private fun renderTokenDetail(views: RemoteViews, data: WidgetDisplayData) {
            views.setTextViewText(R.id.tv_title, "DeepSeek 📊  ·  🕐${data.formattedUpdatedTime}")
            views.setTextViewText(R.id.tv_balance, "V4 Flash 今日")
            views.setTextViewText(R.id.tv_line2, "📝 Prompt ${data.formattedInputTokens}  ·  输出 ${data.formattedOutputTokens}")
            views.setTextViewText(R.id.tv_line3, "💾 Hit ${data.formattedCacheHitTokens}  ·  Miss ${data.formattedCacheMissTokens}")
            views.setTextViewText(R.id.tv_line4, "命中率 ${data.formattedCacheHitRate}    今日 ¥${data.todayCost}")
            views.setTextViewText(R.id.tv_page_dot, "○   ●")
        }
    }

    // ─── Widget lifecycle ──────────────────────────────────────

    override fun onUpdate(context: Context, mgr: AppWidgetManager, appWidgetIds: IntArray) {
        schedulePeriodicUpdate(context)
        val hasAuth = getAuthToken(context) != null

        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            if (hasAuth && prefs(context).getBoolean(KEY_HAS_CACHE, false)) {
                views.setTextViewText(R.id.tv_title, "DeepSeek 📊")
                views.setTextViewText(R.id.tv_balance,
                    prefs(context).getString(KEY_CACHED, "⟳ 刷新中...") ?: "⟳ 刷新中...")
            } else {
                views.setTextViewText(R.id.tv_title, if (hasAuth) "DeepSeek 📊" else "DeepSeek ⚙️")
                views.setTextViewText(R.id.tv_balance, "⟳ 刷新中...")
            }
            views.setTextViewText(R.id.tv_page_dot, "●   ○")

            setupClickCycle(context, views)
            mgr.updateAppWidget(id, views)
        }

        if (hasAuth) triggerRefresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_CYCLE_VIEW) {
            val current = getCurrentView(context)
            val next = if (current == 0) 1 else 0
            setCurrentView(context, next)

            // Re-render with cached data
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, DeepSeekWidget::class.java))
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_layout)
                views.setTextViewText(R.id.tv_title, if (next == 0) "DeepSeek 📊" else "DeepSeek 🔄")
                views.setTextViewText(R.id.tv_balance,
                    prefs(context).getString(KEY_CACHED, "⟳ 刷新中...") ?: "⟳ 刷新中...")
                views.setTextViewText(R.id.tv_page_dot, if (next == 0) "●   ○" else "○   ●")
                setupClickCycle(context, views)
                mgr.updateAppWidget(id, views)
            }

            // Refresh data in background
            if (getAuthToken(context) != null) triggerRefresh(context)
            return
        }

        if (intent.action == ACTION_REFRESH) {
            if (getAuthToken(context).isNullOrBlank()) return
            triggerRefresh(context)
            return
        }
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
