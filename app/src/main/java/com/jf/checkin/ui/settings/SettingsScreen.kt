package com.jf.checkin.ui.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jf.checkin.data.repository.StudentRepository
import com.jf.checkin.data.repository.UserPrefRepository
import com.jf.checkin.ui.theme.PrimaryBlue
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
                        Toast.makeText(
                            context,
                            "成功导入 ${studentRepository.students.value.size} 名线下需签到学生！",
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
        topBar = {
            TopAppBar(
                title = { Text("系统配置与管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 0. 教师信息配置卡片
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "任课教师设置",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "填写的教师姓名将展示在导出的签到表长图副标题中。",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = teacherName,
                        onValueChange = {
                            teacherName = it
                            prefRepository.teacherName = it
                        },
                        label = { Text("教师姓名 (如：张老师)") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 1. 网站账密配置卡片
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "jf-alpha.com 自动登录账密",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "保存在手机本地，内置浏览器打开时会自动填入账号和密码。",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("网站账号 / 手机号 / 邮箱") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

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

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            prefRepository.saveCredentials(username, password)
                            Toast.makeText(context, "账密已成功保存在本地！", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("保存账号密码")
                    }
                }
            }

            // 1.5 局域网打印机设置
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                            Text(
                                text = "局域网打印机管理",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                        }
                        IconButton(onClick = { showAddPrinterDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "添加新打印机", tint = PrimaryBlue)
                        }
                    }

                    Text(
                        text = "支持配置多台打印机的 IP 与端口别名，在席卡打印弹窗可直接快速切换。",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 打印机别名列表
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        printerProfiles.forEach { profile ->
                            val isDefault = (profile.id == selectedPrinterId)
                            val isTestingThis = (testingPrinterId == profile.id)

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDefault) PrimaryBlue.copy(alpha = 0.06f) else Color(0xFFF8FAFC),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isDefault) PrimaryBlue else Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = profile.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color(0xFF1E293B)
                                            )
                                            if (isDefault) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = PrimaryBlue
                                                ) {
                                                    Text(
                                                        text = "默认",
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${profile.ip}:${profile.port}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // 测试连通性按钮
                                        OutlinedButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    testingPrinterId = profile.id
                                                    val reachable = com.jf.checkin.logic.LanPrinterHelper.testConnection(profile.ip, profile.port)
                                                    testingPrinterId = null
                                                    if (reachable) {
                                                        Toast.makeText(context, "✅ 成功连通 ${profile.name} (${profile.ip}:${profile.port})！", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, "❌ 无法连通 ${profile.name} (${profile.ip}:${profile.port})，请检查网络", Toast.LENGTH_LONG).show()
                                                    }
                                                }
                                            },
                                            enabled = !isTestingThis,
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            if (isTestingThis) {
                                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                                            } else {
                                                Text("测试", fontSize = 11.sp)
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
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("设为默认", fontSize = 11.sp)
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
                                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 单面打印偏好开关
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
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "默认单面打印 (强制 PJL DUPLEX=OFF)",
                            fontSize = 13.sp,
                            color = Color(0xFF334155)
                        )
                    }
                }
            }

            // 2. 本地名单与导入管理
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "数据管理与手动导入",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "当前已录入线下学生：${students.size} 人",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "班级总数：${students.map { it.classCode }.distinct().size} 个",
                                fontSize = 13.sp,
                                color = Color(0xFF475569)
                            )
                            if (lastImportTime.isNotBlank()) {
                                Text(
                                    text = "上次更新时间：$lastImportTime",
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = { filePickerLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("从本地手动选择 CSV 文件导入")
                    }

                    if (students.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                studentRepository.clearAllStudents()
                                Toast.makeText(context, "已清空当前全部学生名单", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("清空当前名单数据")
                        }
                    }
                }
            }

            // 3. 业务规则说明
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "业务规则与说明",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• 过滤规则：α1 与 β1 形式为线上课程，系统自动忽略不予录入；仅 β2 和 β3 为线下需要签到。\n" +
                                "• 点名操作：单点卡片直接打勾（√ 已到），长按卡片可快速切换为「迟到」、「请假」、「未到」。\n" +
                                "• 手动加人：点名页「加人」可把其他老师班调入的学生加入当前班级，可填备注（如原班来源），重导名单不丢失；长按卡片可改备注、调到其他班，调入学生可删除。\n" +
                                "• 临时调班：长按卡片「调到其他班」只是临时调动，原班顶部会显示调到哪去了并可一键调回，接收班卡片会标注原班来源；大批量调班请重新导入名单文件。\n" +
                                "• 自动匹配：课前 20 分钟 ～ 课后 30 分钟内，应用将自动为您切换锁定对应班级。\n" +
                                "• 发送分享：导出时离线生成高清签到长图，直接调起安卓系统的“发送给”（支持微信、QQ等）。",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = Color(0xFF475569)
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
            title = { Text("添加打印机配置", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("别名 (如: 办公室HP / 101教室)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newIp,
                        onValueChange = { newIp = it },
                        label = { Text("IP 地址 (如: 172.16.100.222)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPort,
                        onValueChange = { newPort = it },
                        label = { Text("端口号 (默认 9100)") },
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
