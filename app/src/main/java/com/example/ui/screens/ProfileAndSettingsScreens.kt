package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.AppLockManager
import com.example.core.AppLockSettings
import com.example.core.LockMethod
import com.example.core.MediaPickerHelper
import com.example.core.PakistanCarrier
import com.example.core.PakistanPhoneValidator
import com.example.core.PreferencesManager
import com.example.core.UiState
import com.example.core.UserUiPreferences
import com.example.data.model.EncryptedBackup
import com.example.ui.components.ArAvatar
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.formatTimestampShort
import com.example.ui.theme.ArDangerRed
import com.example.ui.theme.ArEmeraldBright
import com.example.ui.theme.ArEmeraldDeep
import com.example.ui.theme.ArEmeraldPrimary
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onSignOut: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val publicState by viewModel.publicProfileState.collectAsStateWithLifecycle()
    val privateState by viewModel.privateProfileState.collectAsStateWithLifecycle()
    val banner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(banner) {
        if (!banner.isNullOrBlank()) {
            snackbarHostState.showSnackbar(banner!!)
            viewModel.clearBanner()
        }
    }

    val pub = (publicState as? UiState.Success)?.data
    val priv = (privateState as? UiState.Success)?.data

    var displayName by remember(pub?.displayName) { mutableStateOf(pub?.displayName ?: "") }
    var about by remember(pub?.about) { mutableStateOf(pub?.about ?: "") }
    var mood by remember(pub?.mood) { mutableStateOf(pub?.mood ?: "Available") }
    var newAvatarBase64 by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            newAvatarBase64 = MediaPickerHelper.uriToCompressedBase64(context, uri, 320, 36_000)
        }
    }

    val keyBundle = remember(viewModel.currentUserId, pub?.keyFingerprint) {
        viewModel.cryptoManager.getPublicKeyBundle(viewModel.currentUserId)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Your Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("profile_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .widthIn(max = 580.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.clickable {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                contentAlignment = Alignment.BottomEnd
            ) {
                ArAvatar(
                    displayName = displayName.ifBlank { "AR" },
                    avatarBase64 = newAvatarBase64 ?: (pub?.avatarData ?: ""),
                    size = 96.dp
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(ArEmeraldPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Change avatar", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("@${pub?.username ?: priv?.username ?: "user"}", style = MaterialTheme.typography.titleSmall, color = ArEmeraldBright)
            if (priv != null) {
                Text(
                    "${priv.phoneNumber} • ${priv.carrierName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("profile_name_input")
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = about,
                onValueChange = { about = it },
                label = { Text("About") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().testTag("profile_about_input")
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = mood,
                onValueChange = { mood = it },
                label = { Text("Mood / Status Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Available", "Busy", "At Work", "Inshallah Soon").forEach { preset ->
                    FilterChip(
                        selected = mood == preset,
                        onClick = { mood = preset },
                        label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (displayName.trim().isNotEmpty()) {
                        viewModel.updateProfile(displayName, about, mood, newAvatarBase64)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ArEmeraldPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_profile_btn")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Profile Changes", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = ArEmeraldBright, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("E2EE Identity Key (ECDH P-256)", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        text = "Fingerprint: ${keyBundle.keyFingerprint}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = ArEmeraldBright
                    )
                    Text(
                        text = "Private key is AES-256-GCM wrapped inside Android Keystore and never leaves your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(
                onClick = onSignOut,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ArDangerRed),
                modifier = Modifier.fillMaxWidth().testTag("profile_sign_out_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign Out / Switch Account")
            }
        }
    }
}

private enum class SettingsSection(val title: String) {
    ROOT("Settings"),
    ACCOUNT("Account"),
    PRIVACY("Privacy"),
    CHATS("Chats & Backups"),
    APPEARANCE("Appearance"),
    NOTIFICATIONS("Notifications"),
    STORAGE("Storage & Data"),
    ACCESSIBILITY("Accessibility"),
    LANGUAGE("Language"),
    HELP("Help & Feedback")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    prefsManager: PreferencesManager,
    uiPrefs: UserUiPreferences,
    lockSettings: AppLockSettings,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAppLockConfig: () -> Unit,
    onOpenLinkedDevices: () -> Unit,
    onAccountDeleted: () -> Unit
) {
    var currentSection by remember { mutableStateOf(SettingsSection.ROOT) }
    BackHandler {
        if (currentSection == SettingsSection.ROOT) onBack()
        else currentSection = SettingsSection.ROOT
    }

    val banner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(banner) {
        if (!banner.isNullOrBlank()) {
            snackbarHostState.showSnackbar(banner!!)
            viewModel.clearBanner()
        }
    }

    val pub = (viewModel.publicProfileState.collectAsStateWithLifecycle().value as? UiState.Success)?.data
    val priv = (viewModel.privateProfileState.collectAsStateWithLifecycle().value as? UiState.Success)?.data

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(currentSection.title) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentSection == SettingsSection.ROOT) onBack()
                            else currentSection = SettingsSection.ROOT
                        },
                        modifier = Modifier.testTag("settings_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (currentSection) {
                SettingsSection.ROOT -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            ListItem(
                                leadingContent = {
                                    ArAvatar(
                                        displayName = pub?.displayName ?: "AR",
                                        avatarBase64 = pub?.avatarData ?: "",
                                        size = 56.dp
                                    )
                                },
                                headlineContent = { Text(pub?.displayName ?: "Your Profile", fontWeight = FontWeight.Bold) },
                                supportingContent = { Text("${pub?.about ?: ""} • ${priv?.phoneNumber ?: ""}") },
                                modifier = Modifier.clickable { onOpenProfile() }.testTag("settings_profile_header")
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        }
                        item { SettingsNavItem(Icons.Default.AccountCircle, "Account", "Change +92 number, rotate E2EE key, delete account") { currentSection = SettingsSection.ACCOUNT } }
                        item { SettingsNavItem(Icons.Default.Lock, "Privacy & App Lock", "Last seen, read receipts, disappearing messages, biometric lock") { currentSection = SettingsSection.PRIVACY } }
                        item { SettingsNavItem(Icons.Default.Chat, "Chats & Encrypted Backups", "PBKDF2 encrypted backups & enter-to-send") { currentSection = SettingsSection.CHATS } }
                        item { SettingsNavItem(Icons.Default.Palette, "Appearance", "Dark/Light theme, emerald accents, typography scale") { currentSection = SettingsSection.APPEARANCE } }
                        item { SettingsNavItem(Icons.Default.Notifications, "Notifications", "Message, group, call alerts & vibration") { currentSection = SettingsSection.NOTIFICATIONS } }
                        item { SettingsNavItem(Icons.Default.Storage, "Storage & Data", "Auto-download, data saver, HD media") { currentSection = SettingsSection.STORAGE } }
                        item { SettingsNavItem(Icons.Default.Devices, "Linked Devices", "Manage active multi-device sessions", onOpenLinkedDevices) }
                        item { SettingsNavItem(Icons.Default.AccessibilityNew, "Accessibility", "High contrast, reduced motion, font scaling") { currentSection = SettingsSection.ACCESSIBILITY } }
                        item { SettingsNavItem(Icons.Default.Language, "Language (English / اردو)", if (uiPrefs.languageCode == "ur") "اردو" else "English") { currentSection = SettingsSection.LANGUAGE } }
                        item { SettingsNavItem(Icons.AutoMirrored.Filled.HelpOutline, "Help & Feedback", "Support tickets, FAQs, version 1.0.0") { currentSection = SettingsSection.HELP } }
                    }
                }
                SettingsSection.ACCOUNT -> AccountSettingsView(viewModel, onAccountDeleted)
                SettingsSection.PRIVACY -> PrivacySettingsView(viewModel, lockSettings, onOpenAppLockConfig)
                SettingsSection.CHATS -> ChatsAndBackupsSettingsView(viewModel, prefsManager, uiPrefs)
                SettingsSection.APPEARANCE -> AppearanceSettingsView(viewModel, prefsManager, uiPrefs)
                SettingsSection.NOTIFICATIONS -> NotificationsSettingsView(prefsManager, uiPrefs)
                SettingsSection.STORAGE -> StorageSettingsView(prefsManager, uiPrefs)
                SettingsSection.ACCESSIBILITY -> AccessibilitySettingsView(prefsManager, uiPrefs)
                SettingsSection.LANGUAGE -> LanguageSettingsView(viewModel, prefsManager, uiPrefs)
                SettingsSection.HELP -> HelpAndSupportView(viewModel)
            }
        }
    }
}

@Composable
private fun SettingsNavItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        leadingContent = {
            Icon(icon, contentDescription = title, tint = ArEmeraldBright)
        },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun AccountSettingsView(viewModel: MainViewModel, onAccountDeleted: () -> Unit) {
    val priv = (viewModel.privateProfileState.collectAsStateWithLifecycle().value as? UiState.Success)?.data
    val pub = (viewModel.publicProfileState.collectAsStateWithLifecycle().value as? UiState.Success)?.data
    var showChangePhone by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Registered Number", style = MaterialTheme.typography.labelMedium, color = ArEmeraldBright)
                Text(priv?.phoneNumber ?: "Not set", style = MaterialTheme.typography.titleMedium)
                Text("Carrier: ${priv?.carrierName ?: "Unknown"} • Email: ${priv?.email ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(onClick = { showChangePhone = true }) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Change Mobile Number")
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("End-to-End Identity Keys", style = MaterialTheme.typography.titleSmall)
                Text("Current Key Fingerprint: ${pub?.keyFingerprint ?: "None"}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                OutlinedButton(onClick = { viewModel.rotateIdentityKeys() }) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rotate ECDH P-256 Identity Key")
                }
            }
        }

        OutlinedButton(
            onClick = { showDeleteConfirm = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ArDangerRed),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Delete My Account Data")
        }
    }

    if (showChangePhone) {
        var digits by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        val carrier = PakistanPhoneValidator.detectCarrier(digits)
        AlertDialog(
            onDismissRequest = { showChangePhone = false },
            title = { Text("Change Pakistan Mobile (+92)") },
            text = {
                Column {
                    OutlinedTextField(
                        value = digits,
                        onValueChange = { digits = it.filter { ch -> ch.isDigit() } },
                        label = { Text("10 digits starting with 3 (e.g. 3001234567)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Detected Carrier: ${carrier.label}", style = MaterialTheme.typography.bodySmall, color = ArEmeraldBright)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!PakistanPhoneValidator.isValidNumber("+92", digits)) {
                        error = "Enter a valid 10-digit +92 mobile number starting with 3."
                    } else {
                        viewModel.updatePhoneNumber("+92", PakistanPhoneValidator.formatE164("+92", digits), carrier.label)
                        showChangePhone = false
                    }
                }) { Text("Update") }
            },
            dismissButton = { TextButton(onClick = { showChangePhone = false }) { Text("Cancel") } }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Account Data?") },
            text = { Text("This permanently deletes your private profile record from Firestore and signs you out.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteAccountData(onAccountDeleted)
                }) { Text("Delete", color = ArDangerRed) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PrivacySettingsView(
    viewModel: MainViewModel,
    lockSettings: AppLockSettings,
    onOpenAppLockConfig: () -> Unit
) {
    val pub = (viewModel.publicProfileState.collectAsStateWithLifecycle().value as? UiState.Success)?.data
    val priv = (viewModel.privateProfileState.collectAsStateWithLifecycle().value as? UiState.Success)?.data

    var lastSeen by remember(pub?.privacyLastSeen) { mutableStateOf(pub?.privacyLastSeen ?: "everyone") }
    var photoVis by remember(pub?.privacyPhoto) { mutableStateOf(pub?.privacyPhoto ?: "everyone") }
    var aboutVis by remember(pub?.privacyAbout) { mutableStateOf(pub?.privacyAbout ?: "everyone") }
    var readReceipts by remember(pub?.readReceiptsEnabled) { mutableStateOf(pub?.readReceiptsEnabled ?: true) }
    var disappearing by remember(priv?.disappearingDefaultSeconds) { mutableStateOf(priv?.disappearingDefaultSeconds ?: 0L) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth().clickable { onOpenAppLockConfig() }.testTag("privacy_app_lock_card")
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = ArEmeraldBright, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("App Lock & Biometrics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        if (lockSettings.lockEnabled) "Active (${lockSettings.primaryMethod.label})" else "Disabled — tap to secure chats",
                        style = MaterialTheme.typography.bodySmall,
                        color = ArEmeraldBright
                    )
                }
            }
        }

        VisibilitySelector("Last Seen & Online", lastSeen) { lastSeen = it }
        VisibilitySelector("Profile Photo", photoVis) { photoVis = it }
        VisibilitySelector("About Info", aboutVis) { aboutVis = it }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Read Receipts", style = MaterialTheme.typography.titleSmall)
                Text("Show blue double-ticks when messages are opened", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = readReceipts, onCheckedChange = { readReceipts = it })
        }

        Column {
            Text("Default Disappearing Message Timer", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0L to "Off", 86400L to "24h", 604800L to "7d", 7776000L to "90d").forEach { (sec, label) ->
                    FilterChip(
                        selected = disappearing == sec,
                        onClick = { disappearing = sec },
                        label = { Text(label) }
                    )
                }
            }
        }

        Button(
            onClick = { viewModel.updatePrivacy(lastSeen, photoVis, aboutVis, readReceipts, disappearing) },
            colors = ButtonDefaults.buttonColors(containerColor = ArEmeraldPrimary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Privacy Preferences", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VisibilitySelector(label: String, selected: String, onSelected: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("everyone" to "Everyone", "contacts" to "Contacts", "nobody" to "Nobody").forEach { (key, title) ->
                FilterChip(selected = selected == key, onClick = { onSelected(key) }, label = { Text(title) })
            }
        }
    }
}

