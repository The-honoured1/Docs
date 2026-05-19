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
import androidx.compose.ui.draw.rotate
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed

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
    var watermarkText by mutableStateOf("")

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

                    // Load text if not PDF and not PNG/JPG/JPEG image
                    val context = getApplication<android.app.Application>()
                    val file = File(context.filesDir, "doc_${doc.id}.${doc.type.lowercase()}")
                    if (type != "PDF" && type != "PNG" && type != "JPG" && type != "JPEG") {
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
            if (type != "PNG" && type != "JPG" && type != "JPEG") {
                val file = File(context.filesDir, "doc_${actualId}.${type.lowercase()}")
                file.writeText(textContent)
            }

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

    // PowerPoint slide state
    var currentSlideIndex by mutableStateOf(0)

    data class SlideData(
        val title: String,
        val body: String
    )

    fun parseSlides(): List<SlideData> {
        val text = textContent
        val slideBlocks = text.split(Regex("(?im)^SLIDE\\s*\\d*\\b|^---"))
        val list = mutableListOf<SlideData>()
        for (block in slideBlocks) {
            val trimmed = block.trim()
            if (trimmed.isEmpty()) continue
            val lines = trimmed.split("\n")
            var sTitle = ""
            val bodyLines = mutableListOf<String>()
            for (line in lines) {
                val cleanLine = line.trim()
                if (cleanLine.startsWith("Title:", ignoreCase = true)) {
                    sTitle = cleanLine.substring(6).trim()
                } else if (cleanLine.startsWith("Subtitle:", ignoreCase = true)) {
                    bodyLines.add(line)
                } else if (sTitle.isEmpty() && cleanLine.isNotEmpty() && !cleanLine.startsWith("•") && !cleanLine.startsWith("-")) {
                    sTitle = cleanLine
                } else {
                    bodyLines.add(line)
                }
            }
            if (sTitle.isEmpty()) sTitle = "Slide ${list.size + 1}"
            list.add(SlideData(sTitle, bodyLines.joinToString("\n").trim()))
        }
        if (list.isEmpty()) {
            list.add(SlideData("New Slide Title", "• Point 1\n• Point 2"))
        }
        return list
    }

    fun updateSlide(index: Int, newTitle: String, newBody: String) {
        val slides = parseSlides().toMutableList()
        if (index in slides.indices) {
            slides[index] = SlideData(newTitle, newBody)
        }
        // Compile back to textContent
        val newText = StringBuilder()
        slides.forEachIndexed { i, slide ->
            newText.append("SLIDE ${i + 1}\n")
            newText.append("Title: ${slide.title}\n")
            newText.append("${slide.body}\n\n")
        }
        updateText(newText.toString().trim())
    }

    fun addSlide() {
        val slides = parseSlides().toMutableList()
        slides.add(SlideData("New Slide Title", "• Point 1\n• Point 2"))
        // Compile back to textContent
        val newText = StringBuilder()
        slides.forEachIndexed { i, slide ->
            newText.append("SLIDE ${i + 1}\n")
            newText.append("Title: ${slide.title}\n")
            newText.append("${slide.body}\n\n")
        }
        updateText(newText.toString().trim())
        currentSlideIndex = slides.size - 1
    }

    fun deleteSlide(index: Int) {
        val slides = parseSlides().toMutableList()
        if (slides.size > 1 && index in slides.indices) {
            slides.removeAt(index)
            if (currentSlideIndex >= slides.size) {
                currentSlideIndex = slides.size - 1
            }
            // Compile back to textContent
            val newText = StringBuilder()
            slides.forEachIndexed { i, slide ->
                newText.append("SLIDE ${i + 1}\n")
                newText.append("Title: ${slide.title}\n")
                newText.append("${slide.body}\n\n")
            }
            updateText(newText.toString().trim())
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
    } else if (viewModel.type == "PPT" || viewModel.type == "PPTX") {
        // --- POWERPOINT SLIDE DECK INTERACTIVE VIEW/EDITOR ---
        PptEditorView(viewModel = viewModel, documentId = documentId, onNavigateBack = onNavigateBack)
    } else if (viewModel.type == "PNG" || viewModel.type == "JPG" || viewModel.type == "JPEG") {
        // --- IMAGE VIEWER / EXPORTER ---
        ImageViewerView(viewModel = viewModel, documentId = documentId, onNavigateBack = onNavigateBack)
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
    var editorTab by remember { mutableStateOf(0) } // 0 = Visual View, 1 = Raw Editor

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
            if (editorTab == 1) {
                // Screen 3 Bottom formatting toolbar (only visible in Raw Editor tab)
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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF3F4F6)) // light grey desk workspace
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Segmented tab toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(1.dp, Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Visual View", "Raw Editor").forEachIndexed { idx, tabTitle ->
                    val active = editorTab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { editorTab = idx }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Paper document card
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .shadow(4.dp, RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .verticalScroll(scrollState)
                    .padding(24.dp)
            ) {
                val cleanTitle = viewModel.title.substringBeforeLast(".")
                
                // Large Bold Title
                Text(
                    text = cleanTitle,
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

                if (editorTab == 1) {
                    // --- RAW MARKDOWN EDITOR MODE ---
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
                } else {
                    // --- BEAUTIFUL VISUAL RENDER MODE ---
                    val blocks = parseVisualBlocks(viewModel.textContent)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        blocks.forEach { block ->
                            when (block) {
                                is VisualBlock.Header -> {
                                    val (size, color, weight) = when (block.level) {
                                        1 -> Triple(22.sp, AccentSky, FontWeight.Bold)
                                        2 -> Triple(18.sp, AccentSky, FontWeight.Bold)
                                        else -> Triple(15.sp, Color(0xFF1E293B), FontWeight.Bold)
                                    }
                                    Column {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = parseMarkdownText(block.text),
                                            fontSize = size,
                                            color = color,
                                            fontWeight = weight
                                        )
                                        if (block.level == 1) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            HorizontalDivider(color = AccentSky.copy(alpha = 0.3f), thickness = 1.2.dp)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }
                                is VisualBlock.ChecklistItem -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { toggleChecklistItem(block.lineIndex, block.checked, viewModel) }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (block.checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                                            contentDescription = null,
                                            tint = if (block.checked) AccentSky else Color.Gray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = parseMarkdownText(block.text),
                                            fontSize = 14.sp,
                                            textDecoration = if (block.checked) TextDecoration.LineThrough else TextDecoration.None,
                                            color = if (block.checked) Color.Gray else Color(0xFF334155)
                                        )
                                    }
                                }
                                is VisualBlock.BulletItem -> {
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "•  ",
                                            fontSize = 14.sp,
                                            color = AccentSky,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = parseMarkdownText(block.text),
                                            fontSize = 14.sp,
                                            color = Color(0xFF334155),
                                            lineHeight = 20.sp
                                        )
                                    }
                                }
                                is VisualBlock.Blockquote -> {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFF1F5F9))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .height(24.dp)
                                                .background(AccentSky, RoundedCornerShape(2.dp))
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = parseMarkdownText(block.text, isItalic = true),
                                            fontSize = 13.sp,
                                            color = Color(0xFF475569)
                                        )
                                    }
                                }
                                is VisualBlock.Table -> {
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                            .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            // Headers
                                            if (block.headers.isNotEmpty()) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(AccentSky.copy(alpha = 0.1f))
                                                        .padding(10.dp)
                                                ) {
                                                    block.headers.forEach { header ->
                                                        Text(
                                                            text = header,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp,
                                                            color = AccentSky,
                                                            modifier = Modifier.weight(1f),
                                                            textAlign = TextAlign.Left
                                                        )
                                                    }
                                                }
                                            }
                                            // Rows
                                            block.rows.forEachIndexed { rowIdx, rowCells ->
                                                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(if (rowIdx % 2 == 1) Color(0xFFF8FAFC) else Color.Transparent)
                                                        .padding(10.dp)
                                                ) {
                                                    rowCells.forEach { cell ->
                                                        Text(
                                                            text = parseMarkdownText(cell),
                                                            fontSize = 12.sp,
                                                            color = Color(0xFF334155),
                                                            modifier = Modifier.weight(1f),
                                                            textAlign = TextAlign.Left
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                is VisualBlock.Paragraph -> {
                                    if (block.text.trim().isNotEmpty()) {
                                        Text(
                                            text = parseMarkdownText(block.text),
                                            fontSize = 14.sp,
                                            color = Color(0xFF334155),
                                            lineHeight = 22.sp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PptEditorView(
    viewModel: EditorViewModel,
    documentId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val slides = viewModel.parseSlides()
    val currentIndex = viewModel.currentSlideIndex.coerceIn(0, slides.size - 1)
    val currentSlide = slides[currentIndex]

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
                    // Synced status indicator
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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF3F4F6)) // desk layout
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Slide Navigation Carousel ---
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Slides",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4B5563)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(slides) { idx, slide ->
                        val isSelected = idx == currentIndex
                        Box(
                            modifier = Modifier
                                .size(width = 110.dp, height = 70.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) AccentAmber.copy(alpha = 0.15f)
                                    else Color.White
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) AccentAmber else Color.LightGray.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.currentSlideIndex = idx }
                                .padding(8.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "${idx + 1}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) AccentAmber else Color.Gray
                                )
                                Text(
                                    text = slide.title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = Color(0xFF1F2937)
                                )
                            }
                        }
                    }
                    item {
                        // "Add slide" button box
                        Box(
                            modifier = Modifier
                                .size(width = 110.dp, height = 70.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .border(1.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .clickable { viewModel.addSlide() },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.Add, contentDescription = "Add Slide", tint = AccentAmber, modifier = Modifier.size(20.dp))
                                Text("Add Slide", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentAmber)
                            }
                        }
                    }
                }
            }

            // --- Widescreen Widescreen 16:9 Canvas ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .shadow(6.dp, RoundedCornerShape(12.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                        )
                    )
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Slide header title
                    Text(
                        text = currentSlide.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )

                    // Slide contents
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        currentSlide.body.split("\n").filter { it.trim().isNotEmpty() }.forEach { bullet ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = if (bullet.trim().startsWith("•") || bullet.trim().startsWith("-")) "" else "• ",
                                    fontSize = 14.sp,
                                    color = AccentAmber,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (bullet.trim().startsWith("•") || bullet.trim().startsWith("-")) bullet.trim().substring(1).trim() else bullet.trim(),
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.9f),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    // Slide footer page number
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = viewModel.title.substringBeforeLast("."),
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${currentIndex + 1} / ${slides.size}",
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // --- Slide Action Bar ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.addSlide() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Slide", color = Color.White, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { viewModel.deleteSlide(currentIndex) },
                    enabled = slides.size > 1,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete Slide", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            // --- Slide Title & Body Editing Panel ---
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Edit Slide ${currentIndex + 1}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF1F2937)
                    )

                    OutlinedTextField(
                        value = currentSlide.title,
                        onValueChange = { viewModel.updateSlide(currentIndex, it, currentSlide.body) },
                        label = { Text("Slide Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentAmber,
                            focusedLabelColor = AccentAmber
                        )
                    )

                    OutlinedTextField(
                        value = currentSlide.body,
                        onValueChange = { viewModel.updateSlide(currentIndex, currentSlide.title, it) },
                        label = { Text("Slide Content (one line per bullet point)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentAmber,
                            focusedLabelColor = AccentAmber
                        )
                    )
                }
            }
        }
    }
}

