package com.example.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.data.model.Channel
import com.example.data.model.StatusUpdate
import com.example.ui.components.ArAvatar
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.formatTimestampShort
import com.example.ui.theme.ArEmeraldBright
import com.example.ui.theme.ArEmeraldDeep
import com.example.ui.theme.ArEmeraldPrimary
import com.example.ui.viewmodel.MainViewModel

@Composable
fun UpdatesTab(
    viewModel: MainViewModel,
    myDisplayName: String
) {
    val context = LocalContext.current
    val ownStatusesState by viewModel.ownStatusesState.collectAsStateWithLifecycle()
    val contactStatusesState by viewModel.contactsStatusesState.collectAsStateWithLifecycle()
    val channelsState by viewModel.channelsState.collectAsStateWithLifecycle()
    val channelPostsState by viewModel.selectedChannelPostsState.collectAsStateWithLifecycle()

    val ownStatuses = (ownStatusesState as? UiState.Success)?.data.orEmpty()
    val contactStatuses = (contactStatusesState as? UiState.Success)?.data.orEmpty()
        .filter { it.ownerUid != viewModel.currentUserId }
    val channels = (channelsState as? UiState.Success)?.data.orEmpty()

    var showTextStatusDialog by remember { mutableStateOf(false) }
    var viewingStatus by remember { mutableStateOf<StatusUpdate?>(null) }
    var showCreateChannelDialog by remember { mutableStateOf(false) }
    var activeChannel by remember { mutableStateOf<Channel?>(null) }

    val statusPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val b64 = MediaPickerHelper.uriToCompressedBase64(context, uri, 720, 95_000) ?: ""
            if (b64.isNotBlank()) {
                viewModel.createStatus(
                    ownerName = myDisplayName,
                    statusType = "photo",
                    textContent = "",
                    backgroundHex = "#073022",
                    mediaDataBase64 = b64,
                    privacyMode = "contacts"
                )
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Status (24h)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { showTextStatusDialog = true },
                        modifier = Modifier.testTag("add_text_status_btn")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Post text status", tint = ArEmeraldBright)
                    }
                    IconButton(
                        onClick = {
                            statusPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.testTag("add_photo_status_btn")
                    ) {
                        Icon(Icons.Default.Photo, contentDescription = "Post photo status", tint = ArEmeraldBright)
                    }
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(76.dp)
                            .clickable {
                                if (ownStatuses.isNotEmpty()) viewingStatus = ownStatuses.first()
                                else showTextStatusDialog = true
                            }
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            ArAvatar(
                                displayName = myDisplayName,
                                size = 60.dp,
                                modifier = Modifier.border(
                                    width = if (ownStatuses.isNotEmpty()) 2.dp else 0.dp,
                                    color = ArEmeraldBright,
                                    shape = CircleShape
                                )
                            )
                            Box(
                                modifier = Modifier.size(22.dp).clip(CircleShape).background(ArEmeraldPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (ownStatuses.isEmpty()) "Add Status" else "My Status (${ownStatuses.size})",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                items(contactStatuses, key = { it.id }) { status ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(76.dp)
                            .clickable {
                                viewModel.markStatusViewed(status)
                                viewingStatus = status
                            }
                    ) {
                        ArAvatar(
                            displayName = status.ownerName,
                            size = 60.dp,
                            modifier = Modifier.border(2.dp, ArEmeraldBright, CircleShape)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = status.ownerName,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        item {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Channels", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(
                    onClick = { showCreateChannelDialog = true },
                    modifier = Modifier.testTag("create_channel_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Create Channel")
                }
            }
        }

        if (channels.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Default.Campaign,
                    title = "Discover & Create Channels",
                    subtitle = "Follow broadcast channels or create your own to share updates with followers."
                )
            }
        } else {
            items(channels, key = { it.id }) { channel ->
                val isFollowing = viewModel.currentUserId in channel.followerUids
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.selectChannel(channel.id)
                            activeChannel = channel
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArAvatar(displayName = channel.name, size = 48.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(channel.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = channel.description.ifBlank { "${channel.followerCount} followers" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${channel.followerCount} followers",
                                style = MaterialTheme.typography.labelSmall,
                                color = ArEmeraldBright
                            )
                        }
                        if (isFollowing) {
                            OutlinedButton(onClick = { viewModel.toggleFollowChannel(channel) }) {
                                Text("Following")
                            }
                        } else {
                            Button(
                                onClick = { viewModel.toggleFollowChannel(channel) },
                                colors = ButtonDefaults.buttonColors(containerColor = ArEmeraldPrimary)
                            ) {
                                Text("Follow")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTextStatusDialog) {
        var text by remember { mutableStateOf("") }
        var privacy by remember { mutableStateOf("contacts") }
        var bgHex by remember { mutableStateOf("#073022") }
        AlertDialog(
            onDismissRequest = { showTextStatusDialog = false },
            title = { Text("Post 24h Status") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Type a status or paste a link...") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Background:", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("#073022", "#1B3A4B", "#4A1942", "#5C3D2E").forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .border(if (bgHex == hex) 2.dp else 0.dp, Color.White, CircleShape)
                                    .clickable { bgHex = hex }
                            )
                        }
                    }
                    Text("Privacy:", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("contacts" to "Contacts", "only" to "Only Me").forEach { (key, label) ->
                            FilterChip(
                                selected = privacy == key,
                                onClick = { privacy = key },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) {
                        viewModel.createStatus(
                            ownerName = myDisplayName,
                            statusType = if (text.contains("http")) "link" else "text",
                            textContent = text,
                            backgroundHex = bgHex,
                            mediaDataBase64 = "",
                            privacyMode = privacy
                        )
                        showTextStatusDialog = false
                    }
                }) { Text("Post") }
            },
            dismissButton = {
                TextButton(onClick = { showTextStatusDialog = false }) { Text("Cancel") }
            }
        )
    }

    viewingStatus?.let { st ->
        AlertDialog(
            onDismissRequest = { viewingStatus = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(st.ownerName, style = MaterialTheme.typography.titleMedium)
                        Text(formatTimestampShort(st.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (st.ownerUid == viewModel.currentUserId) {
                        IconButton(onClick = {
                            viewModel.deleteStatus(st.id)
                            viewingStatus = null
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete status", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (st.mediaDataBase64.isNotBlank()) {
                        val bmp = remember(st.mediaDataBase64) {
                            runCatching {
                                val bytes = Base64.decode(st.mediaDataBase64, Base64.NO_WRAP)
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                            }.getOrNull()
                        }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp,
                                contentDescription = "Status photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp))
                            )
                        }
                    }
                    if (st.textContent.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    runCatching { Color(android.graphics.Color.parseColor(st.backgroundHex)) }
                                        .getOrDefault(ArEmeraldDeep)
                                )
                                .padding(18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(st.textContent, color = Color.White, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp), tint = ArEmeraldBright)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${st.viewerUids.size} views", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingStatus = null }) { Text("Close") }
            }
        )
    }

    if (showCreateChannelDialog) {
        var chName by remember { mutableStateOf("") }
        var chDesc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateChannelDialog = false },
            title = { Text("Create Channel") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = chName, onValueChange = { chName = it }, label = { Text("Channel Name") }, singleLine = true)
                    OutlinedTextField(value = chDesc, onValueChange = { chDesc = it }, label = { Text("Description") }, maxLines = 3)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (chName.isNotBlank()) {
                        viewModel.createChannel(chName, chDesc)
                        showCreateChannelDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateChannelDialog = false }) { Text("Cancel") }
            }
        )
    }

    activeChannel?.let { channel ->
        LaunchedEffect(channel.id) { viewModel.selectChannel(channel.id) }
        val posts = (channelPostsState as? UiState.Success)?.data.orEmpty()
        var newPostText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { activeChannel = null; viewModel.selectChannel(null) },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(channel.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = {
                        viewModel.submitReportOrSupport("channel", channel.id, "Reported channel ${channel.name}")
                    }) {
                        Icon(Icons.Default.Flag, contentDescription = "Report channel")
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(channel.description.ifBlank { "Official channel updates" }, style = MaterialTheme.typography.bodySmall)
                    LazyColumn(modifier = Modifier.height(220.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(posts, key = { it.id }) { post ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(post.content, style = MaterialTheme.typography.bodyMedium)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(formatTimestampShort(post.createdAt), style = MaterialTheme.typography.labelSmall)
                                        TextButton(onClick = { viewModel.reactToChannelPost(channel.id, post.id) }) {
                                            Icon(Icons.Default.Favorite, contentDescription = null, tint = ArEmeraldBright, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("${post.reactionsCount}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (channel.ownerUid == viewModel.currentUserId) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newPostText,
                                onValueChange = { newPostText = it },
                                placeholder = { Text("Publish update...") },
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                if (newPostText.isNotBlank()) {
                                    viewModel.publishChannelPost(channel.id, "text", newPostText)
                                    newPostText = ""
                                }
                            }) { Text("Post") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeChannel = null; viewModel.selectChannel(null) }) { Text("Close") }
            }
        )
    }
}
