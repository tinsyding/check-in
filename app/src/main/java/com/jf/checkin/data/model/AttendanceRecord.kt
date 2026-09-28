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
    val imagePath: String
)
