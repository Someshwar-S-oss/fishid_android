package com.example.fishid.ml

// Pure scan decisions, kept free of Android so they run in unit tests.

/** Below this, the app says "couldn't identify" instead of offering candidates. */
const val UNSURE_FLOOR = 0.30f

/** "High match" pill when this far above the species threshold, else "Likely match". */
const val HIGH_MARGIN = 0.10f

/** Median inference above this means live scanning will lag: switch to tap mode. Tune on real budget phones. */
const val LIVE_MAX_MEDIAN_MS = 150L

/** Frames to time before deciding live vs tap. */
const val LATENCY_SAMPLES = 15

sealed interface Outcome {
    data class Match(val top: Prediction, val others: List<Prediction>) : Outcome
    data class Unsure(val candidates: List<Prediction>) : Outcome
    data object NoMatch : Outcome
}

fun decide(preds: List<Prediction>, threshold: (String) -> Float): Outcome {
    val species = preds.filter { it.speciesId != null }
    val top = species.firstOrNull() ?: return Outcome.NoMatch
    val background = preds.firstOrNull { it.speciesId == null }?.confidence ?: 0f
    return when {
        top.confidence >= threshold(top.speciesId!!) -> Outcome.Match(top, species.drop(1).take(2))
        top.confidence >= UNSURE_FLOOR && top.confidence > background -> Outcome.Unsure(species.take(3))
        else -> Outcome.NoMatch
    }
}

fun isHighMatch(confidence: Float, threshold: Float) = confidence >= threshold + HIGH_MARGIN

/** Confidence as mobile-signal bars (1–4): a scale everyone with a phone already reads. 4 = high, 3 = likely match. */
fun matchBars(confidence: Float, threshold: Float): Int = when {
    isHighMatch(confidence, threshold) -> 4
    confidence >= threshold -> 3
    confidence >= UNSURE_FLOOR -> 2
    else -> 1
}

/** null until enough samples; then true if this phone is too slow for live scanning. */
fun prefersTapMode(samplesMs: List<Long>): Boolean? =
    if (samplesMs.size < LATENCY_SAMPLES) null else samplesMs.sorted()[samplesMs.size / 2] > LIVE_MAX_MEDIAN_MS

/**
 * Live mode: averages the last [window] frames and locks once the same species has stayed
 * above its threshold for [holdMs]. Smoothing stops the result flickering between species.
 */
class LiveLock(private val window: Int = 5, private val holdMs: Long = 500, private val minFrames: Int = 3) {
    private val recent = ArrayDeque<Map<String?, Float>>()
    private var candidate: String? = null
    private var since = 0L

    /** A species is above threshold and the user should hold still. */
    var holding = false
        private set

    fun reset() {
        recent.clear()
        candidate = null
        holding = false
    }

    /** Returns the averaged predictions (best first) once locked, else null. */
    fun update(preds: List<Prediction>, nowMs: Long, threshold: (String) -> Float): List<Prediction>? {
        recent.addLast(preds.associate { it.speciesId to it.confidence })
        if (recent.size > window) recent.removeFirst()
        val avg = recent.flatMap { it.keys }.toSet()
            .map { k -> Prediction(k, recent.sumOf { (it[k] ?: 0f).toDouble() }.toFloat() / recent.size) }
            .sortedByDescending { it.confidence }
        val top = avg.firstOrNull { it.speciesId != null }
        if (top == null || recent.size < minFrames || top.confidence < threshold(top.speciesId!!)) {
            candidate = null
            holding = false
            return null
        }
        if (top.speciesId != candidate) {
            candidate = top.speciesId
            since = nowMs
        }
        holding = true
        return if (nowMs - since >= holdMs) avg else null
    }
}
