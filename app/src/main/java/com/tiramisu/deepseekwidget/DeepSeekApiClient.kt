package com.tiramisu.deepseekwidget

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * HTTP client for fetching data from DeepSeek Platform APIs.
 * Uses Bearer token from login (not API Key).
 *
 * The phone talks to DeepSeek servers directly — no proxy needed.
 *
 * Endpoints:
 *   GET /api/v0/users/get_user_summary  — today/month stats
 *   GET /api/v0/usage/cost              — daily cost breakdown
 *   GET /api/v0/usage/amount            — token usage breakdown
 *   GET /api/v0/users/current           — account info (optional)
 */
class DeepSeekApiClient(private val token: String) {

    companion object {
        private const val PLATFORM_BASE = "https://platform.deepseek.com"
        private const val USAGE_SUMMARY_URL = "$PLATFORM_BASE/api/v0/users/get_user_summary"
        private const val USAGE_COST_URL = "$PLATFORM_BASE/api/v0/usage/cost"
        private const val USAGE_AMOUNT_URL = "$PLATFORM_BASE/api/v0/usage/amount"
        private const val USER_CURRENT_URL = "$PLATFORM_BASE/api/v0/users/current"
        private const val TIMEOUT_SECONDS = 15L
    }

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch all widget data by combining multiple platform API calls.
     * Not a suspend function — can be called from any thread.
     */
    fun fetchAll(): WidgetDisplayData {
        try {
            val summary = fetchUsageSummary()
            val now = System.currentTimeMillis()

            val data = WidgetDisplayData(
                isAvailable = true,
                todayCost = summary.todayCost,
                todayInputTokens = summary.inputTokens,
                todayOutputTokens = summary.outputTokens,
                todayCacheTokens = summary.cacheTokens,
                cacheHitRate = summary.cacheHitRate,
                todayRequests = summary.todayRequests,
                monthlyCost = summary.monthlyCost,
                monthlyTokens = summary.monthlyTokens,
                updatedAt = now
            )
            Log.d("DS_WIDGET_DATA", data.toString())
            return data
        } catch (e: Exception) {
            Log.e("DS_API", "fetchAll failed", e)
            return WidgetDisplayData(error = e.message ?: "未知错误")
        }
    }

    // ─── Usage Summary ────────────────────────────────────────

    private data class UsageSummary(
        val todayCost: String = "0.00",
        val inputTokens: Long = 0,
        val outputTokens: Long = 0,
        val cacheTokens: Long = 0,
        val cacheHitRate: String = "--",
        val todayRequests: Long = 0,
        val monthlyCost: String = "0.00",
        val monthlyTokens: Long = 0
    )

    private fun fetchUsageSummary(): UsageSummary {
        val request = Request.Builder()
            .url(USAGE_SUMMARY_URL)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw when (response.code) {
                401 -> Exception("登录已过期，请重新登录")
                else -> Exception("用量接口返回 ${response.code}")
            }
        }

        val body = response.body?.string() ?: throw Exception("用量接口响应为空")
        Log.d("DS_API_SUMMARY", body)
        val summary = gson.fromJson(body, UsageSummaryResponse::class.java)

        if (summary.code != 0 || summary.data == null) {
            throw Exception("用量数据不可用: ${summary.msg}")
        }

        val data = summary.data

        // Convert from cents (cents in API, yuan in display)
        val todayCents = data.today_costs.toLongOrNull() ?: 0L
        val monthCents = data.month_costs.toLongOrNull() ?: 0L

        val inputTokens = data.input_tokens.toLongOrNull() ?: 0L
        val outputTokens = data.output_tokens.toLongOrNull() ?: 0L
        val cacheTokens = data.cache_input_tokens.toLongOrNull() ?: 0L

        // Calculate cache hit rate
        val totalInput = inputTokens + cacheTokens
        val hitRate = if (totalInput > 0) {
            "%.1f".format((cacheTokens.toDouble() / totalInput) * 100)
        } else {
            "--"
        }

        val monthlyInput = data.month_input_tokens.toLongOrNull() ?: 0L
        val monthlyOutput = data.month_output_tokens.toLongOrNull() ?: 0L
        val todayRequests = data.today_request_counts.toLongOrNull() ?: 0L

        return UsageSummary(
            todayCost = "%.2f".format(todayCents / 100.0),
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cacheTokens = cacheTokens,
            cacheHitRate = hitRate,
            todayRequests = todayRequests,
            monthlyCost = "%.2f".format(monthCents / 100.0),
            monthlyTokens = monthlyInput + monthlyOutput
        )
    }
}