@Composable
private fun ChatsAndBackupsSettingsView(
    viewModel: MainViewModel,
    prefsManager: PreferencesManager,
    uiPrefs: UserUiPreferences
) {
    val scope = rememberCoroutineScope()
    val backupsState by viewModel.backupsState.collectAsStateWithLifecycle()
    val backups = (backupsState as? UiState.Success)?.data.orEmpty()
    var showCreateBackup by remember { mutableStateOf(false) }
    var verifyBackupTarget by remember { mutableStateOf<EncryptedBackup?>(null) }
    var verifyOutput by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Enter Key Sends Message", style = MaterialTheme.typography.titleSmall)
                Text("Quick-send on keyboard IME action", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = uiPrefs.enterSendsMessage,
                onCheckedChange = { enabled -> scope.launch { prefsManager.setEnterSends(enabled) } }
            )
        }

        HorizontalDivider()

        Text("PBKDF2 End-to-End Encrypted Backups", style = MaterialTheme.typography.titleSmall, color = ArEmeraldBright)
        Text(
            "Backups are encrypted on-device with PBKDF2-HMAC-SHA256 (100k iterations) + AES-256-GCM before uploading to your private cloud vault.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = { showCreateBackup = true },
            colors = ButtonDefaults.buttonColors(containerColor = ArEmeraldPrimary),
            modifier = Modifier.fillMaxWidth().testTag("create_backup_btn")
        ) {
            Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create Encrypted Backup")
        }

        verifyOutput?.let {
            Card(colors = CardDefaults.cardColors(containerColor = ArEmeraldDeep)) {
                Text(it, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = ArEmeraldBright)
            }
        }

        backups.forEach { backup ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(backup.label, style = MaterialTheme.typography.titleSmall)
                        Text("${backup.kdfIterations} PBKDF2 iterations • ${formatTimestampShort(backup.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { verifyBackupTarget = backup }) {
                        Text("Decrypt Check", color = ArEmeraldBright)
                    }
                }
            }
        }
    }

    if (showCreateBackup) {
        var label by remember { mutableStateOf("A.R Chat Vault Backup") }
        var passphrase by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showCreateBackup = false },
            title = { Text("Encrypt & Upload Backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Backup Label") }, singleLine = true)
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Passphrase (6+ chars)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (passphrase.length < 6) {
                        error = "Passphrase must be at least 6 characters."
                    } else {
                        viewModel.createEncryptedBackup(label, passphrase)
                        showCreateBackup = false
                    }
                }) { Text("Encrypt & Upload") }
            },
            dismissButton = { TextButton(onClick = { showCreateBackup = false }) { Text("Cancel") } }
        )
    }

    verifyBackupTarget?.let { backup ->
        var passphrase by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { verifyBackupTarget = null },
            title = { Text("Decrypt ${backup.label}") },
            text = {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Enter Backup Passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.decryptBackup(backup, passphrase) { res ->
                        verifyOutput = res.fold(
                            onSuccess = { json -> "Verified & Decrypted: $json" },
                            onFailure = { "Decryption failed: Invalid passphrase or corrupted ciphertext." }
                        )
                    }
                    verifyBackupTarget = null
                }) { Text("Decrypt") }
            },
            dismissButton = { TextButton(onClick = { verifyBackupTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AppearanceSettingsView(
    viewModel: MainViewModel,
    prefsManager: PreferencesManager,
    uiPrefs: UserUiPreferences
) {
    val scope = rememberCoroutineScope()
    var scale by remember(uiPrefs.fontScale) { mutableFloatStateOf(uiPrefs.fontScale) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Theme Mode", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("dark" to "Dark", "light" to "Light", "system" to "System").forEach { (key, label) ->
                FilterChip(
                    selected = uiPrefs.themeMode == key,
                    onClick = {
                        scope.launch { prefsManager.setAppearance(themeMode = key) }
                        viewModel.updateLanguageAndTheme(uiPrefs.languageCode, key)
                    },
                    label = { Text(label) }
                )
            }
        }

        Text("Accent Palette", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("emerald" to "Emerald", "teal" to "Teal", "cobalt" to "Cobalt", "amber" to "Amber").forEach { (key, label) ->
                FilterChip(
                    selected = uiPrefs.accentColorKey == key,
                    onClick = { scope.launch { prefsManager.setAppearance(accentKey = key) } },
                    label = { Text(label) }
                )
            }
        }

        Text("Typography Scale (${String.format("%.2fx", scale)})", style = MaterialTheme.typography.titleSmall)
        Slider(
            value = scale,
            onValueChange = { scale = it },
            onValueChangeFinished = { scope.launch { prefsManager.setAppearance(fontScale = scale) } },
            valueRange = 0.85f..1.35f
        )
    }
}

@Composable
private fun NotificationsSettingsView(prefsManager: PreferencesManager, uiPrefs: UserUiPreferences) {
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PrefToggleRow("Direct Message Alerts", uiPrefs.messageNotifications) {
            scope.launch { prefsManager.setNotificationPrefs(messages = it) }
        }
        PrefToggleRow("Group & Community Alerts", uiPrefs.groupNotifications) {
            scope.launch { prefsManager.setNotificationPrefs(groups = it) }
        }
        PrefToggleRow("Incoming Call Ringtone", uiPrefs.callNotifications) {
            scope.launch { prefsManager.setNotificationPrefs(calls = it) }
        }
        PrefToggleRow("Vibration Feedback", uiPrefs.vibrationEnabled) {
            scope.launch { prefsManager.setNotificationPrefs(vibration = it) }
        }
    }
}

