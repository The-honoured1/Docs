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

                    // Load text
                    val context = getApplication<android.app.Application>()
                    val file = File(context.filesDir, "doc_${doc.id}.${doc.type.lowercase()}")
                    if (file.exists()) {
                        textContent = file.readText()
                    } else {
                        textContent = "Document body empty."
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
        // --- SCREEN 4: PDF Viewer / Annotator ---
        PdfAnnotatorView(viewModel = viewModel, documentId = documentId, onNavigateBack = onNavigateBack)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfAnnotatorView(
    viewModel: EditorViewModel,
    documentId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var activeTool by remember { mutableStateOf("None") } // None, Highlight, Annotate, Draw, Text, Signature
    var currentPath by remember { mutableStateOf<List<Offset>>(emptyList()) }

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
                    IconButton(onClick = { Toast.makeText(context, "Search PDF", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { Toast.makeText(context, "Grid / Page view options", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Filled.GridView, contentDescription = "Layout")
                    }
                    IconButton(onClick = {
                        viewModel.saveDocument(documentId) {
                            Toast.makeText(context, "Annotations Saved", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Filled.Save, contentDescription = "Save")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            // PDF Bottom toolbar (Share, Sign, Export, More)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            Toast.makeText(context, "Sharing PDF...", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Share", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            activeTool = "Signature"
                            Toast.makeText(context, "Tap on page to stamp signature", Toast.LENGTH_LONG).show()
                        }
                    ) {
                        Icon(Icons.Filled.Gesture, contentDescription = "Sign", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Sign", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            Toast.makeText(context, "Exporting PDF...", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Filled.SimCardDownload, contentDescription = "Export", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Export", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            Toast.makeText(context, "More PDF options", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Filled.MoreHoriz, contentDescription = "More", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("More", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFE2E8F0)) // desk slate background
        ) {
            // --- Left Panel Toolbar ---
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(60.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    )
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Highlight (H in circle)
                val highlightActive = activeTool == "Highlight"
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (highlightActive) Color(0xFFFEF08A) else Color.Transparent)
                        .clickable { activeTool = if (highlightActive) "None" else "Highlight" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "H",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (highlightActive) Color(0xFFCA8A04) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Annotate (Pencil)
                val annotateActive = activeTool == "Annotate"
                IconButton(
                    onClick = { activeTool = if (annotateActive) "None" else "Annotate" },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (annotateActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
                    )
                ) {
                    Icon(
                        Icons.Filled.BorderColor,
                        contentDescription = "Annotate",
                        tint = if (annotateActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Draw (Squiggle/Brush)
                val drawActive = activeTool == "Draw"
                IconButton(
                    onClick = { activeTool = if (drawActive) "None" else "Draw" },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (drawActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
                    )
                ) {
                    Icon(
                        Icons.Filled.Brush,
                        contentDescription = "Draw",
                        tint = if (drawActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Text (T)
                val textActive = activeTool == "Text"
                IconButton(
                    onClick = { activeTool = if (textActive) "None" else "Text" },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (textActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
                    )
                ) {
                    Icon(
                        Icons.Filled.Title,
                        contentDescription = "Text",
                        tint = if (textActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Signature (Signature/Pen)
                val signatureActive = activeTool == "Signature"
                IconButton(
                    onClick = { activeTool = if (signatureActive) "None" else "Signature" },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (signatureActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
                    )
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Signature",
                        tint = if (signatureActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Undo drawing actions locally
                IconButton(
                    onClick = {
                        if (viewModel.drawingStrokes.isNotEmpty()) {
                            viewModel.drawingStrokes.removeAt(viewModel.drawingStrokes.size - 1)
                        } else if (viewModel.signatureStamps.isNotEmpty()) {
                            viewModel.signatureStamps.removeAt(viewModel.signatureStamps.size - 1)
                        }
                    }
                ) {
                    Icon(Icons.Filled.Undo, contentDescription = "Undo Annotations", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }

            // --- Main PDF page view ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // PDF page paper sheet
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.707f) // standard A4 ratio
                        .clip(RoundedCornerShape(4.dp))
                        .shadow(6.dp, RoundedCornerShape(4.dp))
                        .background(Color.White)
                        .pointerInput(activeTool) {
                            if (activeTool == "Draw" || activeTool == "Highlight") {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentPath = listOf(offset)
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        currentPath = currentPath + change.position
                                    },
                                    onDragEnd = {
                                        if (currentPath.isNotEmpty()) {
                                            viewModel.drawingStrokes.add(
                                                Stroke(
                                                    points = currentPath,
                                                    color = if (activeTool == "Highlight") Color(0xFFFFF176).copy(alpha = 0.5f) else Color(0xFF1A73E8),
                                                    width = if (activeTool == "Highlight") 20f else 4f,
                                                    isHighlight = activeTool == "Highlight"
                                                )
                                            )
                                            currentPath = emptyList()
                                        }
                                    }
                                )
                            } else if (activeTool == "Signature") {
                                // Stamp signature on tap
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        viewModel.signatureStamps.add(
                                            SignatureStamp(offset, "Alex Mercer")
                                        )
                                        activeTool = "None"
                                    },
                                    onDrag = { _, _ -> },
                                    onDragEnd = {}
                                )
                            }
                        }
                ) {
                    // PDF Contents drawn programmatically (mock details matching Screen 4)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Q2 Financial Report",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            // Page indicator badge top-right: "1/12"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFE2E8F0))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "1/12",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        Text(
                            "April - June 2024",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            "Executive Summary",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // We wrap text around the highlight overlay region
                        Row {
                            Text(
                                text = "Our business delivered strong results in Q2, with ",
                                fontSize = 11.sp,
                                color = Color(0xFF334155),
                                lineHeight = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Yellow highlight mock overlay
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFFFEF08A))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "revenue growth of 18% compared to Q1.",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF854D0E)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "Revenue Growth",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E293B)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Custom Bar Chart matching Screen 4 layout
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Apr Bar
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier.height(120.dp)
                            ) {
                                Text("$120K", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                Box(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(55.dp)
                                        .clip(RoundedCornerShape(t = 4.dp, b = 0.dp))
                                        .background(Color(0xFF3B82F6))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Apr", fontSize = 10.sp, color = Color(0xFF64748B))
                            }

                            // May Bar
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier.height(120.dp)
                            ) {
                                Text("$145K", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                Box(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(72.dp)
                                        .clip(RoundedCornerShape(t = 4.dp, b = 0.dp))
                                        .background(Color(0xFF3B82F6))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("May", fontSize = 10.sp, color = Color(0xFF64748B))
                            }

                            // Jun Bar
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier.height(120.dp)
                            ) {
                                Text("$190K", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
                                Box(
                                    modifier = Modifier
                                        .width(36.dp)
                                        .height(95.dp)
                                        .clip(RoundedCornerShape(t = 4.dp, b = 0.dp))
                                        .background(Color(0xFF3B82F6))
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Jun", fontSize = 10.sp, color = Color(0xFF64748B))
                            }
                        }
                    }

                    // --- DRAWING STROKES OVERLAY CANVAS ---
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Draw Mockup handwritten-style blue arrow and "Great progress!" text
                        // Let's position it pointing towards the June column
                        // (Rough coordinates: width ~ 300, height ~ 400)
                        val chartJuneTopLeft = Offset(size.width * 0.73f, size.height * 0.72f)
                        val textStart = Offset(size.width * 0.45f, size.height * 0.65f)

                        // Draw mock arrow curve
                        val arrowPath = Path().apply {
                            moveTo(textStart.x + 40f, textStart.y + 10f)
                            quadraticTo(
                                (textStart.x + chartJuneTopLeft.x) / 2 + 10f,
                                (textStart.y + chartJuneTopLeft.y) / 2 - 30f,
                                chartJuneTopLeft.x - 10f,
                                chartJuneTopLeft.y - 10f
                            )
                        }
                        drawPath(arrowPath, Color(0xFF1E88E5), style = DrawStroke(width = 3f))

                        // Draw arrow head
                        val headPath = Path().apply {
                            moveTo(chartJuneTopLeft.x - 22f, chartJuneTopLeft.y - 20f)
                            lineTo(chartJuneTopLeft.x - 10f, chartJuneTopLeft.y - 10f)
                            lineTo(chartJuneTopLeft.x - 12f, chartJuneTopLeft.y - 26f)
                        }
                        drawPath(headPath, Color(0xFF1E88E5), style = DrawStroke(width = 3f))

                        // Draw user freehand drawing strokes
                        viewModel.drawingStrokes.forEach { stroke ->
                            if (stroke.points.size > 1) {
                                val path = Path().apply {
                                    moveTo(stroke.points[0].x, stroke.points[0].y)
                                    for (i in 1 until stroke.points.size) {
                                        lineTo(stroke.points[i].x, stroke.points[i].y)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = stroke.color,
                                    style = DrawStroke(width = stroke.width, cap = StrokeCap.Round)
                                )
                            }
                        }
                    }

                    // Simulated handwriting text overlay "Great progress!"
                    Box(
                        modifier = Modifier
                            .offset(x = 100.dp, y = 205.dp)
                    ) {
                        Text(
                            text = "Great progress!",
                            color = Color(0xFF1E88E5),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Cursive
                        )
                    }

                    // Draw signature stamp overlays
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    viewModel.signatureStamps.forEach { stamp ->
                        Box(
                            modifier = Modifier
                                .offset(
                                    x = (stamp.position.x / density.density).dp - 40.dp,
                                    y = (stamp.position.y / density.density).dp - 20.dp
                                )
                                .border(1.dp, Color(0xFF1E88E5).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.8f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stamp.text,
                                color = Color(0xFF1A73E8),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Cursive
                            )
                        }
                    }

                    // Temporary path drawing for active drag gesture
                    if (currentPath.size > 1) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val path = Path().apply {
                                moveTo(currentPath[0].x, currentPath[0].y)
                                for (i in 1 until currentPath.size) {
                                    lineTo(currentPath[i].x, currentPath[i].y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = if (activeTool == "Highlight") Color(0xFFFFF176).copy(alpha = 0.5f) else Color(0xFF1A73E8),
                                style = DrawStroke(
                                    width = if (activeTool == "Highlight") 20f else 4f,
                                    cap = StrokeCap.Round
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
