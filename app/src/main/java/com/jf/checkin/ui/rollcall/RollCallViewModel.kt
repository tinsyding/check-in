package com.jf.checkin.ui.rollcall

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jf.checkin.data.model.AttendanceRecord
import com.jf.checkin.data.model.AttendanceStatus
import com.jf.checkin.data.model.Student
import com.jf.checkin.data.repository.HistoryRepository
import com.jf.checkin.data.repository.StudentRepository
import com.jf.checkin.data.repository.UserPrefRepository
import com.jf.checkin.logic.TableImageGenerator
import com.jf.checkin.logic.TimeMatcher
import com.jf.checkin.util.ShareUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class ClassInfo(
    val classCode: String,
    val classTime: String,
    val totalCount: Int,
    val presentCount: Int
)

class RollCallViewModel(
    private val studentRepository: StudentRepository,
    private val prefRepository: UserPrefRepository,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    val allStudents: StateFlow<List<Student>> = studentRepository.students

    private val _selectedClassCode = MutableStateFlow<String?>(null)
    val selectedClassCode: StateFlow<String?> = _selectedClassCode.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _teacherName = MutableStateFlow(prefRepository.teacherName)
    val teacherName: StateFlow<String> = _teacherName.asStateFlow()

    private val _printerIp = MutableStateFlow(prefRepository.printerIp)
    val printerIp: StateFlow<String> = _printerIp.asStateFlow()

    private val _printerPort = MutableStateFlow(prefRepository.printerPort.toString())
    val printerPort: StateFlow<String> = _printerPort.asStateFlow()

    private val _printerSimplex = MutableStateFlow(prefRepository.printerSimplex)
    val printerSimplex: StateFlow<Boolean> = _printerSimplex.asStateFlow()

    private val _printerProfiles = MutableStateFlow(prefRepository.getPrinterProfiles())
    val printerProfiles: StateFlow<List<com.jf.checkin.data.model.PrinterProfile>> = _printerProfiles.asStateFlow()

    private val _selectedPrinterId = MutableStateFlow(
        prefRepository.selectedPrinterId.ifBlank { _printerProfiles.value.firstOrNull()?.id ?: "" }
    )
    val selectedPrinterId: StateFlow<String> = _selectedPrinterId.asStateFlow()

    private val _nameplateFontStyle = MutableStateFlow(com.jf.checkin.logic.NameplateFontStyle.BOLD_STANDARD)
    val nameplateFontStyle: StateFlow<com.jf.checkin.logic.NameplateFontStyle> = _nameplateFontStyle.asStateFlow()

    fun setNameplateFontStyle(style: com.jf.checkin.logic.NameplateFontStyle) {
        _nameplateFontStyle.value = style
    }

    fun reloadPrinterProfiles() {
        val list = prefRepository.getPrinterProfiles()
        _printerProfiles.value = list
        val currentId = prefRepository.selectedPrinterId
        val active = list.find { it.id == currentId } ?: list.firstOrNull()
        if (active != null) {
            _selectedPrinterId.value = active.id
            _printerIp.value = active.ip
            _printerPort.value = active.port.toString()
        }
    }

    fun getEffectiveTeacherName(): String {
        val prefName = prefRepository.teacherName
        if (prefName.isNotBlank() && _teacherName.value != prefName) {
            _teacherName.value = prefName
        }
        return _teacherName.value.ifBlank { prefName }
    }

    fun reloadSettings() {
        val prefName = prefRepository.teacherName
        _teacherName.value = prefName
        reloadPrinterProfiles()
    }

    fun selectPrinterProfile(profile: com.jf.checkin.data.model.PrinterProfile) {
        _selectedPrinterId.value = profile.id
        prefRepository.selectedPrinterId = profile.id
        _printerIp.value = profile.ip
        _printerPort.value = profile.port.toString()
        prefRepository.printerIp = profile.ip
        prefRepository.printerPort = profile.port
    }

    fun updatePrinterSettings(ip: String, portStr: String, simplex: Boolean) {
        _printerIp.value = ip
        _printerPort.value = portStr
        _printerSimplex.value = simplex
        prefRepository.printerIp = ip
        prefRepository.printerPort = portStr.toIntOrNull() ?: 9100
        prefRepository.printerSimplex = simplex
    }

    // 所有班级列表与统计
    val classList: StateFlow<List<ClassInfo>> = allStudents.combine(_selectedClassCode) { students, _ ->
        students.groupBy { it.classCode }.map { (code, list) ->
            ClassInfo(
                classCode = code,
                classTime = list.firstOrNull()?.classTime ?: "",
                totalCount = list.size,
                presentCount = list.count { it.status == AttendanceStatus.PRESENT }
            )
        }.sortedBy { it.classTime }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 当前选中班级的学生名单
    val currentStudents: StateFlow<List<Student>> = combine(allStudents, _selectedClassCode) { students, selectedCode ->
        if (selectedCode == null) {
            emptyList()
        } else {
            students.filter { it.classCode == selectedCode }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 当前班级是否已经全到
    val isAllPresent: StateFlow<Boolean> = currentStudents.map { list ->
        list.isNotEmpty() && list.all { it.status == AttendanceStatus.PRESENT }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** 全校所有处于临时调动中的学生（人在外班，原班有显示、现班有来源） */
    val movedStudents: StateFlow<List<Student>> = allStudents.map { list ->
        list.filter { it.originalClassCode.isNotBlank() && it.originalClassCode != it.classCode }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // 监听学生列表加载完成，若当前未选中班级，尝试时间智能匹配或默认选第一个
        viewModelScope.launch {
            allStudents.collect { students ->
                if (students.isNotEmpty() && _selectedClassCode.value == null) {
                    val matched = TimeMatcher.findMatchingClass(students)
                    _selectedClassCode.value = matched ?: students.first().classCode
                }
            }
        }
    }

    fun selectClass(classCode: String) {
        _selectedClassCode.value = classCode
    }

    fun updateTeacherName(name: String) {
        _teacherName.value = name
        prefRepository.teacherName = name
    }

    fun toggleCheckIn(studentId: String) {
        studentRepository.toggleStudentCheckIn(studentId)
    }

    fun setStatus(studentId: String, status: AttendanceStatus) {
        studentRepository.setStudentStatus(studentId, status)
    }

    /** 手动加人：null=成功，否则为失败原因。 */
    fun addManualStudent(studentId: String, name: String, remark: String = ""): String? {
        val currentCode = _selectedClassCode.value ?: return "请先选择班级"
        return studentRepository.addManualStudent(studentId, name, currentCode, remark)
    }

    fun updateStudentRemark(studentId: String, remark: String) {
        studentRepository.updateStudentRemark(studentId, remark)
    }

    fun deleteManualStudent(studentId: String): Boolean {
        return studentRepository.deleteManualStudent(studentId)
    }

    /** 跨班调动：null=成功，否则为失败原因；调空原班级后自动切到目标班。 */
    fun moveStudent(studentId: String, targetClassCode: String): String? {
        val error = studentRepository.moveStudent(studentId, targetClassCode)
        if (error == null) {
            autoSelectIfCurrentEmpty()
        }
        return error
    }

    /** 调回原班：null=成功，否则为失败原因；调空当前班后自动跟随。 */
    fun returnMovedStudent(studentId: String): String? {
        val error = studentRepository.returnMovedStudent(studentId)
        if (error == null) {
            autoSelectIfCurrentEmpty()
        }
        return error
    }

    private fun autoSelectIfCurrentEmpty() {
        val currentCode = _selectedClassCode.value
        if (currentCode != null && allStudents.value.none { it.classCode == currentCode }) {
            _selectedClassCode.value = allStudents.value.firstOrNull()?.classCode
        }
    }

    /**
     * 动态切换：若已全到，则取消全到；若未全到，则一键全到
     */
    fun toggleAllPresent() {
        val currentCode = _selectedClassCode.value ?: return
        if (isAllPresent.value) {
            studentRepository.resetClass(currentCode)
        } else {
            studentRepository.markAllPresent(currentCode)
        }
    }

    fun resetCurrentClass() {
        val currentCode = _selectedClassCode.value ?: return
        studentRepository.resetClass(currentCode)
    }

    /** 清空全校所有班级的签到状态 */
    fun resetAllStatuses() {
        studentRepository.resetAllStatuses()
    }

    /**
     * 把一条历史记录恢复回主界面：切到对应班级并还原每个学生的签到状态。
     * @return null 表示成功，否则为失败原因
     */
    fun restoreRecord(record: AttendanceRecord): String? {
        if (allStudents.value.none { it.classCode == record.classCode }) {
            return "班级 ${record.classCode} 已不在当前名单中"
        }
        _selectedClassCode.value = record.classCode
        studentRepository.applyStatuses(record.statusSnapshot)
        return null
    }

    fun autoMatchClassNow(): Boolean {
        val students = allStudents.value
        if (students.isEmpty()) return false
        val matched = TimeMatcher.findMatchingClass(students)
        return if (matched != null) {
            _selectedClassCode.value = matched
            true
        } else {
            false
        }
    }

    /**
     * 保存长图到系统相册并记录到历史
     */
    fun downloadAndSaveImage(context: Context) {
        val currentCode = _selectedClassCode.value ?: return
        val students = currentStudents.value
        if (students.isEmpty()) {
            Toast.makeText(context, "当前班级暂无学生数据", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            _isExporting.value = true
            try {
                val classTime = students.firstOrNull()?.classTime ?: ""
                val currentTeacher = getEffectiveTeacherName()
                val success = withContext(Dispatchers.IO) {
                    // 同时生成 cache 文件用于历史记录
                    val cacheFile = TableImageGenerator.generateTableImageFile(context, currentCode, classTime, currentTeacher, students)
                    saveHistory(cacheFile, currentCode, classTime, students)
                    TableImageGenerator.saveToGallery(context, currentCode, classTime, currentTeacher, students)
                }
                if (success) {
                    Toast.makeText(context, "签到长图已成功保存至相册（Pictures/班级签到表）！", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "保存到相册失败，请检查存储权限", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "保存图片失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                _isExporting.value = false
            }
        }
    }

    /**
     * 生成长图并调起系统“发送给”（微信等），并记录到历史
     */
    fun exportAndShare(context: Context) {
        val currentCode = _selectedClassCode.value ?: return
        val students = currentStudents.value
        if (students.isEmpty()) {
            Toast.makeText(context, "当前班级暂无学生数据", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            _isExporting.value = true
            try {
                val classTime = students.firstOrNull()?.classTime ?: ""
                val currentTeacher = getEffectiveTeacherName()
                val imageFile = withContext(Dispatchers.IO) {
                    val file = TableImageGenerator.generateTableImageFile(context, currentCode, classTime, currentTeacher, students)
                    saveHistory(file, currentCode, classTime, students)
                    file
                }
                ShareUtil.shareImage(context, imageFile, "$currentCode 线下签到表")
            } catch (e: Exception) {
                Toast.makeText(context, "生成签到长图失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                _isExporting.value = false
            }
        }
    }

    /**
     * 生成席卡 PDF 并调起系统“发送给”（发微信、发送至打印机等）
     */
    fun exportAndShareNameplatePdf(context: Context) {
        val currentCode = _selectedClassCode.value ?: return
        val students = currentStudents.value
        if (students.isEmpty()) {
            Toast.makeText(context, "当前班级暂无学生数据", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            _isExporting.value = true
            try {
                val pdfFile = withContext(Dispatchers.IO) {
                    com.jf.checkin.logic.NameplatePdfGenerator.generatePdfFile(
                        context, currentCode, students, fontStyle = _nameplateFontStyle.value
                    )
                }
                ShareUtil.sharePdf(context, pdfFile, "$currentCode 班级席卡(A4打印)")
            } catch (e: Exception) {
                Toast.makeText(context, "生成席卡 PDF 失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                _isExporting.value = false
            }
        }
    }

    /**
     * 下载席卡 PDF 保存到系统 Downloads/班级席卡 文件夹
     */
    fun downloadNameplatePdf(context: Context) {
        val currentCode = _selectedClassCode.value ?: return
        val students = currentStudents.value
        if (students.isEmpty()) {
            Toast.makeText(context, "当前班级暂无学生数据", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            _isExporting.value = true
            try {
                val success = withContext(Dispatchers.IO) {
                    com.jf.checkin.logic.NameplatePdfGenerator.savePdfToDownloads(
                        context, currentCode, students, fontStyle = _nameplateFontStyle.value
                    )
                }
                if (success) {
                    Toast.makeText(context, "席卡 PDF 已下载至“下载 (Download/班级席卡)”目录！", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "保存 PDF 失败，请检查存储权限", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "下载席卡 PDF 失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                _isExporting.value = false
            }
        }
    }

    /**
     * 局域网直连打印机开始打印 (RAW 9100 端口流式直发)
     * @param isTestOneSheet 为 true 时仅打印第 1 位学生的一张纸测试席卡
     */
    fun directPrintNameplatePdf(
        context: Context,
        ip: String = _printerIp.value,
        portStr: String = _printerPort.value,
        forceSimplex: Boolean = _printerSimplex.value,
        isTestOneSheet: Boolean = false,
        onComplete: (() -> Unit)? = null
    ) {
        val currentCode = _selectedClassCode.value ?: return
        val students = currentStudents.value
        if (students.isEmpty()) {
            Toast.makeText(context, "当前班级暂无学生数据", Toast.LENGTH_SHORT).show()
            return
        }

        val port = portStr.toIntOrNull() ?: 9100
        updatePrinterSettings(ip, portStr, forceSimplex)

        viewModelScope.launch {
            _isExporting.value = true
            val countDesc = if (isTestOneSheet) "测试单页 (1张)" else "全班 (${students.size}张)"
            Toast.makeText(context, "正在连接打印机 $ip:$port 发送 $countDesc...", Toast.LENGTH_SHORT).show()
            try {
                val pdfFile = withContext(Dispatchers.IO) {
                    com.jf.checkin.logic.NameplatePdfGenerator.generatePdfFile(
                        context, currentCode, students, onlyFirstStudent = isTestOneSheet, fontStyle = _nameplateFontStyle.value
                    )
                }
                val result = withContext(Dispatchers.IO) {
                    com.jf.checkin.logic.LanPrinterHelper.printPdf(pdfFile, ip, port, forceSimplex)
                }
                result.fold(
                    onSuccess = {
                        val msg = if (isTestOneSheet) {
                            "席卡测试页 (1张) 已成功发送至打印机 $ip:$port"
                        } else {
                            "全班席卡 (共 ${students.size} 张) 已成功发送至打印机 $ip:$port"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        onComplete?.invoke()
                    },
                    onFailure = { error ->
                        Toast.makeText(context, "发送打印失败: ${error.localizedMessage}\n请检查手机与打印机是否在同一局域网", Toast.LENGTH_LONG).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(context, "打印异常: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                _isExporting.value = false
            }
        }
    }

    /**
     * 调用系统打印服务 (Android PrintManager - 满血 600 DPI 驱动级渲染，推荐)
     */
    fun printViaSystem(context: Context, isTestOneSheet: Boolean = false) {
        val currentCode = _selectedClassCode.value ?: return
        val students = currentStudents.value
        if (students.isEmpty()) {
            Toast.makeText(context, "当前班级暂无学生数据", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            _isExporting.value = true
            try {
                val pdfFile = withContext(Dispatchers.IO) {
                    com.jf.checkin.logic.NameplatePdfGenerator.generatePdfFile(
                        context, currentCode, students, onlyFirstStudent = isTestOneSheet, fontStyle = _nameplateFontStyle.value
                    )
                }
                val jobTitle = if (isTestOneSheet) "$currentCode 席卡测试单页" else "$currentCode 班级席卡"
                com.jf.checkin.util.SystemPrintUtil.printPdf(context, pdfFile, jobTitle)
            } catch (e: Exception) {
                Toast.makeText(context, "调起系统打印失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                _isExporting.value = false
            }
        }
    }

    /**
     * 通过 LPR/LPD (RFC 1179, 端口 515) 协议发送打印作业
     */
    fun printViaLpr(
        context: Context,
        ip: String = _printerIp.value,
        portStr: String = "515",
        queue: String = "lp",
        isTestOneSheet: Boolean = false,
        onComplete: (() -> Unit)? = null
    ) {
        val currentCode = _selectedClassCode.value ?: return
        val students = currentStudents.value
        if (students.isEmpty()) {
            Toast.makeText(context, "当前班级暂无学生数据", Toast.LENGTH_SHORT).show()
            return
        }

        val port = portStr.toIntOrNull() ?: 515
        viewModelScope.launch {
            _isExporting.value = true
            val countDesc = if (isTestOneSheet) "测试单页 (1张)" else "全班 (${students.size}张)"
            Toast.makeText(context, "正在连接 LPR 打印机 $ip:$port 发送 $countDesc...", Toast.LENGTH_SHORT).show()
            try {
                val pdfFile = withContext(Dispatchers.IO) {
                    com.jf.checkin.logic.NameplatePdfGenerator.generatePdfFile(
                        context, currentCode, students, onlyFirstStudent = isTestOneSheet, fontStyle = _nameplateFontStyle.value
                    )
                }
                val result = withContext(Dispatchers.IO) {
                    com.jf.checkin.logic.LprPrinterHelper.printPdf(pdfFile, ip, port, queue)
                }
                result.fold(
                    onSuccess = {
                        val msg = if (isTestOneSheet) {
                            "LPR 席卡测试页 (1张) 已成功发送至打印机 $ip:$port"
                        } else {
                            "LPR 全班席卡 (共 ${students.size} 张) 已成功发送至打印机 $ip:$port"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        onComplete?.invoke()
                    },
                    onFailure = { error ->
                        Toast.makeText(context, "LPR 打印失败: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(context, "LPR 打印异常: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                _isExporting.value = false
            }
        }
    }

    /**
     * 通过 FTP (端口 21) 投递 PDF 打印任务
     */
    fun printViaFtp(
        context: Context,
        ip: String = _printerIp.value,
        portStr: String = "21",
        isTestOneSheet: Boolean = false,
        onComplete: (() -> Unit)? = null
    ) {
        val currentCode = _selectedClassCode.value ?: return
        val students = currentStudents.value
        if (students.isEmpty()) {
            Toast.makeText(context, "当前班级暂无学生数据", Toast.LENGTH_SHORT).show()
            return
        }

        val port = portStr.toIntOrNull() ?: 21
        viewModelScope.launch {
            _isExporting.value = true
            val countDesc = if (isTestOneSheet) "测试单页 (1张)" else "全班 (${students.size}张)"
            Toast.makeText(context, "正在通过 FTP 投递打印任务至 $ip:$port ($countDesc)...", Toast.LENGTH_SHORT).show()
            try {
                val pdfFile = withContext(Dispatchers.IO) {
                    com.jf.checkin.logic.NameplatePdfGenerator.generatePdfFile(
                        context, currentCode, students, onlyFirstStudent = isTestOneSheet, fontStyle = _nameplateFontStyle.value
                    )
                }
                val result = withContext(Dispatchers.IO) {
                    com.jf.checkin.logic.FtpPrinterHelper.printPdf(pdfFile, ip, port)
                }
                result.fold(
                    onSuccess = {
                        val msg = if (isTestOneSheet) {
                            "FTP 席卡测试页 (1张) 已成功投递至复合机 $ip"
                        } else {
                            "FTP 全班席卡 (共 ${students.size} 张) 已成功投递至复合机 $ip"
                        }
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        onComplete?.invoke()
                    },
                    onFailure = { error ->
                        Toast.makeText(context, "FTP 投递失败: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                )
            } catch (e: Exception) {
                Toast.makeText(context, "FTP 投递异常: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                _isExporting.value = false
            }
        }
    }

    /**
     * 打开复合机内置 Web 管理后台 (直接打印页面)
     */
    fun openPrinterWeb(context: Context, ip: String = _printerIp.value) {
        try {
            val url = if (ip.startsWith("http://") || ip.startsWith("https://")) ip else "http://$ip"
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            Toast.makeText(context, "已打开复合机网页后台，请在页面中上传 PDF 执行直接打印", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开浏览器: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveHistory(imageFile: File, classCode: String, classTime: String, students: List<Student>) {
        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val record = AttendanceRecord(
            id = UUID.randomUUID().toString(),
            classCode = classCode,
            classTime = classTime,
            teacherName = _teacherName.value,
            recordTime = nowStr,
            totalCount = students.size,
            presentCount = students.count { it.status == AttendanceStatus.PRESENT },
            lateCount = students.count { it.status == AttendanceStatus.LATE },
            leaveCount = students.count { it.status == AttendanceStatus.LEAVE },
            absentCount = students.count { it.status == AttendanceStatus.UNCHECKED },
            imagePath = imageFile.absolutePath,
            statusSnapshot = students.associate { it.studentId to it.status.name }
        )
        historyRepository.addRecord(record)
    }
}
