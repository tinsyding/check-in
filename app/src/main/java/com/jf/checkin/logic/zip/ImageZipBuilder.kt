package com.jf.checkin.logic.zip

import android.content.ContentResolver
import android.net.Uri
import com.jf.checkin.util.displayNameOf
import com.jf.checkin.util.extensionFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min

/**
 * 把图片原样流式写入 ZIP，不压缩、不解码整张图。
 */
object ImageZipBuilder {

    /** 标准 32 位 ZIP 格式的最大安全字节数（约 4GB）。 */
    private const val MAX_STANDARD_ZIP_BYTES = 0xFFFF_FFFEL
    private const val MIN_EOCD_BYTES = 22
    private const val MAX_EOCD_SEARCH_BYTES = 65_557L

    suspend fun build(
        resolver: ContentResolver,
        uris: List<Uri>,
        prefix: String,
        output: File,
        sizeMultiplier: Int = 1,
        onProgress: (Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zip ->
            zip.setLevel(Deflater.NO_COMPRESSION)

            uris.forEachIndexed { index, uri ->
                coroutineContext.ensureActive()

                val extension = extensionFor(
                    mimeType = resolver.getType(uri),
                    displayName = resolver.displayNameOf(uri),
                )
                zip.putNextEntry(ZipEntry(imageEntryName(prefix, index + 1, extension)))

                resolver.openInputStream(uri)?.use { input ->
                    input.copyTo(zip, 64 * 1024)
                } ?: throw IOException("Cannot open $uri")

                zip.closeEntry()
                onProgress(index + 1)
            }
        }

        if (sizeMultiplier > 1) {
            coroutineContext.ensureActive()
            inflateWithUnreferencedGap(output, sizeMultiplier)
        }
    }

    /**
     * 在最后一个 ZipEntry 数据区之后、Central Directory（中央目录）之前插入未引用填充区，
     * 并同步修正 EOCD 中的中央目录起始偏移量。
     *
     * 效果：
     * - 压缩包总文件大小变为原来的 [sizeMultiplier] 倍；
     * - 压缩包内文件列表、每个图片的原始大小与哈希值（CRC32 / MD5 / SHA256）保持原样不变。
     */
    internal fun inflateWithUnreferencedGap(output: File, sizeMultiplier: Int) {
        val factor = sizeMultiplier.coerceAtLeast(1)
        if (factor <= 1) return

        val origLen = output.length()
        if (origLen < MIN_EOCD_BYTES || origLen >= MAX_STANDARD_ZIP_BYTES) return

        val targetLen = (origLen.toBigInteger() * factor.toBigInteger())
            .min(MAX_STANDARD_ZIP_BYTES.toBigInteger())
            .toLong()
        val paddingBytes = targetLen - origLen
        if (paddingBytes <= 0L) return

        RandomAccessFile(output, "rw").use { raf ->
            val searchLen = min(origLen, MAX_EOCD_SEARCH_BYTES).toInt()
            val searchBuf = ByteArray(searchLen)
            raf.seek(origLen - searchLen)
            raf.readFully(searchBuf)

            var eocdInSearch = -1
            for (i in searchLen - MIN_EOCD_BYTES downTo 0) {
                if (searchBuf[i] == 0x50.toByte() &&
                    searchBuf[i + 1] == 0x4b.toByte() &&
                    searchBuf[i + 2] == 0x05.toByte() &&
                    searchBuf[i + 3] == 0x06.toByte()
                ) {
                    eocdInSearch = i
                    break
                }
            }
            if (eocdInSearch < 0) return

            val eocdOffset = (origLen - searchLen) + eocdInSearch
            val eocdView = ByteBuffer.wrap(searchBuf).order(ByteOrder.LITTLE_ENDIAN)
            val cdSize = Integer.toUnsignedLong(eocdView.getInt(eocdInSearch + 12))
            val cdOffset = Integer.toUnsignedLong(eocdView.getInt(eocdInSearch + 16))
            if (cdOffset + cdSize > eocdOffset || origLen - cdOffset > Int.MAX_VALUE) return

            val tailLen = (origLen - cdOffset).toInt()
            val tail = ByteArray(tailLen)
            raf.seek(cdOffset)
            raf.readFully(tail)

            val newCdOffset = cdOffset + paddingBytes
            val eocdInTail = (eocdOffset - cdOffset).toInt()
            ByteBuffer.wrap(tail)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(eocdInTail + 16, newCdOffset.toInt())

            // 抹掉原位置的中央目录与 EOCD 签名，防止部分解压工具误扫到旧签名
            raf.seek(cdOffset)
            raf.write(ByteArray(tailLen))

            // 将中央目录与修正后的 EOCD 写入新偏移位置
            raf.seek(newCdOffset)
            raf.write(tail)
        }
    }
}
