package com.ceo3.docs.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
    val context = LocalContext.current
    val density = LocalDensity.current

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE2E8F0)) // desk slate background
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(pageCount) { index ->
                PdfPage(pdfRenderer = pdfRenderer, pageIndex = index)
            }
        }
    }
}

@Composable
fun PdfPage(pdfRenderer: PdfRenderer?, pageIndex: Int) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pdfRenderer, pageIndex) {
        withContext(Dispatchers.IO) {
            pdfRenderer?.let { renderer ->
                try {
                    val page = renderer.openPage(pageIndex)
                    // Render at a higher resolution (e.g., 2.5x) for better quality
                    val scale = 2.5f
                    val width = (page.width * scale).toInt()
                    val height = (page.height * scale).toInt()
                    
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    // Fill background white
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

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Page ${pageIndex + 1}",
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .shadow(6.dp, RoundedCornerShape(4.dp))
                .background(Color.White)
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.707f) // A4 ratio
                .clip(RoundedCornerShape(4.dp))
                .shadow(6.dp, RoundedCornerShape(4.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}
