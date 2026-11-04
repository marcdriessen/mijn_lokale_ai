package com.example.auralocalai.data.work

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.example.auralocalai.data.translate.awaitResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads PDF, Word (.docx), OpenDocument (.odt), RTF, HTML and plain text files from the phone. */
object DocumentReader {

    /** MIME types offered in the file picker. */
    val PICKER_TYPES = arrayOf(
        "application/pdf",
        DocxIO.MIME,
        "application/msword",
        "application/vnd.oasis.opendocument.text",
        "application/rtf",
        "text/*"
    )

    sealed class Result {
        data class Text(val text: String, val kind: DocumentText.Kind, val name: String?) : Result()
        object OldWordFormat : Result()
        object Empty : Result()
    }

    suspend fun read(context: Context, uri: Uri, maxPdfPages: Int = 20, onPdfPage: (Int, Int) -> Unit = { _, _ -> }): Result {
        val name = withContext(Dispatchers.IO) { displayName(context, uri) }
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: return Result.Empty
        val kind = DocumentText.detect(bytes, name)
        val text = when (kind) {
            DocumentText.Kind.OLD_DOC -> return Result.OldWordFormat
            DocumentText.Kind.PDF -> ocrPdf(context, uri, maxPdfPages, onPdfPage)
            else -> withContext(Dispatchers.Default) { DocumentText.extract(bytes, kind) }
        }
        return if (text.isNullOrBlank()) Result.Empty else Result.Text(text.trim(), kind, name)
    }

    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment

    /** PdfRenderer draws each page; ML Kit reads the text on the phone (works for scanned and digital PDFs). */
    private suspend fun ocrPdf(context: Context, uri: Uri, maxPages: Int, onPage: (Int, Int) -> Unit): String {
        val descriptor = withContext(Dispatchers.IO) { context.contentResolver.openFileDescriptor(uri, "r") } ?: return ""
        val renderer = PdfRenderer(descriptor)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val builder = StringBuilder()
        try {
            val pages = minOf(renderer.pageCount, maxPages)
            for (i in 0 until pages) {
                onPage(i + 1, pages)
                val bitmap = withContext(Dispatchers.Default) {
                    renderer.openPage(i).use { page ->
                        val scale = if (page.width > 1200) 1 else 2
                        Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888).also {
                            Canvas(it).drawColor(Color.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }
                try {
                    builder.append(recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitResult().text).append("\n\n")
                } finally {
                    bitmap.recycle()
                }
            }
        } finally {
            runCatching { recognizer.close() }
            runCatching { renderer.close() }
            runCatching { descriptor.close() }
        }
        return builder.toString()
    }
}
