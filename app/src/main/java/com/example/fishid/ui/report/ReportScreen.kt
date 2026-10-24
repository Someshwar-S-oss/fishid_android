package com.example.fishid.ui.report

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import com.example.fishid.R
import androidx.compose.ui.Alignment
import com.example.fishid.ui.readableWidth
import com.example.fishid.data.Catalog
import com.example.fishid.data.History
import com.example.fishid.data.Report
import com.example.fishid.data.Reports
import com.example.fishid.ml.Classifier
import com.example.fishid.ui.FishImage
import com.example.fishid.ui.PrimaryButton
import com.example.fishid.ui.theme.FishTheme
import com.example.fishid.ui.TopBar
import com.example.fishid.ui.currentLang
import com.example.fishid.ui.primaryName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val DONT_KNOW = ""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(scanId: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val lang = currentLang()
    val record = remember(scanId) { History.get(context, scanId) }
    if (record == null) { LaunchedEffect(Unit) { onDone() }; return }
    val catalog = Catalog.get(context)
    val tamil = lang == "ta"
    val species = remember(lang) { catalog.entries(lang).sortedBy { it.primaryName(tamil) } }
    val guessId = record.speciesId ?: record.candidates.firstOrNull()?.speciesId
    val guess = guessId?.let { catalog.entry(it, lang) }
    val guessConfidence = record.candidates.firstOrNull { it.speciesId == guessId }?.confidence

    var answer by rememberSaveable { mutableStateOf<String?>(null) } // null = not chosen yet, "" = don't know
    var place by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val thanks = stringResource(R.string.report_saved)
    val fieldColors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface)

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
        TopBar(stringResource(R.string.report_title), onBack = onDone)
        Column(Modifier.weight(1f).readableWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(FishTheme.style.cardRadius))) {
                FishImage(record.photo, stringResource(R.string.label_your_photo), Modifier.fillMaxSize(), maxPx = 800)
            }
            if (guess != null) {
                Column {
                    Text(stringResource(R.string.report_our_guess), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        guessConfidence?.let { stringResource(R.string.report_guess_value, guess.primaryName(), (it * 100).toInt()) } ?: guess.primaryName(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            Text(stringResource(R.string.report_your_answer), style = MaterialTheme.typography.titleMedium)
            ExposedDropdownMenuBox(expanded = menu, onExpandedChange = { menu = it }) {
                val label = when (answer) {
                    null -> ""
                    DONT_KNOW -> stringResource(R.string.report_dont_know)
                    else -> species.firstOrNull { it.id == answer }?.primaryName().orEmpty()
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = {},
                    readOnly = true,
                    placeholder = { Text(stringResource(R.string.report_pick_species)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menu) },
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    species.forEach { e ->
                        DropdownMenuItem(text = { Text(e.primaryName()) }, onClick = { answer = e.id; menu = false }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.report_dont_know)) }, onClick = { answer = DONT_KNOW; menu = false }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            OutlinedTextField(place, { place = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.report_place_hint)) }, shape = RoundedCornerShape(12.dp), colors = fieldColors)
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth().heightIn(min = 112.dp), label = { Text(stringResource(R.string.report_note_hint)) }, shape = RoundedCornerShape(12.dp), colors = fieldColors)
            Text(stringResource(R.string.report_privacy), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PrimaryButton(
            stringResource(R.string.report_submit),
            enabled = answer != null,
            icon = R.drawable.ic_check,
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
                        Reports.save(
                            context,
                            Report(
                                id = UUID.randomUUID().toString(),
                                timeMs = System.currentTimeMillis(),
                                appVersion = PackageInfoCompat.getLongVersionCode(pi),
                                modelVersion = runCatching { Classifier.get(context).info.modelVersion }.getOrNull(),
                                dataVersion = catalog.dataVersion,
                                guessSpeciesId = guessId,
                                guessConfidence = guessConfidence,
                                answerSpeciesId = answer?.takeIf { it != DONT_KNOW },
                                place = place.trim(),
                                note = note.trim(),
                            ),
                            record.photo,
                        )
                    }
                    Toast.makeText(context, thanks, Toast.LENGTH_LONG).show()
                    onDone()
                }
            },
            modifier = Modifier.readableWidth().padding(16.dp).navigationBarsPadding(),
        )
    }
}