// --- HELPER CLASSES & PARSERS FOR VISUAL VIEW ---

sealed class VisualBlock {
    data class Header(val level: Int, val text: String) : VisualBlock()
    data class ChecklistItem(val checked: Boolean, val text: String, val lineIndex: Int) : VisualBlock()
    data class BulletItem(val text: String) : VisualBlock()
    data class Blockquote(val text: String) : VisualBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : VisualBlock()
    data class Paragraph(val text: String) : VisualBlock()
}

fun parseVisualBlocks(text: String): List<VisualBlock> {
    val lines = text.split("\n")
    val blocks = mutableListOf<VisualBlock>()
    
    var inTable = false
    val tableLines = mutableListOf<String>()
    
    fun flushTable() {
        if (tableLines.isNotEmpty()) {
            val parsedHeaders = mutableListOf<String>()
            val parsedRows = mutableListOf<List<String>>()
            
            var headerParsed = false
            for (tLine in tableLines) {
                val cells = tLine.split("|").map { it.trim() }.filterIndexed { index, _ -> index > 0 && index < tLine.split("|").size - 1 }
                if (cells.isEmpty()) continue
                if (cells.all { it.all { char -> char == '-' || char == ':' } }) {
                    // divider line, skip
                    continue
                }
                if (!headerParsed) {
                    parsedHeaders.addAll(cells)
                    headerParsed = true
                } else {
                    parsedRows.add(cells)
                }
            }
            if (parsedHeaders.isNotEmpty() || parsedRows.isNotEmpty()) {
                blocks.add(VisualBlock.Table(parsedHeaders, parsedRows))
            }
            tableLines.clear()
        }
        inTable = false
    }
    
    for (i in lines.indices) {
        val line = lines[i]
        val trimmed = line.trim()
        
        if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
            inTable = true
            tableLines.add(line)
            continue
        } else if (inTable) {
            flushTable()
        }
        
        when {
            trimmed.startsWith("# ") -> {
                blocks.add(VisualBlock.Header(1, trimmed.substring(2)))
            }
            trimmed.startsWith("## ") -> {
                blocks.add(VisualBlock.Header(2, trimmed.substring(3)))
            }
            trimmed.startsWith("### ") -> {
                blocks.add(VisualBlock.Header(3, trimmed.substring(4)))
            }
            trimmed.startsWith("- [x] ", ignoreCase = true) || trimmed.startsWith("* [x] ", ignoreCase = true) -> {
                blocks.add(VisualBlock.ChecklistItem(true, trimmed.substring(6), i))
            }
            trimmed.startsWith("- [ ] ") || trimmed.startsWith("* [ ] ") -> {
                blocks.add(VisualBlock.ChecklistItem(false, trimmed.substring(6), i))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                blocks.add(VisualBlock.BulletItem(trimmed.substring(2)))
            }
            trimmed.startsWith("> ") -> {
                blocks.add(VisualBlock.Blockquote(trimmed.substring(2)))
            }
            else -> {
                blocks.add(VisualBlock.Paragraph(line))
            }
        }
    }
    
    if (inTable) {
        flushTable()
    }
    
    return blocks
}

