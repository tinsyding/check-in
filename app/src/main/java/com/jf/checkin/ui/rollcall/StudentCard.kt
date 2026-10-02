package com.jf.checkin.ui.rollcall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jf.checkin.data.model.AttendanceStatus
import com.jf.checkin.data.model.Student
import com.jf.checkin.ui.theme.AccentBlue
import com.jf.checkin.ui.theme.AccentGreen
import com.jf.checkin.ui.theme.AccentOrange
import com.jf.checkin.ui.theme.AccentPurple
import com.jf.checkin.ui.theme.CardBackgroundOnline
import com.jf.checkin.data.parser.StudentCsvParser
import com.jf.checkin.ui.theme.CardBackgroundPresent
import com.jf.checkin.ui.theme.CardBackgroundUnchecked
import com.jf.checkin.ui.theme.CardBorderOnline
import com.jf.checkin.ui.theme.CardBorderPresent

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StudentCard(
    student: Student,
    onToggleCheckIn: () -> Unit,
    onSetStatus: (AttendanceStatus) -> Unit,
    onEditRemark: () -> Unit = {},
    onDeleteManual: (() -> Unit)? = null,
    onMoveClass: () -> Unit = {},
    onOnlineClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val (cardBgColor, borderColor, statusTextColor) = when (student.status) {
        AttendanceStatus.PRESENT -> Triple(CardBackgroundPresent, CardBorderPresent, AccentGreen)
        AttendanceStatus.LATE -> Triple(Color(0xFFFEF3C7), Color(0xFFFCD34D), AccentOrange)
        AttendanceStatus.LEAVE -> Triple(Color(0xFFE0E7FF), Color(0xFFA5B4FC), AccentBlue)
        AttendanceStatus.ONLINE -> Triple(CardBackgroundOnline, CardBorderOnline, AccentPurple)
        AttendanceStatus.UNCHECKED -> Triple(CardBackgroundUnchecked, Color(0xFFE2E8F0), Color(0xFF94A3B8))
    }

    Box(modifier = modifier) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = cardBgColor),
            border = BorderStroke(1.5.dp, borderColor),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (student.status == AttendanceStatus.PRESENT || student.status == AttendanceStatus.ONLINE) 2.dp else 0.dp
            ),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (student.status == AttendanceStatus.ONLINE) {
                            onOnlineClick?.invoke()
                        } else {
                            onToggleCheckIn()
                        }
                    },
                    onLongClick = { showMenu = true }
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 名字上方展示五位数学号
                Text(
                    text = student.studentId,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )

                Spacer(modifier = Modifier.height(2.dp))

                // 大字姓名
                Text(
                    text = student.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
                    maxLines = 1
                )

                // 备注（手动调入来源等），为空则不占位
                if (student.remark.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = student.remark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF6366F1),
                        maxLines = 2
                    )
                }
                // 手动添加角标
                if (student.isManual) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "调入",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF059669)
                    )
                }
                // 临时调班来源（接收班显示从哪调来）
                if (student.originalClassCode.isNotBlank() && student.originalClassCode != student.classCode) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "由 ${student.originalClassCode} 临时调入",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0F766E),
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 状态指示徽章
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (student.status) {
                        AttendanceStatus.PRESENT -> AccentGreen
                        AttendanceStatus.LATE -> AccentOrange
                        AttendanceStatus.LEAVE -> AccentBlue
                        AttendanceStatus.ONLINE -> AccentPurple
                        AttendanceStatus.UNCHECKED -> Color(0xFFCBD5E1)
                    },
                    modifier = Modifier.height(26.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (student.status == AttendanceStatus.PRESENT) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        } else if (student.status == AttendanceStatus.ONLINE) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = when (student.status) {
                                AttendanceStatus.PRESENT -> "已到"
                                AttendanceStatus.LATE -> "迟到"
                                AttendanceStatus.LEAVE -> "请假"
                                AttendanceStatus.ONLINE -> if (student.classFormat in StudentCsvParser.OFFLINE_FORMATS) "转线上 · 免签" else "线上 · 免签"
                                AttendanceStatus.UNCHECKED -> "未到"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // 长按弹出快捷状态菜单
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("已到 (√)", color = AccentGreen, fontWeight = FontWeight.Bold) },
                leadingIcon = { Icon(Icons.Default.Check, contentDescription = null, tint = AccentGreen) },
                onClick = {
                    onSetStatus(AttendanceStatus.PRESENT)
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("迟到", color = AccentOrange, fontWeight = FontWeight.SemiBold) },
                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null, tint = AccentOrange) },
                onClick = {
                    onSetStatus(AttendanceStatus.LATE)
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("请假", color = AccentBlue, fontWeight = FontWeight.SemiBold) },
                leadingIcon = { Icon(Icons.Default.EventBusy, contentDescription = null, tint = AccentBlue) },
                onClick = {
                    onSetStatus(AttendanceStatus.LEAVE)
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text(if (student.classFormat in StudentCsvParser.OFFLINE_FORMATS) "转线上" else "线上", color = AccentPurple, fontWeight = FontWeight.SemiBold) },
                leadingIcon = { Icon(Icons.Default.Videocam, contentDescription = null, tint = AccentPurple) },
                onClick = {
                    onSetStatus(AttendanceStatus.ONLINE)
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("设为未到", color = Color(0xFF64748B)) },
                leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFF64748B)) },
                onClick = {
                    onSetStatus(AttendanceStatus.UNCHECKED)
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("备注…", color = Color(0xFF4F46E5)) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = Color(0xFF4F46E5)) },
                onClick = {
                    showMenu = false
                    onEditRemark()
                }
            )
            DropdownMenuItem(
                text = { Text("调到其他班…", color = Color(0xFF0F766E)) },
                leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = Color(0xFF0F766E)) },
                onClick = {
                    showMenu = false
                    onMoveClass()
                }
            )
            if (onDeleteManual != null) {
                DropdownMenuItem(
                    text = { Text("删除该调入学生", color = Color(0xFFEF4444)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444)) },
                    onClick = {
                        showMenu = false
                        onDeleteManual()
                    }
                )
            }
        }
    }
}
