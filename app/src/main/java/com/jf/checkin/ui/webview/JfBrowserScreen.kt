package com.jf.checkin.ui.webview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jf.checkin.data.repository.StudentRepository
import com.jf.checkin.data.repository.UserPrefRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun JfBrowserScreen(
    prefRepository: UserPrefRepository,
    studentRepository: StudentRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isDownloading by remember { mutableStateOf(false) }

    val targetUrl = "https://jf-alpha.com/dashboard/"
    val targetHost = "jf-alpha.com"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("JF 系统管理台", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(targetUrl, fontSize = 11.sp, color = androidx.compose.ui.graphics.Color(0xFF64748B))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { webViewInstance?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url ?: return false
                                // 限制只允许在 jf-alpha.com 域名内浏览
                                return if (url.host?.contains(targetHost) == true) {
                                    false
                                } else {
                                    Toast.makeText(ctx, "受限浏览器：仅允许访问 $targetHost", Toast.LENGTH_SHORT).show()
                                    true
                                }
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false

                                // 自动注入保存的账号和密码
                                val user = prefRepository.username
                                val pass = prefRepository.password
                                if (user.isNotBlank() || pass.isNotBlank()) {
                                    val jsInjection = """
                                        (function() {
                                            const u = '$user';
                                            const p = '$pass';
                                            const userInputs = document.querySelectorAll('input[type="text"], input[type="email"], input[name*="user"], input[id*="user"]');
                                            const passInputs = document.querySelectorAll('input[type="password"]');
                                            if (userInputs.length > 0 && u) {
                                                userInputs[0].value = u;
                                                userInputs[0].dispatchEvent(new Event('input', { bubbles: true }));
                                                userInputs[0].dispatchEvent(new Event('change', { bubbles: true }));
                                            }
                                            if (passInputs.length > 0 && p) {
                                                passInputs[0].value = p;
                                                passInputs[0].dispatchEvent(new Event('input', { bubbles: true }));
                                                passInputs[0].dispatchEvent(new Event('change', { bubbles: true }));
                                            }
                                        })();
                                    """.trimIndent()
                                    view?.evaluateJavascript(jsInjection, null)
                                }
                            }
                        }

                        // 核心：监听下载流，检测到 all_students*.csv 自动抓取并导入
                        setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->
                            val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)
                            val isStudentCsv = Pattern.compile("all_students.*\\.csv", Pattern.CASE_INSENSITIVE)
                                .matcher(filename).find() || url.contains("all_students", ignoreCase = true)

                            if (isStudentCsv) {
                                isDownloading = true
                                Toast.makeText(ctx, "检测到最新名单，正在自动下载与导入...", Toast.LENGTH_SHORT).show()

                                coroutineScope.launch {
                                    try {
                                        val cookie = CookieManager.getInstance().getCookie(url)
                                        val csvText = withContext(Dispatchers.IO) {
                                            downloadCsvContent(url, userAgent, cookie)
                                        }

                                        if (csvText.isNotBlank()) {
                                            studentRepository.loadFromCsv(csvText)
                                            val offlineCount = studentRepository.students.value.size
                                            Toast.makeText(ctx, "导入成功！已自动录入线下签到学生 $offlineCount 人", Toast.LENGTH_LONG).show()
                                            // 导入后自动切回点名主界面
                                            onNavigateBack()
                                        } else {
                                            Toast.makeText(ctx, "文件内容为空", Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(ctx, "自动导入名单失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        isDownloading = false
                                    }
                                }
                            } else {
                                Toast.makeText(ctx, "跳过非学生名单文件: $filename", Toast.LENGTH_SHORT).show()
                            }
                        }

                        loadUrl(targetUrl)
                        webViewInstance = this
                    }
                }
            )

            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                )
            }

            if (isDownloading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                }
            }
        }
    }
}

/**
 * 携带当前 WebView 的 Session/Cookie 异步下载 CSV 纯文本
 */
private fun downloadCsvContent(urlStr: String, userAgent: String?, cookie: String?): String {
    val connection = URL(urlStr).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    if (!userAgent.isNullOrBlank()) {
        connection.setRequestProperty("User-Agent", userAgent)
    }
    if (!cookie.isNullOrBlank()) {
        connection.setRequestProperty("Cookie", cookie)
    }
    connection.connectTimeout = 15000
    connection.readTimeout = 15000

    return connection.inputStream.use { input ->
        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
    }
}
