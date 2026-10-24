package com.example.fishid.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fishid.R
import com.example.fishid.ui.readableWidth
import com.example.fishid.data.Catalog
import com.example.fishid.data.History
import com.example.fishid.data.ScanRecord
import com.example.fishid.ml.Classifier
import com.example.fishid.ml.matchBars
import com.example.fishid.ui.FishCard
import com.example.fishid.ui.FishImage
import com.example.fishid.ui.IconBadge
import com.example.fishid.ui.LinkButton
import com.example.fishid.ui.MatchChip
import com.example.fishid.ui.Pill
import com.example.fishid.ui.PrimaryButton
import com.example.fishid.ui.SecondaryButton
import com.example.fishid.ui.TopBar
import com.example.fishid.ui.currentLang
import com.example.fishid.ui.imagePath
import com.example.fishid.ui.primaryName
import com.example.fishid.ui.secondaryName
import com.example.fishid.ui.theme.FishTheme
import com.example.fishid.ui.theme.tamilLineHeight

/**
 * Low confidence: up to three candidates to compare by eye (deck: field marks "used to visually confirm a match").
 * The user's photo stays pinned at the top while the candidates scroll, so each one is compared against it.
 */
@Composable
fun CompareScreen(scanId: String, onBack: () -> Unit, onPicked: (speciesId: String) -> Unit, onNone: () -> Unit) {
    val context = LocalContext.current
    val lang = currentLang()
    val record = remember(scanId) { History.get(context, scanId) }
    if (record == null) { LaunchedEffect(Unit) { onBack() }; return }
    val catalog = Catalog.get(context)
    val candidates = remember(scanId, lang) { record.candidates.mapNotNull { c -> catalog.entry(c.speciesId, lang)?.let { it to c.confidence } } }
    val defaultThreshold by produceState(0.7f) { value = runCatching { Classifier.get(context).info.defaultThreshold }.getOrDefault(value) }
    val style = FishTheme.style

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), horizontalAlignment = Alignment.CenterHorizontally) {
        TopBar("", onBack = onBack)
        Box(Modifier.readableWidth().padding(horizontal = 16.dp).height(140.dp).clip(RoundedCornerShape(style.cardRadius))) {
            FishImage(record.photo, stringResource(R.string.label_your_photo), Modifier.fillMaxSize(), maxPx = 800)
            Pill(stringResource(R.string.label_your_photo), Color(0xCC000000), Color.White, Modifier.align(Alignment.BottomStart).padding(10.dp))
        }
        Column(
            Modifier.weight(1f).readableWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.low_confidence_title), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                Text(stringResource(R.string.low_confidence_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            candidates.forEachIndexed { i, (entry, confidence) ->
                val threshold = entry.species.confidenceThreshold ?: defaultThreshold
                FishCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        FishImage(
                            entry.imagePath(), stringResource(R.string.label_reference_photo),
                            Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(style.cardRadius - 6.dp)), maxPx = 640,
                        )
                        Text(entry.primaryName(), style = tamilLineHeight(MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)), modifier = Modifier.padding(top = 12.dp))
                        entry.secondaryName()?.let { Text(it, style = tamilLineHeight(MaterialTheme.typography.bodyMedium), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        MatchChip(matchBars(confidence, threshold), (confidence * 100).toInt(), Modifier.padding(top = 8.dp))
                        entry.fieldMarks.take(2).forEach { mark ->
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(painterResource(R.drawable.ic_check), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text(mark, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        val pick = {
                            History.update(context, scanId) { it.copy(speciesId = entry.id, pickedByUser = true) }
                            onPicked(entry.id)
                        }
                        // The model's best guess gets the main button; the rest stay quieter.
                        if (i == 0) {
                            PrimaryButton(stringResource(R.string.low_confidence_pick), pick, Modifier.padding(top = 12.dp), icon = R.drawable.ic_check)
                        } else {
                            SecondaryButton(stringResource(R.string.low_confidence_pick), pick, Modifier.padding(top = 12.dp), icon = R.drawable.ic_check)
                        }
                    }
                }
            }
        }
        Column(Modifier.readableWidth().padding(16.dp).navigationBarsPadding()) {
            SecondaryButton(stringResource(R.string.low_confidence_none), onNone, icon = R.drawable.ic_close)
        }
    }
}

@Composable
fun NoMatchScreen(scanId: String, onBack: () -> Unit, onReport: () -> Unit) {
    val context = LocalContext.current
    val record: ScanRecord? = remember(scanId) { History.get(context, scanId) }
    val tips = listOf(
        R.drawable.ic_scan to R.string.no_match_tip_whole,
        R.drawable.ic_fish to R.string.no_match_tip_side,
        R.drawable.ic_sun to R.string.no_match_tip_light,
        R.drawable.ic_frame to R.string.no_match_tip_background,
    )
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), horizontalAlignment = Alignment.CenterHorizontally) {
        TopBar("", onBack = onBack)
        Column(Modifier.weight(1f).readableWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            record?.let {
                Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(FishTheme.style.cardRadius))) {
                    FishImage(it.photo, stringResource(R.string.label_your_photo), Modifier.fillMaxSize(), maxPx = 800)
                }
            }
            Text(stringResource(R.string.no_match_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
            FishCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    tips.forEachIndexed { i, (icon, text) ->
                        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            IconBadge(icon)
                            Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
                        }
                        if (i < tips.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
        Column(Modifier.readableWidth().padding(16.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            PrimaryButton(stringResource(R.string.action_retry), onBack, icon = R.drawable.ic_scan)
            if (record != null) LinkButton(stringResource(R.string.profile_report_link), onReport, Modifier.padding(top = 6.dp), icon = R.drawable.ic_flag)
        }
    }
}
