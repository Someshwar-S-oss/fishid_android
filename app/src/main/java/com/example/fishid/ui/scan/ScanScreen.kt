package com.example.fishid.ui.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.fishid.R
import com.example.fishid.data.Catalog
import com.example.fishid.data.FishEntry
import com.example.fishid.data.ScanRecord
import com.example.fishid.ml.Classifier
import com.example.fishid.ml.matchBars
import com.example.fishid.ui.FishImage
import com.example.fishid.ui.IconBadge
import com.example.fishid.ui.LanguageSwitch
import com.example.fishid.ui.LinkButton
import com.example.fishid.ui.MatchBars
import com.example.fishid.ui.NameStack
import com.example.fishid.ui.PriceTag
import com.example.fishid.ui.PrimaryButton
import com.example.fishid.ui.ScrollFill
import com.example.fishid.ui.SecondaryButton
import com.example.fishid.ui.currentLang
import com.example.fishid.ui.formatRupees
import com.example.fishid.ui.imagePath
import com.example.fishid.ui.matchLook
import com.example.fishid.ui.onboarding.Brackets
import com.example.fishid.ui.theme.FishTheme
import com.example.fishid.ui.theme.Sea
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

private fun Context.prefs() = getSharedPreferences("fishid", Context.MODE_PRIVATE)

private const val BRACKET_FRACTION = 0.92f

@Composable
fun ScanScreen(
    onProfile: (speciesId: String, scanId: String) -> Unit,
    onUnsure: (ScanRecord) -> Unit,
    onReport: (scanId: String) -> Unit,
    onSettings: () -> Unit,
    onCameraShown: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        context.prefs().edit().putBoolean("camera_asked", true).apply()
    }

    LifecycleResumeEffect(Unit) {
        granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        onPauseOrDispose {}
    }

    // Registered before the camera, so it is disposed after the camera unbinds (no frames hit a dead executor).
    val executor = remember { Executors.newSingleThreadExecutor() }
    val engine = remember { ScanEngine(context, executor) }
    engine.onElsewhere = onUnsure
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    LaunchedEffect(Unit) { engine.classifier = runCatching { Classifier.get(context) }.getOrNull() }

    val scope = rememberCoroutineScope()
    val dispatcher = remember { executor.asCoroutineDispatcher() }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            withContext(dispatcher) { loadPicked(context, uri)?.let(engine::identifyBitmap) }
        }
    }
    val pickPhoto = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    if (!granted) {
        PermissionRationale(
            onAllow = {
                val askedBefore = context.prefs().getBoolean("camera_asked", false)
                val activity = context as? androidx.activity.ComponentActivity
                // After a permanent denial the system dialog no longer appears: send the user to settings.
                if (askedBefore && activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                } else {
                    permission.launch(Manifest.permission.CAMERA)
                }
            },
            onGallery = pickPhoto,
        )
        // Without the camera there is no viewfinder to show a result card on: go straight to the profile.
        LaunchedEffect(engine.result) {
            engine.result?.let { r -> engine.scanAgain(); onProfile(r.speciesId!!, r.id) }
        }
        return
    }

    // Light status/nav bar icons over the dark camera; restored when leaving the tab.
    val rootView = LocalView.current
    DisposableEffect(Unit) {
        onCameraShown(true)
        val window = (rootView.context as android.app.Activity).window
        val bars = WindowCompat.getInsetsController(window, rootView)
        val light = bars.isAppearanceLightStatusBars to bars.isAppearanceLightNavigationBars
        bars.isAppearanceLightStatusBars = false
        bars.isAppearanceLightNavigationBars = false
        onDispose {
            bars.isAppearanceLightStatusBars = light.first
            bars.isAppearanceLightNavigationBars = light.second
            onCameraShown(false)
        }
    }

    val style = FishTheme.style
    val c = style.colors
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        var camera by remember { mutableStateOf<Camera?>(null) }
        var cameraFailed by remember { mutableStateOf(false) }
        CameraPreview(engine, onCamera = { camera = it }, onError = { cameraFailed = true })

        val bracketColor by animateColorAsState(
            when {
                engine.result != null -> c.lock
                !engine.live -> Color.White
                else -> c.accent
            },
            tween(200), label = "brackets",
        )
        // Same shape and size as the area the model sees (ScanEngine.bracketArea).
        Box(Modifier.fillMaxWidth(BRACKET_FRACTION).widthIn(max = 640.dp).aspectRatio(BRACKET_ASPECT).align(Alignment.Center)) {
            Brackets(bracketColor, inset = 0.dp, stroke = style.bracketStroke)
        }

        Column(Modifier.fillMaxSize()) {
            CameraTopBar(onSettings)
            Spacer(Modifier.weight(1f))
            if (engine.switchedToTap && !context.prefs().getBoolean("tap_notice_shown", false)) {
                TapNotice(onSettings = { context.prefs().edit().putBoolean("tap_notice_shown", true).apply(); engine.switchedToTap = false; onSettings() }) {
                    context.prefs().edit().putBoolean("tap_notice_shown", true).apply()
                    engine.switchedToTap = false
                }
            }
            // One status line replaces the old "Live / Tap" chip: say what is happening, not which mode it is.
            val hint = when {
                cameraFailed -> R.string.scan_camera_error
                engine.identifying -> R.string.scan_identifying
                !engine.live -> R.string.scan_hint_tap
                engine.holding -> R.string.scan_hint_hold_still
                else -> R.string.scan_hint_frame
            }
            val dot = when {
                cameraFailed -> MaterialTheme.colorScheme.error
                !engine.live -> Color.White
                engine.holding -> c.lock
                else -> c.accent
            }
            StatusPill(stringResource(hint), dot, busy = engine.identifying, pulse = engine.live && !engine.holding && !cameraFailed)
            Controls(
                identifying = engine.identifying,
                hasFlash = camera?.cameraInfo?.hasFlashUnit() == true,
                onGallery = pickPhoto,
                onShutter = engine::shutter,
                onFlash = { on -> camera?.cameraControl?.enableTorch(on) },
            )
        }

        // Back closes the result card and keeps scanning, instead of leaving the app.
        BackHandler(enabled = engine.result != null) { engine.scanAgain() }
        // Keep showing the last result while the card slides away.
        var last by remember { mutableStateOf<ScanRecord?>(null) }
        engine.result?.let { last = it }
        val view = LocalView.current
        LaunchedEffect(engine.result) {
            if (engine.result != null) view.performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
        }
        AnimatedVisibility(
            visible = engine.result != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(280)) { it },
            exit = slideOutVertically(tween(200)) { it },
        ) {
            last?.let { r ->
                ResultSheet(r, engine::thresholdFor, onDetails = { onProfile(r.speciesId!!, r.id) }, onAgain = engine::scanAgain, onReport = { onReport(r.id) })
            }
        }
    }
}

