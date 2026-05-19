package com.ceo3.docs.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ceo3.docs.data.settings.SettingsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityPasscodeScreen(
    onNavigateBack: () -> Unit,
    settingsManager: SettingsManager? = null
) {
    val coroutineScope = rememberCoroutineScope()
    
    val requirePasscode by settingsManager?.requirePasscodeFlow?.collectAsState(initial = false) ?: remember { mutableStateOf(false) }
    val useBiometrics by settingsManager?.useBiometricsFlow?.collectAsState(initial = false) ?: remember { mutableStateOf(false) }
    val savedPasscode by settingsManager?.passcodeFlow?.collectAsState(initial = "") ?: remember { mutableStateOf("") }
    
    var showPasscodeSheet by remember { mutableStateOf(false) }
    var currentPinInput by remember { mutableStateOf("") }
    var isChangingPasscode by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Enter a new 4-digit PIN") }
    var isErrorState by remember { mutableStateOf(false) }

    // Color definitions matching the Docs system
    val brandViolet = Color(0xFF6366F1)
    val brandIndigo = Color(0xFF4F46E5)
    val lightSlate = Color(0xFFF8FAFC)
    val darkSlate = Color(0xFF1E293B)
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Security Center",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        color = darkSlate
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = darkSlate
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = lightSlate
                )
            )
        },
        containerColor = lightSlate
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Shield graphic banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(brandViolet, brandIndigo)
                            )
                        )
                        .shadow(6.dp, RoundedCornerShape(24.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (requirePasscode) Icons.Filled.Shield else Icons.Outlined.Shield,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (requirePasscode) "ENCRYPTED VAULT LOCK ACTIVE" else "APP UNPROTECTED",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (requirePasscode) "Docs, PDFs, and Presentations are safely secured under dynamic device passcode locks." else "Set up a passcode locker to protect offline documents and templates from unauthorized access.",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Passcode Toggle row card
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(1.dp, RoundedCornerShape(18.dp))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(brandViolet.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Lock,
                                        contentDescription = null,
                                        tint = brandViolet,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        "Require Passcode Lock",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = darkSlate
                                    )
                                    Text(
                                        "Locks document lists on open",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                            Switch(
                                checked = requirePasscode,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = brandIndigo
                                ),
                                onCheckedChange = { isChecked ->
                                    if (isChecked && savedPasscode.isEmpty()) {
                                        isChangingPasscode = false
                                        currentPinInput = ""
                                        statusMessage = "Enter a new 4-digit PIN"
                                        showPasscodeSheet = true
                                    } else {
                                        coroutineScope.launch { settingsManager?.setRequirePasscode(isChecked) }
                                    }
                                }
                            )
                        }

                        if (requirePasscode) {
                            HorizontalDivider(
                                color = Color.LightGray.copy(alpha = 0.25f),
                                modifier = Modifier.padding(vertical = 14.dp)
                            )
                            Button(
                                onClick = {
                                    isChangingPasscode = true
                                    currentPinInput = ""
                                    statusMessage = "Enter new 4-digit PIN"
                                    showPasscodeSheet = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF1F5F9),
                                    contentColor = darkSlate
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Icon(Icons.Filled.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Change Dynamic PIN", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // Biometrics card row
                if (requirePasscode) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(1.dp, RoundedCornerShape(18.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF10B981).copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Fingerprint,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        "Use Biometrics Lock",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = darkSlate
                                    )
                                    Text(
                                        "Fingerprint / Face Unlock",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                            Switch(
                                checked = useBiometrics,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF10B981)
                                ),
                                onCheckedChange = {
                                    coroutineScope.launch { settingsManager?.setUseBiometrics(it) }
                                }
                            )
                        }
                    }
                }
            }

            // Custom Interactive Vault Pin Pad Overlay (Full screen bottom-sheet vibe)
            AnimatedVisibility(
                visible = showPasscodeSheet,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x990F172A)) // Dark dim background
                        .clickable { /* Block clicks */ },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Card(
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.85f)
                            .shadow(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Top Bar indicators
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { showPasscodeSheet = false }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = darkSlate)
                                }
                                Text(
                                    text = if (isChangingPasscode) "CHANGE PIN" else "SET VAULT PIN",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
                                    letterSpacing = 1.sp,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.size(48.dp)) // Spacer to keep balance
                            }

                            // Passcode display indicators
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                Icon(
                                    imageVector = if (isErrorState) Icons.Filled.LockPerson else Icons.Filled.VpnKey,
                                    contentDescription = null,
                                    tint = if (isErrorState) Color.Red else brandViolet,
                                    modifier = Modifier.size(44.dp)
                                )

                                Text(
                                    text = statusMessage,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isErrorState) Color.Red else darkSlate
                                )

                                // Glowing Dots row representation
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    repeat(4) { idx ->
                                        val isActive = idx < currentPinInput.length
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isActive) {
                                                        if (isErrorState) Color.Red else brandViolet
                                                    } else {
                                                        Color.LightGray.copy(alpha = 0.4f)
                                                    }
                                                )
                                                .border(
                                                    width = 1.5.dp,
                                                    color = if (isActive) Color.Transparent else Color.LightGray,
                                                    shape = CircleShape
                                                )
                                                .shadow(
                                                    elevation = if (isActive) 4.dp else 0.dp,
                                                    shape = CircleShape,
                                                    ambientColor = brandViolet,
                                                    spotColor = brandViolet
                                                )
                                        )
                                    }
                                }
                            }

                            // Custom Tactical Grid Numeric Keypad
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .padding(bottom = 12.dp)
                            ) {
                                val keys = listOf(
                                    listOf("1", "2", "3"),
                                    listOf("4", "5", "6"),
                                    listOf("7", "8", "9"),
                                    listOf("C", "0", "⌫")
                                )

                                keys.forEach { rowKeys ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        rowKeys.forEach { key ->
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1.6f)
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(
                                                        if (key.isEmpty()) Color.Transparent
                                                        else if (key == "⌫" || key == "C") Color(0xFFF1F5F9)
                                                        else Color(0xFFF8FAFC)
                                                    )
                                                    .clickable(enabled = key.isNotEmpty()) {
                                                        isErrorState = false
                                                        when (key) {
                                                            "⌫" -> {
                                                                if (currentPinInput.isNotEmpty()) {
                                                                    currentPinInput = currentPinInput.dropLast(1)
                                                                }
                                                            }
                                                            "C" -> {
                                                                currentPinInput = ""
                                                            }
                                                            else -> {
                                                                if (currentPinInput.length < 4) {
                                                                    currentPinInput += key
                                                                    if (currentPinInput.length == 4) {
                                                                        // Auto validate
                                                                        coroutineScope.launch {
                                                                            delay(200)
                                                                            settingsManager?.setPasscode(currentPinInput)
                                                                            if (!isChangingPasscode) {
                                                                                settingsManager?.setRequirePasscode(true)
                                                                            }
                                                                            showPasscodeSheet = false
                                                                            currentPinInput = ""
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (key == "⌫") {
                                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Backspace", tint = darkSlate)
                                                } else {
                                                    Text(
                                                        text = key,
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = if (key == "C") Color.Red else darkSlate
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
