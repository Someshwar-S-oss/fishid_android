package com.example.fishid.bridge

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Size
import android.view.HapticFeedbackConstants
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.LifecycleOwner
import com.example.fishid.data.Catalog
import com.example.fishid.data.History
import com.example.fishid.data.Report
import com.example.fishid.data.Reports
import com.example.fishid.data.ScanRecord
import com.example.fishid.data.primaryName
import com.example.fishid.data.secondaryName
import com.example.fishid.ml.Classifier
import com.example.fishid.ml.HIGH_MARGIN
import com.example.fishid.ml.frameCenterShare
import com.example.fishid.ml.frameWidthShare
import com.example.fishid.scan.ScanEngine
import com.example.fishid.scan.loadPicked
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// The only seam between the React Native UI and the Kotlin model/data layer. Data crosses as JSON strings
// (snake_case, same shape as assets/data and history.json) so JS types mirror the files one-to-one.

/** Writes every field, defaults included, so JS never has to guess a missing key. */
private val bridgeJson = Json(Catalog.json) { encodeDefaults = true }

class FishIdPackage : ReactPackage {
    @Deprecated("Legacy package API; works through the new architecture's interop layer")
    override fun createNativeModules(reactContext: ReactApplicationContext) = listOf(FishIdModule(reactContext))
    override fun createViewManagers(reactContext: ReactApplicationContext) = listOf(FishCameraManager())
}

private fun Context.prefs() = getSharedPreferences("fishid", Context.MODE_PRIVATE)

class FishIdModule(private val ctx: ReactApplicationContext) : ReactContextBaseJavaModule(ctx) {
    override fun getName() = "FishID"

    // Gallery photos when no camera is open. One thread, like the camera analyzer, so the classifier stays single-threaded.
    private val galleryExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    private val galleryEngine: ScanEngine by lazy { newEngine(ctx, galleryExecutor) }

    override fun invalidate() {
        if (FishCameraView.events === this) FishCameraView.events = null
        super.invalidate()
    }

    init {
        FishCameraView.events = this
    }

    fun emitState(e: ScanEngine, hasFlash: Boolean) = ctx.emitDeviceEvent("FishID.scanState", Arguments.createMap().apply {
        putBoolean("live", e.live)
        putBoolean("holding", e.holding)
        putBoolean("identifying", e.identifying)
        putBoolean("switchedToTap", e.switchedToTap)
        putBoolean("hasFlash", hasFlash)
    })

    fun emitResult(r: ScanRecord) = ctx.emitDeviceEvent("FishID.scanResult", bridgeJson.encodeToString(r))

    fun emitCameraError() = ctx.emitDeviceEvent("FishID.cameraError", null)

    // ---------- Catalog ----------

    /** Species for [lang] matching [query] (every name in every script), with text resolved and names ordered for that language. */
    @ReactMethod
    fun catalog(lang: String, query: String, promise: Promise) = promise.json {
        val catalog = Catalog.get(ctx)
        val tamil = lang == "ta"
        val defaultThreshold = runCatching { runBlocking { Classifier.get(ctx) }.info.defaultThreshold }.getOrDefault(0.7f)
        buildJsonObject {
            put("data_version", catalog.dataVersion)
            put("updated", catalog.updated)
            put("entries", buildJsonArray {
                catalog.entries(lang).filter { it.matches(query) }.forEach { e ->
                    add(buildJsonObject {
                        bridgeJson.encodeToJsonElement(e.species).jsonObject.forEach { (k, v) -> put(k, v) }
                        put("common_name", e.commonName)
                        put("primary_name", e.primaryName(tamil))
                        put("secondary_name", e.secondaryName(tamil))
                        put("distribution", e.distribution)
                        put("field_marks", buildJsonArray { e.fieldMarks.forEach { add(JsonPrimitive(it)) } })
                        put("threshold", e.species.confidenceThreshold ?: defaultThreshold)
                        put("image_uris", buildJsonArray { e.species.images.forEach { add(JsonPrimitive("asset:///${Catalog.IMAGE_DIR}/$it")) } })
                    })
                }
            })
        }.toString()
    }

