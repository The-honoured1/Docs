package com.ceo3.docs.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// Confetti particle representation for high-juice micro-interactions
data class ConfettiParticle(
    val id: Int,
    var x: Float,
    var y: Float,
    val color: Color,
    val size: Float,
    var speedY: Float,
    var speedX: Float,
    var rotation: Float,
    val rotationSpeed: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonateScreen(
    onNavigateBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var selectedTier by remember { mutableStateOf<Int?>(1) } // 0: $2, 1: $5, 2: $10, 3: $25, 4: Custom
    var customAmount by remember { mutableStateOf("") }
    val customAmountVal = customAmount.toDoubleOrNull() ?: 0.0

    var isProcessing by remember { mutableStateOf(false) }
    var showThankYouDialog by remember { mutableStateOf(false) }

    // Particle state for thank-you celebration
    val particles = remember { mutableStateListOf<ConfettiParticle>() }
    var confettiActive by remember { mutableStateOf(false) }

    val tiers = listOf(
        DonateTier("☕ Coffee", 2.0, "Keeps developers awake & writing code"),
        DonateTier("🍰 Cake Slice", 5.0, "Brings happiness & sweet new features"),
        DonateTier("🍕 Pizza Pack", 10.0, "Fuels robust PDF tools development"),
        DonateTier("👑 Gold Patron", 25.0, "Includes your name in our contributors list")
    )

    val currentAmount = when (selectedTier) {
        in 0..3 -> tiers[selectedTier!!].amount
        4 -> customAmountVal
        else -> 0.0
    }

    // Confetti physics loop
    if (confettiActive) {
        LaunchedEffect(Unit) {
            val colors = listOf(
                Color(0xFFFFDF70), // Bright Yellow
                Color(0xFF9DD68A), // Accent Green
                Color(0xFFFFAA3B), // Accent Orange
                Color(0xFFCFC3FF), // Accent Purple
                Color(0xFFBCE3A6)  // Soft Green
            )
            // Initialize particles
            particles.clear()
            repeat(120) { id ->
                particles.add(
                    ConfettiParticle(
                        id = id,
                        x = Random.nextFloat() * 1000f,
                        y = -Random.nextFloat() * 400f,
                        color = colors.random(),
                        size = Random.nextFloat() * 15f + 10f,
                        speedY = Random.nextFloat() * 8f + 8f,
                        speedX = Random.nextFloat() * 6f - 3f,
                        rotation = Random.nextFloat() * 360f,
                        rotationSpeed = Random.nextFloat() * 4f - 2f
                    )
                )
            }

            // Physics frame steps
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 4000) {
                particles.forEach { p ->
                    p.y += p.speedY
                    p.x += p.speedX
                    p.rotation += p.rotationSpeed
                }
                delay(16) // ~60fps
            }
            confettiActive = false
            particles.clear()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Donate & Support",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                            RoundedCornerShape(24.dp)
                        )
                        .padding(24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "100% Free & Open Source",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "We don't believe in paywalls or premium licenses. All professional tools, scanners, OCR extraction, and PDF signatures are completely free for everyone. If you find value in our app, consider buying us a coffee!",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = "Select Support Tier",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Tier Grid
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tiers.forEachIndexed { index, tier ->
                        TierRowItem(
                            tier = tier,
                            selected = selectedTier == index,
                            onClick = { selectedTier = index }
                        )
                    }

                    // Custom Tier
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (selectedTier == 4) MaterialTheme.colorScheme.surfaceVariant
                                else MaterialTheme.colorScheme.surface
                            )
                            .border(
                                BorderStroke(
                                    if (selectedTier == 4) 2.dp else 1.dp,
                                    if (selectedTier == 4) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                ),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { selectedTier = 4 }
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Stars,
                                        contentDescription = null,
                                        tint = if (selectedTier == 4) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Custom Amount",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(
                                            if (selectedTier == 4) MaterialTheme.colorScheme.primary
                                            else Color.Transparent
                                        )
                                        .border(
                                            1.5.dp,
                                            if (selectedTier == 4) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            CircleShape
                                        )
                                        .size(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selectedTier == 4) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }

                            if (selectedTier == 4) {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = customAmount,
                                    onValueChange = { customAmount = it.filter { char -> char.isDigit() || char == '.' } },
                                    label = { Text("Enter Amount ($)") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Action Button
                Button(
                    onClick = {
                        scope.launch {
                            isProcessing = true
                            delay(1500) // Simulate elegant payment flow
                            isProcessing = false
                            confettiActive = true
                            showThankYouDialog = true
                        }
                    },
                    enabled = currentAmount > 0.0 && !isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(Icons.Filled.Payment, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (currentAmount > 0.0) "Support with $${String.format("%.2f", currentAmount)}" else "Support Application",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Secured sandbox payment processing.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(40.dp))
            }

            // Celebration Confetti Canvas
            if (confettiActive) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    particles.forEach { p ->
                        drawCircle(
                            color = p.color,
                            radius = p.size / 2,
                            center = Offset(p.x, p.y)
                        )
                    }
                }
            }
        }
    }

    // Thank you Dialog
    if (showThankYouDialog) {
        AlertDialog(
            onDismissRequest = { showThankYouDialog = false },
            confirmButton = {
                Button(
                    onClick = { showThankYouDialog = false },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("You're Welcome!")
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Filled.CardGiftcard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    "Thank You for Your Support!",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    "Your generous contribution of $${String.format("%.2f", currentAmount)} makes it possible for us to maintain Docs as a free, open-source, and premium-quality office alternative for everyone. We appreciate your backing!",
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

data class DonateTier(
    val name: String,
    val amount: Double,
    val description: String
)

@Composable
fun TierRowItem(
    tier: DonateTier,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    val borderCol = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(
                BorderStroke(if (selected) 2.dp else 1.dp, borderCol),
                RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tier.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tier.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$${String.format("%.0f", tier.amount)}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
                        .border(
                            1.5.dp,
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            CircleShape
                        )
                        .size(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
