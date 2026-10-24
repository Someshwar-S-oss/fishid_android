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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.example.fishid.R
import com.example.fishid.data.Catalog
import com.example.fishid.data.FishEntry
import com.example.fishid.ui.theme.Design
import com.example.fishid.ui.theme.DesignChoice
import com.example.fishid.ui.theme.FishTheme
import com.example.fishid.ui.theme.IucnColors
import com.example.fishid.ui.theme.ScientificStyle
import com.example.fishid.ui.theme.Sea
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

fun Context.design(): Design = runCatching { Design.valueOf(prefs().getString("design", null)!!) }.getOrDefault(Design.SEA)

/** Saves and applies at once: the theme reads [DesignChoice], so open screens restyle without a restart. */
fun Context.setDesign(design: Design) {
    prefs().edit().putString("design", design.name).apply()
    DesignChoice.current = design
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

/**
 * Market design's solid "sticker" shadow: the shape repeated [offset] down-right in [color].
 * No-op for a zero offset or transparent colour, so call sites don't branch on the design.
 */
fun Modifier.hardShadow(shape: Shape, offset: Dp, color: Color): Modifier =
    if (offset <= 0.dp || color.alpha == 0f) this
    else drawBehind {
        val o = offset.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)
        translate(o, o) { drawOutline(outline, color) }
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

// ---------- Cards and buttons ----------

/** Sea: soft card with a hairline. Market: ink-bordered card on a solid offset shadow. Clickable when [onClick] is set. */
@Composable
fun FishCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val s = FishTheme.style
    val shape = RoundedCornerShape(s.cardRadius)
    val m = modifier.hardShadow(shape, s.hardShadow, s.colors.shadow)
    val border = BorderStroke(s.cardBorder, s.colors.border)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = m, shape = shape, color = color, contentColor = contentColor, border = border, shadowElevation = s.cardElevation) { Column(content = content) }
    } else {
        Surface(modifier = m, shape = shape, color = color, contentColor = contentColor, border = border, shadowElevation = s.cardElevation) { Column(content = content) }
    }
}

