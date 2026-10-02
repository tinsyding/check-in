package com.jf.checkin

import com.jf.checkin.data.model.AttendanceRecord
import com.jf.checkin.data.model.AttendanceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceStatusTest {

    @Test
    fun testOnlineStatusProperties() {
        val online = AttendanceStatus.ONLINE
        assertEquals("转线上", online.label)
        assertEquals("线上", online.symbol)
        assertTrue(online.isOnline())
        assertFalse(online.isPresent())

        val valueOfOnline = AttendanceStatus.valueOf("ONLINE")
        assertEquals(AttendanceStatus.ONLINE, valueOfOnline)
    }

    @Test
    fun testAttendanceRecordOnlineCountDefault() {
        val record = AttendanceRecord(
            id = "test-id",
            classCode = "TEST01",
            classTime = "08:00~10:00",
            teacherName = "教师A",
            recordTime = "2026-10-02 21:00",
            totalCount = 20,
            presentCount = 18,
            lateCount = 1,
            leaveCount = 1,
            absentCount = 0,
            imagePath = "/path/to/img.png"
        )
        assertEquals(0, record.onlineCount)
        assertEquals(0, record.movedOutCount)
    }

    @Test
    fun testClassInfoOfflineCount() {
        val info = com.jf.checkin.ui.rollcall.RollCallViewModel.ClassInfo(
            classCode = "2026028MAA12",
            classTime = "周六07:50~09:50",
            totalCount = 20,
            presentCount = 15,
            onlineCount = 3
        )
        assertEquals(17, info.offlineCount)
    }
}
