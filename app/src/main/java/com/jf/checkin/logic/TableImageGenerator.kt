package com.jf.checkin.logic

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
        val headerRowHeight = 65
        // 去掉总人数出勤率那一栏后，顶部 banner 高度缩减至 110
        val bannerHeight = 110
        val footerHeight = 65
        val tableHeight = headerRowHeight + (totalCount * rowHeight)
        val height = bannerHeight + tableHeight + footerHeight + padding * 2

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 背景纯白
        canvas.drawColor(Color.WHITE)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E5E7EB")
            strokeWidth = 2f
            style = Paint.Style.STROKE
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        var currentY = padding.toFloat()

        // 1. 顶部标题区域
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 40f
        textPaint.color = Color.parseColor("#111827")
        canvas.drawText("$classCode 签到表", padding.toFloat(), currentY + 42f, textPaint)

        // 副标题栏：展示上课时间、任课教师姓名、导出时间
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = 23f
        textPaint.color = Color.parseColor("#6B7280")
        val nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val teacherPart = if (teacherName.isNotBlank()) "   |   任课教师: $teacherName" else ""
        val subtitleText = "上课时间: $classTime$teacherPart   |   导出时间: $nowStr"
        canvas.drawText(subtitleText, padding.toFloat(), currentY + 84f, textPaint)

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

        // 表头背景
        bgPaint.color = Color.parseColor("#F3F4F6")
        canvas.drawRect(tableLeft, currentY, tableRight, currentY + headerRowHeight, bgPaint)

        // 表头文字
        textPaint.textSize = 24f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.color = Color.parseColor("#374151")
        var colX = tableLeft
        for (i in headers.indices) {
            val text = headers[i]
            val textWidth = textPaint.measureText(text)
            val centerX = colX + (colWidths[i] - textWidth) / 2f
            canvas.drawText(text, centerX, currentY + 42f, textPaint)
            colX += colWidths[i]
        }

        // 表头下边线
        linePaint.color = Color.parseColor("#D1D5DB")
        canvas.drawLine(tableLeft, currentY + headerRowHeight, tableRight, currentY + headerRowHeight, linePaint)
        currentY += headerRowHeight

        // 数据行
        for (rowIndex in students.indices) {
            val student = students[rowIndex]
            val rowY = currentY + rowIndex * rowHeight

            // 斑马条纹
            if (rowIndex % 2 == 1) {
                bgPaint.color = Color.parseColor("#FBFBFD")
                canvas.drawRect(tableLeft, rowY, tableRight, rowY + rowHeight, bgPaint)
            }

            // 行内底线
            linePaint.color = Color.parseColor("#F3F4F6")
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

            textPaint.typeface = Typeface.DEFAULT
            textPaint.textSize = 23f
            textPaint.color = Color.parseColor("#1F2937")

            for (i in rowValues.indices) {
                val value = rowValues[i]
                val textWidth = textPaint.measureText(value)
                val textX = curX + (colWidths[i] - textWidth) / 2f
                canvas.drawText(value, textX, rowY + 38f, textPaint)
                curX += colWidths[i]
            }

            // 最后一列：签到状态
            val status = student.status
            val statusText: String
            val statusColor: Int
            if (isMovedOut) {
                statusText = "调出"
                statusColor = Color.parseColor("#0F766E") // 深青色标识调出
            } else {
                when (status) {
                    AttendanceStatus.PRESENT -> {
                        statusText = "√"
                        statusColor = Color.parseColor("#10B981") // 绿色对号
                    }
                    AttendanceStatus.LATE -> {
                        statusText = "迟到"
                        statusColor = Color.parseColor("#F59E0B") // 琥珀橙
                    }
                    AttendanceStatus.LEAVE -> {
                        statusText = "请假"
                        statusColor = Color.parseColor("#3B82F6") // 科技蓝
                    }
                    AttendanceStatus.ONLINE -> {
                        statusText = if (student.classFormat in StudentCsvParser.OFFLINE_FORMATS) "转线上" else "线上"
                        statusColor = Color.parseColor("#8B5CF6") // 优雅紫
                    }
                    AttendanceStatus.UNCHECKED -> {
                        statusText = "未到"
                        statusColor = Color.parseColor("#EF4444") // 浅红
                    }
                }
            }

            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = if (!isMovedOut && status == AttendanceStatus.PRESENT) 30f else 23f
            textPaint.color = statusColor
            val stWidth = textPaint.measureText(statusText)
            val stX = curX + (colWidths[5] - stWidth) / 2f
            canvas.drawText(statusText, stX, rowY + 39f, textPaint)
            curX += colWidths[5]

            // 备注列（调出去向、调入来源等，过长截断；无备注的临时调入显示原班）
            textPaint.typeface = Typeface.DEFAULT
            textPaint.textSize = 20f
            var remark = student.remark
            if (isMovedOut) {
                // 调到其他班的，备注为“转到某某班”
                val targetCode = student.classCode
                val targetLabel = if (targetCode.endsWith("班")) "转到$targetCode" else "转到${targetCode}班"
                remark = if (remark.isNotBlank()) "$targetLabel ($remark)" else targetLabel
                textPaint.color = Color.parseColor("#0F766E")
            } else {
                if (remark.isBlank() && student.originalClassCode.isNotBlank() &&
                    student.originalClassCode != student.classCode
                ) {
                    remark = "原${student.originalClassCode}调入"
                }
                textPaint.color = Color.parseColor("#4F46E5")
            }
            if (textPaint.measureText(remark) > colWidths[6] - 16f && remark.length > 14) {
                remark = remark.take(14) + "…"
            }
            val remarkWidth = textPaint.measureText(remark)
            val remarkX = curX + (colWidths[6] - remarkWidth) / 2f
            canvas.drawText(remark, remarkX, rowY + 38f, textPaint)
        }

        // 表格最外框
        linePaint.color = Color.parseColor("#D1D5DB")
        canvas.drawRect(tableLeft, currentY - headerRowHeight, tableRight, currentY + totalCount * rowHeight, linePaint)

        // 3. 页脚
        val footerY = currentY + totalCount * rowHeight + 36f
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = 20f
        textPaint.color = Color.parseColor("#9CA3AF")
        val footerText = "— 本签到记录由「班级点名签到」App 自动生成 —"
        val fw = textPaint.measureText(footerText)
        canvas.drawText(footerText, (width - fw) / 2f, footerY, textPaint)

        return bitmap
    }

    /**
     * 生成文件用于“发送给”（存放在 cache 共享目录）
     */
    fun generateTableImageFile(
        context: Context,
        classCode: String,
        classTime: String,
        teacherName: String,
        students: List<Student>
    ): File {
        val bitmap = createTableBitmap(classCode, classTime, teacherName, students)
        val exportDir = File(context.cacheDir, "attendance_exports").apply { mkdirs() }
        val filename = "checkin_${classCode}_${System.currentTimeMillis()}.png"
        val file = File(exportDir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }

    /**
     * 下载/保存图片到系统相册 (Pictures/班级签到表)
     */
    fun saveToGallery(
        context: Context,
        classCode: String,
        classTime: String,
        teacherName: String,
        students: List<Student>
    ): Boolean {
        val bitmap = createTableBitmap(classCode, classTime, teacherName, students)
        val filename = "签到表_${classCode}_${System.currentTimeMillis()}.png"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/班级签到表")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            return true
        }
        return false
    }
}
