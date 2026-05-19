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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
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
    var showOcrConfirm by remember { mutableStateOf(false) }
    var showToolsSheet by remember { mutableStateOf(false) }

    val hostState = remember { SnackbarHostState() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState) },
        bottomBar = {
            // Elegant Toolbar for styling controls inside Edit mode
            AnimatedVisibility(
                visible = state.viewMode == ViewMode.TEXT_EDIT,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolbarButton(
                        icon = Icons.Filled.FormatBold,
                        label = "Bold",
                        active = state.isBoldEnabled,
                        tint = currentAccent.color,
                        onClick = { viewModel.toggleBold() }
                    )
                    ToolbarButton(
                        icon = Icons.Filled.FormatItalic,
                        label = "Italic",
                        active = state.isItalicEnabled,
                        tint = currentAccent.color,
                        onClick = { viewModel.toggleItalic() }
                    )
                    ToolbarButton(
                        icon = Icons.Filled.FormatListBulleted,
                        label = "List",
                        active = false,
                        tint = currentAccent.color,
                        onClick = {
                            viewModel.onContentChanged(
                                TextFieldValue(state.content.text + "\n• ")
                            )
                        }
                    )
                    ToolbarButton(
                        icon = Icons.Filled.Palette,
                        label = "Palette",
                        active = false,
                        tint = currentAccent.color,
                        onClick = { showCustomizer = true }
                    )
                }
            }
        },
        floatingActionButton = {
            // High-Juice Dynamic FAB that triggers Tools Sheet expansion
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

                // ── Segmented Top Three Primary Buttons ────────────────────
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
                        label = "Edit Text",
                        icon = Icons.Filled.Edit,
                        selected = state.viewMode == ViewMode.TEXT_EDIT,
                        accentColor = currentAccent.color,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (state.pdfPages.isNotEmpty() && state.content.text.isEmpty()) {
                                showOcrConfirm = true
                            } else {
                                viewModel.setViewMode(ViewMode.TEXT_EDIT)
                            }
                        }
                    )
                    SegmentedButton(
                        label = "Draw / Sign",
                        icon = Icons.Filled.Gesture,
                        selected = state.viewMode == ViewMode.PDF_SIGN,
                        accentColor = currentAccent.color,
                        modifier = Modifier.weight(1f),
                        onClick = { viewModel.setViewMode(ViewMode.PDF_SIGN) }
                    )
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
                    when (state.viewMode) {
                        ViewMode.TEXT_EDIT -> {
                            val textWeight = if (state.isBoldEnabled) FontWeight.Bold else FontWeight.Normal
                            val textStyle = if (state.isItalicEnabled) FontStyle.Italic else FontStyle.Normal

                            BasicTextField(
                                value = state.content,
                                onValueChange = viewModel::onContentChanged,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                textStyle = TextStyle(
                                    fontSize = 16.sp,
                                    lineHeight = 26.sp,
                                    color = currentTheme.text,
                                    fontWeight = textWeight,
                                    fontStyle = textStyle
                                ),
                                decorationBox = { inner ->
                                    if (state.content.text.isEmpty()) {
                                        Text(
                                            "Start typing or convert document...",
                                            color = currentTheme.text.copy(alpha = 0.5f),
                                            fontSize = 16.sp
                                        )
                                    }
                                    inner()
                                }
                            )
                        }
                        ViewMode.PDF_VIEW, ViewMode.PDF_SIGN -> {
                            if (state.pdfPages.isEmpty()) {
                                // Draw on normal empty/text documents
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(32.dp)
                                    ) {
                                        var currentStrokePoints = remember { mutableStateListOf<Offset>() }

                                        Text(
                                            "Digital Signature Board",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = currentTheme.text
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "Use your finger to write, sign, or draw below.",
                                            fontSize = 13.sp,
                                            color = currentTheme.text.copy(alpha = 0.6f),
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(24.dp))

                                        // Hand-drawing canvas block
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(280.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color.White)
                                                .border(2.dp, currentAccent.color.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                                .pointerInput(Unit) {
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
                                                                    pageIndex = 0,
                                                                    stroke = DrawingStroke(
                                                                        points = currentStrokePoints.toList(),
                                                                        color = currentAccent.color,
                                                                        strokeWidth = 6f
                                                                    )
                                                                )
                                                                currentStrokePoints.clear()
                                                            }
                                                        }
                                                    )
                                                }
                                        ) {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                // Saved strokes
                                                val savedStrokes = state.pageStrokes[0] ?: emptyList()
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
                                                // Active drag stroke
                                                if (currentStrokePoints.size > 1) {
                                                    for (i in 0 until currentStrokePoints.size - 1) {
                                                        drawLine(
                                                            color = currentAccent.color,
                                                            start = currentStrokePoints[i],
                                                            end = currentStrokePoints[i + 1],
                                                            strokeWidth = 6f,
                                                            cap = StrokeCap.Round
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))
                                        if (state.pageStrokes[0]?.isNotEmpty() == true) {
                                            Button(
                                                onClick = { viewModel.clearDrawings(0) },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Wipe Drawing")
                                            }
                                        }
                                    }
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

            // ── OCR Overlay Loader ──────────────────────────────────────────
            if (state.isOcrRunning) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = currentAccent.color)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Running OCR Engine...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
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
                        title = "OCR & Edit",
                        subtitle = "Extract text from PDF",
                        icon = Icons.Filled.DocumentScanner,
                        color = currentAccent.color,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showToolsSheet = false
                            if (state.pdfPages.isNotEmpty()) {
                                showOcrConfirm = true
                            }
                        }
                    )
                    ToolHubCard(
                        title = "Compress",
                        subtitle = "Reduce PDF size",
                        icon = Icons.Filled.Compress,
                        color = currentAccent.color,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showToolsSheet = false
                            viewModel.compressPdf()
                        }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
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
                            } else {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, state.content.text)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Text"))
                            }
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

    // OCR conversion confirm dialog
    if (showOcrConfirm) {
        AlertDialog(
            onDismissRequest = { showOcrConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Convert PDF to Editable Text?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "We will extract all text from this PDF file using smart OCR, letting you format and edit it fully.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showOcrConfirm = false
                        viewModel.runOcrOnPdf()
                    }
                ) {
                    Text("Convert", color = currentAccent.color, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOcrConfirm = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
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

@Composable
private fun ToolbarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) tint.copy(alpha = 0.2f) else Color.Transparent)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) tint else MaterialTheme.colorScheme.onSurface
        )
    }
}

