package com.jf.checkin.logic

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.jf.checkin.data.model.Student
import java.io.File
import java.io.FileOutputStream

enum class NameplateFontStyle(val label: String) {
    CURSIVE("书法行楷"),
    BOLD_STANDARD("厚重粗体 (推荐防发虚)")
}

object NameplatePdfGenerator {

    // A4 纸纵向尺寸 (点数, 72 DPI 标准: 210mm x 297mm -> 595 x 842)
    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842

    /**
     * 生成并构建班级席卡 PdfDocument
     * @param onlyFirstStudent 为 true 时仅生成第 1 位学生的席卡（单张纸测试打印）
     * @param fontStyle 席卡姓名文字风格 (书法行楷 / 厚重粗体)
     */
    fun buildPdfDocument(
        context: Context,
        classCode: String,
        students: List<Student>,
        onlyFirstStudent: Boolean = false,
        fontStyle: NameplateFontStyle = NameplateFontStyle.CURSIVE
    ): PdfDocument {
        val document = PdfDocument()
        val targetStudents = if (onlyFirstStudent) students.take(1) else students

        // 根据样式选择字体
        val targetTypeface: Typeface = when (fontStyle) {
            NameplateFontStyle.CURSIVE -> {
                try {
                    Typeface.createFromAsset(context.assets, "fonts/cursive.ttf")
                } catch (_: Exception) {
                    Typeface.create(Typeface.SERIF, Typeface.BOLD)
                }
            }
            NameplateFontStyle.BOLD_STANDARD -> {
                Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
        }

        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = targetTypeface
            textSize = 110f
            color = Color.BLACK // 纯黑避免激光打印机半色调网点抖动
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = if (fontStyle == NameplateFontStyle.BOLD_STANDARD) 1.6f else 2.2f // 物理加厚笔锋边缘，即使碳粉不足也浓墨实心
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        val idPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 25f
            color = Color.BLACK
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = 0.8f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        val foldLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#999999")
            strokeWidth = 1.0f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
        }

        val foldTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT
            textSize = 10f
            color = Color.parseColor("#888888")
            textAlign = Paint.Align.CENTER
        }

        val midY = A4_HEIGHT / 2f
        val centerX = A4_WIDTH / 2f
        val baseFlapHeight = 115f // 两侧向内弯折形成底座的边宽 (约 40mm)
        val faceHeight = midY - baseFlapHeight // 单个展示面高度 (约 108mm)
        val upperCenterY = baseFlapHeight + faceHeight / 2f // 上半面中心 (268f)
        val lowerCenterY = midY + faceHeight / 2f // 下半面中心 (574f)
        val bottomFoldY = A4_HEIGHT - baseFlapHeight // 底部折线 (727f)

