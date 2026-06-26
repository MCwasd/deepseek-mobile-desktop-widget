package com.tiramisu.deepseekwidget

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data models for DeepSeek API responses.
 */

// ─── Widget Display Data ─────────────────────────────────────

data class WidgetDisplayData(
    val isAvailable: Boolean = false,
    val balance: String = "0.00",
    val totalAvailableTokens: Long = 0,
    val todayCost: String = "0.00",
    val todayInputTokens: Long = 0,
    val todayOutputTokens: Long = 0,
    val todayCacheHitTokens: Long = 0,
    val todayCacheMissTokens: Long = 0,
    val todayRequests: Long = 0,
    val monthlyCost: String = "0.00",
    val monthlyTokens: Long = 0,
    val updatedAt: Long = 0L,
    val error: String? = null
) {
    val formattedBalance: String
        get() = "¥$balance"

    val formattedTodayCost: String
        get() = if (todayCost == "0.00") "¥0" else "¥$todayCost"

    val formattedInputTokens: String
        get() = formatTokenCount(todayInputTokens)

    val formattedOutputTokens: String
        get() = formatTokenCount(todayOutputTokens)

    val formattedCacheHitTokens: String
        get() = formatTokenCount(todayCacheHitTokens)

    val formattedCacheMissTokens: String
        get() = formatTokenCount(todayCacheMissTokens)

    val formattedMonthCost: String
        get() = if (monthlyCost == "0.00") "¥0" else "¥$monthlyCost"

    val formattedMonthTokens: String
        get() = formatTokenCount(monthlyTokens)

    val formattedAvailableTokens: String
        get() = formatTokenCount(totalAvailableTokens)

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