@Composable
private fun CameraPreview(engine: ScanEngine, onCamera: (Camera) -> Unit, onError: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    AndroidView({ previewView }, Modifier.fillMaxSize())
    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { it.setAnalyzer(engine.analyzerExecutor, engine) }
                provider.unbindAll()
                onCamera(provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis))
            }.onFailure { onError() }
        }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { future.get().unbindAll() } }
    }
}

@Composable
private fun CameraTopBar(onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(FishTheme.colors.cameraScrim).statusBarsPadding().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
        LanguageSwitch(onDark = true)
        IconButton(onClick = onSettings) { Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.action_settings), tint = Color.White) }
    }
}

/** What the scanner is doing right now, in words, with a coloured dot (pulsing while it searches) or a spinner. */
@Composable
private fun StatusPill(text: String, dot: Color, busy: Boolean, pulse: Boolean) {
    val dotAlpha = if (pulse) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 1f, targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse), label = "dot",
        ).value
    } else 1f
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(50), color = FishTheme.colors.cameraScrim, contentColor = Color.White,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Row(Modifier.padding(start = 16.dp, end = 20.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (busy) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(16.dp))
                } else {
                    Box(Modifier.size(12.dp).graphicsLayer { alpha = dotAlpha }.clip(CircleShape).background(dot))
                }
                Text(text, style = MaterialTheme.typography.titleSmall.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun TapNotice(onSettings: () -> Unit, onDismiss: () -> Unit) {
    val c = FishTheme.colors
    Surface(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(FishTheme.style.cardRadius), color = c.cameraBar, contentColor = Color.White, shadowElevation = 6.dp) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.scan_switched_to_tap), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
            TextButton(onClick = onSettings) { Text(stringResource(R.string.action_settings), color = c.cameraHighlight) }
            IconButton(onClick = onDismiss) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_done), tint = Color.White) }
        }
    }
}

