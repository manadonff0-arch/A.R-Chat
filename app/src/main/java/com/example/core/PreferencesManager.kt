package com.example.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appPrefsDataStore by preferencesDataStore(name = "ar_chat_preferences")

data class UserUiPreferences(
    val themeMode: String = "dark",
    val accentColorKey: String = "emerald",
    val fontScale: Float = 1.0f,
    val highContrast: Boolean = false,
    val reducedMotion: Boolean = false,
    val languageCode: String = "en",
    val messageNotifications: Boolean = true,
    val groupNotifications: Boolean = true,
    val callNotifications: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val wifiAutoDownload: Boolean = true,
    val mobileAutoDownload: Boolean = false,
    val dataSaverMode: Boolean = false,
    val hdMediaDefault: Boolean = false,
    val enterSendsMessage: Boolean = true,
    val acceptedTerms: Boolean = false
)

class PreferencesManager(context: Context) {
    private val appContext = context.applicationContext

    val preferencesFlow: Flow<UserUiPreferences> = appContext.appPrefsDataStore.data.map { prefs ->
        UserUiPreferences(
            themeMode = prefs[KEY_THEME_MODE] ?: "dark",
            accentColorKey = prefs[KEY_ACCENT] ?: "emerald",
            fontScale = prefs[KEY_FONT_SCALE] ?: 1.0f,
            highContrast = prefs[KEY_HIGH_CONTRAST] ?: false,
            reducedMotion = prefs[KEY_REDUCED_MOTION] ?: false,
            languageCode = prefs[KEY_LANGUAGE] ?: "en",
            messageNotifications = prefs[KEY_MSG_NOTIF] ?: true,
            groupNotifications = prefs[KEY_GRP_NOTIF] ?: true,
            callNotifications = prefs[KEY_CALL_NOTIF] ?: true,
            vibrationEnabled = prefs[KEY_VIBRATION] ?: true,
            wifiAutoDownload = prefs[KEY_WIFI_DL] ?: true,
            mobileAutoDownload = prefs[KEY_MOBILE_DL] ?: false,
            dataSaverMode = prefs[KEY_DATA_SAVER] ?: false,
            hdMediaDefault = prefs[KEY_HD_MEDIA] ?: false,
            enterSendsMessage = prefs[KEY_ENTER_SEND] ?: true,
            acceptedTerms = prefs[KEY_ACCEPTED_TERMS] ?: false
        )
    }

    suspend fun setAcceptedTerms(accepted: Boolean) {
        appContext.appPrefsDataStore.edit { it[KEY_ACCEPTED_TERMS] = accepted }
    }

    suspend fun setLanguage(code: String) {
        appContext.appPrefsDataStore.edit { it[KEY_LANGUAGE] = code }
    }

    suspend fun setAppearance(
        themeMode: String? = null,
        accentKey: String? = null,
        fontScale: Float? = null,
        highContrast: Boolean? = null,
        reducedMotion: Boolean? = null
    ) {
        appContext.appPrefsDataStore.edit { prefs ->
            themeMode?.let { prefs[KEY_THEME_MODE] = it }
            accentKey?.let { prefs[KEY_ACCENT] = it }
            fontScale?.let { prefs[KEY_FONT_SCALE] = it }
            highContrast?.let { prefs[KEY_HIGH_CONTRAST] = it }
            reducedMotion?.let { prefs[KEY_REDUCED_MOTION] = it }
        }
    }

    suspend fun setNotificationPrefs(
        messages: Boolean? = null,
        groups: Boolean? = null,
        calls: Boolean? = null,
        vibration: Boolean? = null
    ) {
        appContext.appPrefsDataStore.edit { prefs ->
            messages?.let { prefs[KEY_MSG_NOTIF] = it }
            groups?.let { prefs[KEY_GRP_NOTIF] = it }
            calls?.let { prefs[KEY_CALL_NOTIF] = it }
            vibration?.let { prefs[KEY_VIBRATION] = it }
        }
    }

    suspend fun setStoragePrefs(
        wifiAuto: Boolean? = null,
        mobileAuto: Boolean? = null,
        dataSaver: Boolean? = null,
        hdDefault: Boolean? = null
    ) {
        appContext.appPrefsDataStore.edit { prefs ->
            wifiAuto?.let { prefs[KEY_WIFI_DL] = it }
            mobileAuto?.let { prefs[KEY_MOBILE_DL] = it }
            dataSaver?.let { prefs[KEY_DATA_SAVER] = it }
            hdDefault?.let { prefs[KEY_HD_MEDIA] = it }
        }
    }

    suspend fun setEnterSends(enabled: Boolean) {
        appContext.appPrefsDataStore.edit { it[KEY_ENTER_SEND] = enabled }
    }

    companion object {
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_ACCENT = stringPreferencesKey("accent_color")
        private val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
        private val KEY_HIGH_CONTRAST = booleanPreferencesKey("high_contrast")
        private val KEY_REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        private val KEY_LANGUAGE = stringPreferencesKey("language_code")
        private val KEY_MSG_NOTIF = booleanPreferencesKey("msg_notif")
        private val KEY_GRP_NOTIF = booleanPreferencesKey("grp_notif")
        private val KEY_CALL_NOTIF = booleanPreferencesKey("call_notif")
        private val KEY_VIBRATION = booleanPreferencesKey("vibration")
        private val KEY_WIFI_DL = booleanPreferencesKey("wifi_dl")
        private val KEY_MOBILE_DL = booleanPreferencesKey("mobile_dl")
        private val KEY_DATA_SAVER = booleanPreferencesKey("data_saver")
        private val KEY_HD_MEDIA = booleanPreferencesKey("hd_media")
        private val KEY_ENTER_SEND = booleanPreferencesKey("enter_sends")
        private val KEY_ACCEPTED_TERMS = booleanPreferencesKey("accepted_terms")
    }
}
