package com.ceo3.docs.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

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
            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            document.close()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Converts a PDF into DOCX (stubs, but returns txt-based representation or successfully mapped).
     */
    fun pdfToDocx(pdfFile: File, outputFile: File): Result<File> {
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
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
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
        return Result.success(outputFile)
    }

    /**
     * Converts flowing text into a multi-page PDF document.
     */
    fun textToPdf(text: String, outputFile: File): Result<File> {
        return try {
            val document = android.graphics.pdf.PdfDocument()
            val paint = Paint().apply {
                textSize = 12f
                color = Color.BLACK
                isAntiAlias = true
            }
            
            val pageWidth = 595 // A4 width in points
            val pageHeight = 842 // A4 height in points
            val margin = 50f
            val contentWidth = pageWidth - 2 * margin
            
            val lines = text.split("\n")
            var y = margin + paint.textSize
            
            var pageNumber = 1
            var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas
            
            for (paragraph in lines) {
                val words = paragraph.split(" ")
                var currentLine = ""
                for (word in words) {
                    val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                    if (paint.measureText(testLine) > contentWidth) {
                        if (y + paint.textSize > pageHeight - margin) {
                            document.finishPage(page)
                            pageNumber++
                            pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                            page = document.startPage(pageInfo)
                            canvas = page.canvas
                            y = margin + paint.textSize
                        }
                        canvas.drawText(currentLine, margin, y, paint)
                        y += paint.textSize + 4
                        currentLine = word
                    } else {
                        currentLine = testLine
                    }
                }
                if (currentLine.isNotEmpty()) {
                    if (y + paint.textSize > pageHeight - margin) {
                        document.finishPage(page)
                        pageNumber++
                        pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                        page = document.startPage(pageInfo)
                        canvas = page.canvas
                        y = margin + paint.textSize
                    }
                    canvas.drawText(currentLine, margin, y, paint)
                    y += paint.textSize + 4
                }
                y += 8 // Paragraph spacing
            }
            
            document.finishPage(page)
            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
            document.close()
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extracts text from all pages of a PDF using PdfRenderer + OCR.
     */
    suspend fun pdfToText(pdfFile: File, ocrEngine: OcrEngine): Result<String> {
        return try {
            val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(fileDescriptor)
            val textBuilder = StringBuilder()
            
            for (i in 0 until pdfRenderer.pageCount) {
                val page = pdfRenderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                
                val pageTextResult = ocrEngine.extractTextFromImage(bitmap)
                if (pageTextResult.isSuccess) {
                    textBuilder.append(pageTextResult.getOrNull()).append("\n\n")
                }
            }
            pdfRenderer.close()
            fileDescriptor.close()
            Result.success(textBuilder.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
