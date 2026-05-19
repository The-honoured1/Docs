package com.ceo3.docs.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ceo3.docs.data.local.DocumentEntity
import com.ceo3.docs.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class Stroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float,
    val isHighlight: Boolean = false
)

data class SignatureStamp(
    val position: Offset,
    val text: String
)

class EditorViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val dao = com.ceo3.docs.data.local.DocDatabase.getDatabase(application).documentDao()

    var title by mutableStateOf("")
    var textContent by mutableStateOf("")
    var type by mutableStateOf("TXT")
    var isSaving by mutableStateOf(false)
    var isStarred by mutableStateOf(false)

    // Drawing strokes for PDF annotation
    val drawingStrokes = mutableStateListOf<Stroke>()
    val signatureStamps = mutableStateListOf<SignatureStamp>()

    // Text formatting states
    var isBold by mutableStateOf(false)
    var isItalic by mutableStateOf(false)
    var isUnderline by mutableStateOf(false)
    var alignment by mutableStateOf("left") // left, center, right
    var headerMode by mutableStateOf("") // H1, H2, or plain

    // History stack for Undo/Redo
    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()

    fun loadDocument(docId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val doc = dao.getDocumentById(docId)
            withContext(Dispatchers.Main) {
                if (doc != null) {
                    title = doc.title + "." + doc.type.lowercase()
                    type = doc.type.uppercase()
                    isStarred = doc.isPinned

                    // Load text if not PDF
                    val context = getApplication<android.app.Application>()
                    val file = File(context.filesDir, "doc_${doc.id}.${doc.type.lowercase()}")
                    if (type != "PDF") {
                        if (file.exists()) {
                            textContent = file.readText()
                        } else {
                            textContent = "Document body empty."
                        }
                    }
                } else if (docId == "new_blank_document") {
                    title = "Untitled Document.docx"
                    type = "DOCX"
                    textContent = "Project Proposal\n\n1. Introduction\nStart typing your introduction..."
                } else if (docId.startsWith("Marketing")) {
                    title = "Marketing Plan.docx"
                    type = "DOCX"
                    textContent = "Marketing Plan\n\n1. Target Audience\nWe need to define our demographics..."
                } else if (docId.startsWith("Brand")) {
                    title = "Brand Guidelines.pdf"
                    type = "PDF"
                } else if (docId.startsWith("Budget")) {
                    title = "Budget Overview.xlsx"
                    type = "XLSX"
                    textContent = "Budget Sheet content..."
                } else {
                    title = docId
                    type = if (docId.endsWith(".pdf", ignoreCase = true)) "PDF" else "DOCX"
                    textContent = "Default text..."
                }

                // Reset drawing
                drawingStrokes.clear()
                signatureStamps.clear()
                undoStack.clear()
                redoStack.clear()
            }
        }
    }

    fun updateText(newText: String) {
        if (newText != textContent) {
            undoStack.add(textContent)
            redoStack.clear()
            textContent = newText
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(textContent)
            textContent = undoStack.removeAt(undoStack.size - 1)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(textContent)
            textContent = redoStack.removeAt(redoStack.size - 1)
        }
    }

    fun toggleStarred() {
        isStarred = !isStarred
    }

    fun saveDocument(docId: String, onComplete: () -> Unit = {}) {
        isSaving = true
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<android.app.Application>()
            val actualId = if (docId == "new_blank_document") UUID.randomUUID().toString() else docId
            val file = File(context.filesDir, "doc_${actualId}.${type.lowercase()}")
            file.writeText(textContent)

            // Upsert in database
            val existing = dao.getDocumentById(actualId)
            val updated = DocumentEntity(
                id = actualId,
                title = title.substringBeforeLast("."),
                type = type,
                lastModified = System.currentTimeMillis(),
                isPinned = isStarred,
                tags = existing?.tags ?: "Work",
                accentTheme = existing?.accentTheme ?: "classic",
                accentColor = existing?.accentColor ?: "blue"
            )
            dao.insertDocument(updated)
            withContext(Dispatchers.Main) {
                isSaving = false
                onComplete()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    documentId: String,
    onNavigateBack: () -> Unit,
    viewModel: EditorViewModel = viewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(documentId) {
        viewModel.loadDocument(documentId)
    }

    if (viewModel.type == "PDF") {
        // --- REAL PDF VIEWER ---
        val file = File(context.filesDir, "doc_${documentId}.pdf")
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = viewModel.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                PdfRendererView(file = file)
            }
        }
    } else {
        // --- SCREEN 3: DOCX / TXT Rich-text Editor ---
        RichTextEditorView(viewModel = viewModel, documentId = documentId, onNavigateBack = onNavigateBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RichTextEditorView(
    viewModel: EditorViewModel,
    documentId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = viewModel.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Synced status indicator "All changes saved"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF34A853))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "All changes saved",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF5F6368)
                        )
                    }

                    IconButton(onClick = { viewModel.undo() }) {
                        Icon(Icons.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = { viewModel.redo() }) {
                        Icon(Icons.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = { Toast.makeText(context, "Comment Mode", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Filled.Comment, contentDescription = "Comment")
                    }
                    IconButton(onClick = {
                        viewModel.saveDocument(documentId) {
                            Toast.makeText(context, "Saved successfully", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Filled.Save, contentDescription = "Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            // Screen 3 Bottom formatting toolbar (floating style above keyboard)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                    .padding(vertical = 8.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bold
                    IconButton(
                        onClick = { viewModel.isBold = !viewModel.isBold },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (viewModel.isBold) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
                        )
                    ) {
                        Icon(Icons.Filled.FormatBold, contentDescription = "Bold", tint = if (viewModel.isBold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    // Italic
                    IconButton(
                        onClick = { viewModel.isItalic = !viewModel.isItalic },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (viewModel.isItalic) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
                        )
                    ) {
                        Icon(Icons.Filled.FormatItalic, contentDescription = "Italic", tint = if (viewModel.isItalic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    // Underline
                    IconButton(
                        onClick = { viewModel.isUnderline = !viewModel.isUnderline },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (viewModel.isUnderline) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
                        )
                    ) {
                        Icon(Icons.Filled.FormatUnderlined, contentDescription = "Underline", tint = if (viewModel.isUnderline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    // Color selector (represented as A)
                    IconButton(onClick = { Toast.makeText(context, "Select Font Color", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Filled.FormatColorText, contentDescription = "Text Color")
                    }
                    // Bullet list
                    IconButton(onClick = {
                        val currentText = viewModel.textContent
                        if (!currentText.contains("- ")) {
                            viewModel.updateText(currentText + "\n- New bullet point")
                        } else {
                            Toast.makeText(context, "Bullet List Active", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Filled.FormatListBulleted, contentDescription = "Bullet List")
                    }
                    // Alignment
                    IconButton(onClick = {
                        viewModel.alignment = when (viewModel.alignment) {
                            "left" -> "center"
                            "center" -> "right"
                            else -> "left"
                        }
                    }) {
                        Icon(
                            imageVector = when (viewModel.alignment) {
                                "center" -> Icons.Filled.FormatAlignCenter
                                "right" -> Icons.Filled.FormatAlignRight
                                else -> Icons.Filled.FormatAlignLeft
                            },
                            contentDescription = "Alignment"
                        )
                    }
                }

                // Row 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { viewModel.headerMode = if (viewModel.headerMode == "H1") "" else "H1" },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (viewModel.headerMode == "H1") MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
                        )
                    ) {
                        Text("H1", fontWeight = FontWeight.Bold, color = if (viewModel.headerMode == "H1") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    TextButton(
                        onClick = { viewModel.headerMode = if (viewModel.headerMode == "H2") "" else "H2" },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (viewModel.headerMode == "H2") MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
                        )
                    ) {
                        Text("H2", fontWeight = FontWeight.Bold, color = if (viewModel.headerMode == "H2") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { Toast.makeText(context, "Select Font Style", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Filled.TextFields, contentDescription = "Font Family")
                    }
                    IconButton(onClick = { Toast.makeText(context, "Indent", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Filled.FormatIndentIncrease, contentDescription = "Indent")
                    }
                    IconButton(onClick = { Toast.makeText(context, "Outdent", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Filled.FormatIndentDecrease, contentDescription = "Outdent")
                    }
                    IconButton(onClick = { Toast.makeText(context, "Quote Block", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Filled.FormatQuote, contentDescription = "Quote")
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF3F4F6)) // light grey desk workspace
                .padding(16.dp)
        ) {
            // Paper document card
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .shadow(4.dp, RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .verticalScroll(scrollState)
                    .padding(24.dp)
            ) {
                // Large Bold Title
                Text(
                    text = "Project Proposal",
                    fontSize = if (viewModel.headerMode == "H1") 32.sp else 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    color = Color(0xFF1E293B),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = when (viewModel.alignment) {
                        "center" -> TextAlign.Center
                        "right" -> TextAlign.Right
                        else -> TextAlign.Left
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Structured Rich-text Content editable
                BasicTextField(
                    value = viewModel.textContent,
                    onValueChange = { viewModel.updateText(it) },
                    textStyle = TextStyle(
                        fontSize = if (viewModel.headerMode == "H2") 18.sp else 14.sp,
                        fontWeight = if (viewModel.isBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (viewModel.isItalic) FontStyle.Italic else FontStyle.Normal,
                        color = Color(0xFF334155),
                        lineHeight = 22.sp,
                        textAlign = when (viewModel.alignment) {
                            "center" -> TextAlign.Center
                            "right" -> TextAlign.Right
                            else -> TextAlign.Left
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default
                )
            }
        }
    }
}
