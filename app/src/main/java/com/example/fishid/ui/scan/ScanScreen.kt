package com.example.fishid.ui.scan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import android.view.HapticFeedbackConstants
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
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.fishid.R
import com.example.fishid.data.Catalog
import com.example.fishid.data.ScanRecord
import com.example.fishid.ml.Classifier
import com.example.fishid.ml.isHighMatch
import com.example.fishid.ui.FishImage
import com.example.fishid.ui.LanguageSwitch
import com.example.fishid.ui.NameStack
import com.example.fishid.ui.Pill
import com.example.fishid.ui.ScrollFill
import androidx.compose.foundation.layout.height
import com.example.fishid.ui.currentLang
import com.example.fishid.ui.formatRupees
import com.example.fishid.ui.habitatRes
import com.example.fishid.ui.codeLabel
import com.example.fishid.ui.imagePath
import com.example.fishid.ui.onboarding.Brackets
import com.example.fishid.ui.theme.CameraScrim
import com.example.fishid.ui.theme.Ink
import com.example.fishid.ui.theme.LockGreen
import com.example.fishid.ui.theme.Marigold
import com.example.fishid.ui.theme.MarigoldInk
import com.example.fishid.ui.theme.MarigoldTint
import com.example.fishid.ui.theme.NightPrimary
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

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        var camera by remember { mutableStateOf<Camera?>(null) }
        var cameraFailed by remember { mutableStateOf(false) }
        CameraPreview(engine, onCamera = { camera = it }, onError = { cameraFailed = true })

        val bracketColor by animateColorAsState(
            when {
                engine.result != null -> LockGreen
                !engine.live -> Color.White
                else -> Marigold
            },
            tween(200), label = "brackets",
        )
        // Same shape and size as the area the model sees (ScanEngine.bracketArea).
        Box(Modifier.fillMaxWidth(BRACKET_FRACTION).widthIn(max = 640.dp).aspectRatio(BRACKET_ASPECT).align(Alignment.Center)) {
            Brackets(bracketColor, inset = 0.dp)
        }

        Column(Modifier.fillMaxSize()) {
            CameraTopBar(onSettings)
            ModeChip(engine.live)
            Spacer(Modifier.weight(1f))
            if (engine.switchedToTap && !context.prefs().getBoolean("tap_notice_shown", false)) {
                TapNotice(onSettings = { context.prefs().edit().putBoolean("tap_notice_shown", true).apply(); engine.switchedToTap = false; onSettings() }) {
                    context.prefs().edit().putBoolean("tap_notice_shown", true).apply()
                    engine.switchedToTap = false
                }
            }
            val hint = when {
                cameraFailed -> R.string.scan_camera_error
                engine.identifying -> R.string.scan_identifying
                !engine.live -> R.string.scan_hint_tap
                engine.holding -> R.string.scan_hint_hold_still
                else -> R.string.scan_hint_frame
            }
            HintChip(stringResource(hint))
            Controls(
                live = engine.live,
                identifying = engine.identifying,
                hasFlash = camera?.cameraInfo?.hasFlashUnit() == true,
                onGallery = pickPhoto,
                onShutter = engine::shutter,
                onFlash = { on -> camera?.cameraControl?.enableTorch(on) },
            )
        }

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
                ResultCard(r, engine::thresholdFor, onDetails = { onProfile(r.speciesId!!, r.id) }, onAgain = engine::scanAgain, onReport = { onReport(r.id) })
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
        Modifier.fillMaxWidth().background(CameraScrim).statusBarsPadding().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
        LanguageSwitch(onDark = true)
        IconButton(onClick = onSettings) { Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.action_settings), tint = Color.White) }
    }
}

@Composable
private fun ModeChip(live: Boolean) {
    Surface(Modifier.padding(16.dp), shape = RoundedCornerShape(50), color = CameraScrim, contentColor = Color.White) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (live) LockGreen else Color.White))
            Text(stringResource(if (live) R.string.scan_mode_live else R.string.scan_mode_tap), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun HintChip(text: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(50), color = Color(0xB8080F1A), contentColor = Color.White, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Text(text, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun TapNotice(onSettings: () -> Unit, onDismiss: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(12.dp), color = Ink, contentColor = Color.White, shadowElevation = 6.dp) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.scan_switched_to_tap), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
            TextButton(onClick = onSettings) { Text(stringResource(R.string.action_settings), color = NightPrimary) }
            IconButton(onClick = onDismiss) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_done), tint = Color.White) }
        }
    }
}

