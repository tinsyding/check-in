package com.jf.checkin.data.repository

import com.jf.checkin.data.model.AttendanceStatus
import com.jf.checkin.data.model.Student
import com.jf.checkin.data.parser.StudentCsvParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class StudentRepository(private val prefRepo: UserPrefRepository) {

    private val _students = MutableStateFlow<List<Student>>(emptyList())
    val students: StateFlow<List<Student>> = _students.asStateFlow()

    init {
        // 启动时优先读取本地持久化的上次 CSV 数据，并合并手动添加的学生 + 备注
        rebuildFromCache()
    }

    private fun rebuildFromCache() {
        val cachedCsv = prefRepo.lastCsvContent
        val csvStudents = if (cachedCsv.isNotBlank()) {
            StudentCsvParser.parse(cachedCsv)
        } else {
            emptyList()
        }
        _students.value = mergeWithManual(csvStudents)
    }

    /** CSV 名单 + 手动名单合并：手动学号去重优先保留，备注与调班叠加。 */
    private fun mergeWithManual(csvStudents: List<Student>): List<Student> {
        val remarks = prefRepo.getStudentRemarks()
        val manual = prefRepo.getManualStudents()
        val moves = prefRepo.getStudentMoves()
        val manualIds = manual.map { it.studentId }.toSet()
        // 各班级的上课时间 lookup（调班后继承目标班时间）
        val timeByClass = mutableMapOf<String, String>()
        for (s in csvStudents) timeByClass.putIfAbsent(s.classCode, s.classTime)
        for (m in manual) timeByClass.putIfAbsent(m.classCode, m.classTime)
        val merged = mutableListOf<Student>()
        // CSV 部分：去掉与手动学号冲突的，并叠加备注 + 调班
        for (s in csvStudents) {
            if (manualIds.contains(s.studentId)) continue
            var cur = s
            val movedCode = moves[s.studentId]
            if (!movedCode.isNullOrBlank() && movedCode != cur.classCode) {
                cur = cur.copy(
                    classCode = movedCode,
                    classTime = timeByClass[movedCode] ?: cur.classTime,
                    originalClassCode = s.classCode
                )
            }
            val remark = remarks[s.studentId] ?: ""
            merged.add(if (remark.isNotBlank() && cur.remark != remark) cur.copy(remark = remark) else cur)
        }
        // 手动部分：手动自带 remark 优先，无则用备注表兜底
        for (m in manual) {
            val remark = m.remark.ifBlank { remarks[m.studentId] ?: "" }
            merged.add(if (m.remark != remark) m.copy(remark = remark) else m)
        }
        // 叠加持久化的签到状态（重启 App 后保留）
        val statuses = prefRepo.getAttendanceStatuses()
        if (statuses.isNotEmpty()) {
            for (i in merged.indices) {
                val name = statuses[merged[i].studentId] ?: continue
                val st = try {
                    AttendanceStatus.valueOf(name)
                } catch (_: Exception) {
                    null
                } ?: continue
                if (merged[i].status != st) merged[i] = merged[i].copy(status = st)
            }
        }
        return merged
    }

    fun loadFromCsv(csvContent: String) {
        val parsed = StudentCsvParser.parse(csvContent)
        _students.value = mergeWithManual(parsed)
        // 持久化到本地（手动名单独立存储，不受 CSV 覆盖影响）
        prefRepo.lastCsvContent = csvContent
        prefRepo.lastImportTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    }

    /**
     * 手动向指定班级添加一名学生（如其他老师班上调入）。
     * @return null 表示成功，否则返回失败原因。
     */
    fun addManualStudent(
        studentId: String,
        name: String,
        classCode: String,
        remark: String = ""
    ): String? {
        val id = studentId.trim()
        val displayName = name.trim()
        val code = classCode.trim()
        if (id.isEmpty()) return "学号不能为空"
        if (displayName.isEmpty()) return "姓名不能为空"
        if (code.isEmpty()) return "班级不能为空"
        if (_students.value.any { it.studentId == id }) return "学号 $id 已存在"
        // 继承同班的上课时间，保持自动匹配/排序正常
        val classTime = _students.value.firstOrNull { it.classCode == code }?.classTime ?: ""
        val student = Student(
            studentId = id,
            name = displayName,
            classCode = code,
            classTime = classTime,
            classFormat = "手动",
            status = AttendanceStatus.UNCHECKED,
            remark = remark.trim(),
            isManual = true
        )
        val updated = prefRepo.getManualStudents() + student
        prefRepo.saveManualStudents(updated)
        if (remark.trim().isNotBlank()) {
            val remarks = prefRepo.getStudentRemarks().toMutableMap()
            remarks[id] = remark.trim()
            prefRepo.saveStudentRemarks(remarks)
        }
        _students.value = _students.value + student
        return null
    }

    /** 更新任意学生的备注（CSV 学生与手动学生通用），空字符串表示清除。 */
    fun updateStudentRemark(studentId: String, remark: String) {
        val clean = remark.trim()
        val remarks = prefRepo.getStudentRemarks().toMutableMap()
        if (clean.isBlank()) remarks.remove(studentId) else remarks[studentId] = clean
        prefRepo.saveStudentRemarks(remarks)
        // 同步手动名单中的副本
        val manual = prefRepo.getManualStudents()
        if (manual.any { it.studentId == studentId }) {
            prefRepo.saveManualStudents(manual.map {
                if (it.studentId == studentId) it.copy(remark = clean) else it
            })
        }
        _students.value = _students.value.map {
            if (it.studentId == studentId) it.copy(remark = clean) else it
        }
    }

    /** 仅允许删除手动添加的学生，避免误删 CSV 名单。成功返回 true。 */
    fun deleteManualStudent(studentId: String): Boolean {
        val manual = prefRepo.getManualStudents()
        if (manual.none { it.studentId == studentId }) return false
        prefRepo.saveManualStudents(manual.filterNot { it.studentId == studentId })
        val remarks = prefRepo.getStudentRemarks().toMutableMap()
        remarks.remove(studentId)
        prefRepo.saveStudentRemarks(remarks)
        val moves = prefRepo.getStudentMoves().toMutableMap()
        if (moves.remove(studentId) != null) prefRepo.saveStudentMoves(moves)
        _students.value = _students.value.filterNot { it.studentId == studentId }
        return true
    }

    /**
     * 把一名学生调到其他班级（CSV 学生记调动表持久化，手动学生直接改名单）。
     * @return null 表示成功，否则返回失败原因。
     */
    fun moveStudent(studentId: String, targetClassCode: String): String? {
        val code = targetClassCode.trim()
        if (code.isEmpty()) return "目标班级不能为空"
        val current = _students.value
        val student = current.find { it.studentId == studentId } ?: return "未找到该学生"
        if (student.classCode == code) return "该学生已在 $code"
        val targetTime = current.firstOrNull { it.classCode == code }?.classTime
            ?: return "目标班级 $code 不存在"
        // 保留最早的原班级，连续调动 A→B→C 仍记住 A，方便一键调回
        val origin = student.originalClassCode.ifBlank { student.classCode }
        if (student.isManual) {
            val manual = prefRepo.getManualStudents()
            prefRepo.saveManualStudents(manual.map {
                if (it.studentId == studentId) {
                    it.copy(classCode = code, classTime = targetTime, originalClassCode = origin)
                } else it
            })
        } else {
            val moves = prefRepo.getStudentMoves().toMutableMap()
            moves[studentId] = code
            prefRepo.saveStudentMoves(moves)
        }
        _students.value = current.map {
            if (it.studentId == studentId) {
                it.copy(classCode = code, classTime = targetTime, originalClassCode = origin)
            } else it
        }
        return null
    }

    /**
     * 把临时调出的学生调回原班级，并清除调动标记。
     * @return null 表示成功，否则返回失败原因。
     */
    fun returnMovedStudent(studentId: String): String? {
        val current = _students.value
        val student = current.find { it.studentId == studentId } ?: return "未找到该学生"
        val origin = student.originalClassCode.trim()
        if (origin.isEmpty()) return "该学生不在调动中"
        val originTime = current.firstOrNull { it.classCode == origin }?.classTime ?: student.classTime
        if (student.isManual) {
            val manual = prefRepo.getManualStudents()
            prefRepo.saveManualStudents(manual.map {
                if (it.studentId == studentId) {
                    it.copy(classCode = origin, classTime = originTime, originalClassCode = "")
                } else it
            })
        } else {
            val moves = prefRepo.getStudentMoves().toMutableMap()
            moves.remove(studentId)
            prefRepo.saveStudentMoves(moves)
        }
        _students.value = current.map {
            if (it.studentId == studentId) {
                it.copy(classCode = origin, classTime = originTime, originalClassCode = "")
            } else it
        }
        return null
    }

    fun toggleStudentCheckIn(studentId: String) {
        _students.value = _students.value.map {
            if (it.studentId == studentId) {
                // 线上听课人员无需签到，点击不切换签到状态
                if (it.status == AttendanceStatus.ONLINE) {
                    it
                } else {
                    val nextStatus = if (it.status == AttendanceStatus.PRESENT) {
                        AttendanceStatus.UNCHECKED
                    } else {
                        AttendanceStatus.PRESENT
                    }
                    it.copy(status = nextStatus)
                }
            } else {
                it
            }
        }
        persistStatuses()
    }

    fun setStudentStatus(studentId: String, status: AttendanceStatus) {
        _students.value = _students.value.map {
            if (it.studentId == studentId) {
                it.copy(status = status)
            } else {
                it
            }
        }
        persistStatuses()
    }

    fun markAllPresent(classCode: String) {
        _students.value = _students.value.map {
            if (it.classCode == classCode) {
                // 线上学生无需签到，全到时保持 ONLINE 不变
                if (it.status == AttendanceStatus.ONLINE) {
                    it
                } else {
                    it.copy(status = AttendanceStatus.PRESENT)
                }
            } else {
                it
            }
        }
        persistStatuses()
    }

    fun resetClass(classCode: String) {
        _students.value = _students.value.map {
            if (it.classCode == classCode) {
                // 原生线上学生（α1/β1）重置仍为 ONLINE，线下学生重置为 UNCHECKED
                val isOriginallyOnline = it.classFormat !in com.jf.checkin.data.parser.StudentCsvParser.OFFLINE_FORMATS
                it.copy(status = if (isOriginallyOnline) AttendanceStatus.ONLINE else AttendanceStatus.UNCHECKED)
            } else {
                it
            }
        }
        persistStatuses()
    }

    /** 按快照批量还原签到状态（历史记录恢复回主界面用，不存在的学号自动忽略） */
    fun applyStatuses(snapshot: Map<String, String>) {
        if (snapshot.isEmpty()) return
        _students.value = _students.value.map { s ->
            val name = snapshot[s.studentId] ?: return@map s
            val st = try {
                AttendanceStatus.valueOf(name)
            } catch (_: Exception) {
                null
            } ?: return@map s
            if (s.status == st) s else s.copy(status = st)
        }
        persistStatuses()
    }

    /** 清空全校所有班级的签到状态（回到默认状态：原生线上为线上，线下为未到） */
    fun resetAllStatuses() {
        _students.value = _students.value.map {
            val isOriginallyOnline = it.classFormat !in com.jf.checkin.data.parser.StudentCsvParser.OFFLINE_FORMATS
            val target = if (isOriginallyOnline) AttendanceStatus.ONLINE else AttendanceStatus.UNCHECKED
            if (it.status == target) it else it.copy(status = target)
        }
        persistStatuses()
    }

    /** 只持久化非默认状态，保持 prefs 精简 */
    private fun persistStatuses() {
        val map = _students.value
            .filter { it.status != AttendanceStatus.UNCHECKED }
            .associate { it.studentId to it.status.name }
        prefRepo.saveAttendanceStatuses(map)
    }

    fun clearAllStudents() {
        _students.value = emptyList()
        prefRepo.lastCsvContent = ""
        prefRepo.lastImportTime = ""
        prefRepo.saveManualStudents(emptyList())
        prefRepo.saveStudentRemarks(emptyMap())
        prefRepo.saveStudentMoves(emptyMap())
        prefRepo.saveAttendanceStatuses(emptyMap())
    }
}
