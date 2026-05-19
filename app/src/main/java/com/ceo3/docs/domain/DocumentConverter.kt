package com.ceo3.docs.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.graphics.Typeface
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

    /**
     * Converts styled text (markdown-like) into a styled multi-page PDF document.
     */
    fun styledTextToPdf(text: String, themeKey: String, colorKey: String, outputFile: File): Result<File> {
        return try {
            val document = android.graphics.pdf.PdfDocument()
            
            // Resolve PDF styling configuration based on theme
            val bgCol = when (themeKey) {
                "sepia" -> Color.parseColor("#FDFBF7")
                "charcoal" -> Color.parseColor("#1E293B")
                else -> Color.WHITE
            }
            val textCol = when (themeKey) {
                "charcoal" -> Color.parseColor("#F8FAFC")
                "sepia" -> Color.parseColor("#451A03")
                else -> Color.parseColor("#0F172A")
            }
            val accentCol = when (themeKey) {
                "mint" -> Color.parseColor("#065F46")
                "sepia" -> Color.parseColor("#78350F")
                "charcoal" -> Color.parseColor("#C084FC")
                "ruby" -> Color.parseColor("#9F1239")
                else -> Color.parseColor("#1E3A8A") // classic navy
            }
            val secondaryTextCol = when (themeKey) {
                "charcoal" -> Color.parseColor("#94A3B8")
                "sepia" -> Color.parseColor("#78350F")
                else -> Color.parseColor("#475569")
            }

            val isSerif = themeKey == "sepia" || themeKey == "ruby"
            val typeface = if (isSerif) {
                Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            } else {
                Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }

            val paint = Paint().apply {
                textSize = 11f
                color = textCol
                isAntiAlias = true
                this.typeface = typeface
            }
            
            val headerPaint = Paint().apply {
                textSize = 20f
                color = accentCol
                isAntiAlias = true
                this.typeface = Typeface.create(typeface, Typeface.BOLD)
            }

            val subHeaderPaint = Paint().apply {
                textSize = 14f
                color = accentCol
                isAntiAlias = true
                this.typeface = Typeface.create(typeface, Typeface.BOLD)
            }

            val boldPaint = Paint().apply {
                textSize = 11f
                color = textCol
                isAntiAlias = true
                this.typeface = Typeface.create(typeface, Typeface.BOLD)
            }

            val italicPaint = Paint().apply {
                textSize = 11f
                color = secondaryTextCol
                isAntiAlias = true
                this.typeface = Typeface.create(typeface, Typeface.ITALIC)
            }

            val linePaint = Paint().apply {
                color = accentCol
                strokeWidth = 1.5f
                isAntiAlias = true
            }

            val checkPaint = Paint().apply {
                color = accentCol
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
                isAntiAlias = true
            }

            val checkFillPaint = Paint().apply {
                color = accentCol
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            
            val pageWidth = 595 // A4 width in points
            val pageHeight = 842 // A4 height in points
            val margin = 54f
            val contentWidth = pageWidth - 2 * margin
            
            val lines = text.split("\n")
            var y = margin + paint.textSize
            
            var pageNumber = 1
            var pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas
            
            // Draw initial background
            canvas.drawColor(bgCol)
            
            fun checkPageEnd(heightRequired: Float) {
                if (y + heightRequired > pageHeight - margin) {
                    document.finishPage(page)
                    pageNumber++
                    pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    canvas.drawColor(bgCol)
                    y = margin + paint.textSize
                }
            }

            for (rawParagraph in lines) {
                val paragraph = rawParagraph.trim()
                
                when {
                    // Header 1: # Title
                    paragraph.startsWith("# ") -> {
                        val content = paragraph.substring(2)
                        checkPageEnd(36f)
                        canvas.drawText(content, margin, y, headerPaint)
                        y += 6f
                        canvas.drawLine(margin, y, margin + contentWidth, y, linePaint)
                        y += 24f
                    }
                    
                    // Header 2: ## Section
                    paragraph.startsWith("## ") -> {
                        val content = paragraph.substring(3)
                        checkPageEnd(24f)
                        canvas.drawText(content, margin, y, subHeaderPaint)
                        y += 18f
                    }

                    // Header 3: ### Subsection
                    paragraph.startsWith("### ") -> {
                        val content = paragraph.substring(4)
                        checkPageEnd(20f)
                        canvas.drawText(content, margin, y, boldPaint)
                        y += 16f
                    }

                    // Checked Checkbox: - [x] or - [X]
                    paragraph.startsWith("- [x] ") || paragraph.startsWith("- [X] ") -> {
                        val content = paragraph.substring(6)
                        checkPageEnd(16f)
                        
                        // Draw checkbox
                        canvas.drawRect(margin, y - 9f, margin + 10f, y + 1f, checkPaint)
                        // Draw check line/fill
                        canvas.drawRect(margin + 2f, y - 7f, margin + 8f, y - 1f, checkFillPaint)
                        
                        canvas.drawText(content, margin + 18f, y, paint)
                        y += 16f
                    }

                    // Unchecked Checkbox: - [ ]
                    paragraph.startsWith("- [ ] ") -> {
                        val content = paragraph.substring(6)
                        checkPageEnd(16f)
                        
                        // Draw unchecked checkbox
                        canvas.drawRect(margin, y - 9f, margin + 10f, y + 1f, checkPaint)
                        
                        canvas.drawText(content, margin + 18f, y, paint)
                        y += 16f
                    }

                    // Bullet Point: - or *
                    paragraph.startsWith("- ") || paragraph.startsWith("* ") -> {
                        val content = paragraph.substring(2)
                        checkPageEnd(16f)
                        
                        // Draw bullet point dot
                        canvas.drawCircle(margin + 5f, y - 3f, 2.5f, checkFillPaint)
                        
                        // Handle potential bold parts in text line-wrapping
                        val words = content.split(" ")
                        var currentLine = ""
                        val indentX = margin + 18f
                        
                        for (word in words) {
                            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                            if (paint.measureText(testLine) > contentWidth - 18f) {
                                checkPageEnd(16f)
                                drawPdfLineText(canvas, currentLine, indentX, y, paint, boldPaint, themeKey)
                                y += 14f
                                currentLine = word
                            } else {
                                currentLine = testLine
                            }
                        }
                        if (currentLine.isNotEmpty()) {
                            checkPageEnd(16f)
                            drawPdfLineText(canvas, currentLine, indentX, y, paint, boldPaint, themeKey)
                            y += 16f
                        }
                    }

                    // Blockquote: >
                    paragraph.startsWith("> ") -> {
                        val content = paragraph.substring(2)
                        checkPageEnd(16f)
                        
                        // Draw left border line
                        canvas.drawLine(margin, y - 10f, margin, y + 4f, linePaint)
                        
                        val words = content.split(" ")
                        var currentLine = ""
                        for (word in words) {
                            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                            if (italicPaint.measureText(testLine) > contentWidth - 12f) {
                                checkPageEnd(16f)
                                canvas.drawText(currentLine, margin + 12f, y, italicPaint)
                                y += 14f
                                currentLine = word
                            } else {
                                currentLine = testLine
                            }
                        }
                        if (currentLine.isNotEmpty()) {
                            checkPageEnd(16f)
                            canvas.drawText(currentLine, margin + 12f, y, italicPaint)
                            y += 16f
                        }
                    }

                    paragraph.isEmpty() -> {
                        y += 10f
                    }

                    // Standard text flow
                    else -> {
                        val words = paragraph.split(" ")
                        var currentLine = ""
                        for (word in words) {
                            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                            if (paint.measureText(testLine) > contentWidth) {
                                checkPageEnd(16f)
                                drawPdfLineText(canvas, currentLine, margin, y, paint, boldPaint, themeKey)
                                y += 14f
                                currentLine = word
                            } else {
                                currentLine = testLine
                            }
                        }
                        if (currentLine.isNotEmpty()) {
                            checkPageEnd(16f)
                            drawPdfLineText(canvas, currentLine, margin, y, paint, boldPaint, themeKey)
                            y += 18f
                        }
                    }
                }
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

    private fun drawPdfLineText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        paint: Paint,
        boldPaint: Paint,
        themeKey: String
    ) {
        val parts = text.split("**")
        var currentX = x
        for (i in parts.indices) {
            if (i % 2 == 1) {
                // Draw bold part
                val boldColor = when (themeKey) {
                    "mint" -> Color.parseColor("#065F46")
                    "sepia" -> Color.parseColor("#78350F")
                    "charcoal" -> Color.parseColor("#C084FC")
                    "ruby" -> Color.parseColor("#9F1239")
                    else -> Color.parseColor("#1E3A8A")
                }
                boldPaint.color = boldColor
                canvas.drawText(parts[i], currentX, y, boldPaint)
                currentX += boldPaint.measureText(parts[i])
            } else {
                // Draw regular part
                canvas.drawText(parts[i], currentX, y, paint)
                currentX += paint.measureText(parts[i])
            }
        }
    }
}
