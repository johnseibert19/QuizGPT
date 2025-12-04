package com.example.quizgpt

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun MainContainerScreen(
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    totalSetsCount: Int,
    onSignOut: () -> Unit,
    onNavigateToCreateSet: (Boolean) -> Unit,
    onManageCards: (String, String) -> Unit,
    onStudySet: (String, String, Boolean) -> Unit,
    onWriteSet: (String, String) -> Unit,
    onImportSet: () -> Unit,
    quizSetViewModel: QuizSetViewModel
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Library, 1 = Profile

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Library") },
                    label = { Text("Library") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        }
    ) { innerPadding ->
        if (selectedTab == 0) {
            MainScreenWrapper(
                modifier = Modifier.padding(innerPadding),
                onNavigateToCreateSet = onNavigateToCreateSet,
                onManageCards = onManageCards,
                onStudySet = onStudySet,
                onWriteSet = onWriteSet,
                onImportSet = onImportSet,
                viewModel = quizSetViewModel
            )
        } else {
            ProfileScreen(
                onSignOut = onSignOut,
                totalSets = totalSetsCount,
                isDarkTheme = isDarkTheme,     // Pass current state
                onThemeChange = onThemeChange  // Pass toggle function
            )
        }
    }
}

// Wrapper to match MainScreen signature with Modifier
@Composable
fun MainScreenWrapper(
    modifier: Modifier = Modifier,
    onNavigateToCreateSet: (Boolean) -> Unit,
    onManageCards: (String, String) -> Unit,
    onStudySet: (String, String, Boolean) -> Unit,
    onWriteSet: (String, String) -> Unit,
    onImportSet: () -> Unit,
    viewModel: QuizSetViewModel
) {
    Box(modifier = modifier) {
        MainScreen(
            onNavigateToCreateSet = onNavigateToCreateSet,
            onManageCards = onManageCards,
            onStudySet = onStudySet,
            onWriteSet = onWriteSet,
            onImportSet = onImportSet,
            quizSetViewModel = viewModel
        )
    }
}