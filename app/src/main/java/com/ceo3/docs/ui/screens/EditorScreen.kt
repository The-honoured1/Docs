package com.ceo3.docs.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ─── ViewModel ────────────────────────────────────────────────────────────────

class EditorViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState
    private var currentDocumentId: String? = null
    private var documentType: String = "TXT"

    private val converter = com.ceo3.docs.domain.DocumentConverter(application)
    private val ocrEngine = com.ceo3.docs.domain.OcrEngine()

    /** Called when user opens a doc from the DB (by Room ID). */
    fun loadDocument(id: String) {
        // If it looks like a content URI, treat it as an external file
        if (id.startsWith("content://") || id.startsWith("file://")) {
            loadFromUri(id); return
        }
        currentDocumentId = id
        viewModelScope.launch(Dispatchers.IO) {
            val dao = com.ceo3.docs.data.local.DocDatabase
                .getDatabase(getApplication()).documentDao()
            val entity = dao.getDocumentById(id)
            documentType = entity?.type ?: "TXT"

            val ext  = if (documentType == "PDF") "pdf" else "txt"
            val file = File(getApplication<android.app.Application>().filesDir, "doc_${id}.$ext")
            val title = entity?.title ?: "Document $id"

            if (!file.exists()) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(title = title, viewMode = ViewMode.TEXT_EDIT)
                }
                return@launch
            }

            if (documentType == "PDF") {
                val pages = renderPdfPages(file)
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        title = title,
                        viewMode = ViewMode.PDF_VIEW,
                        pdfPages = pages
                    )
                }
            } else {
                val text = file.readText()
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        title = title,
                        content = TextFieldValue(text),
                        viewMode = ViewMode.TEXT_EDIT
                    )
                }
            }
        }
    }

    /** Called when a user picks an external file via the system picker. */
    private fun loadFromUri(uriString: String) {
        currentDocumentId = uriString
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<android.app.Application>()
            val uri = Uri.parse(uriString)
            val mimeType = context.contentResolver.getType(uri) ?: ""
            documentType = when {
                mimeType == "application/pdf"                   -> "PDF"
                mimeType.startsWith("image/")                   -> "IMG"
                mimeType.contains("word") || uriString.endsWith(".docx") -> "DOCX"
                else                                             -> "TXT"
            }
            val title = uri.lastPathSegment?.substringAfterLast('/') ?: "Document"

            when (documentType) {
                "PDF" -> {
                    // Copy to cache so PdfRenderer can open it
                    val cacheFile = File(context.cacheDir, "external_${System.currentTimeMillis()}.pdf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        cacheFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    val pages = renderPdfPages(cacheFile)
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            title = title,
                            viewMode = ViewMode.PDF_VIEW,
                            pdfPages = pages
                        )
                    }
                }
                else -> {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(
                            title = title,
                            content = TextFieldValue(text),
                            viewMode = ViewMode.TEXT_EDIT
                        )
                    }
                }
            }
        }
    }

    private fun renderPdfPages(file: File): List<Bitmap> {
        val bitmaps = mutableListOf<Bitmap>()
        return try {
            val fd       = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(fd)
            val scale    = 2f // 2× for crisp display
            for (i in 0 until renderer.pageCount) {
                val page   = renderer.openPage(i)
                val bmp    = Bitmap.createBitmap(
                    (page.width  * scale).toInt(),
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

    fun onContentChanged(value: TextFieldValue) {
        _uiState.value = _uiState.value.copy(content = value)
    }

    fun toggleBold() {
        _uiState.value = _uiState.value.copy(isBoldEnabled = !_uiState.value.isBoldEnabled)
    }

    fun toggleItalic() {
        _uiState.value = _uiState.value.copy(isItalicEnabled = !_uiState.value.isItalicEnabled)
    }

    fun saveDocument() {
        val id = currentDocumentId ?: return
        if (_uiState.value.viewMode == ViewMode.PDF_VIEW) return // PDF is read-only for now
        viewModelScope.launch(Dispatchers.IO) {
            val file = File(
                getApplication<android.app.Application>().filesDir,
                "doc_${id}.txt"
            )
            file.writeText(_uiState.value.content.text)
            val dao = com.ceo3.docs.data.local.DocDatabase
                .getDatabase(getApplication()).documentDao()
            dao.insertDocument(
                com.ceo3.docs.data.local.DocumentEntity(
                    id           = id,
                    title        = _uiState.value.title,
                    type         = documentType,
                    lastModified = System.currentTimeMillis(),
                    isPinned     = false,
                    tags         = "Personal"
                )
            )
        }
    }
}

enum class ViewMode { TEXT_EDIT, PDF_VIEW }

data class EditorUiState(
    val title: String          = "Untitled Document",
    val content: TextFieldValue = TextFieldValue(""),
    val isBoldEnabled: Boolean  = false,
    val isItalicEnabled: Boolean = false,
    val viewMode: ViewMode      = ViewMode.TEXT_EDIT,
    val pdfPages: List<Bitmap>  = emptyList()
)

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    documentId: String,
    onNavigateBack: () -> Unit,
    viewModel: EditorViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    LaunchedEffect(documentId) { viewModel.loadDocument(documentId) }
    val state by viewModel.uiState.collectAsState()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Top Bar ───────────────────────────────────────────────────
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
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                if (state.viewMode == ViewMode.TEXT_EDIT) {
                    Button(
                        onClick = { viewModel.saveDocument(); onNavigateBack() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Save", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Page count badge for PDF view
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${state.pdfPages.size} pages",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // ── Toolbar (text edit only) ───────────────────────────────────
            if (state.viewMode == ViewMode.TEXT_EDIT) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ToolbarBtn(
                        icon = Icons.Filled.FormatBold,
                        label = "Bold",
                        active = state.isBoldEnabled,
                        onClick = { viewModel.toggleBold() }
                    )
                    ToolbarBtn(
                        icon = Icons.Filled.FormatItalic,
                        label = "Italic",
                        active = state.isItalicEnabled,
                        onClick = { viewModel.toggleItalic() }
                    )
                    ToolbarBtn(
                        icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                        label = "List",
                        active = false,
                        onClick = { viewModel.onContentChanged(
                            TextFieldValue(state.content.text + "\n• ")
                        ) }
                    )
                    ToolbarBtn(
                        icon = Icons.Filled.FormatUnderlined,
                        label = "Underline",
                        active = false,
                        onClick = {}
                    )
                    ToolbarBtn(
                        icon = Icons.Filled.FormatSize,
                        label = "Size",
                        active = false,
                        onClick = {}
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── Content Area ──────────────────────────────────────────────
            when (state.viewMode) {
                ViewMode.TEXT_EDIT -> {
                    val textWeight = if (state.isBoldEnabled) FontWeight.Bold else FontWeight.Normal
                    val textStyle  = if (state.isItalicEnabled) FontStyle.Italic else FontStyle.Normal

                    BasicTextField(
                        value = state.content,
                        onValueChange = viewModel::onContentChanged,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp)
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(20.dp),
                        textStyle = TextStyle(
                            fontSize   = 16.sp,
                            lineHeight = 26.sp,
                            color      = MaterialTheme.colorScheme.onSurface,
                            fontWeight = textWeight,
                            fontStyle  = textStyle
                        ),
                        decorationBox = { inner ->
                            if (state.content.text.isEmpty()) {
                                Text(
                                    "Start typing…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 16.sp
                                )
                            }
                            inner()
                        }
                    )
                }
                ViewMode.PDF_VIEW -> {
                    if (state.pdfPages.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(state.pdfPages) { index, bmp ->
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    elevation = CardDefaults.cardElevation(4.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Page ${index + 1}",
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text(
                                            text = "Page ${index + 1}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .align(Alignment.CenterHorizontally)
                                                .padding(vertical = 6.dp)
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

@Composable
private fun ToolbarBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val bg   = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = tint)
    }
}
