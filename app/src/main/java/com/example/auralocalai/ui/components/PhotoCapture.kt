package com.example.auralocalai.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.example.auralocalai.R
import com.example.auralocalai.data.ocr.OcrEngine
import kotlinx.coroutines.launch
import java.io.File

/**
 * Takes a full-resolution photo with the camera app (the small preview image is too blurry for
 * text recognition). The photo is stored in the app's cache and deleted right after reading.
 * Returns a function that opens the camera.
 */
@Composable
fun rememberPhotoCapture(onBitmap: (Bitmap) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnBitmap by rememberUpdatedState(onBitmap)
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingPath ?: return@rememberLauncherForActivityResult
        pendingPath = null
        val file = File(path)
        if (!ok) {
            file.delete()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val bitmap = OcrEngine.loadBitmap(context, Uri.fromFile(file))
            file.delete()
            if (bitmap != null) currentOnBitmap(bitmap)
            else Toast.makeText(context, context.getString(R.string.could_not_open_file), Toast.LENGTH_SHORT).show()
        }
    }
    return {
        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
        val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
        pendingPath = file.absolutePath
        try {
            launcher.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
        } catch (e: Exception) {
            pendingPath = null
            Toast.makeText(context, context.getString(R.string.speech_error_generic), Toast.LENGTH_SHORT).show()
        }
    }
}
