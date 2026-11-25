package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SupervisedUserCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.PakistanPhoneValidator
import com.example.core.UiState
import com.example.data.model.ReportTicket
import com.example.data.repository.AuthRepository
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.formatTimestampShort
import com.example.ui.theme.ArDangerRed
import com.example.ui.theme.ArEmeraldBright
import com.example.ui.theme.ArEmeraldDeep
import com.example.ui.theme.ArEmeraldPrimary
import com.example.ui.theme.ArWarningAmber
import com.example.ui.viewmodel.OwnerViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerPortalScreen(
    ownerViewModel: OwnerViewModel,
    authRepository: AuthRepository,
    currentAuthUid: String?,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val ownerVerified by ownerViewModel.ownerVerified.collectAsStateWithLifecycle()
    val ownerConfigLoaded by ownerViewModel.ownerConfigLoaded.collectAsStateWithLifecycle()
    val ownerSecurityConfig by ownerViewModel.ownerSecurityConfig.collectAsStateWithLifecycle()
    val authMessage by ownerViewModel.authMessage.collectAsStateWithLifecycle()

    LaunchedEffect(currentAuthUid) {
        if (!currentAuthUid.isNullOrBlank()) {
            ownerViewModel.loadOwnerSecurityConfig()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = ArEmeraldBright)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Owner Portal", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("owner_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!currentAuthUid.isNullOrBlank()) {
                        IconButton(onClick = { ownerViewModel.loadOwnerSecurityConfig() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh authorization")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            if (currentAuthUid.isNullOrBlank()) {
                OwnerGoogleSignInPrompt(
                    onSignInClick = {
                        authRepository.onGoogleSignInClicked(
                            context = context,
                            credentialManager = credentialManager,
                            onAuthSuccess = { ownerViewModel.loadOwnerSecurityConfig() },
                            onAuthError = { /* reflected via auth state */ },
                            scope = scope
                        )
                    }
                )
            } else if (!ownerConfigLoaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ArEmeraldPrimary)
                }
            } else if (!ownerVerified) {
                OwnerTwoFactorGate(
                    signedInEmail = Firebase.auth.currentUser?.email ?: "Unknown",
                    existingConfig = ownerSecurityConfig,
                    authMessage = authMessage,
                    onProvision = { phone, pin -> ownerViewModel.provisionOwnerCredentials(phone, pin) },
                    onVerify = { phone, pin -> ownerViewModel.verifyOwnerLogin(phone, pin) },
                    onSwitchAccount = {
                        authRepository.signOut(credentialManager, scope) {}
                    }
                )
            } else {
                OwnerDashboardContent(
                    ownerViewModel = ownerViewModel,
                    authMessage = authMessage
                )
            }
        }
    }
}

