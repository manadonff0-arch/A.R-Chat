package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.MediaPickerHelper
import com.example.core.UiState
import com.example.core.UserUiPreferences
import com.example.data.model.CallSession
import com.example.data.model.Chat
import com.example.data.model.PublicProfile
import com.example.ui.components.ArAvatar
import com.example.ui.components.DeliveryTickIcon
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.formatTimestampShort
import com.example.ui.theme.ArEmeraldBright
import com.example.ui.theme.ArEmeraldDeep
import com.example.ui.theme.ArEmeraldPrimary
import com.example.ui.theme.ArWarningAmber
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainHomeScreen(
    viewModel: MainViewModel,
    uiPrefs: UserUiPreferences,
    onOpenChat: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenLinkedDevices: () -> Unit,
    onOpenStarredMessages: () -> Unit,
    onOpenCallOverlay: (CallSession) -> Unit,
    onSwitchAccount: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showNewChatSheet by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf<String?>(null) } // "group" or "broadcast"
    var showCreateCommunityDialog by remember { mutableStateOf(false) }

    val privateProfileState by viewModel.privateProfileState.collectAsStateWithLifecycle()
    val publicProfileState by viewModel.publicProfileState.collectAsStateWithLifecycle()
    val appConfigState by viewModel.publicAppConfigState.collectAsStateWithLifecycle()
    val directoryState by viewModel.activeDirectoryState.collectAsStateWithLifecycle()
    val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.statusBannerMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(bannerMessage) {
        if (!bannerMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(bannerMessage!!)
            viewModel.clearBanner()
        }
    }

    val myPrivate = (privateProfileState as? UiState.Success)?.data
    val myPublic = (publicProfileState as? UiState.Success)?.data
    val appConfig = (appConfigState as? UiState.Success)?.data
    val directory = (directoryState as? UiState.Success)?.data.orEmpty()
    val chats = (chatsState as? UiState.Success)?.data.orEmpty()
    val myName = myPublic?.displayName?.ifBlank { "A.R User" } ?: "A.R User"

    val cameraStatusLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val b64 = MediaPickerHelper.uriToCompressedBase64(context, uri, 720, 90_000) ?: ""
            if (b64.isNotBlank()) {
                viewModel.createStatus(
                    ownerName = myName,
                    statusType = "photo",
                    textContent = "Shared via A.R Chat Camera",
                    backgroundHex = "#073022",
                    mediaDataBase64 = b64,
                    privacyMode = "contacts"
                )
                selectedTab = 1
            }
        }
    }

    val tabItems = listOf(
        Triple(stringResource(R.string.tab_chats), Icons.Filled.Chat, Icons.Outlined.Chat),
        Triple(stringResource(R.string.tab_updates), Icons.Filled.Update, Icons.Outlined.Update),
        Triple(stringResource(R.string.tab_communities), Icons.Filled.Groups, Icons.Outlined.Groups),
        Triple(stringResource(R.string.tab_calls), Icons.Filled.Call, Icons.Outlined.Call)
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (selectedTab) {
                                0 -> "A.R Chat"
                                1 -> stringResource(R.string.tab_updates)
                                2 -> stringResource(R.string.tab_communities)
                                else -> stringResource(R.string.tab_calls)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 0) ArEmeraldBright else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                cameraStatusLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.testTag("top_camera_button")
                        ) {
                            Icon(Icons.Outlined.PhotoCamera, contentDescription = "Camera status shortcut")
                        }
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.testTag("three_dot_menu_button")
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("New Group") },
                                    onClick = { menuExpanded = false; showCreateGroupDialog = "group" }
                                )
                                DropdownMenuItem(
                                    text = { Text("New Community") },
                                    onClick = { menuExpanded = false; showCreateCommunityDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Broadcast Lists") },
                                    onClick = { menuExpanded = false; showCreateGroupDialog = "broadcast" }
                                )
                                DropdownMenuItem(
                                    text = { Text("Linked Devices") },
                                    onClick = { menuExpanded = false; onOpenLinkedDevices() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Starred Messages") },
                                    onClick = { menuExpanded = false; onOpenStarredMessages() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Read All") },
                                    onClick = { menuExpanded = false; viewModel.markAllChatsRead() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Profile") },
                                    onClick = { menuExpanded = false; onOpenProfile() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    onClick = { menuExpanded = false; onOpenSettings() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Switch Account") },
                                    onClick = { menuExpanded = false; onSwitchAccount() }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                if (!isWide) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        tabItems.forEachIndexed { idx, (label, filled, outlined) ->
                            NavigationBarItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == idx) filled else outlined,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = ArEmeraldBright,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = ArEmeraldDeep
                                ),
                                modifier = Modifier.testTag("bottom_tab_$idx")
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (selectedTab == 0) {
                    FloatingActionButton(
                        onClick = { showNewChatSheet = true },
                        containerColor = ArEmeraldPrimary,
                        contentColor = Color(0xFF042119),
                        modifier = Modifier.testTag("fab_new_chat")
                    ) {
                        Icon(Icons.Default.AddComment, contentDescription = "New chat")
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isWide) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        tabItems.forEachIndexed { idx, (label, filled, outlined) ->
                            NavigationRailItem(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == idx) filled else outlined,
                                        contentDescription = label
                                    )
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    if (appConfig?.maintenanceMode == true) {
                        Surface(
                            color = ArWarningAmber.copy(alpha = 0.2f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Maintenance Mode: ${appConfig.maintenanceMessage.ifBlank { "Scheduled maintenance in progress." }}",
                                style = MaterialTheme.typography.labelMedium,
                                color = ArWarningAmber,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    } else if (!appConfig?.globalAnnouncement.isNullOrBlank()) {
                        Surface(
                            color = ArEmeraldDeep.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Campaign, contentDescription = null, tint = ArEmeraldBright, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = appConfig!!.globalAnnouncement,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ArEmeraldBright
                                )
                            }
                        }
                    }

                    when (selectedTab) {
                        0 -> ChatsTabContent(
                            viewModel = viewModel,
                            chatsState = chatsState,
                            directory = directory,
                            archivedIds = myPrivate?.archivedChatIds.orEmpty(),
                            pinnedIds = myPrivate?.pinnedChatIds.orEmpty(),
                            mutedIds = myPrivate?.mutedChatIds.orEmpty(),
                            favoriteIds = myPrivate?.favoriteChatIds.orEmpty(),
                            onOpenChat = onOpenChat,
                            onNewChatClicked = { showNewChatSheet = true }
                        )
                        1 -> UpdatesTab(
                            viewModel = viewModel,
                            myDisplayName = myName
                        )
                        2 -> CommunitiesTab(
                            viewModel = viewModel,
                            directory = directory,
                            chats = chats,
                            showCreateDialogExternal = showCreateCommunityDialog,
                            onDismissExternalDialog = { showCreateCommunityDialog = false },
                            onOpenChat = onOpenChat
                        )
                        3 -> CallsTab(
                            viewModel = viewModel,
                            myDisplayName = myName,
                            directory = directory,
                            onStartCallOverlay = onOpenCallOverlay
                        )
                    }
                }
            }
        }
    }

    if (showNewChatSheet) {
        NewChatDirectoryDialog(
            myUid = viewModel.currentUserId,
            myDisplayName = myName,
            directory = directory,
            onDismiss = { showNewChatSheet = false },
            onSelectPeer = { peer ->
                showNewChatSheet = false
                viewModel.openDirectChat(peer, myName, onOpenChat)
            },
            onCreateGroup = {
                showNewChatSheet = false
                showCreateGroupDialog = "group"
            },
            onCreateBroadcast = {
                showNewChatSheet = false
                showCreateGroupDialog = "broadcast"
            }
        )
    }

    showCreateGroupDialog?.let { mode ->
        CreateGroupOrBroadcastDialog(
            mode = mode,
            myUid = viewModel.currentUserId,
            directory = directory,
            onDismiss = { showCreateGroupDialog = null },
            onCreate = { title, desc, members ->
                showCreateGroupDialog = null
                viewModel.createGroupOrBroadcast(mode, title, desc, members, onOpenChat)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatsTabContent(
    viewModel: MainViewModel,
    chatsState: UiState<List<Chat>>,
    directory: List<PublicProfile>,
    archivedIds: List<String>,
    pinnedIds: List<String>,
    mutedIds: List<String>,
    favoriteIds: List<String>,
    onOpenChat: (String) -> Unit,
    onNewChatClicked: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var customFilterName by remember { mutableStateOf<String?>(null) }
    var showAddFilterDialog by remember { mutableStateOf(false) }
    var showArchivedOnly by remember { mutableStateOf(false) }
    var contextMenuChat by remember { mutableStateOf<Chat?>(null) }

    val profileById = remember(directory) { directory.associateBy { it.userId } }

    Column(modifier = Modifier.fillMaxSize()) {
        // Rounded WhatsApp-style Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("chats_search_input")
        )

        // Filter Chips Row
        val baseFilters = listOfNotNull("All", "Unread", "Favorites", "Groups", customFilterName)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            baseFilters.forEach { label ->
                val isSelected = selectedFilter == label
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = label },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ArEmeraldDeep,
                        selectedLabelColor = ArEmeraldBright
                    ),
                    modifier = Modifier.testTag("filter_chip_$label")
                )
            }
            FilterChip(
                selected = false,
                onClick = { showAddFilterDialog = true },
                label = { Icon(Icons.Default.Add, contentDescription = "Add filter", modifier = Modifier.size(16.dp)) },
                modifier = Modifier.testTag("filter_chip_add")
            )
        }

        // Archived Row
        if (archivedIds.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showArchivedOnly = !showArchivedOnly }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Archive, contentDescription = "Archived", tint = ArEmeraldBright, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = if (showArchivedOnly) "Back to Active Chats" else "Archived (${archivedIds.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        }

        when (chatsState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ArEmeraldPrimary)
            }
            is UiState.Error -> EmptyStateCard(
                icon = Icons.Default.Chat,
                title = "Unable to sync chats",
                subtitle = chatsState.message
            )
            is UiState.Success -> {
                val filtered = remember(chatsState.data, searchQuery, selectedFilter, showArchivedOnly, archivedIds, pinnedIds, favoriteIds) {
                    chatsState.data
                        .filter { chat ->
                            val isArch = chat.id in archivedIds
                            if (showArchivedOnly) isArch else !isArch
                        }
                        .filter { chat ->
                            when (selectedFilter) {
                                "Groups" -> chat.chatType == "group" || chat.chatType == "broadcast"
                                "Favorites" -> chat.id in favoriteIds
                                "Unread" -> chat.lastMessageSenderUid.isNotBlank() && chat.lastMessageSenderUid != viewModel.currentUserId
                                else -> true
                            }
                        }
                        .filter { chat ->
                            if (searchQuery.isBlank()) true
                            else chat.title.contains(searchQuery, ignoreCase = true) ||
                                chat.lastMessagePreview.contains(searchQuery, ignoreCase = true)
                        }
                        .sortedByDescending { if (it.id in pinnedIds) 1 else 0 }
                }

                if (filtered.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        EmptyStateCard(
                            icon = Icons.Default.Chat,
                            title = if (showArchivedOnly) "No archived chats" else "Start a secure conversation",
                            subtitle = "Tap the green message button below to chat with any registered A.R Chat user using ECDH + AES-256-GCM encryption."
                        )
                        TextButton(onClick = onNewChatClicked) {
                            Text("Find Contacts / Start Self Chat", color = ArEmeraldBright)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        items(filtered, key = { it.id }) { chat ->
                            val peerUid = chat.memberUids.firstOrNull { it != viewModel.currentUserId }
                            val peer = peerUid?.let { profileById[it] }
                            val displayTitle = if (chat.chatType == "direct" && peer != null) peer.displayName else chat.title
                            val isPinned = chat.id in pinnedIds
                            val isMuted = chat.id in mutedIds
                            val isFav = chat.id in favoriteIds

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = { onOpenChat(chat.id) },
                                        onLongClick = { contextMenuChat = chat }
                                    )
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .testTag("chat_row_${chat.id}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ArAvatar(
                                    displayName = displayTitle,
                                    avatarBase64 = peer?.avatarData ?: "",
                                    size = 52.dp,
                                    showOnlineBadge = chat.chatType == "direct",
                                    isOnline = peer?.online == true
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = displayTitle,
                                                style = MaterialTheme.typography.titleMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (chat.lastMessageEncrypted) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    Icons.Default.Lock,
                                                    contentDescription = "E2EE",
                                                    tint = ArEmeraldBright,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = formatTimestampShort(chat.lastMessageAt ?: chat.updatedAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (chat.lastMessageSenderUid == viewModel.currentUserId) {
                                            DeliveryTickIcon(status = "delivered")
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        if (chat.lastMessageType == "voice") {
                                            Icon(Icons.Default.Mic, contentDescription = null, tint = ArEmeraldBright, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        Text(
                                            text = chat.lastMessagePreview.ifBlank { "Tap to chat" },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isPinned) {
                                            Icon(Icons.Default.PushPin, contentDescription = "Pinned", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        if (isFav) {
                                            Icon(Icons.Default.Favorite, contentDescription = "Favorite", tint = ArEmeraldBright, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                        }
                                        if (isMuted) {
                                            Icon(Icons.Default.NotificationsOff, contentDescription = "Muted", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    contextMenuChat?.let { chat ->
        AlertDialog(
            onDismissRequest = { contextMenuChat = null },
            title = { Text(chat.title) },
            text = {
                Column {
                    TextButton(onClick = {
                        viewModel.toggleChatListMembership("pinnedChatIds", pinnedIds, chat.id)
                        contextMenuChat = null
                    }) { Text(if (chat.id in pinnedIds) "Unpin Chat" else "Pin Chat") }
                    TextButton(onClick = {
                        viewModel.toggleChatListMembership("favoriteChatIds", favoriteIds, chat.id)
                        contextMenuChat = null
                    }) { Text(if (chat.id in favoriteIds) "Remove from Favorites" else "Add to Favorites") }
                    TextButton(onClick = {
                        viewModel.toggleChatListMembership("mutedChatIds", mutedIds, chat.id)
                        contextMenuChat = null
                    }) { Text(if (chat.id in mutedIds) "Unmute Notifications" else "Mute Notifications") }
                    TextButton(onClick = {
                        viewModel.toggleChatListMembership("archivedChatIds", archivedIds, chat.id)
                        contextMenuChat = null
                    }) { Text(if (chat.id in archivedIds) "Unarchive Chat" else "Archive Chat") }
                }
            },
            confirmButton = {
                TextButton(onClick = { contextMenuChat = null }) { Text("Done") }
            }
        )
    }

    if (showAddFilterDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddFilterDialog = false },
            title = { Text("Create Custom Chat Folder") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder Name (e.g. Work, Family)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) customFilterName = name.trim()
                    showAddFilterDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddFilterDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun NewChatDirectoryDialog(
    myUid: String,
    myDisplayName: String,
    directory: List<PublicProfile>,
    onDismiss: () -> Unit,
    onSelectPeer: (PublicProfile) -> Unit,
    onCreateGroup: () -> Unit,
    onCreateBroadcast: () -> Unit
) {
    val mySelfProfile = directory.firstOrNull { it.userId == myUid }
        ?: PublicProfile(userId = myUid, displayName = "$myDisplayName (Saved Messages)", username = "you")
    val peers = directory.filter { it.userId != myUid }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Conversation") },
        text = {
            LazyColumn(modifier = Modifier.height(340.dp)) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCreateGroup() }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(ArEmeraldPrimary),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Groups, contentDescription = null, tint = Color.White) }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("New Group", style = MaterialTheme.typography.titleSmall)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCreateBroadcast() }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(ArEmeraldDeep),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Campaign, contentDescription = null, tint = ArEmeraldBright) }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("New Broadcast List", style = MaterialTheme.typography.titleSmall)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPeer(mySelfProfile) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArAvatar(displayName = myDisplayName, avatarBase64 = mySelfProfile.avatarData, size = 40.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("$myDisplayName (You)", style = MaterialTheme.typography.titleSmall)
                            Text("Encrypted personal notes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                items(peers, key = { it.userId }) { peer ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPeer(peer) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArAvatar(displayName = peer.displayName, avatarBase64 = peer.avatarData, size = 40.dp, showOnlineBadge = true, isOnline = peer.online)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(peer.displayName, style = MaterialTheme.typography.titleSmall)
                            Text("@${peer.username} • ${peer.carrierName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun CreateGroupOrBroadcastDialog(
    mode: String,
    myUid: String,
    directory: List<PublicProfile>,
    onDismiss: () -> Unit,
    onCreate: (String, String, List<String>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    val selectedUids = remember { mutableStateListOf<String>() }
    val peers = directory.filter { it.userId != myUid }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mode == "broadcast") "New Broadcast List" else "Create New Group") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (mode == "broadcast") "Broadcast List Name" else "Group Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text("Select Members (${selectedUids.size})", style = MaterialTheme.typography.labelMedium)
                LazyColumn(modifier = Modifier.height(180.dp)) {
                    items(peers, key = { it.userId }) { peer ->
                        val checked = peer.userId in selectedUids
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedUids.remove(peer.userId) else selectedUids.add(peer.userId)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = {
                                if (it) selectedUids.add(peer.userId) else selectedUids.remove(peer.userId)
                            })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(peer.displayName, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank()) onCreate(title, desc, selectedUids.toList()) }
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
