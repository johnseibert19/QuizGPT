package com.example.quizgpt

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardManagerScreen(
    setId: String,
    setTitle: String,
    onNavigateBack: () -> Unit,
    quizSetViewModel: QuizSetViewModel = viewModel()
) {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }

    // Load cards for this set
    LaunchedEffect(setId) {
        quizSetViewModel.getCardsForSet(setId)
    }

    val cards by quizSetViewModel.cards.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage: $setTitle") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            // Add Card Input
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text("Term / Question") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = answer,
                onValueChange = { answer = it },
                label = { Text("Definition / Answer") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    quizSetViewModel.addCardToSet(setId, question, answer, null, null)
                    question = ""
                    answer = ""
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = question.isNotBlank() && answer.isNotBlank()
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add Card")
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()

            // List of Cards
            LazyColumn(
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cards) { card ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(card.question, style = MaterialTheme.typography.titleMedium)
                                Text(card.answer, style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = { quizSetViewModel.deleteCard(setId, card.id) }) {
                                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}