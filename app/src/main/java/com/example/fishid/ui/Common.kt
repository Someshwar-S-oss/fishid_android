package com.example.fishid.ui

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.example.fishid.R
import com.example.fishid.data.Catalog
import com.example.fishid.data.FishEntry
import com.example.fishid.ui.theme.Ink
import com.example.fishid.ui.theme.IucnColors
import com.example.fishid.ui.theme.ScientificStyle
import com.example.fishid.ui.theme.isTamil
import com.example.fishid.ui.theme.tamilLineHeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.util.Locale

// ---------- Language ----------

/** Switches the app language; AppCompat recreates the activity and persists the choice. */
fun setAppLanguage(tag: String) = AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))

@Composable
fun currentLang(): String = if (isTamil()) "ta" else "en"

// ---------- Theme ----------

enum class ThemeMode(val night: Int) {
    SYSTEM(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(AppCompatDelegate.MODE_NIGHT_NO),
    DARK(AppCompatDelegate.MODE_NIGHT_YES),
}

private fun Context.prefs() = getSharedPreferences("fishid", Context.MODE_PRIVATE)

fun Context.themeMode(): ThemeMode = runCatching { ThemeMode.valueOf(prefs().getString("theme", null)!!) }.getOrDefault(ThemeMode.SYSTEM)

/** Saves and applies; AppCompat recreates the activity in the new mode. */
fun Context.setThemeMode(mode: ThemeMode) {
    prefs().edit().putString("theme", mode.name).apply()
    AppCompatDelegate.setDefaultNightMode(mode.night)
}

// ---------- Layout ----------

/** Text columns stop growing here, so tablets and landscape get a centred column instead of 1000dp lines. */
val ReadableWidth = 640.dp

fun Modifier.readableWidth() = this.widthIn(max = ReadableWidth).fillMaxWidth()

/**
 * Full-height screen with [top] content and [bottom] actions pinned to the bottom, that scrolls
 * instead of overlapping when the screen is short (landscape, large system font).
 */
@Composable
fun ScrollFill(modifier: Modifier = Modifier, top: @Composable ColumnScope.() -> Unit, bottom: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.readableWidth(), content = top)
            Column(Modifier.readableWidth().padding(top = 24.dp), content = bottom)
        }
    }
}

/** Light (white) or dark status-bar icons while this composable is shown; restores the previous style after. */
@Composable
fun StatusBarIcons(light: Boolean) {
    val view = LocalView.current
    DisposableEffect(light) {
        val window = (view.context as? Activity)?.window
        val bars = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = bars?.isAppearanceLightStatusBars
        bars?.isAppearanceLightStatusBars = !light
        onDispose { if (previous != null) bars.isAppearanceLightStatusBars = previous }
    }
}

// ---------- Formatting ----------

// Western digits in both languages, Indian grouping (₹1,200).
private val rupees = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply { maximumFractionDigits = 0 }

fun formatRupees(amount: Int): String = rupees.format(amount)

fun formatGrams(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

// ---------- Coded values → UI strings ----------

@StringRes fun habitatRes(code: String) = when (code) {
    "marine" -> R.string.habitat_marine
    "brackish" -> R.string.habitat_brackish
    "freshwater" -> R.string.habitat_freshwater
    "reef_associated" -> R.string.habitat_reef_associated
    else -> null
}

@StringRes fun feedingRes(code: String) = when (code) {
    "carnivore" -> R.string.feeding_carnivore
    "herbivore" -> R.string.feeding_herbivore
    "omnivore" -> R.string.feeding_omnivore
    "planktivore" -> R.string.feeding_planktivore
    "detritivore" -> R.string.feeding_detritivore
    else -> null
}

@StringRes fun verticalRes(code: String) = when (code) {
    "demersal" -> R.string.vertical_demersal
    "pelagic" -> R.string.vertical_pelagic
    "benthopelagic" -> R.string.vertical_benthopelagic
    "reef" -> R.string.vertical_reef
    else -> null
}

@StringRes fun iucnRes(code: String) = when (code) {
    "ne" -> R.string.iucn_ne
    "dd" -> R.string.iucn_dd
    "lc" -> R.string.iucn_lc
    "nt" -> R.string.iucn_nt
    "vu" -> R.string.iucn_vu
    "en" -> R.string.iucn_en
    "cr" -> R.string.iucn_cr
    else -> null
}

/** Unknown codes show raw rather than crash, so a data typo is visible but harmless. */
@Composable
fun codeLabel(code: String, res: (String) -> Int?): String = res(code)?.let { stringResource(it) } ?: code

// ---------- Images ----------

private val imageCache = LruCache<String, ImageBitmap>(32)

/** Decodes an asset ("data/images/x.jpg") or an absolute file path, downsampled to about [maxPx]. */
private fun decodeImage(context: Context, path: String, maxPx: Int): ImageBitmap? = runCatching {
    fun open() = if (path.startsWith("/")) File(path).inputStream() else context.assets.open(path)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    open().use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
    open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }?.asImageBitmap()
}.getOrNull()

