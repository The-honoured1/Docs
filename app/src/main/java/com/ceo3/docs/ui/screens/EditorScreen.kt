package com.ceo3.docs.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        containerColor = Color(0xFFF7F8F8)
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { 
                            viewModel.saveDocument()
                            onNavigateBack() 
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = state.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = { 
                        viewModel.saveDocument()
                        onNavigateBack() 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBCE3A6)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            // Formatting Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = { /* Toggle Bold */ }) {
                    Icon(Icons.Filled.FormatBold, contentDescription = "Bold", tint = Color.Black)
                }
                IconButton(onClick = { /* Toggle Italic */ }) {
                    Icon(Icons.Filled.FormatItalic, contentDescription = "Italic", tint = Color.Black)
                }
                IconButton(onClick = { /* Add List */ }) {
                    Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Bullet List", tint = Color.Black)
                }
                IconButton(onClick = { /* Add Image */ }) {
                    Icon(Icons.Filled.Image, contentDescription = "Insert Image", tint = Color.Black)
                }
                IconButton(onClick = { /* Add Table */ }) {
                    Icon(Icons.Filled.TableChart, contentDescription = "Insert Table", tint = Color.Black)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = state.content,
                onValueChange = viewModel::onContentChanged,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                placeholder = { Text("Start typing...", color = Color.Gray) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                )
            )
        }
    }
}
