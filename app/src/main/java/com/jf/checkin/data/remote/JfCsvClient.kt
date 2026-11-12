package com.jf.checkin.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class JfCsvException(message: String) : Exception(message)

/**
 * 直接走 HTTP 完成 JF 系统的三步：
 * 1. 打开登录页并取 CSRF
 * 2. 提交账号密码
 * 3. 下载 /students/all/export/ 的 CSV
 *
 * 不依赖 WebView，失败时调用方仍可回退到网页版手动操作。
 */
object JfCsvClient {

    private const val BASE = "https://jf-alpha.com"
    private const val LOGIN_URL = "$BASE/accounts/login/?next=/dashboard/"
    private const val EXPORT_URL = "$BASE/students/all/export/?"
    private const val STUDENTS_URL = "$BASE/students/all/"

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val csrfInputRegex = Regex(
        "<input[^>]*name=\"csrfmiddlewaretoken\"[^>]*>",
        RegexOption.IGNORE_CASE
    )
    private val valueRegex = Regex("value=\"([^\"]+)\"", RegexOption.IGNORE_CASE)

    suspend fun downloadCsv(username: String, password: String): String =
        withContext(Dispatchers.IO) {
            val user = username.trim()
            if (user.isBlank() || password.isBlank()) {
                throw JfCsvException("未配置网页账号或密码")
            }

            val jar = CookieJar()

            val loginPage = request(LOGIN_URL, "GET", null, jar)
            if (loginPage.code != 200) {
                throw JfCsvException("无法打开登录页（${loginPage.code}）")
            }
            val csrf = extractCsrf(loginPage.body)
                ?: throw JfCsvException("登录页缺少 CSRF 校验，网站可能已改版")

            val form = buildString {
                append("csrfmiddlewaretoken=").append(encode(csrf))
                append("&username=").append(encode(user))
                append("&password=").append(encode(password))
            }
            val loginResult = request(
                url = LOGIN_URL,
                method = "POST",
                body = form.toByteArray(Charsets.UTF_8),
                jar = jar,
                referer = LOGIN_URL,
            )

            if (loginResult.body.contains("name=\"password\"") &&
                loginResult.body.contains("csrfmiddlewaretoken")
            ) {
                throw JfCsvException("账号或密码错误")
            }
            if (loginResult.code !in 200..299) {
                throw JfCsvException("登录失败（${loginResult.code}）")
            }

            val export = request(
                url = EXPORT_URL,
                method = "GET",
                body = null,
                jar = jar,
                referer = STUDENTS_URL,
            )
            if (export.code != 200) {
                throw JfCsvException("下载名单失败（${export.code}）")
            }
            val contentType = export.contentType.orEmpty()
            if (!contentType.contains("text/csv", ignoreCase = true)) {
                if (export.body.contains("/accounts/login/")) {
                    throw JfCsvException("登录状态失效")
                }
                throw JfCsvException("网站返回的不是 CSV 文件")
            }

            val csv = export.body.removePrefix("\uFEFF").trim()
            if (csv.isBlank()) throw JfCsvException("CSV 内容为空")
            csv
        }

    private fun extractCsrf(html: String): String? {
        val tag = csrfInputRegex.find(html)?.value ?: return null
        return valueRegex.find(tag)?.groupValues?.get(1)
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun request(
        url: String,
        method: String,
        body: ByteArray?,
        jar: CookieJar,
        referer: String? = null,
    ): HttpResponse {
        var currentUrl = url
        var currentMethod = method
        var currentBody = body
        var redirects = 0

        while (true) {
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.requestMethod = currentMethod
                connection.connectTimeout = 15_000
                connection.readTimeout = 20_000
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,text/csv,*/*;q=0.8"
                )
                connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                if (referer != null) {
                    connection.setRequestProperty("Referer", referer)
                }
                jar.header().takeIf { it.isNotBlank() }?.let {
                    connection.setRequestProperty("Cookie", it)
                }

                if (currentBody != null) {
                    connection.doOutput = true
                    connection.setRequestProperty(
                        "Content-Type",
                        "application/x-www-form-urlencoded; charset=UTF-8"
                    )
                    connection.setFixedLengthStreamingMode(currentBody.size)
                    connection.outputStream.use { it.write(currentBody) }
                }

                val code = connection.responseCode
                jar.store(connection)

                val location = connection.getHeaderField("Location")
                if (code in 300..399 && !location.isNullOrBlank() && redirects < 10) {
                    currentUrl = URL(URL(currentUrl), location).toString()
                    if (code == 303 ||
                        ((code == 301 || code == 302) && currentMethod == "POST")
                    ) {
                        currentMethod = "GET"
                        currentBody = null
                    }
                    redirects++
                    continue
                }

                val stream = if (code >= 400) connection.errorStream else connection.inputStream
                val content = stream?.use { input ->
                    ByteArrayOutputStream().also { output -> input.copyTo(output) }.toByteArray()
                } ?: ByteArray(0)

                return HttpResponse(
                    code = code,
                    body = content.toString(Charsets.UTF_8),
                    contentType = connection.contentType,
                )
            } finally {
                connection.disconnect()
            }
        }
    }

    private data class HttpResponse(
        val code: Int,
        val body: String,
        val contentType: String?,
    )

    /** 只服务 jf-alpha.com，不处理 Domain/Path，够用且不会串站。 */
    private class CookieJar {
        private val cookies = linkedMapOf<String, String>()

        fun header(): String = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

        fun store(connection: HttpURLConnection) {
            val setCookies = connection.headerFields
                .filterKeys { it?.equals("Set-Cookie", ignoreCase = true) == true }
                .values
                .flatten()
            for (raw in setCookies) {
                val pair = raw.substringBefore(';')
                val name = pair.substringBefore('=', "").trim()
                val value = pair.substringAfter('=', "").trim()
                if (name.isBlank()) continue
                if (value.isBlank() || value == "deleted") {
                    cookies.remove(name)
                } else {
                    cookies[name] = value
                }
            }
        }
    }
}
