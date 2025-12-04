package com.example.quizgpt

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
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

    LaunchedEffect(dbCards) {
        if (!isInitialized && dbCards.isNotEmpty()) {
            studyQueue = dbCards.shuffled()
            isInitialized = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Studying: $setTitle", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Default.Close, contentDescription = "Close") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
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

                isInitialized && dbCards.isEmpty() -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Style, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(16.dp))
                        Text("Set is empty", style = MaterialTheme.typography.headlineSmall)
                    }
                }

                studyQueue.isEmpty() -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(24.dp))
                        Text("All Done!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(onClick = { studyQueue = dbCards.shuffled() }, modifier = Modifier.height(50.dp)) {
                            Icon(Icons.Default.Refresh, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restart Session")
                        }
                    }
                }

                else -> {
                    val currentCard = studyQueue.first()
                    val progress = 1f - (studyQueue.size.toFloat() / dbCards.size.toFloat())

                    Column(Modifier.fillMaxSize()) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        )
                        Spacer(Modifier.height(24.dp))

                        StudyCardView(
                            card = currentCard,
                            quizSetViewModel = quizSetViewModel,
                            onResult = { knewIt ->
                                val nextQueue = studyQueue.toMutableList()
                                nextQueue.removeAt(0)
                                if (!knewIt) nextQueue.add(currentCard)
                                studyQueue = nextQueue
                                val level = if (knewIt) MasteryLevel.MASTERED else MasteryLevel.NEEDS_IMPROVEMENT
                                quizSetViewModel.updateCardMastery(setId, currentCard.id, level)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StudyCardView(
    card: QuizCard,
    quizSetViewModel: QuizSetViewModel,
    onResult: (Boolean) -> Unit
) {
    var isFlipped by remember { mutableStateOf(false) }
    var showAiDialog by remember { mutableStateOf(false) }
    var aiDialogTitle by remember { mutableStateOf("") }
    var aiDialogContent by remember { mutableStateOf("") }
    var isAiLoading by remember { mutableStateOf(false) }

    LaunchedEffect(card) {
        isFlipped = false
        showAiDialog = false
    }

    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "cardFlip"
    )

    if (showAiDialog) {
        AlertDialog(
            onDismissRequest = { showAiDialog = false },
            title = { Text(aiDialogTitle) },
            text = { if (isAiLoading) CircularProgressIndicator() else Text(aiDialogContent) },
            confirmButton = { TextButton(onClick = { showAiDialog = false }) { Text("Close") } }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // --- Flashcard ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                }
                .clickable { isFlipped = !isFlipped },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxSize().padding(bottom = 32.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    if (rotation <= 90f) {
                        // FRONT
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (card.questionImageUri != null) {
                                Image(
                                    painter = rememberAsyncImagePainter(card.questionImageUri),
                                    contentDescription = null,
                                    modifier = Modifier.height(180.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(Modifier.height(24.dp))
                            }
                            Text(
                                text = card.question,
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text("Tap to flip", style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.BottomCenter), color = MaterialTheme.colorScheme.outline)
                    } else {
                        // BACK
                        Box(modifier = Modifier.graphicsLayer { rotationY = 180f }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (card.answerImageUri != null) {
                                    Image(
                                        painter = rememberAsyncImagePainter(card.answerImageUri),
                                        contentDescription = null,
                                        modifier = Modifier.height(180.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                    Spacer(Modifier.height(24.dp))
                                }
                                Text(
                                    text = card.answer,
                                    style = MaterialTheme.typography.headlineMedium,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(Modifier.height(32.dp))

                                // AI Helpers
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(onClick = {
                                        aiDialogTitle = "Mnemonic"; isAiLoading = true; showAiDialog = true
                                        quizSetViewModel.generateMnemonic(card.question, card.answer) { aiDialogContent = it; isAiLoading = false }
                                    }) {
                                        Icon(Icons.Default.Lightbulb, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Mnemonic")
                                    }
                                    OutlinedButton(onClick = {
                                        aiDialogTitle = "Explain Like I'm 5"; isAiLoading = true; showAiDialog = true
                                        quizSetViewModel.generateELI5(card.question, card.answer) { aiDialogContent = it; isAiLoading = false }
                                    }) {
                                        Icon(Icons.Default.Face, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("ELI5")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Controls ---
        if (isFlipped) {
            Row(modifier = Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = { onResult(false) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Study Again")
                }
                Button(
                    onClick = { onResult(true) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Check, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Got It")
                }
            }
        } else {
            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}