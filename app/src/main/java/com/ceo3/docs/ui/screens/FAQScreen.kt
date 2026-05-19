package com.ceo3.docs.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FAQScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Frequently Asked Questions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("FAQ", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            
            FAQItem("How do I scan a document?", "Navigate to the Home screen or Tools screen and tap the floating action button or 'Scanner' tool. Point your camera at the document and capture.")
            FAQItem("Are my documents secure?", "Yes, all your documents are stored locally on your device. You can also set a passcode or use biometrics to restrict access to the app.")
            FAQItem("How do I sync to the cloud?", "Go to Settings > Cloud Sync & Backup and sign in to your preferred cloud provider. Ensure Auto-sync is enabled.")
            FAQItem("How do I highlight a PDF?", "Open a PDF document. Tap the Highlight icon in the tools overlay. Then, draw your finger across the text to highlight it.")
            FAQItem("How does my donation help children in Africa?", "All donations are routed directly to partner charities that provide educational materials, nutritious meals, and support infrastructure for children in underserved communities across Africa.")
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun FAQItem(question: String, answer: String) {
    Column {
        Text(question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(answer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
