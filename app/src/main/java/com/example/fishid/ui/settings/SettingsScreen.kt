package com.example.fishid.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.fishid.R
import com.example.fishid.data.Catalog
import com.example.fishid.ml.Classifier
import com.example.fishid.ui.FishCard
import com.example.fishid.ui.IconBadge
import com.example.fishid.ui.LanguageSwitch
import com.example.fishid.ui.ThemeMode
import com.example.fishid.ui.TopBar
import com.example.fishid.ui.readableWidth
import com.example.fishid.ui.scan.ScanMode
import com.example.fishid.ui.scan.scanModeSetting
import com.example.fishid.ui.scan.setScanModeSetting
import com.example.fishid.ui.design
import com.example.fishid.ui.setDesign
import com.example.fishid.ui.setThemeMode
import com.example.fishid.ui.themeMode
import com.example.fishid.ui.theme.Design
import com.example.fishid.ui.theme.Market
import com.example.fishid.ui.theme.Sea

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val catalog = Catalog.get(context)
    val modelVersion by produceState<Int?>(null) { value = runCatching { Classifier.get(context).info.modelVersion }.getOrNull() }
    var theme by remember { mutableStateOf(context.themeMode()) }
    var scanMode by remember { mutableStateOf(context.scanModeSetting()) }
    var design by remember { mutableStateOf(context.design()) }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.settings_title), onBack = onBack)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.readableWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Design preview for the team: applies instantly. Remove once one design is chosen (see Design.kt).
                RadioCard(
                    R.drawable.ic_layers, R.string.settings_design,
                    listOf(Option(Design.SEA, R.string.design_sea), Option(Design.MARKET, R.string.design_market)),
                    selected = design,
                    trailing = { Swatches(it) },
                ) { design = it; context.setDesign(it) }
                FishCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(R.drawable.ic_globe, size = 36.dp)
                        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp).weight(1f))
                        LanguageSwitch()
                    }
                }
                RadioCard(
                    R.drawable.ic_sun, R.string.settings_theme,
                    listOf(
                        Option(ThemeMode.SYSTEM, R.string.theme_system, R.string.theme_system_description),
                        Option(ThemeMode.LIGHT, R.string.theme_light),
                        Option(ThemeMode.DARK, R.string.theme_dark),
                    ),
                    selected = theme,
                ) { theme = it; context.setThemeMode(it) }
                RadioCard(
                    R.drawable.ic_scan, R.string.settings_scan_mode,
                    listOf(
                        Option(ScanMode.AUTO, R.string.scan_mode_auto, R.string.scan_mode_auto_description),
                        Option(ScanMode.LIVE, R.string.scan_mode_live),
                        Option(ScanMode.TAP, R.string.scan_mode_tap),
                    ),
                    selected = scanMode,
                ) { scanMode = it; context.setScanModeSetting(it) }
                InfoCard(
                    R.drawable.ic_info, R.string.settings_updates,
                    listOfNotNull(
                        stringResource(R.string.settings_data_version, catalog.dataVersion),
                        modelVersion?.let { stringResource(R.string.settings_model_version, it) },
                    ),
                )
                InfoCard(R.drawable.ic_shield, R.string.settings_privacy, listOf(stringResource(R.string.privacy_body)))
                InfoCard(R.drawable.ic_fish, R.string.settings_about, listOf(stringResource(R.string.about_body)))
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

private data class Option<T>(val value: T, val label: Int, val description: Int? = null)

@Composable
private fun <T> RadioCard(
    icon: Int,
    title: Int,
    options: List<Option<T>>,
    selected: T,
    trailing: @Composable (T) -> Unit = {},
    onSelect: (T) -> Unit,
) {
    FishCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp).selectableGroup()) {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, size = 36.dp)
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
            }
            options.forEach { o ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .selectable(selected == o.value, role = Role.RadioButton) { onSelect(o.value) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == o.value, onClick = null)
                    Column(Modifier.weight(1f).padding(start = 8.dp, top = 6.dp, bottom = 6.dp)) {
                        Text(stringResource(o.label), style = MaterialTheme.typography.bodyLarge)
                        o.description?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    trailing(o.value)
                }
            }
        }
    }
}

@Composable
fun InfoCard(icon: Int, title: Int, lines: List<String>) {
    FishCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon, size = 36.dp)
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp))
            }
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** Background, action, accent and ink of a design: a glance at the palette next to its name. */
@Composable
private fun Swatches(design: Design) {
    val colors = when (design) {
        Design.SEA -> listOf(Sea.Sand, Sea.Ocean, Sea.Coral, Sea.Ink)
        Design.MARKET -> listOf(Market.Cream, Market.Turmeric, Market.BoatBlue, Market.Ink)
    }
    Row(Modifier.padding(end = 8.dp).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        colors.forEach { Box(Modifier.size(20.dp).clip(CircleShape).background(it).border(1.dp, Color.Black.copy(alpha = 0.2f), CircleShape)) }
    }
}
