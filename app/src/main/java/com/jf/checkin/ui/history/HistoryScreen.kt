package com.jf.checkin.ui.history

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jf.checkin.data.model.AttendanceRecord
import com.jf.checkin.data.repository.HistoryRepository
import com.jf.checkin.ui.theme.AccentBlue
import com.jf.checkin.ui.theme.AccentGreen
import com.jf.checkin.ui.theme.AccentOrange
import com.jf.checkin.ui.theme.AccentPurple
import com.jf.checkin.ui.theme.AccentRed
import com.jf.checkin.ui.theme.PrimaryBlue
import com.jf.checkin.util.ShareUtil
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    historyRepository: HistoryRepository,
    onNavigateBack: () -> Unit,
    onRestoreRecord: (AttendanceRecord) -> Unit
) {
    val context = LocalContext.current
    val records by historyRepository.records.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("签到历史记录", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无签到历史记录",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "每次点击「发长图」或「存相册」时，系统会自动在此生成归档记录",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF8FAFC))
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = record.classCode,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E293B)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${record.classTime} ${if (record.teacherName.isNotBlank()) "· 教师: ${record.teacherName}" else ""}",
                                        fontSize = 13.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                Text(
                                    text = record.recordTime,
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 状态统计小标签
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StatusBadge(label = "实到: ${record.presentCount}", color = AccentGreen)
                                if (record.onlineCount > 0) {
                                    StatusBadge(label = "转线上: ${record.onlineCount}", color = AccentPurple)
                                }
                                if (record.lateCount > 0) {
                                    StatusBadge(label = "迟到: ${record.lateCount}", color = AccentOrange)
                                }
                                if (record.leaveCount > 0) {
                                    StatusBadge(label = "请假: ${record.leaveCount}", color = AccentBlue)
                                }
                                if (record.absentCount > 0) {
                                    StatusBadge(label = "未到: ${record.absentCount}", color = AccentRed)
                                }
                                if (record.movedOutCount > 0) {
                                    StatusBadge(label = "调出: ${record.movedOutCount}", color = Color(0xFF0F766E))
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // 操作按钮
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { onRestoreRecord(record) },
                                    modifier = Modifier.height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("恢复", fontSize = 12.sp)
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                OutlinedButton(
                                    onClick = {
                                        historyRepository.deleteRecord(record.id)
                                        Toast.makeText(context, "已删除该条历史记录", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("删除", fontSize = 12.sp)
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                FilledTonalButton(
                                    onClick = {
                                        val file = File(record.imagePath)
                                        if (file.exists()) {
                                            ShareUtil.shareImage(context, file, "${record.classCode} 历史签到表")
                                        } else {
                                            Toast.makeText(context, "原图片文件已清理或移除", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("重新发送", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
