package com.example.quizgpt

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WriteModeScreen(
    setId: String,
    setTitle: String,
    starredOnly: Boolean,
    onNavigateBack: () -> Unit,
    quizSetViewModel: QuizSetViewModel = viewModel()
) {
    LaunchedEffect(setId) { quizSetViewModel.getCardsForSet(setId) }
    val dbCards by quizSetViewModel.cards.collectAsState()

    var studyQueue by remember { mutableStateOf(listOf<QuizCard>()) }
    var isInitialized by rememberSaveable { mutableStateOf(false) }
    var correctCount by rememberSaveable { mutableStateOf(0) }
    var incorrectCount by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(dbCards) {
        if (!isInitialized && dbCards.isNotEmpty()) {
            val cardsToStudy = if (starredOnly) dbCards.filter { it.isStarred } else dbCards
            studyQueue = cardsToStudy.shuffled()
            isInitialized = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Write Mode", style = MaterialTheme.typography.titleMedium)
                        if (starredOnly) {
                            Text("Starred Only", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                !isInitialized && dbCards.isEmpty() -> CircularProgressIndicator()
                isInitialized && dbCards.isEmpty() -> Text("No cards found.")
                isInitialized && studyQueue.isEmpty() && starredOnly -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No starred cards found.", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onNavigateBack) { Text("Go Back") }
                    }
                }
                studyQueue.isEmpty() -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Session Complete!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$correctCount", style = MaterialTheme.typography.displaySmall, color = Color(0xFF4CAF50))
                                Text("Correct", style = MaterialTheme.typography.bodyMedium)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$incorrectCount", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.error)
                                Text("Incorrect", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Spacer(Modifier.height(48.dp))
                        Button(onClick = {
                            correctCount = 0; incorrectCount = 0
                            val cardsToStudy = if (starredOnly) dbCards.filter { it.isStarred } else dbCards
                            studyQueue = cardsToStudy.shuffled()
                        }) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Practice Again")
                        }
                    }
                }
                else -> {
                    val currentCard = studyQueue.first()
                    WriteCardView(
                        card = currentCard,
                        setId = setId,
                        quizSetViewModel = quizSetViewModel,
                        onResult = { isCorrect ->
                            if (isCorrect) correctCount++ else incorrectCount++
                            val nextQueue = studyQueue.toMutableList()
                            nextQueue.removeAt(0)
                            studyQueue = nextQueue
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun WriteCardView(
    card: QuizCard,
    setId: String,
    quizSetViewModel: QuizSetViewModel,
    onResult: (Boolean) -> Unit
) {
    var userAnswer by rememberSaveable { mutableStateOf("") }
    var showFeedback by rememberSaveable { mutableStateOf(false) }
    var isCorrect by rememberSaveable { mutableStateOf(false) }
    var aiFeedback by rememberSaveable { mutableStateOf("") }
    var isGrading by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(card) {
        userAnswer = ""
        showFeedback = false; isCorrect = false; aiFeedback = ""; isGrading = false
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = card.question,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (!showFeedback) {
            Column {
                OutlinedTextField(
                    value = userAnswer,
                    onValueChange = { userAnswer = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Your Answer") },
                    minLines = 3,
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isGrading
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        isGrading = true
                        quizSetViewModel.gradeWriteModeAnswer(card.question, card.answer, userAnswer) { correct, feedback ->
                            isCorrect = correct; aiFeedback = feedback; isGrading = false; showFeedback = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = userAnswer.isNotBlank() && !isGrading
                ) {
                    if (isGrading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Check Answer")
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (isCorrect) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (isCorrect) Color(0xFF2E7D32) else Color(0xFFC62828),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = if (isCorrect) "Correct!" else "Needs Work",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isCorrect) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(aiFeedback, style = MaterialTheme.typography.bodyLarge)

                    if (!isCorrect) {
                        Spacer(Modifier.height(16.dp))
                        Text("Correct Answer:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(card.answer, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    Text("Update Mastery:", style = MaterialTheme.typography.labelMedium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TextButton(onClick = {
                            quizSetViewModel.updateCardMastery(setId, card.id, MasteryLevel.NEEDS_IMPROVEMENT)
                        }) {
                            Icon(Icons.Default.Warning, null, tint = Color(0xFFFF9800))
                            Spacer(Modifier.width(4.dp))
                            Text("Still Learning", color = Color(0xFFFF9800))
                        }
                        TextButton(onClick = {
                            quizSetViewModel.updateCardMastery(setId, card.id, MasteryLevel.MASTERED)
                        }) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                            Spacer(Modifier.width(4.dp))
                            Text("Mastered", color = Color(0xFF4CAF50))
                        }
                    }
                }
            }

            Button(
                onClick = { onResult(isCorrect) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isCorrect) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary)
            ) {
                Text("Continue")
            }
        }
    }
}