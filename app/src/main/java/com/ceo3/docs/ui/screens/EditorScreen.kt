package com.ceo3.docs.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class EditorViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState

    fun onContentChanged(newContent: String) {
        _uiState.value = _uiState.value.copy(content = newContent)
    }
}

data class EditorUiState(
    val title: String = "Untitled Document",
    val content: String = "",
    val isBoldEnabled: Boolean = false,
    val isItalicEnabled: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    documentId: String,
    onNavigateBack: () -> Unit,
    viewModel: EditorViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(state.title) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                // Formatting Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = { /* Toggle Bold */ }) {
                        Icon(Icons.Filled.FormatBold, contentDescription = "Bold")
                    }
                    IconButton(onClick = { /* Toggle Italic */ }) {
                        Icon(Icons.Filled.FormatItalic, contentDescription = "Italic")
                    }
                    IconButton(onClick = { /* Add List */ }) {
                        Icon(Icons.Filled.FormatListBulleted, contentDescription = "Bullet List")
                    }
                    IconButton(onClick = { /* Add Image */ }) {
                        Icon(Icons.Filled.Image, contentDescription = "Insert Image")
                    }
                    IconButton(onClick = { /* Add Table */ }) {
                        Icon(Icons.Filled.TableChart, contentDescription = "Insert Table")
                    }
                }
                Divider()
            }
        }
    ) { paddingValues ->
        TextField(
            value = state.content,
            onValueChange = viewModel::onContentChanged,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            placeholder = { Text("Start typing...") },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
            )
        )
    }
}
