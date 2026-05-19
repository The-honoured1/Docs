package com.ceo3.docs.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    val savedPasscode by settingsManager?.passcodeFlow?.collectAsState(initial = "") ?: remember { mutableStateOf("") }
    
    var showPasscodeDialog by remember { mutableStateOf(false) }
    var newPasscode by remember { mutableStateOf("") }
    var isChangingPasscode by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
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
                    onCheckedChange = { isChecked ->
                        if (isChecked && savedPasscode.isEmpty()) {
                            // Prompt to set passcode first
                            isChangingPasscode = false
                            showPasscodeDialog = true
                        } else {
                            coroutineScope.launch { settingsManager?.setRequirePasscode(isChecked) }
                        }
                    }
                )
            }
            
            if (requirePasscode) {
                Button(
                    onClick = { 
                        isChangingPasscode = true
                        newPasscode = ""
                        showPasscodeDialog = true
                    },
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
        
        if (showPasscodeDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showPasscodeDialog = false 
                    newPasscode = ""
                    errorMessage = null
                },
                title = { Text(if (isChangingPasscode) "Change Passcode" else "Set Passcode") },
                text = {
                    Column {
                        Text("Enter a 4-digit PIN")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPasscode,
                            onValueChange = { 
                                if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                    newPasscode = it
                                    errorMessage = null
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            isError = errorMessage != null
                        )
                        if (errorMessage != null) {
                            Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (newPasscode.length == 4) {
                            coroutineScope.launch {
                                settingsManager?.setPasscode(newPasscode)
                                if (!isChangingPasscode) {
                                    settingsManager?.setRequirePasscode(true)
                                }
                                showPasscodeDialog = false
                                newPasscode = ""
                            }
                        } else {
                            errorMessage = "Passcode must be 4 digits"
                        }
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        showPasscodeDialog = false
                        newPasscode = ""
                        errorMessage = null
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
