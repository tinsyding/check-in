package com.jf.checkin.data.parser

import com.jf.checkin.data.model.AttendanceStatus
import com.jf.checkin.data.model.Student

object StudentCsvParser {

    // 只有 β2 和 β3 是线下需要签到的；α1 和 β1 是线上，直接忽略
    private val OFFLINE_FORMATS = setOf("β2", "β3")

    fun parse(csvContent: String): List<Student> {
        val students = mutableListOf<Student>()
        val lines = csvContent.lines()

        for ((index, rawLine) in lines.withIndex()) {
            val line = rawLine.trim().replace("\uFEFF", "")
            if (line.isBlank()) continue

            // 跳过表头
            if (index == 0 && (line.contains("学号") || line.contains("姓名"))) {
                continue
            }

            val parts = splitCsvLine(line)
            if (parts.size >= 5) {
                val studentId = parts[0].trim()
                val name = parts[1].trim()
                val classCode = parts[2].trim()
                val classTime = parts[3].trim()
                val classFormat = parts[4].trim()
                val checkInMark = if (parts.size >= 6) parts[5].trim() else ""

                // 核心过滤规则：仅提取线下 β2 和 β3
                if (OFFLINE_FORMATS.contains(classFormat)) {
                    val initialStatus = when (checkInMark) {
                        "√", "对号", "已到", "出勤" -> AttendanceStatus.PRESENT
                        "迟到" -> AttendanceStatus.LATE
                        "请假" -> AttendanceStatus.LEAVE
                        else -> AttendanceStatus.UNCHECKED
                    }

                    students.add(
                        Student(
                            studentId = studentId,
                            name = name,
                            classCode = classCode,
                            classTime = classTime,
                            classFormat = classFormat,
                            status = initialStatus
                        )
                    )
                }
            }
        }
        return students
    }

    private fun splitCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when {
                ch == '\"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    tokens.add(sb.toString().trim())
                    sb.setLength(0)
                }
                else -> sb.append(ch)
            }
        }
        tokens.add(sb.toString().trim())
        return tokens
    }
}
