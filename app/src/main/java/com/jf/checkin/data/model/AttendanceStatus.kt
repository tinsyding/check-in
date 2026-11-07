package com.jf.checkin.data.model

enum class AttendanceStatus(val label: String, val symbol: String) {
    UNCHECKED("未到", ""),
    PRESENT("已到", "√"),
    LATE("迟到", "迟到"),
    LEAVE("请假（不发回放）", "请假"),
    LEAVE_REPLAY("请假（发回放）", "请假·回放"),
    ONLINE("转线上", "线上");

    fun isPresent(): Boolean = this == PRESENT
    fun isOnline(): Boolean = this == ONLINE

    /** 是否属于请假（含发回放与不发回放），统计与同步时统一按请假处理。 */
    fun isLeave(): Boolean = this == LEAVE || this == LEAVE_REPLAY
}
