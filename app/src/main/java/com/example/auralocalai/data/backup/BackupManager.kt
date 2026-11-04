package com.example.auralocalai.data.backup

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Back-up of conversations, briefings, the Q&A library and flashcards as one zip file.
 * Models are not included (they are large and can be downloaded again).
 */
object BackupManager {

    private val INCLUDED = listOf("chat_sessions.json", "chat_history.json", "sessions", "briefing", "medical", "work")

    /** Writes the back-up to [target]. Returns the number of files. */
    fun export(context: Context, target: Uri): Int {
        val base = context.filesDir
        var count = 0
        context.contentResolver.openOutputStream(target)?.use { out ->
            ZipOutputStream(out).use { zip ->
                INCLUDED.map { File(base, it) }.filter { it.exists() }.forEach { root ->
                    root.walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.forEach { file ->
                        zip.putNextEntry(ZipEntry(file.relativeTo(base).path.replace(File.separatorChar, '/')))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        count++
                    }
                }
            }
        } ?: throw IllegalStateException("Cannot write the back-up file")
        return count
    }

    /** Restores a back-up made by [export]. Only known folders are written; returns the number of files. */
    fun restore(context: Context, source: Uri): Int {
        val base = context.filesDir.canonicalFile
        var count = 0
        context.contentResolver.openInputStream(source)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val allowed = INCLUDED.any { name == it || name.startsWith("$it/") }
                    val target = File(base, name).canonicalFile
                    // Protect against paths that point outside the app folder.
                    if (!entry.isDirectory && allowed && target.path.startsWith(base.path + File.separator)) {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
                        count++
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        } ?: throw IllegalStateException("Cannot read the back-up file")
        return count
    }
}
