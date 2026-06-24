package com.tiramisu.deepseekwidget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Configuration activity shown when adding the widget to the desktop.
 *
 * The user enters their DeepSeek API Key, which is stored securely
 * using EncryptedSharedPreferences (AES-256-GCM).
 */
class DeepSeekWidgetConfig : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.config_layout)

        // Get the widget ID from the intent
        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // If invalid, finish immediately
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Set result to cancelled by default (user must save to confirm)
        setResult(RESULT_CANCELED)

        val etApiKey = findViewById<EditText>(R.id.et_api_key)
        val btnSave = findViewById<Button>(R.id.btn_save)
        val btnCancel = findViewById<Button>(R.id.btn_cancel)
        val tvStatus = findViewById<TextView>(R.id.tv_config_status)

        // Pre-fill if already configured (re-configuring)
        val existingKey = DeepSeekWidget.getApiKey(this)
        if (!existingKey.isNullOrBlank()) {
            etApiKey.setText(existingKey)
        }

        btnSave.setOnClickListener {
            val apiKey = etApiKey.text.toString().trim()

            if (apiKey.isBlank()) {
                Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!apiKey.startsWith("sk-")) {
                Toast.makeText(this, "API Key 格式错误，应以 sk- 开头", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show loading
            tvStatus.text = "验证中..."
            tvStatus.visibility = View.VISIBLE
            btnSave.isEnabled = false

            // Verify the API key by making a test request
            saveAndConfigure(apiKey, tvStatus, btnSave)
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun saveAndConfigure(
        apiKey: String,
        tvStatus: TextView,
        btnSave: Button
    ) {
        // Run the verification in background
        Thread {
            try {
                val client = DeepSeekApiClient(apiKey)
                val data = client.fetchAll()

                runOnUiThread {
                    if (data.error == null) {
                        // Save the API key
                        DeepSeekWidget.setApiKey(this, apiKey)

                        // Show initial data on widget
                        DeepSeekWidget.updateWidgets(this, data)

                        // Schedule periodic updates
                        scheduleWork()

                        // Set success result so widget gets placed
                        val resultValue = Intent().apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        }
                        setResult(RESULT_OK, resultValue)

                        Toast.makeText(
                            this,
                            "验证成功！余额: ${data.formattedBalance}",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    } else {
                        tvStatus.text = "验证失败: ${data.error}"
                        btnSave.isEnabled = true
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = "验证失败: ${e.message?.take(50) ?: "连接错误"}"
                    btnSave.isEnabled = true
                }
            }
        }.start()
    }

    private fun scheduleWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            DeepSeekWidget.UPDATE_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WIDGET_UPDATE_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
    }
}
