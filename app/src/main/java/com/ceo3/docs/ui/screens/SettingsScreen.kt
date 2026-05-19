package com.ceo3.docs.ui.screens

import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ceo3.docs.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- Header ---
        Text(
            text = "Settings",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(top = 56.dp, bottom = 12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp, start = 24.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- User Profile Card ---
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profile Avatar Placeholder
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE5E7EB)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "AM",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4B5563)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Alex Mercer",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "alex.mercer@docs.app",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // --- Premium Card ---
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Docs Premium",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "PRO",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Unlock advanced PDF tools, unlimited OCR scanning, and cloud backup integration.",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { /* Upgrade action */ },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Upgrade Now",
                                color = Color(0xFF4F46E5),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // --- Settings Options ---
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    SettingsItem(
                        icon = Icons.Filled.AccountCircle,
                        iconColor = Color(0xFF3B82F6),
                        title = "Account Settings"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Filled.CloudQueue,
                        iconColor = Color(0xFF10B981),
                        title = "Cloud Sync & Backup"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Filled.Palette,
                        iconColor = Color(0xFFEC4899),
                        title = "Theme Settings"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Filled.Security,
                        iconColor = Color(0xFFF59E0B),
                        title = "Security & Passcode"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Filled.HelpOutline,
                        iconColor = Color(0xFF8B5CF6),
                        title = "Help & Support"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f), modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Filled.Info,
                        iconColor = Color(0xFF6B7280),
                        title = "About Docs"
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    iconColor: Color,
    title: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Handle click */ }
            .padding(vertical = 14.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp)
        )
    }
}
