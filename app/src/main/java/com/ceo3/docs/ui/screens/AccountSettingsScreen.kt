package com.ceo3.docs.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ceo3.docs.data.settings.SettingsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    onNavigateBack: () -> Unit,
    settingsManager: SettingsManager? = null
) {
    val coroutineScope = rememberCoroutineScope()
    
    val initialName by settingsManager?.userNameFlow?.collectAsState(initial = "Alex Mercer") ?: remember { mutableStateOf("Alex Mercer") }
    val initialEmail by settingsManager?.userEmailFlow?.collectAsState(initial = "alex.mercer@docs.app") ?: remember { mutableStateOf("alex.mercer@docs.app") }
    
    var name by remember(initialName) { mutableStateOf(initialName) }
    var email by remember(initialEmail) { mutableStateOf(initialEmail) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account Settings") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { 
                    name = it 
                    coroutineScope.launch { settingsManager?.setUserName(it) }
                },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = email,
                onValueChange = { 
                    email = it
                    coroutineScope.launch { settingsManager?.setUserEmail(it) }
                },
                label = { Text("Email Address") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { /* TODO: Change Password */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Change Password")
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            OutlinedButton(
                onClick = { /* TODO: Delete Account */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete Account", fontWeight = FontWeight.Bold)
            }
        }
    }
}
