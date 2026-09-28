package com.jf.checkin.logic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

object FtpPrinterHelper {

    /**
     * 通过 FTP (端口 21) 上传 PDF 文件至复合机自动打印
     */
    suspend fun printPdf(
        pdfFile: File,
        ip: String,
        port: Int = 21,
        timeoutMs: Int = 15000
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var ctrlSocket: Socket? = null
        var dataSocket: Socket? = null
        try {
            ctrlSocket = Socket()
            ctrlSocket.connect(InetSocketAddress(ip, port), timeoutMs)
            ctrlSocket.soTimeout = timeoutMs

            val reader = BufferedReader(InputStreamReader(ctrlSocket.getInputStream(), Charsets.US_ASCII))
            val writer = OutputStreamWriter(ctrlSocket.getOutputStream(), Charsets.US_ASCII)

            fun readReply(): String {
                var line = reader.readLine() ?: throw RuntimeException("FTP连接中断")
                while (line.length >= 4 && line[3] == '-') {
                    line = reader.readLine() ?: break
                }
                return line
            }

            fun sendCmd(cmd: String): String {
                writer.write(cmd + "\r\n")
                writer.flush()
                return readReply()
            }

            // 1. 读取 Banner (220)
            readReply()

            // 2. 登录 anonymous
            val userResp = sendCmd("USER anonymous")
            if (userResp.startsWith("331")) {
                val passResp = sendCmd("PASS anonymous@")
                if (!passResp.startsWith("230")) {
                    throw RuntimeException("FTP登录失败: $passResp")
                }
            } else if (!userResp.startsWith("230")) {
                throw RuntimeException("FTP用户错误: $userResp")
            }

            // 3. 设置二进制模式 (TYPE I)
            val typeResp = sendCmd("TYPE I")
            if (!typeResp.startsWith("200")) {
                throw RuntimeException("设置二进制传输失败: $typeResp")
            }

            // 4. 进入被动模式 (PASV)
            val pasvResp = sendCmd("PASV")
            val pStart = pasvResp.indexOf('(')
            val pEnd = pasvResp.indexOf(')')
            if (pStart == -1 || pEnd == -1) {
                throw RuntimeException("解析PASV失败: $pasvResp")
            }
            val parts = pasvResp.substring(pStart + 1, pEnd).split(',')
            val dataPort = parts[4].trim().toInt() * 256 + parts[5].trim().toInt()

            // 5. 连接数据端口
            dataSocket = Socket()
            dataSocket.connect(InetSocketAddress(ip, dataPort), timeoutMs)
            dataSocket.soTimeout = timeoutMs

            // 6. 发送 STOR 命令
            val storName = "print_" + System.currentTimeMillis() + ".pdf"
            val storResp = sendCmd("STOR $storName")
            if (!storResp.startsWith("150") && !storResp.startsWith("125")) {
                throw RuntimeException("STOR被拒绝: $storResp")
            }

            // 7. 发送文件流
            dataSocket.getOutputStream().use { dataOut ->
                FileInputStream(pdfFile).use { fIn ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (fIn.read(buffer).also { read = it } != -1) {
                        dataOut.write(buffer, 0, read)
                    }
                }
                dataOut.flush()
            }
            dataSocket.close()
            dataSocket = null

            // 8. 等待传输完成响应 (226)
            val doneResp = readReply()
            if (!doneResp.startsWith("226")) {
                throw RuntimeException("文件传输未完成: $doneResp")
            }

            try { sendCmd("QUIT") } catch (_: Exception) {}

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { dataSocket?.close() } catch (_: Exception) {}
            try { ctrlSocket?.close() } catch (_: Exception) {}
        }
    }
}
