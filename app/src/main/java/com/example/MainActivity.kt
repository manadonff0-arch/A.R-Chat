package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.AppLockManager
import com.example.core.E2eeCryptoManager
import com.example.core.PreferencesManager
import com.example.core.UiState
import com.example.core.UserUiPreferences
import com.example.data.model.CallSession
import com.example.data.repository.AuthRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.CommunityAndCallsRepository
import com.example.data.repository.OwnerRepository
import com.example.data.repository.UpdatesRepository
import com.example.ui.screens.ActiveCallOverlay
import com.example.ui.screens.AppLockConfigScreen
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.LinkedDevicesScreen
import com.example.ui.screens.MainHomeScreen
import com.example.ui.screens.OwnerPortalScreen
import com.example.ui.screens.PakistanProfileSetupScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StarredMessagesScreen
import com.example.ui.screens.WelcomeAndAuthScreen
import com.example.ui.theme.ARChatTheme
import com.example.ui.theme.ArEmeraldPrimary
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.OwnerViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

private const val ROUTE_HOME = "home"
private const val ROUTE_CHAT_DETAIL = "chat_detail/{chatId}"
private const val ROUTE_PROFILE = "profile"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_APP_LOCK_CONFIG = "app_lock_config"
private const val ROUTE_LINKED_DEVICES = "linked_devices"
private const val ROUTE_STARRED_MESSAGES = "starred_messages"

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appCtx = applicationContext
        val db = FirebaseFirestore.getInstance(getString(R.string.firestore_database_id))
        val cryptoManager = E2eeCryptoManager(appCtx)
        val appLockManager = AppLockManager(appCtx)
        val prefsManager = PreferencesManager(appCtx)
        val authRepository = AuthRepository(db, cryptoManager)
        val chatRepository = ChatRepository(db, cryptoManager)
        val updatesRepository = UpdatesRepository(db)
        val communityAndCallsRepository = CommunityAndCallsRepository(db)
        val ownerRepository = OwnerRepository(db)

        setContent {
            val scope = rememberCoroutineScope()
            val credentialManager = remember { CredentialManager.create(this) }

            val currentUser by remember { Firebase.auth.authStateFlow() }
                .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
            val uiPrefs by prefsManager.preferencesFlow.collectAsStateWithLifecycle(initialValue = UserUiPreferences())
            val lockSettings by appLockManager.settingsFlow.collectAsStateWithLifecycle(
                initialValue = com.example.core.AppLockSettings()
            )

            var splashComplete by remember { mutableStateOf(false) }
            var isAppLocked by remember { mutableStateOf(false) }
            var showOwnerPortal by remember { mutableStateOf(false) }
            var configureLockDuringOnboarding by remember { mutableStateOf(false) }

            // Dynamic FLAG_SECURE protection when App Lock + Secure App Switcher are enabled
            LaunchedEffect(lockSettings.lockEnabled, lockSettings.secureAppSwitcher) {
                if (lockSettings.lockEnabled && lockSettings.secureAppSwitcher) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            // Initial startup splash + silent auto-sign-in + initial lock check
            LaunchedEffect(Unit) {
                val shouldLock = appLockManager.shouldLockOnForeground()
                if (shouldLock) isAppLocked = true

                var finishedAuthCheck = false
                authRepository.attemptAutoSignIn(
                    context = this@MainActivity,
                    credentialManager = credentialManager,
                    onAuthSuccess = { finishedAuthCheck = true },
                    onUnauthenticated = { finishedAuthCheck = true },
                    scope = scope
                )
                delay(900L)
                var waitTicks = 0
                while (!finishedAuthCheck && waitTicks < 20) {
                    delay(100L)
                    waitTicks++
                }
                splashComplete = true
            }

            // ProcessLifecycle background timer -> auto-lock on return to foreground
            DisposableEffect(appLockManager) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> {
                            scope.launch { appLockManager.recordBackgroundTimestamp(System.currentTimeMillis()) }
                        }
                        Lifecycle.Event.ON_START -> {
                            scope.launch {
                                if (Firebase.auth.currentUser != null && appLockManager.shouldLockOnForeground()) {
                                    isAppLocked = true
                                }
                            }
                        }
                        else -> Unit
                    }
                }
                val processLifecycle = ProcessLifecycleOwner.get().lifecycle
                processLifecycle.addObserver(observer)
                onDispose { processLifecycle.removeObserver(observer) }
            }

            val accentColor = when (uiPrefs.accentColorKey) {
                "teal" -> Color(0xFF009688)
                "cobalt" -> Color(0xFF2F80ED)
                "amber" -> Color(0xFFF59E0B)
                else -> ArEmeraldPrimary
            }

            ARChatTheme(
                themeMode = uiPrefs.themeMode,
                accentColor = accentColor,
                fontScale = uiPrefs.fontScale,
                highContrast = uiPrefs.highContrast
            ) {
                when {
                    !splashComplete -> {
                        SplashScreen()
                    }
                    showOwnerPortal -> {
                        val ownerViewModel: OwnerViewModel = viewModel(key = "owner_${currentUser?.uid ?: "anon"}") {
                            OwnerViewModel(ownerRepository)
                        }
                        OwnerPortalScreen(
                            ownerViewModel = ownerViewModel,
                            authRepository = authRepository,
                            currentAuthUid = currentUser?.uid,
                            onBack = { showOwnerPortal = false }
                        )
                    }
                    currentUser == null -> {
                        WelcomeAndAuthScreen(
                            authRepository = authRepository,
                            languageCode = uiPrefs.languageCode,
                            acceptedTerms = uiPrefs.acceptedTerms,
                            onLanguageSelected = { lang ->
                                scope.launch { prefsManager.setLanguage(lang) }
                            },
                            onTermsChanged = { accepted ->
                                scope.launch { prefsManager.setAcceptedTerms(accepted) }
                            },
                            onAuthSuccess = { },
                            onOpenOwnerPortal = { showOwnerPortal = true }
                        )
                    }
                    isAppLocked -> {
                        AppLockScreen(
                            lockManager = appLockManager,
                            settings = lockSettings,
                            onUnlocked = { isAppLocked = false }
                        )
                    }
                    else -> {
                        val user = currentUser!!
                        key(user.uid) {
                            val mainVm: MainViewModel = viewModel(key = "main_${user.uid}") {
                                MainViewModel(
                                    currentUserId = user.uid,
                                    authRepository = authRepository,
                                    chatRepository = chatRepository,
                                    updatesRepository = updatesRepository,
                                    communityAndCallsRepository = communityAndCallsRepository,
                                    ownerRepository = ownerRepository,
                                    cryptoManager = cryptoManager
                                )
                            }

                            AuthenticatedWorkspace(
                                user = user,
                                viewModel = mainVm,
                                appLockManager = appLockManager,
                                lockSettings = lockSettings,
                                prefsManager = prefsManager,
                                uiPrefs = uiPrefs,
                                configureLockDuringOnboarding = configureLockDuringOnboarding,
                                onToggleConfigureLockOnboarding = { configureLockDuringOnboarding = it },
                                onSignOut = {
                                    authRepository.signOut(credentialManager, scope) {}
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedWorkspace(
    user: FirebaseUser,
    viewModel: MainViewModel,
    appLockManager: AppLockManager,
    lockSettings: com.example.core.AppLockSettings,
    prefsManager: PreferencesManager,
    uiPrefs: UserUiPreferences,
    configureLockDuringOnboarding: Boolean,
    onToggleConfigureLockOnboarding: (Boolean) -> Unit,
    onSignOut: () -> Unit
) {
    val privateState by viewModel.privateProfileState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    var activeCallSession by remember { mutableStateOf<CallSession?>(null) }

    when (val priv = privateState) {
        is UiState.Loading -> {
            SplashScreen(statusText = "Loading encrypted profile...")
        }
        is UiState.Error -> {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Connection Issue") },
                text = { Text(priv.message) },
                confirmButton = {
                    TextButton(onClick = onSignOut) { Text("Sign Out") }
                }
            )
        }
        is UiState.Success -> {
            val userPrivate = priv.data
            if (userPrivate == null) {
                if (configureLockDuringOnboarding) {
                    AppLockConfigScreen(
                        lockManager = appLockManager,
                        settings = lockSettings,
                        onBack = { onToggleConfigureLockOnboarding(false) }
                    )
                } else {
                    PakistanProfileSetupScreen(
                        initialEmail = user.email ?: "",
                        initialName = user.displayName ?: "",
                        languageCode = uiPrefs.languageCode,
                        onSaveProfile = { phone, code, carrier, name, handle, about, avatar, email, lang ->
                            viewModel.saveInitialProfile(
                                phoneNumber = phone,
                                countryCode = code,
                                carrierName = carrier,
                                displayName = name,
                                username = handle,
                                about = about,
                                avatarData = avatar,
                                email = email,
                                languageCode = lang,
                                onSuccess = {}
                            )
                        },
                        onConfigureAppLockRequested = { onToggleConfigureLockOnboarding(true) }
                    )
                }
            } else if (userPrivate.accountStatus == "suspended") {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Account Suspended") },
                    text = { Text("Your A.R Chat account has been suspended by platform moderation. Sign out to switch accounts.") },
                    confirmButton = {
                        TextButton(onClick = onSignOut) { Text("Sign Out") }
                    }
                )
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_HOME
                    ) {
                        composable(ROUTE_HOME) {
                            MainHomeScreen(
                                viewModel = viewModel,
                                uiPrefs = uiPrefs,
                                onOpenChat = { chatId -> navController.navigate("chat_detail/$chatId") },
                                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                                onOpenProfile = { navController.navigate(ROUTE_PROFILE) },
                                onOpenLinkedDevices = { navController.navigate(ROUTE_LINKED_DEVICES) },
                                onOpenStarredMessages = { navController.navigate(ROUTE_STARRED_MESSAGES) },
                                onOpenCallOverlay = { session -> activeCallSession = session },
                                onSwitchAccount = onSignOut
                            )
                        }
                        composable(
                            route = ROUTE_CHAT_DETAIL,
                            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
                        ) { entry ->
                            val chatId = entry.arguments?.getString("chatId") ?: ""
                            ChatDetailScreen(
                                viewModel = viewModel,
                                chatId = chatId,
                                onBack = { navController.popBackStack() },
                                onCallStarted = { session -> activeCallSession = session }
                            )
                        }
                        composable(ROUTE_PROFILE) {
                            ProfileScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onSignOut = onSignOut
                            )
                        }
                        composable(ROUTE_SETTINGS) {
                            SettingsScreen(
                                viewModel = viewModel,
                                prefsManager = prefsManager,
                                uiPrefs = uiPrefs,
                                lockSettings = lockSettings,
                                onBack = { navController.popBackStack() },
                                onOpenProfile = { navController.navigate(ROUTE_PROFILE) },
                                onOpenAppLockConfig = { navController.navigate(ROUTE_APP_LOCK_CONFIG) },
                                onOpenLinkedDevices = { navController.navigate(ROUTE_LINKED_DEVICES) },
                                onAccountDeleted = onSignOut
                            )
                        }
                        composable(ROUTE_APP_LOCK_CONFIG) {
                            AppLockConfigScreen(
                                lockManager = appLockManager,
                                settings = lockSettings,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(ROUTE_LINKED_DEVICES) {
                            LinkedDevicesScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(ROUTE_STARRED_MESSAGES) {
                            StarredMessagesScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpenChat = { chatId -> navController.navigate("chat_detail/$chatId") }
                            )
                        }
                    }

                    activeCallSession?.let { session ->
                        ActiveCallOverlay(
                            viewModel = viewModel,
                            initialSession = session,
                            onDismiss = { activeCallSession = null }
                        )
                    }
                }
            }
        }
    }
}
