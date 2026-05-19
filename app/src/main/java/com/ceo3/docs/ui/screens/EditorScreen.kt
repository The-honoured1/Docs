package com.ceo3.docs.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ceo3.docs.data.local.DocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.zip.ZipFile

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    documentId: String,
    onNavigateBack: () -> Unit,
    viewModel: EditorViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(documentId) { viewModel.loadDocument(documentId) }
    val state by viewModel.uiState.collectAsState()

    val currentTheme = Themes.firstOrNull { it.name == state.selectedTheme } ?: Themes[0]
    val currentAccent = Accents.firstOrNull { it.name == state.selectedColorName } ?: Accents[0]

    var showCustomizer by remember { mutableStateOf(false) }
    var showToolsSheet by remember { mutableStateOf(false) }

    val hostState = remember { SnackbarHostState() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState) },
        floatingActionButton = {
            // High-Juice Dynamic FAB visible only if NOT a TXT file (since editing TXT is disabled)
            if (state.documentType != "TXT") {
                FloatingActionButton(
                    onClick = { showToolsSheet = true },
                    containerColor = currentAccent.color,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Build,
                        contentDescription = "Expand Document Tools",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Top Bar ───────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable {
                                viewModel.saveDocument()
                                onNavigateBack()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = state.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    // Pinned / Bookmark indicator
                    IconButton(
                        onClick = {
                            viewModel.toggleBookmark()
                            viewModel.saveDocument()
                        }
                    ) {
                        Icon(
                            imageVector = if (state.isBookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "Bookmark",
                            tint = if (state.isBookmarked) Color(0xFFFFD54F) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.saveDocument()
                            onNavigateBack()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = currentAccent.color
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // ── Segmented Navigation (Read vs Edit/Sign) ─────────────────
                // Hidden for TXT documents since editing TXT is disabled.
                if (state.documentType != "TXT") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SegmentedButton(
                            label = "Read Mode",
                            icon = Icons.Filled.MenuBook,
                            selected = state.viewMode == ViewMode.PDF_VIEW,
                            accentColor = currentAccent.color,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setViewMode(ViewMode.PDF_VIEW) }
                        )
                        SegmentedButton(
                            label = "Edit / Sign",
                            icon = Icons.Filled.Gesture,
                            selected = state.viewMode == ViewMode.PDF_SIGN,
                            accentColor = currentAccent.color,
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.setViewMode(ViewMode.PDF_SIGN) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Document Sheet Canvas ─────────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                        .shadow(8.dp, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .background(currentTheme.paperBg)
                ) {
                    if (state.documentType == "TXT") {
                        // Read-Only Plain Text Viewer
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp)
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Plain text editing is disabled. Open a PDF or Word document to annotate and sign.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        lineHeight = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = state.content.text,
                                    color = currentTheme.text,
                                    fontSize = 16.sp,
                                    lineHeight = 26.sp,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        // PDF/Doc Pages Canvas (Sign vs View modes)
                        if (state.pdfPages.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Loading document pages…",
                                    color = currentTheme.text.copy(alpha = 0.5f)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                itemsIndexed(state.pdfPages) { pageIndex, bmp ->
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        elevation = CardDefaults.cardElevation(4.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White)
                                    ) {
                                        var currentStrokePoints = remember { mutableStateListOf<Offset>() }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .then(
                                                    if (state.viewMode == ViewMode.PDF_SIGN) {
                                                        Modifier.pointerInput(Unit) {
                                                            detectDragGestures(
                                                                onDragStart = { offset ->
                                                                    currentStrokePoints.clear()
                                                                    currentStrokePoints.add(offset)
                                                                },
                                                                onDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    currentStrokePoints.add(change.position)
                                                                },
                                                                onDragEnd = {
                                                                    if (currentStrokePoints.isNotEmpty()) {
                                                                        viewModel.addDrawingStroke(
                                                                            pageIndex = pageIndex,
                                                                            stroke = DrawingStroke(
                                                                                points = currentStrokePoints.toList(),
                                                                                color = currentAccent.color,
                                                                                strokeWidth = 5f
                                                                            )
                                                                        )
                                                                        currentStrokePoints.clear()
                                                                    }
                                                                }
                                                            )
                                                        }
                                                    } else Modifier
                                                )
                                        ) {
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = "Page ${pageIndex + 1}",
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Canvas(modifier = Modifier.matchParentSize()) {
                                                val savedStrokes = state.pageStrokes[pageIndex] ?: emptyList()
                                                for (stroke in savedStrokes) {
                                                    if (stroke.points.size > 1) {
                                                        for (i in 0 until stroke.points.size - 1) {
                                                            drawLine(
                                                                color = stroke.color,
                                                                start = stroke.points[i],
                                                                end = stroke.points[i + 1],
                                                                strokeWidth = stroke.strokeWidth,
                                                                cap = StrokeCap.Round
                                                            )
                                                        }
                                                    }
                                                }
                                                if (currentStrokePoints.size > 1) {
                                                    for (i in 0 until currentStrokePoints.size - 1) {
                                                        drawLine(
                                                            color = currentAccent.color,
                                                            start = currentStrokePoints[i],
                                                            end = currentStrokePoints[i + 1],
                                                            strokeWidth = 5f,
                                                            cap = StrokeCap.Round
                                                        )
                                                    }
                                                }
                                            }

                                            if (state.viewMode == ViewMode.PDF_SIGN && (state.pageStrokes[pageIndex]?.isNotEmpty() == true)) {
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(12.dp)
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.Black.copy(alpha = 0.6f))
                                                        .clickable { viewModel.clearDrawings(pageIndex) },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Delete,
                                                        contentDescription = "Clear",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(18.dp)
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

    // High-Juice Modal Tools Expansion Sheet
    if (showToolsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showToolsSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Document Hub Tools",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ToolHubCard(
                        title = "Compress",
                        subtitle = "Reduce PDF file size",
                        icon = Icons.Filled.Compress,
                        color = currentAccent.color,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showToolsSheet = false
                            viewModel.compressPdf()
                        }
                    )
                    ToolHubCard(
                        title = "Bookmark",
                        subtitle = if (state.isBookmarked) "Remove from Bookmarks" else "Pin to Favorites",
                        icon = if (state.isBookmarked) Icons.Filled.Star else Icons.Filled.StarBorder,
                        color = currentAccent.color,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.toggleBookmark()
                            viewModel.saveDocument()
                        }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ToolHubCard(
                        title = "Share",
                        subtitle = "Send document copy",
                        icon = Icons.Filled.Share,
                        color = currentAccent.color,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showToolsSheet = false
                            val fileToSend = state.pdfFile
                            if (fileToSend != null && fileToSend.exists()) {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_STREAM, Uri.fromFile(fileToSend))
                                    type = "application/pdf"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share PDF"))
                            }
                        }
                    )
                    ToolHubCard(
                        title = "Wipe Draw",
                        subtitle = "Clear canvas ink",
                        icon = Icons.Filled.DeleteOutline,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showToolsSheet = false
                            viewModel.clearDrawings(0)
                        }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ToolHubCard(
                        title = "Style Options",
                        subtitle = "Paper and theme colors",
                        icon = Icons.Filled.Palette,
                        color = currentAccent.color,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showToolsSheet = false
                            showCustomizer = true
                        }
                    )
                    Box(modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Theme customizer modal bottom sheet
    if (showCustomizer) {
        ModalBottomSheet(
            onDismissRequest = { showCustomizer = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                Text(
                    text = "Customize Accent & Theme",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Paper Accent Tone",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Themes.forEach { theme ->
                        val selected = state.selectedTheme == theme.name
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.paperBg)
                                .border(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) currentAccent.color else Color.LightGray.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { viewModel.setTheme(theme.name) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = theme.label,
                                color = theme.text,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Highlight & Signature Color",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Accents.forEach { accent ->
                        val selected = state.selectedColorName == accent.name
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(accent.color)
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { viewModel.setColor(accent.name) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = { showCustomizer = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = currentAccent.color),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Apply & Save Accent", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// ─── Component Helpers ────────────────────────────────────────────────────────

@Composable
fun SegmentedButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) accentColor.copy(alpha = 0.15f) else Color.Transparent
    val tint = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = tint
            )
        }
    }
}

@Composable
fun ToolHubCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp,
                maxLines = 2
            )
        }
    }
}

// ─── Updated Viewmodel ──────────────────────────────────────────────

class EditorViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState

    private var currentDocumentId: String? = null
    private var documentType: String = "TXT"
    private val converter = com.ceo3.docs.domain.DocumentConverter(application)

    fun loadDocument(id: String) {
        if (id.startsWith("content://") || id.startsWith("file://")) {
            loadFromUri(id)
            return
        }
        currentDocumentId = id
        viewModelScope.launch(Dispatchers.IO) {
            val dao = com.ceo3.docs.data.local.DocDatabase.getDatabase(getApplication()).documentDao()
            val entity = dao.getDocumentById(id)
            documentType = entity?.type ?: "TXT"

            val ext = when (documentType) {
                "PDF" -> "pdf"
                "DOCX", "DOC" -> "docx"
                else -> "txt"
            }
            val file = File(getApplication<android.app.Application>().filesDir, "doc_${id}.$ext")
            val title = entity?.title ?: "Document $id"
            val theme = entity?.accentTheme ?: "classic"
            val color = entity?.accentColor ?: "blue"
            val bookmarked = entity?.isPinned ?: false

            if (!file.exists()) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        title = title,
                        viewMode = ViewMode.PDF_VIEW,
                        selectedTheme = theme,
                        selectedColorName = color,
                        isBookmarked = bookmarked,
                        documentType = documentType
                    )
                }
                return@launch
            }

            if (documentType == "PDF") {
                val pages = renderPdfPages(file)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        title = title,
                        viewMode = ViewMode.PDF_VIEW,
                        pdfPages = pages,
                        pdfFile = file,
                        selectedTheme = theme,
                        selectedColorName = color,
                        isBookmarked = bookmarked,
                        documentType = documentType
                    )
                }
            } else if (documentType == "DOCX" || documentType == "DOC") {
                val text = extractTextFromDocx(file)
                val pdfFile = File(getApplication<android.app.Application>().cacheDir, "docx_converted_${System.currentTimeMillis()}.pdf")
                converter.textToPdf(text, pdfFile)
                val pages = renderPdfPages(pdfFile)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        title = title,
                        viewMode = ViewMode.PDF_VIEW,
                        pdfPages = pages,
                        pdfFile = pdfFile,
                        selectedTheme = theme,
                        selectedColorName = color,
                        isBookmarked = bookmarked,
                        documentType = documentType
                    )
                }
            } else {
                val text = file.readText()
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        title = title,
                        content = TextFieldValue(text),
                        viewMode = ViewMode.PDF_VIEW,
                        selectedTheme = theme,
                        selectedColorName = color,
                        isBookmarked = bookmarked,
                        documentType = "TXT"
                    )
                }
            }
        }
    }

    private fun loadFromUri(uriString: String) {
        currentDocumentId = uriString
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<android.app.Application>()
            val uri = Uri.parse(uriString)
            val mimeType = context.contentResolver.getType(uri) ?: ""
            documentType = when {
                mimeType == "application/pdf" -> "PDF"
                mimeType.contains("word") || uriString.endsWith(".docx") || uriString.endsWith(".doc") -> "DOCX"
                else -> "TXT"
            }
            val title = uri.lastPathSegment?.substringAfterLast('/') ?: "Document"

            when (documentType) {
                "PDF" -> {
                    val cacheFile = File(context.cacheDir, "external_${System.currentTimeMillis()}.pdf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        cacheFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    val pages = renderPdfPages(cacheFile)
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            title = title,
                            viewMode = ViewMode.PDF_VIEW,
                            pdfPages = pages,
                            pdfFile = cacheFile,
                            documentType = "PDF"
                        )
                    }
                }
                "DOCX", "DOC" -> {
                    val cacheFile = File(context.cacheDir, "external_${System.currentTimeMillis()}.docx")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        cacheFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    val text = extractTextFromDocx(cacheFile)
                    val pdfFile = File(context.cacheDir, "docx_converted_${System.currentTimeMillis()}.pdf")
                    converter.textToPdf(text, pdfFile)
                    val pages = renderPdfPages(pdfFile)
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            title = title,
                            viewMode = ViewMode.PDF_VIEW,
                            pdfPages = pages,
                            pdfFile = pdfFile,
                            documentType = documentType
                        )
                    }
                }
                else -> {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            title = title,
                            content = TextFieldValue(text),
                            viewMode = ViewMode.PDF_VIEW,
                            documentType = "TXT"
                        )
                    }
                }
            }
        }
    }

    private fun extractTextFromDocx(file: File): String {
        return try {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("word/document.xml") ?: return "Empty Word Document"
                zip.getInputStream(entry).use { input ->
                    val xml = input.bufferedReader().readText()
                    val regex = "<w:t.*?>(.*?)</w:t>".toRegex()
                    val rawText = regex.findAll(xml).map { it.groupValues[1] }.joinToString(" ")
                    val cleaned = unescapeXml(rawText)
                    if (cleaned.trim().isEmpty()) "Empty Word Document" else cleaned
                }
            }
        } catch (e: Exception) {
            "Failed to extract text from Word document: ${e.message}"
        }
    }

    private fun unescapeXml(text: String): String {
        return text.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    private fun renderPdfPages(file: File): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        return try {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            val scale = 2.0f
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bmp = Bitmap.createBitmap(
                    (page.width * scale).toInt(),
                    (page.height * scale).toInt(),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bmp)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmaps.add(bmp)
            }
            renderer.close()
            fd.close()
            bitmaps
        } catch (e: Exception) {
            bitmaps
        }
    }

    fun setViewMode(mode: ViewMode) {
        _uiState.value = _uiState.value.copy(viewMode = mode)
    }

    fun setTheme(theme: String) {
        _uiState.value = _uiState.value.copy(selectedTheme = theme)
        saveCustomizationSettings()
    }

    fun setColor(colorName: String) {
        _uiState.value = _uiState.value.copy(selectedColorName = colorName)
        saveCustomizationSettings()
    }

    fun toggleBookmark() {
        _uiState.value = _uiState.value.copy(isBookmarked = !_uiState.value.isBookmarked)
    }

    private fun saveCustomizationSettings() {
        val id = currentDocumentId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val dao = com.ceo3.docs.data.local.DocDatabase.getDatabase(getApplication()).documentDao()
            val entity = dao.getDocumentById(id)
            if (entity != null) {
                dao.insertDocument(
                    entity.copy(
                        accentTheme = _uiState.value.selectedTheme,
                        accentColor = _uiState.value.selectedColorName,
                        isPinned = _uiState.value.isBookmarked
                    )
                )
            }
        }
    }

    fun addDrawingStroke(pageIndex: Int, stroke: DrawingStroke) {
        val currentStrokes = _uiState.value.pageStrokes[pageIndex] ?: emptyList()
        val updatedStrokes = currentStrokes + stroke
        val updatedMap = _uiState.value.pageStrokes.toMutableMap()
        updatedMap[pageIndex] = updatedStrokes
        _uiState.value = _uiState.value.copy(pageStrokes = updatedMap)
    }

    fun clearDrawings(pageIndex: Int) {
        val updatedMap = _uiState.value.pageStrokes.toMutableMap()
        updatedMap.remove(pageIndex)
        _uiState.value = _uiState.value.copy(pageStrokes = updatedMap)
    }

    fun compressPdf() {
        val file = _uiState.value.pdfFile ?: return
        _uiState.value = _uiState.value.copy(isCompressing = true)
        viewModelScope.launch(Dispatchers.IO) {
            val out = File(getApplication<android.app.Application>().filesDir, "compressed_${System.currentTimeMillis()}.pdf")
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            val bitmapPaths = mutableListOf<String>()
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val scale = 0.5f
                val bmp = Bitmap.createBitmap(
                    (page.width * scale).toInt(),
                    (page.height * scale).toInt(),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bmp)
                canvas.drawColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                val imgFile = File(getApplication<android.app.Application>().cacheDir, "cmp_pg_$i.jpg")
                imgFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 60, it) }
                bitmapPaths.add(imgFile.absolutePath)
            }
            renderer.close(); fd.close()
            converter.imagesToPdf(bitmapPaths, out)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isCompressing = false,
                    pdfPages = renderPdfPages(out),
                    pdfFile = out
                )
            }
        }
    }

    fun saveDocument() {
        val id = currentDocumentId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val dao = com.ceo3.docs.data.local.DocDatabase.getDatabase(getApplication()).documentDao()
            val existing = dao.getDocumentById(id)

            dao.insertDocument(
                DocumentEntity(
                    id = id,
                    title = _uiState.value.title,
                    type = documentType,
                    lastModified = System.currentTimeMillis(),
                    isPinned = _uiState.value.isBookmarked,
                    tags = existing?.tags ?: "Personal",
                    accentTheme = _uiState.value.selectedTheme,
                    accentColor = _uiState.value.selectedColorName
                )
            )
        }
    }
}

