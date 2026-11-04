package ru.diary.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.diary.app.R
import ru.diary.app.data.model.Note
import ru.diary.app.data.model.Task
import ru.diary.app.data.repository.NoteRepository
import ru.diary.app.data.repository.TaskRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    taskRepository: TaskRepository,
    noteRepository: NoteRepository,
    onBack: () -> Unit
) {
    val deletedTasks by taskRepository.observeTrash().collectAsState(initial = emptyList())
    val deletedNotes by noteRepository.observeTrash().collectAsState(initial = emptyList())
    var tabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<Any?>(null) }

    val filteredTasks = if (searchQuery.isBlank()) deletedTasks
    else {
        val q = searchQuery.lowercase()
        deletedTasks.filter {
            it.title.lowercase().contains(q) || it.description.lowercase().contains(q)
        }
    }

    val filteredNotes = if (searchQuery.isBlank()) deletedNotes
    else {
        val q = searchQuery.lowercase()
        deletedNotes.filter {
            it.title.lowercase().contains(q) || it.content.lowercase().contains(q)
        }
    }

    if (showDeleteDialog && itemToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                itemToDelete = null
            },
            title = { Text(stringResource(R.string.trash_permanent_delete_title)) },
            text = {
                val name = when (val item = itemToDelete) {
                    is Task -> item.title
                    is Note -> item.title.ifBlank { stringResource(R.string.trash_no_title) }
                    else -> ""
                }
                Text(stringResource(R.string.trash_permanent_delete_msg, name))
            },
            confirmButton = {
                TextButton(onClick = {
                    when (val item = itemToDelete) {
                        is Task -> scope.launch { taskRepository.deleteById(item.id) }
                        is Note -> scope.launch { noteRepository.deleteById(item.id) }
                    }
                    showDeleteDialog = false
                    itemToDelete = null
                }) {
                    Text(stringResource(R.string.trash_forever_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    itemToDelete = null
                }) {
                    Text(stringResource(R.string.task_cancel))
                }
            }
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trash_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.trash_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Text(
                stringResource(R.string.trash_auto_delete_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }) {
                    Text(stringResource(R.string.trash_tasks_tab, deletedTasks.size), modifier = Modifier.padding(12.dp))
                }
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }) {
                    Text(stringResource(R.string.trash_notes_tab, deletedNotes.size), modifier = Modifier.padding(12.dp))
                }
            }
            when (tabIndex) {
                0 -> TrashTaskList(
                    tasks = filteredTasks,
                    onRestore = { scope.launch { taskRepository.restore(it) } },
                    onDelete = {
                        itemToDelete = it
                        showDeleteDialog = true
                    }
                )
                1 -> TrashNoteList(
                    notes = filteredNotes,
                    onRestore = { scope.launch { noteRepository.restore(it) } },
                    onDelete = {
                        itemToDelete = it
                        showDeleteDialog = true
                    }
                )
            }
        }
    }
}

@Composable
private fun TrashTaskList(tasks: List<Task>, onRestore: (Long) -> Unit, onDelete: (Task) -> Unit) {
    if (tasks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.trash_tasks_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            items(tasks, key = { it.id }) { task ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(task.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (task.description.isNotBlank()) {
                                Text(task.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(stringResource(R.string.trash_task_date, task.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            task.deletedAt?.let { millis ->
                                val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                                Text(stringResource(R.string.trash_deleted_at, fmt.format(Date(millis))), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { onDelete(task) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.trash_delete_cd), tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = { onRestore(task.id) }) {
                            Icon(Icons.Default.RestoreFromTrash, contentDescription = stringResource(R.string.trash_restore_cd), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun TrashNoteList(notes: List<Note>, onRestore: (Long) -> Unit, onDelete: (Note) -> Unit) {
    if (notes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.trash_notes_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            items(notes, key = { it.id }) { note ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(note.title.ifBlank { stringResource(R.string.trash_no_title) }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (note.content.isNotBlank()) {
                                Text(note.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(2.dp))
                            note.deletedAt?.let { millis ->
                                val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                                Text(stringResource(R.string.trash_deleted_at, fmt.format(Date(millis))), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { onDelete(note) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.trash_delete_cd), tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = { onRestore(note.id) }) {
                            Icon(Icons.Default.RestoreFromTrash, contentDescription = stringResource(R.string.trash_restore_cd), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
