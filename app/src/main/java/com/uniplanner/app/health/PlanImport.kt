package com.uniplanner.app.health

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Reads the text of a nutritionist's plan from a photo or a PDF, on the phone itself. */
object PlanImport {
    private const val MAX_PAGES = 4

    suspend fun readText(ctx: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val type = ctx.contentResolver.getType(uri).orEmpty()
            val images = if (type == "application/pdf") pdfPages(ctx, uri).map { InputImage.fromBitmap(it, 0) }
            else listOf(InputImage.fromFilePath(ctx, uri))
            val pages = mutableListOf<String>()
            for (image in images) {
                val result = recognizer.process(image).await()
                // Line by line, top to bottom, so a time and its meal stay on one line when they share a row.
                val lines = result.textBlocks.flatMap { it.lines }
                    .sortedWith(compareBy({ (it.boundingBox?.centerY() ?: 0) / 24 }, { it.boundingBox?.left ?: 0 }))
                val rows = mutableListOf<MutableList<String>>()
                var lastRow = Int.MIN_VALUE
                for (line in lines) {
                    val row = (line.boundingBox?.centerY() ?: 0) / 24
                    if (row != lastRow) rows += mutableListOf<String>()
                    rows.last() += line.text
                    lastRow = row
                }
                pages += rows.joinToString("\n") { it.joinToString(" ") }
            }
            pages.joinToString("\n")
        } finally {
            recognizer.close()
        }
    }

    private fun pdfPages(ctx: Context, uri: Uri): List<Bitmap> {
        val fd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: return emptyList()
        return fd.use { descriptor ->
            PdfRenderer(descriptor).use { pdf ->
                (0 until minOf(pdf.pageCount, MAX_PAGES)).map { i ->
                    pdf.openPage(i).use { page ->
                        val width = 1600
                        val height = width * page.height / page.width
                        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(Color.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
            }
        }
    }
}
