package com.ceo3.docs.ui.screens

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.util.concurrent.Executor
import android.util.Log

class ScannerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState

    fun onImageCaptured(imageUri: String) {
        val updatedPages = _uiState.value.capturedPages + imageUri
        _uiState.value = _uiState.value.copy(capturedPages = updatedPages)
    }

    fun removePage(imageUri: String) {
        val updatedPages = _uiState.value.capturedPages.filter { it != imageUri }
        _uiState.value = _uiState.value.copy(capturedPages = updatedPages)
    }
}

data class ScannerUiState(
    val capturedPages: List<String> = emptyList()
)

@Composable
fun ScannerScreen(
    onScanComplete: () -> Unit,
    onCancel: () -> Unit,
    viewModel: ScannerViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val executor = ContextCompat.getMainExecutor(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    imageCapture = ImageCapture.Builder().build()
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner, cameraSelector, preview, imageCapture
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, executor)
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Guide overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .border(2.dp, Color.Green.copy(alpha = 0.5f))
        )

        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
            }
            if (state.capturedPages.isNotEmpty()) {
                IconButton(onClick = onScanComplete) {
                    Icon(Icons.Filled.Done, contentDescription = "Done", tint = Color.White)
                }
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.capturedPages.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.capturedPages) { page ->
                        // Thumbnail mockup
                        Box(
                            modifier = Modifier
                                .size(60.dp, 80.dp)
                                .background(Color.Gray)
                        )
                    }
                }
            }

            // Capture button
            Button(
                onClick = {
                    val file = File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                    val executor = ContextCompat.getMainExecutor(context)
                    imageCapture?.takePicture(
                        outputOptions,
                        executor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                viewModel.onImageCaptured(file.absolutePath)
                            }
                            override fun onError(exception: ImageCaptureException) {
                                Log.e("Scanner", "Capture failed", exception)
                            }
                        }
                    )
                },
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                // Inner circle
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, Color.Black, CircleShape)
                )
            }
        }
    }
}
