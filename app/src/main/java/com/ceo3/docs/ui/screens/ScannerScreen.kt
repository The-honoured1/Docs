package com.ceo3.docs.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ceo3.docs.data.local.DocumentEntity
import com.ceo3.docs.domain.DocumentConverter
import com.ceo3.docs.domain.OcrEngine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// ─── Scanned Page definition ──────────────────────────────────────────────────

data class ScannedPage(
    val rawPath: String,
    var processedPath: String,
    val filterName: String = "original" // original, magic, grayscale, contrast
)

// ─── ViewModel ────────────────────────────────────────────────────────────────

class ScannerViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState

    private val db = com.ceo3.docs.data.local.DocDatabase.getDatabase(application).documentDao()
    private val converter = DocumentConverter(application)
    private val ocrEngine = OcrEngine()

    fun onImageCaptured(rawPath: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val bmp = BitmapFactory.decodeFile(rawPath) ?: return@launch
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    activeCapture = bmp,
                    activeCapturePath = rawPath,
                    isCropping = true
                )
            }
            // Auto-crop via ML Kit text bounding box detection
            val croppedBmp = autoCropBitmap(bmp)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    activeCapture = croppedBmp,
                    isCropping = false
                )
            }
        }
    }

    private suspend fun autoCropBitmap(bitmap: Bitmap): Bitmap {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val visionText = recognizer.process(image).await()
            val blocks = visionText.textBlocks
            if (blocks.isNotEmpty()) {
                var minX = bitmap.width
                var minY = bitmap.height
                var maxX = 0
                var maxY = 0
                for (block in blocks) {
                    block.boundingBox?.let { box ->
                        if (box.left < minX) minX = box.left
                        if (box.top < minY) minY = box.top
                        if (box.right > maxX) maxX = box.right
                        if (box.bottom > maxY) maxY = box.bottom
                    }
                }
                val pad = 40
                val startX = (minX - pad).coerceAtLeast(0)
                val startY = (minY - pad).coerceAtLeast(0)
                val width = (maxX - minX + 2 * pad).coerceAtMost(bitmap.width - startX)
                val height = (maxY - minY + 2 * pad).coerceAtMost(bitmap.height - startY)
                if (width > 50 && height > 50) {
                    Bitmap.createBitmap(bitmap, startX, startY, width, height)
                } else {
                    bitmap
                }
            } else {
                bitmap
            }
        } catch (e: Exception) {
            bitmap
        }
    }

    fun applyFilterAndSave(filterName: String) {
        val rawBmp = _uiState.value.activeCapture ?: return
        val path = _uiState.value.activeCapturePath ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val filteredBmp = applyFilterToBitmap(rawBmp, filterName)
            val outFile = File(getApplication<android.app.Application>().cacheDir, "processed_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { out ->
                filteredBmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            withContext(Dispatchers.Main) {
                val newPage = ScannedPage(
                    rawPath = path,
                    processedPath = outFile.absolutePath,
                    filterName = filterName
                )
                _uiState.value = _uiState.value.copy(
                    capturedPages = _uiState.value.capturedPages + newPage,
                    activeCapture = null,
                    activeCapturePath = null
                )
            }
        }
    }

    fun applyFilterToBitmap(bitmap: Bitmap, filter: String): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val filtered = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(filtered)
        val paint = Paint()
        val matrix = ColorMatrix()

        when (filter) {
            "magic" -> {
                // High saturation and contrast booster for paper scans
                matrix.set(floatArrayOf(
                    1.4f, 0f, 0f, 0f, 20f,
                    0f, 1.4f, 0f, 0f, 20f,
                    0f, 0f, 1.4f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            "grayscale" -> {
                matrix.setSaturation(0f)
            }
            "contrast" -> {
                matrix.set(floatArrayOf(
                    1.7f, 0f, 0f, 0f, -50f,
                    0f, 1.7f, 0f, 0f, -50f,
                    0f, 0f, 1.7f, 0f, -50f,
                    0f, 0f, 0f, 1f, 0f
                ))
            }
            else -> {
                return bitmap
            }
        }
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return filtered
    }

    fun removePage(page: ScannedPage) {
        _uiState.value = _uiState.value.copy(
            capturedPages = _uiState.value.capturedPages.filter { it.processedPath != page.processedPath }
        )
    }

    fun cancelCropWorkspace() {
        _uiState.value = _uiState.value.copy(
            activeCapture = null,
            activeCapturePath = null
        )
    }

    fun exportScans(
        docName: String,
        format: String,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        val pages = _uiState.value.capturedPages
        if (pages.isEmpty()) return
        _uiState.value = _uiState.value.copy(isExporting = true)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val docId = System.currentTimeMillis().toString()
                val finalFile = File(getApplication<android.app.Application>().filesDir, "doc_${docId}.${format.lowercase()}")

                when (format) {
                    "PDF" -> {
                        val imagePaths = pages.map { it.processedPath }
                        converter.imagesToPdf(imagePaths, finalFile).getOrThrow()
                    }
                    "TXT" -> {
                        // OCR Text joiner
                        val builder = StringBuilder()
                        for (page in pages) {
                            val bmp = BitmapFactory.decodeFile(page.processedPath) ?: continue
                            val res = ocrEngine.extractTextFromImage(bmp)
                            if (res.isSuccess) {
                                builder.append(res.getOrNull()).append("\n\n")
                            }
                        }
                        finalFile.writeText(builder.toString())
                    }
                }

                // Insert into Room
                db.insertDocument(
                    DocumentEntity(
                        id = docId,
                        title = docName,
                        type = format,
                        lastModified = System.currentTimeMillis(),
                        isPinned = false,
                        tags = "Personal"
                    )
                )

                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isExporting = false, capturedPages = emptyList())
                    onComplete()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(isExporting = false)
                    onError(e.message ?: "Failed to generate document")
                }
            }
        }
    }
}

