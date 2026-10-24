package com.example.fishid

import com.example.fishid.ml.BACKGROUND
import com.example.fishid.ml.LiveLock
import com.example.fishid.ml.ModelInfo
import com.example.fishid.ml.Outcome
import com.example.fishid.ml.Prediction
import com.example.fishid.ml.aggregate
import com.example.fishid.ml.decide
import com.example.fishid.ml.FRAME_FRACTION
import com.example.fishid.ml.frameWidthShare
import com.example.fishid.ml.FRAME_SHIFT
import com.example.fishid.ml.frameCenterShare
import com.example.fishid.ml.prefersTapMode
import com.example.fishid.ml.softmax
import com.example.fishid.ml.validateModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanLogicTest {
    private val t = { _: String -> 0.7f }
    private fun p(id: String?, c: Float) = Prediction(id, c)

    @Test fun aggregateSumsLabelsPerSpeciesAndFoldsBackground() {
        val r = aggregate(floatArrayOf(0.1f, 0.3f, 0.4f, 0.2f), listOf(BACKGROUND, "a", "a", "b"))
        assertEquals(p("a", 0.7f).speciesId, r[0].speciesId)
        assertEquals(0.7f, r[0].confidence, 1e-6f)
        assertEquals(listOf("a", "b", null), r.map { it.speciesId })
    }

    @Test fun softmaxIsAProbabilityDistribution() {
        val s = softmax(floatArrayOf(1f, 2f, 3f, 1000f))
        assertEquals(1f, s.sum(), 1e-5f)
        assertTrue(s[3] > 0.99f)
    }

    @Test fun decideMatchUnsureNoMatch() {
        assertTrue(decide(listOf(p("a", 0.9f), p("b", 0.05f), p(null, 0.05f)), t) is Outcome.Match)
        val unsure = decide(listOf(p("a", 0.5f), p("b", 0.3f), p(null, 0.2f)), t)
        assertEquals(listOf("a", "b"), (unsure as Outcome.Unsure).candidates.map { it.speciesId })
        assertEquals(Outcome.NoMatch, decide(listOf(p(null, 0.6f), p("a", 0.4f)), t)) // background wins
        assertEquals(Outcome.NoMatch, decide(listOf(p("a", 0.2f), p(null, 0.1f)), t))   // below floor
        assertEquals(Outcome.NoMatch, decide(listOf(p(null, 1f)), t))
    }

    @Test fun liveLockNeedsAStableSpeciesForTheHoldTime() {
        val lock = LiveLock(window = 5, holdMs = 500, minFrames = 3)
        val good = listOf(p("a", 0.9f), p(null, 0.1f))
        assertNull(lock.update(good, 0, t))
        assertNull(lock.update(good, 100, t))
        assertNull(lock.update(good, 200, t)) // 3rd frame: candidate starts now
        assertTrue(lock.holding)
        assertNull(lock.update(good, 600, t))
        assertNotNull(lock.update(good, 700, t))
        // Switching species restarts the hold.
        lock.reset()
        repeat(3) { lock.update(good, it * 100L, t) }
        val other = listOf(p("b", 0.95f), p("a", 0.05f))
        repeat(5) { assertNull(lock.update(other, 300L + it * 50, t)) }
    }

    @Test fun tapModeWhenMedianLatencyIsHigh() {
        assertNull(prefersTapMode(List(14) { 500L }))
        assertEquals(false, prefersTapMode(List(15) { 40L } + List(4) { 900L }))
        assertEquals(true, prefersTapMode(List(15) { 220L }))
    }

    @Test fun modelValidation() {
        val info = ModelInfo(modelVersion = 1, minDataVersion = 2, labels = listOf("a", BACKGROUND, "b"))
        assertNull(validateModel(info, 3, setOf("a", "b"), appVersion = 1, dataVersion = 2))
        assertNotNull(validateModel(info, 4, setOf("a", "b"), 1, 2))   // size mismatch
        assertNotNull(validateModel(info, 3, setOf("a"), 1, 2))        // label not in data
        assertNotNull(validateModel(info, 3, setOf("a", "b"), 1, 1))   // data too old
        assertNotNull(validateModel(info.copy(minAppVersion = 9), 3, setOf("a", "b"), 1, 2))
    }

    @Test fun frameCropFollowsWhatThePreviewShows() {
        // 1080x2400 phone, 720x1280 upright camera: preview shows 0.8 of the camera width.
        assertEquals(FRAME_FRACTION * 0.8f, frameWidthShare(1080, 2400, 720, 1280), 1e-4f)
        // View wider than the camera (tablet): camera width fully visible, top/bottom cropped instead.
        assertEquals(FRAME_FRACTION, frameWidthShare(1600, 2000, 720, 1280), 1e-4f)
        assertEquals(FRAME_FRACTION, frameWidthShare(0, 0, 720, 1280), 1e-4f) // not laid out yet
        // Phone: the camera's full height is on screen, so the shift carries over as-is.
        assertEquals(0.5f - FRAME_SHIFT, frameCenterShare(1080, 2400, 720, 1280), 1e-4f)
        // Tablet: the camera's top and bottom are off-screen, so the same screen shift is a smaller share of it.
        assertEquals(0.5f - FRAME_SHIFT * 2000f / (1600f / 720 * 1280), frameCenterShare(1600, 2000, 720, 1280), 1e-4f)
    }
}
