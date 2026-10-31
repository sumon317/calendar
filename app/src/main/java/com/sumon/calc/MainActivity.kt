package com.sumon.calc

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sumon.calc.data.CalcDatabase
import com.sumon.calc.data.EventRepository
import com.sumon.calc.ui.month.CalendarViewModel
import com.sumon.calc.ui.month.MonthScreen
import com.sumon.calc.ui.theme.CalcTheme

class MainActivity : ComponentActivity() {

    private val appViewModelFactory = viewModelFactory {
        initializer {
            val app = requireNotNull(this[APPLICATION_KEY]) { "Application must be provided by CreationExtras" }
            CalendarViewModel(
                repository = EventRepository(dao = CalcDatabase.get(app).eventDao()),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalcTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MonthScreen(viewModel(factory = appViewModelFactory))
                }
            }
        }
    }
}
