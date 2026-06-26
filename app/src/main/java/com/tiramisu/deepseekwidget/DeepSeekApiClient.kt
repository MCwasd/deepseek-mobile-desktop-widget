package com.tiramisu.deepseekwidget

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * HTTP client for fetching data from DeepSeek Platform APIs.
 * Uses Bearer token from login (not API Key).
 */
class DeepSeekApiClient(private val token: String) {

    companion object {
        private const val PLATFORM_BASE = "https://platform.deepseek.com"
        private const val SUMMARY_URL = "$PLATFORM_BASE/api/v0/users/get_user_summary"
        private const val COST_URL = "$PLATFORM_BASE/api/v0/usage/cost"
        private const val AMOUNT_URL = "$PLATFORM_BASE/api/v0/usage/amount"
        private const val TIMEOUT_SECONDS = 15L
    }

    private val gson = Gson()
    private val cal = Calendar.getInstance()

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private fun buildRequest(url: String): Request = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer $token")
        .header("Accept", "application/json")
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
        .header("x-client-platform", "web")
        .header("x-client-version", "1.0.0")
        .header("x-app-version", "1.0.0")
        .build()

    // ─── Main entry ────────────────────────────────────────────

    fun fetchAll(): WidgetDisplayData {
        try {
            val summary = fetchSummary()
            val today = fetchTodayUsage()
            Log.d("DS_WIDGET", "summary=$summary, today=$today")
            return WidgetDisplayData(
                isAvailable = true,
                balance = summary.balance,
                totalAvailableTokens = summary.totalAvailableTokens,
                todayCost = today.cost,
                todayInputTokens = today.inputTokens,
                todayOutputTokens = today.outputTokens,
                todayCacheHitTokens = today.cacheHitTokens,
                todayCacheMissTokens = today.cacheMissTokens,
                cacheHitRate = today.cacheHitRate,
                monthlyCost = summary.monthlyCost,
                monthlyTokens = summary.monthlyTokens,
                updatedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e("DS_API", "fetchAll failed", e)
            return WidgetDisplayData(error = e.message ?: "未知错误")
        }
    }

    // ─── get_user_summary (余额 + 月统计) ────────────────────

    private data class SummaryResult(
        val balance: String = "0.00",
        val totalAvailableTokens: Long = 0,
        val monthlyCost: String = "0.00",
        val monthlyTokens: Long = 0
    )

    private fun fetchSummary(): SummaryResult {
        val body = execute(SUMMARY_URL)
        Log.d("DS_SUMMARY", body)
        val resp = gson.fromJson(body, SummaryResponse::class.java)
        val biz = resp.data?.bizData ?: throw Exception("summary 数据为空: $body")

        // Parse primary wallet balance
        val balance = biz.normalWallets?.firstOrNull()?.let {
            val b = it.balance?.toDoubleOrNull() ?: 0.0
            "%.2f".format(b)
        } ?: "0.00"

        val totalAvailableTokens = biz.totalAvailableTokenEstimation?.toLongOrNull() ?: 0L

        // Parse monthly cost
        val monthlyCost = biz.monthlyCosts?.firstOrNull()?.let {
            val a = it.amount?.toDoubleOrNull() ?: 0.0
            "%.2f".format(a)
        } ?: "0.00"

        val monthlyTokens = biz.monthlyTokenUsage?.toLongOrNull() ?: 0L

        return SummaryResult(balance, totalAvailableTokens, monthlyCost, monthlyTokens)
    }

    // ─── usage/amount & usage/cost (今日明细) ─────────────────

    private data class TodayUsage(
        val cost: String = "0.00",
        val inputTokens: Long = 0,
        val outputTokens: Long = 0,
        val cacheHitTokens: Long = 0,
        val cacheMissTokens: Long = 0,
        val cacheHitRate: String = "--"
    )

    private fun fetchTodayUsage(): TodayUsage {
        val month = String.format("%02d", cal.get(Calendar.MONTH) + 1)
        val year = cal.get(Calendar.YEAR).toString()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        var costStr = "0.00"
        var inputTk = 0L
        var outputTk = 0L
        var cacheHit = 0L
        var cacheMiss = 0L

        // --- usage/amount (token counts) ---
        try {
            val amountBody = execute("$AMOUNT_URL?month=$month&year=$year")
            Log.d("DS_AMOUNT", amountBody)
            val amountResp = gson.fromJson(amountBody, UsageAmountResponse::class.java)
            val days = amountResp.data?.bizData?.days
            if (days != null) {
                val today = days.find { it.date == todayStr }
                if (today != null) {
                    for (modelData in today.data ?: emptyList()) {
                        // Only deepseek-v4-flash model
                        if (modelData.model != "deepseek-v4-flash") continue
                        for (u in modelData.usage ?: emptyList()) {
                            // Amount is in millions of tokens (e.g. 0.06 = 60K tokens)
                            val amt = u.amount?.toLongOrNull() ?: 0L
                            when (u.type) {
                                "PROMPT_TOKEN" -> inputTk += amt
                                "PROMPT_CACHE_HIT_TOKEN" -> cacheHit += amt
                                "PROMPT_CACHE_MISS_TOKEN" -> cacheMiss += amt
                                "RESPONSE_TOKEN" -> outputTk += amt
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("DS_AMOUNT", "amount API failed", e)
        }

        // --- usage/cost (cost in CNY) ---
        try {
            val costBody = execute("$COST_URL?month=$month&year=$year")
            Log.d("DS_COST", costBody)
            val costResp = gson.fromJson(costBody, UsageCostResponse::class.java)
            val bizData = costResp.data?.bizData
            if (bizData != null && bizData.isNotEmpty()) {
                // Find today's entry
                for (entry in bizData) {
                    val todayDay = entry.days?.find { it.date == todayStr }
                    if (todayDay != null) {
                        var todayCostTotal = 0.0
                        for (modelData in todayDay.data ?: emptyList()) {
                            if (modelData.model != "deepseek-v4-flash") continue
                            for (u in modelData.usage ?: emptyList()) {
                                // Cost amounts are already in CNY
                                if (u.type == "PROMPT_CACHE_MISS_TOKEN" ||
                                    u.type == "RESPONSE_TOKEN") {
                                    todayCostTotal += u.amount?.toDoubleOrNull() ?: 0.0
                                }
                            }
                        }
                        costStr = "%.2f".format(todayCostTotal)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("DS_COST", "cost API failed", e)
        }

        // Calculate cache hit rate
        val totalCache = cacheHit + cacheMiss
        val hitRate = if (totalCache > 0) {
            "%.1f".format((cacheHit.toDouble() / totalCache) * 100)
        } else {
            "--"
        }

        return TodayUsage(costStr, inputTk, outputTk, cacheHit, cacheMiss, hitRate)
    }

    // ─── HTTP helper ──────────────────────────────────────────

    private fun execute(url: String): String {
        val request = buildRequest(url)
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw when (response.code) {
                401 -> Exception("登录已过期，请重新登录")
                else -> Exception("API ${response.code}: ${response.body?.string() ?: ""}")
            }
        }
        return response.body?.string() ?: throw Exception("空响应")
    }
}

// ═══════════════════════════════════════════════════════════════
//  Response DTOs
// ═══════════════════════════════════════════════════════════════

// ── get_user_summary ───────────────────────────────────────────

data class SummaryResponse(
    val code: Int = -1,
    val msg: String = "",
    val data: SummaryData? = null
)

data class SummaryData(
    @SerializedName("biz_code") val bizCode: Int = -1,
    @SerializedName("biz_msg") val bizMsg: String = "",
    @SerializedName("biz_data") val bizData: SummaryBizData? = null
)

data class SummaryBizData(
    @SerializedName("current_token") val currentToken: Long? = null,
    @SerializedName("monthly_usage") val monthlyUsage: String? = null,
    @SerializedName("total_usage") val totalUsage: Long? = null,
    @SerializedName("normal_wallets") val normalWallets: List<Wallet>? = null,
    @SerializedName("bonus_wallets") val bonusWallets: List<Wallet>? = null,
    @SerializedName("total_available_token_estimation") val totalAvailableTokenEstimation: String? = null,
    @SerializedName("monthly_costs") val monthlyCosts: List<MonthlyCost>? = null,
    @SerializedName("monthly_token_usage") val monthlyTokenUsage: String? = null
)

data class Wallet(
    val currency: String? = null,
    val balance: String? = null,
    @SerializedName("token_estimation") val tokenEstimation: String? = null
)

data class MonthlyCost(
    val currency: String? = null,
    val amount: String? = null
)

// ── usage/amount ────────────────────────────────────────────────

data class UsageAmountResponse(
    val code: Int = -1,
    val msg: String = "",
    val data: UsageAmountData? = null
)

data class UsageAmountData(
    @SerializedName("biz_code") val bizCode: Int = -1,
    @SerializedName("biz_msg") val bizMsg: String = "",
    @SerializedName("biz_data") val bizData: UsageAmountBizData? = null
)

data class UsageAmountBizData(
    val total: List<ModelUsageList>? = null,
    val days: List<DayUsage>? = null
)

data class DayUsage(
    val date: String? = null,
    val data: List<ModelUsageList>? = null
)

data class ModelUsageList(
    val model: String? = null,
    val usage: List<UsageEntry>? = null
)

data class UsageEntry(
    val type: String? = null,
    val amount: String? = null
)

// ── usage/cost ──────────────────────────────────────────────────

data class UsageCostResponse(
    val code: Int = -1,
    val msg: String = "",
    val data: UsageCostData? = null
)

data class UsageCostData(
    @SerializedName("biz_code") val bizCode: Int = -1,
    @SerializedName("biz_msg") val bizMsg: String = "",
    @SerializedName("biz_data") val bizData: List<CostEntry>? = null
)

data class CostEntry(
    val total: List<ModelUsageList>? = null,
    val days: List<DayUsage>? = null,
    val currency: String? = null
)
