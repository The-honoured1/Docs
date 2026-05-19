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
fun CloudSyncBackupScreen(
    onNavigateBack: () -> Unit,
    settingsManager: SettingsManager? = null
) {
    val coroutineScope = rememberCoroutineScope()
    
    val autoSync by settingsManager?.autoSyncFlow?.collectAsState(initial = true) ?: remember { mutableStateOf(true) }
    val syncWifiOnly by settingsManager?.syncWifiOnlyFlow?.collectAsState(initial = true) ?: remember { mutableStateOf(true) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Sync & Backup") },
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
                    Text("Auto-Sync", style = MaterialTheme.typography.titleMedium)
                    Text("Keep documents backed up automatically", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = autoSync, 
                    onCheckedChange = { 
                        coroutineScope.launch { settingsManager?.setAutoSync(it) } 
                    }
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Sync over Wi-Fi only", style = MaterialTheme.typography.titleMedium)
                    Text("Save cellular data", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = syncWifiOnly, 
                    onCheckedChange = { 
                        coroutineScope.launch { settingsManager?.setSyncWifiOnly(it) } 
                    },
                    enabled = autoSync
                )
            }
            
            HorizontalDivider()
            
            Column {
                Text("Last Backup", style = MaterialTheme.typography.titleMedium)
                Text("Today at 3:45 AM", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { /* TODO: Trigger manual backup */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Backup Now")
                }
            }
        }
    }
}
