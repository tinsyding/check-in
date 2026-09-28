package com.jf.checkin.ui.rollcall

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jf.checkin.data.model.AttendanceStatus
import com.jf.checkin.ui.theme.AccentGreen
import com.jf.checkin.logic.NameplateFontStyle
import com.jf.checkin.ui.theme.PrimaryBlue

enum class PrintChannel(val label: String) {
    RAW_9100("9100直发"),
    LPR_515("LPR打印"),
    FTP_21("FTP投递"),
    SYSTEM("系统打印"),
    WEB("网页端"),
    OFFICIAL("官方分享")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RollCallScreen(
    viewModel: RollCallViewModel,
    onNavigateToBrowser: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    val context = LocalContext.current
    val classList by viewModel.classList.collectAsState()
    val selectedClassCode by viewModel.selectedClassCode.collectAsState()
    val currentStudents by viewModel.currentStudents.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val teacherName by viewModel.teacherName.collectAsState()
    val isAllPresent by viewModel.isAllPresent.collectAsState()
    val nameplateFontStyle by viewModel.nameplateFontStyle.collectAsState()
    val movedStudents by viewModel.movedStudents.collectAsState()
    val movedOutOfCurrent = movedStudents.filter { it.originalClassCode == selectedClassCode }

    var showNameplateDialog by remember { mutableStateOf(false) }
    var showTeacherDialog by remember { mutableStateOf(false) }
    var inputTeacherName by remember { mutableStateOf("") }
    var pendingExportAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showAddStudentDialog by remember { mutableStateOf(false) }
    var remarkEditingStudent by remember { mutableStateOf<com.jf.checkin.data.model.Student?>(null) }
    var remarkInput by remember { mutableStateOf("") }
    var deleteConfirmStudent by remember { mutableStateOf<com.jf.checkin.data.model.Student?>(null) }
    var moveStudent by remember { mutableStateOf<com.jf.checkin.data.model.Student?>(null) }
    var showResetAllDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.reloadSettings()
    }

    fun triggerExportWithTeacherCheck(action: () -> Unit) {
        val effectiveTeacher = viewModel.getEffectiveTeacherName()
        if (effectiveTeacher.isBlank()) {
            inputTeacherName = ""
            pendingExportAction = action
            showTeacherDialog = true
        } else {
            action()
        }
    }

