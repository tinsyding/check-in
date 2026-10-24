package com.jf.checkin.logic

import com.jf.checkin.data.model.Student
import java.time.LocalDateTime
import java.util.regex.Pattern

data class ScheduleSlot(val dayIndex: Int, val startMinutes: Int)

object TimeMatcher {

    private val TIME_PATTERN = Pattern.compile("(周[一二三四五六日天])\\s*(\\d{1,2}:\\d{2})~(\\d{1,2}:\\d{2})")

    /**
     * 解析“周六07:50~09:50”形式的课表时间。
     * dayIndex 使用 java.time.DayOfWeek 的编号：周一=1，周日=7。
     */
    fun parseSchedule(classTime: String): ScheduleSlot? {
        val matcher = TIME_PATTERN.matcher(classTime)
        if (!matcher.find()) return null

        val dayIndex = when (matcher.group(1)) {
            "周一" -> 1
            "周二" -> 2
            "周三" -> 3
            "周四" -> 4
            "周五" -> 5
            "周六" -> 6
            "周日", "周天" -> 7
            else -> return null
        }

        val timeParts = matcher.group(2)?.split(':') ?: return null
        val hour = timeParts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = timeParts.getOrNull(1)?.toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null

        return ScheduleSlot(dayIndex = dayIndex, startMinutes = hour * 60 + minute)
    }

    /** 供班级列表排序使用的自然课表顺序：周一靠前，周日靠后；无法解析的排到最后。 */
    fun scheduleSortKey(classTime: String): Int =
        parseSchedule(classTime)?.let { it.dayIndex * 24 * 60 + it.startMinutes } ?: Int.MAX_VALUE

    /**
     * 根据当前时间自动匹配班级
     * 规则：上课前 20 分钟 ～ 上课后 30 分钟
     */
    fun findMatchingClass(
        students: List<Student>,
        now: LocalDateTime = LocalDateTime.now()
    ): String? {
        val classTimes = students
            .groupBy { it.classCode }
            .mapValues { entry -> entry.value.firstOrNull()?.classTime ?: "" }

        val currentDayIndex = now.dayOfWeek.value
        val currentMinutes = now.hour * 60 + now.minute

        val matchingClasses = mutableListOf<String>()

        for ((classCode, timeStr) in classTimes) {
            val slot = parseSchedule(timeStr) ?: continue
            if (slot.dayIndex != currentDayIndex) continue

            val windowStart = slot.startMinutes - 20
            val windowEnd = slot.startMinutes + 30
            if (currentMinutes in windowStart..windowEnd) {
                matchingClasses.add(classCode)
            }
        }

        // 如果刚好匹配到唯一班级，返回该班级代码
        return if (matchingClasses.size == 1) matchingClasses.first() else null
    }
}
