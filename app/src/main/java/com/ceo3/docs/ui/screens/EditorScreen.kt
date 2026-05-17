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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class EditorViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState
    private var currentDocumentId: String? = null
    private var documentType: String = "TXT"

    private val converter = com.ceo3.docs.domain.DocumentConverter(application)
    private val ocrEngine = com.ceo3.docs.domain.OcrEngine()

    fun loadDocument(id: String) {
        currentDocumentId = id
        viewModelScope.launch {
            val dao = com.ceo3.docs.data.local.DocDatabase.getDatabase(getApplication()).documentDao()
            val docEntity = dao.getDocumentById(id)
            documentType = docEntity?.type ?: "TXT"

            val ext = if (documentType == "PDF") "pdf" else "txt"
            val file = File(getApplication<android.app.Application>().filesDir, "doc_${id}.$ext")
            
            if (file.exists()) {
                val content = if (documentType == "PDF") {
                    val ocrResult = converter.pdfToText(file, ocrEngine)
                    ocrResult.getOrDefault("")
                } else {
                    file.readText()
                }
                _uiState.value = _uiState.value.copy(content = content, title = docEntity?.title ?: "Document $id")
            } else {
                _uiState.value = _uiState.value.copy(content = "", title = docEntity?.title ?: "New Document")
            }
        }
    }

    fun onContentChanged(newContent: String) {
        _uiState.value = _uiState.value.copy(content = newContent)
    }

    fun saveDocument() {
        val id = currentDocumentId ?: return
        viewModelScope.launch {
            val ext = if (documentType == "PDF") "pdf" else "txt"
            val file = File(getApplication<android.app.Application>().filesDir, "doc_${id}.$ext")
            
            if (documentType == "PDF") {
                converter.textToPdf(_uiState.value.content, file)
            } else {
                file.writeText(_uiState.value.content)
            }
            
            // Also update room DB
            val dao = com.ceo3.docs.data.local.DocDatabase.getDatabase(getApplication()).documentDao()
            dao.insertDocument(
                com.ceo3.docs.data.local.DocumentEntity(
                    id = id,
                    title = _uiState.value.title,
                    type = documentType,
                    lastModified = System.currentTimeMillis(),
                    isPinned = false,
                    tags = "Personal"
                )
            )
        }
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
    viewModel: EditorViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    LaunchedEffect(documentId) {
        viewModel.loadDocument(documentId)
    }

    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(state.title) },
                    navigationIcon = {
                        IconButton(onClick = { 
                            viewModel.saveDocument()
                            onNavigateBack() 
                        }) {
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
