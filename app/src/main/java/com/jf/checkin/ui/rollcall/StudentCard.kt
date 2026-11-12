package com.jf.checkin.ui.rollcall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jf.checkin.data.model.AttendanceStatus
import com.jf.checkin.data.model.Student
import com.jf.checkin.data.parser.StudentCsvParser
import com.jf.checkin.ui.theme.BadgeBackground
import com.jf.checkin.ui.theme.Danger
import com.jf.checkin.ui.theme.Hairline
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

    val statusAccent = when (student.status) {
        AttendanceStatus.PRESENT -> RedPen
        AttendanceStatus.LATE -> StatusLateInk
        AttendanceStatus.LEAVE -> StatusLeaveInk
        AttendanceStatus.LEAVE_REPLAY -> StatusLeaveReplayInk
        AttendanceStatus.ONLINE -> StatusOnlineInk
        AttendanceStatus.UNCHECKED -> StatusUncheckedInk
    }

    val statusLabel = when (student.status) {
        AttendanceStatus.PRESENT -> "已到"
        AttendanceStatus.LATE -> "迟到"
        AttendanceStatus.LEAVE -> "请假·无回放"
        AttendanceStatus.LEAVE_REPLAY -> "请假·回放"
        AttendanceStatus.ONLINE -> if (StudentCsvParser.isOfflineStudent(student)) "转线上 · 免签" else "线上 · 免签"
        AttendanceStatus.UNCHECKED -> "未到"
    }

    Box(modifier = modifier) {
        Card(
            shape = RoundedCornerShape(Radii.Card),
            colors = CardDefaults.cardColors(containerColor = PaperSurface),
            border = BorderStroke(1.dp, Hairline),
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
            // 中性纸面卡片 + 左侧 3dp 状态色边
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(Sizes.StatusEdge)
                        .fillMaxHeight()
                        .background(statusAccent)
                )
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

                    Spacer(modifier = Modifier.height(Spacing.Sm))

                    Surface(
                        shape = RoundedCornerShape(Radii.Chip),
                        color = BadgeBackground,
                        modifier = Modifier.height(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.Sm, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (student.status == AttendanceStatus.PRESENT) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = statusAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(statusAccent, CircleShape)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(Spacing.Sm))
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = InkPrimary
                            )
                        }
                    }
                }
            }
        }

        // 长按弹出快捷状态菜单：统一中性文字 + 语义圆点
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            StatusMenuItem(
                label = "已到",
                accent = RedPen,
                checked = true,
                onClick = {
                    onSetStatus(AttendanceStatus.PRESENT)
                    showMenu = false
                }
            )
            StatusMenuItem(
                label = "迟到",
                accent = StatusLateInk,
                onClick = {
                    onSetStatus(AttendanceStatus.LATE)
                    showMenu = false
                }
            )
            StatusMenuItem(
                label = "请假（不发回放）",
                accent = StatusLeaveInk,
                onClick = {
                    onSetStatus(AttendanceStatus.LEAVE)
                    showMenu = false
                }
            )
            StatusMenuItem(
                label = "请假（发回放）",
                accent = StatusLeaveReplayInk,
                onClick = {
                    onSetStatus(AttendanceStatus.LEAVE_REPLAY)
                    showMenu = false
                }
            )
            StatusMenuItem(
                label = if (StudentCsvParser.isOfflineStudent(student)) "转线上" else "线上",
                accent = StatusOnlineInk,
                onClick = {
                    onSetStatus(AttendanceStatus.ONLINE)
                    showMenu = false
                }
            )
            StatusMenuItem(
                label = "设为未到",
                accent = StatusUncheckedInk,
                icon = Icons.Default.Close,
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
 * 状态菜单项：语义色只出现在 8dp 圆点上，文字始终是墨色。
 */
@Composable
private fun StatusMenuItem(
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    checked: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label, color = InkPrimary) },
        leadingIcon = {
            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                when {
                    checked -> Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                    icon != null -> Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                    else -> Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(accent, CircleShape)
                    )
                }
            }
        },
        onClick = onClick
    )
}
