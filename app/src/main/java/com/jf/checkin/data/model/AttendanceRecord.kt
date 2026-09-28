package com.jf.checkin.data.model

data class AttendanceRecord(
    val id: String,
    val classCode: String,
    val classTime: String,
    val teacherName: String,
    val recordTime: String,
    val totalCount: Int,
    val presentCount: Int,
    val lateCount: Int,
    val leaveCount: Int,
    val absentCount: Int,
    val imagePath: String,
    /** 生成记录时全班每个学生的签到状态快照（studentId -> status.name），用于恢复回主界面 */
    val statusSnapshot: Map<String, String> = emptyMap()
)
