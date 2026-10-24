package com.example.fishid.ui.history

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.fishid.R
import com.example.fishid.ui.readableWidth
import com.example.fishid.data.Catalog
import com.example.fishid.data.History
import com.example.fishid.data.ScanRecord
import com.example.fishid.ui.FishCard
import com.example.fishid.ui.FishImage
import com.example.fishid.ui.IconBadge
import com.example.fishid.ui.LanguageSwitch
import com.example.fishid.ui.TopBar
import com.example.fishid.ui.currentLang
import com.example.fishid.ui.primaryName
import com.example.fishid.ui.theme.FishTheme
import com.example.fishid.ui.theme.tamilLineHeight
import java.util.Calendar

@Composable
fun HistoryScreen(onOpen: (ScanRecord) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val records by remember { History.flow(context) }.collectAsState()
    val list = records.orEmpty()
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.history_title)) {
            if (list.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text(stringResource(R.string.history_clear_all)) }
            LanguageSwitch()
            IconButton(onClick = onSettings) { Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.action_settings)) }
        }
        if (list.isEmpty()) EmptyHistory() else HistoryList(list, onOpen)
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        text = { Text(stringResource(R.string.history_clear_confirm), style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { TextButton(onClick = { History.clear(context); confirmClear = false }) { Text(stringResource(R.string.history_clear_all)) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private fun dayStart(ms: Long) = Calendar.getInstance().apply {
    timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
private fun HistoryList(list: List<ScanRecord>, onOpen: (ScanRecord) -> Unit) {
    val context = LocalContext.current
    val today = dayStart(System.currentTimeMillis())
    val groups = list.groupBy { dayStart(it.timeMs) }
    LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        groups.forEach { (day, items) ->
            item(key = "day-$day") {
                val label = when (day) {
                    today -> stringResource(R.string.history_today)
                    today - DateUtils.DAY_IN_MILLIS -> stringResource(R.string.history_yesterday)
                    else -> DateUtils.formatDateTime(context, day, DateUtils.FORMAT_SHOW_DATE)
                }
                Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.readableWidth().padding(top = 12.dp))
            }
            items(items, key = { it.id }) { HistoryRow(it, onOpen) }
        }
    }
}

@Composable
private fun HistoryRow(record: ScanRecord, onOpen: (ScanRecord) -> Unit) {
    val context = LocalContext.current
    val lang = currentLang()
    val entry = remember(record.speciesId, lang) { record.speciesId?.let { Catalog.get(context).entry(it, lang) } }
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        modifier = Modifier.readableWidth(),
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.EndToStart) History.delete(context, record.id) },
        backgroundContent = {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(FishTheme.style.cardRadius)).background(MaterialTheme.colorScheme.error).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(painterResource(R.drawable.ic_trash), stringResource(R.string.history_delete), tint = MaterialTheme.colorScheme.onError)
            }
        },
    ) {
        FishCard(onClick = { onOpen(record) }) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box {
                    FishImage(record.photo, stringResource(R.string.label_your_photo), Modifier.size(68.dp).clip(RoundedCornerShape(12.dp)), maxPx = 240)
                    OutcomeMark(record, Modifier.align(Alignment.BottomEnd).offset(6.dp, 6.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(entry?.primaryName() ?: stringResource(R.string.history_not_identified), style = tamilLineHeight(MaterialTheme.typography.titleMedium))
                    val time = DateFormat.getTimeFormat(context).format(record.timeMs)
                    val pct = record.confidence?.let { stringResource(R.string.result_confidence, (it * 100).toInt()) }
                    Text(listOfNotNull(pct, time).joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** ✓ identified · ? not sure · ✕ not identified, in a ringed dot on the photo corner. Shape and colour both differ. */
@Composable
private fun OutcomeMark(record: ScanRecord, modifier: Modifier = Modifier) {
    val c = FishTheme.colors
    val (bg, fg) = when {
        record.speciesId != null -> c.sureContainer to c.onSureContainer
        record.outcome == ScanRecord.Outcome.UNSURE -> c.likelyContainer to c.onLikelyContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier.size(26.dp).clip(CircleShape).background(bg).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            record.speciesId != null -> Icon(painterResource(R.drawable.ic_check), null, tint = fg, modifier = Modifier.size(15.dp))
            record.outcome == ScanRecord.Outcome.UNSURE -> Text("?", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = fg)
            else -> Icon(painterResource(R.drawable.ic_close), null, tint = fg, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun EmptyHistory() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(R.drawable.ic_history, size = 96.dp)
        Text(stringResource(R.string.history_empty_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        Text(stringResource(R.string.history_empty_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
