package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.core.AppLockManager
import com.example.core.AppLockSettings
import com.example.core.LockMethod
import com.example.ui.theme.ArEmeraldBright
import com.example.ui.theme.ArEmeraldDeep
import com.example.ui.theme.ArEmeraldPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

@Composable
fun AppLockScreen(
    lockManager: AppLockManager,
    settings: AppLockSettings,
    onUnlocked: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()

    var pinOrPass by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    val lockoutRemainingSec = ((settings.lockoutUntilMillis - nowMs) / 1000L).coerceAtLeast(0L)

    LaunchedEffect(settings.lockoutUntilMillis) {
        while (System.currentTimeMillis() < settings.lockoutUntilMillis) {
            nowMs = System.currentTimeMillis()
            delay(500L)
        }
        nowMs = System.currentTimeMillis()
    }

    LaunchedEffect(settings.biometricEnabled) {
        if (settings.biometricEnabled && activity != null && lockoutRemainingSec == 0L) {
            lockManager.authenticateWithBiometric(
                activity = activity,
                onSuccess = onUnlocked,
                onFallbackRequested = {},
                onError = { err -> errorMsg = err }
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(ArEmeraldDeep),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Lock, contentDescription = "Locked", tint = ArEmeraldBright, modifier = Modifier.size(30.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("A.R Chat is Locked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (settings.hideMessagePreviews) "Message previews are hidden until you authenticate." else "Authenticate to continue.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (lockoutRemainingSec > 0) {
                Text(
                    text = "Too many failed attempts. Try again in ${lockoutRemainingSec}s.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                when (settings.primaryMethod) {
                    LockMethod.PASSWORD -> {
                        OutlinedTextField(
                            value = pinOrPass,
                            onValueChange = { pinOrPass = it },
                            label = { Text("Enter App Lock Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("app_lock_password_input")
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    if (lockManager.verifySecret(pinOrPass)) {
                                        onUnlocked()
                                    } else {
                                        errorMsg = "Incorrect password"
                                        pinOrPass = ""
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ArEmeraldPrimary),
                            modifier = Modifier.fillMaxWidth().testTag("app_lock_unlock_button")
                        ) {
                            Text("Unlock")
                        }
                    }
                    LockMethod.PATTERN -> {
                        PatternLockGrid(
                            onPatternCompleted = { pattern ->
                                scope.launch {
                                    if (lockManager.verifySecret(pattern)) {
                                        onUnlocked()
                                    } else {
                                        errorMsg = "Incorrect pattern"
                                    }
                                }
                            }
                        )
                    }
                    else -> {
                        val expectedLen = if (settings.primaryMethod == LockMethod.PIN_6) 6 else 4
                        PinPad(
                            pinLength = expectedLen,
                            currentPin = pinOrPass,
                            onDigit = { digit ->
                                if (pinOrPass.length < expectedLen) {
                                    val next = pinOrPass + digit
                                    pinOrPass = next
                                    if (next.length == expectedLen) {
                                        scope.launch {
                                            if (lockManager.verifySecret(next)) {
                                                onUnlocked()
                                            } else {
                                                errorMsg = "Incorrect PIN"
                                                pinOrPass = ""
                                            }
                                        }
                                    }
                                }
                            },
                            onBackspace = {
                                if (pinOrPass.isNotEmpty()) pinOrPass = pinOrPass.dropLast(1)
                            }
                        )
                    }
                }
            }

            errorMsg?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (settings.biometricEnabled && activity != null) {
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedButton(
                    onClick = {
                        lockManager.authenticateWithBiometric(
                            activity = activity,
                            onSuccess = onUnlocked,
                            onFallbackRequested = {},
                            onError = { err -> errorMsg = err }
                        )
                    },
                    modifier = Modifier.testTag("app_lock_biometric_button")
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = "Biometric unlock", tint = ArEmeraldBright)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Use Biometric (Fingerprint / Class 3 Face)")
                }
            }
        }
    }
}

@Composable
fun PinPad(
    pinLength: Int,
    currentPin: String,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(pinLength) { idx ->
                val filled = idx < currentPin.length
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (filled) ArEmeraldBright else MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "DEL")
        )
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                row.forEach { key ->
                    when (key) {
                        "" -> Spacer(modifier = Modifier.size(68.dp))
                        "DEL" -> IconButton(
                            onClick = onBackspace,
                            modifier = Modifier.size(68.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete digit")
                        }
                        else -> FilledTonalButton(
                            onClick = { onDigit(key) },
                            shape = CircleShape,
                            modifier = Modifier.size(68.dp)
                        ) {
                            Text(key, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PatternLockGrid(
    onPatternCompleted: (String) -> Unit
) {
    val selectedNodes = remember { mutableStateListOf<Int>() }
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Draw your unlock pattern", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start ->
                            selectedNodes.clear()
                            dragPosition = start
                            hitTestPatternNode(start, canvasSize)?.let { selectedNodes.add(it) }
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragPosition = change.position
                            hitTestPatternNode(change.position, canvasSize)?.let { node ->
                                if (node !in selectedNodes) selectedNodes.add(node)
                            }
                        },
                        onDragEnd = {
                            dragPosition = null
                            if (selectedNodes.size >= 4) {
                                onPatternCompleted(selectedNodes.joinToString("-"))
                            }
                            selectedNodes.clear()
                        },
                        onDragCancel = {
                            dragPosition = null
                            selectedNodes.clear()
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centers = (0 until 9).map { idx -> nodeCenter(idx, size.width, size.height) }
                for (i in 0 until selectedNodes.size - 1) {
                    drawLine(
                        color = ArEmeraldBright,
                        start = centers[selectedNodes[i]],
                        end = centers[selectedNodes[i + 1]],
                        strokeWidth = 8f,
                        cap = StrokeCap.Round
                    )
                }
                val activeDrag = dragPosition
                if (activeDrag != null && selectedNodes.isNotEmpty()) {
                    drawLine(
                        color = ArEmeraldBright.copy(alpha = 0.6f),
                        start = centers[selectedNodes.last()],
                        end = activeDrag,
                        strokeWidth = 6f,
                        cap = StrokeCap.Round
                    )
                }
                centers.forEachIndexed { idx, offset ->
                    val isSelected = idx in selectedNodes
                    drawCircle(
                        color = if (isSelected) ArEmeraldBright else Color(0xFF8696A0),
                        radius = if (isSelected) 22f else 12f,
                        center = offset
                    )
                }
            }
        }
    }
}

private fun nodeCenter(index: Int, width: Float, height: Float): Offset {
    val row = index / 3
    val col = index % 3
    val stepX = width / 4f
    val stepY = height / 4f
    return Offset((col + 1) * stepX, (row + 1) * stepY)
}

private fun hitTestPatternNode(pos: Offset, size: IntSize): Int? {
    if (size.width == 0 || size.height == 0) return null
    val threshold = size.width / 8f
    for (i in 0 until 9) {
        val c = nodeCenter(i, size.width.toFloat(), size.height.toFloat())
        if (hypot(pos.x - c.x, pos.y - c.y) <= threshold) return i
    }
    return null
}
