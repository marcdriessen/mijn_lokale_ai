package com.example.auralocalai.data.medical

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Stores the Q&A configuration, the offline library, Brave usage and recent answers as JSON files. */
class MedicalRepository(baseDir: File) {

    private val dir = File(baseDir, "medical").apply { mkdirs() }
    private val configFile = File(dir, "config.json")
    private val libraryFile = File(dir, "library.json")
    private val usageFile = File(dir, "brave_usage.json")
    private val historyFile = File(dir, "history.json")

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun loadConfig(): MedicalConfig = read(configFile) { json.decodeFromString(MedicalConfig.serializer(), it) } ?: MedicalConfig()

    @Synchronized
    fun saveConfig(config: MedicalConfig) = write(configFile, json.encodeToString(MedicalConfig.serializer(), config))

    @Synchronized
    fun loadLibrary(): List<LibraryDocument> =
        read(libraryFile) { json.decodeFromString(ListSerializer(LibraryDocument.serializer()), it) } ?: emptyList()

    @Synchronized
    fun saveLibrary(documents: List<LibraryDocument>) =
        write(libraryFile, json.encodeToString(ListSerializer(LibraryDocument.serializer()), documents))

    @Synchronized
    fun loadHistory(): List<MedicalAnswer> =
        read(historyFile) { json.decodeFromString(ListSerializer(MedicalAnswer.serializer()), it) } ?: emptyList()

    @Synchronized
    fun saveAnswer(answer: MedicalAnswer, max: Int = 30): List<MedicalAnswer> {
        val updated = (listOf(answer) + loadHistory().filter { it.id != answer.id }).take(max)
        write(historyFile, json.encodeToString(ListSerializer(MedicalAnswer.serializer()), updated))
        return updated
    }

    @Synchronized
    fun deleteAnswer(id: String): List<MedicalAnswer> {
        val updated = loadHistory().filter { it.id != id }
        write(historyFile, json.encodeToString(ListSerializer(MedicalAnswer.serializer()), updated))
        return updated
    }

    /** Brave searches used this calendar month. */
    @Synchronized
    fun braveUsageThisMonth(now: Long = System.currentTimeMillis()): Int {
        val usage = read(usageFile) { json.decodeFromString(BraveUsage.serializer(), it) } ?: BraveUsage()
        return if (usage.month == monthKey(now)) usage.count else 0
    }

    @Synchronized
    fun recordBraveUse(now: Long = System.currentTimeMillis()): Int {
        val count = braveUsageThisMonth(now) + 1
        write(usageFile, json.encodeToString(BraveUsage.serializer(), BraveUsage(monthKey(now), count)))
        return count
    }

    private fun monthKey(now: Long): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(now))

    private fun <T> read(file: File, parse: (String) -> T): T? {
        if (!file.exists()) return null
        return try {
            parse(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    private fun write(target: File, content: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            target.writeText(content)
            tmp.delete()
        }
    }
}
