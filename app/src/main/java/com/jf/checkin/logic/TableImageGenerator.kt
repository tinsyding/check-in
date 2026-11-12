package com.jf.checkin.logic

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.jf.checkin.data.model.AttendanceStatus
import com.jf.checkin.data.model.Student
import com.jf.checkin.data.parser.StudentCsvParser
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object TableImageGenerator {

    private val regularTypeface: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val mediumTypeface: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    /**
     * 生成签到表 Bitmap
     */
    fun createTableBitmap(
        classCode: String,
        classTime: String,
        teacherName: String,
        students: List<Student>
    ): Bitmap {
        val totalCount = students.size
        val width = 1400
        val padding = 48
        val rowHeight = 60
        val headerRowHeight = 64
        val bannerHeight = 112
        val footerHeight = 64
        val tableHeight = headerRowHeight + (totalCount * rowHeight)
        val height = bannerHeight + tableHeight + footerHeight + padding * 2

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 暖纸底
        canvas.drawColor(ExportPalette.Paper)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = regularTypeface
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ExportPalette.Hairline
            strokeWidth = 1.2f
            style = Paint.Style.STROKE
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ExportPalette.RedPen
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        var currentY = padding.toFloat()

        // 1. 顶部标题区域
        textPaint.typeface = mediumTypeface
        textPaint.textSize = 40f
        textPaint.color = ExportPalette.InkPrimary
        canvas.drawText("$classCode 签到表", padding.toFloat(), currentY + 44f, textPaint)

        // 副标题：上课时间、任课教师、导出时间
        textPaint.typeface = regularTypeface
        textPaint.textSize = 22f
        textPaint.color = ExportPalette.InkSecondary
        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val metaParts = buildList {
            add("上课时间 $classTime")
            if (teacherName.isNotBlank()) add("任课教师 $teacherName")
            add("导出 $nowStr")
        }
        canvas.drawText(metaParts.joinToString("   ·   "), padding.toFloat(), currentY + 84f, textPaint)

        currentY += bannerHeight

        // 2. 绘制表格
        val colWidths = floatArrayOf(
            150f, // 学号
            150f, // 姓名
            210f, // 班级名称
            230f, // 上课时间
            110f, // 上课形式
            120f, // 签到
            334f  // 备注
        )
        val headers = arrayOf("学号", "姓名", "班级名称", "上课时间", "上课形式", "签到", "备注")
        val tableLeft = padding.toFloat()
        val tableRight = (width - padding).toFloat()
        val tableTop = currentY

        // 表头背景：下沉纸面
        bgPaint.color = ExportPalette.PaperSunken
        canvas.drawRect(tableLeft, tableTop, tableRight, tableTop + headerRowHeight, bgPaint)

        // 表头文字
        textPaint.textSize = 23f
        textPaint.typeface = mediumTypeface
        textPaint.color = ExportPalette.InkSecondary
        var colX = tableLeft
        for (i in headers.indices) {
            drawCenteredText(
                canvas = canvas,
                text = headers[i],
                centerX = colX + colWidths[i] / 2f,
                baselineY = tableTop + 40f,
                paint = textPaint
            )
            colX += colWidths[i]
        }

        // 表头上下发丝线
        linePaint.color = ExportPalette.Hairline
        canvas.drawLine(tableLeft, tableTop, tableRight, tableTop, linePaint)
        linePaint.color = ExportPalette.InkTertiary
        linePaint.strokeWidth = 1.6f
        canvas.drawLine(tableLeft, tableTop + headerRowHeight, tableRight, tableTop + headerRowHeight, linePaint)
        linePaint.strokeWidth = 1.2f
        currentY += headerRowHeight

        // 数据行
        for (rowIndex in students.indices) {
            val student = students[rowIndex]
            val rowY = currentY + rowIndex * rowHeight

            // 行底线
            linePaint.color = ExportPalette.Hairline
            canvas.drawLine(tableLeft, rowY + rowHeight, tableRight, rowY + rowHeight, linePaint)

            val isMovedOut = student.originalClassCode.isNotBlank() &&
                    student.originalClassCode == classCode &&
                    student.classCode != classCode

            var curX = tableLeft
            val rowValues = arrayOf(
                student.studentId,
                student.name,
                classCode,
                classTime,
                student.classFormat
            )

            for (i in rowValues.indices) {
                when (i) {
                    0 -> {
                        // 学号
                        textPaint.typeface = regularTypeface
                        textPaint.textSize = 22f
                        textPaint.color = ExportPalette.InkTertiary
                    }
                    1 -> {
                        // 姓名：整行视觉锚点
                        textPaint.typeface = mediumTypeface
                        textPaint.textSize = 24f
                        textPaint.color = ExportPalette.InkPrimary
                    }
                    else -> {
                        textPaint.typeface = regularTypeface
                        textPaint.textSize = 22f
                        textPaint.color = ExportPalette.InkSecondary
                    }
                }
                drawCenteredText(
                    canvas = canvas,
                    text = rowValues[i],
                    centerX = curX + colWidths[i] / 2f,
                    baselineY = rowY + 38f,
                    paint = textPaint
                )
                curX += colWidths[i]
            }

            // 签到状态列
            val status = student.status
            val statusText: String
            val statusColor: Int
            if (isMovedOut) {
                statusText = "调出"
                statusColor = ExportPalette.InkSecondary
            } else {
                when (status) {
                    AttendanceStatus.PRESENT -> {
                        statusText = ""
                        statusColor = ExportPalette.RedPen
                    }
                    AttendanceStatus.LATE -> {
                        statusText = "迟到"
                        statusColor = ExportPalette.StatusLate
                    }
                    AttendanceStatus.LEAVE -> {
                        statusText = "请假"
                        statusColor = ExportPalette.StatusLeave
                    }
                    AttendanceStatus.LEAVE_REPLAY -> {
                        statusText = "请假·回放"
                        statusColor = ExportPalette.StatusLeaveReplay
                    }
                    AttendanceStatus.ONLINE -> {
                        statusText = if (StudentCsvParser.isOfflineStudent(student)) "转线上" else "线上"
                        statusColor = ExportPalette.StatusOnline
                    }
                    AttendanceStatus.UNCHECKED -> {
                        statusText = "未到"
                        statusColor = ExportPalette.StatusUnchecked
                    }
                }
            }

            val statusCenterX = curX + colWidths[5] / 2f
            if (!isMovedOut && status == AttendanceStatus.PRESENT) {
                // 红笔勾：已到
                drawPenCheck(canvas, statusCenterX, rowY + rowHeight / 2f, checkPaint)
            } else {
                textPaint.typeface = mediumTypeface
                textPaint.textSize = if (statusText.length >= 5) 21f else 23f
                textPaint.color = statusColor
                drawCenteredText(
                    canvas = canvas,
                    text = statusText,
                    centerX = statusCenterX,
                    baselineY = rowY + 38f,
                    paint = textPaint
                )
            }
            curX += colWidths[5]

            // 备注列（调出去向、调入来源等，过长截断）
            textPaint.typeface = regularTypeface
            textPaint.textSize = 20f
            textPaint.color = ExportPalette.InkTertiary
            var remark = student.remark
            if (isMovedOut) {
                val targetCode = student.classCode
                val targetLabel = if (targetCode.endsWith("班")) "转到$targetCode" else "转到${targetCode}班"
                remark = if (remark.isNotBlank()) "$targetLabel ($remark)" else targetLabel
            } else {
                if (remark.isBlank() && student.originalClassCode.isNotBlank() &&
                    student.originalClassCode != student.classCode
                ) {
                    remark = "原${student.originalClassCode}调入"
                }
            }
            if (textPaint.measureText(remark) > colWidths[6] - 16f && remark.length > 14) {
                remark = remark.take(14) + "…"
            }
            drawCenteredText(
                canvas = canvas,
                text = remark,
                centerX = curX + colWidths[6] / 2f,
                baselineY = rowY + 38f,
                paint = textPaint
            )
        }

        // 表格外框
        val tableBottom = currentY + totalCount * rowHeight
        linePaint.color = ExportPalette.Hairline
        canvas.drawRect(tableLeft, tableTop, tableRight, tableBottom, linePaint)

        // 3. 页脚
        val footerY = tableBottom + 42f
        textPaint.typeface = regularTypeface
        textPaint.textSize = 20f
        textPaint.color = ExportPalette.InkTertiary
        val footerText = "本签到记录由「班级点名签到」生成"
        val fw = textPaint.measureText(footerText)
        canvas.drawText(footerText, (width - fw) / 2f, footerY, textPaint)

        return bitmap
    }

    private fun drawCenteredText(
        canvas: Canvas,
        text: String,
        centerX: Float,
        baselineY: Float,
        paint: Paint
    ) {
        if (text.isEmpty()) return
        val textWidth = paint.measureText(text)
        canvas.drawText(text, centerX - textWidth / 2f, baselineY, paint)
    }

    /** 一笔红勾，代替旧版绿色对号字符 */
    private fun drawPenCheck(canvas: Canvas, centerX: Float, centerY: Float, paint: Paint) {
        val path = Path().apply {
            moveTo(centerX - 15f, centerY - 1f)
            lineTo(centerX - 4f, centerY + 10f)
            lineTo(centerX + 17f, centerY - 14f)
        }
        canvas.drawPath(path, paint)
    }

    /**
     * 生成文件用于“发送给”（存放在 cache 共享目录）
     */
    fun generateTableImageFile(
        context: Context,
        classCode: String,
        classTime: String,
        teacherName: String,
        students: List<Student>,
        bitmap: Bitmap? = null
    ): File {
        val targetBitmap = bitmap ?: createTableBitmap(classCode, classTime, teacherName, students)
        try {
            val exportDir = File(context.cacheDir, "attendance_exports").apply { mkdirs() }
            val filename = "checkin_${classCode}_${System.currentTimeMillis()}.png"
            val file = File(exportDir, filename)
            FileOutputStream(file).use { out ->
                targetBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            return file
        } finally {
            // 仅回收本函数内部创建的 Bitmap，外部传入的由调用方负责回收
            if (bitmap == null) targetBitmap.recycle()
        }
    }

    /**
     * 下载/保存图片到系统相册 (Pictures/班级签到表)
     */
    fun saveToGallery(
        context: Context,
        classCode: String,
        classTime: String,
        teacherName: String,
        students: List<Student>,
        bitmap: Bitmap? = null
    ): Boolean {
        val targetBitmap = bitmap ?: createTableBitmap(classCode, classTime, teacherName, students)
        try {
            val filename = "签到表_${classCode}_${System.currentTimeMillis()}.png"
            val resolver = context.contentResolver

            // Android 8/9：直接写入公共 Pictures 目录，再插入 MediaStore 供相册索引
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val targetDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "班级签到表"
                ).apply { mkdirs() }
                val targetFile = File(targetDir, filename)
                FileOutputStream(targetFile).use { out ->
                    targetBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.DATA, targetFile.absolutePath)
                }
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                return true
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/班级签到表")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        targetBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    return true
                }
            }
            return false
        } finally {
            // 仅回收本函数内部创建的 Bitmap，外部传入的由调用方负责回收
            if (bitmap == null) targetBitmap.recycle()
        }
    }
}