    @ReactMethod
    fun versions(promise: Promise) = promise.json {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        buildJsonObject {
            put("app_version", pi.versionName)
            put("data_version", Catalog.get(ctx).dataVersion)
            put("model_version", runCatching { runBlocking { Classifier.get(ctx) }.info.modelVersion }.getOrNull())
            put("high_margin", HIGH_MARGIN)
        }.toString()
    }

    // ---------- History ----------

    @ReactMethod
    fun history(promise: Promise) = promise.json { bridgeJson.encodeToString(History.flow(ctx).value.orEmpty()) }

    @ReactMethod
    fun deleteScan(id: String, promise: Promise) = promise.json { History.delete(ctx, id); "null" }

    @ReactMethod
    fun clearHistory(promise: Promise) = promise.json { History.clear(ctx); "null" }

    /** Compare screen: the user says this candidate is their fish. */
    @ReactMethod
    fun pickCandidate(scanId: String, speciesId: String, promise: Promise) = promise.json {
        History.update(ctx, scanId) { it.copy(speciesId = speciesId, pickedByUser = true) }
        "null"
    }

    // ---------- Reports ----------

    /** [answerSpeciesId] null = "I don't know". Saved to the local upload queue; nothing is sent. */
    @ReactMethod
    fun saveReport(scanId: String, answerSpeciesId: String?, place: String, note: String, promise: Promise) = promise.json {
        val record = History.get(ctx, scanId) ?: error("scan $scanId not found")
        val guessId = record.speciesId ?: record.candidates.firstOrNull()?.speciesId
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        Reports.save(
            ctx,
            Report(
                id = UUID.randomUUID().toString(),
                timeMs = System.currentTimeMillis(),
                appVersion = PackageInfoCompat.getLongVersionCode(pi),
                modelVersion = runCatching { runBlocking { Classifier.get(ctx) }.info.modelVersion }.getOrNull(),
                dataVersion = Catalog.get(ctx).dataVersion,
                guessSpeciesId = guessId,
                guessConfidence = record.candidates.firstOrNull { it.speciesId == guessId }?.confidence,
                answerSpeciesId = answerSpeciesId,
                place = place.trim(),
                note = note.trim(),
            ),
            record.photo,
        )
        "null"
    }

    // ---------- Settings ----------

    /** Every stored preference as a string ("theme", "scan_mode", "onboarded", ...). */
    @ReactMethod
    fun prefs(promise: Promise) {
        val map = Arguments.createMap()
        ctx.prefs().all.forEach { (k, v) -> if (v != null) map.putString(k, v.toString()) }
        promise.resolve(map)
    }

    @ReactMethod
    fun setPref(key: String, value: String) = ctx.prefs().edit().putString(key, value).apply()

    /** "en" / "ta": the app's own language if one was chosen, else the phone's. */
    @ReactMethod
    fun language(promise: Promise) {
        val tag = AppCompatDelegate.getApplicationLocales()[0]?.language ?: Locale.getDefault().language
        promise.resolve(if (tag == "ta") "ta" else "en")
    }

