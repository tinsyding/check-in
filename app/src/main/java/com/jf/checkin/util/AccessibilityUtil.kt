package com.jf.checkin.util

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import com.jf.checkin.service.TextExpanderService

/** 无障碍服务状态检测与跳转。 */
object AccessibilityUtil {

    /**
     * 判断指定无障碍服务是否已在系统设置中开启或正在运行。
     *
     * 采用多重检测以兼容各类定制系统（AOSP、MIUI/HyperOS、ColorOS、OriginOS、One UI、HarmonyOS 等）：
     * 1. 内存运行状态：服务自身标记位 TextExpanderService.isRunning（最直接最准确）
     * 2. 官方 AccessibilityManager API 查询已启用的服务列表
     * 3. 系统 Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES 列表（同时支持短类名与完整类名反序列化）
     */
    fun isServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
        // 1. 如果当前无障碍服务已经连上且在进程中存活，直接返回 true
        if (service == TextExpanderService::class.java && TextExpanderService.isRunning) {
            return true
        }

        // 2. 官方 AccessibilityManager API 查询已启用的服务列表
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            if (am != null && am.isEnabled) {
                val enabledList = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                if (enabledList != null) {
                    val targetPackage = context.packageName
                    val targetClass = service.name
                    val isFound = enabledList.any { info ->
                        val serviceInfo = info.resolveInfo?.serviceInfo
                        if (serviceInfo != null) {
                            serviceInfo.packageName == targetPackage && serviceInfo.name == targetClass
                        } else {
                            val id = info.id ?: ""
                            id.startsWith(targetPackage) && id.contains(service.simpleName)
                        }
                    }
                    if (isFound) return true
                }
            }
        } catch (_: Exception) {
            // 忽略异常，降级到 Secure Settings 比对
        }

        // 3. 系统 Secure Settings 列表比对
        val expectedComp = ComponentName(context, service)
        val expectedLong = expectedComp.flattenToString()       // com.jf.checkin/com.jf.checkin.service.TextExpanderService
        val expectedShort = expectedComp.flattenToShortString() // com.jf.checkin/.service.TextExpanderService (AOSP 标准格式)

        val enabledSetting = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
        }.getOrNull() ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledSetting)
        while (splitter.hasNext()) {
            val token = splitter.next().trim()
            if (token.isEmpty()) continue

            // 格式 A：直接匹配短字符串（AOSP 标准）或长字符串
            if (token.equals(expectedShort, ignoreCase = true) || token.equals(expectedLong, ignoreCase = true)) {
                return true
            }

            // 格式 B：通过 ComponentName.unflattenFromString 还原为组件比对
            val unflattened = ComponentName.unflattenFromString(token)
            if (unflattened != null &&
                unflattened.packageName == expectedComp.packageName &&
                unflattened.className == expectedComp.className
            ) {
                return true
            }

            // 格式 C：宽松匹配同包名和类名后缀
            if (token.startsWith("${context.packageName}/") && token.endsWith(service.simpleName)) {
                return true
            }
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