/** Photos · Identify fish · Flash. Every control has a visible label, over a gradient so it stays legible on bright scenes. */
@Composable
private fun Controls(identifying: Boolean, hasFlash: Boolean, onGallery: () -> Unit, onShutter: () -> Unit, onFlash: (Boolean) -> Unit) {
    var torch by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, FishTheme.colors.cameraScrim)))
            .padding(top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Control(stringResource(R.string.scan_gallery_label)) { GlassButton(R.drawable.ic_gallery, stringResource(R.string.scan_gallery_description), onGallery) }
        Control(stringResource(R.string.scan_shutter_description)) { Shutter(identifying, onShutter) }
        if (hasFlash) {
            Control(stringResource(R.string.scan_flash_label)) {
                GlassButton(
                    R.drawable.ic_flash,
                    stringResource(if (torch) R.string.scan_flash_off_description else R.string.scan_flash_on_description),
                    { torch = !torch; onFlash(torch) },
                    active = torch,
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun RowScope.Control(label: String, button: @Composable () -> Unit) {
    // The label repeats what the button already tells TalkBack, so it grows only to 130%: at 200% a Tamil word
    // is wider than a third of the screen and split mid-word.
    val fontScale = LocalDensity.current.fontScale
    val size = MaterialTheme.typography.labelMedium.fontSize.value * minOf(fontScale, 1.3f) / fontScale
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(84.dp), contentAlignment = Alignment.Center) { button() }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = size.sp, lineHeight = (size * 1.5f).sp,
                fontWeight = FontWeight.SemiBold, shadow = Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 6f),
            ),
            color = Color.White, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp).clearAndSetSemantics {}, // the button already says it
        )
    }
}

/** Solid, so it reads as a button and not a disabled control. Sea: white disc, ocean icon. Market: turmeric disc, ink rim. */
@Composable
private fun Shutter(identifying: Boolean, onClick: () -> Unit) {
    val style = FishTheme.style
    val core = if (style.isMarket) style.colors.cta else Color.White
    val coreInk = if (style.isMarket) style.colors.onCta else Sea.Ocean
    val label = stringResource(R.string.scan_shutter_description)
    Surface(
        onClick = onClick,
        enabled = !identifying,
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(4.dp, Color.White),
        modifier = Modifier.size(80.dp).semantics { contentDescription = label },
    ) {
        Box(
            Modifier.padding(7.dp).clip(CircleShape).background(core)
                .then(if (style.isMarket) Modifier.border(2.dp, style.colors.onCta, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (identifying) CircularProgressIndicator(color = coreInk, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
            else Icon(painterResource(R.drawable.ic_scan), null, tint = coreInk, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun GlassButton(icon: Int, description: String, onClick: () -> Unit, active: Boolean = false) {
    val c = FishTheme.colors
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) c.cameraHighlight else c.cameraScrim,
        contentColor = if (active) Sea.Ink else Color.White,
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = if (active) 0f else 0.35f)),
        modifier = Modifier.size(58.dp),
    ) { Box(contentAlignment = Alignment.Center) { Icon(painterResource(icon), description) } }
}

/**
 * The identified fish, sliding up over the paused camera. Price and match level sit at the same weight as the name;
 * actions are stacked full width (Tamil labels wrapped to three lines side by side). Scrolls on short screens.
 */
@Composable
private fun ResultSheet(record: ScanRecord, thresholdFor: (String) -> Float, onDetails: () -> Unit, onAgain: () -> Unit, onReport: () -> Unit) {
    val context = LocalContext.current
    val lang = currentLang()
    val entry = remember(record.id, lang) { record.speciesId?.let { Catalog.get(context).entry(it, lang) } } ?: return
    val confidence = record.confidence ?: 0f
    val bars = matchBars(confidence, thresholdFor(entry.id))
    val percent = (confidence * 100).toInt()
    val style = FishTheme.style
    Surface(
        shape = RoundedCornerShape(topStart = if (style.isMarket) 22.dp else 28.dp, topEnd = if (style.isMarket) 22.dp else 28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (style.isMarket) 0.dp else 12.dp,
        border = if (style.isMarket) BorderStroke(2.dp, style.colors.border) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp, 12.dp, 16.dp, 12.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 5.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.outline))
            if (style.isMarket) MarketResult(entry, bars, percent) else SeaResult(entry, bars, percent)
            PrimaryButton(stringResource(R.string.result_view_details), onDetails, Modifier.padding(top = 16.dp), trailingIcon = R.drawable.ic_chevron_right)
            SecondaryButton(stringResource(R.string.result_scan_again), onAgain, Modifier.padding(top = 10.dp), icon = R.drawable.ic_scan)
            LinkButton(stringResource(R.string.profile_report_link), onReport, Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp), icon = R.drawable.ic_flag)
        }
    }
}

