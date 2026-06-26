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
 * DeepSeek Dashboard Widget
 *
 * Layout:
 *   ┌────────────────────────────────┐
 *   │ DeepSeek              09:21    │ ← top section: tap to refresh
 *   │ ¥82.35                         │
 *   │ Today ¥2.31    Month ¥68.24    │
 *   ├────────────────────────────────┤
 *   │ Flash ›                        │ ← bottom section: tap to switch model
 *   │ 4.82M    91.3%    ¥2.31        │
 *   │ Token    Cache    Cost         │
 *   └────────────────────────────────┘
 */
class DeepSeekWidget : AppWidgetProvider() {

    companion object {
        const val PREFS_NAME = "deepseek_widget_prefs"
        const val KEY_API_KEY = "***"
        const val ACTION_REFRESH = "com.tiramisu.deepseekwidget.ACTION_REFRESH"
        const val ACTION_CYCLE_MODEL = "com.tiramisu.deepseekwidget.ACTION_CYCLE_MODEL"
        const val UPDATE_INTERVAL_MINUTES = 30L

        private const val DISPLAY_PREFS = "deepseek_widget_display"
        private const val KEY_CACHED = "cached_text"
        private const val KEY_HAS_CACHE = "has_cache"
        private const val KEY_CURRENT_MODEL = "current_model" // 0=flash, 1=pro

        private fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(DISPLAY_PREFS, Context.MODE_PRIVATE)

        // ─── Model toggle state ─────────────────────────────────

        private fun getCurrentModel(context: Context): Int =
            prefs(context).getInt(KEY_CURRENT_MODEL, 0)

        private fun cycleModel(context: Context) {
            val cur = getCurrentModel(context)
            prefs(context).edit().putInt(KEY_CURRENT_MODEL, if (cur == 0) 1 else 0).apply()
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

        // ─── Account auth ──────────────────────────────────────

        fun hasAccountCredentials(context: Context): Boolean =
            DeepSeekAccountManager(context).hasCredentials()

        fun getTokenFromAccount(context: Context): String? =
            DeepSeekAccountManager(context).getValidToken()

        fun getAuthToken(context: Context): String? =
            getTokenFromAccount(context) ?: getApiKey(context)

        // ─── Triggers ──────────────────────────────────────────

        fun triggerRefresh(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            )
        }

        // ─── Render ────────────────────────────────────────────

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
                    renderTop(context, views, data)
                    renderBottom(context, views, data)
                }

                // Top click → refresh
                val refreshIntent = Intent(context, DeepSeekWidget::class.java).apply { action = ACTION_REFRESH }
                val refreshPi = PendingIntent.getBroadcast(context, 0, refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.top_section, refreshPi)

                // Bottom click → cycle model
                val cycleIntent = Intent(context, DeepSeekWidget::class.java).apply { action = ACTION_CYCLE_MODEL }
                val cyclePi = PendingIntent.getBroadcast(context, 1, cycleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.bottom_section, cyclePi)

                mgr.updateAppWidget(id, views)
            }
        }

        private fun renderError(views: RemoteViews, error: String) {
            views.setTextViewText(R.id.tv_title, "DeepSeek ⚠️")
            views.setTextViewText(R.id.tv_balance, error)
        }

        private fun renderTop(context: Context, views: RemoteViews, data: WidgetDisplayData) {
            views.setTextViewText(R.id.tv_title, "DeepSeek")
            views.setTextViewText(R.id.tv_refresh_time, data.formattedUpdatedTime)
            views.setTextViewText(R.id.tv_balance, data.formattedBalance)
            views.setTextViewText(R.id.tv_today, "Today ${data.formattedTodayCost}")
            views.setTextViewText(R.id.tv_month, "Month ${data.formattedMonthCost}")
        }

        private fun renderBottom(context: Context, views: RemoteViews, data: WidgetDisplayData) {
            val isFlash = getCurrentModel(context) == 0
            val model = if (isFlash) data.flashData else data.proData
            val label = if (isFlash) "Flash ›" else "Pro ›"

            views.setTextViewText(R.id.tv_model_label, label)
            views.setTextViewText(R.id.tv_token, WidgetDisplayData.formatTokenCount(model.totalTokens))
            views.setTextViewText(R.id.tv_cache_rate,
                if (model.cacheHitRate == "--") "--" else "${model.cacheHitRate}%")
            views.setTextViewText(R.id.tv_model_cost,
                if (model.cost == "0.00") "¥0" else "¥${model.cost}")
        }
    }

    // ─── Lifecycle ─────────────────────────────────────────────

    override fun onUpdate(context: Context, mgr: AppWidgetManager, appWidgetIds: IntArray) {
        schedulePeriodicUpdate(context)
        val hasAuth = getAuthToken(context) != null

        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            if (hasAuth && prefs(context).getBoolean(KEY_HAS_CACHE, false)) {
                views.setTextViewText(R.id.tv_title, "DeepSeek")
                views.setTextViewText(R.id.tv_balance,
                    prefs(context).getString(KEY_CACHED, "⟳ 刷新中...") ?: "⟳ 刷新中...")
            } else {
                views.setTextViewText(R.id.tv_title, if (hasAuth) "DeepSeek" else "DeepSeek ⚙️")
                views.setTextViewText(R.id.tv_balance, "⟳ 刷新中...")
            }

            val refreshPi = PendingIntent.getBroadcast(context, 0,
                Intent(context, DeepSeekWidget::class.java).apply { action = ACTION_REFRESH },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.top_section, refreshPi)

            val cyclePi = PendingIntent.getBroadcast(context, 1,
                Intent(context, DeepSeekWidget::class.java).apply { action = ACTION_CYCLE_MODEL },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.bottom_section, cyclePi)

            mgr.updateAppWidget(id, views)
        }

        if (hasAuth) triggerRefresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_CYCLE_MODEL) {
            cycleModel(context)

            // Re-render with cached data by triggering refresh
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, DeepSeekWidget::class.java))
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_layout)
                views.setTextViewText(R.id.tv_title, "DeepSeek")
                views.setTextViewText(R.id.tv_balance,
                    prefs(context).getString(KEY_CACHED, "") ?: "")

                val refreshPi = PendingIntent.getBroadcast(context, 0,
                    Intent(context, DeepSeekWidget::class.java).apply { action = ACTION_REFRESH },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.top_section, refreshPi)

                val cyclePi = PendingIntent.getBroadcast(context, 1,
                    Intent(context, DeepSeekWidget::class.java).apply { action = ACTION_CYCLE_MODEL },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.bottom_section, cyclePi)

                mgr.updateAppWidget(id, views)
            }

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
