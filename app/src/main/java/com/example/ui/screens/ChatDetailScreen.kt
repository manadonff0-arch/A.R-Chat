package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.MediaPickerHelper
import com.example.core.UiState
import com.example.core.VoiceAudioHelper
import com.example.data.model.CallSession
import com.example.data.model.Chat
import com.example.data.model.ChatMessage
import com.example.data.model.PublicProfile
import com.example.ui.components.ArAvatar
import com.example.ui.components.DeliveryTickIcon
import com.example.ui.components.E2eeInfoPill
import com.example.ui.components.formatTimestampShort
import com.example.ui.theme.ArBubbleIncomingDark
import com.example.ui.theme.ArBubbleOutgoingDark
import com.example.ui.theme.ArEmeraldBright
import com.example.ui.theme.ArEmeraldPrimary
import com.example.ui.theme.ArSurfaceElevatedDark
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatDetailScreen(
    viewModel: MainViewModel,
    chatId: String,
    onBack: () -> Unit,
    onCallStarted: (CallSession) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val voiceHelper = remember(context) { VoiceAudioHelper(context) }
    DisposableEffect(Unit) {
        onDispose {
            voiceHelper.cancelRecording()
            voiceHelper.stopPlayback()
        }
    }

    LaunchedEffect(chatId) { viewModel.selectChat(chatId) }

    val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    val messagesState by viewModel.selectedChatMessagesState.collectAsStateWithLifecycle()
    val directoryState by viewModel.activeDirectoryState.collectAsStateWithLifecycle()
    val myPublicState by viewModel.publicProfileState.collectAsStateWithLifecycle()

    val chat = (chatsState as? UiState.Success)?.data?.firstOrNull { it.id == chatId }
    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    val directory = (directoryState as? UiState.Success)?.data.orEmpty()
    val myProfile = (myPublicState as? UiState.Success)?.data
    val myName = myProfile?.displayName?.ifBlank { "A.R User" } ?: "A.R User"

    val peerUid = chat?.memberUids?.firstOrNull { it != viewModel.currentUserId } ?: viewModel.currentUserId
    val peerProfile = directory.firstOrNull { it.userId == peerUid }
    val peerKey = peerProfile?.ecdhPublicKey?.takeIf { it.isNotBlank() }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            viewModel.markChatRead(chatId, messages)
        }
    }

    var composerText by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var selectedMessageForActions by remember { mutableStateOf<ChatMessage?>(null) }
    var showAttachmentDialog by remember { mutableStateOf(false) }
    var showSafetyDialog by remember { mutableStateOf(false) }
    var showForwardDialog by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var playingMsgId by remember { mutableStateOf<String?>(null) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null && chat != null) {
            val b64 = MediaPickerHelper.uriToCompressedBase64(context, uri, 640, 80_000) ?: ""
            if (b64.isNotBlank()) {
                viewModel.sendMessage(
                    chat = chat,
                    senderName = myName,
                    plaintext = "Photo",
                    messageType = "image",
                    peerPublicKeyBase64 = peerKey,
                    attachmentMeta = "Image (JPEG)",
                    attachmentDataBase64 = b64
                )
            }
        }
    }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && chat != null) {
            val meta = MediaPickerHelper.readDocumentMetadata(context, uri)
            viewModel.sendMessage(
                chat = chat,
                senderName = myName,
                plaintext = meta,
                messageType = "document",
                peerPublicKeyBase64 = peerKey,
                attachmentMeta = meta
            )
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceHelper.startRecording().onSuccess { isRecording = true }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && chat != null) {
            viewModel.sendMessage(
                chat = chat,
                senderName = myName,
                plaintext = "Shared Current Location (Pakistan region)",
                messageType = "location",
                peerPublicKeyBase64 = peerKey,
                attachmentMeta = "Location • Coarse GPS authorized"
            )
        }
    }

    val displayTitle = when {
        chat == null -> "Chat"
        chat.chatType == "direct" && peerProfile != null -> peerProfile.displayName
        else -> chat.title
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    if (searchMode) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search messages...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { showSafetyDialog = true }
                        ) {
                            ArAvatar(
                                displayName = displayTitle,
                                avatarBase64 = peerProfile?.avatarData ?: "",
                                size = 38.dp,
                                showOnlineBadge = chat?.chatType == "direct",
                                isOnline = peerProfile?.online == true
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(displayTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = when {
                                        chat?.chatType == "group" -> "${chat.memberUids.size} members"
                                        chat?.chatType == "broadcast" -> "Broadcast list"
                                        peerProfile?.online == true -> "online • ${peerProfile.carrierName}"
                                        else -> "tap for encryption info"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ArEmeraldBright
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (chat != null && chat.chatType != "broadcast") {
                        IconButton(onClick = {
                            val type = if (chat.chatType == "group") "group_video" else "video"
                            viewModel.startCall(myName, peerUid, displayTitle, chat.memberUids, type) { callId ->
                                onCallStarted(
                                    CallSession(
                                        id = callId,
                                        callerUid = viewModel.currentUserId,
                                        callerName = myName,
                                        calleeUid = peerUid,
                                        calleeName = displayTitle,
                                        participantUids = chat.memberUids,
                                        callType = type,
                                        callState = "ringing"
                                    )
                                )
                            }
                        }, modifier = Modifier.testTag("chat_video_call_btn")) {
                            Icon(Icons.Default.Videocam, contentDescription = "Video call")
                        }
                        IconButton(onClick = {
                            val type = if (chat.chatType == "group") "group_voice" else "voice"
                            viewModel.startCall(myName, peerUid, displayTitle, chat.memberUids, type) { callId ->
                                onCallStarted(
                                    CallSession(
                                        id = callId,
                                        callerUid = viewModel.currentUserId,
                                        callerName = myName,
                                        calleeUid = peerUid,
                                        calleeName = displayTitle,
                                        participantUids = chat.memberUids,
                                        callType = type,
                                        callState = "ringing"
                                    )
                                )
                            }
                        }, modifier = Modifier.testTag("chat_voice_call_btn")) {
                            Icon(Icons.Default.Call, contentDescription = "Voice call")
                        }
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Chat options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Search Messages") }, onClick = { showMenu = false; searchMode = !searchMode })
                            DropdownMenuItem(text = { Text("Verify Safety Number") }, onClick = { showMenu = false; showSafetyDialog = true })
                            if (chat != null && chat.chatType == "group") {
                                DropdownMenuItem(text = { Text("Leave Group") }, onClick = {
                                    showMenu = false
                                    viewModel.leaveChat(chat, onBack)
                                })
                            }
                            DropdownMenuItem(text = { Text("Report Chat / User") }, onClick = {
                                showMenu = false
                                viewModel.submitReportOrSupport("user", peerUid, "Reported from chat $chatId")
                            })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
            ) {
                replyTarget?.let { (_, preview) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ArSurfaceElevatedDark)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Replying to: $preview", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { replyTarget = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel reply", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                editingMessage?.let {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ArSurfaceElevatedDark)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = ArEmeraldBright, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Editing message", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { editingMessage = null; composerText = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel edit", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = composerText,
                        onValueChange = { composerText = it },
                        placeholder = { Text(if (isRecording) "Recording voice note..." else "Message") },
                        enabled = !isRecording,
                        trailingIcon = {
                            IconButton(onClick = { showAttachmentDialog = true }, modifier = Modifier.testTag("attach_button")) {
                                Icon(Icons.Default.AttachFile, contentDescription = "Attach file")
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("message_composer_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (composerText.isNotBlank() && chat != null) {
                        Surface(
                            onClick = {
                                val editTarget = editingMessage
                                if (editTarget != null) {
                                    viewModel.editMessage(chat, editTarget, composerText, peerKey)
                                    editingMessage = null
                                } else {
                                    viewModel.sendMessage(
                                        chat = chat,
                                        senderName = myName,
                                        plaintext = composerText,
                                        messageType = "text",
                                        peerPublicKeyBase64 = peerKey,
                                        replyToMessageId = replyTarget?.first ?: "",
                                        replyToPreview = replyTarget?.second ?: ""
                                    )
                                    replyTarget = null
                                }
                                composerText = ""
                            },
                            shape = CircleShape,
                            color = ArEmeraldPrimary,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("send_message_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color(0xFF042119))
                            }
                        }
                    } else {
                        Surface(
                            onClick = {
                                if (isRecording) {
                                    isRecording = false
                                    val result = voiceHelper.stopRecording()
                                    if (result != null && chat != null) {
                                        val (audioB64, durSec) = result
                                        viewModel.sendMessage(
                                            chat = chat,
                                            senderName = myName,
                                            plaintext = "Voice message (${durSec}s)",
                                            messageType = "voice",
                                            peerPublicKeyBase64 = peerKey,
                                            attachmentMeta = "${durSec}s • AAC",
                                            attachmentDataBase64 = audioB64
                                        )
                                    }
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            shape = CircleShape,
                            color = if (isRecording) MaterialTheme.colorScheme.error else ArEmeraldPrimary,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("voice_record_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                                    contentDescription = if (isRecording) "Stop recording" else "Record voice note",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val e2eeActive = chat?.chatType == "direct" && !peerKey.isNullOrBlank()
            E2eeInfoPill(
                text = if (e2eeActive) {
                    "End-to-end encrypted (ECDH P-256 + AES-256-GCM)"
                } else if (chat?.chatType == "direct") {
                    "Pending peer key exchange — messages will not use E2EE until peer registers keys"
                } else {
                    "Group / Broadcast transport encrypted via TLS & Firestore rules"
                },
                active = e2eeActive
            )

            chat?.pinnedMessageId?.takeIf { it.isNotBlank() }?.let { pinnedId ->
                val pinnedMsg = messages.firstOrNull { it.id == pinnedId }
                if (pinnedMsg != null) {
                    Surface(
                        color = ArSurfaceElevatedDark,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PushPin, contentDescription = null, tint = ArEmeraldBright, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = viewModel.decryptMessageText(pinnedMsg, peerKey),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            val visibleMessages = remember(messages, searchMode, searchQuery) {
                if (!searchMode || searchQuery.isBlank()) messages
                else messages.filter {
                    viewModel.decryptMessageText(it, peerKey).contains(searchQuery, ignoreCase = true)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(visibleMessages, key = { it.id }) { msg ->
                    val isMine = msg.senderUid == viewModel.currentUserId
                    val decodedText = remember(msg.id, msg.ciphertextBase64, msg.plaintextFallback, msg.deletedForAll, peerKey) {
                        viewModel.decryptMessageText(msg, peerKey)
                    }
                    val isPlayingThis = playingMsgId == msg.id

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            color = if (isMine) ArBubbleOutgoingDark else ArBubbleIncomingDark,
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (isMine) 16.dp else 4.dp,
                                bottomEnd = if (isMine) 4.dp else 16.dp
                            ),
                            modifier = Modifier
                                .widthIn(max = 310.dp)
                                .combinedClickable(
                                    onClick = {},
                                    onLongClick = { selectedMessageForActions = msg }
                                )
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                if (!isMine && chat?.chatType == "group") {
                                    Text(
                                        text = msg.senderName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ArEmeraldBright,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (msg.replyToPreview.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 4.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black.copy(alpha = 0.25f))
                                            .padding(6.dp)
                                    ) {
                                        Text(msg.replyToPreview, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                if (msg.messageType == "image" && msg.attachmentDataBase64.isNotBlank()) {
                                    val bmp = remember(msg.attachmentDataBase64) {
                                        runCatching {
                                            val bytes = Base64.decode(msg.attachmentDataBase64, Base64.NO_WRAP)
                                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                                        }.getOrNull()
                                    }
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp,
                                            contentDescription = "Shared photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(180.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                } else if (msg.messageType == "voice" && msg.attachmentDataBase64.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = {
                                            if (isPlayingThis) {
                                                voiceHelper.stopPlayback()
                                                playingMsgId = null
                                            } else {
                                                val ok = voiceHelper.playBase64Audio(msg.id, msg.attachmentDataBase64, playbackSpeed) {
                                                    playingMsgId = null
                                                }
                                                if (ok) playingMsgId = msg.id
                                            }
                                        }) {
                                            Icon(
                                                imageVector = if (isPlayingThis) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = "Play voice note",
                                                tint = ArEmeraldBright
                                            )
                                        }
                                        Text(msg.attachmentMeta.ifBlank { "Voice Note" }, style = MaterialTheme.typography.bodyMedium)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        TextButton(onClick = {
                                            playbackSpeed = when (playbackSpeed) {
                                                1.0f -> 1.5f
                                                1.5f -> 2.0f
                                                else -> 1.0f
                                            }
                                        }) {
                                            Text("${playbackSpeed}x", style = MaterialTheme.typography.labelSmall, color = ArEmeraldBright)
                                        }
                                    }
                                }
                                Text(
                                    text = decodedText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (msg.deletedForAll) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.align(Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (viewModel.currentUserId in msg.starredByUids) {
                                        Icon(Icons.Default.Star, contentDescription = "Starred", tint = ArEmeraldBright, modifier = Modifier.size(12.dp))
                                    }
                                    if (msg.edited) {
                                        Text("edited", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (msg.encrypted) {
                                        Icon(Icons.Default.Lock, contentDescription = "Encrypted", tint = ArEmeraldBright, modifier = Modifier.size(11.dp))
                                    }
                                    Text(
                                        text = formatTimestampShort(msg.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isMine) {
                                        DeliveryTickIcon(status = msg.deliveryStatus)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Message Actions Menu
    selectedMessageForActions?.let { msg ->
        val decoded = viewModel.decryptMessageText(msg, peerKey)
        AlertDialog(
            onDismissRequest = { selectedMessageForActions = null },
            title = { Text("Message Options") },
            text = {
                Column {
                    TextButton(onClick = {
                        replyTarget = msg.id to decoded.take(60)
                        selectedMessageForActions = null
                    }) { Text("Reply") }
                    TextButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("A.R Chat", decoded))
                        selectedMessageForActions = null
                    }) { Text("Copy") }
                    TextButton(onClick = {
                        showForwardDialog = decoded
                        selectedMessageForActions = null
                    }) { Text("Forward") }
                    TextButton(onClick = {
                        viewModel.toggleStarMessage(chatId, msg)
                        selectedMessageForActions = null
                    }) { Text(if (viewModel.currentUserId in msg.starredByUids) "Unstar" else "Star Message") }
                    TextButton(onClick = {
                        viewModel.pinMessage(chatId, msg.id)
                        selectedMessageForActions = null
                    }) { Text("Pin Message") }
                    if (msg.senderUid == viewModel.currentUserId && !msg.deletedForAll) {
                        TextButton(onClick = {
                            editingMessage = msg
                            composerText = decoded
                            selectedMessageForActions = null
                        }) { Text("Edit Message") }
                        TextButton(onClick = {
                            viewModel.deleteMessage(chatId, msg.id)
                            selectedMessageForActions = null
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete for Everyone", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMessageForActions = null }) { Text("Close") }
            }
        )
    }

    // Attachment Picker Dialog
    if (showAttachmentDialog) {
        AlertDialog(
            onDismissRequest = { showAttachmentDialog = false },
            title = { Text("Send Attachment") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = {
                        showAttachmentDialog = false
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Icon(Icons.Default.Photo, contentDescription = null, tint = ArEmeraldBright)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Photo (Android Photo Picker)")
                    }
                    TextButton(onClick = {
                        showAttachmentDialog = false
                        docPicker.launch(arrayOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/zip", "*/*"))
                    }) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = ArEmeraldBright)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Document (PDF / Word / ZIP)")
                    }
                    TextButton(onClick = {
                        showAttachmentDialog = false
                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = ArEmeraldBright)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Share Location (Coarse GPS)")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAttachmentDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Safety Number Verification Dialog
    if (showSafetyDialog) {
        val myPub = myProfile?.ecdhPublicKey ?: ""
        val safetyCode = if (myPub.isNotBlank() && !peerKey.isNullOrBlank()) {
            viewModel.cryptoManager.computeSafetyNumber(myPub, peerKey)
        } else {
            "Awaiting both devices to publish ECDH P-256 identity keys."
        }
        AlertDialog(
            onDismissRequest = { showSafetyDialog = false },
            icon = { Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = ArEmeraldBright) },
            title = { Text("Verify Security Code") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Compare this 60-digit Safety Number derived from your ECDH P-256 keys with $displayTitle:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = safetyCode,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Text(
                        text = "Your fingerprint: ${myProfile?.keyFingerprint ?: "-"}\nPeer fingerprint: ${peerProfile?.keyFingerprint ?: "Not registered"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSafetyDialog = false }) { Text("Done") }
            }
        )
    }

    // Forward Dialog
    showForwardDialog?.let { textToForward ->
        val allChats = (chatsState as? UiState.Success)?.data.orEmpty()
        AlertDialog(
            onDismissRequest = { showForwardDialog = null },
            title = { Text("Forward To") },
            text = {
                LazyColumn(modifier = Modifier.height(220.dp)) {
                    items(allChats, key = { it.id }) { targetChat ->
                        TextButton(
                            onClick = {
                                val targetPeerUid = targetChat.memberUids.firstOrNull { it != viewModel.currentUserId }
                                val targetPeerKey = directory.firstOrNull { it.userId == targetPeerUid }?.ecdhPublicKey
                                viewModel.sendMessage(
                                    chat = targetChat,
                                    senderName = myName,
                                    plaintext = textToForward,
                                    peerPublicKeyBase64 = targetPeerKey,
                                    forwarded = true
                                )
                                showForwardDialog = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(targetChat.title) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showForwardDialog = null }) { Text("Cancel") }
            }
        )
    }
}
