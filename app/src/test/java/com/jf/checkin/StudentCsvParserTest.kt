package com.jf.checkin

import com.jf.checkin.data.model.AttendanceStatus
import com.jf.checkin.data.parser.StudentCsvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StudentCsvParserTest {

    @Test
    fun testParseAllStudentsCsvFiltersOnlineStudents() {
        val file = File("../../all_students.csv")
        val content = if (file.exists()) file.readText() else """
            学号,姓名,班级名称,上课时间,上课形式,签到
            35569,吴桐,2026028MAA12,周六07:50~09:50,α1,
            55136,黄天煦,2026028MAA12,周六07:50~09:50,β2,
            65222,余映漩,2026028MSS33,周六13:20~15:20,β1,
            55203,储沛岑,2026028MAA12,周六07:50~09:50,β3,
        """.trimIndent()

        val students = StudentCsvParser.parse(content)

        // 验证只包含 β2 和 β3
        assertTrue(students.all { it.classFormat in setOf("β2", "β3") })
        // 验证不包含 α1 和 β1
        assertTrue(students.none { it.classFormat in setOf("α1", "β1") })

        // 初始状态均为 UNCHECKED
        assertTrue(students.all { it.status == AttendanceStatus.UNCHECKED })
    }
}
