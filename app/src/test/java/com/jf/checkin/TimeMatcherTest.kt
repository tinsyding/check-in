package com.jf.checkin

import com.jf.checkin.data.model.Student
import com.jf.checkin.logic.TimeMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class TimeMatcherTest {

    private val sampleStudents = listOf(
        Student("1", "张三", "2026028MAA12", "周六07:50~09:50", "β2"),
        Student("2", "李四", "2026028MSS33", "周六13:20~15:20", "β2"),
        Student("3", "王五", "2026028MA+73", "周日10:20~12:20", "β2")
    )

    @Test
    fun testAutoMatchWithinWindow() {
        // 2026-09-19 是周六
        // 测试课前 10 分钟 (07:40) -> 应该命中 2026028MAA12
        val time1 = LocalDateTime.of(2026, 9, 19, 7, 40)
        assertEquals("2026028MAA12", TimeMatcher.findMatchingClass(sampleStudents, time1))

        // 测试上课后 10 分钟 (08:00) -> 应该命中 2026028MAA12
        val time2 = LocalDateTime.of(2026, 9, 19, 8, 0)
        assertEquals("2026028MAA12", TimeMatcher.findMatchingClass(sampleStudents, time2))

        // 测试课前 15 分钟 (13:05) -> 应该命中 2026028MSS33
        val time3 = LocalDateTime.of(2026, 9, 19, 13, 5)
        assertEquals("2026028MSS33", TimeMatcher.findMatchingClass(sampleStudents, time3))
    }

    @Test
    fun testAutoMatchOutsideWindow() {
        // 2026-09-19 周六 11:00 (不在任何上课时间前20min至后30min内)
        val timeOut = LocalDateTime.of(2026, 9, 19, 11, 0)
        assertNull(TimeMatcher.findMatchingClass(sampleStudents, timeOut))

        // 周三同一时间 -> 不应该命中周六/周日的班
        val timeWednesday = LocalDateTime.of(2026, 9, 16, 7, 45)
        assertNull(TimeMatcher.findMatchingClass(sampleStudents, timeWednesday))
    }
}
