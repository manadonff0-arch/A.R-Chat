package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.UiState
import com.example.core.VoiceAudioHelper
import com.example.data.model.CallSession
import com.example.data.model.Chat
import com.example.data.model.PublicProfile
import com.example.ui.components.ArAvatar
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.formatTimestampShort
import com.example.ui.theme.ArBackgroundDark
import com.example.ui.theme.ArDangerRed
import com.example.ui.theme.ArEmeraldBright
import com.example.ui.theme.ArEmeraldDeep
import com.example.ui.theme.ArEmeraldPrimary
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@Composable
fun CommunitiesTab(
    viewModel: MainViewModel,
    directory: List<PublicProfile>,
    chats: List<Chat>,
    showCreateDialogExternal: Boolean,
    onDismissExternalDialog: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val communitiesState by viewModel.communitiesState.collectAsStateWithLifecycle()
    val communities = (communitiesState as? UiState.Success)?.data.orEmpty()
    var showCreateInternal by remember { mutableStateOf(false) }

    val showDialog = showCreateDialogExternal || showCreateInternal

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Image(
                        painter = painterResource(id = R.drawable.img_community_banner),
                        contentDescription = "Communities Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCreateInternal = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(ArEmeraldPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("New Community", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Bring related groups and announcements together", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (communities.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.Groups,
                    title = "Stay connected with Communities",
                    subtitle = "Create a community to organize neighborhood, university, or family groups with an automatic announcement hub."
                )
            }
        } else {
            items(communities, key = { it.id }) { comm ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(ArEmeraldDeep),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.Groups, contentDescription = null, tint = ArEmeraldBright) }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(comm.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("${comm.memberUids.size} members • ${comm.linkedChatIds.size} linked groups", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (comm.description.isNotBlank()) {
                            Text(comm.description, style = MaterialTheme.typography.bodySmall)
                        }
                        comm.linkedChatIds.forEach { linkedId ->
                            val linkedChat = chats.firstOrNull { it.id == linkedId }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onOpenChat(linkedId) }
                                    .padding(vertical = 6.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Campaign, contentDescription = null, tint = ArEmeraldBright, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(linkedChat?.title ?: "Announcements", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        val selectedMembers = remember { mutableStateListOf<String>() }
        val peers = directory.filter { it.userId != viewModel.currentUserId }
        AlertDialog(
            onDismissRequest = { showCreateInternal = false; onDismissExternalDialog() },
            title = { Text("Create Community") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Community Name") }, singleLine = true)
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, maxLines = 2)
                    Text("Invite Members (${selectedMembers.size})", style = MaterialTheme.typography.labelMedium)
                    LazyColumn(modifier = Modifier.height(140.dp)) {
                        items(peers, key = { it.userId }) { peer ->
                            val checked = peer.userId in selectedMembers
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    if (checked) selectedMembers.remove(peer.userId) else selectedMembers.add(peer.userId)
                                },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = checked, onCheckedChange = {
                                    if (it) selectedMembers.add(peer.userId) else selectedMembers.remove(peer.userId)
                                })
                                Text(peer.displayName)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        viewModel.createCommunity(name, desc, selectedMembers.toList(), emptyList())
                        showCreateInternal = false
                        onDismissExternalDialog()
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateInternal = false; onDismissExternalDialog() }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun CallsTab(
    viewModel: MainViewModel,
    myDisplayName: String,
    directory: List<PublicProfile>,
    onStartCallOverlay: (CallSession) -> Unit
) {
    val callsState by viewModel.callsState.collectAsStateWithLifecycle()
    val calls = (callsState as? UiState.Success)?.data.orEmpty()
    var showNewCallDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Button(
                onClick = { showNewCallDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = ArEmeraldPrimary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("start_new_call_btn")
            ) {
                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Voice or Video Call", fontWeight = FontWeight.Bold)
            }
        }

        if (calls.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.Call,
                    title = "No Call History Yet",
                    subtitle = "Place end-to-end signaled voice or video calls to registered A.R Chat accounts."
                )
            }
        } else {
            items(calls, key = { it.id }) { call ->
                val outgoing = call.callerUid == viewModel.currentUserId
                val peerName = if (outgoing) call.calleeName else call.callerName
                val isIncomingRinging = !outgoing && call.callState == "ringing"

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArAvatar(displayName = peerName, size = 44.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(peerName, style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (outgoing) Icons.AutoMirrored.Filled.CallMade else Icons.AutoMirrored.Filled.CallReceived,
                                    contentDescription = null,
                                    tint = if (call.callState == "missed" || call.callState == "declined") ArDangerRed else ArEmeraldBright,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${call.callState} • ${call.durationSeconds}s • ${formatTimestampShort(call.createdAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (isIncomingRinging) {
                            TextButton(onClick = {
                                viewModel.updateCallState(call.id, "connected", 0L)
                                onStartCallOverlay(call.copy(callState = "connected"))
                            }) { Text("Answer", color = ArEmeraldBright) }
                        } else {
                            IconButton(onClick = {
                                val targetUid = if (outgoing) call.calleeUid else call.callerUid
                                viewModel.startCall(myDisplayName, targetUid, peerName, call.participantUids, call.callType) { callId ->
                                    onStartCallOverlay(
                                        call.copy(
                                            id = callId,
                                            callerUid = viewModel.currentUserId,
                                            callerName = myDisplayName,
                                            callState = "ringing"
                                        )
                                    )
                                }
                            }) {
                                Icon(
                                    imageVector = if (call.callType.contains("video")) Icons.Default.Videocam else Icons.Default.Call,
                                    contentDescription = "Call again",
                                    tint = ArEmeraldBright
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewCallDialog) {
        val peers = directory.filter { it.userId != viewModel.currentUserId }
        AlertDialog(
            onDismissRequest = { showNewCallDialog = false },
            title = { Text("Select Contact to Call") },
            text = {
                if (peers.isEmpty()) {
                    Text("No other registered accounts found in directory yet.")
                } else {
                    LazyColumn(modifier = Modifier.height(240.dp)) {
                        items(peers, key = { it.userId }) { peer ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ArAvatar(displayName = peer.displayName, avatarBase64 = peer.avatarData, size = 38.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(peer.displayName, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    showNewCallDialog = false
                                    viewModel.startCall(myDisplayName, peer.userId, peer.displayName, listOf(peer.userId), "voice") { id ->
                                        onStartCallOverlay(
                                            CallSession(
                                                id = id,
                                                callerUid = viewModel.currentUserId,
                                                callerName = myDisplayName,
                                                calleeUid = peer.userId,
                                                calleeName = peer.displayName,
                                                participantUids = listOf(viewModel.currentUserId, peer.userId),
                                                callType = "voice",
                                                callState = "ringing"
                                            )
                                        )
                                    }
                                }) { Icon(Icons.Default.Call, contentDescription = "Voice call", tint = ArEmeraldBright) }
                                IconButton(onClick = {
                                    showNewCallDialog = false
                                    viewModel.startCall(myDisplayName, peer.userId, peer.displayName, listOf(peer.userId), "video") { id ->
                                        onStartCallOverlay(
                                            CallSession(
                                                id = id,
                                                callerUid = viewModel.currentUserId,
                                                callerName = myDisplayName,
                                                calleeUid = peer.userId,
                                                calleeName = peer.displayName,
                                                participantUids = listOf(viewModel.currentUserId, peer.userId),
                                                callType = "video",
                                                callState = "ringing"
                                            )
                                        )
                                    }
                                }) { Icon(Icons.Default.Videocam, contentDescription = "Video call", tint = ArEmeraldBright) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNewCallDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
fun ActiveCallOverlay(
    viewModel: MainViewModel,
    initialSession: CallSession,
    onDismiss: () -> Unit
) {
    BackHandler {
        viewModel.updateCallState(initialSession.id, "ended", 0L)
        onDismiss()
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val voiceHelper = remember(context) { VoiceAudioHelper(context) }

    val callsState by viewModel.callsState.collectAsStateWithLifecycle()
    val liveSession = (callsState as? UiState.Success)?.data?.firstOrNull { it.id == initialSession.id } ?: initialSession

    var micMuted by remember { mutableStateOf(false) }
    var speakerOn by remember { mutableStateOf(initialSession.callType.contains("video")) }
    var cameraEnabled by remember { mutableStateOf(initialSession.callType.contains("video")) }
    var useFrontCamera by remember { mutableStateOf(true) }
    var hasCameraPermission by remember { mutableStateOf(false) }
    var elapsedSec by remember { mutableLongStateOf(0L) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }

    val camPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasCameraPermission = perms[Manifest.permission.CAMERA] == true
    }

    LaunchedEffect(Unit) {
        voiceHelper.setSpeakerphone(speakerOn)
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (initialSession.callType.contains("video")) perms.add(Manifest.permission.CAMERA)
        camPermLauncher.launch(perms.toTypedArray())
    }

    LaunchedEffect(liveSession.callState) {
        if (liveSession.callState == "ended" || liveSession.callState == "declined") {
            delay(800L)
            onDismiss()
        } else if (liveSession.callState == "connected") {
            while (true) {
                delay(1000L)
                elapsedSec++
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceHelper.setSpeakerphone(false)
        }
    }

    val isVideo = initialSession.callType.contains("video")
    val peerName = if (liveSession.callerUid == viewModel.currentUserId) liveSession.calleeName else liveSession.callerName

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ArBackgroundDark
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            if (isVideo && cameraEnabled && hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    },
                    update = { previewView ->
                        val providerFuture = ProcessCameraProvider.getInstance(context)
                        providerFuture.addListener({
                            val cameraProvider = providerFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val selector = CameraSelector.Builder()
                                .requireLensFacing(lensFacing)
                                .build()
                            runCatching {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview)
                            }
                        }, ContextCompat.getMainExecutor(context))
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SignalCellularAlt, contentDescription = null, tint = ArEmeraldBright, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "WebRTC Signaling • ${liveSession.networkQuality.uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ArEmeraldBright
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    if (!isVideo || !cameraEnabled || !hasCameraPermission) {
                        ArAvatar(displayName = peerName, size = 104.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    Text(peerName, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when (liveSession.callState) {
                            "ringing" -> "Signaling peer via Firestore SDP... Waiting for answer"
                            "connected" -> "Connected • %02d:%02d".format(elapsedSec / 60, elapsedSec % 60)
                            else -> liveSession.callState.replaceFirstChar { it.uppercase() }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = ArEmeraldBright
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { micMuted = !micMuted }) {
                        Icon(if (micMuted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = "Mute", tint = Color.White)
                    }
                    IconButton(onClick = {
                        speakerOn = !speakerOn
                        voiceHelper.setSpeakerphone(speakerOn)
                    }) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Speaker", tint = if (speakerOn) ArEmeraldBright else Color.White)
                    }
                    if (isVideo) {
                        IconButton(onClick = { cameraEnabled = !cameraEnabled }) {
                            Icon(if (cameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff, contentDescription = "Camera", tint = Color.White)
                        }
                        IconButton(onClick = {
                            useFrontCamera = !useFrontCamera
                            lensFacing = if (useFrontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                        }) {
                            Icon(Icons.Default.Cameraswitch, contentDescription = "Switch camera", tint = Color.White)
                        }
                    }
                    FilledIconButton(
                        onClick = {
                            viewModel.updateCallState(liveSession.id, "ended", elapsedSec)
                            onDismiss()
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = ArDangerRed),
                        modifier = Modifier.size(56.dp).testTag("end_call_button")
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "End call", tint = Color.White)
                    }
                }
            }
        }
    }
}
