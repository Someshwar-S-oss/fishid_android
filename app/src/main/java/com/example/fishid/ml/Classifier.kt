package com.example.fishid.ml

import android.content.Context
import android.graphics.Bitmap
import androidx.core.content.pm.PackageInfoCompat
import com.example.fishid.data.Catalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp

// The model package contract (docs/PLAN.md §4): model.tflite + model.json.
// Input size and dtype come from the tensor; only normalisation and labels live in the JSON.

const val BACKGROUND = "__background__"

@Serializable
data class ModelInfo(
    val modelVersion: Int,
    val minAppVersion: Int = 1,
    val minDataVersion: Int = 0,
    val input: InputSpec = InputSpec(),
    val output: String = "softmax", // or "logits"
    val defaultThreshold: Float = 0.7f,
    val labels: List<String>,
)

@Serializable
data class InputSpec(
    val mean: List<Float> = listOf(0f, 0f, 0f),
    val std: List<Float> = listOf(1f, 1f, 1f),
    val range: String = "0-1",
    /** "squash": whole photo resized to the input size (aspect not kept). "center_crop": centre square first. */
    val resize: String = "squash",
)

/** One species' probability. [speciesId] null = background / "not a known fish". */
data class Prediction(val speciesId: String?, val confidence: Float)

/**
 * Halves with bilinear filtering until close, then one final resize: each halving averages 2x2 pixels,
 * which approximates the anti-aliased resize used in training (PIL). A single big bilinear step aliases
 * (it samples only 2x2 source pixels per output pixel) and shifts predictions.
 */
fun downscale(src: Bitmap, w: Int, h: Int): Bitmap {
    var b = src
    while (b.width >= w * 2 && b.height >= h * 2) {
        val next = Bitmap.createScaledBitmap(b, b.width / 2, b.height / 2, true)
        if (b !== src) b.recycle()
        b = next
    }
    if (b.width == w && b.height == h) return b
    return Bitmap.createScaledBitmap(b, w, h, true).also { if (b !== src) b.recycle() }
}

/** Returns null if the package can be used, else why not. Any failure keeps the previous model. */
fun validateModel(info: ModelInfo, outputSize: Int, speciesIds: Set<String>, appVersion: Long, dataVersion: Int): String? = when {
    info.labels.size != outputSize -> "labels (${info.labels.size}) != model outputs ($outputSize)"
    info.minAppVersion > appVersion -> "needs app version ${info.minAppVersion}"
    info.minDataVersion > dataVersion -> "needs fish data version ${info.minDataVersion}"
    else -> info.labels.filter { it != BACKGROUND && it !in speciesIds }.distinct().takeIf { it.isNotEmpty() }
        ?.let { "labels missing from species data: ${it.take(5)}" }
}

fun softmax(x: FloatArray): FloatArray {
    val max = x.max()
    val e = FloatArray(x.size) { exp(x[it] - max) }
    val sum = e.sum()
    return FloatArray(e.size) { e[it] / sum }
}

/**
 * Sums probabilities per species (several labels may map to one species, e.g. sexes or life stages)
 * and returns them best first; background is folded into one null-id entry.
 */
fun aggregate(probs: FloatArray, labels: List<String>): List<Prediction> {
    val sums = LinkedHashMap<String?, Float>()
    probs.forEachIndexed { i, p ->
        val id = labels[i].takeIf { it != BACKGROUND }
        sums[id] = (sums[id] ?: 0f) + p
    }
    return sums.map { Prediction(it.key, it.value) }.sortedByDescending { it.confidence }
}

class Classifier private constructor(private val interpreter: Interpreter, val info: ModelInfo) : Closeable {
    private val inShape = interpreter.getInputTensor(0).shape() // [1, h, w, 3]
    private val inType = interpreter.getInputTensor(0).dataType()
    private val out = interpreter.getOutputTensor(0)
    private val h = inShape[1]
    private val w = inShape[2]
    private val pixels = IntArray(w * h)
    private val input = ByteBuffer.allocateDirect(w * h * 3 * if (inType == DataType.FLOAT32) 4 else 1).order(ByteOrder.nativeOrder())
    private val output = ByteBuffer.allocateDirect(out.numBytes()).order(ByteOrder.nativeOrder())
    private val mean = info.input.mean.toFloatArray()
    private val std = info.input.std.toFloatArray()
    private val div = if (info.input.range == "0-255") 1f else 255f

    /** Not thread-safe: call from one thread (the camera analyzer or a single coroutine). Takes an upright photo. */
    fun classify(bitmap: Bitmap): List<Prediction> {
        val src = if (info.input.resize == "center_crop") bitmap.centerSquare() else bitmap
        val scaled = downscale(src, w, h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== bitmap) scaled.recycle()
        if (src !== bitmap) src.recycle()
        input.rewind()
        for (p in pixels) {
            val r = p shr 16 and 0xFF
            val g = p shr 8 and 0xFF
            val b = p and 0xFF
            when (inType) {
                DataType.FLOAT32 -> {
                    input.putFloat((r / div - mean[0]) / std[0])
                    input.putFloat((g / div - mean[1]) / std[1])
                    input.putFloat((b / div - mean[2]) / std[2])
                }
                DataType.INT8 -> { input.put((r - 128).toByte()); input.put((g - 128).toByte()); input.put((b - 128).toByte()) }
                else -> { input.put(r.toByte()); input.put(g.toByte()); input.put(b.toByte()) } // UINT8 takes raw 0..255
            }
        }
        output.rewind()
        interpreter.run(input, output)
        output.rewind()
        val n = info.labels.size
        val q = out.quantizationParams()
        val raw = FloatArray(n) {
            when (out.dataType()) {
                DataType.FLOAT32 -> output.getFloat()
                DataType.UINT8 -> ((output.get().toInt() and 0xFF) - q.zeroPoint) * q.scale
                DataType.INT8 -> (output.get() - q.zeroPoint) * q.scale
                else -> error("unsupported output type ${out.dataType()}")
            }
        }
        return aggregate(if (info.output == "logits") softmax(raw) else raw, info.labels)
    }

    override fun close() = interpreter.close()

    private fun Bitmap.centerSquare(): Bitmap {
        val side = minOf(width, height)
        return Bitmap.createBitmap(this, (width - side) / 2, (height - side) / 2, side, side)
    }

    companion object {
        private val mutex = Mutex()
        @Volatile private var instance: Classifier? = null

        // ponytail: bundled package only; phase 5 OTA picks filesDir/model/v<N> when a validated newer one exists.
        suspend fun get(context: Context): Classifier = instance ?: mutex.withLock {
            instance ?: withContext(Dispatchers.IO) { load(context) }.also { instance = it }
        }

        private fun load(context: Context): Classifier {
            val app = context.applicationContext
            val info = app.assets.open("model/model.json").use { Catalog.json.decodeFromString<ModelInfo>(it.reader().readText()) }
            val model: MappedByteBuffer = app.assets.openFd("model/model.tflite").use { fd ->
                FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
            val interpreter = Interpreter(model, Interpreter.Options().setNumThreads(4))
            val catalog = Catalog.get(app)
            val appVersion = PackageInfoCompat.getLongVersionCode(app.packageManager.getPackageInfo(app.packageName, 0))
            val outputSize = interpreter.getOutputTensor(0).shape().last()
            validateModel(info, outputSize, catalog.species.map { it.id }.toSet(), appVersion, catalog.dataVersion)?.let {
                interpreter.close()
                error("Model rejected: $it")
            }
            return Classifier(interpreter, info)
        }
    }
}
