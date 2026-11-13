package com.jf.checkin.ui.history

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jf.checkin.data.model.AttendanceRecord
import com.jf.checkin.data.repository.HistoryRepository
import com.jf.checkin.ui.theme.BadgeBackground
import com.jf.checkin.ui.theme.Danger
import com.jf.checkin.ui.theme.Hairline
import com.jf.checkin.ui.theme.InkPrimary
import com.jf.checkin.ui.theme.InkSecondary
import com.jf.checkin.ui.theme.InkTertiary
import com.jf.checkin.ui.theme.PaperBackground
import com.jf.checkin.ui.theme.PaperSurface
import com.jf.checkin.ui.theme.Radii
import com.jf.checkin.ui.theme.RedPen
import com.jf.checkin.ui.theme.Sizes
import com.jf.checkin.ui.theme.Spacing
import com.jf.checkin.ui.theme.StatusLateInk
import com.jf.checkin.ui.theme.StatusLeaveInk
import com.jf.checkin.ui.theme.StatusLeaveReplayInk
import com.jf.checkin.ui.theme.StatusOnlineInk
import com.jf.checkin.ui.theme.StatusUncheckedInk
import com.jf.checkin.util.ShareUtil
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    historyRepository: HistoryRepository,
    onNavigateBack: () -> Unit,
    onRestoreRecord: (AttendanceRecord) -> Unit
) {
    val context = LocalContext.current
    val records by historyRepository.records.collectAsState()

    Scaffold(
        containerColor = PaperBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "签到历史",
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
        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PaperBackground)
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = Spacing.Xxxl)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = InkTertiary
                    )
                    Spacer(modifier = Modifier.height(Spacing.Lg))
                    Text(
                        text = "暂无签到历史记录",
                        style = MaterialTheme.typography.titleLarge,
                        color = InkPrimary
                    )
                    Spacer(modifier = Modifier.height(Spacing.Sm))
                    Text(
                        text = "每次点击「发长图」或「存相册」时，系统会自动在此生成归档记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PaperBackground)
                    .padding(paddingValues),
                contentPadding = PaddingValues(Spacing.Lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.Md)
            ) {
                items(records, key = { it.id }) { record ->
                    Card(
                        shape = RoundedCornerShape(Radii.Card),
                        colors = CardDefaults.cardColors(containerColor = PaperSurface),
                        border = BorderStroke(1.dp, Hairline),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(Spacing.Lg)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = record.classCode,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = InkPrimary
                                    )
                                    Spacer(modifier = Modifier.height(Spacing.Xs))
                                    Text(
                                        text = buildString {
                                            append(record.classTime)
                                            if (record.teacherName.isNotBlank()) {
                                                append(" · 教师: ")
                                                append(record.teacherName)
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = InkSecondary
                                    )
                                }
                                Text(
                                    text = record.recordTime,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                                    color = InkTertiary
                                )
                            }

                            Spacer(modifier = Modifier.height(Spacing.Md))

                            // 状态统计：中性 chip + 语义圆点，允许换行
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
                                verticalArrangement = Arrangement.spacedBy(Spacing.Sm)
                            ) {
                                StatusChip(label = "实到 ${record.presentCount}", accent = RedPen)
                                if (record.onlineCount > 0) {
                                    StatusChip(label = "线上 ${record.onlineCount}", accent = StatusOnlineInk)
                                }
                                if (record.lateCount > 0) {
                                    StatusChip(label = "迟到 ${record.lateCount}", accent = StatusLateInk)
                                }
                                if (record.leaveCount > 0) {
                                    StatusChip(label = "请假 ${record.leaveCount}", accent = StatusLeaveInk)
                                }
                                if (record.absentCount > 0) {
                                    StatusChip(label = "未到 ${record.absentCount}", accent = StatusUncheckedInk)
                                }
                                if (record.movedOutCount > 0) {
                                    StatusChip(label = "调出 ${record.movedOutCount}", accent = StatusLeaveReplayInk)
                                }
                            }

                            Spacer(modifier = Modifier.height(Spacing.Lg))

                            // 操作按钮：重新发送为主，恢复为次，删除弱化
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        historyRepository.deleteRecord(record.id)
                                        Toast.makeText(context, "已删除该条历史记录", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = Spacing.Md)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Danger
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.Xs))
                                    Text(
                                        "删除",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Danger
                                    )
                                }

                                Spacer(modifier = Modifier.width(Spacing.Sm))

                                OutlinedButton(
                                    onClick = { onRestoreRecord(record) },
                                    modifier = Modifier.height(Sizes.CompactControlHeight),
                                    contentPadding = PaddingValues(horizontal = Spacing.Md),
                                    border = BorderStroke(1.dp, Hairline)
                                ) {
                                    Icon(
                                        Icons.Default.Restore,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = InkPrimary
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.Xs))
                                    Text(
                                        "恢复",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = InkPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.width(Spacing.Sm))

                                Button(
                                    onClick = {
                                        val file = File(record.imagePath)
                                        if (file.exists()) {
                                            ShareUtil.shareImage(context, file, "${record.classCode} 历史签到表")
                                        } else {
                                            Toast.makeText(context, "原图片文件已清理或移除", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.height(Sizes.CompactControlHeight),
                                    contentPadding = PaddingValues(horizontal = Spacing.Md)
                                ) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.Xs))
                                    Text(
                                        "重新发送",
                                        style = MaterialTheme.typography.labelMedium
                                    )
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
private fun StatusChip(label: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(Radii.Chip),
        color = BadgeBackground
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.Sm, vertical = Spacing.Xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(accent, CircleShape)
            )
            Spacer(modifier = Modifier.width(Spacing.Xs))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = InkPrimary
            )
        }
    }
}
