package com.ceo3.docs.domain

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class OcrEngine {

    /**
     * Extracts text from the given bitmap using ML Kit Text Recognition.
     */
    suspend fun extractTextFromImage(bitmap: Bitmap): Result<String> {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val visionText = recognizer.process(image).await()
            Result.success(visionText.text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
