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
fun TermsOfServiceScreen(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terms of Service") },
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
            Text("Terms of Service", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Last updated: May 2026", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Text("1. Acceptance of Terms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("By accessing and using this application, you accept and agree to be bound by the terms and provision of this agreement. In addition, when using these particular services, you shall be subject to any posted guidelines or rules applicable to such services.", style = MaterialTheme.typography.bodyMedium)
            
            Text("2. Description of Service", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("We provide users with access to a rich collection of resources, including various document editing tools, formatting capabilities, and local document management features.", style = MaterialTheme.typography.bodyMedium)
            
            Text("3. User Conduct", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("You agree to use the Service only for purposes that are permitted by the Terms and any applicable law, regulation, or generally accepted practices or guidelines in the relevant jurisdictions.", style = MaterialTheme.typography.bodyMedium)
            
            Text("4. Modifications to Service", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("We reserve the right at any time and from time to time to modify or discontinue, temporarily or permanently, the Service (or any part thereof) with or without notice.", style = MaterialTheme.typography.bodyMedium)
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
