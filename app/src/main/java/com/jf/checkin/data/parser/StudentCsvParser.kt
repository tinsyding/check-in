package com.jf.checkin.data.parser

import com.jf.checkin.data.model.AttendanceStatus
import com.jf.checkin.data.model.Student

object StudentCsvParser {

    // β2 和 β3 是线下需要签到的；其他（如 α1, β1）是线上，无需签到
    val OFFLINE_FORMATS = setOf("β2", "β3")

    /**
     * 判断学生是否属于线下需签到人员。
     * 手动调入学生虽然 classFormat 为“手动”，但同样属于线下，不能按线上处理。
     */
    fun isOfflineFormat(classFormat: String): Boolean =
        classFormat == "手动" || classFormat in OFFLINE_FORMATS

    fun isOfflineStudent(student: Student): Boolean =
        student.isManual || isOfflineFormat(student.classFormat)

    /** 重置点名时使用的默认状态：线下（含手动调入）为未到，原生线上为免签线上。 */
    fun defaultStatus(student: Student): AttendanceStatus =
        if (isOfflineStudent(student)) AttendanceStatus.UNCHECKED else AttendanceStatus.ONLINE

    fun parse(csvContent: String): List<Student> {
        val students = mutableListOf<Student>()
        val lines = csvContent.lines()

        for (rawLine in lines) {
            val line = rawLine.trim().replace("\uFEFF", "")
            if (line.isBlank()) continue

            // 跳过表头（兼容表头前存在空行/BOM 的情况，仅在没有解析出任何学生时判断）
            if (students.isEmpty() && line.contains("学号") && line.contains("姓名")) {
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

                if (studentId.isNotBlank() && name.isNotBlank() && classCode.isNotBlank()) {
                    val initialStatus = if (isOfflineFormat(classFormat)) {
                        when (checkInMark) {
                            "√", "对号", "已到", "出勤" -> AttendanceStatus.PRESENT
                            "迟到" -> AttendanceStatus.LATE
                            "请假" -> AttendanceStatus.LEAVE
                            "转线上", "线上" -> AttendanceStatus.ONLINE
                            else -> AttendanceStatus.UNCHECKED
                        }
                    } else {
                        // 线上学生（如 α1, β1）：初始状态即为 ONLINE，无需签到
                        AttendanceStatus.ONLINE
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
