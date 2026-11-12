package com.jf.checkin.ui.zip

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.jf.checkin.ui.theme.JFCheckInTheme

/**
 * 独立的图片打包页：
 * - 作为系统分享目标接收图片
 * - 也可以从点名页右上角“更多”菜单进入
 */
class ZipActivity : ComponentActivity() {

    private val viewModel: ZipViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) {
            viewModel.handleShareIntent(intent)
        }

        setContent {
            JFCheckInTheme {
                val state by viewModel.state.collectAsState()

                ZipScreen(
                    state = state,
                    onAddUris = viewModel::addUris,
                    onRemoveUri = viewModel::removeUri,
                    onClearUris = viewModel::clearUris,
                    onNameChange = viewModel::setName,
                    onGenerate = viewModel::generate,
                    onBack = { finish() },
                    onMessageShown = viewModel::dismissMessage,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleShareIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshDate()
    }
}
