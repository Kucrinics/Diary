package ru.diary.app.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.diary.app.data.db.DateUtils
import ru.diary.app.data.model.Note
import ru.diary.app.data.repository.NoteRepository

class NotesViewModel(private val repository: NoteRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")

    val notes: StateFlow<List<Note>> = combine(
        repository.observeAll(),
        _searchQuery
    ) { allNotes, query ->
        if (query.isBlank()) allNotes
        else {
            val q = query.lowercase()
            allNotes.filter {
                it.title.lowercase().contains(q) || it.content.lowercase().contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    suspend fun addAndGetId(title: String, content: String): Long {
        return repository.add(Note(title = title, content = content, date = DateUtils.today(), updatedAt = DateUtils.now()))
    }

    fun add(title: String, content: String, isImportant: Boolean = false) {
        viewModelScope.launch {
            repository.add(Note(title = title, content = content, date = DateUtils.today(), updatedAt = DateUtils.now(), isImportant = isImportant))
        }
    }

    fun update(note: Note) {
        viewModelScope.launch { repository.update(note.copy(updatedAt = DateUtils.now())) }
    }

    fun softDelete(id: Long) {
        viewModelScope.launch {
            repository.getById(id)?.let { note ->
                repository.update(note.copy(isDeleted = true, deletedAt = System.currentTimeMillis()))
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteById(id) }
    }

    fun observeTrash() = repository.observeTrash()

    fun restoreNote(id: Long) {
        viewModelScope.launch { repository.restore(id) }
    }

    fun toggleImportant(note: Note) {
        viewModelScope.launch {
            repository.update(note.copy(isImportant = !note.isImportant, updatedAt = DateUtils.now()))
        }
    }

    fun toggleCompleted(note: Note) {
        viewModelScope.launch {
            repository.update(note.copy(isCompleted = !note.isCompleted, updatedAt = DateUtils.now()))
        }
    }

    fun reorderNotes(reordered: List<Note>) {
        viewModelScope.launch {
            reordered.forEachIndexed { index, note ->
                repository.updateSortOrder(note.id, index)
            }
        }
    }

    companion object {
        fun factory(repo: NoteRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = NotesViewModel(repo) as T
        }
    }
}
