package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.core.MediaPickerHelper
import com.example.core.PakistanCarrier
import com.example.core.PakistanPhoneValidator
import com.example.data.repository.AuthRepository
import com.example.ui.components.ArAvatar
import com.example.ui.theme.ArBackgroundDark
import com.example.ui.theme.ArEmeraldBright
import com.example.ui.theme.ArEmeraldDeep
import com.example.ui.theme.ArEmeraldPrimary
import com.example.ui.theme.ArSurfaceDark

@Composable
fun SplashScreen(statusText: String = "Initializing secure session...") {
    val transition = rememberInfiniteTransition(label = "splash")
    val scale by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(ArBackgroundDark, ArEmeraldDeep.copy(alpha = 0.6f), ArBackgroundDark))
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.ar_chat_launcher_fg),
                contentDescription = "A.R Chat Logo",
                modifier = Modifier
                    .size(112.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(28.dp))
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "A.R Chat",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = ArEmeraldBright
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(
                color = ArEmeraldPrimary,
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.5.dp
            )
        }
    }
}

@Composable
fun WelcomeAndAuthScreen(
    authRepository: AuthRepository,
    languageCode: String,
    acceptedTerms: Boolean,
    onLanguageSelected: (String) -> Unit,
    onTermsChanged: (Boolean) -> Unit,
    onAuthSuccess: () -> Unit,
    onOpenOwnerPortal: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPolicyDialog by remember { mutableStateOf<String?>(null) }

    val isUrdu = languageCode == "ur"

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ar_chat_launcher_fg),
                        contentDescription = "A.R Chat Emblem",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "A.R Chat",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                FilterChip(
                    selected = isUrdu,
                    onClick = { onLanguageSelected(if (isUrdu) "en" else "ur") },
                    label = { Text(if (isUrdu) "اردو / EN" else "EN / اردو") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = "Switch language",
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("welcome_language_toggle")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Image(
                painter = painterResource(id = R.drawable.img_welcome_hero),
                contentDescription = "A.R Chat encrypted messaging illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(24.dp))
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (isUrdu) "A.R Chat میں خوش آمدید" else "Welcome to A.R Chat",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isUrdu) {
                    "پاکستان کے لیے محفوظ، تیز اور اینڈ ٹو اینڈ انکرپٹڈ پیغام رسانی، کمیونٹیز اور بائیومیٹرک لاک۔"
                } else {
                    "Fast, Pakistan-ready messaging with ECDH + AES-256-GCM encryption, statuses, communities, and hardware biometric lock."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Onboarding Highlights
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OnboardingFeatureRow(
                        icon = Icons.Default.PhoneAndroid,
                        title = "Pakistan +92 Network Ready",
                        subtitle = "Supports Jazz, Zong, Ufone, and Telenor number formatting & carrier tags."
                    )
                    OnboardingFeatureRow(
                        icon = Icons.Default.Security,
                        title = "True Device-Key E2EE",
                        subtitle = "Private EC P-256 keys stay wrapped in Android Keystore on your device."
                    )
                    OnboardingFeatureRow(
                        icon = Icons.Default.Fingerprint,
                        title = "Class 3 Biometrics & PIN Lock",
                        subtitle = "Protects chats with Android BiometricPrompt and salted PBKDF2 PINs."
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onTermsChanged(!acceptedTerms) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = acceptedTerms,
                    onCheckedChange = onTermsChanged,
                    modifier = Modifier.testTag("terms_checkbox")
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isUrdu) "میں شرائط و ضوابط اور رازداری کی پالیسی سے اتفاق کرتا ہوں" else "I accept the Terms of Service and Privacy Policy",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row {
                        TextButton(
                            onClick = { showPolicyDialog = "terms" },
                            modifier = Modifier.padding(0.dp)
                        ) { Text("Read Terms", style = MaterialTheme.typography.labelSmall) }
                        TextButton(
                            onClick = { showPolicyDialog = "privacy" },
                            modifier = Modifier.padding(0.dp)
                        ) { Text("Privacy Policy", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }

            errorMessage?.let { msg ->
                Text(
                    text = msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (!acceptedTerms) {
                        errorMessage = "Please accept the Terms of Service & Privacy Policy first."
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    authRepository.onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { err ->
                            isLoading = false
                            errorMessage = err
                        },
                        scope = scope,
                        onAuthCancelled = { isLoading = false }
                    )
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ArEmeraldPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("google_sign_in_button")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isUrdu) "گوگل کے ساتھ سائن ان کریں" else "Agree & Sign in with Google",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onOpenOwnerPortal,
                modifier = Modifier.testTag("owner_portal_entry_button")
            ) {
                Icon(
                    imageVector = Icons.Default.AdminPanelSettings,
                    contentDescription = "Owner Portal",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Owner Portal Login",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    showPolicyDialog?.let { kind ->
        AlertDialog(
            onDismissRequest = { showPolicyDialog = null },
            title = { Text(if (kind == "terms") "A.R Chat Terms of Service" else "A.R Chat Privacy Policy") },
            text = {
                Text(
                    text = if (kind == "terms") {
                        "A.R Chat is a real-time encrypted communication service. Users must comply with Pakistani and international laws, refrain from spam or abuse, and safeguard their own backup passphrases and app-lock credentials."
                    } else {
                        "Your Google Account provides cloud identity verification. Private ECDH keys remain inside Android Keystore on your device. Private PII (mobile number, email) is strictly isolated under your user record and never exposed in public profiles."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { showPolicyDialog = null }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun OnboardingFeatureRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(ArEmeraldPrimary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = ArEmeraldBright, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PakistanProfileSetupScreen(
    initialEmail: String,
    initialName: String,
    languageCode: String,
    onSaveProfile: (
        phoneNumber: String,
        countryCode: String,
        carrierName: String,
        displayName: String,
        username: String,
        about: String,
        avatarData: String,
        email: String,
        languageCode: String
    ) -> Unit,
    onConfigureAppLockRequested: () -> Unit
) {
    val context = LocalContext.current
    var selectedCountry by remember { mutableStateOf(PakistanPhoneValidator.supportedCountries.first()) }
    var countryMenuExpanded by remember { mutableStateOf(false) }
    var phoneInput by remember { mutableStateOf("") }
    var selectedCarrierOverride by remember { mutableStateOf<PakistanCarrier?>(null) }
    var displayName by remember { mutableStateOf(initialName) }
    var username by remember { mutableStateOf(initialName.lowercase().replace(" ", "_").filter { it.isLetterOrDigit() || it == '_' }) }
    var about by remember { mutableStateOf("Hey there! I am using A.R Chat.") }
    var emailInput by remember { mutableStateOf(initialEmail) }
    var avatarBase64 by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var notifGranted by remember { mutableStateOf(false) }
    var contactsGranted by remember { mutableStateOf(false) }

    val detectedCarrier = remember(phoneInput, selectedCountry) {
        if (selectedCountry.code == "+92") PakistanPhoneValidator.detectCarrier(phoneInput)
        else PakistanCarrier.OTHER
    }
    val activeCarrier = selectedCarrierOverride ?: detectedCarrier

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            avatarBase64 = MediaPickerHelper.uriToCompressedBase64(context, uri, 320, 36_000) ?: ""
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> notifGranted = granted }

    val contactsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> contactsGranted = granted }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Set Up Your Pakistan Profile",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Register your +92 Pakistani mobile number, carrier network, and encrypted profile.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .clickable {
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    .testTag("setup_avatar_picker"),
                contentAlignment = Alignment.BottomEnd
            ) {
                ArAvatar(displayName = displayName.ifBlank { "AR" }, avatarBase64 = avatarBase64, size = 88.dp)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ArEmeraldPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Pick photo", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(
                        onClick = { countryMenuExpanded = true },
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("${selectedCountry.flag} ${selectedCountry.code}")
                    }
                    DropdownMenu(expanded = countryMenuExpanded, onDismissRequest = { countryMenuExpanded = false }) {
                        PakistanPhoneValidator.supportedCountries.forEach { country ->
                            DropdownMenuItem(
                                text = { Text("${country.name} (${country.code})") },
                                onClick = {
                                    selectedCountry = country
                                    countryMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = phoneInput,
                    onValueChange = { phoneInput = it.filter { ch -> ch.isDigit() || ch == ' ' || ch == '-' } },
                    label = { Text("Mobile Number (e.g. 3001234567)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("phone_number_input")
                )
            }

            if (selectedCountry.code == "+92") {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(PakistanCarrier.JAZZ, PakistanCarrier.ZONG, PakistanCarrier.UFONE, PakistanCarrier.TELENOR).forEach { carrier ->
                        FilterChip(
                            selected = activeCarrier == carrier,
                            onClick = { selectedCarrierOverride = carrier },
                            label = { Text(carrier.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Text(
                    text = "Number verified by Google identity + Pakistani format check. (External SMS OTP gateway not configured.)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("display_name_input")
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' } },
                label = { Text("Username (@handle)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("username_input")
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = about,
                onValueChange = { about = it },
                label = { Text("About") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = emailInput,
                onValueChange = { emailInput = it },
                label = { Text("Recovery Email (Optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Permission rationale cards before requesting
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Optional Permissions (Explain First)", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp), tint = ArEmeraldBright)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Alerts for incoming messages & calls", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                notifGranted = true
                            }
                        }) {
                            Text(if (notifGranted) "Allowed" else "Allow")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(18.dp), tint = ArEmeraldBright)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Find friends already on A.R Chat", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { contactsLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                            Text(if (contactsGranted) "Allowed" else "Allow")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp), tint = ArEmeraldBright)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Biometric & PIN App Lock", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = onConfigureAppLockRequested) {
                            Text("Configure")
                        }
                    }
                }
            }

            errorText?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (!PakistanPhoneValidator.isValidNumber(selectedCountry.code, phoneInput)) {
                        errorText = if (selectedCountry.code == "+92") {
                            "Enter a valid 10-digit Pakistani mobile number starting with 3 (e.g. 3001234567)."
                        } else {
                            "Enter a valid mobile number (7 to 14 digits)."
                        }
                        return@Button
                    }
                    if (displayName.trim().isEmpty() || username.trim().length < 2) {
                        errorText = "Display name and username (2+ chars) are required."
                        return@Button
                    }
                    errorText = null
                    onSaveProfile(
                        PakistanPhoneValidator.formatE164(selectedCountry.code, phoneInput),
                        selectedCountry.code,
                        activeCarrier.label,
                        displayName.trim(),
                        username.trim(),
                        about.trim(),
                        avatarBase64,
                        emailInput.trim(),
                        languageCode
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ArEmeraldPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("complete_profile_button")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Activate A.R Chat Account", fontWeight = FontWeight.Bold)
            }
        }
    }
}
