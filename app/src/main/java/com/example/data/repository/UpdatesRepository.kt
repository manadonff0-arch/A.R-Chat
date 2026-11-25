package com.example.data.repository

import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.Channel
import com.example.data.model.ChannelPost
import com.example.data.model.StatusUpdate
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

class UpdatesRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    fun observeOwnStatuses(uid: String): Flow<List<StatusUpdate>> = flow {
        if (uid.isBlank()) return@flow
        val path = "statuses"
        emitAll(
            db.collection(path)
                .whereEqualTo("ownerUid", uid)
                .snapshots()
                .map { snap ->
                    val now = System.currentTimeMillis()
                    snap.toObjects(StatusUpdate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.expiresAtMillis > now }
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
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

    fun observeContactsStatuses(): Flow<List<StatusUpdate>> = flow {
        if (auth.currentUser?.uid.isNullOrBlank()) return@flow
        val path = "statuses"
        emitAll(
            db.collection(path)
                .whereEqualTo("privacyMode", "contacts")
                .snapshots()
                .map { snap ->
                    val now = System.currentTimeMillis()
                    snap.toObjects(StatusUpdate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.expiresAtMillis > now }
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
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

    suspend fun createStatus(
        ownerName: String,
        statusType: String,
        textContent: String,
        backgroundHex: String,
        mediaDataBase64: String,
        privacyMode: String,
        allowedViewerUids: List<String> = emptyList()
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "statuses"
        return try {
            val expiresAt = System.currentTimeMillis() + 24L * 60L * 60L * 1000L
            val payload = mutableMapOf<String, Any>(
                "ownerUid" to uid,
                "ownerName" to ownerName.trim().take(60),
                "statusType" to statusType,
                "privacyMode" to privacyMode,
                "allowedViewerUids" to allowedViewerUids.take(50),
                "viewerUids" to emptyList<String>(),
                "expiresAtMillis" to expiresAt,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (textContent.isNotBlank()) payload["textContent"] = textContent.trim().take(1000)
            if (backgroundHex.isNotBlank()) payload["backgroundHex"] = backgroundHex.take(16)
            if (mediaDataBase64.isNotBlank()) payload["mediaDataBase64"] = mediaDataBase64.take(196608)

            db.collection(path).document().set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun markStatusViewed(status: StatusUpdate): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        if (status.ownerUid == uid || uid in status.viewerUids) return Result.success(Unit)
        val path = "statuses/${status.id}"
        return try {
            db.document(path).update(
                mapOf(
                    "viewerUids" to (status.viewerUids + uid).distinct().take(100),
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

    suspend fun deleteStatus(statusId: String): Result<Unit> {
        val path = "statuses/$statusId"
        return try {
            db.document(path).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeChannels(): Flow<List<Channel>> = flow {
        if (auth.currentUser?.uid.isNullOrBlank()) return@flow
        val path = "channels"
        emitAll(
            db.collection(path)
                .whereEqualTo("active", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Channel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.followerCount }
                }
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

    suspend fun createChannel(name: String, description: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "channels"
        return try {
            require(name.trim().isNotEmpty()) { "Channel name required" }
            db.collection(path).document().set(
                mapOf(
                    "ownerUid" to uid,
                    "name" to name.trim().take(80),
                    "description" to description.trim().take(400),
                    "followerUids" to listOf(uid),
                    "followerCount" to 1L,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleFollowChannel(channel: Channel): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "channels/${channel.id}"
        return try {
            val isFollowing = uid in channel.followerUids
            val nextFollowers = if (isFollowing) channel.followerUids - uid else (channel.followerUids + uid).distinct().take(200)
            db.document(path).update(
                mapOf(
                    "followerUids" to nextFollowers,
                    "followerCount" to nextFollowers.size.toLong(),
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

    fun observeChannelPosts(channelId: String): Flow<List<ChannelPost>> = flow {
        if (channelId.isBlank() || auth.currentUser?.uid.isNullOrBlank()) return@flow
        val path = "channels/$channelId/posts"
        emitAll(
            db.collection("channels").document(channelId).collection("posts")
                .whereEqualTo("channelId", channelId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChannelPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
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

    suspend fun publishChannelPost(channelId: String, postType: String, content: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "channels/$channelId/posts"
        return try {
            require(content.trim().isNotEmpty()) { "Post content cannot be empty" }
            db.collection("channels").document(channelId).collection("posts").document().set(
                mapOf(
                    "channelId" to channelId,
                    "authorUid" to uid,
                    "postType" to postType,
                    "content" to content.trim().take(2000),
                    "reactionsCount" to 0L,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun reactToChannelPost(channelId: String, postId: String): Result<Unit> {
        val path = "channels/$channelId/posts/$postId"
        return try {
            db.document(path).update(
                mapOf(
                    "reactionsCount" to FieldValue.increment(1L),
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
}
