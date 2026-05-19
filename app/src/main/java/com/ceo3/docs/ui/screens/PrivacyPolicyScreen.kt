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
fun PrivacyPolicyScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy") },
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
            Text("Privacy Policy", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Last updated: May 2026", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Text("1. Information We Collect", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("We only collect the information you choose to give us, and we process it with your consent, or on another legal basis; we only require the minimum amount of personal information that is necessary to fulfill the purpose of your interaction with us.", style = MaterialTheme.typography.bodyMedium)
            
            Text("2. How We Use Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("We use the information we collect to operate, maintain, and provide you with the features and functionality of the Application, as well as to communicate directly with you.", style = MaterialTheme.typography.bodyMedium)
            
            Text("3. Data Security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("We use industry-standard security measures to protect the loss, misuse, and alteration of the information under our control. All documents are stored locally on your device unless you explicitly enable cloud sync.", style = MaterialTheme.typography.bodyMedium)
            
            Text("4. Local Storage & Permissions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Our application requires access to your device's storage to save and retrieve your documents. We do not access other files on your device.", style = MaterialTheme.typography.bodyMedium)
            
            Text("5. Contact Us", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("If you have any questions about this Privacy Policy, please contact us at privacy@ceo3.docs.", style = MaterialTheme.typography.bodyMedium)
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
