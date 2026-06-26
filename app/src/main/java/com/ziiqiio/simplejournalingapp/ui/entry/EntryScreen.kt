package com.ziiqiio.simplejournalingapp.ui.entry

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ziiqiio.simplejournalingapp.JournalApp
import com.ziiqiio.simplejournalingapp.data.db.JournalEntry
import com.ziiqiio.simplejournalingapp.data.db.SyncStatus
import com.ziiqiio.simplejournalingapp.data.google.JournalFormatter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as JournalApp
    val vm: EntryViewModel = viewModel(factory = EntryViewModel.Factory(app.container))
    val state by vm.state.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val savedTags by vm.savedTags.collectAsStateWithLifecycle()
    val pendingCount by vm.pendingCount.collectAsStateWithLifecycle()
    val syncUiState by vm.syncUiState.collectAsStateWithLifecycle()

    val snackbarHost = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    var tagSheetOpen by remember { mutableStateOf(false) }
    var detailEntry by remember { mutableStateOf<JournalEntry?>(null) }
    var syncHintOpen by remember { mutableStateOf(false) }
    var confirmClearSyncedOpen by remember { mutableStateOf(false) }
    val pendingEntries = entries.filter { it.status != SyncStatus.SYNCED }
    val syncedEntries = entries.filter { it.status == SyncStatus.SYNCED }

    LaunchedEffect(state.justSaved) {
        if (state.justSaved) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            snackbarHost.showSnackbar("Entry saved")
            vm.consumeSavedFlag()
        }
    }

    LaunchedEffect(syncHintOpen) {
        if (syncHintOpen) {
            kotlinx.coroutines.delay(if (syncUiState.isOnline) 1_400 else 4_000)
            syncHintOpen = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Journal") },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        BadgedBox(badge = {
                            if (pendingCount > 0) Badge { Text(pendingCount.toString()) }
                        }) {
                            IconButton(onClick = { syncHintOpen = true }) {
                                Icon(
                                    imageVector = when {
                                        syncUiState.isSyncing -> Icons.Default.Sync
                                        syncUiState.isOnline -> Icons.Default.Wifi
                                        else -> Icons.Default.WifiOff
                                    },
                                    contentDescription = when {
                                        syncUiState.isSyncing -> "Syncing"
                                        syncUiState.isOnline -> "Connected"
                                        else -> "Not connected"
                                    }
                                )
                            }
                        }
                        if (syncUiState.isSyncing) {
                            Text(
                                "Updating your Journal Entries",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        DropdownMenu(
                            expanded = syncHintOpen,
                            onDismissRequest = { syncHintOpen = false }
                        ) {
                            Text(
                                when {
                                    syncUiState.isSyncing -> "Updating your Journal Entries"
                                    syncUiState.isOnline -> "Connected and ready to sync"
                                    else -> "No internet connection. Entries will sync when you're back online."
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (state.restoredFromDraft) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            "Continuing your unsaved entry",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(onClick = vm::dismissBanner) { Text("Dismiss") }
                    }
                }
                Spacer(Modifier.size(8.dp))
            }

            LiveTimestamp()
            Spacer(Modifier.size(12.dp))

            PromptSection(
                prompt = state.prompt?.text,
                onPromptChange = vm::setPromptText,
                onGenerate = vm::requestPrompt,
                onDismiss = vm::dismissPrompt
            )

            Spacer(Modifier.size(16.dp))
            MoodEnergyControls(
                mood = state.moodIndex,
                energy = state.energyLevel,
                onMoodChange = { i ->
                    vm.setMood(i)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                onEnergyChange = { i ->
                    vm.setEnergy(i)
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            )
            Spacer(Modifier.size(16.dp))
            TagsRow(
                tags = state.tags,
                allTagNames = (savedTags.map { it.tagName } + state.tags).distinctBy { it.lowercase() },
                onAddTagTap = { tagSheetOpen = true },
                onRemove = vm::removeTag
            )

            Spacer(Modifier.size(16.dp))
            OutlinedTextField(
                value = state.body,
                onValueChange = vm::setBody,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                label = { Text("Entry") },
                placeholder = { Text("Write whatever comes…") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
            )
            Spacer(Modifier.size(16.dp))
            Button(
                onClick = {
                    vm.saveEntry()
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save Entry") }

            Spacer(Modifier.size(24.dp))
            HorizontalDivider()
            Spacer(Modifier.size(16.dp))
            EntrySection(
                title = "Pending Entries",
                entries = pendingEntries,
                emptyText = "No entries waiting to sync.",
                onDismiss = { vm.dismissEntry(it.id) },
                onOpenGoogleDoc = { },
                onClick = { detailEntry = it }
            )
            Spacer(Modifier.size(16.dp))
            EntrySection(
                title = "Synced Entries",
                entries = syncedEntries,
                emptyText = "Synced entries will appear here after they reach Google Docs.",
                headerAction = if (syncedEntries.isNotEmpty()) {
                    {
                        TextButton(onClick = { confirmClearSyncedOpen = true }) {
                            Text("Clear all")
                        }
                    }
                } else {
                    null
                },
                onDismiss = { vm.dismissEntry(it.id) },
                onOpenGoogleDoc = { entry ->
                    entry.syncedDocId?.let { docId -> openGoogleDoc(context, docId, entry.syncedTabId) }
                },
                onClick = { detailEntry = it }
            )
            Spacer(Modifier.size(40.dp))
        }
    }

    if (tagSheetOpen) {
        TagPickerSheet(
            savedTags = savedTags.map { it.tagName },
            currentlySelected = state.tags,
            onDismiss = { tagSheetOpen = false },
            onSelect = { vm.addTag(it) },
            onDelete = { vm.deleteSavedTag(it) }
        )
    }

    detailEntry?.let { entry ->
        EntryDetailSheet(entry = entry, onClose = { detailEntry = null })
    }

    if (confirmClearSyncedOpen) {
        AlertDialog(
            onDismissRequest = { confirmClearSyncedOpen = false },
            title = { Text("Clear synced entries?") },
            text = { Text("This will dismiss all synced entries from this list. They will remain in Google Docs.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.dismissSyncedEntries()
                    confirmClearSyncedOpen = false
                }) {
                    Text("Clear all")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearSyncedOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EntrySection(
    title: String,
    entries: List<JournalEntry>,
    emptyText: String,
    headerAction: (@Composable () -> Unit)? = null,
    onDismiss: (JournalEntry) -> Unit,
    onOpenGoogleDoc: (JournalEntry) -> Unit,
    onClick: (JournalEntry) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        headerAction?.invoke()
    }
    Spacer(Modifier.size(8.dp))

    if (entries.isEmpty()) {
        Text(
            emptyText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        entries.forEach { entry ->
            EntryRow(
                entry = entry,
                onDismiss = { onDismiss(entry) },
                onOpenGoogleDoc = { onOpenGoogleDoc(entry) },
                onClick = { onClick(entry) }
            )
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun LiveTimestamp() {
    val format = remember { SimpleDateFormat("EEEE, MMM d, yyyy • h:mm a", Locale.getDefault()) }
    var nowText by remember { mutableStateOf(format.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            nowText = format.format(Date())
            kotlinx.coroutines.delay(30_000)
        }
    }
    Text(
        nowText,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PromptSection(
    prompt: String?,
    onPromptChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onDismiss: () -> Unit
) {
    Column {
        OutlinedTextField(
            value = prompt.orEmpty(),
            onValueChange = onPromptChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Prompt") },
            placeholder = { Text("Type your own prompt") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
        )
        Spacer(Modifier.size(8.dp))
        OutlinedButton(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
            Text("Get Prompt Suggestion")
        }
        if (prompt != null) {
            Spacer(Modifier.size(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "This prompt will be saved with your entry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Clear prompt")
                }
            }
        }
    }
}

@Composable
private fun MoodEnergyControls(
    mood: Int,
    energy: Int,
    onMoodChange: (Int) -> Unit,
    onEnergyChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconStepper(
            label = "Mood",
            valueText = JournalFormatter.moodLabel(mood),
            iconText = JournalFormatter.MOOD_EMOJI[mood.coerceIn(0, 4)],
            canDecrease = mood > 0,
            canIncrease = mood < 4,
            onDecrease = { onMoodChange((mood - 1).coerceIn(0, 4)) },
            onIncrease = { onMoodChange((mood + 1).coerceIn(0, 4)) },
            modifier = Modifier.weight(1f)
        )
        IconStepper(
            label = "Energy",
            valueText = "$energy / 5",
            iconText = JournalFormatter.energyIcon(energy),
            canDecrease = energy > 1,
            canIncrease = energy < 5,
            onDecrease = { onEnergyChange((energy - 1).coerceIn(1, 5)) },
            onIncrease = { onEnergyChange((energy + 1).coerceIn(1, 5)) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun IconStepper(
    label: String,
    valueText: String,
    iconText: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onDecrease,
                    enabled = canDecrease,
                    modifier = Modifier.size(44.dp)
                ) {
                    Text("-", style = MaterialTheme.typography.headlineMedium)
                }
                Text(
                    iconText,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                IconButton(
                    onClick = onIncrease,
                    enabled = canIncrease,
                    modifier = Modifier.size(44.dp)
                ) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            }
            Text(
                valueText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TagsRow(
    tags: List<String>,
    allTagNames: List<String>,
    onAddTagTap: () -> Unit,
    onRemove: (String) -> Unit
) {
    Column {
        Text("Tags", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.size(4.dp))
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            tags.forEach { name ->
                val colors = tagInputChipColors(name, allTagNames)
                InputChip(
                    selected = true,
                    onClick = { onRemove(name) },
                    label = { Text(name) },
                    colors = colors,
                    trailingIcon = {
                        Icon(Icons.Default.Close, contentDescription = "Remove tag", modifier = Modifier.size(16.dp))
                    }
                )
            }
            AssistChip(
                onClick = onAddTagTap,
                label = { Text("Add") },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TagPickerSheet(
    savedTags: List<String>,
    currentlySelected: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var tagPendingDelete by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search or create tag") },
                singleLine = true
            )
            Spacer(Modifier.size(12.dp))

            val filtered = savedTags.filter { it.contains(query, ignoreCase = true) }
            val exactMatch = savedTags.any { it.equals(query.trim(), ignoreCase = true) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filtered.forEach { name ->
                        val isSelected = name in currentlySelected
                        val colors = tagFilterChipColors(name, savedTags)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    onSelect(name)
                                    scope.launch { sheetState.hide(); onDismiss() }
                                },
                                label = { Text(name) },
                                colors = colors,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                            IconButton(
                                onClick = { tagPendingDelete = name },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Delete tag $name",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                if (query.isNotBlank() && !exactMatch) {
                    TextButton(onClick = {
                        onSelect(query.trim())
                        scope.launch { sheetState.hide(); onDismiss() }
                    }) {
                        Text("Create new tag: \"${query.trim()}\"")
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
        }
    }

    tagPendingDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { tagPendingDelete = null },
            title = { Text("Delete tag?") },
            text = { Text("Remove \"$name\" from your saved tags? Existing journal entries will keep their text.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(name)
                    tagPendingDelete = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { tagPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun tagInputChipColors(tagName: String, allTagNames: List<String>): androidx.compose.material3.SelectableChipColors {
    val color = tagColor(tagName, allTagNames)
    return InputChipDefaults.inputChipColors(
        selectedContainerColor = color.copy(alpha = 0.22f),
        selectedLabelColor = color,
        selectedLeadingIconColor = color,
        selectedTrailingIconColor = color,
    )
}

@Composable
private fun tagFilterChipColors(tagName: String, allTagNames: List<String>): androidx.compose.material3.SelectableChipColors {
    val color = tagColor(tagName, allTagNames)
    return FilterChipDefaults.filterChipColors(
        containerColor = color.copy(alpha = 0.14f),
        labelColor = color,
        iconColor = color,
        selectedContainerColor = color.copy(alpha = 0.22f),
        selectedLabelColor = color,
        selectedLeadingIconColor = color,
        selectedTrailingIconColor = color,
    )
}

private fun tagColor(tagName: String, allTagNames: List<String>): Color {
    defaultTagColors[tagName.lowercase()]?.let { return it }

    val reservedColors = defaultTagColors.values.toSet()
    val customPalette = tagPalette.filterNot { it in reservedColors }
    val customTags = allTagNames
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.lowercase() !in defaultTagColors }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }

    val index = customTags.indexOfFirst { it.equals(tagName, ignoreCase = true) }
    return customPalette.getOrElse(index) { customPalette[index.floorMod(customPalette.size)] }
}

private fun Int.floorMod(other: Int): Int = ((this % other) + other) % other

private val defaultTagColors = mapOf(
    "gratitude" to Color(0xFFD81B60),
    "idea" to Color(0xFF1E88E5),
    "goal" to Color(0xFF43A047),
    "reflection" to Color(0xFF8E24AA),
    "event" to Color(0xFFF4511E),
)

private val tagPalette = listOf(
    Color(0xFFE53935),
    Color(0xFFD81B60),
    Color(0xFF8E24AA),
    Color(0xFF5E35B1),
    Color(0xFF3949AB),
    Color(0xFF1E88E5),
    Color(0xFF039BE5),
    Color(0xFF00897B),
    Color(0xFF43A047),
    Color(0xFFF4511E),
    Color(0xFF6D4C41),
    Color(0xFF546E7A),
    Color(0xFFC0A000),
    Color(0xFF7CB342),
    Color(0xFF00ACC1),
    Color(0xFFFB8C00),
)

@Composable
private fun EntryRow(
    entry: JournalEntry,
    onDismiss: () -> Unit,
    onOpenGoogleDoc: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        onClick = onClick
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.formattedDate,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(entry.status)
            }
            Spacer(Modifier.size(4.dp))
            val preview = entry.body.take(80).let { if (entry.body.length > 80) "$it…" else it }
            if (preview.isNotEmpty()) {
                Text(preview, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    JournalFormatter.moodLabel(entry.moodIndex) + "  •  Energy ${JournalFormatter.energyLabel(entry.energyLevel)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (entry.status == SyncStatus.SYNCED) {
                    if (!entry.syncedDocId.isNullOrBlank()) {
                        TextButton(onClick = onOpenGoogleDoc) { Text("Open Google Doc") }
                    }
                    TextButton(onClick = onDismiss) { Text("Dismiss") }
                }
            }
        }
    }
}

private fun openGoogleDoc(context: Context, docId: String, tabId: String?) {
    val tabQuery = tabId?.takeIf { it.isNotBlank() }?.let { "?tab=$it" }.orEmpty()
    val docUri = Uri.parse("https://docs.google.com/document/d/$docId/edit$tabQuery")
    val docsIntent = Intent(Intent.ACTION_VIEW, docUri)
        .setPackage("com.google.android.apps.docs.editors.docs")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(docsIntent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, docUri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

@Composable
private fun StatusBadge(status: SyncStatus) {
    when (status) {
        SyncStatus.PENDING -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(4.dp))
            Text("Waiting to sync…", style = MaterialTheme.typography.labelSmall)
        }
        SyncStatus.SYNCED -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.size(4.dp))
            Text("Synced", style = MaterialTheme.typography.labelSmall)
        }
        SyncStatus.FAILED -> Text(
            "Sync failed — will retry",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryDetailSheet(entry: JournalEntry, onClose: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(entry.formattedDate, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(8.dp))
            Text(
                "Mood: ${JournalFormatter.moodLabel(entry.moodIndex)}   |   Energy: ${JournalFormatter.energyLabel(entry.energyLevel)}",
                style = MaterialTheme.typography.bodySmall
            )
            JournalFormatter.formatTagsLine(entry.tags)?.let {
                Spacer(Modifier.size(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            entry.promptUsed?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.size(4.dp))
                Text("Prompt: $it", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.size(12.dp))
            Text(entry.body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.size(16.dp))
        }
    }
}
