package com.example.quizgpt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.quizgpt.ui.theme.QuizGPTTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Detect system default
            val systemDark = isSystemInDarkTheme()
            // App-wide state for theme
            var isDarkTheme by remember { mutableStateOf(systemDark) }

            QuizGPTTheme(darkTheme = isDarkTheme) {
                // Pass state and toggle function
                AppNavigation(
                    isDarkTheme = isDarkTheme,
                    onThemeChange = { isDarkTheme = it }
                )
            }
        }
    }
}