package com.jf.checkin.logic

/**
 * 导出长图 / 席卡 PDF 共用的纸墨色值。
 *
 * 逻辑层不能依赖 Compose，因此这里用 Android 颜色常量与
 * `ui/theme/Color.kt` 保持一一对应。改色时两边一起改。
 */
object ExportPalette {
    /** 页面暖纸底 */
    val Paper = 0xFFF4F0E8.toInt()

    /** 纸面卡片 / 席卡纸面 */
    val PaperSurface = 0xFFFFFDF8.toInt()

    /** 表头、下沉纸面 */
    val PaperSunken = 0xFFECE6DA.toInt()

    /** 1dp 发丝线 */
    val Hairline = 0xFFE0D7C7.toInt()

    /** 墨色文字 */
    val InkPrimary = 0xFF1F1B16.toInt()
    val InkSecondary = 0xFF5C5346.toInt()
    val InkTertiary = 0xFF6E6455.toInt()

    /** 红笔：已到 / 当前班 */
    val RedPen = 0xFFA63A1E.toInt()

    /** 状态色 */
    val StatusLate = 0xFF9A4708.toInt()
    val StatusLeave = 0xFF2B4C7E.toInt()
    val StatusLeaveReplay = 0xFF0F766E.toInt()
    val StatusOnline = 0xFF5B4B7A.toInt()
    val StatusUnchecked = 0xFF6F6559.toInt()

    /** 打印正文：纯黑，避免激光打印机半色调网点 */
    val PrintInk = 0xFF000000.toInt()
}
