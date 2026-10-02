package com.jf.checkin

import com.jf.checkin.data.model.AttendanceStatus
import com.jf.checkin.data.parser.StudentCsvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StudentCsvParserTest {

    @Test
    fun testParseAllStudentsCsvIncludesOnlineStudentsWithOnlineStatus() {
        val file = File("../../all_students.csv")
        val content = if (file.exists()) file.readText() else """
            学号,姓名,班级名称,上课时间,上课形式,签到
            35569,吴桐,2026028MAA12,周六07:50~09:50,α1,
            55136,黄天煦,2026028MAA12,周六07:50~09:50,β2,
            65222,余映漩,2026028MSS33,周六13:20~15:20,β1,
            55203,储沛岑,2026028MAA12,周六07:50~09:50,β3,
        """.trimIndent()

        val students = StudentCsvParser.parse(content)

        // 验证线上学生（α1, β1）与线下学生（β2, β3）均被解析
        val onlineStudents = students.filter { it.classFormat !in StudentCsvParser.OFFLINE_FORMATS }
        val offlineStudents = students.filter { it.classFormat in StudentCsvParser.OFFLINE_FORMATS }

        assertEquals(2, onlineStudents.size)
        assertEquals(2, offlineStudents.size)

        // 线上学生初始状态为 ONLINE (免签)
        assertTrue(onlineStudents.all { it.status == AttendanceStatus.ONLINE })

        // 线下学生初始状态为 UNCHECKED (待签到)
        assertTrue(offlineStudents.all { it.status == AttendanceStatus.UNCHECKED })
    }
}
