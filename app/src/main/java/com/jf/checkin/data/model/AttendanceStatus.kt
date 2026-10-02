package com.jf.checkin.data.model

enum class AttendanceStatus(val label: String, val symbol: String) {
    UNCHECKED("未到", ""),
    PRESENT("已到", "√"),
    LATE("迟到", "迟到"),
    LEAVE("请假", "请假"),
    ONLINE("转线上", "线上");

    fun isPresent(): Boolean = this == PRESENT
    fun isOnline(): Boolean = this == ONLINE
}
