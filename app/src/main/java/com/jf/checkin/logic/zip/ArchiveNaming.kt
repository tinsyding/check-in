package com.jf.checkin.logic.zip

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 图片打包命名规则：日期前缀 + 姓名 + 序号。
 *
 * ZIP 内图片名：yy.MM.dd-01.jpg
 * 压缩包文件名：yy.MM.dd-姓名.zip
 */
private val dateFormat = DateTimeFormatter.ofPattern("yy.MM.dd")
private val illegalChars = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

fun datePrefix(date: LocalDate = LocalDate.now()): String = date.format(dateFormat)

fun sanitizeName(raw: String): String {
    val cleaned = raw.trim()
        .replace(illegalChars, "_")
        .trim(' ', '.')
        .take(24)
        .trim(' ', '.')
    return cleaned.ifBlank { "未命名" }
}

fun archiveFileName(prefix: String, rawName: String): String =
    "$prefix-${sanitizeName(rawName)}.zip"

fun imageEntryName(prefix: String, index: Int, extension: String): String =
    "$prefix-${index.toString().padStart(2, '0')}.$extension"
