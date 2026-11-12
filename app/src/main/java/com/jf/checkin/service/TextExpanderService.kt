package com.jf.checkin.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jf.checkin.CheckInApplication
import com.jf.checkin.logic.homework.TextProcessor

/**
 * 错题自动展开无障碍服务。
 *
 * 监听全局输入框的文字变化，当文本末尾出现「数字 + 触发词」（默认 135qqq）时，
 * 自动擦除触发词并就地展开为预设文案，例如：
 * 135qqq → 本次作业的错题题号为：1，3，5
 *
 * 服务需要用户在系统设置中手动开启，应用本身无法代开。
 */
class TextExpanderService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isReplacing = false
    private var lastReplacedText: String? = null

    private val prefs
        get() = (application as CheckInApplication).prefRepository

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.i(TAG, "错题自动展开服务已启动")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !isRunning || isReplacing) return
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        if (!prefs.homeworkTriggerEnabled) return
        // 不处理本应用自己的输入框，避免设置页测试输入被自动替换
        if (event.packageName?.toString() == packageName) return

        val source = event.source ?: return
        try {
            // 只处理可编辑、可操作的文本框；密码框和超长文本直接跳过
            if (!source.isEditable || !source.isEnabled || source.isPassword) return

            val currentText = source.text?.toString() ?: return
            if (currentText.isEmpty() || currentText.length > 2000) return

            // 避免刚才替换生成的文字引起死循环触发
            if (currentText == lastReplacedText) return

            val triggerWord = prefs.homeworkTriggerWord
            val match = TextProcessor.matchTrigger(currentText, triggerWord) ?: return

            val numbers = TextProcessor.parseNumbers(match.rawNumbers, prefs.homeworkSplitMode)
            if (numbers.isEmpty()) return

            val formatted = TextProcessor.formatOutput(
                numbers = numbers,
                template = prefs.homeworkTemplate,
                separator = prefs.homeworkSeparator,
            )

            val prefix = currentText.substring(0, match.startIndex)
            val suffix = currentText.substring(match.endIndex)
            val newText = prefix + formatted + suffix

            isReplacing = true
            lastReplacedText = newText

            val args = android.os.Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    newText,
                )
            }
            val replaced = source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

            if (replaced) {
                // 把光标放到展开文案末尾
                val cursorPosition = (prefix + formatted).length
                val cursorArgs = android.os.Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursorPosition)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursorPosition)
                }
                source.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, cursorArgs)
            }
        } catch (error: Exception) {
            Log.e(TAG, "文本替换出错: ${error.message}", error)
        } finally {
            // 延时解除防重入锁，避开文本变更回调的重入
            mainHandler.postDelayed({ isReplacing = false }, REENTRY_DELAY_MS)
            @Suppress("DEPRECATION")
            source.recycle()
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "错题自动展开服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.i(TAG, "错题自动展开服务已销毁")
    }

    companion object {
        private const val TAG = "TextExpanderService"
        private const val REENTRY_DELAY_MS = 300L

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
