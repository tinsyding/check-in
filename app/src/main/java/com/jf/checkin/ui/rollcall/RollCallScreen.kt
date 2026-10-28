package com.jf.checkin.ui.rollcall

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import com.jf.checkin.data.parser.StudentCsvParser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TextButton
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
import androidx.core.content.ContextCompat
import com.jf.checkin.data.model.AttendanceStatus
import com.jf.checkin.data.remote.TmsCoursePlan
import com.jf.checkin.data.remote.TmsCourseResult
import com.jf.checkin.data.remote.TmsSyncPreview
import com.jf.checkin.ui.theme.AccentGreen
import com.jf.checkin.logic.NameplateFontStyle
import com.jf.checkin.ui.theme.PrimaryBlue
import java.time.LocalDate
import java.util.Locale

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
    val tmsSyncState by viewModel.tmsSyncState.collectAsState()
    val scheduledSync by viewModel.scheduledSync.collectAsState()
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
    var showTmsAfterShareDialog by remember { mutableStateOf(false) }
    var showScheduledDetailDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedClassCode) {
        viewModel.refreshScheduledSync()
    }

    // Android 8/9 保存相册/下载目录仍需 WRITE_EXTERNAL_STORAGE，按需申请后继续原操作
    var pendingStorageAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingStorageAction?.invoke()
        } else {
            Toast.makeText(
                context,
                "未授予存储权限，无法保存到相册或下载目录",
                Toast.LENGTH_LONG
            ).show()
        }
        pendingStorageAction = null
    }

    fun runWithStoragePermission(action: () -> Unit) {
        val needsPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED

        if (needsPermission) {
            pendingStorageAction = action
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            action()
        }
    }

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
    val onlineCount = currentStudents.count { it.status == AttendanceStatus.ONLINE }
    val totalCount = currentStudents.size

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
        // 顶部全局导航栏：展示标题、当前班级上课时间、任课教师
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "班级点名签到",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                // 校管家自动考勤：先预览，确认后才提交
                IconButton(onClick = { viewModel.startTmsSync(LocalDate.now()) }) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "自动同步校管家考勤",
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

        // 预约考勤等待提醒条
        if (scheduledSync != null && scheduledSync?.classCode == selectedClassCode) {
            Surface(
                color = Color(0xFFEFF6FF),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showScheduledDetailDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "已预约在 ${scheduledSync?.targetTime} 上课后自动考勤",
                            color = PrimaryBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = "查看 / 取消",
                        color = PrimaryBlue,
                        fontSize = 12.sp,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                    )
                }
            }
        }

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
                        onlineCount = onlineCount,
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
                        onDownloadImage = {
                            triggerExportWithTeacherCheck {
                                runWithStoragePermission { viewModel.downloadAndSaveImage(context) }
                            }
                        },
                        onExportAndShare = {
                            triggerExportWithTeacherCheck {
                                viewModel.exportAndShare(context) {
                                    if (viewModel.shouldPromptTmsSync()) {
                                        showTmsAfterShareDialog = true
                                    }
                                }
                            }
                        },
                        onShowNameplateDialog = { showNameplateDialog = true },
                        onShowAddStudentDialog = { showAddStudentDialog = true },
                        onStartTmsSync = { viewModel.startTmsSync(LocalDate.now()) },
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
                        onlineCount = onlineCount,
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
                        onDownloadImage = {
                            triggerExportWithTeacherCheck {
                                runWithStoragePermission { viewModel.downloadAndSaveImage(context) }
                            }
                        },
                        onExportAndShare = {
                            triggerExportWithTeacherCheck {
                                viewModel.exportAndShare(context) {
                                    if (viewModel.shouldPromptTmsSync()) {
                                        showTmsAfterShareDialog = true
                                    }
                                }
                            }
                        },
                        onShowNameplateDialog = { showNameplateDialog = true },
                        onShowAddStudentDialog = { showAddStudentDialog = true },
                        onStartTmsSync = { viewModel.startTmsSync(LocalDate.now()) },
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

    // 校管家自动考勤：加载 / 预览 / 提交结果 / 错误 / 定时预约
    TmsSyncDialogs(
        state = tmsSyncState,
        onDismiss = { viewModel.clearTmsSyncState() },
        onReload = { date -> viewModel.startTmsSync(date) },
        onConfirm = { viewModel.submitTmsSync(context) },
        onCancelSchedule = { code -> viewModel.cancelScheduledSync(context, code) },
        onNavigateToSettings = {
            viewModel.clearTmsSyncState()
            onNavigateToSettings()
        }
    )

    // 点击预约考勤条弹出的详情与取消弹窗
    if (showScheduledDetailDialog && scheduledSync != null) {
        val currentScheduled = scheduledSync
        AlertDialog(
            onDismissRequest = { showScheduledDetailDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("考勤已预约", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "班级 ${currentScheduled?.classCode} 已预约在 ${currentScheduled?.targetTime} 上课后自动提交考勤至校管家。",
                        fontSize = 14.sp,
                        color = Color(0xFF1E293B)
                    )
                    Text(
                        text = "您可以随时退出应用，系统在到达上课时间后会在后台自动执行同步并发送通知。若在此之前修改了学生签到状态，后台将自动按最新点名结果提交。",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showScheduledDetailDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("我知道了")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        val code = currentScheduled?.classCode
                        if (code != null) {
                            viewModel.cancelScheduledSync(context, code)
                        }
                        showScheduledDetailDialog = false
                    }
                ) {
                    Text("取消预约")
                }
            }
        )
    }

    // 分享签到长图后，询问是否继续把当前班考勤同步到校管家。
    if (showTmsAfterShareDialog) {
        val hasTmsCredentials = viewModel.hasTmsCredentials()
        AlertDialog(
            onDismissRequest = { showTmsAfterShareDialog = false },
            icon = {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = if (hasTmsCredentials) "同步校管家考勤？" else "尚未配置校管家账号",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (hasTmsCredentials) {
                        "签到表已生成。是否现在把当前班级的考勤同步到校管家？\n\n同步前会先显示预览，确认后才会提交。"
                    } else {
                        "签到表已生成，但还没有填写校管家账号密码。请先到设置中完成配置，之后就能在分享后直接同步。"
                    },
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTmsAfterShareDialog = false
                        if (hasTmsCredentials) {
                            viewModel.startTmsSync(LocalDate.now())
                        } else {
                            onNavigateToSettings()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text(if (hasTmsCredentials) "同步校管家" else "去设置")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTmsAfterShareDialog = false }) {
                    Text("暂不")
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
                                        runWithStoragePermission { viewModel.downloadNameplatePdf(context) }
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
                                        runWithStoragePermission { viewModel.downloadNameplatePdf(context) }
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
                            runWithStoragePermission { viewModel.downloadNameplatePdf(context) }
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

@Composable
private fun TmsSyncDialogs(
    state: TmsSyncState,
    onDismiss: () -> Unit,
    onReload: (LocalDate) -> Unit,
    onConfirm: () -> Unit,
    onCancelSchedule: (String) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    when (state) {
        TmsSyncState.Idle -> Unit
        TmsSyncState.Loading -> TmsBusyDialog(
            title = "正在连接校管家",
            message = "正在登录并匹配当天课程，请稍候…"
        )
        TmsSyncState.Submitting -> TmsBusyDialog(
            title = "正在提交考勤",
            message = "正在把点名结果写入校管家，请勿关闭应用…"
        )
        is TmsSyncState.Preview -> TmsPreviewDialog(
            preview = state.preview,
            onDismiss = onDismiss,
            onReload = onReload,
            onConfirm = onConfirm
        )
        is TmsSyncState.Scheduled -> TmsScheduledDialog(
            state = state,
            onDismiss = onDismiss,
            onCancelSchedule = onCancelSchedule
        )
        is TmsSyncState.Success -> TmsSuccessDialog(
            results = state.results,
            onDismiss = onDismiss
        )
        is TmsSyncState.Error -> TmsErrorDialog(
            state = state,
            onDismiss = onDismiss,
            onNavigateToSettings = onNavigateToSettings
        )
    }
}

@Composable
private fun TmsScheduledDialog(
    state: TmsSyncState.Scheduled,
    onDismiss: () -> Unit,
    onCancelSchedule: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text("考勤已预约", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "班级 ${state.classCode} 的考勤已加入后台等待队列，将于上课时间（${state.startTime}）自动提交至校管家。",
                    fontSize = 14.sp,
                    color = Color(0xFF1E293B)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF8FAFC),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("• 您可以退出应用，到达上课时间后台将自动执行提交。", fontSize = 12.sp, color = Color(0xFF64748B))
                        Text("• 考勤完成后将通过系统通知栏提醒结果。", fontSize = 12.sp, color = Color(0xFF64748B))
                        Text("• 等待期间若有点名变动，后台将以最新点名数据为准。", fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("我知道了")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = {
                    onCancelSchedule(state.classCode)
                    onDismiss()
                }
            ) {
                Text("取消预约")
            }
        }
    )
}

@Composable
private fun TmsBusyDialog(title: String, message: String) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(message, fontSize = 14.sp)
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun TmsPreviewDialog(
    preview: TmsSyncPreview,
    onDismiss: () -> Unit,
    onReload: (LocalDate) -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    var dateInput by remember(preview.date) { mutableStateOf(preview.date) }
    val dateChanged = dateInput.trim() != preview.date
    val futureStart = preview.earliestFutureStartTime()
    val targetTimeStr = futureStart?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    val reload = {
        val parsed = runCatching { LocalDate.parse(dateInput.trim()) }.getOrNull()
        if (parsed == null) {
            Toast.makeText(
                context,
                "日期格式应为 yyyy-MM-dd",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            onReload(parsed)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("校管家考勤同步预览", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = dateInput,
                        onValueChange = { dateInput = it },
                        label = { Text("同步日期") },
                        placeholder = { Text("yyyy-MM-dd") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = reload) {
                        Text("重新匹配")
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "默认同步今天；补课或跨天点名可修改日期后重新匹配。",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
                if (dateChanged) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "日期已修改，请先点「重新匹配」，否则确认按钮不会提交。",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFB45309)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (futureStart != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFEFF6FF),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color(0xFFBFDBFE)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "尚未到达上课时间（课程将于 $targetTimeStr 开始）。点击「预约到时考勤」后，系统将在后台等待至 $targetTimeStr 自动提交考勤至校管家，期间无需保持应用打开。",
                                fontSize = 12.sp,
                                color = Color(0xFF1E40AF),
                                lineHeight = 18.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (preview.plans.isEmpty()) {
                    Text(
                        text = "没有找到可同步的课程（α1/β1/β2/β3）。",
                        fontSize = 13.sp,
                        color = Color(0xFFB45309)
                    )
                }
                preview.plans.forEach { plan ->
                    TmsPlanCard(plan)
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (preview.warnings.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFFFBEB),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color(0xFFFCD34D)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "需要注意",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF92400E)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            preview.warnings.forEach { warning ->
                                Text(
                                    text = "• $warning",
                                    fontSize = 12.sp,
                                    color = Color(0xFF92400E)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Text(
                    text = "规则：α1/β1/β2/β3 均会同步；所有已匹配学员（含未到、请假）统一按出勤提交并计费；已点名课程自动跳过；不会发送微信/短信通知。",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        },
        confirmButton = {
            when {
                dateChanged -> {
                    Button(
                        onClick = reload,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text("按新日期重新匹配")
                    }
                }
                preview.canSubmit -> {
                    val isScheduled = futureStart != null
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Icon(
                            imageVector = if (isScheduled) Icons.Default.Schedule else Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val label = if (isScheduled) {
                            "预约到时考勤 ($targetTimeStr)"
                        } else {
                            "确认同步 (${preview.submittableCount})"
                        }
                        Text(label, fontWeight = FontWeight.Bold)
                    }
                }
                else -> {
                    Button(onClick = onDismiss) {
                        Text("关闭")
                    }
                }
            }
        },
        dismissButton = if (preview.canSubmit || dateChanged) {
            {
                OutlinedButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        } else {
            null
        }
    )
}

@Composable
private fun TmsPlanCard(plan: TmsCoursePlan) {
    val canSubmit = plan.canSubmit
    val cardColor = when {
        canSubmit -> Color(0xFFF0FDF4)
        plan.alreadyPointed -> Color(0xFFF8FAFC)
        else -> Color(0xFFFFFBEB)
    }
    val borderColor = when {
        canSubmit -> Color(0xFF86EFAC)
        plan.alreadyPointed -> Color(0xFFE2E8F0)
        else -> Color(0xFFFCD34D)
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = cardColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plan.className,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF1E293B),
                    modifier = Modifier.weight(1f)
                )
                if (plan.displayTime.isNotBlank()) {
                    Text(
                        text = plan.displayTime,
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            val message = plan.message
            if (message != null) {
                Text(
                    text = message,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (plan.alreadyPointed) Color(0xFF64748B) else Color(0xFFB45309)
                )
            } else {
                Text(
                    text = "已匹配 ${plan.matches.size} 人：本地已到 ${plan.presentCount}（含迟到 ${plan.lateCount}） / 请假 ${plan.leaveCount} / 未到 ${plan.absentCount}",
                    fontSize = 12.sp,
                    color = Color(0xFF334155)
                )
                Text(
                    text = "提交时全部按出勤并计费",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF15803D)
                )
                if (plan.unmatchedAppStudents.isNotEmpty()) {
                    Text(
                        text = "未匹配、不会提交：${plan.unmatchedAppStudents.joinToString("、") { it.name }}",
                        fontSize = 11.sp,
                        color = Color(0xFFB45309)
                    )
                }
                if (plan.tmsOnlyStudentNames.isNotEmpty()) {
                    Text(
                        text = "校管家多出 ${plan.tmsOnlyStudentNames.size} 人，将保持原状态。",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
                Text(
                    text = "每人课消：${formatTmsCost(plan.costAmount, plan.unitCode)}",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

private fun formatTmsCost(cost: Double, unitCode: Int): String {
    val value = if (unitCode == 1) cost / 60.0 else cost
    val text = if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.ROOT, "%.2f", value)
    }
    val unit = when (unitCode) {
        1 -> "小时"
        2 -> "次"
        3 -> "天"
        else -> ""
    }
    return if (unit.isBlank()) text else "$text $unit"
}

@Composable
private fun TmsSuccessDialog(
    results: List<TmsCourseResult>,
    onDismiss: () -> Unit
) {
    val successCount = results.count { it.success }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when {
                    results.isNotEmpty() && successCount == results.size -> "校管家考勤已同步"
                    successCount > 0 -> "部分课程同步成功"
                    else -> "同步未完成"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (results.isEmpty()) {
                    Text("没有可提交的课程。", fontSize = 13.sp)
                }
                results.forEach { result ->
                    Text(
                        text = "${if (result.success) "成功" else "失败"}：${result.className}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (result.success) Color(0xFF15803D) else Color(0xFFB91C1C)
                    )
                    Text(
                        text = result.message,
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (successCount > 0) {
                    Text(
                        text = "建议再到校管家网页核对一次点名结果。",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("完成")
            }
        }
    )
}

@Composable
private fun TmsErrorDialog(
    state: TmsSyncState.Error,
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("无法同步校管家", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                text = state.message,
                fontSize = 14.sp,
                color = Color(0xFF334155)
            )
        },
        confirmButton = {
            if (state.needsSettings) {
                Button(
                    onClick = onNavigateToSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("去设置")
                }
            } else {
                Button(onClick = onDismiss) {
                    Text("知道了")
                }
            }
        },
        dismissButton = if (state.needsSettings) {
            {
                OutlinedButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        } else {
            null
        }
    )
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
    onlineCount: Int = 0,
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
    onStartTmsSync: () -> Unit,
    onMoveClass: (com.jf.checkin.data.model.Student) -> Unit,
    movedOutStudents: List<com.jf.checkin.data.model.Student>,
    onReturnStudent: (com.jf.checkin.data.model.Student) -> Unit
) {
    val context = LocalContext.current
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
                                        progress = {
                                            val offlineTotal = info.offlineCount
                                            if (offlineTotal > 0) info.presentCount.toFloat() / offlineTotal else if (info.totalCount > 0) 1f else 0f
                                        },
                                        color = AccentGreen,
                                        trackColor = Color(0xFFE2E8F0),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(5.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (info.onlineCount > 0) "${info.presentCount}/${info.offlineCount}" else "${info.presentCount}/${info.totalCount}",
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
                    val offlineTotal = (totalCount - onlineCount).coerceAtLeast(0)
                    val countText = if (onlineCount > 0) {
                        "实到: $presentCount / 线下应到 $offlineTotal (线上: $onlineCount)"
                    } else {
                        "实到: $presentCount / $totalCount"
                    }
                    Text(
                        text = countText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                }

                var showMoreMenu by remember { mutableStateOf(false) }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    // 分享发送按钮（核心主要操作，高亮突出，永不遮挡）
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
                            Text("发长图", fontWeight = FontWeight.Bold)
                        }
                    }
                    // 更多操作下拉菜单
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多操作", tint = Color(0xFF475569))
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("下载长图 (存相册)") },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = PrimaryBlue) },
                                onClick = {
                                    showMoreMenu = false
                                    onDownloadImage()
                                },
                                enabled = !isExporting
                            )
                            DropdownMenuItem(
                                text = { Text("自动同步校管家考勤") },
                                leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null, tint = PrimaryBlue) },
                                onClick = {
                                    showMoreMenu = false
                                    onStartTmsSync()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("重置本班签到") },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFF59E0B)) },
                                onClick = {
                                    showMoreMenu = false
                                    onReset()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("全部清空 (所有班)", color = Color(0xFFEF4444)) },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444)) },
                                onClick = {
                                    showMoreMenu = false
                                    onResetAll()
                                }
                            )
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

            // 线上与转线上学生提示（免签）
            OnlineStudentsNotice(
                students = currentStudents.filter { it.status == AttendanceStatus.ONLINE }
            )

            // 线下学生优先排前，线上学生排在末尾
            val sortedStudents = remember(currentStudents) {
                currentStudents.sortedWith(compareBy { it.status == AttendanceStatus.ONLINE })
            }

            // 学生大字点名网格 (横屏自适应 4~5 列)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sortedStudents, key = { it.studentId }) { student ->
                    StudentCard(
                        student = student,
                        onToggleCheckIn = { onToggleCheckIn(student.studentId) },
                        onSetStatus = { status -> onSetStatus(student.studentId, status) },
                        onEditRemark = { onEditRemark(student) },
                        onDeleteManual = if (student.isManual) ({ onDeleteManual(student) }) else null,
                        onMoveClass = { onMoveClass(student) },
                        onOnlineClick = {
                            Toast.makeText(
                                context,
                                "${student.name} 为线上学生，无需签到。如需更改状态请长按卡片",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
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
    onlineCount: Int = 0,
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
    onStartTmsSync: () -> Unit,
    onMoveClass: (com.jf.checkin.data.model.Student) -> Unit,
    movedOutStudents: List<com.jf.checkin.data.model.Student>,
    onReturnStudent: (com.jf.checkin.data.model.Student) -> Unit
) {
    val context = LocalContext.current
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
                                text = if (info.onlineCount > 0) "${info.presentCount}/${info.offlineCount}" else "${info.presentCount}/${info.totalCount}",
                                fontSize = 11.sp,
                                color = if (index == selectedIndex) PrimaryBlue else Color(0xFF94A3B8)
                            )
                        }
                    }
                )
            }
        }

        // 快速统计与一键操作栏
        var showMoreMenu by remember { mutableStateOf(false) }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val offlineTotal = (totalCount - onlineCount).coerceAtLeast(0)
                val countText = if (onlineCount > 0) {
                    "$presentCount/$offlineTotal (线上$onlineCount)"
                } else {
                    "$presentCount/$totalCount"
                }
                Text(
                    text = countText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.weight(1f))

                // 手动加人
                OutlinedButton(
                    onClick = onShowAddStudentDialog,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("加人", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(6.dp))

                // 一键全到 / 取消全到 动态切换
                FilledTonalButton(
                    onClick = onToggleAllPresent,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = if (isAllPresent) Icons.Default.Close else Icons.Default.DoneAll,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(if (isAllPresent) "取消" else "全到", fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(6.dp))

                // 分享发送按钮（主要核心操作，高亮蓝色，绝不遮挡）
                Button(
                    onClick = onExportAndShare,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    enabled = !isExporting
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("发长图", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(2.dp))

                // 更多操作下拉菜单
                Box {
                    IconButton(
                        onClick = { showMoreMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多操作", tint = Color(0xFF475569))
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("存长图到相册") },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = PrimaryBlue) },
                            onClick = {
                                showMoreMenu = false
                                onDownloadImage()
                            },
                            enabled = !isExporting
                        )
                        DropdownMenuItem(
                            text = { Text("席卡打印 / PDF") },
                            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = Color(0xFF0F766E)) },
                            onClick = {
                                showMoreMenu = false
                                onShowNameplateDialog()
                            },
                            enabled = !isExporting
                        )
                        DropdownMenuItem(
                            text = { Text("自动同步校管家考勤") },
                            leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null, tint = PrimaryBlue) },
                            onClick = {
                                showMoreMenu = false
                                onStartTmsSync()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("重置本班签到") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFF59E0B)) },
                            onClick = {
                                showMoreMenu = false
                                onReset()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("全部清空 (所有班)", color = Color(0xFFEF4444)) },
                            leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444)) },
                            onClick = {
                                showMoreMenu = false
                                onResetAll()
                            }
                        )
                    }
                }
            }
        }

        // 手机竖屏 2 列学生卡片列表
        MovedOutNotice(
            students = movedOutStudents,
            onReturnStudent = onReturnStudent,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        OnlineStudentsNotice(
            students = currentStudents.filter { it.status == AttendanceStatus.ONLINE },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        // 线下学生优先排前，线上学生排在末尾
        val sortedStudents = remember(currentStudents) {
            currentStudents.sortedWith(compareBy { it.status == AttendanceStatus.ONLINE })
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(sortedStudents, key = { it.studentId }) { student ->
                StudentCard(
                    student = student,
                    onToggleCheckIn = { onToggleCheckIn(student.studentId) },
                    onSetStatus = { status -> onSetStatus(student.studentId, status) },
                    onEditRemark = { onEditRemark(student) },
                    onDeleteManual = if (student.isManual) ({ onDeleteManual(student) }) else null,
                    onMoveClass = { onMoveClass(student) },
                    onOnlineClick = {
                        Toast.makeText(
                            context,
                            "${student.name} 为线上学生，无需签到。如需更改状态请长按卡片",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
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

/**
 * 线上学生提示条：线上听课与转线上人员（免签），展示姓名与状态标签，为空时不占位。
 */
@Composable
private fun OnlineStudentsNotice(
    students: List<com.jf.checkin.data.model.Student>,
    modifier: Modifier = Modifier
) {
    if (students.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF3E8FF),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD8B4FE)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = Color(0xFF7E22CE),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "线上/转线上学生（免签，共 ${students.size} 人）",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6B21A8)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val itemsText = students.joinToString("、") { student ->
                val tag = if (StudentCsvParser.isOfflineStudent(student)) "转线上" else "线上"
                "${student.name} [${tag}]"
            }
            Text(
                text = itemsText,
                fontSize = 12.sp,
                color = Color(0xFF581C87),
                lineHeight = 18.sp
            )
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
