package com.jf.checkin.logic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.InetSocketAddress
import java.net.Socket

object LanPrinterHelper {

    /**
     * 发送 PDF 数据至局域网打印机 (RAW 9100 端口)
     * @param pdfFile 要打印的 PDF 文件
     * @param ip 打印机 IP 地址，例如 172.16.100.222
     * @param port 端口号，默认 9100 (HP JetDirect / RAW)
     * @param forceSimplex 是否强制单面打印 (使用 PJL 协议头指定 DUPLEX=OFF)
     * @param timeoutMs 连接与传输超时时间 (毫秒)
     */
    suspend fun printPdf(
        pdfFile: File,
        ip: String,
        port: Int = 9100,
        forceSimplex: Boolean = true,
        timeoutMs: Int = 15000
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            socket = Socket()
            socket.connect(InetSocketAddress(ip, port), timeoutMs)
            socket.soTimeout = timeoutMs

            socket.getOutputStream().use { out ->
                // PJL 命令：强制高清晰度输出，防发虚模糊；并按需设置单面打印
                val pjlHeader = buildString {
                    append("\u001B%-12345X@PJL\r\n")
                    if (forceSimplex) {
                        append("@PJL SET DUPLEX=OFF\r\n")
                    }
                    append("@PJL SET RESOLUTION=600\r\n")
                    append("@PJL SET PRINTQUALITY=HIGH\r\n")
                    append("@PJL SET ECONOMODE=OFF\r\n")
                    append("@PJL SET DENSITY=5\r\n")
                    append("@PJL SET RET=ON\r\n")
                    append("@PJL ENTER LANGUAGE=PDF\r\n")
                }
                out.write(pjlHeader.toByteArray(Charsets.US_ASCII))

                // 流式发送 PDF 文件二进制流
                FileInputStream(pdfFile).use { fileIn ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (fileIn.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                    }
                }

                // PJL 结束标志
                val pjlFooter = "\r\n\u001B%-12345X@PJL EOJ\r\n\u001B%-12345X"
                out.write(pjlFooter.toByteArray(Charsets.US_ASCII))

                out.flush()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    /**
     * 测试与局域网打印机的 TCP 连通性
     */
    suspend fun testConnection(ip: String, port: Int = 9100, timeoutMs: Int = 3000): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
