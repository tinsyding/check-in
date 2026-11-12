package com.jf.checkin.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.jf.checkin.data.parser.StudentCsvParser
import com.jf.checkin.data.repository.StudentRepository
import com.jf.checkin.data.repository.UserPrefRepository
import com.jf.checkin.logic.TmsReminderScheduler
import com.jf.checkin.ui.theme.Danger
import com.jf.checkin.ui.theme.Hairline
import com.jf.checkin.ui.theme.InkOnDark
import com.jf.checkin.ui.theme.InkPrimary
import com.jf.checkin.ui.theme.InkSecondary
import com.jf.checkin.ui.theme.InkTertiary
import com.jf.checkin.ui.theme.PaperBackground
import com.jf.checkin.ui.theme.PaperSunken
import com.jf.checkin.ui.theme.PaperSurface
import com.jf.checkin.ui.theme.Radii
import com.jf.checkin.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefRepository: UserPrefRepository,
    studentRepository: StudentRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var teacherName by remember { mutableStateOf(prefRepository.teacherName) }
    var username by remember { mutableStateOf(prefRepository.username) }
    var password by remember { mutableStateOf(prefRepository.password) }
    var showPassword by remember { mutableStateOf(false) }

    var tmsUsername by remember { mutableStateOf(prefRepository.tmsUsername) }
    var tmsPassword by remember { mutableStateOf(prefRepository.tmsPassword) }
    var showTmsPassword by remember { mutableStateOf(false) }
    var tmsReminderEnabled by remember { mutableStateOf(prefRepository.tmsDailyReminderEnabled) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            tmsReminderEnabled = true
            prefRepository.tmsDailyReminderEnabled = true
            TmsReminderScheduler.schedule(context)
            Toast.makeText(context, "已开启每天 18:00 的未同步提醒", Toast.LENGTH_SHORT).show()
        } else {
            tmsReminderEnabled = false
            prefRepository.tmsDailyReminderEnabled = false
            TmsReminderScheduler.cancel(context)
            Toast.makeText(context, "未授予通知权限，无法显示每日提醒", Toast.LENGTH_LONG).show()
        }
    }

    fun setTmsReminderEnabled(enabled: Boolean) {
        if (!enabled) {
            tmsReminderEnabled = false
            prefRepository.tmsDailyReminderEnabled = false
            TmsReminderScheduler.cancel(context)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        tmsReminderEnabled = true
        prefRepository.tmsDailyReminderEnabled = true
        TmsReminderScheduler.schedule(context)
    }

    var printerProfiles by remember { mutableStateOf(prefRepository.getPrinterProfiles()) }
    var selectedPrinterId by remember {
        mutableStateOf(prefRepository.selectedPrinterId.ifBlank { printerProfiles.firstOrNull()?.id ?: "" })
    }
    var printerSimplex by remember { mutableStateOf(prefRepository.printerSimplex) }
    var showAddPrinterDialog by remember { mutableStateOf(false) }
    var testingPrinterId by remember { mutableStateOf<String?>(null) }

    val students by studentRepository.students.collectAsState()
    val lastImportTime = prefRepository.lastImportTime

    // 本地 CSV 选择器（备用手动导入）
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val csvText = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
                        } ?: ""
                    }

                    if (csvText.isNotBlank()) {
                        studentRepository.loadFromCsv(csvText)
                        val imported = studentRepository.students.value
                        val offlineCount = imported.count { StudentCsvParser.isOfflineStudent(it) }
                        val onlineCount = imported.size - offlineCount
                        Toast.makeText(
                            context,
                            "成功导入 ${imported.size} 名学生（线下需签到 $offlineCount 人，线上免签 $onlineCount 人）",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(context, "文件读取为空", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "导入错误: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        containerColor = PaperBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "系统配置与管理",
                        style = MaterialTheme.typography.titleMedium,
                        color = InkPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = InkPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PaperSurface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(PaperBackground)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.Lg, vertical = Spacing.Xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.Xxl)
        ) {
            // ── 账号与同步 ─────────────────────────────────────
            SettingsSection(title = "账号与同步") {
                SettingsCard {
                    Text(
                        text = "任课教师",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkSecondary
                    )
                    Text(
                        text = "姓名会展示在导出的签到表长图副标题中。",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkTertiary
                    )
                    OutlinedTextField(
                        value = teacherName,
                        onValueChange = {
                            teacherName = it
                            prefRepository.teacherName = it
                        },
                        label = { Text("教师姓名（如：张老师）") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SettingsCard {
                    Text(
                        text = "jf-alpha.com 自动登录",
                        style = MaterialTheme.typography.titleSmall,
                        color = InkPrimary
                    )
                    Text(
                        text = "保存在手机本地。配置后，点名页「打开网站下载名单」会直接登录并导入最新 CSV；自动下载失败时才打开内置浏览器手动操作。",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkTertiary
                    )

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("网站账号 / 手机号 / 邮箱") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("网站密码") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            prefRepository.saveCredentials(username, password)
                            Toast.makeText(context, "账密已保存在本地", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.Sm))
                        Text("保存账号密码")
                    }
                }

                SettingsCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
                    ) {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = InkSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "校管家（TMS）自动考勤",
                            style = MaterialTheme.typography.titleSmall,
                            color = InkPrimary
                        )
                    }
                    Text(
                        text = "用于点名后同步到 tms.xiaogj.com。支持 α1/β1/β2/β3 班级；请假（不发回放）按事假提交且不计费，其他已匹配学员按出勤并计费；提交前会显示预览，确认后才写入。账号密码只保存在本机。",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkTertiary
                    )

                    OutlinedTextField(
                        value = tmsUsername,
                        onValueChange = { tmsUsername = it },
                        label = { Text("校管家账号 / 邮箱") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = tmsPassword,
                        onValueChange = { tmsPassword = it },
                        label = { Text("校管家密码") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showTmsPassword = !showTmsPassword }) {
                                Icon(
                                    imageVector = if (showTmsPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (showTmsPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            prefRepository.saveTmsCredentials(tmsUsername.trim(), tmsPassword)
                            Toast.makeText(context, "校管家账密已保存在本地", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.Sm))
                        Text("保存校管家账密")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = InkSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.Sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "每天 18:00 提醒未同步班级",
                                style = MaterialTheme.typography.titleSmall,
                                color = InkPrimary
                            )
                            Text(
                                text = "本地通知，不需要注册远程推送。若今天有班级还没有同步成功，会在通知栏提醒。",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkTertiary
                            )
                        }
                        Switch(
                            checked = tmsReminderEnabled,
                            onCheckedChange = { setTmsReminderEnabled(it) }
                        )
                    }

                    if (tmsReminderEnabled &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        TextButton(
                            onClick = {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                "系统通知权限未开启，点此授权",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    Text(
                        text = "安全提示：校管家官方登录跳转会在 URL 中携带密码，应用已强制使用 HTTPS；请勿在公共 Wi-Fi 下使用。更换密码后请回到这里重新保存。",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkTertiary
                    )
                }
            }

            // ── 打印 ───────────────────────────────────────────
            SettingsSection(title = "打印") {
                SettingsCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
                        ) {
                            Icon(
                                Icons.Default.Print,
                                contentDescription = null,
                                tint = InkSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "局域网打印机",
                                style = MaterialTheme.typography.titleSmall,
                                color = InkPrimary
                            )
                        }
                        IconButton(onClick = { showAddPrinterDialog = true }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "添加新打印机",
                                tint = InkPrimary
                            )
                        }
                    }

                    Text(
                        text = "支持配置多台打印机的 IP 与端口别名，在席卡打印弹窗可直接快速切换。",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkTertiary
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                        printerProfiles.forEach { profile ->
                            val isDefault = (profile.id == selectedPrinterId)
                            val isTestingThis = (testingPrinterId == profile.id)

                            Surface(
                                shape = RoundedCornerShape(Radii.Control),
                                color = if (isDefault) PaperSurface else PaperSunken,
                                border = BorderStroke(
                                    1.dp,
                                    if (isDefault) InkPrimary else Hairline
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.Md),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
                                        ) {
                                            Text(
                                                text = profile.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = InkPrimary
                                            )
                                            if (isDefault) {
                                                Surface(
                                                    shape = RoundedCornerShape(Radii.Chip),
                                                    color = InkPrimary
                                                ) {
                                                    Text(
                                                        text = "默认",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = InkOnDark,
                                                        modifier = Modifier.padding(
                                                            horizontal = Spacing.Xs,
                                                            vertical = 1.dp
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(Spacing.Xs))
                                        Text(
                                            text = "${profile.ip}:${profile.port}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                            color = InkTertiary
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    testingPrinterId = profile.id
                                                    val reachable = com.jf.checkin.logic.LanPrinterHelper.testConnection(profile.ip, profile.port)
                                                    testingPrinterId = null
                                                    if (reachable) {
                                                        Toast.makeText(context, "已连通 ${profile.name} (${profile.ip}:${profile.port})", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "无法连通 ${profile.name} (${profile.ip}:${profile.port})，请检查网络", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            },
                                            enabled = !isTestingThis,
                                            contentPadding = PaddingValues(horizontal = Spacing.Sm, vertical = Spacing.Xs)
                                        ) {
                                            if (isTestingThis) {
                                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                            } else {
                                                Text("测试", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }

                                        if (!isDefault) {
                                            OutlinedButton(
                                                onClick = {
                                                    selectedPrinterId = profile.id
                                                    prefRepository.selectedPrinterId = profile.id
                                                    prefRepository.printerIp = profile.ip
                                                    prefRepository.printerPort = profile.port
                                                    Toast.makeText(context, "已将 ${profile.name} 设为默认", Toast.LENGTH_SHORT).show()
                                                },
                                                contentPadding = PaddingValues(horizontal = Spacing.Sm, vertical = Spacing.Xs)
                                            ) {
                                                Text("设为默认", style = MaterialTheme.typography.labelMedium)
                                            }
                                        }

                                        if (printerProfiles.size > 1) {
                                            IconButton(
                                                onClick = {
                                                    val updated = printerProfiles.filterNot { it.id == profile.id }
                                                    printerProfiles = updated
                                                    prefRepository.savePrinterProfiles(updated)
                                                    if (selectedPrinterId == profile.id) {
                                                        val newDefault = updated.first()
                                                        selectedPrinterId = newDefault.id
                                                        prefRepository.selectedPrinterId = newDefault.id
                                                        prefRepository.printerIp = newDefault.ip
                                                        prefRepository.printerPort = newDefault.port
                                                    }
                                                    Toast.makeText(context, "已删除打印机 ${profile.name}", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "删除",
                                                    tint = Danger,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                printerSimplex = !printerSimplex
                                prefRepository.printerSimplex = printerSimplex
                            }
                    ) {
                        Checkbox(
                            checked = printerSimplex,
                            onCheckedChange = {
                                printerSimplex = it
                                prefRepository.printerSimplex = it
                            }
                        )
                        Spacer(modifier = Modifier.width(Spacing.Xs))
                        Text(
                            text = "默认单面打印（强制 PJL DUPLEX=OFF）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSecondary
                        )
                    }
                }
            }

            // ── 数据 ───────────────────────────────────────────
            SettingsSection(title = "数据") {
                SettingsCard {
                    Text(
                        text = "本地名单",
                        style = MaterialTheme.typography.titleSmall,
                        color = InkPrimary
                    )

                    Surface(
                        shape = RoundedCornerShape(Radii.Control),
                        color = PaperSunken,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.Md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.Xs)
                        ) {
                            Text(
                                text = "当前已录入线下学生：${students.size} 人",
                                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                                color = InkPrimary
                            )
                            Text(
                                text = "班级总数：${students.map { it.classCode }.distinct().size} 个",
                                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                color = InkSecondary
                            )
                            if (lastImportTime.isNotBlank()) {
                                Text(
                                    text = "上次更新时间：$lastImportTime",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                    color = InkTertiary
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.Sm))
                        Text("从本地手动选择 CSV 文件导入")
                    }

                    if (students.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                studentRepository.clearAllStudents()
                                Toast.makeText(context, "已清空当前全部学生名单", Toast.LENGTH_SHORT).show()
                            },
                            border = BorderStroke(1.dp, Danger),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("清空当前名单数据", color = Danger)
                        }
                    }
                }
            }

            // ── 错题快速转换 ───────────────────────────────────
            SettingsSection(title = "错题快速转换") {
                HomeworkSettingsCard(prefRepository = prefRepository)
            }

            // ── 规则 ───────────────────────────────────────────
            SettingsSection(title = "规则与说明") {
                SettingsCard {
                    Text(
                        text = "• 人员范围：α1 与 β1 形式为线上课程，系统仍会录入名单并标记为「线上 · 免签」；β2 与 β3 形式为线下课程，需要签到。四种班型都会同步到校管家。手动调入学生按线下人员处理。\n" +
                            "• 点名操作：单点卡片直接打勾（√ 已到），长按卡片可快速切换为「迟到」、「请假（不发回放）」、「请假（发回放）」、「未到」。\n" +
                            "• 手动加人：点名页「加人」可把其他老师班调入的学生加入当前班级，可填备注（如原班来源），重导名单不丢失；长按卡片可改备注、调到其他班，调入学生可删除。\n" +
                            "• 临时调班：长按卡片「调到其他班」只是临时调动，原班顶部会显示调到哪去了并可一键调回，接收班卡片会标注原班来源；大批量调班请重新导入名单文件。\n" +
                            "• 自动匹配：课前 20 分钟 ～ 课后 30 分钟内，应用将自动为您切换锁定对应班级。\n" +
                            "• 自动考勤：点名页顶部云上传图标或「更多」中可打开校管家同步。应用按「班级号+班型」匹配 α1/β1/β2/β3 当天课程，先显示已到/迟到/请假/未到预览，确认后才提交；请假（不发回放）按校管家「事假」提交且不计费，请假（发回放）与其他已匹配学员按出勤并计费；已点名课程自动跳过，不发送微信/短信通知。\n" +
                            "• 每日提醒：每天 18:00 使用本地通知检查今天课表；如果还有班级没有成功同步记录，会在通知栏提醒。不需要注册远程推送，但 Android 13+ 需允许通知权限。\n" +
                            "• 发送分享：导出时离线生成高清签到长图，直接调起安卓系统的“发送给”（支持微信、QQ等）；分享后会询问是否继续同步校管家，当天已同步的班型不会重复提示。\n" +
                            "• 错题转换：在任意输入框输入题号后紧接着输入触发词（默认 qqq，如 135qqq），会自动展开为预设文案；也可以长按选中题号，在系统菜单点「转为错题格式」。首次使用需到系统无障碍设置中开启「错题自动展开」。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                }
            }
        }
    }

    if (showAddPrinterDialog) {
        var newName by remember { mutableStateOf("") }
        var newIp by remember { mutableStateOf("172.16.100.222") }
        var newPort by remember { mutableStateOf("9100") }

        AlertDialog(
            onDismissRequest = { showAddPrinterDialog = false },
            title = {
                Text(
                    "添加打印机配置",
                    style = MaterialTheme.typography.titleMedium,
                    color = InkPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("别名（如：办公室HP / 101教室）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newIp,
                        onValueChange = { newIp = it },
                        label = { Text("IP 地址（如：172.16.100.222）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPort,
                        onValueChange = { newPort = it },
                        label = { Text("端口号（默认 9100）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val alias = newName.trim().ifBlank { "打印机 ${printerProfiles.size + 1}" }
                        val ip = newIp.trim().ifBlank { "172.16.100.222" }
                        val port = newPort.trim().toIntOrNull() ?: 9100
                        val newProfile = com.jf.checkin.data.model.PrinterProfile(
                            name = alias,
                            ip = ip,
                            port = port
                        )
                        val updated = printerProfiles + newProfile
                        printerProfiles = updated
                        prefRepository.savePrinterProfiles(updated)
                        showAddPrinterDialog = false
                        Toast.makeText(context, "已添加打印机: $alias", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddPrinterDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
internal fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = InkPrimary,
            modifier = Modifier.padding(bottom = Spacing.Sm)
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md),
            content = content
        )
    }
}

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(Radii.Card),
        colors = CardDefaults.cardColors(containerColor = PaperSurface),
        border = BorderStroke(1.dp, Hairline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md),
            content = content
        )
    }
}
