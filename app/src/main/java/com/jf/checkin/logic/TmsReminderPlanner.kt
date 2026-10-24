package com.jf.checkin.logic

import com.jf.checkin.data.model.Student
import com.jf.checkin.data.remote.TmsAttendanceMapper
import java.time.LocalDate

data class PendingTmsClass(
    val classCode: String,
    val classTime: String,
    val classNames: List<String>
)

/**
 * 计算今天还有哪些校管家班级没有同步。
 *
 * 这里只使用本机记录，不额外请求校管家。也就是说，如果老师直接在校管家网页
 * 完成了点名，本机不会自动知道；下一次在本应用里点开同步预览时会识别并补记。
 */
object TmsReminderPlanner {

    /** 一个本地班级对应的校管家 ClassName 列表，例如 2026028MAA12β2 / β3。 */
    fun classNamesFor(students: List<Student>): List<String> {
        val classCode = students.firstOrNull()?.classCode ?: return emptyList()
        return students.asSequence()
            .map { it.classFormat }
            .filter { TmsAttendanceMapper.isSyncableFormat(it) }
            .distinct()
            .sorted()
            .map { classCode + it }
            .toList()
    }

    fun findPendingClasses(
        students: List<Student>,
        date: LocalDate,
        syncedClassNames: Set<String>
    ): List<PendingTmsClass> {
        val dayIndex = date.dayOfWeek.value
        val syncedKeys = syncedClassNames.map { TmsAttendanceMapper.classNameKey(it) }.toSet()
        return students.groupBy { it.classCode }
            .mapNotNull { (classCode, classStudents) ->
                val classTime = classStudents.firstOrNull()?.classTime.orEmpty()
                val slot = TimeMatcher.parseSchedule(classTime) ?: return@mapNotNull null
                if (slot.dayIndex != dayIndex) return@mapNotNull null

                val pendingNames = classNamesFor(classStudents)
                    .filterNot { TmsAttendanceMapper.classNameKey(it) in syncedKeys }
                if (pendingNames.isEmpty()) {
                    null
                } else {
                    PendingTmsClass(
                        classCode = classCode,
                        classTime = classTime,
                        classNames = pendingNames
                    )
                }
            }
            .sortedWith(
                compareBy<PendingTmsClass> { TimeMatcher.scheduleSortKey(it.classTime) }
                    .thenBy { it.classCode }
            )
    }
}