@Composable
fun FishImage(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    maxPx: Int = 1080,
) {
    val context = LocalContext.current
    val key = "$path@$maxPx"
    val bitmap by produceState(path?.let { imageCache.get(key) }, key) {
        if (value == null && path != null) {
            value = withContext(Dispatchers.IO) { decodeImage(context, path, maxPx) }?.also { imageCache.put(key, it) }
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.outlineVariant), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) {
            Image(b, contentDescription, Modifier.matchParentSize(), contentScale = contentScale)
        } else if (path == null) {
            Icon(painterResource(R.drawable.ic_fish), contentDescription, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun FishEntry.imagePath(index: Int = 0): String? = species.images.getOrNull(index)?.let { "${Catalog.IMAGE_DIR}/$it" }

// ---------- Names ----------

/** Primary name follows the UI language; Tamil mode leads with the local market name. */
fun FishEntry.primaryName(tamil: Boolean): String = if (tamil) species.localNameTa ?: commonName else commonName

fun FishEntry.secondaryName(tamil: Boolean): String? = if (tamil) commonName.takeIf { species.localNameTa != null } else species.localNameTa

@Composable
fun FishEntry.primaryName(): String = primaryName(isTamil())

@Composable
fun FishEntry.secondaryName(): String? = secondaryName(isTamil())

/** Primary + secondary + scientific name stack used on result cards and the profile. */
@Composable
fun NameStack(entry: FishEntry, primary: TextStyle, secondary: TextStyle, scientific: TextStyle, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(entry.primaryName(), style = if (isTamil()) tamilLineHeight(primary) else primary)
        entry.secondaryName()?.let {
            Text(it, style = tamilLineHeight(secondary), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(entry.species.scientificName, style = scientific.merge(ScientificStyle), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- Small components ----------

@Composable
fun FishCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) { Column(content = content) }
}

@Composable
fun Pill(text: String, background: Color, contentColor: Color, modifier: Modifier = Modifier, border: Color? = null) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = background,
        contentColor = contentColor,
        border = border?.let { BorderStroke(1.dp, it) },
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
    }
}

@Composable
fun IucnBadge(code: String, long: Boolean = true) {
    val (bg, fg) = IucnColors[code] ?: return
    val label = codeLabel(code, ::iucnRes)
    Pill(if (long) "${code.uppercase()} · $label" else label, bg, fg, border = if (code == "ne") MaterialTheme.colorScheme.onSurfaceVariant else null)
}

/** Screen top bar: title, language switch, optional settings. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.action_back)) }
        } else {
            Spacer(Modifier.size(12.dp))
        }
        // One line: long Tamil titles shrink to fit beside the language switch instead of doubling the bar height.
        val style = MaterialTheme.typography.titleLarge
        Text(
            title, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 15.sp, maxFontSize = style.fontSize),
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** A / அ segmented switch. Labels are each language's own script, never translated. */
@Composable
fun LanguageSwitch(onDark: Boolean = false) {
    val tamil = isTamil()
    val outline = if (onDark) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline
    Surface(shape = RoundedCornerShape(50), color = Color.Transparent, border = BorderStroke(1.dp, outline)) {
        Row(Modifier.padding(2.dp)) {
            LangOption("A", stringResource(R.string.language_english), selected = !tamil, onDark) { setAppLanguage("en") }
            LangOption("அ", stringResource(R.string.language_tamil), selected = tamil, onDark) { setAppLanguage("ta") }
        }
    }
}

@Composable
private fun LangOption(glyph: String, name: String, selected: Boolean, onDark: Boolean, onClick: () -> Unit) {
    val selBg = if (onDark) Color.White else MaterialTheme.colorScheme.primary
    val selFg = if (onDark) Ink else MaterialTheme.colorScheme.onPrimary
    val fg = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = { if (!selected) onClick() },
        shape = RoundedCornerShape(50),
        color = if (selected) selBg else Color.Transparent,
        contentColor = if (selected) selFg else fg,
        // Spoken label is the language's own name, not the glyph.
        modifier = Modifier.heightIn(min = 40.dp).semantics {
            role = Role.RadioButton
            this.selected = selected
            contentDescription = name
        },
    ) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text(glyph, style = MaterialTheme.typography.titleSmall)
        }
    }
}
