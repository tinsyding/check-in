package com.jf.checkin.data.remote

import com.jf.checkin.data.model.AttendanceStatus
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmsAttendanceMapperTest {

    private fun course(
        unitCode: Int = 2,
        durationMinutes: Int = 120,
        minutesToTimes: Int = 0,
        standardMinutes: Double = 0.0,
        consumeAmount: Double = 1.0,
        isDynamicConsume: Boolean = false
    ) = TmsCourseSummary(
        id = "course-id",
        className = "2026028MAA12β2",
        startTime = "2026-10-24T07:50:00",
        endTime = "2026-10-24T09:50:00",
        finished = 0,
        attendanceCount = 0,
        lastAttendTime = "0001-01-01T00:00:00",
        studentCount = 7,
        unitCode = unitCode,
        durationMinutes = durationMinutes,
        minutesToTimes = minutesToTimes,
        standardMinutes = standardMinutes,
        consumeAmount = consumeAmount,
        isDynamicConsume = isDynamicConsume
    )

    @Test
    fun parseClassTimeSupportsTildeAndDash() {
        val tilde = TmsAttendanceMapper.parseClassTime("周六07:50~09:50")
        assertEquals("07:50", tilde?.start)
        assertEquals("09:50", tilde?.end)

        val dash = TmsAttendanceMapper.parseClassTime("周日 10:20-12:20")
        assertEquals("10:20", dash?.start)
        assertEquals("12:20", dash?.end)

        assertNull(TmsAttendanceMapper.parseClassTime("时间待定"))
    }

    @Test
    fun tmsTimeHandlesIsoAndCompactValues() {
        assertEquals("07:50", TmsAttendanceMapper.tmsTime("2026-10-24T07:50:00"))
        assertEquals("07:50", TmsAttendanceMapper.tmsTime("2026-10-24 07:50:00"))
        assertEquals("07:50", TmsAttendanceMapper.tmsTime("07:50"))
    }

    @Test
    fun durationIsParsedFromHourMinuteText() {
        assertEquals(120, TmsAttendanceMapper.durationMinutes("02:00"))
        assertEquals(90, TmsAttendanceMapper.durationMinutes("01:30"))
        assertEquals(0, TmsAttendanceMapper.durationMinutes(""))
    }

    @Test
    fun classNameNormalizationIgnoresWhitespace() {
        assertEquals(
            "2026028MAA12β2",
            TmsAttendanceMapper.normalizeClassName(" 2026028MAA12 β2 ")
        )
    }

    @Test
    fun studentNumberAndNameSplitsNumericPrefix() {
        val (number, name) = TmsAttendanceMapper.studentNumberAndName("35455王康楚")
        assertEquals("35455", number)
        assertEquals("王康楚", name)

        val (emptyNumber, plainName) = TmsAttendanceMapper.studentNumberAndName("王康楚")
        assertEquals("", emptyNumber)
        assertEquals("王康楚", plainName)
    }

    @Test
    fun costFollowsTmsClassCheckRules() {
        // 按次、不按时长、非动态 -> 1
        assertEquals(1.0, TmsAttendanceMapper.computeCost(course()), 0.00001)

        // 按小时 -> 上课分钟数
        assertEquals(
            120.0,
            TmsAttendanceMapper.computeCost(course(unitCode = 1)),
            0.00001
        )

        // 按天/按月等特殊计费不在自动同步范围内 -> 0，阻止提交
        assertEquals(
            0.0,
            TmsAttendanceMapper.computeCost(course(unitCode = 3)),
            0.00001
        )

        // 按次且按时长 -> 分钟数 / 标准分钟，保留 5 位小数
        assertEquals(
            2.66667,
            TmsAttendanceMapper.computeCost(
                course(minutesToTimes = 1, standardMinutes = 45.0)
            ),
            0.00001
        )

        // 动态课消 -> 课程设置的 ConsumeAmount
        assertEquals(
            1.5,
            TmsAttendanceMapper.computeCost(
                course(isDynamicConsume = true, consumeAmount = 1.5)
            ),
            0.00001
        )

        // 动态课消为 0 -> 0，不能擅自按 1 计费
        assertEquals(
            0.0,
            TmsAttendanceMapper.computeCost(
                course(isDynamicConsume = true, consumeAmount = 0.0)
            ),
            0.00001
        )

        // 按次且按时长，但标准分钟缺失 -> 0，阻止自动提交
        assertEquals(
            0.0,
            TmsAttendanceMapper.computeCost(
                course(minutesToTimes = 1, standardMinutes = 0.0)
            ),
            0.00001
        )
    }

    @Test
    fun attendanceTimeIgnoresZeroAndNullValues() {
        assertFalse(TmsAttendanceMapper.hasAttendanceTime("0001-01-01T00:00:00"))
        assertFalse(TmsAttendanceMapper.hasAttendanceTime(""))
        assertFalse(TmsAttendanceMapper.hasAttendanceTime("null"))
        assertFalse(TmsAttendanceMapper.hasAttendanceTime("/Date(-62135596800000)/"))
        assertTrue(TmsAttendanceMapper.hasAttendanceTime("2026-10-24T09:50:00"))
    }

    @Test
    fun lastClassTimeUsesTmsDisplayFormat() {
        assertEquals(
            "2026-10-2407:50-09:50[周六]",
            TmsAttendanceMapper.lastClassTime(
                "2026-10-24T07:50:00",
                "2026-10-24T09:50:00"
            )
        )
    }

    @Test
    fun syncableFormatsIncludeOnlineAndOfflineClasses() {
        assertTrue(TmsAttendanceMapper.isSyncableFormat("α1"))
        assertTrue(TmsAttendanceMapper.isSyncableFormat("β1"))
        assertTrue(TmsAttendanceMapper.isSyncableFormat("β2"))
        assertTrue(TmsAttendanceMapper.isSyncableFormat("β3"))
        assertFalse(TmsAttendanceMapper.isSyncableFormat("手动"))
        assertFalse(TmsAttendanceMapper.isSyncableFormat("γ1"))
    }

    @Test
    fun allAbsentPlanCanSubmitWhenCostPositive() {
        val plan = plan(
            matches = listOf(tmsMatch(AttendanceStatus.UNCHECKED)),
            costAmount = 1.0
        )

        assertEquals(0, plan.presentCount)
        assertTrue(plan.canSubmit)
    }

    @Test
    fun allLeavePlanCanSubmitWhenCostPositive() {
        val plan = plan(
            matches = listOf(tmsMatch(AttendanceStatus.LEAVE)),
            costAmount = 1.0
        )

        assertEquals(0, plan.presentCount)
        assertTrue(plan.canSubmit)
    }

    @Test
    fun absentPlanStillRequiresPositiveCost() {
        val plan = plan(
            matches = listOf(tmsMatch(AttendanceStatus.UNCHECKED)),
            costAmount = 0.0
        )

        assertFalse(plan.canSubmit)
    }

    @Test
    fun presentPlanStillRequiresPositiveCost() {
        val plan = plan(
            matches = listOf(tmsMatch(AttendanceStatus.PRESENT)),
            costAmount = 0.0
        )

        assertFalse(plan.canSubmit)
    }

    @Test
    fun getStartDateTimeResolvesFromStartTimeOrClassTime() {
        val date = LocalDate.of(2026, 10, 24)

        // 1. 从 startTime ISO 时间解析
        val planIso = plan(listOf(tmsMatch(AttendanceStatus.PRESENT))).copy(
            startTime = "2026-10-24T07:50:00"
        )
        assertEquals(LocalDateTime.of(2026, 10, 24, 7, 50), planIso.getStartDateTime(date))

        // 2. 从 HH:mm 解析
        val planCompact = plan(listOf(tmsMatch(AttendanceStatus.PRESENT))).copy(
            startTime = "07:50"
        )
        assertEquals(LocalDateTime.of(2026, 10, 24, 7, 50), planCompact.getStartDateTime(date))

        // 3. startTime 为空时从 classTime 解析
        val planFallback = plan(listOf(tmsMatch(AttendanceStatus.PRESENT))).copy(
            startTime = "",
            classTime = "周六07:50~09:50"
        )
        assertEquals(LocalDateTime.of(2026, 10, 24, 7, 50), planFallback.getStartDateTime(date))
    }

    @Test
    fun earliestFutureStartTimeReturnsTargetWhenBeforeCourseStart() {
        val date = "2026-10-24"
        val plan = plan(listOf(tmsMatch(AttendanceStatus.PRESENT))).copy(
            startTime = "2026-10-24T07:50:00"
        )
        val preview = TmsSyncPreview(
            date = date,
            plans = listOf(plan),
            warnings = emptyList()
        )

        // 上课前 20 分钟 (07:30)：应该返回当天 07:50 作为预约时间
        val beforeCourse = LocalDateTime.of(2026, 10, 24, 7, 30)
        assertEquals(LocalDateTime.of(2026, 10, 24, 7, 50), preview.earliestFutureStartTime(beforeCourse))

        // 上课时间刚好到达 (07:50)：应该返回 null（表示可立即考勤）
        val atCourse = LocalDateTime.of(2026, 10, 24, 7, 50)
        assertNull(preview.earliestFutureStartTime(atCourse))

        // 上课后 (08:15)：应该返回 null（可立即考勤）
        val afterCourse = LocalDateTime.of(2026, 10, 24, 8, 15)
        assertNull(preview.earliestFutureStartTime(afterCourse))
    }

    @Test
    fun earliestFutureStartTimeHandlesPastDateAndMultiplePlans() {
        val plan1 = plan(listOf(tmsMatch(AttendanceStatus.PRESENT))).copy(
            className = "2026028MAA12β2",
            startTime = "2026-10-24T07:50:00"
        )
        val plan2 = plan(listOf(tmsMatch(AttendanceStatus.PRESENT))).copy(
            className = "2026028MAA12β3",
            startTime = "2026-10-24T09:50:00"
        )
        val preview = TmsSyncPreview(
            date = "2026-10-24",
            plans = listOf(plan1, plan2),
            warnings = emptyList()
        )

        // 上课前 (07:00)：返回最早的一个 (07:50)
        val early = LocalDateTime.of(2026, 10, 24, 7, 0)
        assertEquals(LocalDateTime.of(2026, 10, 24, 7, 50), preview.earliestFutureStartTime(early))

        // 补点昨天的课：返回 null（直接提交）
        val yesterdayPreview = TmsSyncPreview(
            date = "2026-10-23",
            plans = listOf(plan1),
            warnings = emptyList()
        )
        val today = LocalDateTime.of(2026, 10, 24, 7, 0)
        assertNull(yesterdayPreview.earliestFutureStartTime(today))
    }

    private fun tmsMatch(status: AttendanceStatus) = TmsStudentMatch(
        appStudentId = "app-1",
        appName = "学生A",
        appStatus = status,
        tmsStudentId = "tms-1",
        tmsName = "35455学生A"
    )

    private fun plan(
        matches: List<TmsStudentMatch>,
        costAmount: Double = 1.0
    ) = TmsCoursePlan(
        className = "2026028MAA12β2",
        classFormat = "β2",
        classTime = "周六07:50~09:50",
        courseId = "course-id",
        startTime = "2026-10-24T07:50:00",
        endTime = "2026-10-24T09:50:00",
        alreadyPointed = false,
        matches = matches,
        unmatchedAppStudents = emptyList(),
        tmsOnlyStudentNames = emptyList(),
        message = null,
        attendanceDetailJson = "{}",
        costAmount = costAmount,
        unitCode = 2
    )
}
