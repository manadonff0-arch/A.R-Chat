package com.example.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class PublicKeyBundle(
    val publicKeyBase64: String,
    val keyFingerprint: String
)

data class EncryptedPayload(
    val ciphertextBase64: String,
    val ivBase64: String,
    val senderKeyFingerprint: String
)

data class EncryptedBackupBlob(
    val ciphertextBase64: String,
    val ivBase64: String,
    val saltBase64: String,
    val iterations: Int
)

/**
 * Hardware-backed Android Keystore wrapper + ECDH (P-256) + HKDF-SHA256 + AES-256-GCM
 * end-to-end encryption engine. Private keys are wrapped locally using an AndroidKeyStore
 * AES-256-GCM master key and never leave the device.
 */
class E2eeCryptoManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    fun ensureIdentityKeyPair(uid: String): PublicKeyBundle {
        val existingPub = prefs.getString("pub_$uid", null)
        val existingWrappedPriv = prefs.getString("priv_$uid", null)
        if (!existingPub.isNullOrBlank() && !existingWrappedPriv.isNullOrBlank()) {
            return PublicKeyBundle(existingPub, computeFingerprint(existingPub))
        }

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        val pair = kpg.generateKeyPair()
        val pubBase64 = Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)
        val privWrapped = wrapWithKeystore(uid, pair.private.encoded)

        prefs.edit()
            .putString("pub_$uid", pubBase64)
            .putString("priv_$uid", privWrapped)
            .apply()

        return PublicKeyBundle(pubBase64, computeFingerprint(pubBase64))
    }

    fun rotateIdentityKeyPair(uid: String): PublicKeyBundle {
        prefs.edit().remove("pub_$uid").remove("priv_$uid").apply()
        return ensureIdentityKeyPair(uid)
    }

    fun getPublicKeyBundle(uid: String): PublicKeyBundle = ensureIdentityKeyPair(uid)

    fun encryptForPeer(
        myUid: String,
        peerPublicKeyBase64: String,
        plaintext: String
    ): EncryptedPayload {
        val myBundle = ensureIdentityKeyPair(myUid)
        val aesKey = deriveSharedAesKey(myUid, peerPublicKeyBase64)
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return EncryptedPayload(
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            senderKeyFingerprint = myBundle.keyFingerprint
        )
    }

    fun decryptFromPeer(
        myUid: String,
        peerPublicKeyBase64: String,
        ciphertextBase64: String,
        ivBase64: String
    ): Result<String> = runCatching {
        val aesKey = deriveSharedAesKey(myUid, peerPublicKeyBase64)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    /**
     * Computes a deterministic 60-digit Safety Number from both parties' ECDH public keys.
     */
    fun computeSafetyNumber(myPublicKeyBase64: String, peerPublicKeyBase64: String): String {
        val ordered = listOf(myPublicKeyBase64, peerPublicKeyBase64).sorted()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((ordered[0] + "|" + ordered[1]).toByteArray(StandardCharsets.UTF_8))
        val digits = StringBuilder()
        var index = 0
        while (digits.length < 60) {
            val b0 = digest[index % digest.size].toInt() and 0xFF
            val b1 = digest[(index + 1) % digest.size].toInt() and 0xFF
            val block = ((b0 shl 8) or b1) % 100000
            digits.append(block.toString().padStart(5, '0'))
            index += 2
        }
        return digits.toString().take(60).chunked(5).joinToString(" ")
    }

    fun encryptBackup(plaintextJson: String, passphrase: String): EncryptedBackupBlob {
        require(passphrase.length >= 6) { "Backup passphrase must be at least 6 characters." }
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val iv = ByteArray(12).also { secureRandom.nextBytes(it) }
        val keyBytes = derivePbkdf2Key(passphrase, salt, BACKUP_KDF_ITERATIONS, 256)
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintextJson.toByteArray(StandardCharsets.UTF_8))
        return EncryptedBackupBlob(
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            saltBase64 = Base64.encodeToString(salt, Base64.NO_WRAP),
            iterations = BACKUP_KDF_ITERATIONS
        )
    }

    fun decryptBackup(blob: EncryptedBackupBlob, passphrase: String): Result<String> = runCatching {
        val salt = Base64.decode(blob.saltBase64, Base64.NO_WRAP)
        val iv = Base64.decode(blob.ivBase64, Base64.NO_WRAP)
        val ciphertext = Base64.decode(blob.ciphertextBase64, Base64.NO_WRAP)
        val keyBytes = derivePbkdf2Key(passphrase, salt, blob.iterations, 256)
        val keySpec = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec, GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    fun computeFingerprint(publicKeyBase64: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(Base64.decode(publicKeyBase64, Base64.NO_WRAP))
        return bytes.take(8).joinToString(":") { "%02X".format(it) }
    }

    private fun deriveSharedAesKey(myUid: String, peerPublicKeyBase64: String): SecretKeySpec {
        val privateKey = loadPrivateKey(myUid)
        val peerPublicKey = decodePublicKey(peerPublicKeyBase64)
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(peerPublicKey, true)
        val sharedSecret = agreement.generateSecret()

        // HKDF-SHA256 Extract & Expand
        val prk = hmacSha256(ByteArray(32), sharedSecret)
        val okm = hmacSha256(prk, "A.R-Chat-E2EE-v1".toByteArray(StandardCharsets.UTF_8) + byteArrayOf(0x01))
        return SecretKeySpec(okm.copyOf(32), "AES")
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun loadPrivateKey(uid: String): PrivateKey {
        val wrapped = prefs.getString("priv_$uid", null)
            ?: throw IllegalStateException("Local identity private key missing.")
        val rawBytes = unwrapWithKeystore(uid, wrapped)
        return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(rawBytes))
    }

    private fun decodePublicKey(base64: String): PublicKey {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    }

    private fun getOrCreateKeystoreMasterKey(uid: String): SecretKey {
        val alias = "ar_chat_master_$uid"
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.getKey(alias, null)?.let { return it as SecretKey }
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGen.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGen.generateKey()
    }

    private fun wrapWithKeystore(uid: String, plaintext: ByteArray): String {
        val masterKey = getOrCreateKeystoreMasterKey(uid)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey)
        val iv = cipher.iv
        val enc = cipher.doFinal(plaintext)
        val combined = byteArrayOf(iv.size.toByte()) + iv + enc
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun unwrapWithKeystore(uid: String, encoded: String): ByteArray {
        val masterKey = getOrCreateKeystoreMasterKey(uid)
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val ivLen = combined[0].toInt() and 0xFF
        val iv = combined.copyOfRange(1, 1 + ivLen)
        val enc = combined.copyOfRange(1 + ivLen, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(enc)
    }

    companion object {
        private const val PREFS_NAME = "ar_chat_e2ee_keystore"
        private const val BACKUP_KDF_ITERATIONS = 100_000

        fun derivePbkdf2Key(secret: String, salt: ByteArray, iterations: Int, keyBits: Int): ByteArray {
            val spec = PBEKeySpec(secret.toCharArray(), salt, iterations, keyBits)
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        }
    }
}
