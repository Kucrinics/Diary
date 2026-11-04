package ru.diary.app.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.diary.app.R
import ru.diary.app.data.repository.NoteRepository
import ru.diary.app.ui.VoiceInputTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditScreen(
    noteId: Long,
    repository: NoteRepository,
    onBack: () -> Unit
) {
    val vm: NotesViewModel = viewModel(factory = NotesViewModel.factory(repository))
    val notes by vm.notes.collectAsState()

    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    var isLoaded by rememberSaveable { mutableStateOf(false) }
    var currentNoteId by rememberSaveable { mutableStateOf(noteId) }
    var isImportant by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(noteId, notes) {
        if (!isLoaded) {
            if (noteId == 0L) {
                title = ""
                content = ""
                isLoaded = true
            } else {
                val note = notes.find { it.id == noteId }
                if (note != null) {
                    title = note.title
                    content = note.content
                    isImportant = note.isImportant
                    isLoaded = true
                }
            }
        }
    }

    fun saveAndBack() {
        if (title.isBlank() && content.isBlank()) {
            onBack()
            return
        }
        if (currentNoteId == 0L) {
            vm.add(title.trim(), content.trim(), isImportant)
        } else {
            val existing = notes.find { it.id == currentNoteId }
            if (existing != null) {
                vm.update(existing.copy(title = title.trim(), content = content.trim(), isImportant = isImportant))
            }
        }
        onBack()
    }

    BackHandler { saveAndBack() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { saveAndBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_ok)
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { saveAndBack() }) {
                        Text(
                            stringResource(R.string.notes_save),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            VoiceInputTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.notes_title_hint)) },
                modifier = Modifier.fillMaxWidth()
            )
            VoiceInputTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(R.string.notes_content_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .heightIn(min = 150.dp)
            )

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(ru.diary.app.R.string.notes_important))
                IconButton(onClick = { isImportant = !isImportant }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (isImportant) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = null,
                        tint = if (isImportant) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
