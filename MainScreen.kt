package com.example.quizgpt

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToCreateSet: (Boolean) -> Unit,
    onManageCards: (setId: String, setTitle: String) -> Unit,
    onStudySet: (setId: String, setTitle: String, isAiGraded: Boolean) -> Unit,
    onWriteSet: (setId: String, setTitle: String) -> Unit,
    onImportSet: () -> Unit,
    quizSetViewModel: QuizSetViewModel = viewModel()
) {
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val quizSets by quizSetViewModel.sets.collectAsState(initial = emptyList())

    // NEW: Observe current sort option
    val currentSort by quizSetViewModel.sortOption.collectAsState()

    var setToDelete by remember { mutableStateOf<QuizSet?>(null) }
    var setToEdit by remember { mutableStateOf<QuizSet?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }

    // NEW: Sort Menu State
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it; quizSetViewModel.onSearchQueryChanged(it) },
                    onSearch = { isSearchActive = false },
                    active = false,
                    onActiveChange = { isSearchActive = it },
                    placeholder = { Text("Search sets...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = { IconButton(onClick = { isSearchActive = false; searchQuery = ""; quizSetViewModel.onSearchQueryChanged("") }) { Icon(Icons.Default.Close, "Close") } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {}
            } else {
                TopAppBar(
                    title = { Text("My Quiz Sets", style = MaterialTheme.typography.headlineMedium) },
                    actions = {
                        // NEW: Sort Action
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Date (Newest)") },
                                    onClick = {
                                        quizSetViewModel.updateSortOrder(SortOption.CREATION_DATE_DESC)
                                        showSortMenu = false
                                    },
                                    leadingIcon = { if (currentSort == SortOption.CREATION_DATE_DESC) Icon(Icons.Default.Check, null) else null }
                                )
                                DropdownMenuItem(
                                    text = { Text("Date (Oldest)") },
                                    onClick = {
                                        quizSetViewModel.updateSortOrder(SortOption.CREATION_DATE_ASC)
                                        showSortMenu = false
                                    },
                                    leadingIcon = { if (currentSort == SortOption.CREATION_DATE_ASC) Icon(Icons.Default.Check, null) else null }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Name (A-Z)") },
                                    onClick = {
                                        quizSetViewModel.updateSortOrder(SortOption.ALPHABETICAL_AZ)
                                        showSortMenu = false
                                    },
                                    leadingIcon = { if (currentSort == SortOption.ALPHABETICAL_AZ) Icon(Icons.Default.Check, null) else null }
                                )
                                DropdownMenuItem(
                                    text = { Text("Name (Z-A)") },
                                    onClick = {
                                        quizSetViewModel.updateSortOrder(SortOption.ALPHABETICAL_ZA)
                                        showSortMenu = false
                                    },
                                    leadingIcon = { if (currentSort == SortOption.ALPHABETICAL_ZA) Icon(Icons.Default.Check, null) else null }
                                )
                            }
                        }

                        IconButton(onClick = { isSearchActive = true }) { Icon(Icons.Default.Search, "Search") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showBottomSheet = true },
                icon = { Icon(Icons.Default.Add, "Create") },
                text = { Text("New Set") }
            )
        }
    ) { paddingValues ->

        setToDelete?.let { set ->
            AlertDialog(
                onDismissRequest = { setToDelete = null },
                title = { Text("Delete Set?") },
                text = { Text("Are you sure you want to delete '${set.title}'?") },
                confirmButton = { Button(onClick = { quizSetViewModel.deleteSet(set.id); setToDelete = null }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Delete") } },
                dismissButton = { TextButton(onClick = { setToDelete = null }) { Text("Cancel") } }
            )
        }

        setToEdit?.let { set ->
            EditSetDialog(
                set = set,
                onConfirm = { newTitle, newDescription -> quizSetViewModel.updateSet(set.id, newTitle, newDescription); setToEdit = null },
                onDismiss = { setToEdit = null }
            )
        }

        if (showBottomSheet) {
            ModalBottomSheet(onDismissRequest = { showBottomSheet = false }, sheetState = sheetState) {
                Column(Modifier.padding(bottom = 32.dp)) {
                    Text("Create a new quiz set", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Flashcard Set", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text("Manually create a set of flashcards to study.") },
                        leadingContent = { Icon(Icons.Default.Style, "Flashcard", tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) { showBottomSheet = false; onNavigateToCreateSet(false) } } }
                    )
                }
            }
        }

        if (quizSets.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (searchQuery.isNotEmpty()) Icons.Default.SearchOff else Icons.Default.LibraryBooks,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.outlineVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = if (searchQuery.isNotEmpty()) "No matching sets found" else "Your Library is Empty",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (searchQuery.isNotEmpty()) "Try a different search term." else "Create your first quiz set to get started!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(items = quizSets, key = { it.id }) { set ->
                    QuizSetCard(
                        quizSet = set,
                        onClick = { onStudySet(set.id, set.title, set.isAiGraded) },
                        onManageCards = { onManageCards(set.id, set.title) },
                        onWriteMode = { onWriteSet(set.id, set.title) },
                        onDeleteSet = { setToDelete = set },
                        onEditSet = { setToEdit = set },
                        onToggleStar = { quizSetViewModel.toggleSetStarred(set.id, set.isStarred) }
                    )
                }
            }
        }
    }
}

@Composable
fun QuizSetCard(
    quizSet: QuizSet,
    onClick: () -> Unit,
    onManageCards: () -> Unit,
    onWriteMode: () -> Unit,
    onDeleteSet: () -> Unit,
    onEditSet: () -> Unit,
    onToggleStar: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (quizSet.isAiGraded) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (quizSet.isAiGraded) {
                        Icon(Icons.Default.Psychology, "AI", tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    } else {
                        Icon(Icons.Default.Style, "Standard", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = quizSet.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (quizSet.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = quizSet.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }

            IconButton(onClick = onToggleStar) {
                Icon(
                    imageVector = if (quizSet.isStarred) Icons.Default.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Star",
                    tint = if (quizSet.isStarred) Color(0xFFFFC107) else MaterialTheme.colorScheme.outline
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "Options") }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Manage Cards") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { showMenu = false; onManageCards() }
                    )
                    DropdownMenuItem(
                        text = { Text("Write Mode") },
                        leadingIcon = { Icon(Icons.Default.Create, null) },
                        onClick = { showMenu = false; onWriteMode() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDeleteSet() }
                    )
                }
            }
        }
    }
}

@Composable
fun EditSetDialog(set: QuizSet, onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(set.title) }
    var description by rememberSaveable { mutableStateOf(set.description) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Edit Set") },
        text = { Column { OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth()); Spacer(modifier = Modifier.height(8.dp)); OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth()) } },
        confirmButton = { Button(onClick = { onConfirm(title, description) }, enabled = title.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}