package com.jf.checkin.logic

import com.jf.checkin.data.model.Student
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TmsReminderPlannerTest {

    private val saturday = LocalDate.of(2026, 10, 24)

    private fun student(
        id: String,
        classCode: String,
        classTime: String,
        format: String
    ) = Student(
        studentId = id,
        name = "学生$id",
        classCode = classCode,
        classTime = classTime,
        classFormat = format
    )

    @Test
    fun `finds today's classes that are not synced`() {
        val students = listOf(
            student("1", "A", "周六07:50~09:50", "α1"),
            student("2", "A", "周六07:50~09:50", "β1"),
            student("3", "B", "周六10:00~12:00", "β2"),
            student("4", "C", "周日07:50~09:50", "β3")
        )

        val pending = TmsReminderPlanner.findPendingClasses(
            students = students,
            date = saturday,
            syncedClassNames = emptySet()
        )

        assertEquals(2, pending.size)
        assertEquals("A", pending[0].classCode)
        assertEquals(listOf("Aα1", "Aβ1"), pending[0].classNames)
        assertEquals("B", pending[1].classCode)
        assertEquals(listOf("Bβ2"), pending[1].classNames)
    }

    @Test
    fun `filters out class names already synced today`() {
        val students = listOf(
            student("1", "A", "周六07:50~09:50", "α1"),
            student("2", "A", "周六07:50~09:50", "β1"),
            student("3", "B", "周六10:00~12:00", "β2")
        )

        val pending = TmsReminderPlanner.findPendingClasses(
            students = students,
            date = saturday,
            syncedClassNames = setOf("Aα1", "Bβ2")
        )

        assertEquals(1, pending.size)
        assertEquals("A", pending[0].classCode)
        assertEquals(listOf("Aβ1"), pending[0].classNames)
    }

    @Test
    fun `returns empty when every class is synced`() {
        val students = listOf(
            student("1", "A", "周六07:50~09:50", "α1"),
            student("2", "B", "周六10:00~12:00", "β2")
        )

        val pending = TmsReminderPlanner.findPendingClasses(
            students = students,
            date = saturday,
            syncedClassNames = setOf("Aα1", "Bβ2")
        )

        assertTrue(pending.isEmpty())
    }

    @Test
    fun `ignores classes without a syncable format`() {
        val students = listOf(
            student("1", "A", "周六07:50~09:50", "手动"),
            student("2", "B", "周六10:00~12:00", "γ1")
        )

        val pending = TmsReminderPlanner.findPendingClasses(
            students = students,
            date = saturday,
            syncedClassNames = emptySet()
        )

        assertTrue(pending.isEmpty())
    }

    @Test
    fun `ignores classes with unparseable or missing time`() {
        val students = listOf(
            student("1", "A", "", "β2"),
            student("2", "B", "待定", "β3")
        )

        val pending = TmsReminderPlanner.findPendingClasses(
            students = students,
            date = saturday,
            syncedClassNames = emptySet()
        )

        assertTrue(pending.isEmpty())
    }

    @Test
    fun `matches synced class names ignoring case and whitespace`() {
        val students = listOf(
            student("1", "A", "周六07:50~09:50", "α1")
        )

        val pending = TmsReminderPlanner.findPendingClasses(
            students = students,
            date = saturday,
            syncedClassNames = setOf(" aα1 ")
        )

        assertTrue(pending.isEmpty())
    }

    @Test
    fun `builds distinct class names for current class`() {
        val students = listOf(
            student("1", "A", "周六07:50~09:50", "β3"),
            student("2", "A", "周六07:50~09:50", "α1"),
            student("3", "A", "周六07:50~09:50", "β3"),
            student("4", "A", "周六07:50~09:50", "手动")
        )

        assertEquals(listOf("Aα1", "Aβ3"), TmsReminderPlanner.classNamesFor(students))
    }

    @Test
    fun `sorts pending classes by class time`() {
        val students = listOf(
            student("1", "Late", "周六15:00~17:00", "β2"),
            student("2", "Early", "周六07:50~09:50", "β2")
        )

        val pending = TmsReminderPlanner.findPendingClasses(
            students = students,
            date = saturday,
            syncedClassNames = emptySet()
        )

        assertEquals(listOf("Early", "Late"), pending.map { it.classCode })
    }
}