    /** Persists the choice and lists FishID under the phone's per-app language settings (Android 13+). */
    @ReactMethod
    fun setLanguage(tag: String) = ui {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    // ---------- Scanning ----------

    @ReactMethod
    fun shutter() = ui { FishCameraView.active?.engine?.shutter() }

    @ReactMethod
    fun scanAgain() = ui { FishCameraView.active?.engine?.scanAgain() }

    @ReactMethod
    fun setTorch(on: Boolean) = ui { FishCameraView.active?.camera?.cameraControl?.enableTorch(on) }

    @ReactMethod
    fun dismissTapNotice() = ui { FishCameraView.active?.engine?.dismissTapNotice() }

    /**
     * Opens the system photo picker and identifies the chosen photo; the result arrives as a FishID.scanResult event.
     * Resolves true once a photo was picked (show a spinner), false if cancelled.
     */
    @ReactMethod
    fun pickPhoto(promise: Promise) = ui {
        val activity = reactApplicationContext.currentActivity as? ComponentActivity ?: return@ui promise.resolve(false)
        lateinit var launcher: ActivityResultLauncher<PickVisualMediaRequest>
        launcher = activity.activityResultRegistry.register("fishid_pick_photo", ActivityResultContracts.PickVisualMedia()) { uri ->
            launcher.unregister()
            promise.resolve(uri != null)
            if (uri == null) return@register
            // Same thread as the open camera's analyzer, so the shared classifier is never used from two threads.
            val engine = FishCameraView.active?.engine ?: galleryEngine
            engine.analyzerExecutor.execute { loadPicked(ctx, uri)?.let(engine::identifyBitmap) }
        }
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    // Async @ReactMethods must return void: runOnUiThread returns a Boolean, so wrap it.
    private fun ui(block: () -> Unit) {
        UiThreadUtil.runOnUiThread(block)
    }

    private fun Promise.json(block: () -> String) = Thread {
        runCatching(block).onSuccess(::resolve).onFailure { reject("FISHID", it.message, it) }
    }.start()

    companion object {
        /** An engine whose classifier loads first on its own thread, reporting to JS through the live module. */
        fun newEngine(context: Context, executor: ExecutorService) = ScanEngine(context, executor).also { e ->
            executor.execute { e.classifier = runCatching { runBlocking { Classifier.get(context) } }.getOrNull() }
            e.onResult = { FishCameraView.events?.emitResult(it) }
        }
    }
}

/** Full-bleed CameraX viewfinder running [ScanEngine] on every frame while it is on screen. */
@SuppressLint("ViewConstructor")
class FishCameraView(private val ctx: ThemedReactContext) : FrameLayout(ctx) {
    private val preview = PreviewView(ctx).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
        // TextureView: a SurfaceView punches through the window and goes black inside RN's animated, translucent screens.
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
    }
    private var executor: ExecutorService? = null
    var engine: ScanEngine? = null
        private set
    var camera: Camera? = null
        private set
    private var cameraSize: Size? = null // analysis frame size, upright

    init {
        addView(preview, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val exec = Executors.newSingleThreadExecutor().also { executor = it }
        val e = FishIdModule.newEngine(ctx, exec).also { engine = it }
        e.onChange = { events?.emitState(e, camera?.cameraInfo?.hasFlashUnit() == true) }
        e.onResult = { r ->
            if (r.outcome == ScanRecord.Outcome.MATCH) {
                performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
            }
            events?.emitResult(r)
        }
        active = this
        val owner = ctx.currentActivity as? LifecycleOwner ?: run { events?.emitCameraError(); return }
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            if (engine !== e) return@addListener // detached before the camera was ready
            runCatching {
                val provider = future.get()
                val p = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { it.setAnalyzer(exec, e) }
                provider.unbindAll()
                camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, p, analysis)
                analysis.resolutionInfo?.let { info ->
                    val r = info.resolution
                    cameraSize = if (info.rotationDegrees % 180 == 0) r else Size(r.height, r.width)
                }
                updateCrop()
                e.onChange()
            }.onFailure { events?.emitCameraError() }
        }, ContextCompat.getMainExecutor(ctx))
        e.onChange()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        runCatching { ProcessCameraProvider.getInstance(ctx).get().unbindAll() }
        executor?.shutdown()
        if (active === this) active = null
        engine = null
        camera = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateCrop()
    }

    /** Keeps the model's crop equal to the frame drawn on screen. */
    private fun updateCrop() {
        val c = cameraSize ?: return
        engine?.cropWidth = frameWidthShare(width, height, c.width, c.height)
        engine?.cropCenterY = frameCenterShare(width, height, c.width, c.height)
    }

    // React Native lays out its own views only; native children (the PreviewView's surface) need an explicit pass.
    override fun requestLayout() {
        super.requestLayout()
        post {
            measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
            layout(left, top, right, bottom)
        }
    }

    companion object {
        @Volatile var active: FishCameraView? = null
        @Volatile var events: FishIdModule? = null
    }
}

class FishCameraManager : SimpleViewManager<FishCameraView>() {
    override fun getName() = "FishCameraView"

    /** No props: the view scans while mounted and stops when unmounted. */
    override fun createViewInstance(context: ThemedReactContext) = FishCameraView(context)
}