// ─── Updated Viewmodel with Pin/Bookmark Support ──────────────────────────────

class EditorViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState

    private var currentDocumentId: String? = null
    private var documentType: String = "TXT"
    private val converter = com.ceo3.docs.domain.DocumentConverter(application)
    private val ocrEngine = com.ceo3.docs.domain.OcrEngine()

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

            val ext = if (documentType == "PDF") "pdf" else "txt"
            val file = File(getApplication<android.app.Application>().filesDir, "doc_${id}.$ext")
            val title = entity?.title ?: "Document $id"
            val theme = entity?.accentTheme ?: "classic"
            val color = entity?.accentColor ?: "blue"
            val bookmarked = entity?.isPinned ?: false

            if (!file.exists()) {
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        title = title,
                        viewMode = ViewMode.TEXT_EDIT,
                        selectedTheme = theme,
                        selectedColorName = color,
                        isBookmarked = bookmarked
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
                        isBookmarked = bookmarked
                    )
                }
            } else {
                val text = file.readText()
                withContext(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        title = title,
                        content = TextFieldValue(text),
                        viewMode = ViewMode.TEXT_EDIT,
                        selectedTheme = theme,
                        selectedColorName = color,
                        isBookmarked = bookmarked
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
                mimeType.startsWith("image/") -> "IMG"
                mimeType.contains("word") || uriString.endsWith(".docx") -> "DOCX"
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
                            pdfFile = cacheFile
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

    fun onContentChanged(value: TextFieldValue) {
        _uiState.value = _uiState.value.copy(content = value)
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

    fun toggleBold() {
        _uiState.value = _uiState.value.copy(isBoldEnabled = !_uiState.value.isBoldEnabled)
    }

    fun toggleItalic() {
        _uiState.value = _uiState.value.copy(isItalicEnabled = !_uiState.value.isItalicEnabled)
    }

    fun runOcrOnPdf() {
        val file = _uiState.value.pdfFile ?: return
        _uiState.value = _uiState.value.copy(isOcrRunning = true)
        viewModelScope.launch(Dispatchers.IO) {
            val textResult = converter.pdfToText(file, ocrEngine)
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    isOcrRunning = false,
                    content = TextFieldValue(textResult.getOrDefault("")),
                    viewMode = ViewMode.TEXT_EDIT
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

            if (_uiState.value.viewMode == ViewMode.TEXT_EDIT) {
                val ext = if (documentType == "PDF") "pdf" else "txt"
                val file = File(getApplication<android.app.Application>().filesDir, "doc_${id}.$ext")
                if (documentType == "PDF") {
                    converter.textToPdf(_uiState.value.content.text, file)
                } else {
                    file.writeText(_uiState.value.content.text)
                }

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
            } else {
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
}

data class EditorUiState(
    val title: String = "Untitled Document",
    val content: TextFieldValue = TextFieldValue(""),
    val isBoldEnabled: Boolean = false,
    val isItalicEnabled: Boolean = false,
    val viewMode: ViewMode = ViewMode.TEXT_EDIT,
    val pdfPages: List<Bitmap> = emptyList(),
    val pdfFile: File? = null,
    val pageStrokes: Map<Int, List<DrawingStroke>> = emptyMap(),
    val isOcrRunning: Boolean = false,
    val isCompressing: Boolean = false,
    val isBookmarked: Boolean = false,
    val selectedTheme: String = "classic",
    val selectedColorName: String = "blue"
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
    TEXT_EDIT,
    PDF_VIEW,
    PDF_SIGN
}
