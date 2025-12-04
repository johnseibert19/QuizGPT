package com.example.quizgpt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetDetailsScreen(
    setId: String,
    onNavigateBack: () -> Unit,
    onStudy: (Boolean) -> Unit,
    onWrite: (Boolean) -> Unit,
    onManageCards: () -> Unit,
    onTest: (Boolean) -> Unit,
    onTutor: (Boolean) -> Unit,
    quizSetViewModel: QuizSetViewModel = viewModel()
) {
    LaunchedEffect(setId) { quizSetViewModel.getCardsForSet(setId) }
    val cards by quizSetViewModel.cards.collectAsState()
    val set = quizSetViewModel.getSetById(setId)

    val masteredCount = remember(cards) { cards.count { it.masteryLevel == MasteryLevel.MASTERED.name } }
    val learningCount = remember(cards) { cards.count { it.masteryLevel == MasteryLevel.NEEDS_IMPROVEMENT.name } }
    val newCount = remember(cards) { cards.count { it.masteryLevel == MasteryLevel.NOT_STUDIED.name } }
    val starredCount = remember(cards) { cards.count { it.isStarred } }

    // NEW: Starred Toggle State
    var useStarredOnly by remember { mutableStateOf(false) }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("All", "Learning", "Mastered")

    val filteredCards by remember {
        derivedStateOf {
            val baseList = cards
            val tabFiltered = when (selectedTabIndex) {
                1 -> baseList.filter { it.masteryLevel == MasteryLevel.NEEDS_IMPROVEMENT.name }
                2 -> baseList.filter { it.masteryLevel == MasteryLevel.MASTERED.name }
                else -> baseList
            }
            // Filter list view if toggled
            if (useStarredOnly) tabFiltered.filter { it.isStarred } else tabFiltered
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = { IconButton(onClick = onManageCards) { Icon(Icons.Default.Edit, "Edit Set") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        if (set == null) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {

                Column(modifier = Modifier.padding(16.dp)) {
                    Text(set.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    if (set.description.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(set.description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ProgressStat("Mastered", masteredCount, Color(0xFF4CAF50))
                        ProgressStat("Learning", learningCount, Color(0xFFFF9800))
                        ProgressStat("New", newCount, Color(0xFF9E9E9E))
                    }
                    Spacer(Modifier.height(24.dp))

                    // --- STARRED TOGGLE ---
                    if (starredCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { useStarredOnly = !useStarredOnly }
                                .padding(vertical = 8.dp)
                        ) {
                            Checkbox(checked = useStarredOnly, onCheckedChange = { useStarredOnly = it })
                            Spacer(Modifier.width(8.dp))
                            Text("Study Starred Only ($starredCount)", style = MaterialTheme.typography.bodyLarge)
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            StudyModeButton(Icons.Default.Style, "Flashcards", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, Modifier.weight(1f)) { onStudy(useStarredOnly) }
                            StudyModeButton(Icons.Default.Edit, "Write", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Modifier.weight(1f)) { onWrite(useStarredOnly) }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            StudyModeButton(Icons.Default.Assignment, "Test", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, Modifier.weight(1f)) { onTest(useStarredOnly) }
                            StudyModeButton(Icons.Default.School, "Smart Tutor", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, Modifier.weight(1f)) { onTutor(useStarredOnly) }
                        }
                    }
                }

                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(selected = selectedTabIndex == index, onClick = { selectedTabIndex = index }, text = { Text(title) })
                    }
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(16.dp), modifier = Modifier.weight(1f)) {
                    if (filteredCards.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No cards in this category.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    } else {
                        items(items = filteredCards, key = { it.id }) { card ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(card.question, style = MaterialTheme.typography.bodyLarge)
                                        Text(card.answer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (card.isStarred) Icon(Icons.Default.Star, null, tint = Color(0xFFFFC107), modifier = Modifier.padding(end = 8.dp))
                                    when (card.masteryLevel) {
                                        MasteryLevel.MASTERED.name -> Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50))
                                        MasteryLevel.NEEDS_IMPROVEMENT.name -> Icon(Icons.Default.Warning, null, tint = Color(0xFFFF9800))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressStat(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(40.dp).background(color.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
            Text("$count", style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun StudyModeButton(icon: ImageVector, title: String, color: Color, textColor: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.height(80.dp), colors = CardDefaults.cardColors(containerColor = color), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = null, tint = textColor)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = textColor)
        }
    }
}