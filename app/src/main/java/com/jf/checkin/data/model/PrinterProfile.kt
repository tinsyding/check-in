package com.jf.checkin.data.model

import java.util.UUID

data class PrinterProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String, // 别名，例如 "办公室打印机", "教学楼HP"
    val ip: String,   // IP 地址，例如 "172.16.100.222"
    val port: Int = 9100 // 端口，默认 9100
)
