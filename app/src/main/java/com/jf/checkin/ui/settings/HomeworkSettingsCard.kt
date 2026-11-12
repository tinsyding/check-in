package com.jf.checkin.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.jf.checkin.data.repository.UserPrefRepository
import com.jf.checkin.logic.homework.TextProcessor
import com.jf.checkin.service.TextExpanderService
import com.jf.checkin.ui.theme.Hairline
import com.jf.checkin.ui.theme.InkOnDark
import com.jf.checkin.ui.theme.InkPrimary
import com.jf.checkin.ui.theme.InkSecondary
import com.jf.checkin.ui.theme.InkTertiary
import com.jf.checkin.ui.theme.PaperSunken
import com.jf.checkin.ui.theme.Radii
import com.jf.checkin.ui.theme.Spacing
import com.jf.checkin.ui.theme.StatusLateInk
import com.jf.checkin.util.AccessibilityUtil

/**
 * 错题快速转换：原 qqq 安卓端的配置与测试入口。
 * 无障碍服务需要用户在系统设置中手动开启，这里只做状态展示和跳转。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeworkSettingsCard(prefRepository: UserPrefRepository) {
    val context = LocalContext.current

    var serviceEnabled by remember { mutableStateOf(false) }
    var triggerEnabled by remember { mutableStateOf(prefRepository.homeworkTriggerEnabled) }
    var triggerWord by remember { mutableStateOf(prefRepository.homeworkTriggerWord) }
    var template by remember { mutableStateOf(prefRepository.homeworkTemplate) }
    var separator by remember { mutableStateOf(prefRepository.homeworkSeparator) }
    var splitMode by remember { mutableStateOf(prefRepository.homeworkSplitMode) }
    var testInput by remember { mutableStateOf("135qqq") }

    // 从系统设置返回或生命周期变更时刷新服务状态
    val lifecycleOwner = LocalLifecycleOwner.current
    fun refreshServiceStatus() {
        serviceEnabled = AccessibilityUtil.isServiceEnabled(context, TextExpanderService::class.java)
    }

    LaunchedEffect(Unit) {
        refreshServiceStatus()
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshServiceStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val preview = remember(testInput, triggerWord, template, separator, splitMode) {
        val input = testInput.trim()
        val trigger = triggerWord.trim()
        val rawNumbers = if (trigger.isNotEmpty() && input.endsWith(trigger, ignoreCase = true)) {
            input.dropLast(trigger.length).trim()
        } else {
            input
        }
        val numbers = TextProcessor.parseNumbers(rawNumbers, splitMode)
        if (numbers.isEmpty()) "" else {
            TextProcessor.formatOutput(
                numbers = numbers,
                template = template.ifBlank { TextProcessor.DEFAULT_TEMPLATE },
                separator = separator,
            )
        }
    }

    SettingsCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
        ) {
            Icon(
                Icons.Default.EditNote,
                contentDescription = null,
                tint = InkSecondary,
                modifier = Modifier.size(20.dp),
            )
            Column {
                Text(
                    text = "错题快速转换",
                    style = MaterialTheme.typography.titleSmall,
                    color = InkPrimary,
                )
                Text(
                    text = "输入 135qqq 自动展开为错题文案，也可长按选中题号使用系统菜单。",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkTertiary,
                )
            }
        }

        // 服务状态
        Surface(
            shape = RoundedCornerShape(Radii.Control),
            color = PaperSunken,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    refreshServiceStatus()
                    val msg = if (serviceEnabled) "无障碍服务已开启并正在运行" else "无障碍服务未开启，请在系统设置中启用"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (serviceEnabled) InkPrimary else StatusLateInk,
                            shape = CircleShape,
                        ),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (serviceEnabled) "无障碍服务已开启" else "无障碍服务未开启",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkPrimary,
                    )
                    Text(
                        text = if (serviceEnabled) {
                            "在任意输入框输入 135qqq 即可自动展开。"
                        } else {
                            "需要到系统设置中手动开启，点击本卡片可随时刷新检测。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = InkTertiary,
                    )
                }
                OutlinedButton(
                    onClick = {
                        refreshServiceStatus()
                        if (serviceEnabled) {
                            Toast.makeText(context, "无障碍服务正在正常运行", Toast.LENGTH_SHORT).show()
                        } else {
                            if (!AccessibilityUtil.openAccessibilitySettings(context)) {
                                Toast.makeText(context, "无法打开系统设置", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = Spacing.Md, vertical = Spacing.Xs),
                ) {
                    Text(if (serviceEnabled) "已开启" else "去开启")
                }
            }
        }

        // 自动展开开关
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "输入触发词后自动展开",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkPrimary,
                )
                Text(
                    text = "关闭后仍可长按选中题号，从系统菜单转换。",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkTertiary,
                )
            }
            Switch(
                checked = triggerEnabled,
                onCheckedChange = {
                    triggerEnabled = it
                    prefRepository.homeworkTriggerEnabled = it
                },
            )
        }

        // 触发词
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
        ) {
            OutlinedTextField(
                value = triggerWord,
                onValueChange = { triggerWord = it.take(12) },
                label = { Text("触发词") },
                placeholder = { Text("qqq") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = {
                    val word = triggerWord.trim()
                    if (word.isEmpty()) {
                        Toast.makeText(context, "触发词不能为空", Toast.LENGTH_SHORT).show()
                    } else {
                        prefRepository.homeworkTriggerWord = word
                        Toast.makeText(context, "触发词已保存：$word", Toast.LENGTH_SHORT).show()
                    }
                },
            ) {
                Text("保存")
            }
        }

        // 模板
        Text(
            text = "文案模板（{nums} 为题号占位符）",
            style = MaterialTheme.typography.labelMedium,
            color = InkSecondary,
        )
        OutlinedTextField(
            value = template,
            onValueChange = {
                template = it
                if (it.isNotBlank()) prefRepository.homeworkTemplate = it
            },
            placeholder = { Text(TextProcessor.DEFAULT_TEMPLATE) },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
        ) {
            listOf(
                "错题题号" to "本次作业的错题题号为：{nums}",
                "错题数量" to "本次作业的错题数为：{nums}",
                "简明" to "错题订正：{nums}",
            ).forEach { (label, preset) ->
                QuickChip(label = label) {
                    template = preset
                    prefRepository.homeworkTemplate = preset
                }
            }
        }

        // 分隔符
        Text(
            text = "题号分隔符",
            style = MaterialTheme.typography.labelMedium,
            color = InkSecondary,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
        ) {
            listOf(
                "，" to "中文逗号",
                "、" to "顿号",
                "," to "英文逗号",
                " " to "空格",
            ).forEach { (value, label) ->
                ChoiceChip(
                    label = label,
                    selected = separator == value,
                    onClick = {
                        separator = value
                        prefRepository.homeworkSeparator = value
                    },
                )
            }
        }

        // 拆号方式
        Text(
            text = "拆号方式",
            style = MaterialTheme.typography.labelMedium,
            color = InkSecondary,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
        ) {
            listOf(
                TextProcessor.MODE_SMART to "智能切分",
                TextProcessor.MODE_CONTINUOUS to "逐位拆分",
                TextProcessor.MODE_SEPARATED to "按分隔符",
            ).forEach { (value, label) ->
                ChoiceChip(
                    label = label,
                    selected = splitMode == value,
                    onClick = {
                        splitMode = value
                        prefRepository.homeworkSplitMode = value
                    },
                )
            }
        }
        Text(
            text = when (splitMode) {
                TextProcessor.MODE_CONTINUOUS -> "逐位拆分：157 → 1，5，7"
                TextProcessor.MODE_SEPARATED -> "按分隔符：135 → 135；1，3 → 1，3"
                else -> "智能切分：135 → 1，3，5；157111325 → 1，5，7，11，13，25"
            },
            style = MaterialTheme.typography.labelSmall,
            color = InkTertiary,
        )

        // 测试预览
        OutlinedTextField(
            value = testInput,
            onValueChange = { testInput = it },
            label = { Text("试一下") },
            placeholder = { Text("135qqq") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Surface(
            shape = RoundedCornerShape(Radii.Control),
            color = PaperSunken,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(Spacing.Md)) {
                Text(
                    text = "预览",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkTertiary,
                )
                Spacer(modifier = Modifier.height(Spacing.Xs))
                Text(
                    text = preview.ifEmpty { "输入数字，例如 135 或 135$triggerWord" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (preview.isEmpty()) InkTertiary else InkPrimary,
                )
            }
        }
        if (preview.isNotEmpty()) {
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("错题文案", preview))
                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(Spacing.Sm))
                Text("复制预览结果")
            }
        }

        Text(
            text = "开启方式：点「去开启」→ 在系统无障碍列表找到「班级点名签到」→ 打开「错题自动展开」。系统可能提示读取屏幕内容，服务只用于识别当前输入框文字并就地替换，不会上传或保存。",
            style = MaterialTheme.typography.labelSmall,
            color = InkTertiary,
        )
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(Radii.Chip),
        color = if (selected) InkPrimary else PaperSunken,
        border = BorderStroke(1.dp, if (selected) InkPrimary else Hairline),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) InkOnDark else InkSecondary,
            modifier = Modifier.padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
        )
    }
}

@Composable
private fun QuickChip(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(Radii.Chip),
        color = Color.Transparent,
        border = BorderStroke(1.dp, Hairline),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = InkSecondary,
            modifier = Modifier.padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
        )
    }
}
