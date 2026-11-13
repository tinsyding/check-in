package com.jf.checkin.ui.zip

import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
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
import com.jf.checkin.util.ShareUtil
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZipScreen(
    state: ZipUiState,
    onAddUris: (List<Uri>) -> Unit,
    onRemoveUri: (Uri) -> Unit,
    onClearUris: () -> Unit,
    onNameChange: (String) -> Unit,
    onCustomNameChange: (String) -> Unit,
    onToggleCustomName: (Boolean) -> Unit,
    onGenerate: () -> Unit,
    onBack: () -> Unit,
    onMessageShown: () -> Unit,
    onAutoShareConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_ZIP_IMAGES),
    ) { uris ->
        onAddUris(uris)
    }

    val openPicker: () -> Unit = {
        picker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onMessageShown()
        }
    }

    // 从相册等应用分享进来：自动按日期 + 姓名生成，并直接拉起系统分享
    LaunchedEffect(state.autoSharePending, state.build::class, state.uris.size) {
        if (!state.autoSharePending) return@LaunchedEffect
        when (val build = state.build) {
            is BuildState.Idle -> {
                if (state.uris.isNotEmpty()) onGenerate()
            }

            is BuildState.Ready -> {
                val shared = runCatching {
                    ShareUtil.shareZip(context, build.archive.file)
                }.onFailure {
                    Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
                }.isSuccess

                onAutoShareConsumed()
                if (shared) onBack()
            }

            is BuildState.Building -> Unit
        }
    }

    Scaffold(
        containerColor = PaperBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "图片打包",
                        style = MaterialTheme.typography.titleMedium,
                        color = InkPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = InkPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PaperSurface),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.Xxl)
                    .padding(top = Spacing.Sm),
            ) {
                ArchiveCard(
                    prefix = state.prefix,
                    name = state.name,
                    customNameEnabled = state.customNameEnabled,
                    customName = state.customName,
                    onNameChange = onNameChange,
                    onCustomNameChange = onCustomNameChange,
                    onToggleCustomName = onToggleCustomName,
                )

                SectionHeader(
                    count = state.uris.size,
                    onClear = onClearUris,
                )

                if (state.uris.isEmpty()) {
                    EmptyPick(
                        onPick = openPicker,
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = Spacing.Xxl),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(96.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = Spacing.Xxl),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.Sm),
                    ) {
                        itemsIndexed(
                            items = state.uris,
                            key = { _, uri -> uri.toString() },
                        ) { index, uri ->
                            ImageCell(
                                uri = uri,
                                index = index,
                                onRemove = { onRemoveUri(uri) },
                            )
                        }
                        item {
                            AddCell(onPick = openPicker)
                        }
                    }
                }
            }

            BottomAction(
                state = state,
                onGenerate = onGenerate,
                onShare = { file ->
                    runCatching { ShareUtil.shareZip(context, file) }
                        .onFailure {
                            Toast.makeText(context, "分享失败", Toast.LENGTH_SHORT).show()
                        }
                },
            )
        }
    }
}

@Composable
private fun ArchiveCard(
    prefix: String,
    name: String,
    customNameEnabled: Boolean,
    customName: String,
    onNameChange: (String) -> Unit,
    onCustomNameChange: (String) -> Unit,
    onToggleCustomName: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radii.Card),
        colors = CardDefaults.cardColors(containerColor = PaperSurface),
        border = BorderStroke(1.dp, Hairline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.Xl, vertical = Spacing.Xl),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "压缩包",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkTertiary,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (customNameEnabled) "恢复自动命名" else "自定义",
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radii.Chip))
                        .clickable { onToggleCustomName(!customNameEnabled) }
                        .padding(horizontal = Spacing.Sm, vertical = Spacing.Xs),
                )
            }
            Spacer(modifier = Modifier.height(Spacing.Md))
            if (customNameEnabled) {
                CustomNameField(
                    name = customName,
                    onNameChange = onCustomNameChange,
                )
                Spacer(modifier = Modifier.height(Spacing.Sm))
                Text(
                    text = "可输入任意文件名，.zip 会自动补上",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkTertiary,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DateStamp(prefix)
                    Text(
                        text = "-",
                        style = MaterialTheme.typography.titleLarge,
                        color = InkSecondary,
                        modifier = Modifier.padding(horizontal = Spacing.Xs),
                    )
                    NameField(
                        name = name,
                        onNameChange = onNameChange,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = ".zip",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkTertiary,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun DateStamp(prefix: String) {
    Box(
        modifier = Modifier
            .background(PaperSunken, RoundedCornerShape(Radii.Chip))
            .border(1.dp, Hairline, RoundedCornerShape(Radii.Chip))
            .padding(horizontal = Spacing.Sm, vertical = Spacing.Xs),
    ) {
        Text(
            text = prefix,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
            ),
            color = InkSecondary,
        )
    }
}

@Composable
private fun NameField(
    name: String,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(
                    color = if (focused) InkPrimary.copy(alpha = 0.6f) else Hairline,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(bottom = Spacing.Xs),
    ) {
        if (name.isEmpty() && !focused) {
            Text(
                text = "姓名",
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = InkTertiary.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Medium,
                fontSize = 20.sp,
                color = InkPrimary,
            ),
            cursorBrush = SolidColor(InkPrimary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
        )
    }
}

@Composable
private fun CustomNameField(
    name: String,
    onNameChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(
                    color = if (focused) InkPrimary.copy(alpha = 0.6f) else Hairline,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(bottom = Spacing.Xs),
    ) {
        if (name.isEmpty() && !focused) {
            Text(
                text = "完整文件名",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = InkTertiary.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.CenterStart),
            )
        }
        BasicTextField(
            value = name,
            onValueChange = onNameChange,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                color = InkPrimary,
            ),
            cursorBrush = SolidColor(InkPrimary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
        )
    }
}

@Composable
private fun SectionHeader(count: Int, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.Xxl, bottom = Spacing.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "图片",
            style = MaterialTheme.typography.titleSmall,
            color = InkPrimary,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (count > 0) {
            Text(
                text = "$count 张",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                ),
                color = InkTertiary,
            )
            Spacer(modifier = Modifier.width(Spacing.Md))
            Text(
                text = "清空",
                style = MaterialTheme.typography.labelMedium,
                color = InkSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radii.Chip))
                    .clickable(onClick = onClear)
                    .padding(horizontal = Spacing.Sm, vertical = Spacing.Xs),
            )
        }
    }
}

