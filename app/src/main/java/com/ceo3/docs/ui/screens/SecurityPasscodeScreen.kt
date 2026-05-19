package com.ceo3.docs.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ceo3.docs.data.settings.SettingsManager
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
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security & Passcode") },
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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Require Passcode", style = MaterialTheme.typography.titleMedium)
                    Text("Lock the app with a PIN", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = requirePasscode, 
                    onCheckedChange = { 
                        coroutineScope.launch { settingsManager?.setRequirePasscode(it) } 
                    }
                )
            }
            
            if (requirePasscode) {
                Button(
                    onClick = { /* TODO: Change Passcode */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Change Passcode")
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Use Biometrics", style = MaterialTheme.typography.titleMedium)
                        Text("Unlock with Face/Fingerprint", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = useBiometrics, 
                        onCheckedChange = { 
                            coroutineScope.launch { settingsManager?.setUseBiometrics(it) }
                        }
                    )
                }
            }
        }
    }
}
