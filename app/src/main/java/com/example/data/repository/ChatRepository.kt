package com.example.data.repository

import com.example.core.E2eeCryptoManager
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.Chat
import com.example.data.model.ChatMessage
import com.example.data.model.PublicProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ChatRepository(
    private val db: FirebaseFirestore,
    private val cryptoManager: E2eeCryptoManager,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in.")

    fun observeActivePublicProfiles(): Flow<List<PublicProfile>> = flow {
        if (auth.currentUser?.uid.isNullOrBlank()) return@flow
        val path = "public_profiles"
        emitAll(
            db.collection(path)
                .whereEqualTo("accountStatus", "active")
                .snapshots()
                .map { it.toObjects(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeChats(uid: String): Flow<List<Chat>> = flow {
        if (uid.isBlank()) return@flow
        val path = "chats"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Chat::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.lastMessageAt ?: it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMessages(chatId: String, uid: String): Flow<List<ChatMessage>> = flow {
        if (chatId.isBlank() || uid.isBlank()) return@flow
        val path = "chats/$chatId/messages"
        emitAll(
            db.collection("chats").document(chatId).collection("messages")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createOrOpenDirectChat(
        peerProfile: PublicProfile,
        myDisplayName: String
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val chatId = if (uid == peerProfile.userId) {
            "self_${uid}"
        } else {
            val ordered = listOf(uid, peerProfile.userId).sorted()
            "dm_${ordered[0].take(20)}_${ordered[1].take(20)}"
        }
        val path = "chats/$chatId"
        return try {
            val ref = db.collection("chats").document(chatId)
            val snap = ref.get().await()
            if (!snap.exists()) {
                val members = if (uid == peerProfile.userId) listOf(uid) else listOf(uid, peerProfile.userId)
                val title = if (uid == peerProfile.userId) "$myDisplayName (You)" else peerProfile.displayName
                ref.set(
                    mapOf(
                        "chatType" to "direct",
                        "title" to title.take(100),
                        "ownerUid" to uid,
                        "memberUids" to members,
                        "adminUids" to listOf(uid),
                        "lastMessagePreview" to "Encrypted chat created",
                        "lastMessageSenderUid" to uid,
                        "lastMessageType" to "system",
                        "lastMessageEncrypted" to peerProfile.ecdhPublicKey.isNotBlank(),
                        "lastMessageAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(chatId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun createGroupOrBroadcastChat(
        chatType: String,
        title: String,
        description: String,
        memberUids: List<String>
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanTitle = title.trim().take(100)
        return try {
            require(cleanTitle.isNotEmpty()) { "Title is required" }
            val allMembers = (memberUids + uid).distinct().take(50)
            val docRef = db.collection("chats").document()
            val invite = UUID.randomUUID().toString().take(8).uppercase()
            val payload = mapOf(
                "chatType" to chatType,
                "title" to cleanTitle,
                "description" to description.trim().take(300),
                "ownerUid" to uid,
                "memberUids" to allMembers,
                "adminUids" to listOf(uid),
                "lastMessagePreview" to if (chatType == "broadcast") "Broadcast list created" else "Group created",
                "lastMessageSenderUid" to uid,
                "lastMessageType" to "system",
                "lastMessageEncrypted" to false,
                "inviteCode" to invite,
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "chats")
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
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
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "chats/${chat.id}/messages"
        return try {
            val cleanText = plaintext.trim().take(4000)
            require(cleanText.isNotEmpty() || attachmentDataBase64.isNotEmpty()) { "Message cannot be empty" }

            val canE2ee = chat.chatType == "direct" && !peerPublicKeyBase64.isNullOrBlank()
            val encryptedPayload = if (canE2ee && cleanText.isNotEmpty()) {
                cryptoManager.encryptForPeer(uid, peerPublicKeyBase64!!, cleanText)
            } else null

            val msgRef = db.collection("chats").document(chat.id).collection("messages").document()
            val msgMap = mutableMapOf<String, Any>(
                "chatId" to chat.id,
                "senderUid" to uid,
                "senderName" to senderName.take(60),
                "memberUids" to chat.memberUids.take(50),
                "messageType" to messageType,
                "encrypted" to (encryptedPayload != null),
                "deliveryStatus" to "sent",
                "forwarded" to forwarded,
                "edited" to false,
                "deletedForAll" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (encryptedPayload != null) {
                msgMap["ciphertextBase64"] = encryptedPayload.ciphertextBase64
                msgMap["ivBase64"] = encryptedPayload.ivBase64
                msgMap["senderKeyFingerprint"] = encryptedPayload.senderKeyFingerprint
            } else {
                msgMap["plaintextFallback"] = cleanText
            }
            if (attachmentMeta.isNotBlank()) msgMap["attachmentMeta"] = attachmentMeta.take(500)
            if (attachmentDataBase64.isNotBlank()) msgMap["attachmentDataBase64"] = attachmentDataBase64.take(262144)
            if (replyToMessageId.isNotBlank()) msgMap["replyToMessageId"] = replyToMessageId.take(128)
            if (replyToPreview.isNotBlank()) msgMap["replyToPreview"] = replyToPreview.take(200)

            val previewLabel = when {
                encryptedPayload != null -> "Encrypted message"
                messageType == "voice" -> "Voice message"
                messageType == "image" -> "Photo"
                messageType == "document" -> "Document: ${attachmentMeta.take(40)}"
                messageType == "location" -> "Shared location"
                else -> cleanText.take(120)
            }

            val batch = db.batch()
            batch.set(msgRef, msgMap)
            batch.update(
                db.collection("chats").document(chat.id),
                mapOf(
                    "lastMessagePreview" to previewLabel.take(200),
                    "lastMessageSenderUid" to uid,
                    "lastMessageType" to messageType,
                    "lastMessageEncrypted" to (encryptedPayload != null),
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun editMessage(
        chat: Chat,
        message: ChatMessage,
        newText: String,
        peerPublicKeyBase64: String?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "chats/${chat.id}/messages/${message.id}"
        return try {
            require(message.senderUid == uid) { "Only sender can edit" }
            val clean = newText.trim().take(4000)
            require(clean.isNotEmpty()) { "Message cannot be empty" }
            val updates = mutableMapOf<String, Any>(
                "edited" to true,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (message.encrypted && !peerPublicKeyBase64.isNullOrBlank()) {
                val enc = cryptoManager.encryptForPeer(uid, peerPublicKeyBase64, clean)
                updates["ciphertextBase64"] = enc.ciphertextBase64
                updates["ivBase64"] = enc.ivBase64
            } else {
                updates["plaintextFallback"] = clean
            }
            db.document(path).update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteMessageForAll(chatId: String, messageId: String): Result<Unit> {
        val path = "chats/$chatId/messages/$messageId"
        return try {
            requireUserId()
            db.document(path).update(
                mapOf(
                    "deletedForAll" to true,
                    "plaintextFallback" to "This message was deleted",
                    "ciphertextBase64" to "",
                    "ivBase64" to "",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleStarMessage(chatId: String, message: ChatMessage): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "chats/$chatId/messages/${message.id}"
        return try {
            val next = if (uid in message.starredByUids) {
                message.starredByUids - uid
            } else {
                (message.starredByUids + uid).distinct().take(50)
            }
            db.document(path).update(
                mapOf(
                    "starredByUids" to next,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markMessagesAsRead(chatId: String, messages: List<ChatMessage>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val unread = messages.filter { it.senderUid != uid && it.deliveryStatus != "read" }
        if (unread.isEmpty()) return Result.success(Unit)
        return try {
            val batch = db.batch()
            unread.take(25).forEach { msg ->
                batch.update(
                    db.collection("chats").document(chatId).collection("messages").document(msg.id),
                    mapOf(
                        "deliveryStatus" to "read",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chats/$chatId/messages")
            Result.failure(e)
        }
    }

    suspend fun pinMessage(chatId: String, messageId: String): Result<Unit> {
        val path = "chats/$chatId"
        return try {
            requireUserId()
            db.document(path).update(
                mapOf(
                    "pinnedMessageId" to messageId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateGroupManagement(
        chatId: String,
        title: String,
        description: String,
        memberUids: List<String>,
        adminUids: List<String>,
        disappearingSeconds: Long
    ): Result<Unit> {
        val path = "chats/$chatId"
        return try {
            requireUserId()
            db.document(path).update(
                mapOf(
                    "title" to title.trim().take(100),
                    "description" to description.trim().take(300),
                    "memberUids" to memberUids.distinct().take(50),
                    "adminUids" to adminUids.distinct().take(20),
                    "disappearingSeconds" to disappearingSeconds,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun leaveChat(chat: Chat): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "chats/${chat.id}"
        return try {
            val nextMembers = chat.memberUids - uid
            if (nextMembers.isEmpty()) {
                db.document(path).delete().await()
            } else {
                db.document(path).update(
                    mapOf(
                        "memberUids" to nextMembers,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun fetchStarredMessages(chats: List<Chat>): Result<List<Pair<Chat, ChatMessage>>> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val results = mutableListOf<Pair<Chat, ChatMessage>>()
            for (chat in chats.take(25)) {
                val snap = db.collection("chats").document(chat.id).collection("messages")
                    .whereArrayContains("memberUids", uid)
                    .get()
                    .await()
                val msgs = snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { uid in it.starredByUids && !it.deletedForAll }
                msgs.forEach { results.add(chat to it) }
            }
            Result.success(results.sortedByDescending { it.second.createdAt?.toDate()?.time ?: 0L })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "chats/starred")
            Result.failure(e)
        }
    }

    suspend fun markAllChatsRead(chats: List<Chat>): Result<Int> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            var count = 0
            for (chat in chats.take(20)) {
                val snap = db.collection("chats").document(chat.id).collection("messages")
                    .whereArrayContains("memberUids", uid)
                    .get()
                    .await()
                val unread = snap.toObjects(ChatMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .filter { it.senderUid != uid && it.deliveryStatus != "read" }
                if (unread.isNotEmpty()) {
                    val batch = db.batch()
                    unread.take(20).forEach { msg ->
                        batch.update(
                            db.collection("chats").document(chat.id).collection("messages").document(msg.id),
                            mapOf("deliveryStatus" to "read", "updatedAt" to FieldValue.serverTimestamp())
                        )
                    }
                    batch.commit().await()
                    count += unread.size
                }
            }
            Result.success(count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "chats/read_all")
            Result.failure(e)
        }
    }
}