/** The one main action on a screen: full width, at least 56 dp tall, label wraps rather than clips (Tamil runs long). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
    @DrawableRes trailingIcon: Int? = null,
) = FishButton(text, onClick, modifier, enabled, icon, trailingIcon, FishTheme.colors.cta, FishTheme.colors.onCta)

/** A filled (never outlined) secondary action: outlines read as disabled to first-time smartphone users. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) = FishButton(text, onClick, modifier, enabled, icon, null, FishTheme.colors.secondaryButton, FishTheme.colors.onSecondaryButton)

@Composable
private fun FishButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    @DrawableRes icon: Int?,
    @DrawableRes trailingIcon: Int?,
    container: Color,
    content: Color,
) {
    val s = FishTheme.style
    val shape = RoundedCornerShape(s.buttonRadius)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Market: the button rests on its shadow and sinks into it while pressed. Sea: zero offset, no-op.
    val lift = if (enabled && !pressed) s.hardShadow else 0.dp
    val sink = if (enabled && pressed) s.hardShadow else 0.dp
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container, contentColor = content,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant, disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        elevation = null,
        border = if (s.buttonBorder > 0.dp) BorderStroke(s.buttonBorder, if (enabled) s.colors.border else MaterialTheme.colorScheme.outlineVariant) else null,
        interactionSource = interaction,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = s.buttonHeight).hardShadow(shape, lift, s.colors.shadow).offset(sink, sink),
    ) {
        icon?.let { Icon(painterResource(it), null, Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
        trailingIcon?.let { Spacer(Modifier.width(8.dp)); Icon(painterResource(it), null, Modifier.size(22.dp)) }
    }
}

/** Low-emphasis action ("Wrong fish? Tell us"): text only, still a 48 dp target. Underlined in Market so it reads as tappable. */
@Composable
fun LinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = color)) {
        icon?.let { Icon(painterResource(it), null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(
            text,
            style = MaterialTheme.typography.titleSmall.copy(textDecoration = if (FishTheme.style.isMarket) TextDecoration.Underline else null),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** Section icon in a tinted circle (Sea) or a bordered turmeric tile (Market), like the deck's circled icons. */
@Composable
fun IconBadge(@DrawableRes icon: Int, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val s = FishTheme.style
    val shape = if (s.isMarket) RoundedCornerShape(size * 0.3f) else CircleShape
    Box(
        modifier.size(size).clip(shape).background(s.colors.iconBadge)
            .then(if (s.isMarket) Modifier.border(1.5.dp, s.colors.border, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), null, tint = s.colors.onIconBadge, modifier = Modifier.size(size * 0.55f))
    }
}

// ---------- Match confidence ----------

/** Colours and wording for a bar count (see ml.matchBars). Weaker matches get neutral colours and no word. */
@Immutable
class MatchLook(val container: Color, val content: Color, val bar: Color, @get:StringRes val label: Int?)

@Composable
fun matchLook(bars: Int): MatchLook {
    val c = FishTheme.colors
    return when {
        bars >= 4 -> MatchLook(c.sureContainer, c.onSureContainer, c.sure, R.string.result_match_high)
        bars == 3 -> MatchLook(c.likelyContainer, c.onLikelyContainer, c.likely, R.string.result_match_likely)
        else -> MatchLook(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, null)
    }
}

/** Four rising bars, like phone signal. Decorative: the text next to it carries the meaning for TalkBack. */
@Composable
fun MatchBars(bars: Int, color: Color, modifier: Modifier = Modifier, height: Dp = 18.dp) {
    val empty = color.copy(alpha = 0.25f)
    Canvas(modifier.size(width = height * 1.25f, height = height)) {
        val gap = size.width * 0.1f
        val w = (size.width - gap * 3) / 4
        for (i in 0 until 4) {
            val h = size.height * (i + 1) / 4
            drawRoundRect(
                color = if (i < bars) color else empty,
                topLeft = Offset(i * (w + gap), size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 3),
            )
        }
    }
}

/** Bars + "High match · 97%" (or just "42% match" for weak candidates) in a tinted pill. */
@Composable
fun MatchChip(bars: Int, percent: Int, modifier: Modifier = Modifier) {
    val look = matchLook(bars)
    val text = look.label?.let { stringResource(R.string.result_match_percent, stringResource(it), percent) }
        ?: stringResource(R.string.result_confidence, percent)
    Surface(modifier, shape = RoundedCornerShape(50), color = look.container, contentColor = look.content) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MatchBars(bars, look.bar, height = 14.dp)
            Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

// ---------- Price ----------

/**
 * Price per kg. Sea: a soft pill with a tag icon. Market: a turmeric luggage tag with an eyelet,
 * the way prices hang on fish-market signboards. [large] for the result sheet and profile.
 */
@Composable
fun PriceTag(price: Int, modifier: Modifier = Modifier, large: Boolean = false) {
    val s = FishTheme.style
    val c = s.colors
    if (s.isMarket) {
        val notch = if (large) 18.dp else 13.dp
        val shape = remember(notch) { TagShape(notch) }
        val eyelet = MaterialTheme.colorScheme.surface
        Surface(
            modifier.hardShadow(shape, if (large) s.hardShadow else 2.dp, c.shadow),
            shape = shape, color = c.priceContainer, contentColor = c.onPriceContainer, border = BorderStroke(2.dp, c.border),
        ) {
            Row(
                Modifier
                    .drawBehind {
                        val center = Offset(notch.toPx() * 0.85f, size.height / 2)
                        drawCircle(eyelet, radius = 3.5.dp.toPx(), center = center)
                        drawCircle(c.border, radius = 3.5.dp.toPx(), center = center, style = Stroke(1.5.dp.toPx()))
                    }
                    .padding(start = notch + 8.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val figure = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium
                Text(formatRupees(price), style = figure.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"))
                Text(stringResource(R.string.price_unit_per_kg), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 5.dp))
            }
        }
    } else {
        Surface(modifier, shape = RoundedCornerShape(50), color = c.priceContainer, contentColor = c.onPriceContainer) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(painterResource(R.drawable.ic_price), null, tint = c.accentInk, modifier = Modifier.size(18.dp))
                Text(
                    stringResource(R.string.price_per_kg, formatRupees(price)),
                    style = (if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium).copy(fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

/** A luggage-tag outline: pointed on the left, rounded on the right. */
private class TagShape(private val notch: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val n = with(density) { notch.toPx() }.coerceAtMost(size.height / 2)
        val r = with(density) { 6.dp.toPx() }
        val path = Path().apply {
            moveTo(0f, size.height / 2)
            lineTo(n, 0f)
            lineTo(size.width - r, 0f)
            quadraticTo(size.width, 0f, size.width, r)
            lineTo(size.width, size.height - r)
            quadraticTo(size.width, size.height, size.width - r, size.height)
            lineTo(n, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

// ---------- Small components ----------

@Composable
fun Pill(text: String, background: Color, contentColor: Color, modifier: Modifier = Modifier, border: Color? = null) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = background,
        contentColor = contentColor,
        border = border?.let { BorderStroke(if (FishTheme.style.isMarket) 1.5.dp else 1.dp, it) },
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
    }
}

@Composable
fun IucnBadge(code: String, long: Boolean = true) {
    val (bg, fg) = IucnColors[code] ?: return
    val label = codeLabel(code, ::iucnRes)
    val ringed = code == "ne" || FishTheme.style.isMarket
    Pill(if (long) "${code.uppercase()} · $label" else label, bg, fg, border = if (ringed) MaterialTheme.colorScheme.onSurfaceVariant else null)
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
    val market = FishTheme.style.isMarket
    val outline = if (onDark) Color.White.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline
    Surface(shape = RoundedCornerShape(50), color = Color.Transparent, border = BorderStroke(if (market && !onDark) 2.dp else 1.dp, outline)) {
        Row(Modifier.padding(2.dp)) {
            LangOption("A", stringResource(R.string.language_english), selected = !tamil, onDark) { setAppLanguage("en") }
            LangOption("அ", stringResource(R.string.language_tamil), selected = tamil, onDark) { setAppLanguage("ta") }
        }
    }
}

@Composable
private fun LangOption(glyph: String, name: String, selected: Boolean, onDark: Boolean, onClick: () -> Unit) {
    val s = FishTheme.style
    val selBg = when {
        s.isMarket -> s.colors.cta
        onDark -> Color.White
        else -> MaterialTheme.colorScheme.primary
    }
    val selFg = when {
        s.isMarket -> s.colors.onCta
        onDark -> Sea.Ink
        else -> MaterialTheme.colorScheme.onPrimary
    }
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