/** Sea: thumbnail + names, then two equal tiles: how sure, and what it costs. */
@Composable
private fun SeaResult(entry: FishEntry, bars: Int, percent: Int) {
    Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        FishImage(entry.imagePath(), stringResource(R.string.label_reference_photo), Modifier.size(96.dp).clip(RoundedCornerShape(18.dp)), maxPx = 300)
        NameStack(entry, MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium), MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal), Modifier.weight(1f))
    }
    val price = entry.species.priceInrPerKg
    // Side by side normally; stacked at large text sizes, where half-width tiles split Tamil words.
    if (LocalDensity.current.fontScale > 1.3f) {
        Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MatchTile(bars, percent, Modifier.fillMaxWidth())
            price?.let { PriceTile(it, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(Modifier.padding(top = 16.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MatchTile(bars, percent, Modifier.weight(1f).fillMaxHeight())
            price?.let { PriceTile(it, Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

@Composable
private fun MatchTile(bars: Int, percent: Int, modifier: Modifier) {
    val look = matchLook(bars)
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = look.container, contentColor = look.content) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MatchBars(bars, look.bar, height = 22.dp)
            look.label?.let { Text(stringResource(it), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 4.dp)) }
            Text("$percent%", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
        }
    }
}

@Composable
private fun PriceTile(price: Int, modifier: Modifier) {
    val c = FishTheme.colors
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = c.priceContainer, contentColor = c.onPriceContainer) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(painterResource(R.drawable.ic_price), null, tint = c.accentInk, modifier = Modifier.size(22.dp))
            Text(formatRupees(price), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"), modifier = Modifier.padding(top = 2.dp))
            Text(stringResource(R.string.price_unit_per_kg), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Market: a framed photo carrying the price tag like a market signboard, big name, then a full-width match band. */
@Composable
private fun MarketResult(entry: FishEntry, bars: Int, percent: Int) {
    val c = FishTheme.colors
    val frame = RoundedCornerShape(12.dp)
    Box(Modifier.padding(top = 14.dp).fillMaxWidth()) {
        FishImage(entry.imagePath(), stringResource(R.string.label_reference_photo), Modifier.fillMaxWidth().height(132.dp).clip(frame).border(2.dp, c.border, frame), maxPx = 640)
        entry.species.priceInrPerKg?.let { PriceTag(it, Modifier.align(Alignment.BottomEnd).padding(10.dp).rotate(-3f), large = true) }
    }
    NameStack(entry, MaterialTheme.typography.headlineMedium, MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium), MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal), Modifier.padding(top = 14.dp))
    val look = matchLook(bars)
    Surface(Modifier.padding(top = 14.dp).fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = look.container, contentColor = look.content, border = BorderStroke(2.dp, c.border)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(if (bars >= 4) R.drawable.ic_check else R.drawable.ic_info), null, modifier = Modifier.size(24.dp))
            MatchBars(bars, look.bar, height = 20.dp)
            val text = look.label?.let { stringResource(R.string.result_match_percent, stringResource(it), percent) } ?: stringResource(R.string.result_confidence, percent)
            Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PermissionRationale(onAllow: () -> Unit, onGallery: () -> Unit) {
    ScrollFill(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(16.dp), top = {
        Spacer(Modifier.height(64.dp))
        IconBadge(R.drawable.ic_camera, Modifier.align(Alignment.CenterHorizontally), size = 112.dp)
        Text(stringResource(R.string.permission_camera_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 12.dp))
        Text(stringResource(R.string.permission_camera_body), style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }, bottom = {
        PrimaryButton(stringResource(R.string.permission_allow), onAllow, icon = R.drawable.ic_camera)
        SecondaryButton(stringResource(R.string.permission_use_gallery), onGallery, Modifier.padding(top = 12.dp), icon = R.drawable.ic_gallery)
    })
}
