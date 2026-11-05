package ru.diary.app.ui.notes

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import ru.diary.app.R
import ru.diary.app.data.db.DateUtils
import ru.diary.app.data.model.Note
import ru.diary.app.data.repository.NoteRepository
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    repository: NoteRepository,
    onNoteClick: (Long) -> Unit,
    onAddNote: () -> Unit,
    onOpenTrash: () -> Unit = {}
) {
    val vm: NotesViewModel = viewModel(factory = NotesViewModel.factory(repository))
    val notes by vm.notes.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.notes_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddNote,
                modifier = Modifier.size(40.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.notes_add), modifier = Modifier.size(20.dp))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        vm.setSearchQuery(it)
                    },
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(bottom = 56.dp)) {
                    if (notes.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.notes_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        val listState = rememberLazyListState()
                        var localNotes by remember { mutableStateOf(notes) }
                        val reorderState = rememberReorderableLazyListState(
                            lazyListState = listState,
                            onMove = { from, to ->
                                localNotes = localNotes.toMutableList().apply { add(to.index, removeAt(from.index)) }
                                true
                            }
                        )
                        LaunchedEffect(notes) {
                            if (!reorderState.isAnyItemDragging) {
                                localNotes = notes
                            }
                        }
                        LaunchedEffect(localNotes) {
                            if (localNotes != notes) {
                                delay(500)
                                vm.reorderNotes(localNotes)
                            }
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            state = listState
                        ) {
                            items(localNotes, key = { it.id }) { note ->
                                ReorderableItem(reorderState, key = note.id) { isDragging ->
                                    val dismissState = rememberSwipeToDismissBoxState(
                                        confirmValueChange = { value ->
                                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                                vm.softDelete(note.id)
                                                scope.launch {
                                                    val result = snackbarHostState.showSnackbar(
                                                        message = context.getString(R.string.notes_deleted),
                                                        actionLabel = context.getString(R.string.notes_undo),
                                                        duration = SnackbarDuration.Short
                                                    )
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        vm.restoreNote(note.id)
                                                    }
                                                }
                                                true
                                            } else false
                                        }
                                    )
                                    SwipeToDismissBox(
                                        state = dismissState,
                                        backgroundContent = {
                                            val color by animateColorAsState(
                                                targetValue = when (dismissState.targetValue) {
                                                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                                                    else -> Color.Transparent
                                                },
                                                label = "swipe_color"
                                            )
                                            Box(
                                                Modifier.fillMaxSize().background(color).padding(horizontal = 20.dp),
                                                contentAlignment = Alignment.CenterEnd
                                            ) {
                                                // Показываем иконку только во время свайпа,
                                                // иначе белая корзина просвечивает сквозь прозрачный
                                                // фон строки в тёмной теме (выглядит как задвоение).
                                                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                                                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White)
                                                }
                                            }
                                        },
                                        enableDismissFromStartToEnd = false
                                    ) {
                                        NoteRow(
                                            note = note,
                                            onClick = { onNoteClick(note.id) },
                                            onToggleCompleted = { vm.toggleCompleted(note) },
                                            onToggleImportant = { vm.toggleImportant(note) },
                                            onDelete = {
                                                vm.softDelete(note.id)
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        message = context.getString(R.string.notes_deleted)
                                                    )
                                                }
                                            },
                                            dragModifier = Modifier.draggableHandle(),
                                            isDragging = isDragging
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = onOpenTrash,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .size(40.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = stringResource(R.string.trash_title),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: Note,
    onClick: () -> Unit,
    onToggleCompleted: () -> Unit,
    onToggleImportant: () -> Unit,
    onDelete: () -> Unit,
    dragModifier: Modifier = Modifier,
    isDragging: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp)
            .background(if (isDragging) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = Icons.Default.Menu, contentDescription = null, modifier = dragModifier.then(Modifier.size(24.dp)), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = onToggleImportant, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = if (note.isImportant) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = stringResource(R.string.notes_important),
                tint = if (note.isImportant) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Checkbox(
            checked = note.isCompleted,
            onCheckedChange = { onToggleCompleted() },
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 12.dp)
        ) {
            Text(
                text = note.title.ifBlank { stringResource(R.string.notes_content_hint) },
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    textDecoration = if (note.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                ),
            )
            if (note.content.isNotBlank()) {
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                text = DateUtils.formatDateTime(note.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.notes_delete),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
