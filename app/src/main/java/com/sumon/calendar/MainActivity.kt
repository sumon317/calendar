package com.sumon.calendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sumon.calendar.data.LocalCalendarRepository
import com.sumon.calendar.ui.month.CalendarViewModel
import com.sumon.calendar.ui.month.MonthScreen
import com.sumon.calendar.ui.settings.SettingsScreen
import com.sumon.calendar.ui.theme.CalendarTheme

enum class AppScreen {
    Month,
    Settings,
}

class MainActivity : ComponentActivity() {

    private val appViewModelFactory = viewModelFactory {
        initializer {
            val app = requireNotNull(this[APPLICATION_KEY]) { "Application must be provided by CreationExtras" }
            CalendarViewModel(
                repository = LocalCalendarRepository(app.applicationContext),
            )
        }
    }

    private val calendarViewModel: CalendarViewModel by viewModels { appViewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalendarTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    var currentScreen by rememberSaveable { mutableStateOf(AppScreen.Month) }

                    when (currentScreen) {
                        AppScreen.Month -> {
                            MonthScreen(
                                viewModel = calendarViewModel,
                                onOpenSettings = { currentScreen = AppScreen.Settings },
                            )
                        }
                        AppScreen.Settings -> {
                            BackHandler {
                                currentScreen = AppScreen.Month
                            }
                            SettingsScreen(
                                viewModel = calendarViewModel,
                                onNavigateBack = { currentScreen = AppScreen.Month },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        calendarViewModel.checkPermissions()
    }
}
