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
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.UUID
import android.os.Environment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration

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
    val docId: String = "",
    val title: String = "Loading...",
    val type: String = "PDF",
    val file: File? = null,
    val textContent: String = "",
    val viewMode: EditorViewMode = EditorViewMode.PDF_VIEW,
    val pages: List<Bitmap> = emptyList(),
    val accentTheme: String = "classic",
    val accentColor: String = "blue",
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

    // AI Chat, Summary & TTS Play States
    val chatMessages = mutableStateListOf<Pair<String, String>>()
    val summaryText = mutableStateOf("")
    val isTtsPlaying = mutableStateOf(false)
    val isExporting = mutableStateOf(false)
    val exportProgress = mutableStateOf(0f)

    private val prefs = application.getSharedPreferences("ai_settings", android.content.Context.MODE_PRIVATE)
    val geminiApiKey = mutableStateOf(prefs.getString("gemini_api_key", "") ?: "")

    fun saveGeminiApiKey(key: String) {
        prefs.edit().putString("gemini_api_key", key).apply()
        geminiApiKey.value = key
    }

    private var tts: TextToSpeech? = null
    private var extractedTextCache = ""

    fun initializeTts(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                }
            }
        }
    }

    fun startReadAloud(context: Context) {
        initializeTts(context)
        viewModelScope.launch(Dispatchers.IO) {
            val text = getExtractedText()
            if (text.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    isTtsPlaying.value = true
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No text found to read.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun stopReadAloud() {
        tts?.stop()
        isTtsPlaying.value = false
    }

    fun releaseTts() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsPlaying.value = false
    }

    private suspend fun getExtractedText(): String {
        if (extractedTextCache.isNotEmpty()) return extractedTextCache
        val file = _uiState.value.file ?: return ""
        val type = _uiState.value.type.uppercase()
        if (type == "TXT") {
            extractedTextCache = _uiState.value.textContent
            return extractedTextCache
        }
        val context = getApplication<android.app.Application>()
        val ocrEngine = com.ceo3.docs.domain.OcrEngine()
        val text = if (type == "PDF") {
            val res = converter.pdfToText(file, ocrEngine)
            res.getOrNull() ?: ""
        } else {
            ""
        }
        extractedTextCache = text
        return text
    }

    fun askChatPdf(question: String) {
        if (question.trim().isEmpty()) return
        chatMessages.add("User" to question)
        val apiKey = geminiApiKey.value

        viewModelScope.launch(Dispatchers.IO) {
            val docText = getExtractedText()
            if (apiKey.trim().isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    chatMessages.add("AI" to "Thinking...")
                }
                val reply = callGeminiApi(apiKey, docText, question)
                withContext(Dispatchers.Main) {
                    if (chatMessages.isNotEmpty() && chatMessages.last().second == "Thinking...") {
                        chatMessages.removeAt(chatMessages.size - 1)
                    }
                    chatMessages.add("AI" to reply)
                }
            } else {
                if (docText.trim().isEmpty()) {
                    withContext(Dispatchers.Main) {
                        chatMessages.add("AI" to "The document appears to be empty, so I can't search for answers.")
                    }
                    return@launch
                }

                val sentences = docText.split(Regex("(?<=[.!?])\\s+")).filter { it.trim().isNotEmpty() }
                val queryWords = question.lowercase().split(Regex("[^a-zA-Z]")).filter { it.length > 2 }
                
                if (queryWords.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        chatMessages.add("AI" to "Please ask a more specific question.")
                    }
                    return@launch
                }

                var bestSentence = ""
                var bestScore = 0f

                sentences.forEach { sentence ->
                    val sentenceWords = sentence.lowercase().split(Regex("[^a-zA-Z]")).filter { it.isNotEmpty() }.toSet()
                    val intersection = queryWords.filter { it in sentenceWords }.size
                    val score = intersection.toFloat() / (queryWords.size + sentenceWords.size - intersection)
                    if (score > bestScore) {
                        bestScore = score
                        bestSentence = sentence
                    }
                }

                val reply = if (bestScore > 0.04f) {
                    "Based on the document context, I found this relevant passage:\n\n\"${bestSentence.trim()}\""
                } else {
                    "I couldn't find a direct reference in the document. Enter a Gemini API key at the top to chat with real AI."
                }

                withContext(Dispatchers.Main) {
                    chatMessages.add("AI" to reply)
                }
            }
        }
    }

    fun summarizeDocument() {
        if (summaryText.value.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val text = getExtractedText()
            if (text.trim().isEmpty()) {
                withContext(Dispatchers.Main) {
                    summaryText.value = "Document is empty."
                }
                return@launch
            }
            val sentences = text.split(Regex("(?<=[.!?])\\s+")).filter { it.trim().isNotEmpty() }
            if (sentences.size <= 3) {
                withContext(Dispatchers.Main) {
                    summaryText.value = sentences.joinToString("\n\n")
                }
                return@launch
            }

            val stopWords = setOf("the", "a", "an", "is", "are", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by", "this", "that", "it", "he", "she", "they", "we", "i", "you")
            val wordCounts = mutableMapOf<String, Int>()
            sentences.forEach { sentence ->
                sentence.lowercase()
                    .split(Regex("[^a-zA-Z]"))
                    .filter { it.length > 2 && it !in stopWords }
                    .forEach { word ->
                        wordCounts[word] = (wordCounts[word] ?: 0) + 1
                    }
            }

            val scoredSentences = sentences.map { sentence ->
                val words = sentence.lowercase().split(Regex("[^a-zA-Z]")).filter { it.isNotEmpty() }
                val score = words.sumOf { wordCounts[it] ?: 0 }
                val normalizedScore = if (words.isNotEmpty()) score.toFloat() / words.size else 0f
                sentence to normalizedScore
            }

            val topSentences = scoredSentences.sortedByDescending { it.second }.take(3).map { it.first.trim() }
            withContext(Dispatchers.Main) {
                summaryText.value = "• " + topSentences.joinToString("\n\n• ")
            }
        }
    }

    fun exportPdfPagesAsImages(context: Context, onComplete: () -> Unit) {
        val file = _uiState.value.file ?: return
        isExporting.value = true
        exportProgress.value = 0f

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
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

                    withContext(Dispatchers.Main) {
                        exportProgress.value = (i + 1).toFloat() / pageCount
                    }
                }
                renderer.close()
                pfd.close()

                withContext(Dispatchers.Main) {
                    isExporting.value = false
                    Toast.makeText(context, "Successfully exported $pageCount pages as images!", Toast.LENGTH_LONG).show()
                    onComplete()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isExporting.value = false
                    Toast.makeText(context, "Failed to export: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun callGeminiApi(apiKey: String, documentContext: String, userQuestion: String): String {
        return try {
            val contextSnippet = if (documentContext.length > 30000) {
                documentContext.substring(0, 30000) + "... [truncated due to size]"
            } else {
                documentContext
            }

            val promptText = "You are a helpful assistant analyzing a document for the user. Here is the context/text of the document:\n\n$contextSnippet\n\nUser Question: $userQuestion\n\nProvide a concise and helpful answer based on the document context."

            val url = java.net.URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val jsonBody = org.json.JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(org.json.JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(org.json.JSONObject().apply {
                                put("text", promptText)
                            })
                        })
                    })
                })
            }

            conn.outputStream.use { os ->
                os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                parseGeminiResponse(responseText)
            } else {
                val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                "API Error (HTTP $responseCode): ${extractErrorMessage(errorText)}"
            }
        } catch (e: Exception) {
            "Error contacting Gemini API: ${e.message}"
        }
    }

    private fun parseGeminiResponse(jsonResponse: String): String {
        return try {
            val root = org.json.JSONObject(jsonResponse)
            val candidates = root.getJSONArray("candidates")
            if (candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.getJSONObject("content")
                val parts = content.getJSONArray("parts")
                if (parts.length() > 0) {
                    return parts.getJSONObject(0).getString("text")
                }
            }
            "No response text returned by Gemini."
        } catch (e: Exception) {
            "Failed to parse AI response: ${e.message}"
        }
    }

    private fun extractErrorMessage(errorJson: String): String {
        return try {
            val root = org.json.JSONObject(errorJson)
            val error = root.getJSONObject("error")
            error.getString("message")
        } catch (e: Exception) {
            errorJson
        }
    }

    fun loadDocument(docIdOrUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<android.app.Application>()
                var resolvedFile: File? = null
                var docTitle = "Document"
                var docType = "PDF"
                var textBody = ""
                var loadedTheme = "classic"
                var loadedColor = "blue"

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
                        loadedTheme = docEntity.accentTheme
                        loadedColor = docEntity.accentColor
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
                                docId = docIdOrUri,
                                title = docTitle,
                                type = docType,
                                file = resolvedFile,
                                textContent = textBody,
                                viewMode = EditorViewMode.TXT_VIEW,
                                accentTheme = loadedTheme,
                                accentColor = loadedColor,
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
                                docId = docIdOrUri,
                                title = docTitle,
                                type = docType,
                                file = pdfCacheFile,
                                pages = bitmaps,
                                viewMode = EditorViewMode.PDF_VIEW,
                                accentTheme = loadedTheme,
                                accentColor = loadedColor,
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
                                docId = docIdOrUri,
                                title = docTitle,
                                type = docType,
                                file = resolvedFile,
                                pages = bitmaps,
                                viewMode = EditorViewMode.PDF_VIEW,
                                accentTheme = loadedTheme,
                                accentColor = loadedColor,
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

    fun saveTextDocument(content: String, onComplete: () -> Unit = {}) {
        val file = _uiState.value.file ?: return
        _uiState.value = _uiState.value.copy(isSaving = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                file.writeText(content)
                val currentDocId = _uiState.value.docId
                if (currentDocId.isNotEmpty() && !currentDocId.startsWith("/") && !currentDocId.startsWith("content")) {
                    val existing = dao.getDocumentById(currentDocId)
                    if (existing != null) {
                        dao.insertDocument(
                            existing.copy(
                                lastModified = System.currentTimeMillis()
                            )
                        )
                    }
                }
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        textContent = content
                    )
                    onComplete()
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        error = "Failed to save text: ${e.message}"
                    )
                }
            }
        }
    }

    fun updateDocumentStyling(theme: String, color: String) {
        val currentDocId = _uiState.value.docId
        _uiState.value = _uiState.value.copy(accentTheme = theme, accentColor = color)
        if (currentDocId.isNotEmpty() && !currentDocId.startsWith("/") && !currentDocId.startsWith("content")) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val existing = dao.getDocumentById(currentDocId)
                    if (existing != null) {
                        dao.insertDocument(
                            existing.copy(
                                accentTheme = theme,
                                accentColor = color,
                                lastModified = System.currentTimeMillis()
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
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

    val parts = remember(documentId) { documentId.split("::") }
    val actualDocId = parts[0]
    val initialTool = if (parts.size > 1) parts[1] else ""

    var activeAiTool by remember { mutableStateOf(initialTool) }
    var showAiMenu by remember { mutableStateOf(false) }
    var editorText by remember(state.textContent) { mutableStateOf(state.textContent) }
    var isEditMode by remember { mutableStateOf(false) }

    LaunchedEffect(documentId) {
        viewModel.loadDocument(actualDocId)
        if (initialTool == "Read Aloud") {
            viewModel.startReadAloud(context)
        } else if (initialTool == "Docs Summarize") {
            viewModel.summarizeDocument()
        } else if (initialTool == "Export PDF pages") {
            viewModel.exportPdfPagesAsImages(context) {
                activeAiTool = ""
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.releaseTts()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.releaseTts()
                        onNavigateBack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.viewMode == EditorViewMode.PDF_VIEW && state.pages.isNotEmpty()) {
                        IconButton(onClick = { showAiMenu = !showAiMenu }) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = "AI Tools", tint = MaterialTheme.colorScheme.primary)
                        }
                        DropdownMenu(
                            expanded = showAiMenu,
                            onDismissRequest = { showAiMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Chat PDF") },
                                onClick = {
                                    activeAiTool = "Chat PDF"
                                    showAiMenu = false
                                },
                                leadingIcon = { Icon(Icons.Filled.QuestionAnswer, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Summarize") },
                                onClick = {
                                    activeAiTool = "Docs Summarize"
                                    viewModel.summarizeDocument()
                                    showAiMenu = false
                                },
                                leadingIcon = { Icon(Icons.Filled.Summarize, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Read Aloud") },
                                onClick = {
                                    activeAiTool = "Read Aloud"
                                    viewModel.startReadAloud(context)
                                    showAiMenu = false
                                },
                                leadingIcon = { Icon(Icons.Filled.Hearing, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Pages") },
                                onClick = {
                                    activeAiTool = "Export PDF pages"
                                    viewModel.exportPdfPagesAsImages(context) {
                                        activeAiTool = ""
                                    }
                                    showAiMenu = false
                                },
                                leadingIcon = { Icon(Icons.Filled.ViewStream, contentDescription = null) }
                            )
                        }
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
                    } else if (state.viewMode == EditorViewMode.TXT_VIEW) {
                        IconButton(onClick = {
                            viewModel.saveTextDocument(editorText) {
                                Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            if (state.isSaving) {
                                CircularProgressIndicator(Modifier.size(16.dp))
                            } else {
                                Icon(Icons.Filled.Save, contentDescription = "Save")
                            }
                        }
                        IconButton(onClick = {
                            viewModel.exportStyledTextToPdf(context, editorText) { pdfFile ->
                                Toast.makeText(context, "Exported PDF to Downloads/!", Toast.LENGTH_LONG).show()
                            }
                        }) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = "Export to PDF")
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
                        // ── Text File Viewer / Editor ──────────────────────────────────
                        val activeTheme = documentThemes.firstOrNull { it.themeKey == state.accentTheme }
                            ?: documentThemes[0]

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(activeTheme.windowBg)
                                .padding(16.dp)
                        ) {
                            // Theme Styling selector bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Style:",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = activeTheme.textSecondary,
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                                documentThemes.forEach { theme ->
                                    val isSelected = theme.themeKey == state.accentTheme
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(
                                                if (isSelected) theme.primaryAccent.copy(alpha = 0.15f)
                                                else Color.Transparent
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) theme.primaryAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(20.dp)
                                            )
                                            .clickable {
                                                viewModel.updateDocumentStyling(theme.themeKey, theme.colorKey)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(theme.primaryAccent)
                                            )
                                            Text(
                                                text = theme.name,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) theme.primaryAccent else activeTheme.textSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            // Mode Selector Tabs (Edit / Preview)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(activeTheme.paperBg.copy(alpha = 0.5f))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (!isEditMode) activeTheme.primaryAccent else Color.Transparent)
                                        .clickable { isEditMode = false }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Preview",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (!isEditMode) Color.White else activeTheme.textSecondary
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isEditMode) activeTheme.primaryAccent else Color.Transparent)
                                        .clickable { isEditMode = true }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Edit Text",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isEditMode) Color.White else activeTheme.textSecondary
                                    )
                                }
                            }

                            // Content Editor / Preview Card
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, activeTheme.primaryAccent.copy(alpha = 0.1f)),
                                colors = CardDefaults.cardColors(containerColor = activeTheme.paperBg),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                if (isEditMode) {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        // Quick markdown keys bar
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(activeTheme.windowBg.copy(alpha = 0.5f))
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val keys = listOf(
                                                "#" to "# Title",
                                                "##" to "## Section",
                                                "-" to "- Bullet",
                                                "- [ ]" to "- [ ] Todo",
                                                "**" to "**Bold**",
                                                ">" to "> Quote"
                                            )
                                            keys.forEach { (label, snippet) ->
                                                TextButton(
                                                    onClick = {
                                                        editorText += if (editorText.isNotEmpty() && !editorText.endsWith("\n")) "\n$snippet" else snippet
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.height(28.dp)
                                                ) {
                                                    Text(label, fontSize = 11.sp, color = activeTheme.primaryAccent, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                        Divider(color = activeTheme.primaryAccent.copy(alpha = 0.1f))
                                        TextField(
                                            value = editorText,
                                            onValueChange = { editorText = it },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                fontFamily = activeTheme.fontFamily,
                                                fontSize = 14.sp,
                                                lineHeight = 22.sp,
                                                color = activeTheme.textPrimary
                                            ),
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent,
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent,
                                                cursorColor = activeTheme.primaryAccent
                                            ),
                                            placeholder = { Text("Start typing...", color = activeTheme.textSecondary.copy(alpha = 0.5f)) }
                                        )
                                    }
                                } else {
                                    // Preview Mode styled document renderer
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState())
                                            .padding(20.dp)
                                    ) {
                                        StyledDocumentPreview(
                                            text = editorText,
                                            theme = activeTheme
                                        )
                                    }
                                }
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

            // AI Overlay interfaces
            if (activeAiTool == "Chat PDF") {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(380.dp)
                        .padding(16.dp),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.QuestionAnswer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Chat PDF Workspace", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { activeAiTool = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close")
                            }
                        }
                        
                        var showApiKeyInput by remember { mutableStateOf(viewModel.geminiApiKey.value.isEmpty()) }
                        var apiKeyInputText by remember { mutableStateOf(viewModel.geminiApiKey.value) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (viewModel.geminiApiKey.value.isNotEmpty()) "Gemini AI: Connected" else "Gemini AI: Offline Mode",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (viewModel.geminiApiKey.value.isNotEmpty()) Color(0xFF10B981) else Color.Gray,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { showApiKeyInput = !showApiKeyInput },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(if (showApiKeyInput) "Hide Key Setup" else "Setup API Key", fontSize = 11.sp)
                            }
                        }

                        if (showApiKeyInput) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = apiKeyInputText,
                                    onValueChange = { apiKeyInputText = it },
                                    label = { Text("Enter Gemini API Key", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    trailingIcon = {
                                        if (apiKeyInputText.isNotEmpty()) {
                                            IconButton(onClick = {
                                                viewModel.saveGeminiApiKey(apiKeyInputText)
                                                showApiKeyInput = false
                                            }) {
                                                Icon(Icons.Filled.Check, contentDescription = "Save Key", tint = Color(0xFF10B981))
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(viewModel.chatMessages) { _, (sender, msg) ->
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = if (sender == "User") Alignment.End else Alignment.Start
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (sender == "User") MaterialTheme.colorScheme.primary else Color.White)
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = msg,
                                            color = if (sender == "User") Color.White else Color.Black,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        var chatInput by remember { mutableStateOf("") }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = chatInput,
                                onValueChange = { chatInput = it },
                                placeholder = { Text("Ask about document...") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    viewModel.askChatPdf(chatInput)
                                    chatInput = ""
                                }
                            ) {
                                Icon(Icons.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            if (activeAiTool == "Docs Summarize") {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Summarize, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Document Summary", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { activeAiTool = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        if (viewModel.summaryText.value.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        } else {
                            Text(
                                text = viewModel.summaryText.value,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (activeAiTool == "Read Aloud") {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Hearing, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (viewModel.isTtsPlaying.value) "Reading Aloud..." else "Paused",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (viewModel.isTtsPlaying.value) {
                                    viewModel.stopReadAloud()
                                } else {
                                    viewModel.startReadAloud(context)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (viewModel.isTtsPlaying.value) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play/Pause"
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.stopReadAloud()
                                activeAiTool = ""
                            }
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = "Stop")
                        }
                    }
                }
            }

            if (viewModel.isExporting.value) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.width(280.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(progress = viewModel.exportProgress.value)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Exporting pages: ${(viewModel.exportProgress.value * 100).toInt()}%",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

data class StylingTheme(
    val name: String,
    val themeKey: String,
    val colorKey: String,
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val paperBg: Color,
    val windowBg: Color,
    val fontFamily: FontFamily
)

val documentThemes = listOf(
    StylingTheme("Classic Navy", "classic", "blue", Color(0xFF1E3A8A), Color(0xFF3B82F6), Color(0xFF0F172A), Color(0xFF475569), Color.White, Color(0xFFF8FAFC), FontFamily.SansSerif),
    StylingTheme("Modern Mint", "mint", "green", Color(0xFF065F46), Color(0xFF10B981), Color(0xFF1F2937), Color(0xFF4B5563), Color.White, Color(0xFFF0FDF4), FontFamily.SansSerif),
    StylingTheme("Warm Sepia", "sepia", "orange", Color(0xFF78350F), Color(0xFFD97706), Color(0xFF451A03), Color(0xFF78350F), Color(0xFFFDFBF7), Color(0xFFFAF6F0), FontFamily.Serif),
    StylingTheme("Charcoal Slate", "charcoal", "purple", Color(0xFFC084FC), Color(0xFF818CF8), Color(0xFFF8FAFC), Color(0xFF94A3B8), Color(0xFF1E293B), Color(0xFF0F172A), FontFamily.SansSerif),
    StylingTheme("Royal Ruby", "ruby", "red", Color(0xFF9F1239), Color(0xFFF43F5E), Color(0xFF4C0519), Color(0xFF881337), Color.White, Color(0xFFFFF1F2), FontFamily.Serif)
)

@Composable
fun StyledDocumentPreview(
    text: String,
    theme: StylingTheme,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val lines = text.split("\n")
        lines.forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.startsWith("# ") -> {
                    val content = line.substring(2)
                    Text(
                        text = content,
                        fontFamily = theme.fontFamily,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.primaryAccent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 4.dp)
                    )
                    Divider(
                        color = theme.primaryAccent.copy(alpha = 0.3f),
                        thickness = 2.dp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                line.startsWith("## ") -> {
                    val content = line.substring(3)
                    Text(
                        text = content,
                        fontFamily = theme.fontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = theme.secondaryAccent,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                line.startsWith("### ") -> {
                    val content = line.substring(4)
                    Text(
                        text = content,
                        fontFamily = theme.fontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = theme.textPrimary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                line.startsWith("- [x] ") || line.startsWith("- [X] ") -> {
                    val content = line.substring(6)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Done",
                            tint = theme.secondaryAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = content,
                            fontFamily = theme.fontFamily,
                            fontSize = 14.sp,
                            color = theme.textSecondary,
                            textDecoration = TextDecoration.LineThrough
                        )
                    }
                }
                line.startsWith("- [ ] ") -> {
                    val content = line.substring(6)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RadioButtonUnchecked,
                            contentDescription = "Todo",
                            tint = theme.textSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = content,
                            fontFamily = theme.fontFamily,
                            fontSize = 14.sp,
                            color = theme.textPrimary
                        )
                    }
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    val content = line.substring(2)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            fontFamily = theme.fontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.primaryAccent,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = parseBoldText(content, theme),
                            fontFamily = theme.fontFamily,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = theme.textPrimary
                        )
                    }
                }
                line.startsWith("> ") -> {
                    val content = line.substring(2)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .drawBehind {
                                drawLine(
                                    color = theme.primaryAccent,
                                    start = Offset(0f, 0f),
                                    end = Offset(0f, size.height),
                                    strokeWidth = 4.dp.toPx()
                                )
                            }
                            .padding(start = 12.dp)
                    ) {
                        Text(
                            text = parseBoldText(content, theme),
                            fontFamily = theme.fontFamily,
                            fontSize = 13.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = 18.sp,
                            color = theme.textSecondary
                        )
                    }
                }
                line.isEmpty() -> {
                    Spacer(modifier = Modifier.height(6.dp))
                }
                else -> {
                    Text(
                        text = parseBoldText(line, theme),
                        fontFamily = theme.fontFamily,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = theme.textPrimary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

fun parseBoldText(text: String, theme: StylingTheme): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val parts = text.split("**")
    for (i in parts.indices) {
        if (i % 2 == 1) {
            builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = theme.primaryAccent))
            builder.append(parts[i])
            builder.pop()
        } else {
            builder.append(parts[i])
        }
    }
    return builder.toAnnotatedString()
}
