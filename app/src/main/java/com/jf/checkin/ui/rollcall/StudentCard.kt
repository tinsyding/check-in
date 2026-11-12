package com.jf.checkin.ui.rollcall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jf.checkin.data.model.AttendanceStatus
import com.jf.checkin.data.model.Student
import com.jf.checkin.data.parser.StudentCsvParser
import com.jf.checkin.ui.theme.Danger
import com.jf.checkin.ui.theme.InkPrimary
import com.jf.checkin.ui.theme.InkSecondary
import com.jf.checkin.ui.theme.InkTertiary
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

    val status = statusVisualFor(student)

    Box(modifier = modifier) {
        Card(
            shape = RoundedCornerShape(Radii.Card),
            colors = CardDefaults.cardColors(containerColor = PaperSurface),
            border = BorderStroke(1.dp, status.accent.copy(alpha = 0.32f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
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
            // 纸面卡片 + 左侧状态色边 + 整宽状态页脚
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(Sizes.StatusEdge)
                        .fillMaxHeight()
                        .background(status.accent)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.Md, vertical = Spacing.Md),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = student.studentId,
                            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                            color = InkTertiary
                        )

                        Spacer(modifier = Modifier.height(Spacing.Xs))

                        Text(
                            text = student.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = InkPrimary,
                            maxLines = 1
                        )

                        if (student.remark.isNotBlank()) {
                            Spacer(modifier = Modifier.height(Spacing.Xs))
                            Text(
                                text = student.remark,
                                style = MaterialTheme.typography.labelMedium,
                                color = InkSecondary,
                                maxLines = 2
                            )
                        }
                        if (student.isManual) {
                            Spacer(modifier = Modifier.height(Spacing.Xs))
                            Text(
                                text = "调入",
                                style = MaterialTheme.typography.labelMedium,
                                color = InkTertiary
                            )
                        }
                        if (student.originalClassCode.isNotBlank() && student.originalClassCode != student.classCode) {
                            Spacer(modifier = Modifier.height(Spacing.Xs))
                            Text(
                                text = "由 ${student.originalClassCode} 临时调入",
                                style = MaterialTheme.typography.labelMedium,
                                color = InkTertiary,
                                maxLines = 1
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(status.accent.copy(alpha = 0.22f))
                    )

                    // 状态页脚：淡色底 + 图标 + 状态名，远看也能区分
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(status.accent.copy(alpha = 0.10f))
                            .padding(horizontal = Spacing.Sm, vertical = Spacing.Xs),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = status.icon,
                            contentDescription = null,
                            tint = status.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.Sm))
                        Text(
                            text = status.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = status.accent,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 长按弹出快捷状态菜单：中性文字 + 语义图标，当前状态用尾部勾标出
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            StatusMenuItem(
                label = "已到",
                accent = RedPen,
                icon = Icons.Default.Check,
                selected = student.status == AttendanceStatus.PRESENT,
                onClick = {
                    onSetStatus(AttendanceStatus.PRESENT)
                    showMenu = false
                }
            )
            StatusMenuItem(
                label = "迟到",
                accent = StatusLateInk,
                icon = Icons.Default.Schedule,
                selected = student.status == AttendanceStatus.LATE,
                onClick = {
                    onSetStatus(AttendanceStatus.LATE)
                    showMenu = false
                }
            )
            StatusMenuItem(
                label = "请假（不发回放）",
                accent = StatusLeaveInk,
                icon = Icons.Default.EventBusy,
                selected = student.status == AttendanceStatus.LEAVE,
                onClick = {
                    onSetStatus(AttendanceStatus.LEAVE)
                    showMenu = false
                }
            )
            StatusMenuItem(
                label = "请假（发回放）",
                accent = StatusLeaveReplayInk,
                icon = Icons.Default.PlayCircle,
                selected = student.status == AttendanceStatus.LEAVE_REPLAY,
                onClick = {
                    onSetStatus(AttendanceStatus.LEAVE_REPLAY)
                    showMenu = false
                }
            )
            StatusMenuItem(
                label = if (StudentCsvParser.isOfflineStudent(student)) "转线上" else "线上",
                accent = StatusOnlineInk,
                icon = Icons.Default.Videocam,
                selected = student.status == AttendanceStatus.ONLINE,
                onClick = {
                    onSetStatus(AttendanceStatus.ONLINE)
                    showMenu = false
                }
            )
            StatusMenuItem(
                label = "未到",
                accent = StatusUncheckedInk,
                icon = Icons.Default.RadioButtonUnchecked,
                selected = student.status == AttendanceStatus.UNCHECKED,
                onClick = {
                    onSetStatus(AttendanceStatus.UNCHECKED)
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("备注…", color = InkPrimary) },
                leadingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = InkSecondary)
                },
                onClick = {
                    showMenu = false
                    onEditRemark()
                }
            )
            DropdownMenuItem(
                text = { Text("调到其他班…", color = InkPrimary) },
                leadingIcon = {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = InkSecondary)
                },
                onClick = {
                    showMenu = false
                    onMoveClass()
                }
            )
            if (onDeleteManual != null) {
                DropdownMenuItem(
                    text = { Text("删除该调入学生", color = Danger) },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Danger)
                    },
                    onClick = {
                        showMenu = false
                        onDeleteManual()
                    }
                )
            }
        }
    }
}

/**
 * 状态视觉：颜色、图标、文案一次定义，卡片和菜单共用同一套语义。
 */
private data class StatusVisual(
    val label: String,
    val accent: Color,
    val icon: ImageVector
)

private fun statusVisualFor(student: Student): StatusVisual = when (student.status) {
    AttendanceStatus.PRESENT -> StatusVisual(
        label = "已到",
        accent = RedPen,
        icon = Icons.Default.Check
    )
    AttendanceStatus.LATE -> StatusVisual(
        label = "迟到",
        accent = StatusLateInk,
        icon = Icons.Default.Schedule
    )
    AttendanceStatus.LEAVE -> StatusVisual(
        label = "请假 · 无回放",
        accent = StatusLeaveInk,
        icon = Icons.Default.EventBusy
    )
    AttendanceStatus.LEAVE_REPLAY -> StatusVisual(
        label = "请假 · 回放",
        accent = StatusLeaveReplayInk,
        icon = Icons.Default.PlayCircle
    )
    AttendanceStatus.ONLINE -> StatusVisual(
        label = if (StudentCsvParser.isOfflineStudent(student)) "转线上" else "线上",
        accent = StatusOnlineInk,
        icon = Icons.Default.Videocam
    )
    AttendanceStatus.UNCHECKED -> StatusVisual(
        label = "未到",
        accent = StatusUncheckedInk,
        icon = Icons.Default.RadioButtonUnchecked
    )
}

/**
 * 状态菜单项：语义色只出现在图标上，当前状态用尾部勾标出。
 */
@Composable
private fun StatusMenuItem(
    label: String,
    accent: Color,
    icon: ImageVector,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                color = InkPrimary,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            null
        },
        onClick = onClick
    )
}
