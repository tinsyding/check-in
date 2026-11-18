package com.jf.checkin.logic.zip

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ImageZipBuilderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun inflateWithUnreferencedGap_multipliesFileSizeAndPreservesEntries() {
        val zipFile = tempFolder.newFile("test.zip")
        val payload1 = ByteArray(4096) { (it * 31 + 7).toByte() }
        val payload2 = ByteArray(8192) { (it * 17 + 3).toByte() }

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            zos.setLevel(Deflater.NO_COMPRESSION)
            zos.putNextEntry(ZipEntry("26.11.18-01.jpg"))
            zos.write(payload1)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("26.11.18-02.png"))
            zos.write(payload2)
            zos.closeEntry()
        }

        val originalLength = zipFile.length()
        ImageZipBuilder.inflateWithUnreferencedGap(zipFile, sizeMultiplier = 10)

        assertEquals(originalLength * 10L, zipFile.length())

        // Random-access ZipFile verification (reads via EOCD + Central Directory)
        ZipFile(zipFile).use { zf ->
            val entries = zf.entries().toList()
            assertEquals(2, entries.size)
            assertEquals("26.11.18-01.jpg", entries[0].name)
            assertEquals(payload1.size.toLong(), entries[0].size)
            assertArrayEquals(payload1, zf.getInputStream(entries[0]).readAllBytes())

            assertEquals("26.11.18-02.png", entries[1].name)
            assertEquals(payload2.size.toLong(), entries[1].size)
            assertArrayEquals(payload2, zf.getInputStream(entries[1]).readAllBytes())
        }

        // Streaming ZipInputStream verification (reads sequentially from start of file)
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            val first = zis.nextEntry!!
            assertEquals("26.11.18-01.jpg", first.name)
            assertArrayEquals(payload1, zis.readAllBytes())

            val second = zis.nextEntry!!
            assertEquals("26.11.18-02.png", second.name)
            assertArrayEquals(payload2, zis.readAllBytes())

            assertEquals(null, zis.nextEntry)
        }
    }

    @Test
    fun inflateWithUnreferencedGap_withMultiplierOne_isNoOp() {
        val zipFile = tempFolder.newFile("noop.zip")
        val payload = "hello".toByteArray()

        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            zos.setLevel(Deflater.NO_COMPRESSION)
            zos.putNextEntry(ZipEntry("26.11.18-01.jpg"))
            zos.write(payload)
            zos.closeEntry()
        }

        val originalBytes = zipFile.readBytes()
        ImageZipBuilder.inflateWithUnreferencedGap(zipFile, sizeMultiplier = 1)
        assertArrayEquals(originalBytes, zipFile.readBytes())
    }
}
