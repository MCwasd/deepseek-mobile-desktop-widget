package com.tiramisu.deepseekwidget

import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * HTTP client for fetching data from DeepSeek APIs directly.
 * The phone talks to DeepSeek servers directly — no proxy needed.
 */
class DeepSeekApiClient(private val apiKey: String) {

    companion object {
        private const val BALANCE_URL = "https://api.deepseek.com/user/balance"
        private const val USAGE_SUMMARY_URL =
            "https://platform.deepseek.com/api/v0/users/get_user_summary"
        private const val TIMEOUT_SECONDS = 15L
    }

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch all widget data in one call.
     * Not a suspend function — can be called from any thread.
     */
    fun fetchAll(): WidgetDisplayData {
        try {
            val balanceData = fetchBalance()
            val usageData = try {
                fetchUsageSummary()
            } catch (e: Exception) {
                null // Usage API is optional
            }

            return WidgetDisplayData(
                totalBalance = balanceData.total_balance,
                currency = balanceData.currency,
                isAvailable = true,
                todayCost = usageData?.todayCost ?: "0.00",
                todayInputTokens = usageData?.inputTokens ?: 0,
                todayOutputTokens = usageData?.outputTokens ?: 0,
                todayCacheTokens = usageData?.cacheTokens ?: 0,
                cacheHitRate = usageData?.cacheHitRate ?: "--",
                monthlyCost = usageData?.monthlyCost ?: "0.00",
                monthlyTokens = usageData?.monthlyTokens ?: 0,
                updatedAt = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            return WidgetDisplayData(
                error = e.message ?: "未知错误"
            )
        }
    }

    private fun fetchBalance(): BalanceInfo {
        val request = Request.Builder()
            .url(BALANCE_URL)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw when (response.code) {
                401 -> Exception("API Key 无效或已吊销")
                402 -> Exception("余额不足")
                429 -> Exception("请求过于频繁，请稍后再试")
                else -> Exception("服务器错误 (${response.code})")
            }
        }

        val body = response.body?.string() ?: throw Exception("响应为空")
        val balanceResponse = gson.fromJson(body, BalanceResponse::class.java)

        // Find CNY balance first, fallback to USD
        val cnyInfo = balanceResponse.balance_infos?.find { it.currency == "CNY" }
        val usdInfo = balanceResponse.balance_infos?.find { it.currency == "USD" }
        return cnyInfo ?: usdInfo ?: BalanceInfo()
    }

    private data class UsageSummary(
        val todayCost: String = "0.00",
        val inputTokens: Long = 0,
        val outputTokens: Long = 0,
        val cacheTokens: Long = 0,
        val cacheHitRate: String = "--",
        val monthlyCost: String = "0.00",
        val monthlyTokens: Long = 0
    )

    private fun fetchUsageSummary(): UsageSummary {
        val request = Request.Builder()
            .url(USAGE_SUMMARY_URL)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .header("User-Agent", "DeepSeekWidget/1.0")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("用量接口返回 ${response.code}")
        }

        val body = response.body?.string() ?: throw Exception("用量接口响应为空")
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

        return UsageSummary(
            todayCost = "%.2f".format(todayCents / 100.0),
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cacheTokens = cacheTokens,
            cacheHitRate = hitRate,
            monthlyCost = "%.2f".format(monthCents / 100.0),
            monthlyTokens = monthlyInput + monthlyOutput
        )
    }
}
