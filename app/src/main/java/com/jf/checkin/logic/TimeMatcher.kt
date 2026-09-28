package com.jf.checkin.logic

import com.jf.checkin.data.model.Student
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

object TimeMatcher {

    private val TIME_PATTERN = Pattern.compile("(周[一二三四五六日天])\\s*(\\d{1,2}:\\d{2})~(\\d{1,2}:\\d{2})")

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

        val currentDayChinese = toChineseDayOfWeek(now.dayOfWeek)
        val currentTime = now.toLocalTime()

        val matchingClasses = mutableListOf<String>()

        for ((classCode, timeStr) in classTimes) {
            val matcher = TIME_PATTERN.matcher(timeStr)
            if (matcher.find()) {
                val day = matcher.group(1)
                val startStr = matcher.group(2)
                val isSameDay = day == currentDayChinese || (day == "周天" && currentDayChinese == "周日")

                if (isSameDay) {
                    try {
                        val startTime = LocalTime.parse(startStr, DateTimeFormatter.ofPattern("H:mm"))
                        val windowStart = startTime.minusMinutes(20)
                        val windowEnd = startTime.plusMinutes(30)

                        if (!currentTime.isBefore(windowStart) && !currentTime.isAfter(windowEnd)) {
                            matchingClasses.add(classCode)
                        }
                    } catch (_: Exception) {
                        // 格式解析异常忽略
                    }
                }
            }
        }

        // 如果刚好匹配到唯一班级，返回该班级代码
        return if (matchingClasses.size == 1) matchingClasses.first() else null
    }

    private fun toChineseDayOfWeek(dayOfWeek: DayOfWeek): String {
        return when (dayOfWeek) {
            DayOfWeek.MONDAY -> "周一"
            DayOfWeek.TUESDAY -> "周二"
            DayOfWeek.WEDNESDAY -> "周三"
            DayOfWeek.THURSDAY -> "周四"
            DayOfWeek.FRIDAY -> "周五"
            DayOfWeek.SATURDAY -> "周六"
            DayOfWeek.SUNDAY -> "周日"
        }
    }
}
