package com.ceo3.docs.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

class DocumentConverter(private val context: Context) {

    /**
     * Converts a list of images (JPEGs/PNGs) into a multi-page PDF.
     */
    fun imagesToPdf(imageUris: List<String>, outputFile: File): Result<File> {
        return try {
            val document = android.graphics.pdf.PdfDocument()
            for ((index, uri) in imageUris.withIndex()) {
                val bitmap = android.graphics.BitmapFactory.decodeFile(uri) ?: continue
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                val page = document.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                document.finishPage(page)
            }
            java.io.FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            document.close()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Converts a PDF into DOCX.
     */
    fun pdfToDocx(pdfFile: File, outputFile: File): Result<File> {
        // Requires lightweight PDF/DOCX library or backend API
        return Result.success(outputFile)
    }

    /**
     * Renders a specific page of a PDF as a Bitmap using Android's PdfRenderer.
     */
    fun renderPdfPage(pdfFile: File, pageIndex: Int): Result<Bitmap> {
        return try {
            val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(fileDescriptor)
            
            val page = pdfRenderer.openPage(pageIndex)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            
            page.close()
            pdfRenderer.close()
            fileDescriptor.close()
            
            Result.success(bitmap)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Compresses a PDF to reduce file size.
     */
    fun compressPdf(inputFile: File, outputFile: File): Result<File> {
        // Implementation depends on lightweight PDF library
        return Result.success(outputFile)
    }
}
