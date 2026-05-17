package com.ziiqiio.simplejournalingapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.ziiqiio.simplejournalingapp.AppContainer
import com.ziiqiio.simplejournalingapp.data.google.GoogleDocFile
import com.ziiqiio.simplejournalingapp.data.prefs.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class SettingsUiState(
    val signedInEmail: String? = null,
    val appendDirection: String = "newest_bottom",
    val overrideDocId: String? = null,
    val overrideDocTitle: String? = null,
    val currentMonthDocId: String? = null,
    val lastSyncMessage: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pendingCount: Int = 0,
    val docSearchResults: List<GoogleDocFile> = emptyList(),
    val docSearchLoading: Boolean = false,
    val docSearchError: String? = null
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val _localState = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _localState.asStateFlow()

    val pendingCount: StateFlow<Int> = container.journalRepository.pendingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch {
            container.prefs.accountEmail.collect { email ->
                _localState.value = _localState.value.copy(signedInEmail = email)
            }
        }
        viewModelScope.launch {
            container.prefs.appendDirection.collect { dir ->
                _localState.value = _localState.value.copy(appendDirection = dir)
            }
        }
        viewModelScope.launch {
            container.prefs.overrideDocId.collect { id ->
                _localState.value = _localState.value.copy(overrideDocId = id)
                if (!id.isNullOrBlank() && _localState.value.overrideDocTitle.isNullOrBlank()) {
                    fetchSelectedDocTitle(id)
                }
            }
        }
        viewModelScope.launch {
            container.prefs.overrideDocTitle.collect { title ->
                _localState.value = _localState.value.copy(overrideDocTitle = title)
            }
        }
        viewModelScope.launch {
            container.prefs.lastSyncMessage.collect { message ->
                _localState.value = _localState.value.copy(lastSyncMessage = message)
            }
        }
        viewModelScope.launch {
            container.prefs.themeMode.collect { mode ->
                _localState.value = _localState.value.copy(themeMode = mode)
            }
        }
        viewModelScope.launch {
            val key = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
            val docId = container.prefs.getMonthlyDocId(key)
            _localState.value = _localState.value.copy(currentMonthDocId = docId)
        }
    }

    fun onSignedIn(email: String?) {
        viewModelScope.launch { container.prefs.setAccountEmail(email) }
    }

    fun signOut() {
        viewModelScope.launch {
            container.auth.signOut()
            container.prefs.setAccountEmail(null)
        }
    }

    fun setAppendDirection(direction: String) {
        viewModelScope.launch { container.prefs.setAppendDirection(direction) }
    }

    fun setOverrideDocId(id: String?) {
        viewModelScope.launch { container.prefs.setOverrideDoc(id, null) }
    }

    fun searchDocs(query: String) {
        viewModelScope.launch {
            _localState.value = _localState.value.copy(
                docSearchLoading = true,
                docSearchError = null
            )
            runCatching { container.docsRepository.searchDocs(query) }
                .onSuccess { results ->
                    _localState.value = _localState.value.copy(
                        docSearchResults = results,
                        docSearchLoading = false
                    )
                }
                .onFailure { error ->
                    _localState.value = _localState.value.copy(
                        docSearchLoading = false,
                        docSearchError = error.message ?: "Could not search Google Docs."
                    )
                }
        }
    }

    fun selectDoc(doc: GoogleDocFile) {
        viewModelScope.launch { container.prefs.setOverrideDoc(doc.id, doc.name) }
    }

    private fun fetchSelectedDocTitle(docId: String) {
        viewModelScope.launch {
            runCatching { container.docsRepository.getDocTitle(docId) }
                .getOrNull()
                ?.let { title -> container.prefs.setOverrideDoc(docId, title) }
        }
    }

    fun retrySync() {
        viewModelScope.launch {
            container.prefs.setLastSyncMessage("Sync queued...")
            container.journalRepository.retryNow()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.prefs.setThemeMode(mode) }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(container) as T
        }
    }
}
