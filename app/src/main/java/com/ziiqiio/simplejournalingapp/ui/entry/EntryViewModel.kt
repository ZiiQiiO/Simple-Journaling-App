package com.ziiqiio.simplejournalingapp.ui.entry

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ziiqiio.simplejournalingapp.AppContainer
import com.ziiqiio.simplejournalingapp.data.db.Draft
import com.ziiqiio.simplejournalingapp.data.db.JournalEntry
import com.ziiqiio.simplejournalingapp.data.db.SavedTag
import com.ziiqiio.simplejournalingapp.data.prompts.PromptSuggestion
import com.ziiqiio.simplejournalingapp.sync.SyncWorker
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EntryUiState(
    val moodIndex: Int = 2,
    val energyLevel: Int = 3,
    val tags: List<String> = emptyList(),
    val prompt: PromptSuggestion? = null,
    val body: String = "",
    val restoredFromDraft: Boolean = false,
    val justSaved: Boolean = false,
    val syncedToast: String? = null
)

data class SyncUiState(
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false
)

class EntryViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(EntryUiState())
    val state: StateFlow<EntryUiState> = _state.asStateFlow()

    val entries: StateFlow<List<JournalEntry>> = container.journalRepository.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val savedTags: StateFlow<List<SavedTag>> = container.journalRepository.tags
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingCount: StateFlow<Int> = container.journalRepository.pendingCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val syncUiState: StateFlow<SyncUiState> = combine(
        observeConnectivity(container.context),
        WorkManager.getInstance(container.context)
            .getWorkInfosForUniqueWorkFlow(SyncWorker.UNIQUE_NAME)
            .map { workInfos -> workInfos.any { it.state == WorkInfo.State.RUNNING } }
    ) { isOnline, isSyncing ->
        SyncUiState(isOnline = isOnline, isSyncing = isSyncing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    init {
        viewModelScope.launch {
            container.journalRepository.seedDefaultTagsIfNeeded()
            container.journalRepository.getDraft()?.let { d ->
                _state.value = _state.value.copy(
                    moodIndex = d.moodIndex,
                    energyLevel = d.energyLevel,
                    tags = d.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    prompt = d.promptText?.let { PromptSuggestion(-1, it) },
                    body = d.body,
                    restoredFromDraft = (d.body.isNotEmpty() || d.tags.isNotEmpty() || d.promptText != null)
                )
            }
        }
        startDraftAutosave()
    }

    @OptIn(FlowPreview::class)
    private fun startDraftAutosave() {
        _state.drop(1).debounce(500).onEach { s ->
            container.journalRepository.saveDraft(
                Draft(
                    moodIndex = s.moodIndex,
                    energyLevel = s.energyLevel,
                    tags = s.tags.joinToString(","),
                    promptText = s.prompt?.text,
                    body = s.body
                )
            )
        }.launchIn(viewModelScope)
    }

    fun setMood(i: Int) { _state.value = _state.value.copy(moodIndex = i) }
    fun setEnergy(i: Int) { _state.value = _state.value.copy(energyLevel = i) }
    fun setBody(b: String) { _state.value = _state.value.copy(body = b) }
    fun setPromptText(text: String) {
        _state.value = _state.value.copy(prompt = text.takeIf { it.isNotBlank() }?.let { PromptSuggestion(-1, it) })
    }
    fun addTag(name: String) {
        val cleaned = name.trim().trimStart('#')
        if (cleaned.isEmpty()) return
        val current = _state.value.tags
        if (cleaned in current) return
        _state.value = _state.value.copy(tags = current + cleaned)
        viewModelScope.launch { container.journalRepository.ensureTag(cleaned) }
    }
    fun removeTag(name: String) {
        _state.value = _state.value.copy(tags = _state.value.tags - name)
    }
    fun deleteSavedTag(name: String) {
        _state.value = _state.value.copy(tags = _state.value.tags - name)
        viewModelScope.launch { container.journalRepository.deleteTag(name) }
    }
    fun dismissBanner() {
        _state.value = _state.value.copy(restoredFromDraft = false)
    }
    fun dismissPrompt() {
        _state.value = _state.value.copy(prompt = null)
    }
    fun requestPrompt() {
        viewModelScope.launch {
            val pick = container.promptRepository.pickRandomPrompt() ?: return@launch
            _state.value = _state.value.copy(prompt = pick)
        }
    }

    fun saveEntry() {
        val s = _state.value
        viewModelScope.launch {
            container.journalRepository.saveEntry(
                moodIndex = s.moodIndex,
                energyLevel = s.energyLevel,
                tagsCsv = s.tags.joinToString(","),
                promptUsed = s.prompt?.text?.takeIf { it.isNotBlank() },
                body = s.body
            )
            _state.value = EntryUiState(justSaved = true)
        }
    }

    fun dismissEntry(id: Long) {
        viewModelScope.launch { container.journalRepository.dismissEntry(id) }
    }

    fun dismissSyncedEntries() {
        viewModelScope.launch { container.journalRepository.dismissSyncedEntries() }
    }

    fun consumeSavedFlag() { _state.value = _state.value.copy(justSaved = false) }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return EntryViewModel(container) as T
        }
    }

    private fun observeConnectivity(context: Context): Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        fun currentOnline(): Boolean {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        trySend(currentOnline())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(currentOnline())
            }

            override fun onLost(network: Network) {
                trySend(currentOnline())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(currentOnline())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }
}
