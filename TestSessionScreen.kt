package com.example.quizgpt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestSessionScreen(
    setId: String,
    onNavigateBack: () -> Unit,
    quizSetViewModel: QuizSetViewModel = viewModel()
) {
    val state by quizSetViewModel.testState.collectAsState()

    if (state.isComplete) {
        // --- Results View (End of Test) ---
        Scaffold(
            topBar = { TopAppBar(title = { Text("Test Results") }) }
        ) { p ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(p)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Test Complete!", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(75.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.score}", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("/ ${state.questions.size}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }

                Spacer(Modifier.height(32.dp))
                Text("Review & Update Mastery", style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.Start))
                Spacer(Modifier.height(8.dp))

                state.questions.forEachIndexed { index, q ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = if (q.isCorrect) Color(0xFFE8F5E9) else Color(0xFFFFEBEE))
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Q${index + 1}: ${q.questionText}", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("Your Answer: ${q.userAnswer}", style = MaterialTheme.typography.bodyMedium)
                            if (q.aiFeedback.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("Feedback: ${q.aiFeedback}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Spacer(Modifier.height(8.dp))
                            Divider()
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = {
                                    val card = quizSetViewModel.cards.value.find { it.question == q.questionText }
                                    if (card != null) {
                                        quizSetViewModel.updateCardMastery(setId, card.id, MasteryLevel.NEEDS_IMPROVEMENT)
                                    }
                                }) {
                                    Icon(Icons.Default.Warning, null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Study", color = Color(0xFFFF9800), style = MaterialTheme.typography.labelSmall)
                                }
                                TextButton(onClick = {
                                    val card = quizSetViewModel.cards.value.find { it.question == q.questionText }
                                    if (card != null) {
                                        quizSetViewModel.updateCardMastery(setId, card.id, MasteryLevel.MASTERED)
                                    }
                                }) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Mastered", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
                Button(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) { Text("Return to Set") }
            }
        }
    } else if (state.questions.isNotEmpty()) {
        // --- PAGINATION LOGIC ---
        val startIndex = state.currentPage * state.itemsPerPage
        val endIndex = min(startIndex + state.itemsPerPage, state.questions.size)
        // Get the slice of questions for this page
        val pageQuestions = state.questions.subList(startIndex, endIndex)
        val totalPages = (state.questions.size + state.itemsPerPage - 1) / state.itemsPerPage

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Page ${state.currentPage + 1} of $totalPages") },
                    // NEW: Exit Button added here for friendliness
                    actions = {
                        TextButton(onClick = onNavigateBack) {
                            Text("Exit", color = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            },
            bottomBar = {
                // Bottom bar for navigation/submission
                Surface(tonalElevation = 8.dp) {
                    Column(Modifier.padding(16.dp)) {
                        if (state.isGrading) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(16.dp))
                                Text("Grading page...")
                            }
                        } else {
                            Button(
                                onClick = { quizSetViewModel.submitPage() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (state.currentPage == totalPages - 1) "Finish Test" else "Next Page")
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            // Use LazyColumn to list questions on this page
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    LinearProgressIndicator(
                        progress = { (state.currentPage + 1).toFloat() / totalPages },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    )
                }

                // Render each question on the page
                itemsIndexed(pageQuestions) { index, question ->
                    val realIndex = startIndex + index
                    QuestionCard(
                        index = realIndex + 1,
                        question = question,
                        onAnswerChange = { newAnswer ->
                            quizSetViewModel.updateLocalAnswer(realIndex, newAnswer)
                        }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No questions loaded.") }
    }
}

// Helper Composable for rendering a single question in the list
@Composable
fun QuestionCard(
    index: Int,
    question: TestQuestion,
    onAnswerChange: (String) -> Unit
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text("Q$index.", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                Text(question.questionText, style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Text(
                    text = question.bloomLevel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(16.dp))

            when (question.type) {
                QuestionType.MULTIPLE_CHOICE -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        question.options.forEach { option ->
                            OutlinedButton(
                                onClick = { onAnswerChange(option) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = if (question.userAnswer == option)
                                    ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                else ButtonDefaults.outlinedButtonColors(),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(12.dp)
                            ) {
                                Text(text = option, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
                QuestionType.TRUE_FALSE -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        listOf("True", "False").forEach { option ->
                            OutlinedButton(
                                onClick = { onAnswerChange(option) },
                                modifier = Modifier.weight(1f),
                                colors = if (question.userAnswer == option)
                                    ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                else ButtonDefaults.outlinedButtonColors(),
                            ) {
                                Text(text = option)
                            }
                        }
                    }
                }
                QuestionType.SHORT_ANSWER -> {
                    OutlinedTextField(
                        value = question.userAnswer,
                        onValueChange = { onAnswerChange(it) },
                        label = { Text("Your Answer") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            }
        }
    }
}