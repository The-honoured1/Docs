package com.ceo3.docs.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ceo3.docs.data.local.DocumentEntity
import com.ceo3.docs.domain.DocumentConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipFile

// View modes supported by the Editor
enum class EditorViewMode {
    PDF_VIEW,  // Read mode
    PDF_SIGN,  // Edit / Sign mode
    TXT_VIEW   // Read-only text mode
}

// Drawing path representing signature strokes
data class StrokePath(
    val points: List<Offset> = emptyList(),
    val color: Color = Color.Black,
    val width: Float = 6f
)

// UI State for the Editor
data class EditorUiState(
    val title: String = "Loading...",
    val type: String = "PDF",
    val file: File? = null,
    val textContent: String = "",
    val viewMode: EditorViewMode = EditorViewMode.PDF_VIEW,
    val pages: List<Bitmap> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null
)

class EditorViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState

    private val dao = com.ceo3.docs.data.local.DocDatabase.getDatabase(application).documentDao()
    private val converter = DocumentConverter(application)

    // Temporary list of annotated pages with drawing strokes overlayed
    var annotatedPages = mutableStateListOf<Bitmap>()
    var drawingPaths = mutableStateListOf<List<StrokePath>>() // Stroke list per page

    fun loadDocument(docIdOrUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<android.app.Application>()
                var resolvedFile: File? = null
                var docTitle = "Document"
                var docType = "PDF"
                var textBody = ""

                if (docIdOrUri.startsWith("content://") || docIdOrUri.startsWith("file://")) {
                    // ── Handle external file URIs picked by the system ────────────────
                    val uri = Uri.parse(docIdOrUri)
                    val fileName = getFileNameFromUri(context, uri) ?: "Real_File"
                    docTitle = fileName.substringBeforeLast(".")
                    val extension = fileName.substringAfterLast(".").uppercase()
                    docType = if (extension in listOf("PDF", "DOCX", "DOC", "TXT")) extension else "PDF"

                    // Copy system input stream to local cache to read reliably
                    val cacheFile = File(context.cacheDir, "temp_import_${System.currentTimeMillis()}.${extension.lowercase()}")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(cacheFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    resolvedFile = cacheFile
                } else if (docIdOrUri.startsWith("/")) {
                    // ── Handle direct absolute file paths (e.g. from Downloads/Documents) ──
                    val extFile = File(docIdOrUri)
                    resolvedFile = extFile
                    docTitle = extFile.nameWithoutExtension
                    val extension = extFile.extension.uppercase()
                    docType = if (extension in listOf("PDF", "DOCX", "DOC", "TXT")) extension else "PDF"
                } else {
                    // ── Handle local DB document entries ──────────────────────────────
                    val docEntity = dao.getDocumentById(docIdOrUri)
                    if (docEntity != null) {
                        docTitle = docEntity.title
                        docType = docEntity.type.uppercase()
                        val localFile = File(context.filesDir, "doc_${docEntity.id}.${docEntity.type.lowercase()}")
                        if (localFile.exists()) {
                            resolvedFile = localFile
                        }
                    }
                }

                if (resolvedFile == null || !resolvedFile.exists()) {
                    throw Exception("Could not open or locate document file.")
                }

                // Process based on document type
                when (docType) {
                    "TXT" -> {
                        textBody = resolvedFile.readText()
                        withContext(Dispatchers.Main) {
                            _uiState.value = EditorUiState(
                                title = docTitle,
                                type = docType,
                                file = resolvedFile,
                                textContent = textBody,
                                viewMode = EditorViewMode.TXT_VIEW,
                                isLoading = false
                            )
                        }
                    }
                    "DOCX", "DOC" -> {
                        // Word Document Extraction on the fly
                        val docxText = extractTextFromDocxFile(resolvedFile)
                        // Convert text to PDF in cache
                        val pdfCacheFile = File(context.cacheDir, "docx_converted_${System.currentTimeMillis()}.pdf")
                        converter.textToPdf(docxText, pdfCacheFile).getOrThrow()

                        val bitmaps = renderPdfToBitmaps(pdfCacheFile)
                        withContext(Dispatchers.Main) {
                            annotatedPages.clear()
                            annotatedPages.addAll(bitmaps)
                            drawingPaths.clear()
                            repeat(bitmaps.size) { drawingPaths.add(emptyList()) }

                            _uiState.value = EditorUiState(
                                title = docTitle,
                                type = docType,
                                file = pdfCacheFile,
                                pages = bitmaps,
                                viewMode = EditorViewMode.PDF_VIEW,
                                isLoading = false
                            )
                        }
                    }
                    else -> {
                        // Standard PDF Loading
                        val bitmaps = renderPdfToBitmaps(resolvedFile)
                        withContext(Dispatchers.Main) {
                            annotatedPages.clear()
                            annotatedPages.addAll(bitmaps)
                            drawingPaths.clear()
                            repeat(bitmaps.size) { drawingPaths.add(emptyList()) }

                            _uiState.value = EditorUiState(
                                title = docTitle,
                                type = docType,
                                file = resolvedFile,
                                pages = bitmaps,
                                viewMode = EditorViewMode.PDF_VIEW,
                                isLoading = false
                            )
                        }
                    }
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to render document."
                    )
                }
            }
        }
    }

    private fun extractTextFromDocxFile(file: File): String {
        return try {
            val zipFile = ZipFile(file)
            val entry = zipFile.getEntry("word/document.xml") ?: return "Empty Word Document"
            zipFile.getInputStream(entry).use { stream ->
                val xmlContent = stream.bufferedReader().readText()
                val builder = StringBuilder()
                val regex = Regex("<w:t[^>]*>(.*?)</w:t>")
                val matches = regex.findAll(xmlContent)
                for (match in matches) {
                    builder.append(match.groupValues[1]).append(" ")
                }
                val result = builder.toString().trim()
                if (result.isEmpty()) "Empty Word Document" else result
            }
        } catch (e: Exception) {
            "Failed to parse DOCX file content. Error: ${e.message}"
        }
    }

    private fun renderPdfToBitmaps(pdfFile: File): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        var renderer: PdfRenderer? = null
        var pfd: ParcelFileDescriptor? = null
        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                // Scale rendering for display quality
                val scale = 1.5f
                val width = (page.width * scale).toInt()
                val height = (page.height * scale).toInt()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmaps.add(bitmap)
                page.close()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            renderer?.close()
            pfd?.close()
        }
        return bitmaps
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) name = it.getString(index)
                }
            }
        }
        if (name == null) {
            name = uri.path
            val cut = name?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                name = name?.substring(cut + 1)
            }
        }
        return name
    }

    fun setViewMode(mode: EditorViewMode) {
        _uiState.value = _uiState.value.copy(viewMode = mode)
    }

    fun addStrokeToPage(pageIndex: Int, stroke: StrokePath) {
        if (pageIndex in drawingPaths.indices) {
            val currentList = drawingPaths[pageIndex]
            drawingPaths[pageIndex] = currentList + stroke
        }
    }

    fun clearDrawingPaths(pageIndex: Int) {
        if (pageIndex in drawingPaths.indices) {
            drawingPaths[pageIndex] = emptyList()
        }
    }

    fun saveSignedDocument(onComplete: () -> Unit) {
        val file = _uiState.value.file ?: return
        _uiState.value = _uiState.value.copy(isSaving = true)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Compile the drawings directly on top of the page Bitmaps
                val finalBitmaps = _uiState.value.pages.mapIndexed { pageIndex, baseBmp ->
                    val mutableBmp = baseBmp.copy(Bitmap.Config.ARGB_8888, true)
                    val canvas = Canvas(mutableBmp)
                    val paint = Paint().apply {
                        isAntiAlias = true
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }

                    // Draw each path
                    val pathsOnPage = drawingPaths[pageIndex]
                    pathsOnPage.forEach { strokePath ->
                        if (strokePath.points.size > 1) {
                            paint.color = strokePath.color.toArgb()
                            paint.strokeWidth = strokePath.width * 2f // Scale width to match bitmap rendering resolution

                            val path = Path()
                            path.moveTo(strokePath.points[0].x, strokePath.points[0].y)
                            for (i in 1 until strokePath.points.size) {
                                path.lineTo(strokePath.points[i].x, strokePath.points[i].y)
                            }
                            canvas.drawPath(path, paint)
                        }
                    }
                    mutableBmp
                }

                // Convert final drawn bitmaps into compiled PDF
                val doc = android.graphics.pdf.PdfDocument()
                finalBitmaps.forEachIndexed { index, bitmap ->
                    val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                    val page = doc.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    doc.finishPage(page)
                }

                // Overwrite the file on disk
                FileOutputStream(file).use { out ->
                    doc.writeTo(out)
                }
                doc.close()

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    onComplete()
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isSaving = false, error = "Failed to save annotated document: ${e.message}")
                }
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
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var selectedDrawColor by remember { mutableStateOf(Color.Black) }
    var selectedBrushWidth by remember { mutableStateOf(6f) }

    LaunchedEffect(documentId) {
        viewModel.loadDocument(documentId)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.viewMode == EditorViewMode.PDF_VIEW && state.pages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setViewMode(EditorViewMode.PDF_SIGN) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                    } else if (state.viewMode == EditorViewMode.PDF_SIGN) {
                        TextButton(onClick = {
                            viewModel.saveSignedDocument {
                                Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show()
                                viewModel.setViewMode(EditorViewMode.PDF_VIEW)
                            }
                        }) {
                            if (state.isSaving) CircularProgressIndicator(Modifier.size(16.dp)) else Text("Save")
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.error != null) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = state.error ?: "Error occurred", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { viewModel.loadDocument(documentId) }) {
                        Text("Retry Loading")
                    }
                }
            } else {
                when (state.viewMode) {
                    EditorViewMode.TXT_VIEW -> {
                        // ── Text File Viewer ──────────────────────────────────────────
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            // Read-only Notice Banner
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Plain text editing is disabled. Please open a PDF or Word document to annotate and sign.",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = 16.sp
                                    )
                                }
                            }

                            // Clean paper-style scrollable area
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)), RoundedCornerShape(16.dp))
                                    .verticalScroll(rememberScrollState())
                                    .padding(20.dp)
                            ) {
                                Text(
                                    text = state.textContent,
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    else -> {
                        // ── PDF Pages Renderer / Editor (Read & Sign Modes) ───────────
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Sign/Draw Option bar overlay
                            AnimatedVisibility(
                                visible = state.viewMode == EditorViewMode.PDF_SIGN,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = { viewModel.setViewMode(EditorViewMode.PDF_VIEW) }) {
                                        Icon(Icons.Filled.Close, "Cancel")
                                    }
                                    Spacer(Modifier.weight(1f))
                                    listOf(Color.Black, Color.Blue, Color.Red).forEach { color ->
                                        IconButton(onClick = { selectedDrawColor = color }) {
                                            Icon(Icons.Filled.Circle, "Color", tint = color, modifier = Modifier.size(24.dp))
                                        }
                                    }
                                    IconButton(onClick = { 
                                        state.pages.indices.forEach { viewModel.clearDrawingPaths(it) } 
                                    }) {
                                        Icon(Icons.Filled.Delete, "Clear")
                                    }
                                }
                            }

                            // Render Pages vertically in a scrollable view
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                itemsIndexed(state.pages) { pageIndex, pageBmp ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White)
                                            .border(
                                                BorderStroke(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                                ),
                                                RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Base Page Bitmap
                                        Image(
                                            bitmap = pageBmp.asImageBitmap(),
                                            contentDescription = "Page ${pageIndex + 1}",
                                            contentScale = ContentScale.FillWidth,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        // Signature Drawing Canvas Overlay
                                        val strokesList = viewModel.drawingPaths.getOrElse(pageIndex) { emptyList() }
                                        var activePathPoints = remember { mutableStateListOf<Offset>() }

                                        ComposeCanvas(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .pointerInput(state.viewMode) {
                                                    if (state.viewMode == EditorViewMode.PDF_SIGN) {
                                                        detectDragGestures(
                                                            onDragStart = { offset ->
                                                                activePathPoints.clear()
                                                                activePathPoints.add(offset)
                                                            },
                                                            onDragEnd = {
                                                                if (activePathPoints.size > 1) {
                                                                    viewModel.addStrokeToPage(
                                                                        pageIndex,
                                                                        StrokePath(
                                                                            points = activePathPoints.toList(),
                                                                            color = selectedDrawColor,
                                                                            width = selectedBrushWidth
                                                                        )
                                                                    )
                                                                }
                                                                activePathPoints.clear()
                                                            },
                                                            onDragCancel = {
                                                                activePathPoints.clear()
                                                            },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                activePathPoints.add(change.position)
                                                            }
                                                        )
                                                    }
                                                }
                                        ) {
                                            // 1. Draw saved historical strokes
                                            strokesList.forEach { strokePath ->
                                                if (strokePath.points.size > 1) {
                                                    val path = androidx.compose.ui.graphics.Path()
                                                    path.moveTo(strokePath.points[0].x, strokePath.points[0].y)
                                                    for (i in 1 until strokePath.points.size) {
                                                        path.lineTo(strokePath.points[i].x, strokePath.points[i].y)
                                                    }
                                                    drawPath(
                                                        path = path,
                                                        color = strokePath.color,
                                                        style = Stroke(
                                                            width = strokePath.width,
                                                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                                                        )
                                                    )
                                                }
                                            }

                                            // 2. Draw current active stroke path
                                            if (activePathPoints.size > 1) {
                                                val path = androidx.compose.ui.graphics.Path()
                                                path.moveTo(activePathPoints[0].x, activePathPoints[0].y)
                                                for (i in 1 until activePathPoints.size) {
                                                    path.lineTo(activePathPoints[i].x, activePathPoints[i].y)
                                                }
                                                drawPath(
                                                    path = path,
                                                    color = selectedDrawColor,
                                                    style = Stroke(
                                                        width = selectedBrushWidth,
                                                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                                                    )
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
    }
}
