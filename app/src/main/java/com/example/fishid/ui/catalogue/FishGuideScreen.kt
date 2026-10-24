package com.example.fishid.ui.catalogue

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.fishid.R
import com.example.fishid.ui.theme.ScientificStyle
import com.example.fishid.ui.readableWidth
import com.example.fishid.data.Catalog
import com.example.fishid.data.FishEntry
import com.example.fishid.ui.FishCard
import com.example.fishid.ui.FishImage
import com.example.fishid.ui.LanguageSwitch
import com.example.fishid.ui.PriceTag
import com.example.fishid.ui.TopBar
import com.example.fishid.ui.currentLang
import com.example.fishid.ui.imagePath
import com.example.fishid.ui.primaryName
import com.example.fishid.ui.secondaryName
import com.example.fishid.ui.theme.FishTheme
import com.example.fishid.ui.theme.tamilLineHeight
import java.text.Collator
import java.util.Locale

private enum class Sort(val label: Int) { Name(R.string.sort_name), PriceLow(R.string.sort_price_low), PriceHigh(R.string.sort_price_high) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FishGuideScreen(onOpen: (String) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val lang = currentLang()
    val all = remember(lang) { Catalog.get(context).entries(lang) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(Sort.Name) }
    val tamil = lang == "ta"
    val collator = remember(lang) { Collator.getInstance(Locale.forLanguageTag(lang)) }
    val shown = all.filter { it.matches(query) }.let { list ->
        when (sort) {
            Sort.Name -> list.sortedWith(compareBy(collator) { it.primaryName(tamil) })
            // Species without a price go last in both directions.
            Sort.PriceLow -> list.sortedWith(compareBy(nullsLast<Int>()) { it.species.priceInrPerKg })
            Sort.PriceHigh -> list.sortedWith(compareBy(nullsLast(reverseOrder<Int>())) { it.species.priceInrPerKg })
        }
    }
    val style = FishTheme.style

    Column(Modifier.fillMaxSize()) {
        TopBar(stringResource(R.string.catalogue_title)) {
            LanguageSwitch()
            IconButton(onClick = onSettings) { Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.action_settings)) }
        }
        LazyVerticalGrid(
            // Sea: two photo cards per row (photos lead, recognition over reading), one at large text sizes so names
            // don't split mid-word. Market: one signboard row per fish.
            columns = GridCells.Adaptive(minSize = if (style.isMarket) 300.dp else 150.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)),
            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(if (style.isMarket) 14.dp else 12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.readableWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        singleLine = true,
                        // A wrapping hint makes the single-line field two lines tall; shrink to fit instead.
                        placeholder = { Text(stringResource(R.string.catalogue_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis, autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = MaterialTheme.typography.bodyLarge.fontSize)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                        trailingIcon = if (query.isNotEmpty()) {
                            { IconButton(onClick = { query = "" }) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_cancel)) } }
                        } else null,
                        shape = RoundedCornerShape(if (style.isMarket) 12.dp else 28.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = if (style.isMarket) style.colors.border else MaterialTheme.colorScheme.outline,
                        ),
                    )
                    // One even row in both languages: chips wrapped or scrolled off-screen with long labels.
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        Sort.entries.forEachIndexed { i, s ->
                            SegmentedButton(
                                selected = sort == s,
                                onClick = { sort = s },
                                shape = SegmentedButtonDefaults.itemShape(i, Sort.entries.size),
                                icon = {}, // the checkmark would steal width from the label
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = if (style.isMarket) style.colors.cta else MaterialTheme.colorScheme.primaryContainer,
                                    activeContentColor = if (style.isMarket) style.colors.onCta else MaterialTheme.colorScheme.onPrimaryContainer,
                                    inactiveContainerColor = MaterialTheme.colorScheme.surface,
                                    activeBorderColor = if (style.isMarket) style.colors.border else MaterialTheme.colorScheme.outline,
                                    inactiveBorderColor = if (style.isMarket) style.colors.border else MaterialTheme.colorScheme.outline,
                                ),
                                // Tight padding + 13sp fits "குறைந்த விலை" in a third of a phone width. No autoSize: the
                                // segment measures intrinsically and an auto-sized label wrapped its second word out of sight.
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Text(
                                    stringResource(s.label), style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, fontWeight = if (sort == s) FontWeight.Bold else FontWeight.Medium),
                                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Text(
                        if (shown.isEmpty()) stringResource(R.string.catalogue_no_results, query) else stringResource(R.string.catalogue_count, shown.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(shown, key = { it.id }) {
                if (style.isMarket) SpeciesRow(it) { onOpen(it.id) } else SpeciesCard(it) { onOpen(it.id) }
            }
        }
    }
}

/** Sea: photo-first card, price pill resting on the photo. */
@Composable
private fun SpeciesCard(entry: FishEntry, onClick: () -> Unit) {
    FishCard(onClick = onClick) {
        Box {
            FishImage(entry.imagePath(), null, Modifier.fillMaxWidth().aspectRatio(1.25f), maxPx = 480)
            entry.species.priceInrPerKg?.let { PriceTag(it, Modifier.align(Alignment.BottomStart).padding(8.dp)) }
        }
        Column(Modifier.padding(12.dp, 10.dp, 12.dp, 14.dp)) {
            Text(entry.primaryName(), style = tamilLineHeight(MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)))
            Names(entry)
        }
    }
}

/** Market: a full-width row like a stall signboard: framed thumbnail, big name, hanging price tag. */
@Composable
private fun SpeciesRow(entry: FishEntry, onClick: () -> Unit) {
    val c = FishTheme.colors
    FishCard(onClick = onClick) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            val frame = RoundedCornerShape(10.dp)
            FishImage(entry.imagePath(), null, Modifier.size(width = 120.dp, height = 90.dp).clip(frame).border(2.dp, c.border, frame), maxPx = 360)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(entry.primaryName(), style = tamilLineHeight(MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)))
                Names(entry)
                entry.species.priceInrPerKg?.let { PriceTag(it, Modifier.padding(top = 8.dp)) }
            }
            Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** The other-language name, or the scientific name when there is none. */
@Composable
private fun Names(entry: FishEntry) {
    val second = entry.secondaryName()
    if (second != null) {
        Text(second, style = tamilLineHeight(MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal, fontSize = 14.sp)), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else if (entry.commonName != entry.species.scientificName) {
        Text(entry.species.scientificName, style = MaterialTheme.typography.labelMedium.merge(ScientificStyle), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