@Composable
private fun OwnerGoogleSignInPrompt(onSignInClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .widthIn(max = 480.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Security, contentDescription = null, tint = ArEmeraldBright, modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("A.R Chat Owner Authentication", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Sign in with the verified Application Owner Google Account to unlock administrative controls. Access is strictly enforced server-side by Firestore Security Rules.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onSignInClick,
            colors = ButtonDefaults.buttonColors(containerColor = ArEmeraldPrimary),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("owner_google_signin_btn")
        ) {
            Text("Verify Owner via Google Sign-In", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OwnerTwoFactorGate(
    signedInEmail: String,
    existingConfig: com.example.data.model.OwnerSecurityConfig?,
    authMessage: String?,
    onProvision: (String, String) -> Unit,
    onVerify: (String, String) -> Unit,
    onSwitchAccount: () -> Unit
) {
    var phoneInput by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val isSetupMode = existingConfig == null && authMessage?.contains("permission", ignoreCase = true) != true &&
        authMessage?.contains("Unauthorized", ignoreCase = true) != true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .widthIn(max = 480.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Signed-in Identity", style = MaterialTheme.typography.labelSmall, color = ArEmeraldBright)
                Text(signedInEmail, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (existingConfig != null) "Step 2: Verify your Owner Mobile Number & PBKDF2 2FA PIN."
                    else if (isSetupMode) "First-Time Owner Setup: Register your +92 Owner Mobile & 2FA PIN."
                    else "Server rules rejected access to /app_config/owner_security for this account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (existingConfig != null || isSetupMode) {
            OutlinedTextField(
                value = phoneInput,
                onValueChange = { phoneInput = it },
                label = { Text("Owner Mobile (+923001234567 or 03001234567)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("owner_phone_input")
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = pinInput,
                onValueChange = { pinInput = it },
                label = { Text(if (isSetupMode) "Create Owner 2FA PIN (6+ chars)" else "Enter Owner 2FA PIN") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("owner_pin_input")
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val normalized = if (phoneInput.startsWith("+")) phoneInput.trim()
                    else PakistanPhoneValidator.formatE164("+92", phoneInput)
                    if (normalized.length < 10) {
                        localError = "Enter a valid mobile number."
                        return@Button
                    }
                    if (pinInput.length < 6) {
                        localError = "Owner 2FA PIN must be at least 6 characters."
                        return@Button
                    }
                    localError = null
                    if (isSetupMode) onProvision(normalized, pinInput)
                    else onVerify(normalized, pinInput)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ArEmeraldPrimary),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("owner_verify_btn")
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isSetupMode) "Provision Owner Security & Enter" else "Unlock Owner Dashboard", fontWeight = FontWeight.Bold)
            }
        }

        (localError ?: authMessage)?.let { msg ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(20.dp))
        OutlinedButton(onClick = onSwitchAccount, modifier = Modifier.fillMaxWidth()) {
            Text("Switch Google Account")
        }
    }
}

@Composable
private fun OwnerDashboardContent(
    ownerViewModel: OwnerViewModel,
    authMessage: String?
) {
    var selectedSection by remember { mutableIntStateOf(0) }
    val usersState by ownerViewModel.usersPrivateState.collectAsStateWithLifecycle()
    val profilesState by ownerViewModel.publicProfilesState.collectAsStateWithLifecycle()
    val reportsState by ownerViewModel.reportsState.collectAsStateWithLifecycle()
    val chatsState by ownerViewModel.chatsState.collectAsStateWithLifecycle()
    val channelsState by ownerViewModel.channelsState.collectAsStateWithLifecycle()
    val communitiesState by ownerViewModel.communitiesState.collectAsStateWithLifecycle()
    val adminsState by ownerViewModel.adminsState.collectAsStateWithLifecycle()
    val logsState by ownerViewModel.adminLogsState.collectAsStateWithLifecycle()
    val publicConfigState by ownerViewModel.publicConfigState.collectAsStateWithLifecycle()

    val users = (usersState as? UiState.Success)?.data.orEmpty()
    val profiles = (profilesState as? UiState.Success)?.data.orEmpty()
    val reports = (reportsState as? UiState.Success)?.data.orEmpty()
    val chats = (chatsState as? UiState.Success)?.data.orEmpty()
    val channels = (channelsState as? UiState.Success)?.data.orEmpty()
    val communities = (communitiesState as? UiState.Success)?.data.orEmpty()
    val admins = (adminsState as? UiState.Success)?.data.orEmpty()
    val logs = (logsState as? UiState.Success)?.data.orEmpty()
    val publicConfig = (publicConfigState as? UiState.Success)?.data

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Overview", "Users (${users.size})", "Reports (${reports.count { it.status == "open" }})", "Moderation", "Admins & Logs").forEachIndexed { idx, label ->
                FilterChip(
                    selected = selectedSection == idx,
                    onClick = { selectedSection = idx },
                    label = { Text(label) }
                )
            }
        }

        authMessage?.let { msg ->
            Surface(color = ArEmeraldDeep, modifier = Modifier.fillMaxWidth()) {
                Text(msg, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = ArEmeraldBright, style = MaterialTheme.typography.bodySmall)
            }
        }

        when (selectedSection) {
            0 -> {
                var maintenance by remember(publicConfig?.maintenanceMode) { mutableStateOf(publicConfig?.maintenanceMode ?: false) }
                var maintMsg by remember(publicConfig?.maintenanceMessage) { mutableStateOf(publicConfig?.maintenanceMessage ?: "") }
                var latestVer by remember(publicConfig?.latestVersion) { mutableStateOf(publicConfig?.latestVersion ?: "1.0.0") }
                var announcement by remember(publicConfig?.globalAnnouncement) { mutableStateOf(publicConfig?.globalAnnouncement ?: "") }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("Users", "${users.size}", Modifier.weight(1f))
                            StatTile("Active", "${profiles.count { it.accountStatus == "active" }}", Modifier.weight(1f))
                            StatTile("Chats", "${chats.size}", Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("Open Reports", "${reports.count { it.status == "open" }}", Modifier.weight(1f))
                            StatTile("Channels", "${channels.size}", Modifier.weight(1f))
                            StatTile("Communities", "${communities.size}", Modifier.weight(1f))
                        }
                    }
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Live Platform Controls", style = MaterialTheme.typography.titleMedium, color = ArEmeraldBright)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Maintenance Mode")
                                    Switch(checked = maintenance, onCheckedChange = { maintenance = it })
                                }
                                OutlinedTextField(value = maintMsg, onValueChange = { maintMsg = it }, label = { Text("Maintenance Banner Message") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = announcement, onValueChange = { announcement = it }, label = { Text("Global Announcement Banner") }, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = latestVer, onValueChange = { latestVer = it }, label = { Text("Latest Version") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Button(
                                    onClick = { ownerViewModel.updatePublicConfig(maintenance, maintMsg, latestVer, announcement) },
                                    colors = ButtonDefaults.buttonColors(containerColor = ArEmeraldPrimary),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Publish Platform Config", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                var query by remember { mutableStateOf("") }
                val filtered = users.filter {
                    query.isBlank() || it.displayName.contains(query, true) || it.phoneNumber.contains(query, true) || it.username.contains(query, true)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Search users by name, @username, or +92 number") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    items(filtered, key = { it.userId }) { user ->
                        val suspended = user.accountStatus == "suspended"
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${user.displayName} (@${user.username})", style = MaterialTheme.typography.titleSmall)
                                    Text("${user.phoneNumber} • ${user.carrierName}", style = MaterialTheme.typography.bodySmall, color = ArEmeraldBright)
                                    Text("Status: ${user.accountStatus.uppercase()} • UID: ${user.userId.take(12)}...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = {
                                    ownerViewModel.setUserAccountStatus(user.userId, if (suspended) "active" else "suspended")
                                }) {
                                    Text(if (suspended) "Reactivate" else "Suspend", color = if (suspended) ArEmeraldBright else ArDangerRed)
                                }
                            }
                        }
                    }
                }
            }
            2 -> {
                var selectedReport by remember { mutableStateOf<ReportTicket?>(null) }
                if (reports.isEmpty()) {
                    EmptyStateCard(Icons.Default.Report, "No Reports or Tickets", "User abuse reports and support tickets appear here.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(reports, key = { it.id }) { report ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${report.targetType.uppercase()} • ${report.status.uppercase()}", style = MaterialTheme.typography.labelSmall, color = if (report.status == "open") ArWarningAmber else ArEmeraldBright)
                                        Text(formatTimestampShort(report.createdAt), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(report.reason, style = MaterialTheme.typography.bodyMedium)
                                    if (report.status == "open") {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(onClick = { selectedReport = report }) { Text("Resolve / Dismiss") }
                                        }
                                    } else if (report.resolutionNote.isNotBlank()) {
                                        Text("Note: ${report.resolutionNote}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
                selectedReport?.let { ticket ->
                    var note by remember { mutableStateOf("Reviewed by A.R Chat Owner") }
                    AlertDialog(
                        onDismissRequest = { selectedReport = null },
                        title = { Text("Update Ticket Status") },
                        text = {
                            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Resolution Note") })
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                ownerViewModel.resolveReport(ticket.id, "resolved", note)
                                selectedReport = null
                            }) { Text("Mark Resolved") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                ownerViewModel.resolveReport(ticket.id, "dismissed", note)
                                selectedReport = null
                            }) { Text("Dismiss") }
                        }
                    )
                }
            }
            3 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { Text("Channels Moderation (${channels.size})", style = MaterialTheme.typography.titleSmall, color = ArEmeraldBright) }
                    items(channels, key = { it.id }) { ch ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(ch.name, style = MaterialTheme.typography.titleSmall)
                                    Text("${ch.followerCount} followers • ${if (ch.active) "Active" else "Disabled"}", style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { ownerViewModel.toggleChannelActive(ch.id, !ch.active) }) {
                                    Text(if (ch.active) "Disable" else "Enable", color = if (ch.active) ArDangerRed else ArEmeraldBright)
                                }
                            }
                        }
                    }
                    item { Text("Active Communities (${communities.size}) & Chats (${chats.size})", style = MaterialTheme.typography.titleSmall, color = ArEmeraldBright) }
                    items(communities, key = { it.id }) { comm ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(comm.name, style = MaterialTheme.typography.titleSmall)
                                Text("${comm.memberUids.size} members • ${comm.linkedChatIds.size} linked chats", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            else -> {
                var newAdminUid by remember { mutableStateOf("") }
                var newAdminEmail by remember { mutableStateOf("") }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Add Authorized Admin", style = MaterialTheme.typography.titleSmall, color = ArEmeraldBright)
                                OutlinedTextField(value = newAdminUid, onValueChange = { newAdminUid = it }, label = { Text("Firebase User UID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedTextField(value = newAdminEmail, onValueChange = { newAdminEmail = it }, label = { Text("Admin Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Button(
                                    onClick = {
                                        if (newAdminUid.isNotBlank() && newAdminEmail.isNotBlank()) {
                                            ownerViewModel.addAdmin(newAdminUid, newAdminEmail)
                                            newAdminUid = ""
                                            newAdminEmail = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ArEmeraldPrimary),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Grant Admin Access")
                                }
                            }
                        }
                    }
                    items(admins, key = { it.id }) { admin ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(admin.email, style = MaterialTheme.typography.titleSmall)
                                    Text("UID: ${admin.uid}", style = MaterialTheme.typography.labelSmall)
                                }
                                IconButton(onClick = { ownerViewModel.removeAdmin(admin.uid) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove admin", tint = ArDangerRed)
                                }
                            }
                        }
                    }
                    item { Text("Admin Audit Logs (${logs.size})", style = MaterialTheme.typography.titleSmall, color = ArEmeraldBright) }
                    items(logs, key = { it.id }) { log ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("${log.action} • ${formatTimestampShort(log.createdAt)}", style = MaterialTheme.typography.labelSmall, color = ArEmeraldBright)
                                Text("${log.details} (${log.actorEmail})", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ArEmeraldBright)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