    val currentClassInfo = classList.find { it.classCode == selectedClassCode }
    val presentCount = currentStudents.count { it.status == AttendanceStatus.PRESENT }
    val totalCount = currentStudents.size

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
        // 顶部全局导航栏：展示标题、当前班级上课时间、任课教师
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "班级点名签到",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    if (currentClassInfo != null && currentClassInfo.classTime.isNotBlank()) {
                        val teacherText = if (teacherName.isNotBlank()) " | 教师: $teacherName" else " | 教师: 点击设置"
                        Text(
                            text = "上课时间: ${currentClassInfo.classTime}$teacherText",
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = PrimaryBlue,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable {
                                inputTeacherName = teacherName
                                pendingExportAction = null
                                showTeacherDialog = true
                            }
                        )
                    }
                }
            },
            actions = {
                // 智能自动切班按钮 (时钟图标，根据时间自动锁定正在上课的班级)
                IconButton(onClick = { viewModel.autoMatchClassNow() }) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "根据当前时间自动匹配班级",
                        tint = PrimaryBlue
                    )
                }
                // 历史记录
                IconButton(onClick = onNavigateToHistory) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "查看历史签到记录",
                        tint = Color(0xFF475569)
                    )
                }
                // 内置受控浏览器（去下载/自动更新学生名单）
                IconButton(onClick = onNavigateToBrowser) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "打开网站下载名单",
                        tint = Color(0xFF475569)
                    )
                }
                // 设置
                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "设置",
                        tint = Color(0xFF475569)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.White
            )
        )

        if (classList.isEmpty()) {
            // 空状态提示
            EmptyDataView(
                onNavigateToBrowser = onNavigateToBrowser,
                onNavigateToSettings = onNavigateToSettings
            )
        } else {
            // 自适应平板横屏与手机竖屏
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isTabletLandscape = maxWidth >= 640.dp

                if (isTabletLandscape) {
                    // 平板横屏：双栏 Master-Detail 布局
                    TabletLandscapeLayout(
                        classList = classList,
                        selectedClassCode = selectedClassCode,
                        currentStudents = currentStudents,
                        presentCount = presentCount,
                        totalCount = totalCount,
                        isAllPresent = isAllPresent,
                        isExporting = isExporting,
                        onSelectClass = { viewModel.selectClass(it) },
                        onToggleCheckIn = { viewModel.toggleCheckIn(it) },
                        onSetStatus = { id, status -> viewModel.setStatus(id, status) },
                        onEditRemark = { student ->
                            remarkInput = student.remark
                            remarkEditingStudent = student
                        },
                        onDeleteManual = { student -> deleteConfirmStudent = student },
                        onToggleAllPresent = { viewModel.toggleAllPresent() },
                        onReset = { viewModel.resetCurrentClass() },
                        onResetAll = { showResetAllDialog = true },
                        onDownloadImage = { triggerExportWithTeacherCheck { viewModel.downloadAndSaveImage(context) } },
                        onExportAndShare = { triggerExportWithTeacherCheck { viewModel.exportAndShare(context) } },
                        onShowNameplateDialog = { showNameplateDialog = true },
                        onShowAddStudentDialog = { showAddStudentDialog = true },
                        onMoveClass = { student -> moveStudent = student },
                        movedOutStudents = movedOutOfCurrent,
                        onReturnStudent = { student ->
                            val error = viewModel.returnMovedStudent(student.studentId)
                            Toast.makeText(
                                context,
                                error ?: "已调回 ${student.originalClassCode}",
                                if (error == null) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                } else {
                    // 手机竖屏：单列流式自适应布局
                    PhonePortraitLayout(
                        classList = classList,
                        selectedClassCode = selectedClassCode,
                        currentStudents = currentStudents,
                        presentCount = presentCount,
                        totalCount = totalCount,
                        isAllPresent = isAllPresent,
                        isExporting = isExporting,
                        onSelectClass = { viewModel.selectClass(it) },
                        onToggleCheckIn = { viewModel.toggleCheckIn(it) },
                        onSetStatus = { id, status -> viewModel.setStatus(id, status) },
                        onEditRemark = { student ->
                            remarkInput = student.remark
                            remarkEditingStudent = student
                        },
                        onDeleteManual = { student -> deleteConfirmStudent = student },
                        onToggleAllPresent = { viewModel.toggleAllPresent() },
                        onReset = { viewModel.resetCurrentClass() },
                        onResetAll = { showResetAllDialog = true },
                        onDownloadImage = { triggerExportWithTeacherCheck { viewModel.downloadAndSaveImage(context) } },
                        onExportAndShare = { triggerExportWithTeacherCheck { viewModel.exportAndShare(context) } },
                        onShowNameplateDialog = { showNameplateDialog = true },
                        onShowAddStudentDialog = { showAddStudentDialog = true },
                        onMoveClass = { student -> moveStudent = student },
                        movedOutStudents = movedOutOfCurrent,
                        onReturnStudent = { student ->
                            val error = viewModel.returnMovedStudent(student.studentId)
                            Toast.makeText(
                                context,
                                error ?: "已调回 ${student.originalClassCode}",
                                if (error == null) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
        }
    }

    // 全部清空二次确认弹窗
    if (showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { showResetAllDialog = false },
            title = { Text("全部清空", fontWeight = FontWeight.Bold) },
            text = { Text("将所有班级的签到状态全部清除（回到未到）？该操作不可撤销。") },
            confirmButton = {
                Button(onClick = {
                    showResetAllDialog = false
                    viewModel.resetAllStatuses()
                    Toast.makeText(context, "已清空全部签到状态", Toast.LENGTH_SHORT).show()
                }) {
                    Text("确认清空")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResetAllDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 席卡 PDF 导出与局域网直连打印弹窗
    if (showNameplateDialog) {
        val currentCode = selectedClassCode ?: ""
        val profiles by viewModel.printerProfiles.collectAsState()
        val savedIp by viewModel.printerIp.collectAsState()
        val savedPort by viewModel.printerPort.collectAsState()
        val savedSimplex by viewModel.printerSimplex.collectAsState()

        var editIp by remember(savedIp) { mutableStateOf(savedIp) }
        var editPort by remember(savedPort) { mutableStateOf(savedPort) }
        var editQueue by remember { mutableStateOf("lp") }
        var forceSimplex by remember(savedSimplex) { mutableStateOf(savedSimplex) }
        var selectedChannel by remember { mutableStateOf(PrintChannel.RAW_9100) }

        LaunchedEffect(Unit) {
            viewModel.reloadPrinterProfiles()
        }

        AlertDialog(
            onDismissRequest = { showNameplateDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Print,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text("班级席卡", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 班级与学生人数
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "班级: $currentCode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF1E293B)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = PrimaryBlue.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "共 ${currentStudents.size} 人",
                                color = PrimaryBlue,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // 字体选择 (无 emoji)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = nameplateFontStyle == NameplateFontStyle.BOLD_STANDARD,
                            onClick = { viewModel.setNameplateFontStyle(NameplateFontStyle.BOLD_STANDARD) },
                            label = { Text("粗体 (推荐)") }
                        )
                        FilterChip(
                            selected = nameplateFontStyle == NameplateFontStyle.CURSIVE,
                            onClick = { viewModel.setNameplateFontStyle(NameplateFontStyle.CURSIVE) },
                            label = { Text("行楷") }
                        )
                    }

                    // 六种打印方案通道选择 (2行3列平铺，完全自适应手机屏幕宽度，无需左右滑动)
                    val row1Channels = listOf(PrintChannel.RAW_9100, PrintChannel.LPR_515, PrintChannel.FTP_21)
                    val row2Channels = listOf(PrintChannel.SYSTEM, PrintChannel.WEB, PrintChannel.OFFICIAL)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row1Channels.forEach { ch ->
                                val isSelected = selectedChannel == ch
                                Surface(
                                    selected = isSelected,
                                    onClick = {
                                        selectedChannel = ch
                                        when (ch) {
                                            PrintChannel.RAW_9100 -> editPort = "9100"
                                            PrintChannel.LPR_515 -> editPort = "515"
                                            PrintChannel.FTP_21 -> editPort = "21"
                                            else -> {}
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) PrimaryBlue else Color(0xFFF1F5F9),
                                    contentColor = if (isSelected) Color.White else Color(0xFF334155),
                                    border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                    modifier = Modifier.weight(1f).height(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text(
                                            text = ch.label,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            row2Channels.forEach { ch ->
                                val isSelected = selectedChannel == ch
                                Surface(
                                    selected = isSelected,
                                    onClick = {
                                        selectedChannel = ch
                                        when (ch) {
                                            PrintChannel.RAW_9100 -> editPort = "9100"
                                            PrintChannel.LPR_515 -> editPort = "515"
                                            PrintChannel.FTP_21 -> editPort = "21"
                                            else -> {}
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) PrimaryBlue else Color(0xFFF1F5F9),
                                    contentColor = if (isSelected) Color.White else Color(0xFF334155),
                                    border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                    modifier = Modifier.weight(1f).height(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Text(
                                            text = ch.label,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 针对所选通道展示操作区
                    when (selectedChannel) {
                        PrintChannel.RAW_9100 -> {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF8FAFC),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (profiles.isNotEmpty()) {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(profiles) { profile ->
                                                val isSelected = (profile.ip == editIp && profile.port.toString() == editPort)
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = {
                                                        viewModel.selectPrinterProfile(profile)
                                                        editIp = profile.ip
                                                        editPort = profile.port.toString()
                                                    },
                                                    label = { Text(profile.name, fontSize = 12.sp) }
                                                )
                                            }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = editIp,
                                            onValueChange = { editIp = it },
                                            label = { Text("打印机 IP") },
                                            singleLine = true,
                                            modifier = Modifier.weight(0.68f)
                                        )
                                        OutlinedTextField(
                                            value = editPort,
                                            onValueChange = { editPort = it },
                                            label = { Text("端口") },
                                            singleLine = true,
                                            modifier = Modifier.weight(0.32f)
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { forceSimplex = !forceSimplex }
                                    ) {
                                        Checkbox(checked = forceSimplex, onCheckedChange = { forceSimplex = it })
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("单面打印", fontSize = 12.sp, color = Color(0xFF475569))
                                    }
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = {
                                        showNameplateDialog = false
                                        viewModel.directPrintNameplatePdf(
                                            context, editIp.trim(), editPort.trim(), forceSimplex, isTestOneSheet = true
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("测试打印 (1张)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Button(
                                    onClick = {
                                        showNameplateDialog = false
                                        viewModel.directPrintNameplatePdf(
                                            context, editIp.trim(), editPort.trim(), forceSimplex, isTestOneSheet = false
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("打印全班 (${currentStudents.size}张)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        PrintChannel.LPR_515 -> {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF8FAFC),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = editIp,
                                            onValueChange = { editIp = it },
                                            label = { Text("打印机 IP") },
                                            singleLine = true,
                                            modifier = Modifier.weight(0.55f)
                                        )
                                        OutlinedTextField(
                                            value = editPort,
                                            onValueChange = { editPort = it },
                                            label = { Text("端口") },
                                            singleLine = true,
                                            modifier = Modifier.weight(0.25f)
                                        )
                                        OutlinedTextField(
                                            value = editQueue,
                                            onValueChange = { editQueue = it },
                                            label = { Text("队列") },
                                            singleLine = true,
                                            modifier = Modifier.weight(0.20f)
                                        )
                                    }
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = {
                                        showNameplateDialog = false
                                        viewModel.printViaLpr(
                                            context, editIp.trim(), editPort.trim(), editQueue.trim(), isTestOneSheet = true
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("LPR测试打印 (1张)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Button(
                                    onClick = {
                                        showNameplateDialog = false
                                        viewModel.printViaLpr(
                                            context, editIp.trim(), editPort.trim(), editQueue.trim(), isTestOneSheet = false
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("LPR打印全班 (${currentStudents.size}张)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        PrintChannel.FTP_21 -> {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF8FAFC),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = editIp,
                                            onValueChange = { editIp = it },
                                            label = { Text("打印机 IP") },
                                            singleLine = true,
                                            modifier = Modifier.weight(0.7f)
                                        )
                                        OutlinedTextField(
                                            value = editPort,
                                            onValueChange = { editPort = it },
                                            label = { Text("端口") },
                                            singleLine = true,
                                            modifier = Modifier.weight(0.3f)
                                        )
                                    }
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = {
                                        showNameplateDialog = false
                                        viewModel.printViaFtp(context, editIp.trim(), editPort.trim(), isTestOneSheet = true)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("FTP测试投递 (1张)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Button(
                                    onClick = {
                                        showNameplateDialog = false
                                        viewModel.printViaFtp(context, editIp.trim(), editPort.trim(), isTestOneSheet = false)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("FTP投递全班 (${currentStudents.size}张)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        PrintChannel.SYSTEM -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(
                                    onClick = {
                                        showNameplateDialog = false
                                        viewModel.printViaSystem(context, isTestOneSheet = true)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("系统测试打印 (1张)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Button(
                                    onClick = {
                                        showNameplateDialog = false
                                        viewModel.printViaSystem(context, isTestOneSheet = false)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("系统打印全班 (${currentStudents.size}张)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        PrintChannel.WEB -> {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF8FAFC),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    OutlinedTextField(
                                        value = editIp,
                                        onValueChange = { editIp = it },
                                        label = { Text("打印机 IP / 地址") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        viewModel.openPrinterWeb(context, editIp.trim())
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("打开复合机网页", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                FilledTonalButton(
                                    onClick = {
                                        viewModel.downloadNameplatePdf(context)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("下载PDF (用于网页上传)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        PrintChannel.OFFICIAL -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        showNameplateDialog = false
                                        viewModel.exportAndShareNameplatePdf(context)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("调用官方App / 分享", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                FilledTonalButton(
                                    onClick = {
                                        viewModel.downloadNameplatePdf(context)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("下载PDF", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.downloadNameplatePdf(context)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("下载PDF")
                    }
                    Button(
                        onClick = {
                            showNameplateDialog = false
                            viewModel.exportAndShareNameplatePdf(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("分享")
                    }
                    OutlinedButton(
                        onClick = { showNameplateDialog = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("关闭")
                    }
                }
            }
        )
    }

    if (showTeacherDialog) {
        AlertDialog(
            onDismissRequest = {
                showTeacherDialog = false
                pendingExportAction?.invoke()
                pendingExportAction = null
            },
            title = {
                Text("设置任课教师姓名", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "导出的签到表需要展示任课教师姓名，请在此输入（设置后自动保存）：",
                        fontSize = 13.sp,
                        color = Color(0xFF475569)
                    )
                    OutlinedTextField(
                        value = inputTeacherName,
                        onValueChange = { inputTeacherName = it },
                        label = { Text("教师姓名 (如：张老师)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputTeacherName.isNotBlank()) {
                            viewModel.updateTeacherName(inputTeacherName)
                        }
                        showTeacherDialog = false
                        pendingExportAction?.invoke()
                        pendingExportAction = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("保存并继续导出")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showTeacherDialog = false
                        pendingExportAction?.invoke()
                        pendingExportAction = null
                    }
                ) {
                    Text("跳过")
                }
            }
        )
    }

    // 手动加人弹窗（调入当前选中班级）
    if (showAddStudentDialog) {
        var inputId by remember { mutableStateOf("") }
        var inputName by remember { mutableStateOf("") }
        var inputRemark by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddStudentDialog = false },
            title = { Text("调入学生到 $selectedClassCode", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "用于其他老师班上的学生临时调过来听课，保存后独立存储，重导名单不丢失。",
                        fontSize = 13.sp,
                        color = Color(0xFF475569)
                    )
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        label = { Text("姓名 *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputId,
                        onValueChange = { inputId = it },
                        label = { Text("学号 *（须全校唯一）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inputRemark,
                        onValueChange = { inputRemark = it },
                        label = { Text("备注（如：原XX老师班调入）") },
                        singleLine = false,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val error = viewModel.addManualStudent(inputId, inputName, inputRemark)
                        if (error == null) {
                            Toast.makeText(context, "已调入 $selectedClassCode", Toast.LENGTH_SHORT).show()
                            showAddStudentDialog = false
                        } else {
                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddStudentDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 备注编辑弹窗（所有学生通用）
    remarkEditingStudent?.let { target ->
        AlertDialog(
            onDismissRequest = { remarkEditingStudent = null },
            title = { Text("备注 · ${target.name}(${target.studentId})", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = remarkInput,
                        onValueChange = { remarkInput = it },
                        label = { Text("备注（留空即清除）") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateStudentRemark(target.studentId, remarkInput)
                        remarkEditingStudent = null
                        Toast.makeText(context, "备注已保存", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { remarkEditingStudent = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 删除调入学生确认
    deleteConfirmStudent?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteConfirmStudent = null },
            title = { Text("删除调入学生？", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = { Text("将 ${target.name}(${target.studentId}) 移出 $selectedClassCode，该操作仅影响手动调入名单。") },
            confirmButton = {
                Button(
                    onClick = {
                        val ok = viewModel.deleteManualStudent(target.studentId)
                        deleteConfirmStudent = null
                        Toast.makeText(
                            context,
                            if (ok) "已删除 ${target.name}" else "删除失败：仅支持删除手动调入的学生",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteConfirmStudent = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 跨班调动弹窗：把学生调到其他班级
    moveStudent?.let { target ->
        val candidates = classList.filter { it.classCode != target.classCode }
        var moveTargetCode by remember(target.studentId) {
            mutableStateOf(candidates.firstOrNull()?.classCode ?: "")
        }
        AlertDialog(
            onDismissRequest = { moveStudent = null },
            title = { Text("调班 · ${target.name}(${target.studentId})", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = buildString {
                            append("当前班级：${target.classCode}")
                            if (target.originalClassCode.isNotBlank() && target.originalClassCode != target.classCode) {
                                append("（原 ${target.originalClassCode} 班）")
                            }
                            append("，请选择目标班级。调动后自动继承目标班上课时间，重导名单不丢失。")
                        },
                        fontSize = 13.sp,
                        color = Color(0xFF475569)
                    )
                    if (candidates.isEmpty()) {
                        Text(
                            text = "暂无其他班级可选。",
                            fontSize = 13.sp,
                            color = Color(0xFFEF4444)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            candidates.forEach { info ->
                                val isSelected = (info.classCode == moveTargetCode)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) PrimaryBlue.copy(alpha = 0.1f) else Color(0xFFF8FAFC),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) PrimaryBlue else Color(0xFFE2E8F0)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { moveTargetCode = info.classCode }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = info.classCode,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 14.sp,
                                                color = if (isSelected) PrimaryBlue else Color(0xFF1E293B)
                                            )
                                            if (info.classTime.isNotBlank()) {
                                                Text(
                                                    text = info.classTime,
                                                    fontSize = 11.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "${info.presentCount}/${info.totalCount}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val error = viewModel.moveStudent(target.studentId, moveTargetCode)
                        if (error == null) {
                            Toast.makeText(context, "已调到 $moveTargetCode", Toast.LENGTH_SHORT).show()
                            moveStudent = null
                        } else {
                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = candidates.isNotEmpty() && moveTargetCode.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("确认调班")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { moveStudent = null }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 平板横屏布局 (左侧班级与概览，右侧大网格点名)
 */
@Composable
private fun TabletLandscapeLayout(
    classList: List<ClassInfo>,
    selectedClassCode: String?,
    currentStudents: List<com.jf.checkin.data.model.Student>,
    presentCount: Int,
    totalCount: Int,
    isAllPresent: Boolean,
    isExporting: Boolean,
    onSelectClass: (String) -> Unit,
    onToggleCheckIn: (String) -> Unit,
    onSetStatus: (String, AttendanceStatus) -> Unit,
    onEditRemark: (com.jf.checkin.data.model.Student) -> Unit,
    onDeleteManual: (com.jf.checkin.data.model.Student) -> Unit,
    onToggleAllPresent: () -> Unit,
    onReset: () -> Unit,
    onResetAll: () -> Unit,
    onDownloadImage: () -> Unit,
    onExportAndShare: () -> Unit,
    onShowNameplateDialog: () -> Unit,
    onShowAddStudentDialog: () -> Unit,
    onMoveClass: (com.jf.checkin.data.model.Student) -> Unit,
    movedOutStudents: List<com.jf.checkin.data.model.Student>,
    onReturnStudent: (com.jf.checkin.data.model.Student) -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        // 左侧班级导航栏 (280dp)
        Surface(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight(),
            color = Color.White,
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "班级列表",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF1E293B),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(classList) { info ->
                        val isSelected = info.classCode == selectedClassCode
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                            border = androidx.compose.foundation.BorderStroke(
                                1.5.dp,
                                if (isSelected) PrimaryBlue else Color(0xFFE2E8F0)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectClass(info.classCode) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = info.classCode,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (isSelected) PrimaryBlue else Color(0xFF1E293B)
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = info.classTime,
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LinearProgressIndicator(
                                        progress = { if (info.totalCount > 0) info.presentCount.toFloat() / info.totalCount else 0f },
                                        color = AccentGreen,
                                        trackColor = Color(0xFFE2E8F0),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(5.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${info.presentCount}/${info.totalCount}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF475569)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 右侧点名主区域
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            // 操作工具条
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "实到: $presentCount / $totalCount",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onReset) {
                        Text("重置")
                    }
                    OutlinedButton(onClick = onResetAll) {
                        Text("全部清空")
                    }
                    // 手动加人（跨班调入）
                    OutlinedButton(onClick = onShowAddStudentDialog) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("加人")
                    }
                    // 一键全到 / 一键取消全到 动态切换
                    FilledTonalButton(onClick = onToggleAllPresent) {
                        Icon(
                            imageVector = if (isAllPresent) Icons.Default.Close else Icons.Default.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isAllPresent) "取消全到" else "一键全到")
                    }
                    // 席卡 PDF 按钮
                    OutlinedButton(
                        onClick = onShowNameplateDialog,
                        enabled = !isExporting
                    ) {
                        Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("席卡PDF")
                    }
                    // 下载长图按钮
                    OutlinedButton(
                        onClick = onDownloadImage,
                        enabled = !isExporting
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("下载长图")
                    }
                    // 分享发送按钮
                    Button(
                        onClick = onExportAndShare,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        enabled = !isExporting
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("发长图")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 临时调出提示（人在外班，原班留显示 + 一键调回）
            MovedOutNotice(
                students = movedOutStudents,
                onReturnStudent = onReturnStudent
            )

            // 学生大字点名网格 (横屏自适应 4~5 列)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(currentStudents, key = { it.studentId }) { student ->
                    StudentCard(
                        student = student,
                        onToggleCheckIn = { onToggleCheckIn(student.studentId) },
                        onSetStatus = { status -> onSetStatus(student.studentId, status) },
                        onEditRemark = { onEditRemark(student) },
                        onDeleteManual = if (student.isManual) ({ onDeleteManual(student) }) else null,
                        onMoveClass = { onMoveClass(student) }
                    )
                }
            }
        }
    }
}

/**
 * 手机竖屏布局 (顶部班级 Tab，中部 2 列大字卡片，底部固定快速操作)
 */
@Composable
private fun PhonePortraitLayout(
    classList: List<ClassInfo>,
    selectedClassCode: String?,
    currentStudents: List<com.jf.checkin.data.model.Student>,
    presentCount: Int,
    totalCount: Int,
    isAllPresent: Boolean,
    isExporting: Boolean,
    onSelectClass: (String) -> Unit,
    onToggleCheckIn: (String) -> Unit,
    onSetStatus: (String, AttendanceStatus) -> Unit,
    onEditRemark: (com.jf.checkin.data.model.Student) -> Unit,
    onDeleteManual: (com.jf.checkin.data.model.Student) -> Unit,
    onToggleAllPresent: () -> Unit,
    onReset: () -> Unit,
    onResetAll: () -> Unit,
    onDownloadImage: () -> Unit,
    onExportAndShare: () -> Unit,
    onShowNameplateDialog: () -> Unit,
    onShowAddStudentDialog: () -> Unit,
    onMoveClass: (com.jf.checkin.data.model.Student) -> Unit,
    movedOutStudents: List<com.jf.checkin.data.model.Student>,
    onReturnStudent: (com.jf.checkin.data.model.Student) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 班级滑动选择 Tab
        val selectedIndex = classList.indexOfFirst { it.classCode == selectedClassCode }.coerceAtLeast(0)
        ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            edgePadding = 12.dp,
            containerColor = Color.White
        ) {
            classList.forEachIndexed { index, info ->
                Tab(
                    selected = index == selectedIndex,
                    onClick = { onSelectClass(info.classCode) },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = info.classCode,
                                fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = "${info.presentCount}/${info.totalCount}",
                                fontSize = 11.sp,
                                color = if (index == selectedIndex) PrimaryBlue else Color(0xFF94A3B8)
                            )
                        }
                    }
                )
            }
        }

        // 快速统计与一键操作栏
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$presentCount/$totalCount",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.weight(1f))
                // 手动加人
                OutlinedButton(
                    onClick = onShowAddStudentDialog,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 7.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("加人", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
                // 一键全到 / 取消全到 动态切换
                FilledTonalButton(
                    onClick = onToggleAllPresent,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 7.dp)
                ) {
                    Text(if (isAllPresent) "取消" else "全到", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
                // 全部清空（所有班级签到状态回到未到）
                OutlinedButton(
                    onClick = onResetAll,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 7.dp)
                ) {
                    Text("清空", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
                // 席卡 PDF 按钮
                OutlinedButton(
                    onClick = onShowNameplateDialog,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 7.dp),
                    enabled = !isExporting
                ) {
                    Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("席卡", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
                OutlinedButton(
                    onClick = onDownloadImage,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 7.dp),
                    enabled = !isExporting
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("存相册", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = onExportAndShare,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    enabled = !isExporting
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("发长图", fontSize = 12.sp)
                    }
                }
            }
        }

        // 手机竖屏 2 列学生卡片列表
        MovedOutNotice(
            students = movedOutStudents,
            onReturnStudent = onReturnStudent,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(currentStudents, key = { it.studentId }) { student ->
                StudentCard(
                    student = student,
                    onToggleCheckIn = { onToggleCheckIn(student.studentId) },
                    onSetStatus = { status -> onSetStatus(student.studentId, status) },
                    onEditRemark = { onEditRemark(student) },
                    onDeleteManual = if (student.isManual) ({ onDeleteManual(student) }) else null,
                    onMoveClass = { onMoveClass(student) }
                )
            }
        }
    }
}

/**
 * 临时调出提示条：原班留显示（调到哪去了 + 一键调回），为空时不占位。
 */
@Composable
private fun MovedOutNotice(
    students: List<com.jf.checkin.data.model.Student>,
    onReturnStudent: (com.jf.checkin.data.model.Student) -> Unit,
    modifier: Modifier = Modifier
) {
    if (students.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFFEFCE8),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = "临时调出（${students.size}人）",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF92400E)
            )
            Spacer(modifier = Modifier.height(4.dp))
            students.forEach { student ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${student.name}(${student.studentId}) → ${student.classCode}",
                        fontSize = 13.sp,
                        color = Color(0xFF78350F),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { onReturnStudent(student) },
                        modifier = Modifier.height(30.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("调回", fontSize = 12.sp)
                    }
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun EmptyDataView(
    onNavigateToBrowser: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "暂无学生名单数据",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF334155)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "请点击下方按钮打开网站下载名单，或在设置中手动导入 CSV 文件",
                fontSize = 14.sp,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onNavigateToBrowser,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("打开内置网站下载")
                }
                OutlinedButton(onClick = onNavigateToSettings) {
                    Text("前往设置导入")
                }
            }
        }
    }
}