@Composable
private fun Controls(live: Boolean, identifying: Boolean, hasFlash: Boolean, onGallery: () -> Unit, onShutter: () -> Unit, onFlash: (Boolean) -> Unit) {
    var torch by remember { mutableStateOf(false) }
    val shutterLabel = stringResource(R.string.scan_shutter_description)
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassButton(R.drawable.ic_gallery, stringResource(R.string.scan_gallery_description), onGallery)
        Surface(
            onClick = onShutter,
            enabled = !identifying,
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(4.dp, Color.White),
            modifier = Modifier.size(76.dp).semantics { contentDescription = shutterLabel },
        ) {
            Box(Modifier.padding(6.dp).clip(CircleShape).background(if (live) Color.White.copy(alpha = 0.35f) else Color.White), contentAlignment = Alignment.Center) {
                if (identifying) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
            }
        }
        if (hasFlash) {
            GlassButton(
                R.drawable.ic_flash,
                stringResource(if (torch) R.string.scan_flash_off_description else R.string.scan_flash_on_description),
                { torch = !torch; onFlash(torch) },
                active = torch,
            )
        } else {
            Spacer(Modifier.width(56.dp))
        }
    }
}

@Composable
private fun GlassButton(icon: Int, description: String, onClick: () -> Unit, active: Boolean = false) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) Color.White else CameraScrim,
        contentColor = if (active) Ink else Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
        modifier = Modifier.size(56.dp),
    ) { Box(contentAlignment = Alignment.Center) { Icon(painterResource(icon), description) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultCard(record: ScanRecord, thresholdFor: (String) -> Float, onDetails: () -> Unit, onAgain: () -> Unit, onReport: () -> Unit) {
    val context = LocalContext.current
    val lang = currentLang()
    val entry = remember(record.id, lang) { record.speciesId?.let { Catalog.get(context).entry(it, lang) } } ?: return
    val confidence = record.confidence ?: 0f
    val high = isHighMatch(confidence, thresholdFor(entry.id))
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp, 12.dp, 16.dp, 20.dp)) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 5.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.outline))
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FishImage(entry.imagePath(), stringResource(R.string.label_reference_photo), Modifier.size(84.dp).clip(RoundedCornerShape(16.dp)), maxPx = 300)
                NameStack(entry, MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium), MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal), Modifier.weight(1f))
            }
            Row(Modifier.padding(top = 18.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val label = stringResource(if (high) R.string.result_match_high else R.string.result_match_likely)
                Pill(
                    stringResource(R.string.result_match_percent, label, (confidence * 100).toInt()),
                    if (high) MaterialTheme.colorScheme.primaryContainer else MarigoldTint,
                    if (high) MaterialTheme.colorScheme.onPrimaryContainer else MarigoldInk,
                )
                Spacer(Modifier.weight(1f))
                entry.species.priceInrPerKg?.let {
                    Text(stringResource(R.string.price_per_kg, formatRupees(it)), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
            }
            val chips = listOfNotNull(entry.fieldMarks.firstOrNull(), entry.species.habitat.firstOrNull()?.let { codeLabel(it, ::habitatRes) })
            if (chips.isNotEmpty()) FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { Pill(it, MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.onSurface, border = MaterialTheme.colorScheme.outline) }
            }
            Button(onClick = onDetails, modifier = Modifier.fillMaxWidth().padding(top = 18.dp).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.result_view_details))
                Icon(painterResource(R.drawable.ic_chevron_right), null, modifier = Modifier.padding(start = 6.dp).size(22.dp))
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onAgain, modifier = Modifier.weight(1f).heightIn(min = 52.dp), border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)) {
                    Text(stringResource(R.string.result_scan_again), textAlign = TextAlign.Center)
                }
                TextButton(onClick = onReport, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.profile_report_link), textAlign = TextAlign.Center) }
            }
        }
    }
}

@Composable
private fun PermissionRationale(onAllow: () -> Unit, onGallery: () -> Unit) {
    ScrollFill(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(16.dp), top = {
        Spacer(Modifier.height(64.dp))
        Box(Modifier.align(Alignment.CenterHorizontally).size(112.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_camera), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        }
        Text(stringResource(R.string.permission_camera_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 12.dp))
        Text(stringResource(R.string.permission_camera_body), style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }, bottom = {
        Button(onClick = onAllow, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.permission_allow)) }
        TextButton(onClick = onGallery, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 56.dp)) { Text(stringResource(R.string.permission_use_gallery)) }
    })
}
