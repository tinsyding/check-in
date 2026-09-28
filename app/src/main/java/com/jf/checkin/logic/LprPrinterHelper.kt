package com.jf.checkin.logic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket

object LprPrinterHelper {

    /**
     * 通过 LPR/LPD (RFC 1179, 端口 515) 发送 PDF 打印作业
     */
    suspend fun printPdf(
        pdfFile: File,
        ip: String,
        port: Int = 515,
        queue: String = "lp",
        timeoutMs: Int = 15000
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(ip, port), timeoutMs)
            socket.soTimeout = timeoutMs

            val inStream = socket.getInputStream()
            val outStream = socket.getOutputStream()

            // 1. 请求打印队列
            outStream.write(("\u0002" + queue + "\n").toByteArray(Charsets.US_ASCII))
            outStream.flush()
            checkAck(inStream, "连接打印队列失败")

            val hostname = "localhost"
            val jobNum = "001"
            val cfName = "cfA" + jobNum + hostname
            val dfName = "dfA" + jobNum + hostname

            // 2. 发送控制文件
            val controlContent = ("H" + hostname + "\nPCheckInApp\nJ席卡打印\nldfA" + jobNum + hostname + "\nUdfA" + jobNum + hostname + "\nN" + pdfFile.name + "\n").toByteArray(Charsets.UTF_8)

            outStream.write(("\u0002" + controlContent.size + " " + cfName + "\n").toByteArray(Charsets.US_ASCII))
            outStream.flush()
            checkAck(inStream, "准备发送控制文件失败")

            outStream.write(controlContent)
            outStream.write(0)
            outStream.flush()
            checkAck(inStream, "确认控制文件失败")

            // 3. 发送数据文件 (PDF)
            val fileLen = pdfFile.length()
            outStream.write(("\u0003" + fileLen + " " + dfName + "\n").toByteArray(Charsets.US_ASCII))
            outStream.flush()
            checkAck(inStream, "准备发送数据文件失败")

            FileInputStream(pdfFile).use { fIn ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fIn.read(buffer).also { read = it } != -1) {
                    outStream.write(buffer, 0, read)
                }
            }
            outStream.write(0)
            outStream.flush()
            checkAck(inStream, "数据文件未被确认")

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    private fun checkAck(inStream: InputStream, errorMsg: String) {
        val b = inStream.read()
        if (b != 0) {
            throw RuntimeException(errorMsg + ", code: " + b)
        }
    }
}
