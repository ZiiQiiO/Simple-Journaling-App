package com.ziiqiio.simplejournalingapp.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.ApiException
import com.ziiqiio.simplejournalingapp.JournalApp
import com.ziiqiio.simplejournalingapp.data.prefs.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as JournalApp
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(app.container))
    val state by vm.state.collectAsStateWithLifecycle()
    val pendingCount by vm.pendingCount.collectAsStateWithLifecycle()
    var errorText by remember { mutableStateOf<String?>(null) }
    var showDocPicker by remember { mutableStateOf(false) }
    var docSearchQuery by remember { mutableStateOf("") }
    var openDocPickerAfterSignIn by remember { mutableStateOf(false) }

    fun openDocPicker() {
        docSearchQuery = ""
        showDocPicker = true
        vm.searchDocs("")
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val account = GoogleSignIn
                .getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            vm.onSignedIn(account.email)
            errorText = null
            if (openDocPickerAfterSignIn) {
                openDocPickerAfterSignIn = false
                openDocPicker()
            }
        } catch (e: ApiException) {
            openDocPickerAfterSignIn = false
            errorText = signInErrorMessage(e.statusCode, result.resultCode)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            SectionHeader("Google account")
            if (state.signedInEmail == null) {
                Button(onClick = {
                    signInLauncher.launch(app.container.auth.signInIntent())
                }) { Text("Sign in with Google") }
            } else {
                Text("Signed in as ${state.signedInEmail}")
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = vm::signOut) { Text("Sign out") }
            }
            errorText?.let {
                Spacer(Modifier.size(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.size(20.dp))
            HorizontalDivider()
            Spacer(Modifier.size(12.dp))

            SectionHeader("Appearance")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.themeMode == mode,
                        onClick = { vm.setThemeMode(mode) },
                        label = { Text(mode.displayName) }
                    )
                }
            }

            Spacer(Modifier.size(20.dp))
            HorizontalDivider()
            Spacer(Modifier.size(12.dp))

            SectionHeader("Destination Doc")
            Text(
                "By default the app creates a Google Doc for each calendar month inside a \"Journal\" folder on your Drive.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "Current month's Doc: " + (state.currentMonthDocId?.let { "…${it.takeLast(8)}" } ?: "not yet created"),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.size(12.dp))
            Text(
                "Append target: " + (state.overrideDocTitle ?: "monthly Journal docs"),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = state.signedInEmail != null,
                    onClick = {
                        if (app.container.auth.currentAccount() == null) {
                            openDocPickerAfterSignIn = true
                            signInLauncher.launch(app.container.auth.signInIntent())
                        } else {
                            openDocPicker()
                        }
                    }
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Choose Google Doc")
                }
                OutlinedButton(onClick = {
                    vm.setOverrideDocId(null)
                }) { Text("Use monthly docs") }
            }

            Spacer(Modifier.size(20.dp))
            HorizontalDivider()
            Spacer(Modifier.size(12.dp))

            SectionHeader("Append direction")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Newest at bottom", modifier = Modifier.weight(1f))
                Switch(
                    checked = state.appendDirection == "newest_bottom",
                    onCheckedChange = {
                        vm.setAppendDirection(if (it) "newest_bottom" else "newest_top")
                    }
                )
            }

            Spacer(Modifier.size(20.dp))
            HorizontalDivider()
            Spacer(Modifier.size(12.dp))

            SectionHeader("Sync")
            Text("Pending entries: $pendingCount")
            state.lastSyncMessage?.let {
                Spacer(Modifier.size(4.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.size(8.dp))
            Button(onClick = vm::retrySync) { Text("Retry queue now") }

            Spacer(Modifier.size(40.dp))
        }
    }

    if (showDocPicker) {
        LaunchedEffect(docSearchQuery) {
            vm.searchDocs(docSearchQuery)
        }

        AlertDialog(
            onDismissRequest = { showDocPicker = false },
            title = { Text("Choose Google Doc") },
            text = {
                Column {
                    OutlinedTextField(
                        value = docSearchQuery,
                        onValueChange = { docSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search docs") },
                        singleLine = true
                    )
                    Spacer(Modifier.size(12.dp))
                    when {
                        state.docSearchLoading -> Text("Searching...")
                        state.docSearchError != null -> state.docSearchError?.let { message ->
                            Text(
                                message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        state.docSearchResults.isEmpty() -> Text(
                            "No Google Docs found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> LazyColumn {
                            items(state.docSearchResults) { doc ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            vm.selectDoc(doc)
                                            showDocPicker = false
                                        }
                                        .padding(vertical = 10.dp)
                                ) {
                                    Text(doc.name, style = MaterialTheme.typography.bodyMedium)
                                    doc.modifiedTime?.let {
                                        Text(
                                            "Modified $it",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.searchDocs(docSearchQuery) }) {
                    Text("Search")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDocPicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(Modifier.size(8.dp))
}

private fun signInErrorMessage(statusCode: Int, resultCode: Int): String {
    return when (statusCode) {
        GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> "Sign-in canceled"
        CommonStatusCodes.DEVELOPER_ERROR ->
            "Google Sign-In configuration error (code 10). Check the Android OAuth client package name and SHA-1 in Google Cloud."
        GoogleSignInStatusCodes.SIGN_IN_FAILED ->
            "Google Sign-In failed (code 12500). Check the OAuth consent screen, test users, and requested scopes."
        CommonStatusCodes.NETWORK_ERROR ->
            "Sign-in failed because the network is unavailable."
        else -> {
            val statusName = GoogleSignInStatusCodes.getStatusCodeString(statusCode)
            "Sign-in failed: $statusName ($statusCode, result $resultCode)"
        }
    }
}

private val ThemeMode.displayName: String
    get() = when (this) {
        ThemeMode.SYSTEM -> "System"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }
