package com.example.fishid.data

import android.content.Context
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import java.io.FileNotFoundException
import java.io.InputStream

// Shapes of assets/data/*.json (see docs/PLAN.md §5). species.json holds language-neutral facts;
// text.<lang>.json holds translatable prose keyed by species id.

@Serializable
data class SpeciesFile(val dataVersion: Int, val updated: String, val species: List<Species>)

@Serializable
data class Species(
    val id: String,
    val scientificName: String,
    val scientificNameTa: String? = null,
    val localNameTa: String? = null,
    val localNameRoman: String? = null,
    val images: List<String> = emptyList(),
    val meristics: Meristics? = null,
    val habitat: List<String> = emptyList(),
    val feeding: String? = null,
    val vertical: String? = null,
    val depthM: List<Int>? = null,
    val iucn: String? = null,
    val nutritionG: Nutrition? = null,
    val priceInrPerKg: Int? = null,
    val priceUpdated: String? = null,
    val confidenceThreshold: Float? = null,
    val source: String? = null,
)

@Serializable
data class Meristics(val dorsalSpines: Int, val dorsalRays: Int, val analSpines: Int, val analRays: Int)

@Serializable
data class Nutrition(val protein: Double, val fat: Double, val moisture: Double, val ash: Double, val carbohydrate: Double)

@Serializable
data class SpeciesText(val commonName: String? = null, val distribution: String? = null, val fieldMarks: List<String>? = null)

/** A species with its text resolved for one language; missing Tamil fields fall back to English. */
data class FishEntry(
    val species: Species,
    val commonName: String,
    val distribution: String?,
    val fieldMarks: List<String>,
    private val searchText: String,
) {
    val id get() = species.id

    fun matches(query: String): Boolean {
        val q = query.trim().lowercase()
        return q.isEmpty() || searchText.contains(q)
    }
}

class Catalog(val dataVersion: Int, val updated: String, val species: List<Species>, private val text: Map<String, Map<String, SpeciesText>>) {
    private val byId = species.associateBy { it.id }

    fun entries(lang: String): List<FishEntry> = species.map { entry(it, lang) }

    fun entry(id: String, lang: String): FishEntry? = byId[id]?.let { entry(it, lang) }

    private fun entry(s: Species, lang: String): FishEntry {
        val en = text["en"]?.get(s.id)
        val local = text[lang]?.get(s.id)
        // Search matches every name in every script, whatever the UI language.
        val search = listOfNotNull(
            en?.commonName, text["ta"]?.get(s.id)?.commonName, s.localNameTa, s.localNameRoman, s.scientificName, s.scientificNameTa,
        ).joinToString("\n").lowercase()
        return FishEntry(
            species = s,
            commonName = local?.commonName ?: en?.commonName ?: s.scientificName,
            distribution = local?.distribution ?: en?.distribution,
            fieldMarks = local?.fieldMarks ?: en?.fieldMarks.orEmpty(),
            searchText = search,
        )
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        val json = Json { ignoreUnknownKeys = true; namingStrategy = JsonNamingStrategy.SnakeCase }

        const val IMAGE_DIR = "data/images"

        /** [open] returns a stream for a path under data/, or throws FileNotFoundException. */
        fun load(open: (String) -> InputStream): Catalog {
            val file = open("data/species.json").use { json.decodeFromString<SpeciesFile>(it.reader().readText()) }
            val text = listOf("en", "ta").associateWith { lang ->
                try {
                    open("data/text.$lang.json").use { json.decodeFromString<Map<String, SpeciesText>>(it.reader().readText()) }
                } catch (_: FileNotFoundException) {
                    emptyMap()
                }
            }
            return Catalog(file.dataVersion, file.updated, file.species, text)
        }

        @Volatile private var instance: Catalog? = null

        // ponytail: bundled assets only; OTA data (phase 5) swaps the opener to filesDir when a newer version is active.
        fun get(context: Context): Catalog = instance ?: synchronized(this) {
            instance ?: load { context.applicationContext.assets.open(it) }.also { instance = it }
        }
    }
}
