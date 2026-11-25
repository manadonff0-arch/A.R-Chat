package com.example.data.repository

import com.example.core.AppLockManager
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.AdminAuditLog
import com.example.data.model.AdminMember
import com.example.data.model.AppPublicConfig
import com.example.data.model.Channel
import com.example.data.model.Chat
import com.example.data.model.Community
import com.example.data.model.OwnerSecurityConfig
import com.example.data.model.PublicProfile
import com.example.data.model.ReportTicket
import com.example.data.model.UserPrivate
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

class OwnerRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    fun observePublicAppConfig(): Flow<AppPublicConfig?> = flow {
        if (auth.currentUser?.uid.isNullOrBlank()) return@flow
        val path = "app_config/public_status"
        emitAll(
            db.document(path)
                .snapshots()
                .map { snap ->
                    if (!snap.exists()) null
                    else snap.toObject(AppPublicConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                    throw error
                }
        )
    }

    suspend fun getOwnerSecurityConfig(): Result<OwnerSecurityConfig?> {
        val path = "app_config/owner_security"
        return try {
            val snap = db.document(path).get().await()
            val cfg = if (snap.exists()) {
                snap.toObject(OwnerSecurityConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null
            Result.success(cfg)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun provisionOwnerSecurityConfig(phoneNumber: String, pin: String): Result<Unit> {
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Sign in required"))
        val path = "app_config/owner_security"
        return try {
            require(pin.length >= 6) { "Owner 2FA PIN must be at least 6 characters." }
            val (hash, salt) = AppLockManager.hashOwnerPin(pin)
            db.document(path).set(
                mapOf(
                    "ownerEmail" to (user.email ?: ""),
                    "ownerPhoneNumber" to phoneNumber.trim(),
                    "pinHash" to hash,
                    "pinSalt" to salt,
                    "twoFactorEnabled" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAdminAction("OWNER_SECURITY_PROVISIONED", user.uid, "Owner 2FA security initialized")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun verifyOwnerCredentials(phoneNumber: String, pin: String): Result<Boolean> {
        val configRes = getOwnerSecurityConfig()
        val cfg = configRes.getOrElse { return Result.failure(it) }
            ?: return Result.failure(IllegalStateException("Owner security not provisioned yet."))
        val phoneMatch = cfg.ownerPhoneNumber.trim() == phoneNumber.trim()
        val pinMatch = AppLockManager.verifyOwnerPin(pin, cfg.pinHash, cfg.pinSalt)
        return Result.success(phoneMatch && pinMatch)
    }

    fun observeAllUsersPrivate(): Flow<List<UserPrivate>> = flow {
        val path = "users"
        emitAll(
            db.collection(path).snapshots()
                .map { it.toObjects(UserPrivate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
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

    fun observeAllPublicProfiles(): Flow<List<PublicProfile>> = flow {
        val path = "public_profiles"
        emitAll(
            db.collection(path).snapshots()
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

    fun observeAllReports(): Flow<List<ReportTicket>> = flow {
        val path = "reports"
        emitAll(
            db.collection(path).snapshots()
                .map { snap ->
                    snap.toObjects(ReportTicket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    fun observeAllChatsAdmin(): Flow<List<Chat>> = flow {
        val path = "chats"
        emitAll(
            db.collection(path).snapshots()
                .map { it.toObjects(Chat::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
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

    fun observeAllChannelsAdmin(): Flow<List<Channel>> = flow {
        val path = "channels"
        emitAll(
            db.collection(path).snapshots()
                .map { it.toObjects(Channel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
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

    fun observeAllCommunitiesAdmin(): Flow<List<Community>> = flow {
        val path = "communities"
        emitAll(
            db.collection(path).snapshots()
                .map { it.toObjects(Community::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
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

    fun observeAdmins(): Flow<List<AdminMember>> = flow {
        val path = "admins"
        emitAll(
            db.collection(path).snapshots()
                .map { it.toObjects(AdminMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
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

    fun observeAdminLogs(): Flow<List<AdminAuditLog>> = flow {
        val path = "admin_logs"
        emitAll(
            db.collection(path).snapshots()
                .map { snap ->
                    snap.toObjects(AdminAuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
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

    suspend fun setUserAccountStatus(targetUid: String, newStatus: String): Result<Unit> {
        return try {
            val batch = db.batch()
            batch.update(
                db.collection("users").document(targetUid),
                mapOf("accountStatus" to newStatus, "updatedAt" to FieldValue.serverTimestamp())
            )
            batch.update(
                db.collection("public_profiles").document(targetUid),
                mapOf("accountStatus" to newStatus, "updatedAt" to FieldValue.serverTimestamp())
            )
            batch.commit().await()
            logAdminAction("USER_STATUS_$newStatus", targetUid, "Account status updated to $newStatus")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$targetUid")
            Result.failure(e)
        }
    }

    suspend fun resolveReport(reportId: String, status: String, note: String): Result<Unit> {
        val path = "reports/$reportId"
        return try {
            db.document(path).update(
                mapOf(
                    "status" to status,
                    "resolutionNote" to note.trim().take(400),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAdminAction("REPORT_$status", reportId, note.take(120))
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePublicAppConfig(
        maintenanceMode: Boolean,
        maintenanceMessage: String,
        latestVersion: String,
        globalAnnouncement: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "app_config/public_status"
        return try {
            db.document(path).set(
                mapOf(
                    "maintenanceMode" to maintenanceMode,
                    "maintenanceMessage" to maintenanceMessage.trim().take(300),
                    "latestVersion" to latestVersion.trim().take(30),
                    "globalAnnouncement" to globalAnnouncement.trim().take(300),
                    "updatedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAdminAction("APP_CONFIG_UPDATE", "public_status", "maintenance=$maintenanceMode, version=$latestVersion")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun addAdmin(targetUid: String, email: String): Result<Unit> {
        val ownerUid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "admins/$targetUid"
        return try {
            db.document(path).set(
                mapOf(
                    "uid" to targetUid.trim(),
                    "email" to email.trim(),
                    "role" to "ADMIN",
                    "addedByUid" to ownerUid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAdminAction("ADMIN_ADDED", targetUid, "Added admin $email")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun removeAdmin(targetUid: String): Result<Unit> {
        val path = "admins/$targetUid"
        return try {
            db.document(path).delete().await()
            logAdminAction("ADMIN_REMOVED", targetUid, "Removed administrator access")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleChannelActive(channelId: String, active: Boolean): Result<Unit> {
        val path = "channels/$channelId"
        return try {
            db.document(path).update(
                mapOf("active" to active, "updatedAt" to FieldValue.serverTimestamp())
            ).await()
            logAdminAction("CHANNEL_STATUS", channelId, "active=$active")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    private suspend fun logAdminAction(action: String, targetId: String, details: String) {
        val user = auth.currentUser ?: return
        try {
            db.collection("admin_logs").document().set(
                mapOf(
                    "actorUid" to user.uid,
                    "actorEmail" to (user.email ?: "unknown"),
                    "action" to action,
                    "targetId" to targetId,
                    "details" to details.take(300),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (_: Exception) {
            // Best-effort audit log
        }
    }
}
