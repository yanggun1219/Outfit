package com.outfit.widget

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("outfit_prefs", MODE_PRIVATE)
        val savedUrl = prefs.getString("worker_url", "") ?: ""

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(56, 120, 56, 56)
        }

        val title = TextView(this).apply {
            text = "아침 옷차림 위젯 설정"
            textSize = 22f
            setTextColor(Color.WHITE)
        }

        val desc = TextView(this).apply {
            text = "1단계에서 배포한 Cloudflare Worker 주소를 입력해 주세요.\n(예: https://outfit-briefing.계정.workers.dev)"
            textSize = 14f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, 20, 0, 36)
        }

        val input = EditText(this).apply {
            setText(savedUrl)
            hint = "https://..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(32, 32, 32, 32)
            isSingleLine = true
        }

        val saveBtn = Button(this).apply {
            text = "저장하고 위젯 새로고침"
            setOnClickListener {
                val url = input.text.toString().trim().trimEnd('/')
                if (!url.startsWith("http")) {
                    Toast.makeText(this@MainActivity, "올바른 https:// 주소를 입력해 주세요.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                prefs.edit().putString("worker_url", url).apply()
                OutfitWidgetProvider.triggerRefresh(this@MainActivity)
                Toast.makeText(this@MainActivity, "저장 완료! 위젯을 업데이트합니다.", Toast.LENGTH_SHORT).show()
            }
        }

        val openWebBtn = Button(this).apply {
            text = "규칙 설정 웹페이지 열기"
            setOnClickListener {
                val url = prefs.getString("worker_url", "") ?: ""
                if (url.startsWith("http")) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } else {
                    Toast.makeText(this@MainActivity, "먼저 Worker 주소를 저장해 주세요.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        root.addView(title)
        root.addView(desc)
        root.addView(input)
        root.addView(saveBtn)
        root.addView(openWebBtn)
        setContentView(root)
    }
}
