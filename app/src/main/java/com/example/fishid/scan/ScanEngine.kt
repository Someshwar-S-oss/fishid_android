package com.example.fishid.scan

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.example.fishid.data.Candidate
import com.example.fishid.data.Catalog
import com.example.fishid.data.History
import com.example.fishid.data.ScanRecord
import com.example.fishid.ml.Classifier
import com.example.fishid.ml.FRAME_FRACTION
import com.example.fishid.ml.FRAME_SHIFT
import com.example.fishid.ml.LiveLock
import com.example.fishid.ml.Outcome
import com.example.fishid.ml.Prediction
import com.example.fishid.ml.decide
import com.example.fishid.ml.prefersTapMode
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

enum class ScanMode { AUTO, LIVE, TAP }

fun Context.scanModeSetting(): ScanMode =
    runCatching { ScanMode.valueOf(getSharedPreferences("fishid", Context.MODE_PRIVATE).getString("scan_mode", null)!!) }.getOrDefault(ScanMode.AUTO)

fun Context.setScanModeSetting(mode: ScanMode) =
    getSharedPreferences("fishid", Context.MODE_PRIVATE).edit().putString("scan_mode", mode.name).apply()

/**
 * Camera frame → classifier → scan state. Frames arrive on the analyzer thread; every state change is
 * posted to the main thread and reported through [onChange]. Gallery images go through [identifyBitmap] on the
 * same single-thread executor, so the (non-thread-safe) classifier is only touched by one thread.
 */
class ScanEngine(private val context: Context, val analyzerExecutor: Executor) : ImageAnalysis.Analyzer {
    private val main = ContextCompat.getMainExecutor(context)
    private val lock = LiveLock()
    private val latencies = mutableListOf<Long>()
    private val tapRequested = AtomicBoolean(false)
    @Volatile private var paused = false
    @Volatile var classifier: Classifier? = null

    /** Share of each camera frame's width the model sees: the on-screen frame (set by the camera view). */
    @Volatile var cropWidth = FRAME_FRACTION

    /** Vertical centre of the crop as a share of the frame height (the on-screen frame sits above centre). */
    @Volatile var cropCenterY = 0.5f - FRAME_SHIFT

    /** Called on the main thread whenever [live], [holding], [identifying] or [switchedToTap] change. */
    var onChange: () -> Unit = {}

    /** Called on the main thread with every finished scan (match, unsure or none), already saved to history. */
    var onResult: (ScanRecord) -> Unit = {}

    /** Live unless the setting or the measured speed says tap. */
    var live = initialLive()
        private set(v) { field = v; onChange() }
    var holding = false
        private set(v) { if (field != v) { field = v; onChange() } }
    var identifying = false
        private set(v) { field = v; onChange() }
    var switchedToTap = false // show the one-time notice
        private set(v) { field = v; onChange() }
    private var showingResult = false

    private fun initialLive(): Boolean = when (context.scanModeSetting()) {
        ScanMode.LIVE -> true
        ScanMode.TAP -> false
        ScanMode.AUTO -> !slowDevice()
    }

    private fun slowDevice(): Boolean {
        val am = context.getSystemService(ActivityManager::class.java)
        val hot = Build.VERSION.SDK_INT >= 29 &&
            context.getSystemService(PowerManager::class.java).currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        return am.isLowRamDevice || hot
    }

    fun shutter() {
        if (identifying || showingResult) return
        identifying = true
        tapRequested.set(true)
    }

    fun scanAgain() {
        showingResult = false
        lock.reset()
        holding = false
        paused = false
    }

    override fun analyze(image: ImageProxy) {
        image.use {
            val c = classifier ?: return
            if (paused) return
            val tap = tapRequested.getAndSet(false)
            if (!live && !tap) return
            val frame = it.toBitmap().upright(it.imageInfo.rotationDegrees).bracketArea(cropWidth, cropCenterY)
            val t0 = SystemClock.elapsedRealtime()
            val preds = c.classify(frame)
            val now = SystemClock.elapsedRealtime()
            if (tap) {
                paused = true
                finish(frame, preds)
                return
            }
            measure(now - t0)
            val locked = lock.update(preds, now, ::threshold)
            val hold = lock.holding
            if (locked != null) {
                paused = true
                finish(frame, locked)
            } else {
                main.execute { holding = hold }
            }
        }
    }

    fun dismissTapNotice() { switchedToTap = false }

    private fun measure(ms: Long) {
        if (context.scanModeSetting() != ScanMode.AUTO || latencies.size > 15) return
        latencies += ms
        if (prefersTapMode(latencies) == true) main.execute {
            live = false
            holding = false
            switchedToTap = true
        }
    }

    fun threshold(id: String): Float =
        Catalog.get(context).species.firstOrNull { it.id == id }?.confidenceThreshold ?: classifier?.info?.defaultThreshold ?: 0.7f

    /** For gallery photos: run on [analyzerExecutor]. */
    fun identifyBitmap(bitmap: Bitmap) {
        val c = classifier ?: return
        paused = true
        finish(bitmap, c.classify(bitmap))
    }

    private fun finish(frame: Bitmap, preds: List<Prediction>) {
        val outcome = decide(preds, ::threshold)
        val candidates = preds.filter { it.speciesId != null }.take(3).map { Candidate(it.speciesId!!, it.confidence) }
        val record = ScanRecord(
            id = UUID.randomUUID().toString(),
            timeMs = System.currentTimeMillis(),
            photo = History.savePhoto(context, frame),
            candidates = candidates,
            outcome = when (outcome) {
                is Outcome.Match -> ScanRecord.Outcome.MATCH
                is Outcome.Unsure -> ScanRecord.Outcome.UNSURE
                Outcome.NoMatch -> ScanRecord.Outcome.NONE
            },
            speciesId = (outcome as? Outcome.Match)?.top?.speciesId,
        )
        History.add(context, record)
        main.execute {
            identifying = false
            holding = false
            if (record.outcome == ScanRecord.Outcome.MATCH) showingResult = true
            else scanAgain() // camera keeps running underneath the next screen
            onResult(record)
        }
    }
}

/**
 * Width / height of the on-screen scan frame. Training photos were mostly 4:3 landscape, squashed to the model's
 * square input; cropping the camera to the same shape keeps fish proportions the same as in training.
 */
const val BRACKET_ASPECT = 4f / 3f

/** The [BRACKET_ASPECT] box [widthShare] of this frame's width wide, centred at [centerY] of its height (see frameWidthShare). */
fun Bitmap.bracketArea(widthShare: Float, centerY: Float): Bitmap {
    val w = (width * widthShare).toInt().coerceIn(1, width)
    val h = minOf(height, (w / BRACKET_ASPECT).toInt())
    val top = (height * centerY - h / 2f).toInt().coerceIn(0, height - h)
    return Bitmap.createBitmap(this, (width - w) / 2, top, w, h)
}

/** The whole frame, rotated upright. Cropping (if any) is the model's call: see InputSpec.resize. */
fun Bitmap.upright(rotationDegrees: Int): Bitmap =
    if (rotationDegrees == 0) this
    else Bitmap.createBitmap(this, 0, 0, width, height, Matrix().apply { postRotate(rotationDegrees.toFloat()) }, true)

/** Decodes a picked photo at most [maxPx] on its long side, honouring EXIF rotation. */
fun loadPicked(context: Context, uri: Uri, maxPx: Int = 1280): Bitmap? = runCatching {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
    val bmp = resolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }!!
    val degrees = resolver.openInputStream(uri)!!.use { ExifInterface(it).rotationDegrees }
    if (degrees == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
}.getOrNull()
