package com.example.fishid.data

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

// Scan history and wrong-ID reports, stored on this phone only (docs/PLAN.md S6, S7).

@Serializable
data class Candidate(val speciesId: String, val confidence: Float)

@Serializable
data class ScanRecord(
    val id: String,
    val timeMs: Long,
    val photo: String,              // absolute path in filesDir/scans
    val candidates: List<Candidate>, // model's best species, best first
    val outcome: Outcome,
    val speciesId: String? = null,  // identified, or picked by the user on the compare screen
    val pickedByUser: Boolean = false,
) {
    @Serializable
    enum class Outcome { MATCH, UNSURE, NONE }

    val confidence get() = candidates.firstOrNull { it.speciesId == speciesId }?.confidence
}

object History {
    private const val MAX = 200
    private val lock = Any()
    private val items = MutableStateFlow<List<ScanRecord>?>(null)

    private fun file(context: Context) = File(context.filesDir, "history.json")

    /** Newest first. Blocking on first call (reads one small file). */
    fun flow(context: Context): StateFlow<List<ScanRecord>?> {
        if (items.value == null) synchronized(lock) {
            if (items.value == null) items.value = runCatching {
                Catalog.json.decodeFromString<List<ScanRecord>>(file(context).readText())
            }.getOrDefault(emptyList())
        }
        return items.asStateFlow()
    }

    fun get(context: Context, id: String) = flow(context).value?.firstOrNull { it.id == id }

    fun add(context: Context, record: ScanRecord) = edit(context) { list ->
        val all = listOf(record) + list
        all.drop(MAX).forEach { File(it.photo).delete() } // oldest fall off
        all.take(MAX)
    }

    fun update(context: Context, id: String, change: (ScanRecord) -> ScanRecord) = edit(context) { list ->
        list.map { if (it.id == id) change(it) else it }
    }

    fun delete(context: Context, id: String) = edit(context) { list ->
        list.filter { it.id != id }.also { list.firstOrNull { r -> r.id == id }?.let { r -> File(r.photo).delete() } }
    }

    fun clear(context: Context) = edit(context) { list ->
        list.forEach { File(it.photo).delete() }
        emptyList()
    }

    private fun edit(context: Context, change: (List<ScanRecord>) -> List<ScanRecord>) {
        flow(context)
        synchronized(lock) {
            val next = change(items.value.orEmpty())
            val tmp = File(context.filesDir, "history.json.tmp")
            tmp.writeText(Catalog.json.encodeToString(next))
            tmp.renameTo(file(context)) // replace in one step so a crash mid-write can't corrupt history
            items.value = next
        }
    }

    /** Saves a scan photo as JPEG; returns its path. Call off the main thread. */
    fun savePhoto(context: Context, bitmap: Bitmap): String {
        val dir = File(context.filesDir, "scans").apply { mkdirs() }
        val f = File(dir, "${UUID.randomUUID()}.jpg")
        f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return f.absolutePath
    }
}

@Serializable
data class Report(
    val id: String,
    val timeMs: Long,
    val appVersion: Long,
    val modelVersion: Int?,
    val dataVersion: Int,
    val guessSpeciesId: String?,
    val guessConfidence: Float?,
    val answerSpeciesId: String?, // null = "I don't know"
    val place: String,
    val note: String,
)

/**
 * Each report is a folder (photo.jpg + report.json) under filesDir/reports.
 * That folder is the upload queue: a future uploader sends and deletes them; nothing is sent yet.
 */
object Reports {
    fun save(context: Context, report: Report, photo: String) {
        val dir = File(context.filesDir, "reports/${report.id}").apply { mkdirs() }
        File(photo).copyTo(File(dir, "photo.jpg"), overwrite = true) // a copy, so deleting history keeps the report intact
        File(dir, "report.json").writeText(Catalog.json.encodeToString(report))
    }

    fun pendingCount(context: Context) = File(context.filesDir, "reports").listFiles()?.size ?: 0
}
