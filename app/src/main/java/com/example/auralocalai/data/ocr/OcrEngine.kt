package com.example.auralocalai.data.ocr

import android.content.Context
import android.graphics.Bitmap
import com.example.auralocalai.AppLocale
import com.example.auralocalai.data.translate.awaitResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Text recognition for photos and scanned PDFs, fully on the phone.
 * - Latin script (Dutch, English, German, Polish, …): Google ML Kit.
 * - Cyrillic (Bulgarian): Tesseract with the Bulgarian + English language data (downloaded once,
 *   about 13 MB, then it works offline). ML Kit cannot read Cyrillic.
 */
object OcrEngine {

    enum class Script { LATIN, CYRILLIC }

    private const val PREFS = "ocr_prefs"
    private const val KEY_SCRIPT = "script"
    private const val LANGUAGES = "bul+eng"
    private val DATA_FILES = mapOf(
        "bul.traineddata" to "https://raw.githubusercontent.com/tesseract-ocr/tessdata_best/main/bul.traineddata",
        "eng.traineddata" to "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main/eng.traineddata"
    )
    private val mutex = Mutex()

    /** Chosen script; by default Cyrillic when the app is in Bulgarian, otherwise Latin. */
    fun script(context: Context): Script {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SCRIPT, null)
        return when (saved) {
            Script.CYRILLIC.name -> Script.CYRILLIC
            Script.LATIN.name -> Script.LATIN
            else -> if (AppLocale.effectiveLanguage(context) == "bg") Script.CYRILLIC else Script.LATIN
        }
    }

    fun setScript(context: Context, script: Script) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SCRIPT, script.name).apply()
    }

    private fun baseDir(context: Context) = File(context.filesDir, "tesseract")
    private fun dataDir(context: Context) = File(baseDir(context), "tessdata")

    fun isCyrillicReady(context: Context): Boolean =
        DATA_FILES.keys.all { File(dataDir(context), it).let { f -> f.exists() && f.length() > 100_000 } }

    /** Downloads the Bulgarian/English language data when it is not there yet. Returns false without internet. */
    suspend fun ensureCyrillicData(context: Context, onProgress: (Int) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        if (isCyrillicReady(context)) return@withContext true
        val dir = dataDir(context).apply { mkdirs() }
        val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
        try {
            DATA_FILES.entries.forEachIndexed { index, (name, url) ->
                val target = File(dir, name)
                if (target.exists() && target.length() > 100_000) return@forEachIndexed
                val tmp = File(dir, "$name.part")
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code}")
                    val body = response.body ?: throw IllegalStateException("empty")
                    val total = body.contentLength()
                    body.byteStream().use { input ->
                        tmp.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                                done += n
                                if (total > 0) onProgress(((index * 100) + (done * 100 / total).toInt()) / DATA_FILES.size)
                            }
                        }
                    }
                }
                if (!tmp.renameTo(target)) throw IllegalStateException("rename")
            }
            true
        } catch (_: Exception) {
            dir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
            false
        }
    }

    /**
     * Recognises the text in [bitmap] with the chosen script. Throws [CyrillicDataMissing] when the
     * Bulgarian language data could not be downloaded.
     */
    suspend fun recognize(context: Context, bitmap: Bitmap, script: Script = script(context)): String =
        when (script) {
            Script.LATIN -> recognizeLatin(bitmap)
            Script.CYRILLIC -> {
                if (!ensureCyrillicData(context)) throw CyrillicDataMissing()
                recognizeTesseract(context, bitmap)
            }
        }

    private suspend fun recognizeLatin(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).awaitResult().text
        } finally {
            runCatching { recognizer.close() }
        }
    }

    private suspend fun recognizeTesseract(context: Context, bitmap: Bitmap): String = mutex.withLock {
        withContext(Dispatchers.Default) {
            val tess = TessBaseAPI()
            try {
                if (!tess.init(baseDir(context).absolutePath, LANGUAGES)) throw IllegalStateException("Tesseract init failed")
                val input = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
                tess.setImage(input)
                tess.getUTF8Text().orEmpty().trim()
            } finally {
                tess.recycle()
            }
        }
    }

    /** Loads a picture from the gallery or a share as a bitmap (scaled down to keep memory low). */
    suspend fun loadBitmap(context: Context, uri: android.net.Uri, maxSide: Int = 3000): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                    val largest = maxOf(info.size.width, info.size.height)
                    if (largest > maxSide) {
                        val scale = maxSide.toFloat() / largest
                        decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                    }
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
            }
        }.getOrNull()
    }

    class CyrillicDataMissing : Exception("Cyrillic text recognition data could not be downloaded")
}
