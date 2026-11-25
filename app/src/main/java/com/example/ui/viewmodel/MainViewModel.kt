package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.E2eeCryptoManager
import com.example.core.UiState
import com.example.data.model.AppPublicConfig
import com.example.data.model.CallSession
import com.example.data.model.Channel
import com.example.data.model.ChannelPost
import com.example.data.model.Chat
import com.example.data.model.ChatMessage
import com.example.data.model.Community
import com.example.data.model.EncryptedBackup
import com.example.data.model.LinkedDevice
import com.example.data.model.PublicProfile
import com.example.data.model.ReportTicket
import com.example.data.model.StatusUpdate
import com.example.data.model.UserPrivate
import com.example.data.repository.AuthRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.CommunityAndCallsRepository
import com.example.data.repository.OwnerRepository
import com.example.data.repository.UpdatesRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    val currentUserId: String,
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val updatesRepository: UpdatesRepository,
    private val communityAndCallsRepository: CommunityAndCallsRepository,
    private val ownerRepository: OwnerRepository,
    val cryptoManager: E2eeCryptoManager
) : ViewModel() {

    private fun <T> Flow<T>.toUiStateFlow(tag: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w(tag, "Stream error", root)
                emit(UiState.Error(root.message ?: "Failed to load data"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val privateProfileState: StateFlow<UiState<UserPrivate?>> =
        authRepository.observePrivateProfile(currentUserId).toUiStateFlow("PrivateProfileVM")

    val publicProfileState: StateFlow<UiState<PublicProfile?>> =
        authRepository.observePublicProfile(currentUserId).toUiStateFlow("PublicProfileVM")

    val publicAppConfigState: StateFlow<UiState<AppPublicConfig?>> =
        ownerRepository.observePublicAppConfig().toUiStateFlow("AppConfigVM")

    val activeDirectoryState: StateFlow<UiState<List<PublicProfile>>> =
        chatRepository.observeActivePublicProfiles().toUiStateFlow("DirectoryVM")

    val chatsState: StateFlow<UiState<List<Chat>>> =
        chatRepository.observeChats(currentUserId).toUiStateFlow("ChatsVM")

    val ownStatusesState: StateFlow<UiState<List<StatusUpdate>>> =
        updatesRepository.observeOwnStatuses(currentUserId).toUiStateFlow("OwnStatusVM")

    val contactsStatusesState: StateFlow<UiState<List<StatusUpdate>>> =
        updatesRepository.observeContactsStatuses().toUiStateFlow("ContactStatusVM")

    val channelsState: StateFlow<UiState<List<Channel>>> =
        updatesRepository.observeChannels().toUiStateFlow("ChannelsVM")

    val communitiesState: StateFlow<UiState<List<Community>>> =
        communityAndCallsRepository.observeCommunities(currentUserId).toUiStateFlow("CommunitiesVM")

    val callsState: StateFlow<UiState<List<CallSession>>> =
        communityAndCallsRepository.observeCalls(currentUserId).toUiStateFlow("CallsVM")

    val linkedDevicesState: StateFlow<UiState<List<LinkedDevice>>> =
        authRepository.observeLinkedDevices(currentUserId).toUiStateFlow("DevicesVM")

    val backupsState: StateFlow<UiState<List<EncryptedBackup>>> =
        authRepository.observeBackups(currentUserId).toUiStateFlow("BackupsVM")

    val myReportsState: StateFlow<UiState<List<ReportTicket>>> =
        communityAndCallsRepository.observeMyReports(currentUserId).toUiStateFlow("MyReportsVM")

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    val selectedChatMessagesState: StateFlow<UiState<List<ChatMessage>>> =
        _selectedChatId.flatMapLatest { chatId ->
            if (chatId.isNullOrBlank()) flowOf(emptyList())
            else chatRepository.observeMessages(chatId, currentUserId)
        }.toUiStateFlow("MessagesVM")

    private val _selectedChannelId = MutableStateFlow<String?>(null)
    val selectedChannelPostsState: StateFlow<UiState<List<ChannelPost>>> =
        _selectedChannelId.flatMapLatest { channelId ->
            if (channelId.isNullOrBlank()) flowOf(emptyList())
            else updatesRepository.observeChannelPosts(channelId)
        }.toUiStateFlow("ChannelPostsVM")

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    fun clearBanner() {
        _statusBannerMessage.value = null
    }

    fun selectChat(chatId: String?) {
        _selectedChatId.value = chatId
    }

    fun selectChannel(channelId: String?) {
        _selectedChannelId.value = channelId
    }

    fun decryptMessageText(msg: ChatMessage, peerPublicKeyBase64: String?): String {
        if (msg.deletedForAll) return "This message was deleted"
        if (!msg.encrypted) return msg.plaintextFallback
        if (peerPublicKeyBase64.isNullOrBlank() || msg.ciphertextBase64.isBlank() || msg.ivBase64.isBlank()) {
            return msg.plaintextFallback.ifBlank { "[Encrypted message awaiting key]" }
        }
        return cryptoManager.decryptFromPeer(
            myUid = currentUserId,
            peerPublicKeyBase64 = peerPublicKeyBase64,
            ciphertextBase64 = msg.ciphertextBase64,
            ivBase64 = msg.ivBase64
        ).getOrElse {
            "[Unable to decrypt with current session key]"
        }
    }

    fun saveInitialProfile(
        phoneNumber: String,
        countryCode: String,
        carrierName: String,
        displayName: String,
        username: String,
        about: String,
        avatarData: String,
        email: String,
        languageCode: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val res = authRepository.saveUserProfileSetup(
                phoneNumber = phoneNumber,
                countryCode = countryCode,
                carrierName = carrierName,
                displayName = displayName,
                username = username,
                about = about,
                avatarData = avatarData,
                email = email,
                languageCode = languageCode
            )
            res.onSuccess { onSuccess() }
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Failed to save profile" }
        }
    }

    fun openDirectChat(peer: PublicProfile, myDisplayName: String, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            chatRepository.createOrOpenDirectChat(peer, myDisplayName)
                .onSuccess { chatId ->
                    _selectedChatId.value = chatId
                    onOpened(chatId)
                }
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Failed to open chat" }
        }
    }

    fun createGroupOrBroadcast(
        chatType: String,
        title: String,
        description: String,
        memberUids: List<String>,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            chatRepository.createGroupOrBroadcastChat(chatType, title, description, memberUids)
                .onSuccess { chatId ->
                    _selectedChatId.value = chatId
                    onCreated(chatId)
                }
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Failed to create $chatType" }
        }
    }

    fun sendMessage(
        chat: Chat,
        senderName: String,
        plaintext: String,
        messageType: String = "text",
        peerPublicKeyBase64: String? = null,
        attachmentMeta: String = "",
        attachmentDataBase64: String = "",
        replyToMessageId: String = "",
        replyToPreview: String = "",
        forwarded: Boolean = false
    ) {
        viewModelScope.launch {
            chatRepository.sendMessage(
                chat = chat,
                senderName = senderName,
                plaintext = plaintext,
                messageType = messageType,
                peerPublicKeyBase64 = peerPublicKeyBase64,
                attachmentMeta = attachmentMeta,
                attachmentDataBase64 = attachmentDataBase64,
                replyToMessageId = replyToMessageId,
                replyToPreview = replyToPreview,
                forwarded = forwarded
            ).onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Failed to send message" }
        }
    }

    fun editMessage(chat: Chat, message: ChatMessage, newText: String, peerPublicKeyBase64: String?) {
        viewModelScope.launch {
            chatRepository.editMessage(chat, message, newText, peerPublicKeyBase64)
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Failed to edit message" }
        }
    }

    fun deleteMessage(chatId: String, messageId: String) {
        viewModelScope.launch {
            chatRepository.deleteMessageForAll(chatId, messageId)
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Failed to delete message" }
        }
    }

    fun toggleStarMessage(chatId: String, message: ChatMessage) {
        viewModelScope.launch {
            chatRepository.toggleStarMessage(chatId, message)
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Failed to update star" }
        }
    }

    fun pinMessage(chatId: String, messageId: String) {
        viewModelScope.launch {
            chatRepository.pinMessage(chatId, messageId)
                .onSuccess { _statusBannerMessage.value = "Message pinned" }
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Failed to pin message" }
        }
    }

    fun markChatRead(chatId: String, messages: List<ChatMessage>) {
        viewModelScope.launch {
            chatRepository.markMessagesAsRead(chatId, messages)
        }
    }

    fun markAllChatsRead() {
        val chats = (chatsState.value as? UiState.Success)?.data.orEmpty()
        viewModelScope.launch {
            chatRepository.markAllChatsRead(chats)
                .onSuccess { count ->
                    _statusBannerMessage.value = if (count > 0) "Marked $count messages as read" else "All chats are already read"
                }
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Failed to mark chats read" }
        }
    }

    fun toggleChatListMembership(field: String, currentList: List<String>, chatId: String) {
        viewModelScope.launch {
            val next = if (chatId in currentList) currentList - chatId else currentList + chatId
            authRepository.updateUserListField(field, next)
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Update failed" }
        }
    }

    fun leaveChat(chat: Chat, onLeft: () -> Unit) {
        viewModelScope.launch {
            chatRepository.leaveChat(chat)
                .onSuccess { onLeft() }
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Failed to leave chat" }
        }
    }

    fun createStatus(
        ownerName: String,
        statusType: String,
        textContent: String,
        backgroundHex: String,
        mediaDataBase64: String,
        privacyMode: String
    ) {
        viewModelScope.launch {
            updatesRepository.createStatus(
                ownerName = ownerName,
                statusType = statusType,
                textContent = textContent,
                backgroundHex = backgroundHex,
                mediaDataBase64 = mediaDataBase64,
                privacyMode = privacyMode
            ).onSuccess { _statusBannerMessage.value = "Status published for 24h" }
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Failed to post status" }
        }
    }

    fun markStatusViewed(status: StatusUpdate) {
        viewModelScope.launch { updatesRepository.markStatusViewed(status) }
    }

    fun deleteStatus(statusId: String) {
        viewModelScope.launch {
            updatesRepository.deleteStatus(statusId)
                .onSuccess { _statusBannerMessage.value = "Status deleted" }
        }
    }

    fun createChannel(name: String, description: String) {
        viewModelScope.launch {
            updatesRepository.createChannel(name, description)
                .onSuccess { _statusBannerMessage.value = "Channel created" }
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Channel creation failed" }
        }
    }

    fun toggleFollowChannel(channel: Channel) {
        viewModelScope.launch {
            updatesRepository.toggleFollowChannel(channel)
        }
    }

    fun publishChannelPost(channelId: String, postType: String, content: String) {
        viewModelScope.launch {
            updatesRepository.publishChannelPost(channelId, postType, content)
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Failed to post update" }
        }
    }

    fun reactToChannelPost(channelId: String, postId: String) {
        viewModelScope.launch { updatesRepository.reactToChannelPost(channelId, postId) }
    }

    fun createCommunity(name: String, description: String, memberUids: List<String>, linkedChatIds: List<String>) {
        viewModelScope.launch {
            communityAndCallsRepository.createCommunity(name, description, memberUids, linkedChatIds)
                .onSuccess { _statusBannerMessage.value = "Community created with Announcement group" }
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Community creation failed" }
        }
    }

    fun startCall(
        callerName: String,
        calleeUid: String,
        calleeName: String,
        participantUids: List<String>,
        callType: String,
        onStarted: (String) -> Unit
    ) {
        viewModelScope.launch {
            communityAndCallsRepository.startCallSession(
                callerName = callerName,
                calleeUid = calleeUid,
                calleeName = calleeName,
                participantUids = participantUids,
                callType = callType
            ).onSuccess { onStarted(it) }
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Call signaling failed" }
        }
    }

    fun updateCallState(callId: String, newState: String, durationSeconds: Long = 0L) {
        viewModelScope.launch {
            val answer = if (newState == "connected") "v=0\na=setup:active\ns=A.R Chat Answer" else null
            communityAndCallsRepository.updateCallSessionState(callId, newState, durationSeconds, answer)
        }
    }

    fun updateProfile(displayName: String, about: String, mood: String, avatarData: String?) {
        viewModelScope.launch {
            authRepository.updateProfileDetails(displayName, about, mood, avatarData)
                .onSuccess { _statusBannerMessage.value = "Profile updated" }
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Profile update failed" }
        }
    }

    fun updatePrivacy(lastSeen: String, photo: String, about: String, readReceipts: Boolean, disappearingSec: Long) {
        viewModelScope.launch {
            authRepository.updatePrivacyPreferences(lastSeen, photo, about, readReceipts, disappearingSec)
                .onSuccess { _statusBannerMessage.value = "Privacy settings saved" }
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Privacy update failed" }
        }
    }

    fun updatePhoneNumber(countryCode: String, phoneNumber: String, carrierName: String) {
        viewModelScope.launch {
            authRepository.updatePhoneNumber(countryCode, phoneNumber, carrierName)
                .onSuccess { _statusBannerMessage.value = "Phone number updated" }
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Phone update failed" }
        }
    }

    fun rotateIdentityKeys() {
        viewModelScope.launch {
            authRepository.rotateEncryptionIdentityKey()
                .onSuccess { fp -> _statusBannerMessage.value = "Identity key rotated: $fp" }
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Key rotation failed" }
        }
    }

    fun linkNewDevice(deviceName: String, platform: String) {
        viewModelScope.launch {
            authRepository.linkNewDeviceSession(deviceName, platform)
                .onSuccess { _statusBannerMessage.value = "Device linked" }
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Device link failed" }
        }
    }

    fun revokeLinkedDevice(deviceId: String) {
        viewModelScope.launch {
            authRepository.revokeLinkedDevice(deviceId)
                .onSuccess { _statusBannerMessage.value = "Device session revoked" }
        }
    }

    fun createEncryptedBackup(label: String, passphrase: String) {
        viewModelScope.launch {
            val chatCount = (chatsState.value as? UiState.Success)?.data?.size ?: 0
            val payload = """{"uid":"$currentUserId","chats":$chatCount,"exportedAt":${System.currentTimeMillis()}}"""
            authRepository.createEncryptedBackup(label, payload, passphrase)
                .onSuccess { _statusBannerMessage.value = "Encrypted backup uploaded" }
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Backup failed" }
        }
    }

    fun submitReportOrSupport(targetType: String, targetId: String, reason: String) {
        viewModelScope.launch {
            communityAndCallsRepository.submitReportOrSupport(targetType, targetId, reason)
                .onSuccess { _statusBannerMessage.value = "Submitted to A.R Chat Moderation & Support" }
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Submission failed" }
        }
    }

    fun updateLanguageAndTheme(lang: String, themeMode: String) {
        viewModelScope.launch {
            authRepository.updateLanguageAndTheme(lang, themeMode)
        }
    }

    private val _starredMessagesState = MutableStateFlow<UiState<List<Pair<Chat, ChatMessage>>>>(UiState.Success(emptyList()))
    val starredMessagesState: StateFlow<UiState<List<Pair<Chat, ChatMessage>>>> = _starredMessagesState.asStateFlow()

    fun refreshStarredMessages() {
        val chats = (chatsState.value as? UiState.Success)?.data.orEmpty()
        viewModelScope.launch {
            _starredMessagesState.value = UiState.Loading
            chatRepository.fetchStarredMessages(chats)
                .onSuccess { _starredMessagesState.value = UiState.Success(it) }
                .onFailure { _starredMessagesState.value = UiState.Error(it.localizedMessage ?: "Failed to load starred messages") }
        }
    }

    fun decryptBackup(backup: EncryptedBackup, passphrase: String, onResult: (Result<String>) -> Unit) {
        val blob = com.example.core.EncryptedBackupBlob(
            ciphertextBase64 = backup.ciphertextBase64,
            ivBase64 = backup.ivBase64,
            saltBase64 = backup.saltBase64,
            iterations = backup.kdfIterations.toInt()
        )
        onResult(cryptoManager.decryptBackup(blob, passphrase))
    }

    fun deleteAccountData(onDeleted: () -> Unit) {
        viewModelScope.launch {
            authRepository.deleteAccountRecord()
                .onSuccess { onDeleted() }
                .onFailure { _statusBannerMessage.value = it.localizedMessage ?: "Account deletion failed" }
        }
    }
}