data class ScannerUiState(
    val capturedPages: List<ScannedPage> = emptyList(),
    val activeCapture: Bitmap? = null,
    val activeCapturePath: String? = null,
    val isCropping: Boolean = false,
    val isExporting: Boolean = false
)

// ─── Screen Composable ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onScanComplete: () -> Unit,
    onCancel: () -> Unit,
    viewModel: ScannerViewModel = viewModel()
) {
    val state = viewModel.uiState.collectAsState().value
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var flashEnabled by remember { mutableStateOf(false) }
    var isCaptureInProgress by remember { mutableStateOf(false) }

    var showExportSheet by remember { mutableStateOf(false) }
    var docName by remember { mutableStateOf("Scan_${System.currentTimeMillis() / 1000}") }
    var exportFormat by remember { mutableStateOf("PDF") }

    var selectedFilter by remember { mutableStateOf("original") }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasPermission = it }

    LaunchedEffect(Unit) {
        if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(flashEnabled) {
        camera?.cameraControl?.enableTorch(flashEnabled)
    }

    Scaffold(containerColor = Color.Black) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (hasPermission) {
                // ── Camera Preview ────────────────────────────────────────
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
                        val executor = ContextCompat.getMainExecutor(ctx)
                        cameraProviderFuture.addListener({
                            val provider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            imageCapture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                .build()
                            try {
                                provider.unbindAll()
                                camera = provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCapture
                                )
                            } catch (e: Exception) {
                                Log.e("Scanner", "Camera bind failed", e)
                            }
                        }, executor)
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // ── Document boundary overlays ────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 120.dp)
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.tertiary,
                            shape = RoundedCornerShape(16.dp)
                        )
                )

                CornerAccents()

                // Hint label
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 104.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        "Align document inside the frame",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }

                // Top action bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CamIconBtn(
                        icon = Icons.Filled.Close,
                        label = "Cancel",
                        tint = Color.White,
                        bgAlpha = 0.35f,
                        onClick = onCancel
                    )
                    CamIconBtn(
                        icon = if (flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                        label = "Flash",
                        tint = if (flashEnabled) Color(0xFFFFDF70) else Color.White,
                        bgAlpha = if (flashEnabled) 0.6f else 0.35f,
                        onClick = { flashEnabled = !flashEnabled }
                    )
                }
            } else {
                // Permission Denied View
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Camera access is required to scan.",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { permLauncher.launch(Manifest.permission.CAMERA) },
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Grant Permission")
                    }
                }
            }

            // ── Camera Bottom Panel ───────────────────────────────────────
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Thumbnail Strip
                if (state.capturedPages.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(state.capturedPages) { index, page ->
                            Box {
                                val bmp = remember(page.processedPath) {
                                    BitmapFactory.decodeFile(page.processedPath)
                                }
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Page ${index + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(52.dp, 72.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .align(Alignment.TopEnd)
                                        .clickable { viewModel.removePage(page) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Remove",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Control Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${state.capturedPages.size}",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Shutter button
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable(enabled = hasPermission && !isCaptureInProgress) {
                                val file = File(
                                    context.cacheDir,
                                    "raw_scan_${System.currentTimeMillis()}.jpg"
                                )
                                val opts = ImageCapture.OutputFileOptions.Builder(file).build()
                                isCaptureInProgress = true
                                imageCapture?.takePicture(
                                    opts,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                                            viewModel.onImageCaptured(file.absolutePath)
                                            isCaptureInProgress = false
                                        }
                                        override fun onError(e: ImageCaptureException) {
                                            isCaptureInProgress = false
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCaptureInProgress) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(36.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .border(3.dp, Color.Black, CircleShape)
                            )
                        }
                    }

                    // Done/Join button
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.capturedPages.isEmpty()) Color.White.copy(alpha = 0.15f)
                                else Color(0xFF9DD68A)
                            )
                            .clickable(enabled = state.capturedPages.isNotEmpty()) {
                                showExportSheet = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Done,
                            contentDescription = "Export Scans",
                            tint = if (state.capturedPages.isEmpty()) Color.Gray else Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // ── ML Kit Crop & Custom Filters Workspace Overlay ──────────────────
            AnimatedVisibility(
                visible = state.activeCapture != null,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF111214))
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top Workspace Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Scan Workbench",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { viewModel.cancelCropWorkspace() }) {
                                Icon(Icons.Filled.Close, contentDescription = "Discard", tint = Color.White)
                            }
                        }

                        // Main Preview Sheet
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            val activeBmp = state.activeCapture
                            if (activeBmp != null) {
                                val filteredBmp = remember(activeBmp, selectedFilter) {
                                    viewModel.applyFilterToBitmap(activeBmp, selectedFilter)
                                }
                                Image(
                                    bitmap = filteredBmp.asImageBitmap(),
                                    contentDescription = "Captured Scan Page",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }

                            if (state.isCropping) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Card(
                                        shape = RoundedCornerShape(20.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            CircularProgressIndicator()
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("ML Kit Auto-Cropping...", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                // Auto-crop badge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(16.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF9DD68A).copy(alpha = 0.9f))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Crop, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Auto-Cropped", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Filter Presets & Save Row
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp)
                        ) {
                            Text(
                                "CamScanner Presets",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            // Horizontal Filter scrolling selection
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChipItem(
                                    label = "Original",
                                    selected = selectedFilter == "original",
                                    onClick = { selectedFilter = "original" },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChipItem(
                                    label = "Magic Color",
                                    selected = selectedFilter == "magic",
                                    onClick = { selectedFilter = "magic" },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChipItem(
                                    label = "Grayscale",
                                    selected = selectedFilter == "grayscale",
                                    onClick = { selectedFilter = "grayscale" },
                                    modifier = Modifier.weight(1f)
                                )
                                FilterChipItem(
                                    label = "Contrast",
                                    selected = selectedFilter == "contrast",
                                    onClick = { selectedFilter = "contrast" },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    viewModel.applyFilterAndSave(selectedFilter)
                                    selectedFilter = "original"
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9DD68A)),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Keep & Append Page", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // High-Juice Save & Join Form Modal Bottom Sheet
    if (showExportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExportSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                Text(
                    text = "Generate Document",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = docName,
                    onValueChange = { docName = it },
                    label = { Text("Document Title") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Export File Format",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FormatChoiceCard(
                        title = "PDF Document",
                        subtitle = "Merged multi-page PDF scan",
                        icon = Icons.Filled.PictureAsPdf,
                        selected = exportFormat == "PDF",
                        modifier = Modifier.weight(1f),
                        onClick = { exportFormat = "PDF" }
                    )
                    FormatChoiceCard(
                        title = "Plain Text (OCR)",
                        subtitle = "Extract & merge raw text",
                        icon = Icons.Filled.Description,
                        selected = exportFormat == "TXT",
                        modifier = Modifier.weight(1f),
                        onClick = { exportFormat = "TXT" }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        viewModel.exportScans(
                            docName = docName,
                            format = exportFormat,
                            onComplete = {
                                showExportSheet = false
                                onScanComplete()
                            },
                            onError = {}
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (state.isExporting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Filled.LibraryAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Scan to Documents", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// ─── Workbench helpers ────────────────────────────────────────────────────────

@Composable
fun FilterChipItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected) Color(0xFF9DD68A).copy(alpha = 0.2f) else Color.Transparent
    val borderCol = if (selected) Color(0xFF9DD68A) else Color.Gray.copy(alpha = 0.4f)
    val textCol = if (selected) Color(0xFF9DD68A) else Color.White.copy(alpha = 0.7f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, borderCol, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textCol,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun FormatChoiceCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent
    val borderCol = if (selected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.4f)

    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, borderCol)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 12.sp
            )
        }
    }
}

@Composable
private fun CamIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    bgAlpha: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = bgAlpha))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun BoxScope.CornerAccents() {
    val color = Color(0xFF9DD68A)
    val corner = 20.dp
    val thick = 3.dp
    val pad = 32.dp

    Row(modifier = Modifier.align(Alignment.TopStart).padding(start = pad, top = 120.dp)) {
        Box(modifier = Modifier.width(thick).height(corner).background(color))
        Box(modifier = Modifier.height(thick).width(corner).background(color))
    }
    Row(modifier = Modifier.align(Alignment.TopEnd).padding(end = pad, top = 120.dp)) {
        Box(modifier = Modifier.height(thick).width(corner).background(color))
        Box(modifier = Modifier.width(thick).height(corner).background(color))
    }
    Row(modifier = Modifier.align(Alignment.BottomStart).padding(start = pad, bottom = 220.dp)) {
        Box(modifier = Modifier.width(thick).height(corner).background(color))
        Box(modifier = Modifier.height(thick).width(corner).background(color))
    }
    Row(modifier = Modifier.align(Alignment.BottomEnd).padding(end = pad, bottom = 220.dp)) {
        Box(modifier = Modifier.height(thick).width(corner).background(color))
        Box(modifier = Modifier.width(thick).height(corner).background(color))
    }
}