        for ((index, student) in targetStudents.withIndex()) {
            val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, index + 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            // 背景纯白
            canvas.drawColor(Color.WHITE)

            // 1. 顶部底座折线与说明（顶部向内折）
            val pathTop = Path().apply {
                moveTo(24f, baseFlapHeight)
                lineTo(A4_WIDTH - 24f, baseFlapHeight)
            }
            canvas.drawPath(pathTop, foldLinePaint)
            canvas.drawText("--- 底座折线 (向内弯折) ---", centerX, baseFlapHeight - 8f, foldTextPaint)
            canvas.drawText("【 底座面 (向内折) 】", centerX, baseFlapHeight / 2f + 4f, foldTextPaint)

            // 2. 中间对折线（台卡顶部山线）
            val pathMid = Path().apply {
                moveTo(24f, midY)
                lineTo(A4_WIDTH - 24f, midY)
            }
            canvas.drawPath(pathMid, foldLinePaint)
            canvas.drawText("--- 中心对折线 (顶部对折) ---", centerX, midY - 8f, foldTextPaint)

            // 3. 底部底座折线与说明（底部向内折）
            val pathBottom = Path().apply {
                moveTo(24f, bottomFoldY)
                lineTo(A4_WIDTH - 24f, bottomFoldY)
            }
            canvas.drawPath(pathBottom, foldLinePaint)
            canvas.drawText("--- 底座折线 (向内弯折) ---", centerX, bottomFoldY - 8f, foldTextPaint)
            canvas.drawText("【 底座面 (向内折) 】", centerX, bottomFoldY + baseFlapHeight / 2f + 4f, foldTextPaint)

            // 动态调节草书字号以适应 2 字、3 字或 4 字姓名（大字号更醒目大气）
            namePaint.textSize = when {
                student.name.length <= 2 -> 122f
                student.name.length == 3 -> 110f
                else -> 92f
            }

            // 仅显示 5 位数学号本身，不含“学号:”字样，也不含班级
            val studentIdText = student.studentId

            // 4. 绘制下半部分正面（正向立面：小学号在上方，大字姓名在下方）
            // 调整 Y 轴间距：学号上移至 centerY - 82f，姓名位于 centerY + 38f，彻底消除重叠
            drawVectorText(canvas, studentIdText, centerX, lowerCenterY - 82f, idPaint)
            drawVectorText(canvas, student.name, centerX, lowerCenterY + 38f, namePaint)

            // 5. 绘制上半部分反面（旋转 180 度，对折立起后从另一侧看同样正向：小学号在上，大字姓名在下）
            canvas.save()
            canvas.rotate(180f, centerX, upperCenterY)
            drawVectorText(canvas, studentIdText, centerX, upperCenterY - 82f, idPaint)
            drawVectorText(canvas, student.name, centerX, upperCenterY + 38f, namePaint)
            canvas.restore()

            document.finishPage(page)
        }

        return document
    }

    /**
     * 将文字转换为矢量路径 (Vector Path) 写入 PDF Canvas
     * 优势：
     * 1. 彻底解决 Android Skia 将自定义中文字体降级为 72 DPI 位图导致的打印发虚发糊问题。
     * 2. 在 PDF 内部生成原生贝塞尔矢量曲线，打印机以硬件最高物理精度 (600/1200 DPI) 锐利输出！
     */
    private fun drawVectorText(
        canvas: android.graphics.Canvas,
        text: String,
        x: Float,
        y: Float,
        paint: Paint
    ) {
        if (text.isEmpty()) return
        try {
            val path = Path()
            paint.getTextPath(text, 0, text.length, x, y, path)
            canvas.drawPath(path, paint)
        } catch (_: Exception) {
            canvas.drawText(text, x, y, paint)
        }
    }

    /**
     * 生成席卡 PDF 临时文件（用于分享或局域网直接打印）
     * @param onlyFirstStudent 为 true 时仅生成第 1 位学生单页（用于单张测试打印）
     * @param fontStyle 席卡姓名文字风格 (书法行楷 / 厚重粗体)
     */
    fun generatePdfFile(
        context: Context,
        classCode: String,
        students: List<Student>,
        onlyFirstStudent: Boolean = false,
        fontStyle: NameplateFontStyle = NameplateFontStyle.CURSIVE
    ): File {
        val document = buildPdfDocument(context, classCode, students, onlyFirstStudent, fontStyle)
        val exportDir = File(context.cacheDir, "attendance_exports").apply { mkdirs() }
        val prefix = if (onlyFirstStudent) "席卡测试单页" else "席卡"
        val filename = "${prefix}_${classCode}_${System.currentTimeMillis()}.pdf"
        val file = File(exportDir, filename)
        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
        return file
    }

    /**
     * 下载/保存席卡 PDF 文件到系统“下载 (Downloads)”目录
     */
    fun savePdfToDownloads(
        context: Context,
        classCode: String,
        students: List<Student>,
        onlyFirstStudent: Boolean = false,
        fontStyle: NameplateFontStyle = NameplateFontStyle.CURSIVE
    ): Boolean {
        val document = buildPdfDocument(context, classCode, students, onlyFirstStudent, fontStyle)
        val prefix = if (onlyFirstStudent) "席卡测试单页" else "席卡"
        val filename = "${prefix}_${classCode}_${System.currentTimeMillis()}.pdf"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/班级席卡")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val uri: Uri? = resolver.insert(collection, values)
        if (uri != null) {
            resolver.openOutputStream(uri)?.use { out ->
                document.writeTo(out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            document.close()
            return true
        }
        document.close()
        return false
    }
}
