package com.jf.checkin.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

fun ContentResolver.displayNameOf(uri: Uri): String? = runCatching {
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()

/**
 * 优先按 MIME 判断扩展名，拿不到时退回原文件名后缀。
 */
fun extensionFor(mimeType: String?, displayName: String?): String {
    val fromMime = when (mimeType?.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic" -> "heic"
        "image/heif" -> "heif"
        "image/gif" -> "gif"
        "image/bmp" -> "bmp"
        "image/avif" -> "avif"
        else -> null
    }
    if (fromMime != null) return fromMime

    return displayName
        ?.substringAfterLast('.', "")
        ?.lowercase()
        ?.takeIf { it.length in 2..5 && it.all { char -> char.isLetterOrDigit() } }
        ?: "jpg"
}
