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
import androidx.compose.ui.draw.rotate
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

// Confetti particle representation for high-juice celebration
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

    var selectedTier by remember { mutableStateOf<Int?>(1) } // 0: $3, 1: $8, 2: $15, 3: $35, 4: Custom
    var customAmount by remember { mutableStateOf("") }
    val customAmountVal = customAmount.toDoubleOrNull() ?: 0.0

    var isProcessing by remember { mutableStateOf(false) }
    var showThankYouDialog by remember { mutableStateOf(false) }

    // Particle state for celebration
    val particles = remember { mutableStateListOf<ConfettiParticle>() }
    var confettiActive by remember { mutableStateOf(false) }

    // Pulse animation for the heart icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Rotating ring around heart
    val rotateAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val tiers = listOf(
        DonateTier("☕ App Coffee Support", 3.0, "Keeps the developer highly caffeinated and focused", Color(0xFFFBBF24), Icons.Filled.Coffee),
        DonateTier("🍰 Sweet Appreciation", 8.0, "Sponsors a slice of cake for code achievements", Color(0xFFEC4899), Icons.Filled.Cake),
        DonateTier("🍕 Dev Pizza Feast", 15.0, "Funds a hot cheese pizza for late-night feature builds", Color(0xFF10B981), Icons.Filled.LocalPizza),
        DonateTier("👑 Royal Sponsor", 35.0, "Enrolls you in the ultimate premium hall of fame", Color(0xFF6366F1), Icons.Filled.WorkspacePremium)
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
                Color(0xFFFFDF70), // Gold
                Color(0xFF60A5FA), // Blue
                Color(0xFF34D399), // Emerald
                Color(0xFFF472B6), // Pink
                Color(0xFFA78BFA)  // Violet
            )
            particles.clear()
            repeat(160) { id ->
                particles.add(
                    ConfettiParticle(
                        id = id,
                        x = Random.nextFloat() * 1400f,
                        y = -Random.nextFloat() * 800f - 100f,
                        color = colors.random(),
                        size = Random.nextFloat() * 16f + 8f,
                        speedY = Random.nextFloat() * 12f + 6f,
                        speedX = Random.nextFloat() * 6f - 3f,
                        rotation = Random.nextFloat() * 360f,
                        rotationSpeed = Random.nextFloat() * 8f - 4f
                    )
                )
            }

            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 4200) {
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
                        "Sponsor Application",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        letterSpacing = 0.5.sp,
                        color = Color(0xFF1E293B)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF1E293B)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFFF8FAFC)
                )
            )
        },
        containerColor = Color(0xFFF8FAFC) // Sleek cool-white background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Banner using beautiful premium Indigo-to-Blue Linear Gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF4F46E5), Color(0xFFEC4899)),
                                start = Offset.Zero,
                                end = Offset.Infinite
                            )
                        )
                        .shadow(12.dp, RoundedCornerShape(24.dp))
                        .padding(28.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(90.dp)
                        ) {
                            // Rotating glassmorphic ring
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(
                                        width = 3.dp,
                                        brush = Brush.sweepGradient(
                                            colors = listOf(Color.White.copy(0.1f), Color.White, Color.White.copy(0.1f))
                                        ),
                                        shape = CircleShape
                                    )
                                    .rotate(rotateAngle)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
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
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = "SUPPORT OUR APP IN STYLE",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "If you enjoy our document suite, premium slide editor, and scan utilities, consider buying the dev team a coffee or treat! We keep the app 100% offline, private, and clean.",
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
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B),
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
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .border(
                                BorderStroke(
                                    width = if (selectedTier == 4) 2.dp else 1.dp,
                                    color = if (selectedTier == 4) Color(0xFF6366F1) else Color(0xFFE2E8F0)
                                ),
                                RoundedCornerShape(18.dp)
                            )
                            .shadow(if (selectedTier == 4) 6.dp else 1.dp, RoundedCornerShape(18.dp))
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
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFFF1F5F9)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = null,
                                            tint = Color(0xFF64748B),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = "Custom Contribution",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1E293B)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(
                                            if (selectedTier == 4) Color(0xFF6366F1)
                                            else Color.Transparent
                                        )
                                        .border(
                                            1.5.dp,
                                            if (selectedTier == 4) Color(0xFF6366F1)
                                            else Color(0xFFCBD5E1),
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

                // Realistic Holographic dark credit card simulation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF1E1E2E), Color(0xFF11111B), Color(0xFF313244)),
                                start = Offset.Zero,
                                end = Offset.Infinite
                            )
                        )
                        .border(1.dp, Color.White.copy(0.15f), RoundedCornerShape(22.dp))
                        .shadow(12.dp, RoundedCornerShape(22.dp))
                        .padding(24.dp)
                ) {
                    // Hologram mesh background effect
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Color(0xFFEC4899).copy(alpha = 0.08f),
                            radius = 260f,
                            center = Offset(size.width, 0f)
                        )
                        drawCircle(
                            color = Color(0xFF4F46E5).copy(alpha = 0.08f),
                            radius = 260f,
                            center = Offset(0f, size.height)
                        )
                    }
                    
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DOCS SUITE PREMIUM",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Icon(
                                imageVector = Icons.Filled.Nfc,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Gold card chip simulator
                        Box(
                            modifier = Modifier
                                .size(40.dp, 30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFFDE047), Color(0xFFCA8A04))
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = "SUPPORT SPONSORSHIP",
                                    fontSize = 8.sp,
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$${String.format("%.2f", currentAmount)} USD",
                                    fontSize = 24.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            // Mastercard-style stylized double bubble logo
                            Row {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF43F5E).copy(alpha = 0.85f))
                                )
                                Spacer(modifier = Modifier.width(-8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF59E0B).copy(alpha = 0.85f))
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                // Premium Support Payment Button
                Button(
                    onClick = {
                        scope.launch {
                            isProcessing = true
                            delay(1800) // validation simulation
                            isProcessing = false
                            confettiActive = true
                            showThankYouDialog = true
                        }
                    },
                    enabled = currentAmount > 0.0 && !isProcessing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4F46E5),
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
                            text = if (currentAmount > 0.0) "Contribute $${String.format("%.2f", currentAmount)}" else "Support Application",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Offline demo sandbox environment. No real funds are moved.",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
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
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5))
                ) {
                    Text("You're Welcome!", fontWeight = FontWeight.Bold)
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Filled.CardGiftcard,
                    contentDescription = null,
                    tint = Color(0xFFEC4899),
                    modifier = Modifier.size(44.dp)
                )
            },
            title = {
                Text(
                    "Sponsorship Received!",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    "Your incredibly generous support of $${String.format("%.2f", currentAmount)} makes a huge difference! We really appreciate your backing in building premium utility applications.",
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = Color(0xFF475569)
                )
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White
        )
    }
}

data class DonateTier(
    val name: String,
    val amount: Double,
    val description: String,
    val iconColor: Color,
    val icon: ImageVector
)

@Composable
fun TierRowItem(
    tier: DonateTier,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(
                BorderStroke(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) Color(0xFF6366F1) else Color(0xFFE2E8F0)
                ),
                RoundedCornerShape(18.dp)
            )
            .shadow(if (selected) 6.dp else 1.dp, RoundedCornerShape(18.dp))
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
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tier.iconColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tier.icon,
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
                        color = Color(0xFF1E293B)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tier.description,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$${String.format("%.0f", tier.amount)}",
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    color = Color(0xFF4F46E5)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) Color(0xFF6366F1)
                            else Color.Transparent
                        )
                        .border(
                            1.5.dp,
                            if (selected) Color(0xFF6366F1)
                            else Color(0xFFCBD5E1),
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
