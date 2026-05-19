package com.ceo3.docs.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

    // Pulse animation for the heart icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val tiers = listOf(
        DonateTier("☕ Fresh Coffee", 2.0, "Keeps developers awake & writing code", Color(0xFFFFB84D)),
        DonateTier("🍰 Cake Slice", 5.0, "Brings happiness & sweet features", Color(0xFFFFAA3B)),
        DonateTier("🍕 Pizza Pack", 10.0, "Fuels robust PDF tools development", Color(0xFFE57373)),
        DonateTier("👑 Gold Patron", 25.0, "Includes your name in our contributors", Color(0xFFCFC3FF))
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
                Color(0xFFBCE3A6), // Soft Green
                Color(0xFFFF8A80)  // Pastel Red
            )
            particles.clear()
            repeat(150) { id ->
                particles.add(
                    ConfettiParticle(
                        id = id,
                        x = Random.nextFloat() * 1200f,
                        y = -Random.nextFloat() * 600f,
                        color = colors.random(),
                        size = Random.nextFloat() * 18f + 10f,
                        speedY = Random.nextFloat() * 10f + 7f,
                        speedX = Random.nextFloat() * 8f - 4f,
                        rotation = Random.nextFloat() * 360f,
                        rotationSpeed = Random.nextFloat() * 6f - 3f
                    )
                )
            }

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
            CenterAlignedTopAppBar(
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
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
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
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Banner using beautiful premium Indigo-to-Blue Linear Gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF7C3AED), Color(0xFF2563EB))
                            )
                        )
                        .shadow(8.dp, RoundedCornerShape(24.dp))
                        .padding(24.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "SUPPORT THE DOCS REVOLUTION",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "We believe in professional tools for everyone with absolutely no paywalls, hidden premiums, or ad interruptions. Scanning, spellchecking, translations, and PDF editing are 100% free. If our application adds value to your life, consider backing us!",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.9f),
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

                // Tier Cards Grid
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

                    // Custom input tier card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                BorderStroke(
                                    if (selectedTier == 4) 2.dp else 1.dp,
                                    if (selectedTier == 4) Color(0xFF7C3AED)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                ),
                                RoundedCornerShape(16.dp)
                            )
                            .shadow(if (selectedTier == 4) 4.dp else 1.dp, RoundedCornerShape(16.dp))
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
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFE2E8F0)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Stars,
                                            contentDescription = null,
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Custom Contribution",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(
                                            if (selectedTier == 4) Color(0xFF7C3AED)
                                            else Color.Transparent
                                        )
                                        .border(
                                            1.5.dp,
                                            if (selectedTier == 4) Color(0xFF7C3AED)
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
                                            tint = Color.White,
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

                Spacer(modifier = Modifier.height(28.dp))

                // Elegant interactive card sandbox display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                            )
                        )
                        .shadow(6.dp, RoundedCornerShape(20.dp))
                        .padding(20.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Secure Premium Sandbox", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Filled.Nfc, contentDescription = null, tint = Color.White.copy(alpha = 0.8f))
                        }

                        // Gold card chip simulator
                        Box(
                            modifier = Modifier
                                .size(36.dp, 28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFFDF70))
                        )

                        Column {
                            Text(
                                text = "SUPPORT AMOUNT",
                                fontSize = 9.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$${String.format("%.2f", currentAmount)} USD",
                                fontSize = 22.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Support Payment Button
                Button(
                    onClick = {
                        scope.launch {
                            isProcessing = true
                            delay(1800) // Beautiful transaction validation simulator
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
                        containerColor = Color(0xFF7C3AED),
                        contentColor = Color.White
                    )
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(Icons.Filled.Payment, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (currentAmount > 0.0) "Transact $${String.format("%.2f", currentAmount)}" else "Support Application",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "No real money is charged. Fully offline sandbox demo environment.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(40.dp))
            }

            // Confetti canvas overlays
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

    // Thank you celebration dialogue
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
                    tint = Color(0xFF7C3AED),
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    "Support Received!",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    "Your generous backing of $${String.format("%.2f", currentAmount)} helps keep professional office and OCR tools available for everyone around the world for free!",
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
    val description: String,
    val iconColor: Color
)

@Composable
fun TierRowItem(
    tier: DonateTier,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = MaterialTheme.colorScheme.surface
    val borderCol = if (selected) Color(0xFF7C3AED) else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(
                BorderStroke(if (selected) 2.dp else 1.dp, borderCol),
                RoundedCornerShape(16.dp)
            )
            .shadow(if (selected) 4.dp else 1.dp, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tier.iconColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = when (tier.amount.toInt()) {
                        2 -> Icons.Filled.Coffee
                        5 -> Icons.Filled.Cake
                        10 -> Icons.Filled.LocalPizza
                        else -> Icons.Filled.WorkspacePremium
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = tier.name,
                        tint = tier.iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = tier.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tier.description,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$${String.format("%.0f", tier.amount)}",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = Color(0xFF7C3AED)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) Color(0xFF7C3AED)
                            else Color.Transparent
                        )
                        .border(
                            1.5.dp,
                            if (selected) Color(0xFF7C3AED)
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
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
