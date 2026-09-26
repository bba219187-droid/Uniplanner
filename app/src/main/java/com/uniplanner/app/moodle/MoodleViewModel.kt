package com.uniplanner.app.moodle

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssignmentRow(val assignment: MoodleAssignment, val courseName: String, val state: SubmissionState)

data class MoodleUiState(
    val account: MoodleAccount? = null,
    val loading: Boolean = false,
    val courses: List<MoodleCourse> = emptyList(),
    val assignments: List<AssignmentRow> = emptyList(),
    /** Error code from Moodle (or "network"), turned into a friendly message by the screen. */
    val error: String? = null,
    val errorDetail: String? = null,
    val message: MoodleMessage? = null,
)

sealed interface MoodleMessage {
    data class Imported(val result: ImportResult) : MoodleMessage
    data class Submitted(val name: String, val final: Boolean) : MoodleMessage
}

class MoodleViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MoodleRepository(app)
    private val _state = MutableStateFlow(MoodleUiState(account = runCatching { repo.account() }.getOrNull()))
    val state: StateFlow<MoodleUiState> = _state.asStateFlow()

    init {
        if (_state.value.account != null) refresh()
    }

    fun connect(site: String, username: String, password: String) = launchAction {
        _state.update { it.copy(account = repo.connect(site, username, password)) }
        load()
    }

    fun refresh() = launchAction { load() }

    fun disconnect() {
        repo.disconnect()
        _state.value = MoodleUiState()
    }

    fun importDeadlines() = launchAction {
        val s = _state.value
        val result = repo.importDeadlines(s.courses, s.assignments.map { it.assignment })
        _state.update { it.copy(message = MoodleMessage.Imported(result)) }
    }

    fun submit(row: AssignmentRow, uri: Uri, final: Boolean) = launchAction {
        val client = repo.client() ?: return@launchAction
        val resolver = getApplication<Application>().contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "trabalho"
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val input = resolver.openInputStream(uri) ?: throw MoodleException("file", "Could not open file")
        client.submitFile(row.assignment.id, name, mime, input, submitForGrading = final)
        _state.update { it.copy(message = MoodleMessage.Submitted(row.assignment.name, final)) }
        load()
    }

    fun clearMessage() = _state.update { it.copy(message = null, error = null, errorDetail = null) }

    private suspend fun load() {
        val client = repo.client() ?: return
        val account = _state.value.account ?: return
        val courses = client.courses(account.userId)
        val names = courses.associate { it.id to it.name }
        val now = System.currentTimeMillis()
        val rows = client.assignments(courses.map { it.id })
            .sortedWith(compareBy<MoodleAssignment> { a -> a.dueAt?.let { if (it < now) 1 else 0 } ?: 2 }.thenBy { it.dueAt })
            .map { a ->
                val state = if (a.dueAt == null || a.dueAt > now) {
                    runCatching { client.submissionState(a.id) }.getOrDefault(SubmissionState.UNKNOWN)
                } else {
                    SubmissionState.UNKNOWN
                }
                AssignmentRow(a, names[a.courseId].orEmpty(), state)
            }
        _state.update { it.copy(courses = courses, assignments = rows) }
    }

    /** Runs a Moodle action with a loading flag and turns failures into an error code. */
    private fun launchAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, errorDetail = null) }
            try {
                block()
            } catch (e: MoodleException) {
                _state.update { it.copy(error = e.code, errorDetail = e.message) }
            } catch (e: java.io.IOException) {
                _state.update { it.copy(error = "network", errorDetail = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(error = "unknown", errorDetail = e.message) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}
