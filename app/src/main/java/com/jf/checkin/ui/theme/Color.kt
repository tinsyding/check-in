package com.jf.checkin.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────
// P1 design tokens：暖纸 + 墨 + 红笔勾
// 页面底色是纸，卡片是更亮的纸面，文字是墨，
// 红色只用于「已到」和当前班标记，不用于普通按钮。
// ─────────────────────────────────────────────────────────────

/** 页面底色（暖纸） */
val PaperBackground = Color(0xFFF4F0E8)

/** 卡片/顶栏纸面 */
val PaperSurface = Color(0xFFFFFDF8)

/** 输入框、统计块等下沉纸面 */
val PaperSunken = Color(0xFFECE6DA)

/** 1dp 发丝线 */
val Hairline = Color(0xFFE0D7C7)

/** 主文字：墨 */
val InkPrimary = Color(0xFF1F1B16)

/** 次级文字 */
val InkSecondary = Color(0xFF5C5346)

/** 辅助文字 / 占位（纸面上约 5.1:1） */
val InkTertiary = Color(0xFF6E6455)

/** 深色底上的文字 */
val InkOnDark = Color(0xFFFFFDF8)

/** 红笔：已到 / 当前班 / 主品牌强调 */
val RedPen = Color(0xFFA63A1E)

/** 红笔淡底 */
val RedPenSoft = Color(0xFFF7E7E0)

/** 危险操作 */
val Danger = Color(0xFFB3261E)

/** 危险操作淡底 */
val DangerSoft = Color(0xFFFBEAE8)

/** 状态徽章/中性 chip 底色 */
val BadgeBackground = Color(0xFFF2EEE6)

// 语义状态色：只做圆点、3dp 边和文字强调，不铺整面背景
val StatusLateInk = Color(0xFF9A4708)
val StatusLeaveInk = Color(0xFF1D4ED8)
val StatusLeaveReplayInk = Color(0xFF0F766E)
val StatusOnlineInk = Color(0xFF6D28D9)
val StatusUncheckedInk = Color(0xFF6F6559)
