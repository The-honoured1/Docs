package com.ceo3.docs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ceo3.docs.ui.navigation.DocsApp
import com.ceo3.docs.ui.theme.DocsTheme

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.ceo3.docs.data.settings.SettingsManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsManager = remember { SettingsManager(applicationContext) }
            val themeSetting by settingsManager.themeFlow.collectAsState(initial = "System Default")
            
            DocsTheme(themeSetting = themeSetting) {
                DocsApp(settingsManager = settingsManager)
            }
        }
    }
}