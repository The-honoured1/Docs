package com.ceo3.docs.ui.theme

import androidx.compose.ui.graphics.Color

// ── Brand Core ───────────────────────────────────────────────────────────────
val Brand           = Color(0xFF1A1A2E)   // Deep navy — primary brand
val BrandAccent     = Color(0xFF4F46E5)   // Indigo — vibrant CTA accent
val BrandHighlight  = Color(0xFF7C3AED)   // Purple — gradient partner

// ── Semantic accent tints ─────────────────────────────────────────────────────
val AccentAmber     = Color(0xFFF59E0B)   // File type badge / warning
val AccentEmerald   = Color(0xFF10B981)   // Success / TXT
val AccentRose      = Color(0xFFEF4444)   // PDF / Danger
val AccentSky       = Color(0xFF3B82F6)   // DOCX / Info
val AccentViolet    = Color(0xFF8B5CF6)   // Tools / AI

// ── Light palette ─────────────────────────────────────────────────────────────
val BackgroundLight     = Color(0xFFF4F5F9)   // Slightly warm off-white canvas
val SurfaceLight        = Color(0xFFFFFFFF)   // Card surface
val SurfaceElevated     = Color(0xFFEEEFF6)   // Subtle elevated surface
val OnBackgroundLight   = Color(0xFF0F0F1A)   // Near-black body text
val OnSurfaceLight      = Color(0xFF1C1C30)   // Strong surface text
val OnSurfaceVariantL   = Color(0xFF6B7280)   // Muted sub-text
val DividerLight        = Color(0xFFE5E7EB)   // Hairline separators

// ── Dark palette ──────────────────────────────────────────────────────────────
val BackgroundDark      = Color(0xFF0A0A14)   // Absolute deep black-blue
val SurfaceDark         = Color(0xFF12121F)   // Raised card surface
val SurfaceVariantDark  = Color(0xFF1E1E30)   // Second-level surface
val OnSurfaceDark       = Color(0xFFE8E8F4)   // Bright body text
val OnSurfaceVariantDk  = Color(0xFF9CA3AF)   // Muted sub-text
val DividerDark         = Color(0xFF2D2D42)   // Subtle separators

// ── Gradient helpers ──────────────────────────────────────────────────────────
val GradientStart       = BrandAccent
val GradientEnd         = BrandHighlight