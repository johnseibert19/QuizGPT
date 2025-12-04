package com.example.quizgpt

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder // Needed for unstarred state
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCardsScreen(
    setTitle: String,
    setId: String,
    onNavigateBack: () -> Unit,
    quizSetViewModel: QuizSetViewModel = viewModel()
) {
    LaunchedEffect(setId) { quizSetViewModel.getCardsForSet(setId) }
    val cards by quizSetViewModel.cards.collectAsState()
    val uiState by quizSetViewModel.uiState.collectAsState()

    var question by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf("") }
    var qUri by remember { mutableStateOf<Uri?>(null) }
    var aUri by remember { mutableStateOf<Uri?>(null) }
    var cardToDelete by remember { mutableStateOf<QuizCard?>(null) }
    var cardToEdit by remember { mutableStateOf<QuizCard?>(null) }

    DisposableEffect(Unit) { onDispose { quizSetViewModel.clearUiMessage() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(setTitle, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onNavigateBack) { Text("Done", modifier = Modifier.padding(start = 8.dp)) } }
            )
        },
        bottomBar = {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("Term / Question") }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        ImagePickerButton(selectedUri = qUri, onImageSelected = { qUri = it })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = answer, onValueChange = { answer = it }, label = { Text("Definition / Answer") }, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        ImagePickerButton(selectedUri = aUri, onImageSelected = { aUri = it })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { quizSetViewModel.addCardToSet(setId, question, answer, qUri, aUri); question = ""; answer = ""; qUri = null; aUri = null }, enabled = question.isNotBlank() && answer.isNotBlank(), modifier = Modifier.align(Alignment.End)) {
                        Icon(Icons.Default.Add, contentDescription = "Add Card")
                        Text("Add Card")
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Cards added: ${cards.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp)) }
            items(items = cards, key = { it.id }) { card ->
                CardItem(
                    card = card,
                    onEdit = { cardToEdit = card },
                    onDelete = { cardToDelete = card },
                    // --- RE-ADDED: Toggle Star Logic ---
                    onToggleStar = { quizSetViewModel.toggleCardStarred(setId, card.id, card.isStarred) }
                )
            }
        }
        cardToDelete?.let { DeleteCardDialog(card = it, onConfirm = { quizSetViewModel.deleteCard(setId, it.id); cardToDelete = null }, onDismiss = { cardToDelete = null }) }
        cardToEdit?.let { EditCardDialog(card = it, uiState = uiState, onConfirm = { q, a, qu, au -> quizSetViewModel.updateCard(setId, it.id, q, a, qu, au); cardToEdit = null }, onDismiss = { cardToEdit = null }, onClearError = { quizSetViewModel.clearUiMessage() }) }
    }
}

// ... ImagePickerButton (Unchanged) ...
@Composable
fun ImagePickerButton(selectedUri: Uri?, onImageSelected: (Uri?) -> Unit) {
    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.PickVisualMedia(), onResult = { uri -> onImageSelected(uri) })
    Box(contentAlignment = Alignment.TopEnd) {
        FilledTonalIconButton(onClick = { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.size(56.dp)) {
            if (selectedUri != null) Image(painter = rememberAsyncImagePainter(selectedUri), contentDescription = null, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop) else Icon(Icons.Default.Image, "Add Image")
        }
        if (selectedUri != null) IconButton(onClick = { onImageSelected(null) }, modifier = Modifier.size(24.dp).offset(x = 4.dp, y = (-4).dp).align(Alignment.TopEnd)) { Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun CardItem(
    card: QuizCard,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleStar: () -> Unit // Param present
) {
    var showMenu by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (card.questionImageUri != null) {
                Image(painter = rememberAsyncImagePainter(card.questionImageUri), contentDescription = null, modifier = Modifier.size(60.dp).padding(4.dp).clip(RoundedCornerShape(4.dp)), contentScale = ContentScale.Crop)
            }
            Column(Modifier.weight(1f).padding(12.dp)) {
                Text(card.question, style = MaterialTheme.typography.bodyLarge)
                Text(card.answer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // --- RE-ADDED: Star Icon Button ---
            IconButton(onClick = onToggleStar) {
                Icon(
                    imageVector = if (card.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Star",
                    tint = if (card.isStarred) Color(0xFFFFC107) else MaterialTheme.colorScheme.outline
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Options") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { showMenu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDelete() })
                }
            }
        }
    }
}

// ... DeleteCardDialog and EditCardDialog (Unchanged) ...
@Composable
fun DeleteCardDialog(card: QuizCard, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Delete Card?") }, text = { Text("Are you sure you want to delete '${card.question}'?") }, confirmButton = { Button(onClick = onConfirm) { Text("Delete") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun EditCardDialog(card: QuizCard, uiState: QuizSetUiState, onConfirm: (String, String, Uri?, Uri?) -> Unit, onDismiss: () -> Unit, onClearError: () -> Unit) {
    var question by rememberSaveable { mutableStateOf(card.question) }
    var answer by rememberSaveable { mutableStateOf(card.answer) }
    var qUri by remember { mutableStateOf(card.questionImageUri?.let { Uri.parse(it) }) }
    var aUri by remember { mutableStateOf(card.answerImageUri?.let { Uri.parse(it) }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Card") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = question, onValueChange = { question = it; onClearError() }, label = { Text("Term / Question") }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    ImagePickerButton(selectedUri = qUri, onImageSelected = { qUri = it })
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = answer, onValueChange = { answer = it; onClearError() }, label = { Text("Definition / Answer") }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    ImagePickerButton(selectedUri = aUri, onImageSelected = { aUri = it })
                }
                if (uiState.message.isNotEmpty()) Text(text = uiState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { Button(onClick = { onConfirm(question, answer, qUri, aUri) }, enabled = question.isNotBlank() && answer.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}