@Composable
private fun ImageCell(uri: Uri, index: Int, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(Radii.Control))
            .background(PaperSunken),
    ) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            text = (index + 1).toString(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontFeatureSettings = "tnum",
            ),
            color = InkPrimary,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(Spacing.Xs)
                .background(PaperSurface.copy(alpha = 0.92f), RoundedCornerShape(Radii.Chip))
                .padding(horizontal = Spacing.Xs, vertical = 2.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Spacing.Xs)
                .size(24.dp)
                .clip(RoundedCornerShape(Radii.Chip))
                .background(PaperSurface.copy(alpha = 0.92f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "移除",
                tint = InkSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun AddCell(onPick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(Radii.Control))
            .border(1.dp, Hairline, RoundedCornerShape(Radii.Control))
            .clickable(onClick = onPick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = "添加图片",
            tint = InkTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun EmptyPick(
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.Card))
            .border(1.dp, Hairline, RoundedCornerShape(Radii.Card))
            .clickable(onClick = onPick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = InkTertiary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.height(Spacing.Md))
        Text(
            text = "选择图片",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSecondary,
        )
        Spacer(modifier = Modifier.height(Spacing.Xs))
        Text(
            text = "最多 $MAX_ZIP_IMAGES 张，不压缩原图",
            style = MaterialTheme.typography.bodySmall,
            color = InkTertiary,
        )
    }
}

@Composable
private fun BottomAction(
    state: ZipUiState,
    onGenerate: () -> Unit,
    onShare: (File) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PaperSurface),
    ) {
        HorizontalDivider(thickness = 1.dp, color = Hairline)
        Column(
            modifier = Modifier.padding(
                horizontal = Spacing.Xxl,
                vertical = Spacing.Lg,
            ),
        ) {
            when (val build = state.build) {
                is BuildState.Ready -> {
                    ReadySummary(build.archive)
                    Spacer(modifier = Modifier.height(Spacing.Md))
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                        OutlinedButton(
                            onClick = onGenerate,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(Radii.Control),
                            border = BorderStroke(1.dp, Hairline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = InkPrimary,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(Spacing.Sm))
                            Text("重新生成", style = MaterialTheme.typography.labelLarge)
                        }
                        Button(
                            onClick = { onShare(build.archive.file) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(Radii.Control),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = InkPrimary,
                                contentColor = InkOnDark,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(Spacing.Sm))
                            Text("分享", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                is BuildState.Building -> {
                    Text(
                        text = "${build.done} / ${build.total}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontFeatureSettings = "tnum",
                        ),
                        color = InkTertiary,
                    )
                    Spacer(modifier = Modifier.height(Spacing.Sm))
                    LinearProgressIndicator(
                        progress = {
                            if (build.total == 0) 0f else build.done.toFloat() / build.total
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = InkPrimary,
                        trackColor = Hairline,
                    )
                    Spacer(modifier = Modifier.height(Spacing.Lg))
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(Radii.Control),
                    ) {
                        Text("生成中", style = MaterialTheme.typography.labelLarge)
                    }
                }

                else -> {
                    Button(
                        onClick = onGenerate,
                        enabled = state.uris.isNotEmpty() &&
                            (!state.customNameEnabled || state.customName.isNotBlank()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(Radii.Control),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = InkPrimary,
                            contentColor = InkOnDark,
                        ),
                    ) {
                        Text("生成压缩包", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadySummary(archive: ReadyArchive) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = archive.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = InkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(Spacing.Xs))
            Text(
                text = "${archive.count} 张 · ${
                    Formatter.formatShortFileSize(context, archive.size)
                }",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontFeatureSettings = "tnum",
                ),
                color = InkTertiary,
            )
        }
    }
}
