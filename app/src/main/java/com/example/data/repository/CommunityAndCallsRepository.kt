package com.example.data.repository

import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.CallSession
import com.example.data.model.Community
import com.example.data.model.ReportTicket
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

class CommunityAndCallsRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    fun observeCommunities(uid: String): Flow<List<Community>> = flow {
        if (uid.isBlank()) return@flow
        val path = "communities"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(Community::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L }
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

    suspend fun createCommunity(
        name: String,
        description: String,
        memberUids: List<String>,
        linkedChatIds: List<String>
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "communities"
        return try {
            val cleanName = name.trim().take(80)
            require(cleanName.isNotEmpty()) { "Community name required" }

            // Create announcement group automatically
            val announcementChatRef = db.collection("chats").document()
            val allMembers = (memberUids + uid).distinct().take(100)
            val batch = db.batch()
            batch.set(
                announcementChatRef,
                mapOf(
                    "chatType" to "group",
                    "title" to "$cleanName Announcements".take(100),
                    "description" to "Community announcement channel",
                    "ownerUid" to uid,
                    "memberUids" to allMembers.take(50),
                    "adminUids" to listOf(uid),
                    "lastMessagePreview" to "Welcome to $cleanName community",
                    "lastMessageSenderUid" to uid,
                    "lastMessageType" to "system",
                    "lastMessageEncrypted" to false,
                    "inviteCode" to UUID.randomUUID().toString().take(8).uppercase(),
                    "lastMessageAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val commRef = db.collection(path).document()
            batch.set(
                commRef,
                mapOf(
                    "ownerUid" to uid,
                    "name" to cleanName,
                    "description" to description.trim().take(400),
                    "memberUids" to allMembers,
                    "adminUids" to listOf(uid),
                    "linkedChatIds" to (linkedChatIds + announcementChatRef.id).distinct().take(20),
                    "createdAt" to FieldValue.serverTimestamp(),
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

    suspend fun updateCommunityMembers(
        communityId: String,
        memberUids: List<String>,
        linkedChatIds: List<String>
    ): Result<Unit> {
        val path = "communities/$communityId"
        return try {
            db.document(path).update(
                mapOf(
                    "memberUids" to memberUids.distinct().take(100),
                    "linkedChatIds" to linkedChatIds.distinct().take(20),
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

    fun observeCalls(uid: String): Flow<List<CallSession>> = flow {
        if (uid.isBlank()) return@flow
        val path = "calls"
        emitAll(
            db.collection(path)
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(CallSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun startCallSession(
        callerName: String,
        calleeUid: String,
        calleeName: String,
        participantUids: List<String>,
        callType: String
    ): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "calls"
        return try {
            val docRef = db.collection(path).document()
            val sdpOffer = "v=0\no=archat-${docRef.id.take(8)} 1 1 IN IP4 0.0.0.0\ns=A.R Chat WebRTC\nt=0 0\nm=${if (callType.contains("video")) "video" else "audio"} 9 UDP/TLS/RTP/SAVPF 111\na=setup:actpass\na=fingerprint:sha-256 ${UUID.randomUUID()}"
            docRef.set(
                mapOf(
                    "callerUid" to uid,
                    "callerName" to callerName.trim().take(60),
                    "calleeUid" to calleeUid.take(128),
                    "calleeName" to calleeName.trim().take(60),
                    "participantUids" to (participantUids + uid).distinct().take(8),
                    "callType" to callType,
                    "callState" to "ringing",
                    "offerSdp" to sdpOffer,
                    "durationSeconds" to 0L,
                    "networkQuality" to "good",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateCallSessionState(
        callId: String,
        newState: String,
        durationSeconds: Long = 0L,
        answerSdp: String? = null
    ): Result<Unit> {
        val path = "calls/$callId"
        return try {
            val updates = mutableMapOf<String, Any>(
                "callState" to newState,
                "durationSeconds" to durationSeconds,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!answerSdp.isNullOrBlank()) {
                updates["answerSdp"] = answerSdp.take(16384)
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

    fun observeMyReports(uid: String): Flow<List<ReportTicket>> = flow {
        if (uid.isBlank()) return@flow
        val path = "reports"
        emitAll(
            db.collection(path)
                .whereEqualTo("reporterUid", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ReportTicket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun submitReportOrSupport(
        targetType: String,
        targetId: String,
        reason: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "reports"
        return try {
            val cleanReason = reason.trim().take(600)
            require(cleanReason.length >= 3) { "Please describe your request (at least 3 characters)." }
            db.collection(path).document().set(
                mapOf(
                    "reporterUid" to uid,
                    "targetType" to targetType,
                    "targetId" to targetId.ifBlank { uid }.take(128),
                    "reason" to cleanReason,
                    "status" to "open",
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
}
