package com.jf.checkin.util

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/**
 * 解析其他 App 分享进来的图片，同时兼容 EXTRA_STREAM 与 ClipData。
 */
fun Intent?.imageUris(): List<Uri> {
    if (this == null) return emptyList()

    val uris = when (action) {
        Intent.ACTION_SEND -> buildList {
            IntentCompat.getParcelableExtra(this@imageUris, Intent.EXTRA_STREAM, Uri::class.java)
                ?.let(::add)
            clipData?.let { clip ->
                for (index in 0 until clip.itemCount) {
                    clip.getItemAt(index).uri?.let(::add)
                }
            }
        }

        Intent.ACTION_SEND_MULTIPLE -> buildList {
            IntentCompat.getParcelableArrayListExtra(
                this@imageUris,
                Intent.EXTRA_STREAM,
                Uri::class.java,
            )?.let(::addAll)
            clipData?.let { clip ->
                for (index in 0 until clip.itemCount) {
                    clip.getItemAt(index).uri?.let(::add)
                }
            }
        }

        else -> emptyList()
    }

    return uris.distinct()
}