data class EditorUiState(
    val title: String = "Untitled Document",
    val content: TextFieldValue = TextFieldValue(""),
    val isBoldEnabled: Boolean = false,
    val isItalicEnabled: Boolean = false,
    val viewMode: ViewMode = ViewMode.PDF_VIEW,
    val pdfPages: List<Bitmap> = emptyList(),
    val pdfFile: File? = null,
    val pageStrokes: Map<Int, List<DrawingStroke>> = emptyMap(),
    val isOcrRunning: Boolean = false,
    val isCompressing: Boolean = false,
    val isBookmarked: Boolean = false,
    val selectedTheme: String = "classic",
    val selectedColorName: String = "blue",
    val documentType: String = "PDF"
)

val Themes = listOf(
    ThemePreset("classic", Color.White, Color(0xFF111214), "Classic"),
    ThemePreset("sepia", Color(0xFFFBF0D9), Color(0xFF5C4033), "Sepia"),
    ThemePreset("mint", Color(0xFFE8F5E9), Color(0xFF1B5E20), "Mint Eye-Care"),
    ThemePreset("charcoal", Color(0xFF1E1E24), Color(0xFFE8EAF0), "Charcoal")
)

val Accents = listOf(
    AccentColorPreset("blue", Color(0xFF2196F3), "Blue"),
    AccentColorPreset("red", Color(0xFFF44336), "Red"),
    AccentColorPreset("green", Color(0xFF4CAF50), "Green"),
    AccentColorPreset("orange", Color(0xFFFF9800), "Orange"),
    AccentColorPreset("purple", Color(0xFF9C27B0), "Purple")
)

data class ThemePreset(
    val name: String,
    val paperBg: Color,
    val text: Color,
    val label: String
)

data class AccentColorPreset(
    val name: String,
    val color: Color,
    val label: String
)

data class DrawingStroke(
    val points: List<androidx.compose.ui.geometry.Offset>,
    val color: Color,
    val strokeWidth: Float
)

enum class ViewMode {
    PDF_VIEW,
    PDF_SIGN
}

