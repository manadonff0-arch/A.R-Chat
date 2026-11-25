package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class PublicProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val username: String = "",
    val about: String = "Available on A.R Chat",
    val mood: String = "Available",
    val carrierName: String = "Jazz",
    val avatarData: String = "",
    val online: Boolean = true,
    val privacyLastSeen: String = "everyone",
    val privacyPhoto: String = "everyone",
    val privacyAbout: String = "everyone",
    val readReceiptsEnabled: Boolean = true,
    val ecdhPublicKey: String = "",
    val keyFingerprint: String = "",
    val accountStatus: String = "active",
    @ServerTimestamp val lastSeenAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class UserPrivate(
    @DocumentId val id: String = "",
    val userId: String = "",
    val phoneNumber: String = "",
    val countryCode: String = "+92",
    val carrierName: String = "Jazz",
    val phoneVerifiedStatus: String = "format_verified",
    val email: String = "",
    val emailVerified: Boolean = false,
    val displayName: String = "",
    val username: String = "",
    val accountStatus: String = "active",
    val disappearingDefaultSeconds: Long = 0L,
    val languageCode: String = "en",
    val themeMode: String = "dark",
    val twoFactorEnabled: Boolean = false,
    val archivedChatIds: List<String> = emptyList(),
    val pinnedChatIds: List<String> = emptyList(),
    val mutedChatIds: List<String> = emptyList(),
    val favoriteChatIds: List<String> = emptyList(),
    val blockedUserIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LinkedDevice(
    @DocumentId val id: String = "",
    val deviceName: String = "",
    val platform: String = "Android",
    val sessionHash: String = "",
    val active: Boolean = true,
    @ServerTimestamp val lastActiveAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class EncryptedBackup(
    @DocumentId val id: String = "",
    val label: String = "",
    val ciphertextBase64: String = "",
    val ivBase64: String = "",
    val saltBase64: String = "",
    val kdfIterations: Long = 100000L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Chat(
    @DocumentId val id: String = "",
    val chatType: String = "direct",
    val title: String = "",
    val description: String = "",
    val ownerUid: String = "",
    val memberUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val lastMessagePreview: String = "",
    val lastMessageSenderUid: String = "",
    val lastMessageType: String = "text",
    val lastMessageEncrypted: Boolean = false,
    val pinnedMessageId: String = "",
    val disappearingSeconds: Long = 0L,
    val inviteCode: String = "",
    @ServerTimestamp val lastMessageAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val memberUids: List<String> = emptyList(),
    val messageType: String = "text",
    val encrypted: Boolean = false,
    val ciphertextBase64: String = "",
    val ivBase64: String = "",
    val senderKeyFingerprint: String = "",
    val plaintextFallback: String = "",
    val attachmentMeta: String = "",
    val attachmentDataBase64: String = "",
    val replyToMessageId: String = "",
    val replyToPreview: String = "",
    val forwarded: Boolean = false,
    val deliveryStatus: String = "sent",
    val starredByUids: List<String> = emptyList(),
    val edited: Boolean = false,
    val deletedForAll: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class StatusUpdate(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val ownerName: String = "",
    val statusType: String = "text",
    val textContent: String = "",
    val backgroundHex: String = "#073022",
    val mediaDataBase64: String = "",
    val privacyMode: String = "contacts",
    val allowedViewerUids: List<String> = emptyList(),
    val viewerUids: List<String> = emptyList(),
    val expiresAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Channel(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val name: String = "",
    val description: String = "",
    val followerUids: List<String> = emptyList(),
    val followerCount: Long = 0L,
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ChannelPost(
    @DocumentId val id: String = "",
    val channelId: String = "",
    val authorUid: String = "",
    val postType: String = "text",
    val content: String = "",
    val reactionsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Community(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val name: String = "",
    val description: String = "",
    val memberUids: List<String> = emptyList(),
    val adminUids: List<String> = emptyList(),
    val linkedChatIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CallSession(
    @DocumentId val id: String = "",
    val callerUid: String = "",
    val callerName: String = "",
    val calleeUid: String = "",
    val calleeName: String = "",
    val participantUids: List<String> = emptyList(),
    val callType: String = "voice",
    val callState: String = "ringing",
    val offerSdp: String = "",
    val answerSdp: String = "",
    val durationSeconds: Long = 0L,
    val networkQuality: String = "good",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ReportTicket(
    @DocumentId val id: String = "",
    val reporterUid: String = "",
    val targetType: String = "support",
    val targetId: String = "",
    val reason: String = "",
    val status: String = "open",
    val resolutionNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdminMember(
    @DocumentId val id: String = "",
    val uid: String = "",
    val email: String = "",
    val role: String = "ADMIN",
    val addedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppPublicConfig(
    @DocumentId val id: String = "",
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String = "",
    val latestVersion: String = "1.0.0",
    val globalAnnouncement: String = "",
    val updatedByUid: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class OwnerSecurityConfig(
    @DocumentId val id: String = "",
    val ownerEmail: String = "",
    val ownerPhoneNumber: String = "",
    val pinHash: String = "",
    val pinSalt: String = "",
    val twoFactorEnabled: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AdminAuditLog(
    @DocumentId val id: String = "",
    val actorUid: String = "",
    val actorEmail: String = "",
    val action: String = "",
    val targetId: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