fun parseMarkdownText(text: String, isItalic: Boolean = false): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val parts = text.split("**")
    for (i in parts.indices) {
        if (i % 2 == 1) {
            builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(parts[i])
            }
        } else {
            builder.withStyle(SpanStyle(fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal)) {
                append(parts[i])
            }
        }
    }
    return builder.toAnnotatedString()
}

fun toggleChecklistItem(lineIndex: Int, checked: Boolean, viewModel: EditorViewModel) {
    val lines = viewModel.textContent.split("\n").toMutableList()
    if (lineIndex in lines.indices) {
        val line = lines[lineIndex]
        val trimmed = line.trim()
        val indent = line.substring(0, line.indexOf(trimmed))
        if (checked) {
            if (trimmed.startsWith("- [x] ", ignoreCase = true)) {
                lines[lineIndex] = "$indent- [ ] " + trimmed.substring(6)
            } else if (trimmed.startsWith("* [x] ", ignoreCase = true)) {
                lines[lineIndex] = "$indent* [ ] " + trimmed.substring(6)
            }
        } else {
            if (trimmed.startsWith("- [ ] ")) {
                lines[lineIndex] = "$indent- [x] " + trimmed.substring(6)
            } else if (trimmed.startsWith("* [ ] ")) {
                lines[lineIndex] = "$indent* [x] " + trimmed.substring(6)
            }
        }
        viewModel.updateText(lines.joinToString("\n"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageViewerView(
    viewModel: EditorViewModel,
    documentId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val file = File(context.filesDir, "doc_${documentId}.${viewModel.type.lowercase()}")
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(file) {
        if (file.exists()) {
            bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
        }
    }

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
                    IconButton(onClick = {
                        if (file.exists()) {
                            saveImageToGallery(context, file, viewModel.title)
                        } else {
                            Toast.makeText(context, "Image file not found", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Filled.Download, contentDescription = "Download Image")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF3F4F6)) // Light grey desk workspace
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Image Canvas Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .aspectRatio(1f)
                        .shadow(6.dp, RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFE5E7EB)), // Light gray checkerboard area
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap!!.asImageBitmap(),
                                contentDescription = "Document Image Preview",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.BrokenImage,
                                    contentDescription = "Image Load Error",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "Failed to load image preview",
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }

                // Info details card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .border(1.dp, Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Format & Source",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${viewModel.type} Image Document",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1E293B)
                                )
                            }
                            // Accent badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AccentRose.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = viewModel.type,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentRose
                                )
                            }
                        }

                        if (bitmap != null) {
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Resolution", fontSize = 11.sp, color = Color.Gray)
                                    Text("${bitmap!!.width} x ${bitmap!!.height}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
                                }
                                Column {
                                    Text("File Size", fontSize = 11.sp, color = Color.Gray)
                                    val sizeKb = file.length() / 1024
                                    Text(
                                        text = if (sizeKb > 1024) "${"%.2f".format(sizeKb / 1024f)} MB" else "${sizeKb} KB",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF334155)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (file.exists()) {
                                    saveImageToGallery(context, file, viewModel.title)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export to Device Gallery / Downloads", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

fun saveImageToGallery(context: android.content.Context, file: File, title: String) {
    try {
        val resolver = context.contentResolver
        val contentValues = android.contentValues.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, title)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/DocsApp")
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val imageUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (imageUri != null) {
            resolver.openOutputStream(imageUri)?.use { output ->
                file.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(imageUri, contentValues, null, null)
            }
            android.widget.Toast.makeText(context, "Saved to Pictures/DocsApp successfully!", android.widget.Toast.LENGTH_LONG).show()
        } else {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val destFile = File(downloadsDir, title)
            file.copyTo(destFile, overwrite = true)
            android.widget.Toast.makeText(context, "Saved to Downloads/${title}!", android.widget.Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Export failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
    }
}
