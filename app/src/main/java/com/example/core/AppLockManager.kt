package com.example.core

import android.content.Context
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom

private val Context.appLockDataStore by preferencesDataStore(name = "ar_chat_app_lock")

enum class LockMethod(val key: String, val label: String) {
    BIOMETRIC("biometric", "Biometric (Fingerprint / Class 3 Face)"),
    PIN_4("pin4", "4-Digit PIN"),
    PIN_6("pin6", "6-Digit PIN"),
    PATTERN("pattern", "Pattern Lock"),
    PASSWORD("password", "Password")
}

enum class BiometricCapability(val title: String, val detail: String, val class3Supported: Boolean) {
    CLASS3_READY(
        "Class 3 Strong Biometrics Ready",
        "Your device supports Android Class 3 hardware-backed fingerprint or secure face authentication via BiometricPrompt.",
        true
    ),
    NOT_ENROLLED(
        "No Biometric Enrolled",
        "This device supports Class 3 biometrics, but no fingerprint or secure face credential is enrolled in Android Settings.",
        false
    ),
    WEAK_OR_UNAVAILABLE(
        "Class 3 Biometric Unavailable",
        "This device does not expose a Class 3 (Strong) biometric authenticator. Use PIN, Pattern, or Password lock instead.",
        false
    ),
    NO_HARDWARE(
        "No Biometric Hardware",
        "Biometric sensor not detected on this device. PIN, Pattern, or Password lock is available.",
        false
    )
}

data class AppLockSettings(
    val lockEnabled: Boolean = false,
    val primaryMethod: LockMethod = LockMethod.PIN_4,
    val biometricEnabled: Boolean = false,
    val autoLockMinutes: Int = 0, // 0 = Immediately, 1, 5, 15, 30
    val hideMessagePreviews: Boolean = true,
    val secureAppSwitcher: Boolean = true,
    val hasCredentialConfigured: Boolean = false,
    val failedAttempts: Int = 0,
    val lockoutUntilMillis: Long = 0L
)

class AppLockManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val secureRandom = SecureRandom()

    val settingsFlow: Flow<AppLockSettings> = appContext.appLockDataStore.data.map { prefs ->
        val methodKey = prefs[KEY_METHOD] ?: LockMethod.PIN_4.key
        AppLockSettings(
            lockEnabled = prefs[KEY_ENABLED] ?: false,
            primaryMethod = LockMethod.entries.firstOrNull { it.key == methodKey } ?: LockMethod.PIN_4,
            biometricEnabled = prefs[KEY_BIOMETRIC_ENABLED] ?: false,
            autoLockMinutes = prefs[KEY_AUTO_LOCK_MINUTES] ?: 0,
            hideMessagePreviews = prefs[KEY_HIDE_PREVIEWS] ?: true,
            secureAppSwitcher = prefs[KEY_SECURE_SWITCHER] ?: true,
            hasCredentialConfigured = !prefs[KEY_CREDENTIAL_HASH].isNullOrBlank(),
            failedAttempts = prefs[KEY_FAILED_ATTEMPTS] ?: 0,
            lockoutUntilMillis = prefs[KEY_LOCKOUT_UNTIL] ?: 0L
        )
    }

    fun checkBiometricCapability(): BiometricCapability {
        val manager = BiometricManager.from(appContext)
        return when (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapability.CLASS3_READY
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricCapability.NO_HARDWARE
            else -> BiometricCapability.WEAK_OR_UNAVAILABLE
        }
    }

    suspend fun configureCredentialLock(
        method: LockMethod,
        secret: String,
        enableBiometric: Boolean
    ) {
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val hash = E2eeCryptoManager.derivePbkdf2Key(secret, salt, PBKDF2_ITERATIONS, 256)
        val canUseBio = enableBiometric && checkBiometricCapability().class3Supported
        appContext.appLockDataStore.edit { prefs ->
            prefs[KEY_ENABLED] = true
            prefs[KEY_METHOD] = method.key
            prefs[KEY_BIOMETRIC_ENABLED] = canUseBio
            prefs[KEY_CREDENTIAL_HASH] = Base64.encodeToString(hash, Base64.NO_WRAP)
            prefs[KEY_CREDENTIAL_SALT] = Base64.encodeToString(salt, Base64.NO_WRAP)
            prefs[KEY_FAILED_ATTEMPTS] = 0
            prefs[KEY_LOCKOUT_UNTIL] = 0L
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        val allowed = enabled && checkBiometricCapability().class3Supported
        appContext.appLockDataStore.edit { prefs ->
            prefs[KEY_BIOMETRIC_ENABLED] = allowed
        }
    }

    suspend fun setAutoLockMinutes(minutes: Int) {
        appContext.appLockDataStore.edit { prefs ->
            prefs[KEY_AUTO_LOCK_MINUTES] = minutes
        }
    }

    suspend fun setPrivacyFlags(hidePreviews: Boolean, secureSwitcher: Boolean) {
        appContext.appLockDataStore.edit { prefs ->
            prefs[KEY_HIDE_PREVIEWS] = hidePreviews
            prefs[KEY_SECURE_SWITCHER] = secureSwitcher
        }
    }

    suspend fun disableLock() {
        appContext.appLockDataStore.edit { prefs ->
            prefs[KEY_ENABLED] = false
            prefs[KEY_BIOMETRIC_ENABLED] = false
            prefs[KEY_FAILED_ATTEMPTS] = 0
            prefs[KEY_LOCKOUT_UNTIL] = 0L
        }
    }

    suspend fun verifySecret(secret: String): Boolean {
        val prefs = appContext.appLockDataStore.data.first()
        val now = System.currentTimeMillis()
        val lockoutUntil = prefs[KEY_LOCKOUT_UNTIL] ?: 0L
        if (now < lockoutUntil) return false

        val storedHashB64 = prefs[KEY_CREDENTIAL_HASH] ?: return false
        val storedSaltB64 = prefs[KEY_CREDENTIAL_SALT] ?: return false
        val expectedHash = Base64.decode(storedHashB64, Base64.NO_WRAP)
        val salt = Base64.decode(storedSaltB64, Base64.NO_WRAP)
        val candidateHash = E2eeCryptoManager.derivePbkdf2Key(secret, salt, PBKDF2_ITERATIONS, 256)
        val valid = MessageDigest.isEqual(expectedHash, candidateHash)

        appContext.appLockDataStore.edit { mutable ->
            if (valid) {
                mutable[KEY_FAILED_ATTEMPTS] = 0
                mutable[KEY_LOCKOUT_UNTIL] = 0L
            } else {
                val nextAttempts = (mutable[KEY_FAILED_ATTEMPTS] ?: 0) + 1
                mutable[KEY_FAILED_ATTEMPTS] = nextAttempts
                if (nextAttempts >= 5) {
                    mutable[KEY_LOCKOUT_UNTIL] = System.currentTimeMillis() + 30_000L
                }
            }
        }
        return valid
    }

    suspend fun recordBackgroundTimestamp(timestampMillis: Long) {
        appContext.appLockDataStore.edit { prefs ->
            prefs[KEY_LAST_BACKGROUND_MS] = timestampMillis
        }
    }

    suspend fun shouldLockOnForeground(): Boolean {
        val prefs = appContext.appLockDataStore.data.first()
        val enabled = prefs[KEY_ENABLED] ?: false
        if (!enabled) return false
        val hasCred = !prefs[KEY_CREDENTIAL_HASH].isNullOrBlank()
        val bioEnabled = prefs[KEY_BIOMETRIC_ENABLED] ?: false
        if (!hasCred && !bioEnabled) return false

        val timeoutMinutes = prefs[KEY_AUTO_LOCK_MINUTES] ?: 0
        val lastBg = prefs[KEY_LAST_BACKGROUND_MS] ?: 0L
        if (lastBg == 0L) return true
        if (timeoutMinutes == 0) return true
        val elapsedMillis = System.currentTimeMillis() - lastBg
        return elapsedMillis >= timeoutMinutes * 60_000L
    }

    fun authenticateWithBiometric(
        activity: FragmentActivity,
        title: String = "Unlock A.R Chat",
        subtitle: String = "Use your Class 3 biometric credential",
        negativeText: String = "Use PIN / Password",
        onSuccess: () -> Unit,
        onFallbackRequested: () -> Unit,
        onError: (String) -> Unit
    ) {
        val capability = checkBiometricCapability()
        if (!capability.class3Supported) {
            onError(capability.detail)
            onFallbackRequested()
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED
                    ) {
                        onFallbackRequested()
                    } else {
                        onError(errString.toString())
                    }
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(negativeText)
            .build()
        prompt.authenticate(promptInfo)
    }

    companion object {
        private const val PBKDF2_ITERATIONS = 100_000
        private val KEY_ENABLED = booleanPreferencesKey("lock_enabled")
        private val KEY_METHOD = stringPreferencesKey("lock_method")
        private val KEY_BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        private val KEY_AUTO_LOCK_MINUTES = intPreferencesKey("auto_lock_minutes")
        private val KEY_HIDE_PREVIEWS = booleanPreferencesKey("hide_previews")
        private val KEY_SECURE_SWITCHER = booleanPreferencesKey("secure_switcher")
        private val KEY_CREDENTIAL_HASH = stringPreferencesKey("credential_hash")
        private val KEY_CREDENTIAL_SALT = stringPreferencesKey("credential_salt")
        private val KEY_FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
        private val KEY_LOCKOUT_UNTIL = longPreferencesKey("lockout_until")
        private val KEY_LAST_BACKGROUND_MS = longPreferencesKey("last_background_ms")

        fun hashOwnerPin(pin: String, existingSaltBase64: String? = null): Pair<String, String> {
            val salt = if (existingSaltBase64 != null) {
                Base64.decode(existingSaltBase64, Base64.NO_WRAP)
            } else {
                ByteArray(16).also { SecureRandom().nextBytes(it) }
            }
            val hash = E2eeCryptoManager.derivePbkdf2Key(pin, salt, PBKDF2_ITERATIONS, 256)
            return Base64.encodeToString(hash, Base64.NO_WRAP) to Base64.encodeToString(salt, Base64.NO_WRAP)
        }

        fun verifyOwnerPin(pin: String, storedHashBase64: String, storedSaltBase64: String): Boolean {
            return runCatching {
                val (candidateHash, _) = hashOwnerPin(pin, storedSaltBase64)
                MessageDigest.isEqual(
                    Base64.decode(storedHashBase64, Base64.NO_WRAP),
                    Base64.decode(candidateHash, Base64.NO_WRAP)
                )
            }.getOrDefault(false)
        }
    }
}
