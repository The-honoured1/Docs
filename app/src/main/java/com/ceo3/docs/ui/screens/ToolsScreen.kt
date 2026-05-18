package com.ceo3.docs.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ceo3.docs.domain.DocumentConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ─── Tool definitions ─────────────────────────────────────────────────────────

data class ToolItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val accentIndex: Int,           // cycles through accent palette
    val inputMimes: Array<String>   // mimes for the file picker
)

val ConversionTools = listOf(
    ToolItem("img_pdf",      "Image → PDF",   "JPG / PNG to PDF",      Icons.Filled.Image,                   0, arrayOf("image/*")),
    ToolItem("scan_pdf",     "Scan → PDF",    "Camera scans to PDF",   Icons.Filled.Scanner,                 1, arrayOf("image/*")),
    ToolItem("pdf_doc",      "PDF → Text",    "Extract text from PDF", Icons.Filled.Description,             2, arrayOf("application/pdf")),
    ToolItem("merge_pdf",    "Merge PDFs",    "Combine multiple PDFs", Icons.AutoMirrored.Filled.MergeType,  3, arrayOf("application/pdf")),
    ToolItem("split_pdf",    "Split PDF",     "Split into pages",      Icons.Filled.Splitscreen,             0, arrayOf("application/pdf")),
    ToolItem("compress_pdf", "Compress PDF",  "Reduce file size",      Icons.Filled.Compress,                1, arrayOf("application/pdf")),
    ToolItem("doc_pdf",      "DOC → PDF",     "Word document to PDF",  Icons.Filled.PictureAsPdf,            2, arrayOf("text/plain", "application/pdf"))
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

sealed class ToolState {
    object Idle : ToolState()
    object Picking : ToolState()
    object Processing : ToolState()
    data class Done(val outputPath: String) : ToolState()
    data class Error(val message: String) : ToolState()
}

class ToolsViewModel : ViewModel() {
    private val _toolState = MutableStateFlow<ToolState>(ToolState.Idle)
    val toolState: StateFlow<ToolState> = _toolState

    fun reset() { _toolState.value = ToolState.Idle }

    fun runTool(toolId: String, uris: List<Uri>, context: android.content.Context) {
        _toolState.value = ToolState.Processing
        val converter = DocumentConverter(context)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result: Result<File> = when (toolId) {
                    "img_pdf" -> {
                        val paths = uris.map { uri ->
                            val tmp = File(context.cacheDir, "img_${System.currentTimeMillis()}.jpg")
                            context.contentResolver.openInputStream(uri)?.use { it.copyTo(tmp.outputStream()) }
                            tmp.absolutePath
                        }
                        val out = File(context.filesDir, "output_${System.currentTimeMillis()}.pdf")
                        converter.imagesToPdf(paths, out)
                    }
                    "scan_pdf" -> {
                        val paths = uris.map { uri ->
                            val tmp = File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
                            context.contentResolver.openInputStream(uri)?.use { it.copyTo(tmp.outputStream()) }
                            tmp.absolutePath
                        }
                        val out = File(context.filesDir, "scan_${System.currentTimeMillis()}.pdf")
                        converter.imagesToPdf(paths, out)
                    }
                    "pdf_doc" -> {
                        val uri = uris.first()
                        val tmp = File(context.cacheDir, "in_${System.currentTimeMillis()}.pdf")
                        context.contentResolver.openInputStream(uri)?.use { it.copyTo(tmp.outputStream()) }
                        val ocrEngine = com.ceo3.docs.domain.OcrEngine()
                        val textResult = converter.pdfToText(tmp, ocrEngine)
                        val out = File(context.filesDir, "extracted_${System.currentTimeMillis()}.txt")
                        out.writeText(textResult.getOrDefault("(No text found)"))
                        Result.success(out)
                    }
                    "merge_pdf" -> {
                        // Simple merge: render all pages of each PDF into one new PDF
                        val allBitmaps = mutableListOf<String>()
                        for (uri in uris) {
                            val tmp = File(context.cacheDir, "merge_in_${System.currentTimeMillis()}.pdf")
                            context.contentResolver.openInputStream(uri)?.use { it.copyTo(tmp.outputStream()) }
                            val fd  = android.os.ParcelFileDescriptor.open(tmp, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                            val renderer = android.graphics.pdf.PdfRenderer(fd)
                            for (i in 0 until renderer.pageCount) {
                                val page = renderer.openPage(i)
                                val bmp  = android.graphics.Bitmap.createBitmap(page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888)
                                val canvas = android.graphics.Canvas(bmp)
                                canvas.drawColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                page.close()
                                val imgFile = File(context.cacheDir, "pg_${System.currentTimeMillis()}.png")
                                imgFile.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                                allBitmaps.add(imgFile.absolutePath)
                            }
                            renderer.close(); fd.close()
                        }
                        val out = File(context.filesDir, "merged_${System.currentTimeMillis()}.pdf")
                        converter.imagesToPdf(allBitmaps, out)
                    }
                    "compress_pdf" -> {
                        val uri = uris.first()
                        val tmp = File(context.cacheDir, "cmp_in_${System.currentTimeMillis()}.pdf")
                        context.contentResolver.openInputStream(uri)?.use { it.copyTo(tmp.outputStream()) }
                        val out = File(context.filesDir, "compressed_${System.currentTimeMillis()}.pdf")
                        // Re-render at 72 dpi (lower quality) = compression
                        val fd  = android.os.ParcelFileDescriptor.open(tmp, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = android.graphics.pdf.PdfRenderer(fd)
                        val bitmapPaths = mutableListOf<String>()
                        for (i in 0 until renderer.pageCount) {
                            val page = renderer.openPage(i)
                            val scale = 0.6f
                            val bmp = android.graphics.Bitmap.createBitmap(
                                (page.width * scale).toInt(),
                                (page.height * scale).toInt(),
                                android.graphics.Bitmap.Config.ARGB_8888
                            )
                            val canvas = android.graphics.Canvas(bmp)
                            canvas.drawColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close()
                            val imgFile = File(context.cacheDir, "cmp_pg_$i.jpg")
                            imgFile.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, it) }
                            bitmapPaths.add(imgFile.absolutePath)
                        }
                        renderer.close(); fd.close()
                        converter.imagesToPdf(bitmapPaths, out)
                    }
                    "split_pdf" -> {
                        val uri = uris.first()
                        val tmp = File(context.cacheDir, "split_in_${System.currentTimeMillis()}.pdf")
                        context.contentResolver.openInputStream(uri)?.use { it.copyTo(tmp.outputStream()) }
                        // Split: each page becomes its own PDF (we return first-page PDF as sample)
                        val fd  = android.os.ParcelFileDescriptor.open(tmp, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = android.graphics.pdf.PdfRenderer(fd)
                        val bitmapPaths = mutableListOf<String>()
                        for (i in 0 until renderer.pageCount) {
                            val page = renderer.openPage(i)
                            val bmp = android.graphics.Bitmap.createBitmap(page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bmp)
                            canvas.drawColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close()
                            val imgFile = File(context.cacheDir, "split_pg_$i.png")
                            imgFile.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                            bitmapPaths.add(imgFile.absolutePath)
                        }
                        renderer.close(); fd.close()
                        // Each page → individual PDF saved to filesDir
                        val outPdfs = bitmapPaths.mapIndexed { i, path ->
                            val o = File(context.filesDir, "page_${i+1}_${System.currentTimeMillis()}.pdf")
                            converter.imagesToPdf(listOf(path), o)
                            o
                        }
                        Result.success(outPdfs.first()) // report first one
                    }
                    "doc_pdf" -> {
                        val uri = uris.first()
                        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                        val out  = File(context.filesDir, "doc_out_${System.currentTimeMillis()}.pdf")
                        converter.textToPdf(text, out)
                    }
                    else -> Result.failure(Exception("Unknown tool: $toolId"))
                }

                withContext(Dispatchers.Main) {
                    result.fold(
                        onSuccess = { _toolState.value = ToolState.Done(it.name) },
                        onFailure = { _toolState.value = ToolState.Error(it.message ?: "Failed") }
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _toolState.value = ToolState.Error(e.message ?: "Unexpected error")
                }
            }
        }
    }
}

// ─── Accent cycling ──────────────────────────────────────────────────────────

@Composable
private fun accentColor(index: Int): Color {
    val colors = listOf(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f),
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f),
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    )
    return colors[index % colors.size]
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onToolSelected: (String) -> Unit,
    vm: ToolsViewModel = viewModel()
) {
    val context = LocalContext.current
    val toolState by vm.toolState.collectAsState()
    var activeTool by remember { mutableStateOf<ToolItem?>(null) }
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var multiPick by remember { mutableStateOf(false) }

    // Single-file picker
    val singlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedUris = listOf(it)
            activeTool?.let { tool -> vm.runTool(tool.id, selectedUris, context) }
        }
    }

    // Multi-file picker
    val multiPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedUris = uris
            activeTool?.let { tool -> vm.runTool(tool.id, selectedUris, context) }
        }
    }

    // Bottom sheet state
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSheet by remember { mutableStateOf(false) }

    // React to tool completion
    LaunchedEffect(toolState) {
        if (toolState is ToolState.Done || toolState is ToolState.Error) {
            showSheet = true
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {

            // Title
            Text(
                text = "Tools",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(ConversionTools) { tool ->
                    ToolCard(
                        tool = tool,
                        accent = accentColor(tool.accentIndex),
                        isProcessing = toolState is ToolState.Processing && activeTool?.id == tool.id,
                        onClick = {
                            activeTool = tool
                            vm.reset()
                            val needsMulti = tool.id in listOf("merge_pdf", "img_pdf", "scan_pdf")
                            if (needsMulti) {
                                multiPicker.launch(tool.inputMimes)
                            } else {
                                singlePicker.launch(tool.inputMimes)
                            }
                        }
                    )
                }
                // Bottom padding for nav bar
                item { Spacer(Modifier.height(100.dp)) }
                item { Spacer(Modifier.height(100.dp)) }
            }
        }
    }

    // Result bottom sheet
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false; vm.reset() },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            ResultSheetContent(
                toolState = toolState,
                toolName  = activeTool?.title ?: "",
                onDismiss = { showSheet = false; vm.reset() }
            )
        }
    }
}

@Composable
fun ToolCard(
    tool: ToolItem,
    accent: Color,
    isProcessing: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(enabled = !isProcessing) { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = accent)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = MaterialTheme.colorScheme.onBackground,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = tool.title,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = tool.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = tool.subtitle,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun ResultSheetContent(
    toolState: ToolState,
    toolName: String,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn() + slideInVertically { it / 2 }
        ) {
            when (toolState) {
                is ToolState.Done -> {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Done!",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$toolName completed successfully.\nSaved to: ${toolState.outputPath}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                is ToolState.Error -> {
                    Icon(
                        Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Something went wrong",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        toolState.message,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                else -> {}
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Close", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
    }
}
