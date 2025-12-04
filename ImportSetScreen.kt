package com.example.quizgpt

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSetScreen(
    onNavigateBack: () -> Unit,
    onImportSuccess: () -> Unit,
    quizSetViewModel: QuizSetViewModel = viewModel()
) {
    var title by rememberSaveable { mutableStateOf("") }
    var rawText by rememberSaveable { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    val uiState by quizSetViewModel.uiState.collectAsState()

    // Handle Import Logic
    if (isImporting) {
        LaunchedEffect(Unit) {
            quizSetViewModel.importSetFromText(title, rawText)
            isImporting = false
            // Note: Ideally we check if import was successful via UI state before navigating
            // For simplicity, we assume success if no error state
            if (uiState.message.startsWith("Successfully")) {
                onImportSuccess()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Set") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Paste your list below (Term, Definition format or copy from Quizlet export)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Set Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = rawText,
                onValueChange = { rawText = it },
                label = { Text("Paste Content Here") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp), // Tall box
                placeholder = { Text("e.g.\nApple, Red fruit\nBanana, Yellow fruit") }
            )

            if (uiState.message.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = uiState.message,
                    color = if (uiState.message.startsWith("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { isImporting = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isImporting && title.isNotBlank() && rawText.isNotBlank()
            ) {
                if (isImporting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Importing...")
                } else {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Import Set")
                }
            }
        }
    }
}