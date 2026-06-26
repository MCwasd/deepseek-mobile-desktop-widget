package com.tiramisu.deepseekwidget

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data models for DeepSeek API responses.
 */

// --- Balance API (/user/balance) ---

data class BalanceResponse(
    val is_available: Boolean = false,
    val balance_infos: List<BalanceInfo>? = null
)

data class BalanceInfo(
    val currency: String = "CNY",
    val total_balance: String = "0.00",
    val granted_balance: String = "0.00",
    val topped_up_balance: String = "0.00"
)

// --- Usage Summary API (/api/v0/users/get_user_summary) ---

data class UsageSummaryResponse(
    val code: Int = -1,
    val msg: String = "",
    val data: UsageData? = null
)

data class UsageData(
    val costs: String = "0",              // Total cost this month (cents)
    val request_counts: String = "0",      // Total requests this month
    val today_costs: String = "0",         // Today's cost (cents)
    val today_request_counts: String = "0", // Today's request count
    val input_tokens: String = "0",        // Today input tokens
    val output_tokens: String = "0",       // Today output tokens
    val cache_input_tokens: String = "0",  // Cached input tokens today
    val month_input_tokens: String = "0",  // Monthly input tokens
    val month_output_tokens: String = "0", // Monthly output tokens
    val month_costs: String = "0"          // Monthly cost (cents)
)

// --- Widget Display Data ---

data class WidgetDisplayData(
    val isAvailable: Boolean = false,
    val todayCost: String = "0.00",
    val todayInputTokens: Long = 0,
    val todayOutputTokens: Long = 0,
    val todayCacheTokens: Long = 0,
    val cacheHitRate: String = "--",
    val todayRequests: Long = 0,
    val monthlyCost: String = "0.00",
    val monthlyTokens: Long = 0,
    val updatedAt: Long = 0L,
    val error: String? = null
) {
    val formattedTodayCost: String
        get() {
            if (todayCost == "0.00") return "¥0"
            return "¥$todayCost"
        }

    val formattedInputTokens: String
        get() = formatTokenCount(todayInputTokens)

    val formattedOutputTokens: String
        get() = formatTokenCount(todayOutputTokens)

    val formattedCacheTokens: String
        get() = formatTokenCount(todayCacheTokens)

    val formattedCacheHitRate: String
        get() = if (cacheHitRate == "--") "--" else "$cacheHitRate%"

    val formattedMonthCost: String
        get() {
            if (monthlyCost == "0.00") return "¥0"
            return "¥$monthlyCost"
        }

    val formattedMonthTokens: String
        get() = formatTokenCount(monthlyTokens)

    val formattedUpdatedTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            return sdf.format(Date(updatedAt))
        }

    companion object {
        fun formatTokenCount(count: Long): String {
            return when {
                count >= 1_000_000 -> "%.1fM".format(count / 1_000_000.0)
                count >= 1_000 -> "%.1fK".format(count / 1_000.0)
                else -> count.toString()
            }
        }
    }
}
