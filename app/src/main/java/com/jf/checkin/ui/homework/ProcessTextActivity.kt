package com.jf.checkin.ui.homework

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.jf.checkin.CheckInApplication
import com.jf.checkin.logic.homework.TextProcessor

/**
 * 系统划选菜单「转为错题格式」。
 *
 * 在任意应用中长按选中题号（如 135），系统浮动菜单会出现本入口，
 * 点击后直接把选中内容替换为预设的错题文案；只读文本则复制到剪贴板。
 */
class ProcessTextActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val selectedText = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        val isReadOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
        val prefs = (application as CheckInApplication).prefRepository

        if (!selectedText.isNullOrBlank()) {
            val numbers = TextProcessor.parseNumbers(selectedText, prefs.homeworkSplitMode)

            if (numbers.isNotEmpty()) {
                val formatted = TextProcessor.formatOutput(
                    numbers = numbers,
                    template = prefs.homeworkTemplate,
                    separator = prefs.homeworkSeparator,
                )

                if (isReadOnly) {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("错题文案", formatted))
                    Toast.makeText(this, "已复制：$formatted", Toast.LENGTH_SHORT).show()
                } else {
                    setResult(
                        RESULT_OK,
                        Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, formatted),
                    )
                }
            }
        }

        finish()
    }
}
