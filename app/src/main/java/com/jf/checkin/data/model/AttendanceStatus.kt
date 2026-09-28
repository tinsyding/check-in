package com.jf.checkin.data.model

enum class AttendanceStatus(val label: String, val symbol: String) {
    UNCHECKED("未到", ""),
    PRESENT("已到", "√"),
    LATE("迟到", "迟到"),
    LEAVE("请假", "请假");

    fun isPresent(): Boolean = this == PRESENT
}
