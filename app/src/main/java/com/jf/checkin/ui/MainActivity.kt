package com.jf.checkin.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.jf.checkin.CheckInApplication
import com.jf.checkin.ui.history.HistoryScreen
import com.jf.checkin.ui.rollcall.RollCallScreen
import com.jf.checkin.ui.rollcall.RollCallViewModel
import com.jf.checkin.ui.settings.SettingsScreen
import com.jf.checkin.ui.theme.JFCheckInTheme
import com.jf.checkin.ui.webview.JfBrowserScreen

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                "未开启通知权限，每天 18:00 的未同步校管家提醒将无法显示",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as CheckInApplication
        val studentRepo = app.studentRepository
        val prefRepo = app.prefRepository
        val historyRepo = app.historyRepository

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            prefRepo.tmsDailyReminderEnabled &&
            prefRepo.hasTmsCredentials() &&
            !prefRepo.tmsNotificationPermissionAsked &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            prefRepo.tmsNotificationPermissionAsked = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // 交给 ViewModelStore 管理，Activity 重建时复用实例并在销毁时取消 viewModelScope
        val viewModelFactory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return RollCallViewModel(studentRepo, prefRepo, historyRepo) as T
            }
        }
        val rollCallViewModel = ViewModelProvider(this, viewModelFactory)[RollCallViewModel::class.java]

        setContent {
            JFCheckInTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "rollcall") {
                        composable("rollcall") {
                            RollCallScreen(
                                viewModel = rollCallViewModel,
                                onNavigateToBrowser = { navController.navigate("browser") },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToHistory = { navController.navigate("history") }
                            )
                        }
                        composable("history") {
                            HistoryScreen(
                                historyRepository = historyRepo,
                                onNavigateBack = { navController.popBackStack() },
                                onRestoreRecord = { record ->
                                    val error = rollCallViewModel.restoreRecord(record)
                                    Toast.makeText(
                                        this@MainActivity,
                                        error ?: "已将 ${record.classCode} 的签到状态恢复到主界面",
                                        if (error == null) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                                    ).show()
                                    if (error == null) navController.popBackStack()
                                }
                            )
                        }
                        composable("browser") {
                            JfBrowserScreen(
                                prefRepository = prefRepo,
                                studentRepository = studentRepo,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                prefRepository = prefRepo,
                                studentRepository = studentRepo,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