@Composable
private fun StorageSettingsView(prefsManager: PreferencesManager, uiPrefs: UserUiPreferences) {
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PrefToggleRow("Auto-Download Media on Wi-Fi", uiPrefs.wifiAutoDownload) {
            scope.launch { prefsManager.setStoragePrefs(wifiAuto = it) }
        }
        PrefToggleRow("Auto-Download on Mobile Data", uiPrefs.mobileAutoDownload) {
            scope.launch { prefsManager.setStoragePrefs(mobileAuto = it) }
        }
        PrefToggleRow("Low Data Usage for Calls", uiPrefs.dataSaverMode) {
            scope.launch { prefsManager.setStoragePrefs(dataSaver = it) }
        }
        PrefToggleRow("Send HD Compressed Media by Default", uiPrefs.hdMediaDefault) {
            scope.launch { prefsManager.setStoragePrefs(hdDefault = it) }
        }
    }
}

@Composable
private fun AccessibilitySettingsView(prefsManager: PreferencesManager, uiPrefs: UserUiPreferences) {
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PrefToggleRow("High Contrast Mode", uiPrefs.highContrast) {
            scope.launch { prefsManager.setAppearance(highContrast = it) }
        }
        PrefToggleRow("Reduced Motion Animations", uiPrefs.reducedMotion) {
            scope.launch { prefsManager.setAppearance(reducedMotion = it) }
        }
    }
}

