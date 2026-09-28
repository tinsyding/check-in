package com.jf.checkin.data.model

data class Student(
    val studentId: String,
    val name: String,
    val classCode: String,
    val classTime: String,
    val classFormat: String,
    var status: AttendanceStatus = AttendanceStatus.UNCHECKED,
    val remark: String = "",
    val isManual: Boolean = false,
    /** 临时调班前的原班级号，为空表示未调动 */
    val originalClassCode: String = ""
)
