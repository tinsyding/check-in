package com.jf.checkin.util

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

/** 无障碍服务状态检测与跳转。 */
object AccessibilityUtil {

    /**
     * 判断指定无障碍服务是否已在系统设置中开启。
     * 注意：服务进程是否存活无法直接查询，这里以系统开启列表为准。
     */
    fun isServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
        val expected = ComponentName(context, service).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    /** 打开系统的无障碍设置页，由用户手动找到本应用并开启。 */
    fun openAccessibilitySettings(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        true
    }.getOrDefault(false)
}
