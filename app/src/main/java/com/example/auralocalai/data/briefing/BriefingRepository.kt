package com.example.auralocalai.data.briefing

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Stores the briefing configuration (topics, sources, schedule), saved briefings and the
 * last fetched news as JSON files in [baseDir] (the app's private files directory).
 */
class BriefingRepository(baseDir: File) {

    private val dir = File(baseDir, "briefing").apply { mkdirs() }
    private val configFile = File(dir, "config.json")
    private val historyFile = File(dir, "history.json")
    private val cacheFile = File(dir, "fetch_cache.json")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Synchronized
    fun loadConfig(): BriefingConfig {
        if (!configFile.exists()) return DefaultBriefing.config
        return try {
            json.decodeFromString(BriefingConfig.serializer(), configFile.readText())
        } catch (_: Exception) {
            DefaultBriefing.config
        }
    }

    @Synchronized
    fun saveConfig(config: BriefingConfig) {
        writeAtomically(configFile, json.encodeToString(BriefingConfig.serializer(), config))
    }

    @Synchronized
    fun loadHistory(): List<Briefing> {
        if (!historyFile.exists()) return emptyList()
        return try {
            json.decodeFromString(ListSerializer(Briefing.serializer()), historyFile.readText())
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Adds [briefing] at the top of the history (or replaces the one with the same id). */
    @Synchronized
    fun saveBriefing(briefing: Briefing, maxHistory: Int = MAX_HISTORY): List<Briefing> {
        val updated = (listOf(briefing) + loadHistory().filter { it.id != briefing.id }).take(maxHistory)
        writeAtomically(historyFile, json.encodeToString(ListSerializer(Briefing.serializer()), updated))
        return updated
    }

    @Synchronized
    fun deleteBriefing(id: String): List<Briefing> {
        val updated = loadHistory().filter { it.id != id }
        writeAtomically(historyFile, json.encodeToString(ListSerializer(Briefing.serializer()), updated))
        return updated
    }

    @Synchronized
    fun loadCache(): FetchCache {
        if (!cacheFile.exists()) return FetchCache()
        return try {
            json.decodeFromString(FetchCache.serializer(), cacheFile.readText())
        } catch (_: Exception) {
            FetchCache()
        }
    }

    @Synchronized
    fun saveCache(cache: FetchCache) {
        writeAtomically(cacheFile, json.encodeToString(FetchCache.serializer(), cache))
    }

    private fun writeAtomically(target: File, content: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(target)) {
            target.writeText(content)
            tmp.delete()
        }
    }

    companion object {
        const val MAX_HISTORY = 30
    }
}
