package com.example.fishid.ui.scan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.fishid.ui.FishImage
import com.example.fishid.ui.TopBar
import com.example.fishid.ui.currentLang
import com.example.fishid.ui.imagePath
import com.example.fishid.ui.primaryName
import com.example.fishid.ui.secondaryName
import com.example.fishid.ui.theme.tamilLineHeight

/** Low confidence: up to three candidates to compare by eye (deck: field marks "used to visually confirm a match"). */
@Composable
fun CompareScreen(scanId: String, onBack: () -> Unit, onPicked: (speciesId: String) -> Unit, onNone: () -> Unit) {
    val context = LocalContext.current
    val lang = currentLang()
    val record = remember(scanId) { History.get(context, scanId) }
    if (record == null) { LaunchedEffect(Unit) { onBack() }; return }
    val catalog = Catalog.get(context)
    val candidates = remember(scanId, lang) { record.candidates.mapNotNull { c -> catalog.entry(c.speciesId, lang)?.let { it to c.confidence } } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), horizontalAlignment = Alignment.CenterHorizontally) {
        TopBar("", onBack = onBack)
        Column(Modifier.weight(1f).readableWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FishImage(record.photo, stringResource(R.string.label_your_photo), Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)), maxPx = 300)
                Text(stringResource(R.string.low_confidence_title), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            }
            Text(stringResource(R.string.low_confidence_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            candidates.forEachIndexed { i, (entry, confidence) ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(if (i == 0) 1.5.dp else 1.dp, if (i == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FishImage(entry.imagePath(), stringResource(R.string.label_reference_photo), Modifier.size(104.dp, 78.dp).clip(RoundedCornerShape(10.dp)), maxPx = 400)
                            Column(Modifier.weight(1f)) {
                                Row {
                                    Text(entry.primaryName(), style = tamilLineHeight(MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)), modifier = Modifier.weight(1f))
                                    Text("${(confidence * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                entry.secondaryName()?.let { Text(it, style = tamilLineHeight(MaterialTheme.typography.bodyMedium), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
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
                        if (i == 0) {
                            Button(onClick = pick, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp)) { Text(stringResource(R.string.low_confidence_pick)) }
                        } else {
                            FilledTonalButton(onClick = pick, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp)) { Text(stringResource(R.string.low_confidence_pick)) }
                        }
                    }
                }
            }
        }
        OutlinedButton(
            onClick = onNone,
            modifier = Modifier.readableWidth().padding(16.dp).navigationBarsPadding().heightIn(min = 56.dp),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        ) { Text(stringResource(R.string.low_confidence_none)) }
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
                Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp))) {
                    FishImage(it.photo, stringResource(R.string.label_your_photo), Modifier.fillMaxSize(), maxPx = 800)
                }
            }
            Text(stringResource(R.string.no_match_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 24.dp, bottom = 12.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    tips.forEachIndexed { i, (icon, text) ->
                        Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            }
                            Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
                        }
                        if (i < tips.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
        Column(Modifier.readableWidth().padding(16.dp).navigationBarsPadding()) {
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.action_retry)) }
            if (record != null) TextButton(onClick = onReport, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 52.dp)) { Text(stringResource(R.string.profile_report_link)) }
        }
    }
}
