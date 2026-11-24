package com.outfit.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.widget.RemoteViews
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class OutfitWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.outfit.widget.ACTION_REFRESH"
        private val executor = Executors.newSingleThreadExecutor()

        fun triggerRefresh(context: Context) {
            val intent = Intent(context, OutfitWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        executor.execute {
            try {
                updateAllWidgets(context, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, OutfitWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                val pendingResult = goAsync()
                executor.execute {
                    try {
                        updateAllWidgets(context, manager, ids)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private fun updateAllWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val prefs = context.getSharedPreferences("outfit_prefs", Context.MODE_PRIVATE)
        val workerUrl = prefs.getString("worker_url", "")?.trimEnd('/') ?: ""

        val views = RemoteViews(context.packageName, R.layout.widget_outfit)

        val refreshIntent = Intent(context, OutfitWidgetProvider::class.java).apply { action = ACTION_REFRESH }
        val refreshPending = PendingIntent.getBroadcast(
            context, 0, refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.btnRefresh, refreshPending)

        val openIntent = if (workerUrl.startsWith("http")) {
            Intent(Intent.ACTION_VIEW, Uri.parse(workerUrl))
        } else {
            Intent(context, MainActivity::class.java)
        }
        val openPending = PendingIntent.getActivity(
            context, 1, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widgetRoot, openPending)

        if (!workerUrl.startsWith("http")) {
            views.setTextViewText(R.id.tvDate, "초기 설정 필요")
            views.setTextViewText(R.id.tvTempBig, "--°")
            views.setTextViewText(R.id.tvDailyOutfit, "주소를 입력해 주세요")
            views.setTextViewText(R.id.tvDailyMsg, "여기를 탭하면 설정 화면으로 이동합니다.")
            ids.forEach { manager.updateAppWidget(it, views) }
            return
        }

        try {
            val conn = (URL("$workerUrl/api/today").openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)

            val dateLabel = json.optString("dateLabel", "")
            val minTemp = json.optInt("minTemp", 0)
            val maxTemp = json.optInt("maxTemp", 0)
            val hasRain = json.optBoolean("hasRain", false)
            val dustGrade = json.optString("dustGrade", "보통")
            val umbrellaBadge = json.optString("umbrellaBadge", "")
            val dailyOutfit = json.optString("dailyOutfit", "")
            val dailyMsg = json.optString("dailyMsg", "")
            val bikeAllowed = json.optBoolean("bikeAllowed", true)
            val bikeOutfit = json.optString("bikeOutfit", "")
            val bikeMsg = json.optString("bikeMsg", "")
            val kidsOutfit = json.optString("kidsOutfit", "")
            val kidsMsg = json.optString("kidsMsg", "")

            // 날씨별 감성 플랫 컬러 블록 테마
            val isDustBad = dustGrade.contains("나쁨")
            val topBgHex: String
            val bottomBgHex: String
            val pillBgHex: String
            val weatherIcon: String

            when {
                hasRain -> {
                    topBgHex = "#CFE4FF"     // 비 오는 날: 부드러운 레인 블루
                    bottomBgHex = "#FFE4C4"  // 아이들 블록: 따뜻한 아프리콧
                    pillBgHex = "#0369A1"
                    weatherIcon = "☔"
                }
                isDustBad -> {
                    topBgHex = "#EFECE6"     // 미세먼지 나쁨: 웜 샌드
                    bottomBgHex = "#FDE68A"  // 아이들 블록: 웜 옐로우
                    pillBgHex = "#B91C1C"
                    weatherIcon = "😷"
                }
                maxTemp <= 5 -> {
                    topBgHex = "#DCEEFF"     // 한파: 산뜻한 아이스 블루
                    bottomBgHex = "#FFE8B6"  // 아이들 블록: 포근한 버터 피치
                    pillBgHex = "#1E3A8A"
                    weatherIcon = "❄️"
                }
                else -> {
                    topBgHex = "#D6F4FF"     // 맑은 날: 경쾌한 스카이 블루
                    bottomBgHex = "#FEF08A"  // 아이들 블록: 화사한 레몬 옐로우
                    pillBgHex = "#0284C7"
                    weatherIcon = "☀️"
                }
            }

            views.setColorStateList(R.id.topContainer, "setBackgroundTintList", ColorStateList.valueOf(Color.parseColor(topBgHex)))
            views.setColorStateList(R.id.bottomContainer, "setBackgroundTintList", ColorStateList.valueOf(Color.parseColor(bottomBgHex)))
            views.setColorStateList(R.id.tvBadge, "setBackgroundTintList", ColorStateList.valueOf(Color.parseColor(pillBgHex)))

            views.setTextViewText(R.id.tvDate, dateLabel)
            views.setTextViewText(R.id.tvWeatherIcon, weatherIcon)
            views.setTextViewText(R.id.tvTempBig, "${minTemp}°/${maxTemp}°")

            val badgeText = if (hasRain) umbrellaBadge else "미세 $dustGrade"
            views.setTextViewText(R.id.tvBadge, badgeText)

            views.setTextViewText(R.id.tvDailyOutfit, dailyOutfit)
            views.setTextViewText(R.id.tvDailyMsg, dailyMsg)

            val bikePrefix = if (bikeAllowed) "🚲" else "⛔"
            views.setTextViewText(R.id.tvBikeLine, "$bikePrefix $bikeOutfit · $bikeMsg")
            views.setTextColor(
                R.id.tvBikeLine,
                Color.parseColor(if (bikeAllowed) "#0369A1" else "#DC2626")
            )

            views.setTextViewText(R.id.tvKidsOutfit, kidsOutfit)
            views.setTextViewText(R.id.tvKidsMsg, kidsMsg)
        } catch (e: Exception) {
            views.setTextViewText(R.id.tvDate, "업데이트 실패")
            views.setTextViewText(R.id.tvDailyMsg, "↻ 버튼을 눌러 다시 시도해 주세요.")
        }

        ids.forEach { manager.updateAppWidget(it, views) }
    }
}
