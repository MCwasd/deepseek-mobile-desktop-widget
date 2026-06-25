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
        const val KEY_API_KEY = "***"
        const val ACTION_REFRESH = "com.tiramisu.deepseekwidget.ACTION_REFRESH"
        const val UPDATE_INTERVAL_MINUTES = 30L

        // 缓存所有显示文本，避免 onUpdate 覆盖
        private const val DISPLAY_PREFS = "deepseek_widget_display"
        private const val KEY_HAS_CACHE = "has_cache"
        private const val KEY_BALANCE = "bal"
        private const val KEY_TODAY = "today"
        private const val KEY_TIME = "time"
        private const val KEY_INPUT = "in"
        private const val KEY_OUTPUT = "out"
        private const val KEY_CACHE = "cache"

        private fun prefs(context: Context): SharedPreferences {
            return context.getSharedPreferences(DISPLAY_PREFS, Context.MODE_PRIVATE)
        }

        private fun saveAll(context: Context, bal: String, today: String, time: String,
                            input: String, output: String, cache: String) {
            prefs(context).edit()
                .putString(KEY_BALANCE, bal)
                .putString(KEY_TODAY, today)
                .putString(KEY_TIME, time)
                .putString(KEY_INPUT, input)
                .putString(KEY_OUTPUT, output)
                .putString(KEY_CACHE, cache)
                .putBoolean(KEY_HAS_CACHE, true)
                .apply()
        }

        private fun applyCached(views: RemoteViews, ctx: Context) {
            val p = prefs(ctx)
            views.setTextViewText(R.id.tv_balance, p.getString(KEY_BALANCE, "¥0.00") ?: "¥0.00")
            val today = p.getString(KEY_TODAY, null)
            if (today != null) views.setTextViewText(R.id.tv_today_cost, today)
            val time = p.getString(KEY_TIME, null)
            if (time != null) views.setTextViewText(R.id.tv_updated, time)
            val input = p.getString(KEY_INPUT, null)
            if (input != null) views.setTextViewText(R.id.tv_input_tokens, input)
            val output = p.getString(KEY_OUTPUT, null)
            if (output != null) views.setTextViewText(R.id.tv_output_tokens, output)
            val cache = p.getString(KEY_CACHE, null)
            if (cache != null) views.setTextViewText(R.id.tv_cache_rate, cache)
        }

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

            if (data.error != null) {
                saveAll(context, "⚠️ ${data.error}", "📊 --", "🕐 --:--", "📝 --", "· 输出 --", "💾 --")
            } else {
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(data.updatedAt))
                saveAll(context,
                    data.formattedBalance,
                    "📊 ¥${data.todayCost}",
                    "🕐 $timeStr",
                    "📝 ${data.formattedInputTokens}",
                    "· 输出 ${data.formattedOutputTokens}",
                    "💾 ${data.formattedCacheHitRate}")
            }

            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_layout)
                applyCached(views, context)
                setupClickRefresh(context, views)
                mgr.updateAppWidget(id, views)
            }
        }
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, appWidgetIds: IntArray) {
        schedulePeriodicUpdate(context)
        val apiKey = getApiKey(context)

        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)

            if (apiKey.isNullOrBlank()) {
                views.setTextViewText(R.id.tv_balance, "Hello Widget!")
            } else if (prefs(context).getBoolean(KEY_HAS_CACHE, false)) {
                applyCached(views, context)
            } else {
                views.setTextViewText(R.id.tv_balance, "⟳ 刷新中...")
            }

            setupClickRefresh(context, views)
            mgr.updateAppWidget(id, views)
        }

        if (!apiKey.isNullOrBlank()) triggerImmediateUpdate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_REFRESH) return
        val apiKey = getApiKey(context)
        if (apiKey.isNullOrBlank()) return

        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, DeepSeekWidget::class.java))
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            applyCached(views, context) // 保留其他数据
            views.setTextViewText(R.id.tv_balance, "⟳ 刷新中...") // 仅余额变加载
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
            WIDGET_UPDATE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetUpdateWorker>(UPDATE_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10000L, TimeUnit.MILLISECONDS)
                .build()
        )
    }
}

const val WIDGET_UPDATE_WORK_NAME = "deepseek_widget_update"
