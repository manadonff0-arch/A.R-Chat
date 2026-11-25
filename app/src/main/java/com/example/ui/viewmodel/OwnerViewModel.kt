package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.UiState
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
import com.example.data.repository.OwnerRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OwnerViewModel(
    private val ownerRepository: OwnerRepository
) : ViewModel() {

    private fun <T> Flow<T>.toUiStateFlow(tag: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w(tag, "Owner stream error", root)
                emit(UiState.Error(root.message ?: "Access denied or network error"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    private val _ownerSecurityConfig = MutableStateFlow<OwnerSecurityConfig?>(null)
    val ownerSecurityConfig: StateFlow<OwnerSecurityConfig?> = _ownerSecurityConfig.asStateFlow()

    private val _ownerConfigLoaded = MutableStateFlow(false)
    val ownerConfigLoaded: StateFlow<Boolean> = _ownerConfigLoaded.asStateFlow()

    private val _ownerVerified = MutableStateFlow(false)
    val ownerVerified: StateFlow<Boolean> = _ownerVerified.asStateFlow()

    private val _authMessage = MutableStateFlow<String?>(null)
    val authMessage: StateFlow<String?> = _authMessage.asStateFlow()

    val usersPrivateState: StateFlow<UiState<List<UserPrivate>>> =
        ownerRepository.observeAllUsersPrivate().toUiStateFlow("OwnerUsers")

    val publicProfilesState: StateFlow<UiState<List<PublicProfile>>> =
        ownerRepository.observeAllPublicProfiles().toUiStateFlow("OwnerProfiles")

    val reportsState: StateFlow<UiState<List<ReportTicket>>> =
        ownerRepository.observeAllReports().toUiStateFlow("OwnerReports")

    val chatsState: StateFlow<UiState<List<Chat>>> =
        ownerRepository.observeAllChatsAdmin().toUiStateFlow("OwnerChats")

    val channelsState: StateFlow<UiState<List<Channel>>> =
        ownerRepository.observeAllChannelsAdmin().toUiStateFlow("OwnerChannels")

    val communitiesState: StateFlow<UiState<List<Community>>> =
        ownerRepository.observeAllCommunitiesAdmin().toUiStateFlow("OwnerCommunities")

    val adminsState: StateFlow<UiState<List<AdminMember>>> =
        ownerRepository.observeAdmins().toUiStateFlow("OwnerAdmins")

    val adminLogsState: StateFlow<UiState<List<AdminAuditLog>>> =
        ownerRepository.observeAdminLogs().toUiStateFlow("OwnerLogs")

    val publicConfigState: StateFlow<UiState<AppPublicConfig?>> =
        ownerRepository.observePublicAppConfig().toUiStateFlow("OwnerPublicCfg")

    fun loadOwnerSecurityConfig() {
        viewModelScope.launch {
            ownerRepository.getOwnerSecurityConfig()
                .onSuccess { cfg ->
                    _ownerSecurityConfig.value = cfg
                    _ownerConfigLoaded.value = true
                }
                .onFailure { err ->
                    _ownerConfigLoaded.value = true
                    _authMessage.value = err.localizedMessage ?: "Unauthorized: Owner identity required."
                }
        }
    }

    fun provisionOwnerCredentials(phoneNumber: String, pin: String) {
        viewModelScope.launch {
            ownerRepository.provisionOwnerSecurityConfig(phoneNumber, pin)
                .onSuccess {
                    _ownerVerified.value = true
                    _authMessage.value = "Owner 2FA credentials provisioned."
                    loadOwnerSecurityConfig()
                }
                .onFailure {
                    _authMessage.value = it.localizedMessage ?: "Failed to provision owner security."
                }
        }
    }

    fun verifyOwnerLogin(phoneNumber: String, pin: String) {
        viewModelScope.launch {
            ownerRepository.verifyOwnerCredentials(phoneNumber, pin)
                .onSuccess { valid ->
                    if (valid) {
                        _ownerVerified.value = true
                        _authMessage.value = null
                    } else {
                        _authMessage.value = "Invalid owner mobile number or 2FA PIN."
                    }
                }
                .onFailure {
                    _authMessage.value = it.localizedMessage ?: "Owner verification failed."
                }
        }
    }

    fun setUserAccountStatus(uid: String, status: String) {
        viewModelScope.launch {
            ownerRepository.setUserAccountStatus(uid, status)
                .onFailure { _authMessage.value = it.localizedMessage }
        }
    }

    fun resolveReport(reportId: String, status: String, note: String) {
        viewModelScope.launch {
            ownerRepository.resolveReport(reportId, status, note)
                .onFailure { _authMessage.value = it.localizedMessage }
        }
    }

    fun updatePublicConfig(maintenance: Boolean, message: String, version: String, announcement: String) {
        viewModelScope.launch {
            ownerRepository.updatePublicAppConfig(maintenance, message, version, announcement)
                .onSuccess { _authMessage.value = "Platform configuration updated" }
                .onFailure { _authMessage.value = it.localizedMessage }
        }
    }

    fun addAdmin(uid: String, email: String) {
        viewModelScope.launch {
            ownerRepository.addAdmin(uid, email)
                .onFailure { _authMessage.value = it.localizedMessage }
        }
    }

    fun removeAdmin(uid: String) {
        viewModelScope.launch {
            ownerRepository.removeAdmin(uid)
                .onFailure { _authMessage.value = it.localizedMessage }
        }
    }

    fun toggleChannelActive(channelId: String, active: Boolean) {
        viewModelScope.launch {
            ownerRepository.toggleChannelActive(channelId, active)
                .onFailure { _authMessage.value = it.localizedMessage }
        }
    }
}
