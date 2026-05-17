package com.ceo3.docs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ceo3.docs.ui.navigation.DocsApp
import com.ceo3.docs.ui.theme.DocsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DocsTheme {
                DocsApp()
            }
        }
    }
}