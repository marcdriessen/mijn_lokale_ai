package com.example.auralocalai.data.briefing

/**
 * Ready-made topics for "Personalise your feed", with keywords in Dutch, English and Bulgarian
 * so headlines in all three languages are found.
 */
object TopicPresets {

    data class Preset(val id: String, val names: Map<String, String>, val keywords: List<String>) {
        fun name(language: String): String = names[language] ?: names.getValue("en")
        fun toTopic(language: String) = BriefingTopic(id, name(language), keywords)
    }

    private fun n(nl: String, en: String, bg: String) = mapOf("nl" to nl, "en" to en, "bg" to bg)

    val ALL = listOf(
        Preset("preset_tech", n("Technologie en Wetenschap", "Technology & Science", "Технологии и наука"),
            listOf("technologie", "wetenschap", "AI", "kunstmatige intelligentie", "onderzoek", "technology", "science", "artificial intelligence", "research", "технологии", "наука", "изкуствен интелект")),
        Preset("preset_business", n("Zakelijk", "Business", "Бизнес"),
            listOf("economie", "bedrijf", "beurs", "aandelen", "inflatie", "economy", "business", "markets", "stocks", "inflation", "икономика", "бизнес", "борса")),
        Preset("preset_culture", n("Kunst en Cultuur", "Arts & Culture", "Изкуство и култура"),
            listOf("kunst", "cultuur", "museum", "theater", "boek", "muziek", "art", "culture", "museum", "music", "изкуство", "култура", "музика")),
        Preset("preset_sport", n("Sport", "Sport", "Спорт"),
            listOf("sport", "voetbal", "Formule 1", "F1", "wielrennen", "tennis", "schaatsen", "football", "soccer", "Olympics", "спорт", "футбол")),
        Preset("preset_entertainment", n("Amusement", "Entertainment", "Развлечения"),
            listOf("film", "serie", "Netflix", "game", "games", "acteur", "zanger", "movie", "series", "actor", "entertainment", "филм", "сериал")),
        Preset("preset_health", n("Gezondheid", "Health", "Здраве"),
            listOf("gezondheid", "zorg", "ziekenhuis", "medisch", "health", "hospital", "medical", "healthcare", "здраве", "болница")),
        Preset("preset_ggz", n("Ggz", "Mental health", "Психично здраве"),
            listOf("ggz", "psychiatrie", "psychisch", "depressie", "mental health", "psychiatry", "depression", "психично здраве", "психиатрия")),
        Preset("preset_addiction", n("Verslavingszorg", "Addiction care", "Зависимости"),
            listOf("verslaving", "verslavingszorg", "alcohol", "drugs", "middelengebruik", "addiction", "substance use", "overdose", "зависимост", "наркотици")),
        Preset("preset_nl", n("Nederland", "Netherlands", "Нидерландия"),
            listOf("Nederland", "Nederlandse", "kabinet", "Tweede Kamer", "Netherlands", "Dutch", "Нидерландия")),
        Preset("preset_bg", n("Bulgarije", "Bulgaria", "България"),
            listOf("Bulgarije", "Bulgaars", "Sofia", "Bulgaria", "Bulgarian", "България", "София")),
        Preset("preset_world", n("Wereldpolitiek", "World politics", "Световна политика"),
            listOf("verkiezingen", "president", "oorlog", "EU", "NAVO", "election", "war", "NATO", "Ukraine", "избори", "война")),
        Preset("preset_climate", n("Klimaat", "Climate", "Климат"),
            listOf("klimaat", "duurzaam", "energie", "climate", "renewable", "emissions", "климат", "енергия"))
    )

    /**
     * New topic list for a profile: existing topics keep their keywords and get [enabledIds];
     * chosen presets that are not in the profile yet are added.
     */
    fun apply(current: List<BriefingTopic>, enabledIds: Set<String>, language: String): List<BriefingTopic> {
        val existing = current.map { it.copy(enabled = it.id in enabledIds) }
        val added = ALL.filter { it.id in enabledIds && current.none { t -> t.id == it.id } }.map { it.toTopic(language) }
        return existing + added
    }
}