@Composable
private fun LanguageSettingsView(
    viewModel: MainViewModel,
    prefsManager: PreferencesManager,
    uiPrefs: UserUiPreferences
) {
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Choose App Language", style = MaterialTheme.typography.titleSmall)
        listOf("en" to "English (Default)", "ur" to "اردو (Urdu)").forEach { (code, title) ->
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiPrefs.languageCode == code) ArEmeraldDeep else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth().clickable {
                    scope.launch { prefsManager.setLanguage(code) }
                    viewModel.updateLanguageAndTheme(code, uiPrefs.themeMode)
                }
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (uiPrefs.languageCode == code) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Selected", tint = ArEmeraldBright)
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpAndSupportView(viewModel: MainViewModel) {
    val reportsState by viewModel.myReportsState.collectAsStateWithLifecycle()
    val tickets = (reportsState as? UiState.Success)?.data.orEmpty()
    var ticketText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A.R Chat Support & Bug Reports", style = MaterialTheme.typography.titleSmall, color = ArEmeraldBright)
                    OutlinedTextField(
                        value = ticketText,
                        onValueChange = { ticketText = it },
                        label = { Text("Describe your issue or feedback") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (ticketText.trim().length >= 3) {
                                viewModel.submitReportOrSupport("support", "app_support", ticketText.trim())
                                ticketText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ArEmeraldPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Submit Support Ticket") }
                }
            }
        }
        item {
            Text("A.R Chat v1.0.0 • ECDH P-256 + AES-256-GCM • Pakistan +92 Edition", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (tickets.isNotEmpty()) {
            item { Text("Your Submitted Tickets", style = MaterialTheme.typography.titleSmall) }
            items(tickets, key = { it.id }) { ticket ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("${ticket.targetType.uppercase()} • ${ticket.status.uppercase()}", style = MaterialTheme.typography.labelSmall, color = ArEmeraldBright)
                        Text(ticket.reason, style = MaterialTheme.typography.bodyMedium)
                        if (ticket.resolutionNote.isNotBlank()) {
                            Text("Resolution: ${ticket.resolutionNote}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrefToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockConfigScreen(
    lockManager: AppLockManager,
    settings: AppLockSettings,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val scope = rememberCoroutineScope()
    val capability = remember { lockManager.checkBiometricCapability() }
    var selectedMethod by remember(settings.primaryMethod) { mutableStateOf(settings.primaryMethod) }
    var enableBiometric by remember(settings.biometricEnabled) { mutableStateOf(settings.biometricEnabled) }
    var secretInput by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Lock & Biometrics") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("applock_config_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(capability.title, style = MaterialTheme.typography.titleSmall, color = ArEmeraldBright)
                    Text(capability.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (capability.class3Supported) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Enable Class 3 Biometric Unlock", style = MaterialTheme.typography.bodyMedium)
                            Switch(
                                checked = enableBiometric,
                                onCheckedChange = {
                                    enableBiometric = it
                                    if (settings.lockEnabled) {
                                        scope.launch { lockManager.setBiometricEnabled(it) }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Text("Credential Fallback Method", style = MaterialTheme.typography.titleSmall)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(LockMethod.PIN_4, LockMethod.PIN_6, LockMethod.PATTERN, LockMethod.PASSWORD).forEach { method ->
                    FilterChip(
                        selected = selectedMethod == method,
                        onClick = { selectedMethod = method; secretInput = "" },
                        label = { Text(method.label.substringBefore(" "), style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            when (selectedMethod) {
                LockMethod.PATTERN -> {
                    Text("Draw a pattern (4+ nodes) to configure:", style = MaterialTheme.typography.bodySmall)
                    PatternLockGrid(onPatternCompleted = { pattern ->
                        scope.launch {
                            lockManager.configureCredentialLock(LockMethod.PATTERN, pattern, enableBiometric)
                            statusMsg = "Pattern App Lock activated."
                        }
                    })
                }
                else -> {
                    val isPin = selectedMethod == LockMethod.PIN_4 || selectedMethod == LockMethod.PIN_6
                    val targetLen = if (selectedMethod == LockMethod.PIN_6) 6 else 4
                    OutlinedTextField(
                        value = secretInput,
                        onValueChange = { input ->
                            secretInput = if (isPin) input.filter { it.isDigit() }.take(targetLen) else input
                        },
                        label = { Text(if (isPin) "Set $targetLen-Digit PIN" else "Set Password (6+ chars)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = if (isPin) KeyboardType.NumberPassword else KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("applock_secret_input")
                    )
                    Button(
                        onClick = {
                            val valid = when (selectedMethod) {
                                LockMethod.PIN_4 -> secretInput.length == 4
                                LockMethod.PIN_6 -> secretInput.length == 6
                                LockMethod.PASSWORD -> secretInput.length >= 6
                                else -> false
                            }
                            if (!valid) {
                                statusMsg = "Please satisfy the length requirement for ${selectedMethod.label}."
                            } else {
                                scope.launch {
                                    lockManager.configureCredentialLock(selectedMethod, secretInput, enableBiometric)
                                    secretInput = ""
                                    statusMsg = "${selectedMethod.label} App Lock saved."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ArEmeraldPrimary),
                        modifier = Modifier.fillMaxWidth().testTag("save_applock_btn")
                    ) {
                        Text("Save & Enable App Lock", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Text("Auto-Lock Timeout", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to "Immediate", 1 to "1m", 5 to "5m", 15 to "15m", 30 to "30m").forEach { (min, label) ->
                    FilterChip(
                        selected = settings.autoLockMinutes == min,
                        onClick = { scope.launch { lockManager.setAutoLockMinutes(min) } },
                        label = { Text(label) }
                    )
                }
            }

            PrefToggleRow("Hide Message Previews on Lock Screen", settings.hideMessagePreviews) {
                scope.launch { lockManager.setPrivacyFlags(it, settings.secureAppSwitcher) }
            }
            PrefToggleRow("Secure App Switcher (FLAG_SECURE)", settings.secureAppSwitcher) {
                scope.launch { lockManager.setPrivacyFlags(settings.hideMessagePreviews, it) }
            }

            if (settings.lockEnabled) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            lockManager.disableLock()
                            statusMsg = "App Lock disabled."
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ArDangerRed),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Disable App Lock") }
            }

            statusMsg?.let { Text(it, color = ArEmeraldBright, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkedDevicesScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val devicesState by viewModel.linkedDevicesState.collectAsStateWithLifecycle()
    val devices = (devicesState as? UiState.Success)?.data.orEmpty()
    var showLinkDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Linked Devices") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Button(
                    onClick = { showLinkDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = ArEmeraldPrimary),
                    modifier = Modifier.fillMaxWidth().testTag("link_device_btn")
                ) {
                    Icon(Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Link a Companion Device")
                }
            }
            items(devices, key = { it.id }) { dev ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Devices, contentDescription = null, tint = ArEmeraldBright)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(dev.deviceName, style = MaterialTheme.typography.titleSmall)
                            Text("${dev.platform} • ${formatTimestampShort(dev.lastActiveAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { viewModel.revokeLinkedDevice(dev.id) }) {
                            Text("Revoke", color = ArDangerRed)
                        }
                    }
                }
            }
        }
    }

    if (showLinkDialog) {
        var devName by remember { mutableStateOf("A.R Chat Web / Desktop") }
        var platform by remember { mutableStateOf("Web Browser") }
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text("Authorize Device Session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = devName, onValueChange = { devName = it }, label = { Text("Device Name") }, singleLine = true)
                    OutlinedTextField(value = platform, onValueChange = { platform = it }, label = { Text("Platform") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (devName.isNotBlank()) {
                        viewModel.linkNewDevice(devName, platform)
                        showLinkDialog = false
                    }
                }) { Text("Authorize") }
            },
            dismissButton = { TextButton(onClick = { showLinkDialog = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredMessagesScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    BackHandler { onBack() }
    LaunchedEffect(Unit) { viewModel.refreshStarredMessages() }

    val starredState by viewModel.starredMessagesState.collectAsStateWithLifecycle()
    val directoryState by viewModel.activeDirectoryState.collectAsStateWithLifecycle()
    val directory = (directoryState as? UiState.Success)?.data.orEmpty()
    val profileById = remember(directory) { directory.associateBy { it.userId } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Starred Messages") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        when (val st = starredState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ArEmeraldPrimary)
            }
            is UiState.Error -> EmptyStateCard(Icons.Default.Star, "Could not load starred messages", st.message, Modifier.padding(padding))
            is UiState.Success -> {
                if (st.data.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        EmptyStateCard(Icons.Default.Star, "No Starred Messages", "Long-press any message in a chat and tap Star to save it here.")
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(st.data, key = { it.second.id }) { (chat, msg) ->
                            val peerUid = chat.memberUids.firstOrNull { it != viewModel.currentUserId } ?: viewModel.currentUserId
                            val peerKey = profileById[peerUid]?.ecdhPublicKey
                            val text = viewModel.decryptMessageText(msg, peerKey)
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth().clickable { onOpenChat(chat.id) }
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${msg.senderName} • ${chat.title}", style = MaterialTheme.typography.labelSmall, color = ArEmeraldBright)
                                        Text(formatTimestampShort(msg.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(text, style = MaterialTheme.typography.bodyMedium)
                                    TextButton(
                                        onClick = {
                                            viewModel.toggleStarMessage(chat.id, msg)
                                            viewModel.refreshStarredMessages()
                                        },
                                        contentPadding = PaddingValues(0.dp)
                                    ) { Text("Unstar", style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
