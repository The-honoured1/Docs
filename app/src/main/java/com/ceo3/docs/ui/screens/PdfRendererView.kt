package com.ceo3.docs.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PdfRendererView(file: File) {
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    // Tools state
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isHighlightMode by remember { mutableStateOf(false) }
    
    // Store bookmarked pages (set of page indices)
    var bookmarkedPages by remember { mutableStateOf(setOf<Int>()) }

    LaunchedEffect(file) {
        try {
            withContext(Dispatchers.IO) {
                if (!file.exists()) {
                    error = "File does not exist."
                    return@withContext
                }
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                pdfRenderer = PdfRenderer(fd)
                pageCount = pdfRenderer?.pageCount ?: 0
            }
        } catch (e: Exception) {
            error = e.message
        }
    }

    DisposableEffect(file) {
        onDispose {
            pdfRenderer?.close()
        }
    }

    if (error != null) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Failed to load PDF: $error", color = MaterialTheme.colorScheme.error)
        }
    } else if (pageCount == 0) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            val listState = rememberLazyListState()
            
            // The zoomable/pannable container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFE2E8F0)) // desk slate background
                    .pointerInput(isHighlightMode) {
                        if (!isHighlightMode) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                val maxOffsetX = (scale - 1) * size.width / 2
                                val maxOffsetY = (scale - 1) * size.height / 2
                                offset = Offset(
                                    x = (offset.x + pan.x * scale).coerceIn(-maxOffsetX, maxOffsetX),
                                    y = (offset.y + pan.y * scale).coerceIn(-maxOffsetY, maxOffsetY)
                                )
                            }
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    userScrollEnabled = !isHighlightMode // Disable list scroll when highlighting
                ) {
                    items(pageCount) { index ->
                        PdfPage(
                            pdfRenderer = pdfRenderer, 
                            pageIndex = index,
                            isHighlightMode = isHighlightMode,
                            isBookmarked = bookmarkedPages.contains(index)
                        )
                    }
                }
            }

            // Tools Overlay UI
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .shadow(8.dp, RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Zoom Out
                IconButton(onClick = { 
                    scale = (scale - 0.5f).coerceIn(1f, 5f)
                    if (scale == 1f) offset = Offset.Zero
                }) {
                    Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom Out")
                }
                
                // Zoom In
                IconButton(onClick = { 
                    scale = (scale + 0.5f).coerceIn(1f, 5f) 
                }) {
                    Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom In")
                }
                
                // Reset Zoom
                if (scale > 1f) {
                    IconButton(onClick = { 
                        scale = 1f
                        offset = Offset.Zero
                    }) {
                        Icon(Icons.Filled.SearchOff, contentDescription = "Reset Zoom")
                    }
                }

                // Divider
                Box(modifier = Modifier.height(24.dp).width(1.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)))

                // Highlight Toggle
                IconButton(
                    onClick = { isHighlightMode = !isHighlightMode },
                    modifier = Modifier.background(
                        if (isHighlightMode) Color(0xFFFFEB3B).copy(alpha = 0.3f) else Color.Transparent,
                        CircleShape
                    )
                ) {
                    Icon(
                        Icons.Filled.Edit, 
                        contentDescription = "Highlight",
                        tint = if (isHighlightMode) Color(0xFFFBC02D) else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Bookmark Toggle
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                val currentPage = visibleItems.firstOrNull { it.offset >= 0 }?.index ?: 0
                val isCurrentBookmarked = bookmarkedPages.contains(currentPage)
                
                IconButton(onClick = { 
                    bookmarkedPages = if (isCurrentBookmarked) {
                        bookmarkedPages - currentPage
                    } else {
                        bookmarkedPages + currentPage
                    }
                }) {
                    Icon(
                        if (isCurrentBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = "Bookmark",
                        tint = if (isCurrentBookmarked) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// Representing a drawn stroke for highlighting
data class HighlightStroke(val path: List<Offset>)

@Composable
fun PdfPage(pdfRenderer: PdfRenderer?, pageIndex: Int, isHighlightMode: Boolean, isBookmarked: Boolean) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // Strokes for this specific page
    var strokes by remember { mutableStateOf(listOf<HighlightStroke>()) }
    var currentStroke by remember { mutableStateOf<List<Offset>?>(null) }

    LaunchedEffect(pdfRenderer, pageIndex) {
        withContext(Dispatchers.IO) {
            pdfRenderer?.let { renderer ->
                try {
                    val page = renderer.openPage(pageIndex)
                    val scale = 2.5f
                    val width = (page.width * scale).toInt()
                    val height = (page.height * scale).toInt()
                    
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmap = bmp
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .shadow(6.dp, RoundedCornerShape(4.dp))
            .background(Color.White)
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxWidth()
            )
            
            // Highlighter Canvas overlay
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(isHighlightMode) {
                        if (isHighlightMode) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentStroke = listOf(offset)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentStroke = currentStroke?.plus(change.position)
                                },
                                onDragEnd = {
                                    currentStroke?.let { strokes = strokes + HighlightStroke(it) }
                                    currentStroke = null
                                },
                                onDragCancel = {
                                    currentStroke = null
                                }
                            )
                        }
                    }
            ) {
                val drawStroke = { pathList: List<Offset> ->
                    if (pathList.size > 1) {
                        for (i in 0 until pathList.size - 1) {
                            drawLine(
                                color = Color(0xFFFFEB3B).copy(alpha = 0.5f), // Transparent Yellow
                                start = pathList[i],
                                end = pathList[i + 1],
                                strokeWidth = 30f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
                
                strokes.forEach { drawStroke(it.path) }
                currentStroke?.let { drawStroke(it) }
            }

            // Bookmark Indicator (Top Right Corner)
            if (isBookmarked) {
                Icon(
                    Icons.Filled.Bookmark,
                    contentDescription = "Bookmarked",
                    tint = Color(0xFFE53935),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(32.dp)
                )
            }

        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.707f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}
