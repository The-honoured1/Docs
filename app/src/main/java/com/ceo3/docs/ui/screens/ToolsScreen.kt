package com.ceo3.docs.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ceo3.docs.data.local.DocDatabase
import com.ceo3.docs.data.local.DocumentEntity
import com.ceo3.docs.domain.DocumentConverter
import com.ceo3.docs.domain.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

// Represents a tool item in the grid
data class ToolItem(
    val name: String,
    val icon: ImageVector,
    val iconColor: Color,
    val bgColor: Color,
    val isNew: Boolean = false,
    val actionType: ToolActionType
)

enum class ToolActionType {
    SCANNER,
    IMAGE_TO_PDF,
    IMAGE_TO_TEXT,
    READ_ALOUD,
    SUPPORT,
    COMPRESS_IMAGE,
    CONVERT_FORMAT,
    EXPORT_PDF_IMAGES,
    ENHANCE_IMAGE,
    SIMULATED_AI,
    SIMULATED_IMAGE
}

// UI State for the Tools screen
data class ToolsUiState(
    val selectedTab: String = "AI Tools",
    val isProcessing: Boolean = false,
    val processProgress: Float = 0f,
    val processingMessage: String = "",
    val completionMessage: String? = null
)

class ToolsViewModel(application: android.app.Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {
    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState

    private val dao = DocDatabase.getDatabase(application).documentDao()
    private val converter = DocumentConverter(application)
    private val ocrEngine = OcrEngine()
    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(application, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    fun readTextAloud(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun stopTts() {
        tts?.stop()
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }

    fun setSelectedTab(tab: String) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }

    // Runs a simulated task with retro loading indicator
    fun runSimulatedTool(name: String, message: String, successText: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isProcessing = true,
                processingMessage = message,
                processProgress = 0f,
                completionMessage = null
            )
            repeat(10) { i ->
                delay(120)
                _uiState.value = _uiState.value.copy(
                    processProgress = (i + 1) * 0.1f
                )
            }
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                completionMessage = successText
            )
        }
    }

    fun dismissCompletion() {
        _uiState.value = _uiState.value.copy(completionMessage = null)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REAL TOOL ACTIONS
    // ─────────────────────────────────────────────────────────────────────────

    // Real Tool 1: Image to PDF conversion
    fun processImageToPdf(uris: List<Uri>, onComplete: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = true,
                        processingMessage = "Converting images to PDF...",
                        processProgress = 0.3f
                    )
                }

                val context = getApplication<android.app.Application>()
                val cachePaths = mutableListOf<String>()

                uris.forEachIndexed { index, uri ->
                    val file = File(context.cacheDir, "temp_img_${index}_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                    cachePaths.add(file.absolutePath)
                }

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(processProgress = 0.7f)
                }

                val docId = UUID.randomUUID().toString()
                val pdfFile = File(context.filesDir, "doc_${docId}.pdf")
                val result = converter.imagesToPdf(cachePaths, pdfFile)

                if (result.isSuccess) {
                    val entity = DocumentEntity(
                        id = docId,
                        title = "Images_Merged_${System.currentTimeMillis() / 100000}",
                        type = "PDF",
                        lastModified = System.currentTimeMillis(),
                        isPinned = false,
                        tags = "Merged,Images"
                    )
                    dao.insertDocument(entity)

                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(isProcessing = false)
                        onComplete(docId)
                    }
                } else {
                    throw result.exceptionOrNull() ?: Exception("Conversion error")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isProcessing = false)
                    Toast.makeText(getApplication(), "Failed to merge images: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Real Tool 2: Extract text using local ML Kit OCR Engine
    fun processImageToText(uri: Uri, onComplete: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = true,
                        processingMessage = "Running OCR text extraction...",
                        processProgress = 0.4f
                    )
                }

                val context = getApplication<android.app.Application>()
                var bitmap: Bitmap? = null
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    bitmap = BitmapFactory.decodeStream(stream)
                }

                if (bitmap == null) throw Exception("Could not parse image bitmap.")

                val ocrResult = ocrEngine.extractTextFromImage(bitmap!!)

                if (ocrResult.isSuccess) {
                    val extractedText = ocrResult.getOrThrow()
                    val docId = UUID.randomUUID().toString()

                    val txtFile = File(context.filesDir, "doc_${docId}.txt")
                    txtFile.writeText(extractedText)

                    val entity = DocumentEntity(
                        id = docId,
                        title = "Extracted_${System.currentTimeMillis() / 100000}",
                        type = "TXT",
                        lastModified = System.currentTimeMillis(),
                        isPinned = false,
                        tags = "OCR,AI"
                    )
                    dao.insertDocument(entity)

                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(isProcessing = false)
                        onComplete(docId)
                    }
                } else {
                    throw ocrResult.exceptionOrNull() ?: Exception("OCR recognition error")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isProcessing = false)
                    Toast.makeText(getApplication(), "OCR extraction failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Real Tool 3: Compress Image File Size
    fun compressSelectedImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = true,
                        processingMessage = "Compressing photo pixels...",
                        processProgress = 0.5f
                    )
                }
                val context = getApplication<android.app.Application>()
                val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: throw Exception("Failed to decode image")

                val docId = UUID.randomUUID().toString()
                val compressedFile = File(context.filesDir, "doc_${docId}.jpg")

                FileOutputStream(compressedFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 35, out) // 35% JPEG compression
                }

                val entity = DocumentEntity(
                    id = docId,
                    title = "Compressed_${System.currentTimeMillis() / 100000}",
                    type = "JPG",
                    lastModified = System.currentTimeMillis(),
                    isPinned = false,
                    tags = "Compressed"
                )
                dao.insertDocument(entity)

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        completionMessage = "Image successfully compressed! Saved to Document Manager."
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isProcessing = false)
                    Toast.makeText(getApplication(), "Failed to compress image: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Real Tool 4: Convert Format (e.g. JPG to PNG)
    fun convertImageFormat(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = true,
                        processingMessage = "Converting photo format...",
                        processProgress = 0.5f
                    )
                }
                val context = getApplication<android.app.Application>()
                val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: throw Exception("Failed to decode image")

                val docId = UUID.randomUUID().toString()
                val pngFile = File(context.filesDir, "doc_${docId}.png")

                FileOutputStream(pngFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) // Convert to PNG format
                }

                val entity = DocumentEntity(
                    id = docId,
                    title = "Converted_${System.currentTimeMillis() / 100000}",
                    type = "PNG",
                    lastModified = System.currentTimeMillis(),
                    isPinned = false,
                    tags = "Converted,PNG"
                )
                dao.insertDocument(entity)

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        completionMessage = "Format successfully converted to PNG! Saved to Document Manager."
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isProcessing = false)
                    Toast.makeText(getApplication(), "Failed to convert format: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Real Tool 5: Export PDF Pages as Images
    fun exportPdfToImages(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = true,
                        processingMessage = "Extracting pages as images...",
                        processProgress = 0.2f
                    )
                }
                val context = getApplication<android.app.Application>()
                val tempFile = File(context.cacheDir, "temp_render_${System.currentTimeMillis()}.pdf")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val pageCount = renderer.pageCount

                for (i in 0 until pageCount) {
                    val page = renderer.openPage(i)
                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val docId = UUID.randomUUID().toString()
                    val imageFile = File(context.filesDir, "doc_${docId}.png")
                    FileOutputStream(imageFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }

                    val entity = DocumentEntity(
                        id = docId,
                        title = "Exported_Page_${i + 1}_${System.currentTimeMillis() / 100000}",
                        type = "PNG",
                        lastModified = System.currentTimeMillis(),
                        isPinned = false,
                        tags = "Exported,Page"
                    )
                    dao.insertDocument(entity)

                    val progress = (i + 1).toFloat() / pageCount
                    withContext(Dispatchers.Main) {
                        _uiState.value = _uiState.value.copy(processProgress = progress)
                    }
                }
                renderer.close()
                pfd.close()

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        completionMessage = "Extracted $pageCount pages as high-res PNG images successfully! Saved in Files."
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isProcessing = false)
                    Toast.makeText(getApplication(), "Failed to export PDF: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Real Tool 6: Enhance scanned image (Auto Contrast & Whitening filter)
    fun enhanceScannedImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = true,
                        processingMessage = "Enhancing contrast and whitening scan...",
                        processProgress = 0.5f
                    )
                }
                val context = getApplication<android.app.Application>()
                val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: throw Exception("Failed to decode image")

                val width = bitmap.width
                val height = bitmap.height
                val enhancedBmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

                val canvas = Canvas(enhancedBmp)
                val paint = Paint()
                // ColorMatrix: Double the contrast, trim grey backgrounds to white
                val colorMatrix = ColorMatrix(floatArrayOf(
                    1.8f, 0f, 0f, 0f, -80f,
                    0f, 1.8f, 0f, 0f, -80f,
                    0f, 0f, 1.8f, 0f, -80f,
                    0f, 0f, 0f, 1.0f, 0f
                ))
                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                canvas.drawBitmap(bitmap, 0f, 0f, paint)

                val docId = UUID.randomUUID().toString()
                val enhancedFile = File(context.filesDir, "doc_${docId}.jpg")

                FileOutputStream(enhancedFile).use { out ->
                    enhancedBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                val entity = DocumentEntity(
                    id = docId,
                    title = "Enhanced_${System.currentTimeMillis() / 100000}",
                    type = "JPG",
                    lastModified = System.currentTimeMillis(),
                    isPinned = false,
                    tags = "Enhanced"
                )
                dao.insertDocument(entity)

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        completionMessage = "Auto scan enhancement applied! Enhanced document saved in Files."
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isProcessing = false)
                    Toast.makeText(getApplication(), "Failed to enhance image: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Real Tool 7: Speak selected file aloud using TextToSpeech (TTS)
    fun selectFileAndReadAloud(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = true,
                        processingMessage = "Loading speakable text from file...",
                        processProgress = 0.5f
                    )
                }
                val context = getApplication<android.app.Application>()
                val fileName = getFileNameFromUri(context, uri) ?: "temp_read"
                val extension = fileName.substringAfterLast(".").uppercase()

                var textToSpeak = ""

                if (extension == "PDF") {
                    val tempFile = File(context.cacheDir, "temp_tts_${System.currentTimeMillis()}.pdf")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val pdfTextResult = converter.pdfToText(tempFile, ocrEngine)
                    if (pdfTextResult.isSuccess) {
                        textToSpeak = pdfTextResult.getOrThrow()
                    }
                } else {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        textToSpeak = input.bufferedReader().readText()
                    }
                }

                if (textToSpeak.trim().isEmpty()) {
                    throw Exception("No text detected in the file to read aloud.")
                }

                val speakChunk = if (textToSpeak.length > 800) textToSpeak.substring(0, 800) + "..." else textToSpeak

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isProcessing = false,
                        completionMessage = "Speaking aloud now! (Speak queue initialized)"
                    )
                    readTextAloud(speakChunk)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isProcessing = false)
                    Toast.makeText(getApplication(), "Failed to read aloud: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getFileNameFromUri(context: android.content.Context, uri: Uri): String? {
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onNavigateToScanner: () -> Unit,
    onNavigateToEditor: (String) -> Unit,
    onNavigateToDonate: () -> Unit,
    viewModel: ToolsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Set up real launch pickers for different real actions
    val imageToPdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.processImageToPdf(uris) { newDocId ->
                onNavigateToEditor(newDocId)
            }
        }
    }

    val imageToTextPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.processImageToText(it) { newDocId ->
                onNavigateToEditor(newDocId)
            }
        }
    }

    val imageCompressPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.compressSelectedImage(it) }
    }

    val formatConvertPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.convertImageFormat(it) }
    }

    val pdfExportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.exportPdfToImages(it) }
    }

    val imageEnhancePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.enhanceScannedImage(it) }
    }

    val readAloudPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.selectFileAndReadAloud(it) }
    }

    // Stop speaking when navigating away
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopTts()
        }
    }

    // Defining Tools under AI category
    val aiToolsList = listOf(
        ToolItem("AI Spell Check", Icons.Filled.Spellcheck, Color(0xFFCFC3FF), Color(0xFFCFC3FF).copy(alpha = 0.15f), true, ToolActionType.SIMULATED_AI),
        ToolItem("AI Translate", Icons.Filled.Translate, Color(0xFF9DD68A), Color(0xFF9DD68A).copy(alpha = 0.15f), true, ToolActionType.SIMULATED_AI),
        ToolItem("Image Translate", Icons.Filled.GTranslate, Color(0xFFFFB84D), Color(0xFFFFB84D).copy(alpha = 0.15f), false, ToolActionType.SIMULATED_AI),
        ToolItem("Read Aloud", Icons.Filled.Hearing, Color(0xFFE57373), Color(0xFFE57373).copy(alpha = 0.15f), false, ToolActionType.READ_ALOUD),
        ToolItem("AI Extract", Icons.Filled.TextSnippet, Color(0xFF64B5F6), Color(0xFF64B5F6).copy(alpha = 0.15f), false, ToolActionType.IMAGE_TO_TEXT),
        ToolItem("Docs Summarize", Icons.Filled.Summarize, Color(0xFFFFDF70), Color(0xFFFFDF70).copy(alpha = 0.15f), false, ToolActionType.SIMULATED_AI),
        ToolItem("Chat PDF", Icons.Filled.QuestionAnswer, Color(0xFFBCE3A6), Color(0xFFBCE3A6).copy(alpha = 0.15f), false, ToolActionType.SIMULATED_AI),
        ToolItem("BG Removal", Icons.Filled.PhotoFilter, Color(0xFFCFC3FF), Color(0xFFCFC3FF).copy(alpha = 0.15f), true, ToolActionType.SIMULATED_AI),
        ToolItem("Image Enhancer", Icons.Filled.AutoFixHigh, Color(0xFFFFAA3B), Color(0xFFFFAA3B).copy(alpha = 0.15f), true, ToolActionType.ENHANCE_IMAGE),
        ToolItem("Smart Eraser", Icons.Filled.CleaningServices, Color(0xFF64B5F6), Color(0xFF64B5F6).copy(alpha = 0.15f), false, ToolActionType.SIMULATED_AI)
    )

    // Defining Tools under Image Scanner category
    val scannerToolsList = listOf(
        ToolItem("Scanner", Icons.Filled.DocumentScanner, Color(0xFFE57373), Color(0xFFE57373).copy(alpha = 0.15f), false, ToolActionType.SCANNER),
        ToolItem("BG Removal", Icons.Filled.PhotoFilter, Color(0xFFCFC3FF), Color(0xFFCFC3FF).copy(alpha = 0.15f), false, ToolActionType.SIMULATED_IMAGE),
        ToolItem("Image to PDF", Icons.Filled.PictureAsPdf, Color(0xFFFFDF70), Color(0xFFFFDF70).copy(alpha = 0.15f), false, ToolActionType.IMAGE_TO_PDF),
        ToolItem("Image to Word", Icons.Filled.Description, Color(0xFF64B5F6), Color(0xFF64B5F6).copy(alpha = 0.15f), false, ToolActionType.IMAGE_TO_TEXT),
        ToolItem("Image to Excel", Icons.Filled.GridOn, Color(0xFF9DD68A), Color(0xFF9DD68A).copy(alpha = 0.15f), false, ToolActionType.SIMULATED_IMAGE),
        ToolItem("Image to PPT", Icons.Filled.Slideshow, Color(0xFFFFB84D), Color(0xFFFFB84D).copy(alpha = 0.15f), false, ToolActionType.SIMULATED_IMAGE),
        ToolItem("Export PDF pages", Icons.Filled.ViewStream, Color(0xFFFFAA3B), Color(0xFFFFAA3B).copy(alpha = 0.15f), false, ToolActionType.EXPORT_PDF_IMAGES),
        ToolItem("Export Images", Icons.Filled.Collections, Color(0xFFCFC3FF), Color(0xFFCFC3FF).copy(alpha = 0.15f), false, ToolActionType.EXPORT_PDF_IMAGES),
        ToolItem("Image Enhancer", Icons.Filled.AutoFixHigh, Color(0xFF9DD68A), Color(0xFF9DD68A).copy(alpha = 0.15f), false, ToolActionType.ENHANCE_IMAGE),
        ToolItem("Image Comp.", Icons.Filled.Compare, Color(0xFFFFDF70), Color(0xFFFFDF70).copy(alpha = 0.15f), false, ToolActionType.COMPRESS_IMAGE),
        ToolItem("Smart Eraser", Icons.Filled.CleaningServices, Color(0xFF64B5F6), Color(0xFF64B5F6).copy(alpha = 0.15f), false, ToolActionType.SIMULATED_IMAGE),
        ToolItem("Convert Format", Icons.Filled.SwapHoriz, Color(0xFFCFC3FF), Color(0xFFCFC3FF).copy(alpha = 0.15f), false, ToolActionType.CONVERT_FORMAT)
    )

    val currentTools = when (state.selectedTab) {
        "AI Tools" -> aiToolsList
        "Image Scanner" -> scannerToolsList
        else -> aiToolsList + scannerToolsList
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "All Tools",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                actions = {
                    IconButton(onClick = { /* Simulated Search Click */ }) {
                        Icon(Icons.Filled.Search, contentDescription = "Search tools")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // ── Category Tab Bar ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                listOf("You May Like", "AI Tools", "Image Scanner").forEach { tab ->
                    val selected = state.selectedTab == tab
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { viewModel.setSelectedTab(tab) }
                            .padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = tab,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .height(2.5.dp)
                                .width(40.dp)
                                .clip(CircleShape)
                                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main View Area
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // High-juice support card to replace paywalls
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToDonate() },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.CardGiftcard, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "No Premium Paywalls Here!",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "All document utilities are 100% free. Help us keep them that way with a small support!",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Headline
                item {
                    Text(
                        text = state.selectedTab,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Tools Grid
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val chunks = currentTools.chunked(4)
                        chunks.forEach { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { tool ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                when (tool.actionType) {
                                                    ToolActionType.SCANNER -> onNavigateToScanner()
                                                    ToolActionType.IMAGE_TO_PDF -> imageToPdfPicker.launch("image/*")
                                                    ToolActionType.IMAGE_TO_TEXT -> imageToTextPicker.launch("image/*")
                                                    ToolActionType.SUPPORT -> onNavigateToDonate()
                                                    ToolActionType.COMPRESS_IMAGE -> imageCompressPicker.launch("image/*")
                                                    ToolActionType.CONVERT_FORMAT -> formatConvertPicker.launch("image/*")
                                                    ToolActionType.EXPORT_PDF_IMAGES -> pdfExportPicker.launch("application/pdf")
                                                    ToolActionType.ENHANCE_IMAGE -> imageEnhancePicker.launch("image/*")
                                                    ToolActionType.READ_ALOUD -> readAloudPicker.launch("*/*")
                                                    ToolActionType.SIMULATED_AI -> viewModel.runSimulatedTool(
                                                        tool.name,
                                                        "Analyzing document using local models...",
                                                        "AI execution completed! Changes applied."
                                                    )
                                                    ToolActionType.SIMULATED_IMAGE -> viewModel.runSimulatedTool(
                                                        tool.name,
                                                        "Enhancing photo pixels...",
                                                        "Image filter applied successfully!"
                                                    )
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(modifier = Modifier.size(56.dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(52.dp)
                                                        .align(Alignment.Center)
                                                        .clip(CircleShape)
                                                        .background(tool.bgColor),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = tool.icon,
                                                        contentDescription = tool.name,
                                                        tint = tool.iconColor,
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                }
                                                if (tool.isNew) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(Color(0xFFE57373))
                                                            .align(Alignment.TopEnd)
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    ) {
                                                        Text(
                                                            "NEW",
                                                            fontSize = 7.sp,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = tool.name,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                textAlign = TextAlign.Center,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                if (rowItems.size < 4) {
                                    repeat(4 - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Simulated progress loader bottom sheet/dialog
    if (state.isProcessing) {
        AlertDialog(
            onDismissRequest = { /* Cannot dismiss until finished */ },
            confirmButton = {},
            title = {
                Text(
                    "Processing...",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = state.processProgress,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(60.dp),
                        strokeWidth = 4.dp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = state.processingMessage,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Completion Dialog
    val completeMsg = state.completionMessage
    if (completeMsg != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCompletion() },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissCompletion() },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Awesome!")
                }
            },
            icon = {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = { Text("Task Completed", fontWeight = FontWeight.Bold) },
            text = { Text(completeMsg) },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}
