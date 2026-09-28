package com.jf.checkin.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
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
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as CheckInApplication
        val studentRepo = app.studentRepository
        val prefRepo = app.prefRepository
        val historyRepo = app.historyRepository

        val rollCallViewModel = RollCallViewModel(studentRepo, prefRepo, historyRepo)

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
                                onNavigateBack = { navController.popBackStack() }
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
