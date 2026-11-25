package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.core.E2eeCryptoManager
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.EncryptedBackup
import com.example.data.model.LinkedDevice
import com.example.data.model.PublicProfile
import com.example.data.model.UserPrivate
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class AuthRepository(
    private val db: FirebaseFirestore,
    private val cryptoManager: E2eeCryptoManager,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in with Google.")

    fun attemptAutoSignIn(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onUnauthenticated: () -> Unit,
        scope: CoroutineScope
    ) {
        if (auth.currentUser != null) {
            onAuthSuccess()
            return
        }
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onUnauthenticated()
            return
        }
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val cred = credentialManager.getCredential(context, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    onAuthSuccess()
                } else {
                    onUnauthenticated()
                }
            } catch (_: Exception) {
                onUnauthenticated()
            }
        }
    }

    fun onGoogleSignInClicked(
        context: Context,
        credentialManager: CredentialManager,
        onAuthSuccess: () -> Unit,
        onAuthError: (String) -> Unit,
        scope: CoroutineScope,
        onAuthCancelled: () -> Unit = {}
    ) {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
            return
        }
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch {
            try {
                val activity = context as? Activity
                    ?: run {
                        onAuthError("Activity context required for Google Sign-In")
                        return@launch
                    }
                val cred = credentialManager.getCredential(activity, request).credential
                if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                    auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                    onAuthSuccess()
                } else {
                    onAuthError("Unexpected credential type")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                onAuthCancelled()
            } catch (e: Exception) {
                Log.e("Auth", "Google Sign-In failed", e)
                onAuthError(e.localizedMessage ?: "Sign in failed")
            }
        }
    }

    fun signOut(
        credentialManager: CredentialManager,
        scope: CoroutineScope,
        onSignOutComplete: () -> Unit = {}
    ) {
        scope.launch {
            withContext(NonCancellable) {
                try {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (e: Exception) {
                    Log.w("Auth", "Failed to clear credential state: ${e.message}")
                } finally {
                    auth.signOut()
                    onSignOutComplete()
                }
            }
        }
    }

    fun observePrivateProfile(uid: String): Flow<UserPrivate?> = flow {
        if (uid.isBlank()) return@flow
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap ->
                    if (!snap.exists()) null
                    else snap.toObject(UserPrivate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observePublicProfile(uid: String): Flow<PublicProfile?> = flow {
        if (uid.isBlank()) return@flow
        val path = "public_profiles/$uid"
        emitAll(
            db.collection("public_profiles").document(uid)
                .snapshots()
                .map { snap ->
                    if (!snap.exists()) null
                    else snap.toObject(PublicProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(fsErr ?: error, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeLinkedDevices(uid: String): Flow<List<LinkedDevice>> = flow {
        if (uid.isBlank()) return@flow
        val path = "users/$uid/linked_devices"
        emitAll(
            db.collection("users").document(uid).collection("linked_devices")
                .snapshots()
                .map { it.toObjects(LinkedDevice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
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

    fun observeBackups(uid: String): Flow<List<EncryptedBackup>> = flow {
        if (uid.isBlank()) return@flow
        val path = "users/$uid/backups"
        emitAll(
            db.collection("users").document(uid).collection("backups")
                .snapshots()
                .map { it.toObjects(EncryptedBackup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
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

    suspend fun saveUserProfileSetup(
        phoneNumber: String,
        countryCode: String,
        carrierName: String,
        displayName: String,
        username: String,
        about: String,
        avatarData: String,
        email: String,
        languageCode: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanName = displayName.trim().take(60)
        val cleanUsername = username.trim().lowercase().replace(" ", "_").take(35)
        val cleanAbout = about.trim().take(200)
        return try {
            require(cleanName.isNotEmpty()) { "Display name cannot be empty" }
            require(cleanUsername.length >= 2) { "Username must be at least 2 characters" }
            val keyBundle = cryptoManager.ensureIdentityKeyPair(uid)
            val batch = db.batch()
            val privateRef = db.collection("users").document(uid)
            val publicRef = db.collection("public_profiles").document(uid)

            val existingPriv = privateRef.get().await()
            val privateMap = mutableMapOf<String, Any>(
                "userId" to uid,
                "phoneNumber" to phoneNumber.take(24),
                "countryCode" to countryCode.take(8),
                "carrierName" to carrierName.take(40),
                "phoneVerifiedStatus" to "google_and_format_verified",
                "email" to (email.ifBlank { auth.currentUser?.email ?: "" }).take(120),
                "emailVerified" to (auth.currentUser?.isEmailVerified ?: false),
                "displayName" to cleanName,
                "username" to cleanUsername,
                "languageCode" to languageCode,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!existingPriv.exists()) {
                privateMap["accountStatus"] = "active"
                privateMap["themeMode"] = "dark"
                privateMap["twoFactorEnabled"] = false
                privateMap["disappearingDefaultSeconds"] = 0L
                privateMap["createdAt"] = FieldValue.serverTimestamp()
                batch.set(privateRef, privateMap)
            } else {
                batch.update(privateRef, privateMap)
            }

            val existingPub = publicRef.get().await()
            val publicMap = mutableMapOf<String, Any>(
                "userId" to uid,
                "displayName" to cleanName,
                "username" to cleanUsername,
                "about" to cleanAbout,
                "carrierName" to carrierName.take(40),
                "online" to true,
                "ecdhPublicKey" to keyBundle.publicKeyBase64,
                "keyFingerprint" to keyBundle.keyFingerprint,
                "lastSeenAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (avatarData.isNotBlank()) {
                publicMap["avatarData"] = avatarData.take(65536)
            }
            if (!existingPub.exists()) {
                publicMap["accountStatus"] = "active"
                publicMap["mood"] = "Available"
                publicMap["privacyLastSeen"] = "everyone"
                publicMap["privacyPhoto"] = "everyone"
                publicMap["privacyAbout"] = "everyone"
                publicMap["readReceiptsEnabled"] = true
                publicMap["createdAt"] = FieldValue.serverTimestamp()
                batch.set(publicRef, publicMap)
            } else {
                batch.update(publicRef, publicMap)
            }

            batch.commit().await()
            registerCurrentDevice(uid)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$uid")
            Result.failure(e)
        }
    }

    private suspend fun registerCurrentDevice(uid: String) {
        try {
            val rawId = "${Build.MANUFACTURER}_${Build.MODEL}_${uid}"
            val digest = MessageDigest.getInstance("SHA-256").digest(rawId.toByteArray())
            val deviceId = digest.take(12).joinToString("") { "%02x".format(it) }
            val ref = db.collection("users").document(uid).collection("linked_devices").document(deviceId)
            val snap = ref.get().await()
            if (!snap.exists()) {
                ref.set(
                    mapOf(
                        "deviceName" to "${Build.MANUFACTURER} ${Build.MODEL}".take(80),
                        "platform" to "Android ${Build.VERSION.RELEASE}".take(40),
                        "sessionHash" to deviceId,
                        "active" to true,
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.update(
                    mapOf(
                        "active" to true,
                        "lastActiveAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (_: Exception) {
            // Non-fatal session telemetry
        }
    }

    suspend fun linkNewDeviceSession(deviceName: String, platform: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/linked_devices"
        return try {
            val docRef = db.collection("users").document(uid).collection("linked_devices").document()
            docRef.set(
                mapOf(
                    "deviceName" to deviceName.trim().take(80),
                    "platform" to platform.trim().take(40),
                    "sessionHash" to docRef.id,
                    "active" to true,
                    "lastActiveAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp()
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

    suspend fun revokeLinkedDevice(deviceId: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/linked_devices/$deviceId"
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

    suspend fun updateProfileDetails(
        displayName: String,
        about: String,
        mood: String,
        avatarData: String?
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val batch = db.batch()
            val pubUpdates = mutableMapOf<String, Any>(
                "displayName" to displayName.trim().take(60),
                "about" to about.trim().take(200),
                "mood" to mood.trim().take(60),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!avatarData.isNullOrBlank()) {
                pubUpdates["avatarData"] = avatarData.take(65536)
            }
            batch.update(db.collection("public_profiles").document(uid), pubUpdates)
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "displayName" to displayName.trim().take(60),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "public_profiles/$uid")
            Result.failure(e)
        }
    }

    suspend fun updatePrivacyPreferences(
        lastSeen: String,
        photo: String,
        about: String,
        readReceipts: Boolean,
        disappearingDefaultSeconds: Long
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val batch = db.batch()
            batch.update(
                db.collection("public_profiles").document(uid),
                mapOf(
                    "privacyLastSeen" to lastSeen,
                    "privacyPhoto" to photo,
                    "privacyAbout" to about,
                    "readReceiptsEnabled" to readReceipts,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "disappearingDefaultSeconds" to disappearingDefaultSeconds,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun updateUserListField(field: String, updatedList: List<String>): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    field to updatedList,
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

    suspend fun updateLanguageAndTheme(languageCode: String, themeMode: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "languageCode" to languageCode,
                    "themeMode" to themeMode,
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

    suspend fun updatePhoneNumber(countryCode: String, phoneNumber: String, carrierName: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val batch = db.batch()
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "countryCode" to countryCode,
                    "phoneNumber" to phoneNumber,
                    "carrierName" to carrierName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("public_profiles").document(uid),
                mapOf(
                    "carrierName" to carrierName,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun rotateEncryptionIdentityKey(): Result<String> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val newBundle = cryptoManager.rotateIdentityKeyPair(uid)
            db.collection("public_profiles").document(uid).update(
                mapOf(
                    "ecdhPublicKey" to newBundle.publicKeyBase64,
                    "keyFingerprint" to newBundle.keyFingerprint,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(newBundle.keyFingerprint)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "public_profiles/$uid")
            Result.failure(e)
        }
    }

    suspend fun createEncryptedBackup(label: String, plaintextJson: String, passphrase: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid/backups"
        return try {
            val blob = cryptoManager.encryptBackup(plaintextJson, passphrase)
            db.collection("users").document(uid).collection("backups").document().set(
                mapOf(
                    "label" to label.trim().take(80),
                    "ciphertextBase64" to blob.ciphertextBase64,
                    "ivBase64" to blob.ivBase64,
                    "saltBase64" to blob.saltBase64,
                    "kdfIterations" to blob.iterations,
                    "createdAt" to FieldValue.serverTimestamp()
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

    suspend fun deleteAccountRecord(): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            db.collection("users").document(uid).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, "users/$uid")
            Result.failure(e)
        }
    }
}
