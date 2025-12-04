package com.example.quizgpt

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestConfigScreen(
    setId: String,
    setTitle: String,
    starredOnly: Boolean,
    onNavigateBack: () -> Unit,
    onStartTest: () -> Unit,
    quizSetViewModel: QuizSetViewModel = viewModel()
) {
    LaunchedEffect(setId) {
        quizSetViewModel.getCardsForSet(setId)
        quizSetViewModel.resetTest()
    }

    val testState by quizSetViewModel.testState.collectAsState()

    // Config State
    var questionCount by remember { mutableFloatStateOf(5f) }
    var questionsPerPage by remember { mutableFloatStateOf(1f) } // NEW: Pagination control
    var includeMC by remember { mutableStateOf(true) }
    var includeTF by remember { mutableStateOf(true) }
    var includeSA by remember { mutableStateOf(true) }

    LaunchedEffect(testState.questions) {
        if (testState.questions.isNotEmpty() && !testState.isLoading && !testState.isComplete) {
            onStartTest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configure Test: $setTitle") },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            if (testState.isLoading) {
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("AI is creating your test...")
                }
            } else {
                Column {
                    if (starredOnly) {
                        Text("NOTE: Testing only STARRED cards.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(16.dp))
                    }

                    // Total Questions Slider
                    Text("Total Questions: ${questionCount.toInt()}", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = questionCount,
                        onValueChange = {
                            questionCount = it
                            // Ensure page size isn't larger than total questions
                            if (questionsPerPage > it) questionsPerPage = it
                        },
                        valueRange = 1f..20f,
                        steps = 19
                    )
                    Spacer(Modifier.height(16.dp))

                    // NEW: Questions Per Page Slider
                    Text("Questions Per Page: ${questionsPerPage.toInt()}", style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = questionsPerPage,
                        onValueChange = { questionsPerPage = it },
                        valueRange = 1f..questionCount, // Max is the total number of questions
                        steps = (questionCount.toInt() - 2).coerceAtLeast(0)
                    )
                    Text(
                        text = if (questionsPerPage.toInt() == 1) "Single Card Mode" else "Paper Mode (${questionsPerPage.toInt()} per page)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Spacer(Modifier.height(24.dp))
                    Text("Question Types", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = includeMC, onCheckedChange = { includeMC = it }); Text("Multiple Choice") }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = includeTF, onCheckedChange = { includeTF = it }); Text("True / False") }
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = includeSA, onCheckedChange = { includeSA = it }); Text("Short Answer") }

                    Spacer(Modifier.height(32.dp))

                    Button(
                        onClick = {
                            quizSetViewModel.generateTest(
                                setId = setId,
                                questionCount = questionCount.toInt(),
                                itemsPerPage = questionsPerPage.toInt(), // Pass new param
                                includeMC = includeMC,
                                includeTF = includeTF,
                                includeSA = includeSA,
                                starredOnly = starredOnly
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = includeMC || includeTF || includeSA
                    ) {
                        Text("Generate Test")
                    }

                    if (testState.error != null) {
                        Spacer(Modifier.height(16.dp))
                        Text("Error: ${testState.error}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}