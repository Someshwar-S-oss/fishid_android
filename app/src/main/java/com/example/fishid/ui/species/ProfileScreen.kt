package com.example.fishid.ui.species

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import com.example.fishid.R
import com.example.fishid.data.Catalog
import com.example.fishid.data.FishEntry
import com.example.fishid.data.Nutrition
import com.example.fishid.ui.FishCard
import com.example.fishid.ui.FishImage
import com.example.fishid.ui.IucnBadge
import com.example.fishid.ui.NameStack
import com.example.fishid.ui.Pill
import com.example.fishid.ui.codeLabel
import com.example.fishid.ui.currentLang
import com.example.fishid.ui.feedingRes
import com.example.fishid.ui.formatGrams
import com.example.fishid.ui.formatRupees
import com.example.fishid.ui.habitatRes
import com.example.fishid.ui.imagePath
import com.example.fishid.ui.primaryName
import com.example.fishid.ui.verticalRes
import com.example.fishid.ui.IconBadge
import com.example.fishid.ui.PriceTag
import com.example.fishid.ui.SecondaryButton
import com.example.fishid.ui.theme.FishTheme
import com.example.fishid.ui.theme.Sea
import com.example.fishid.ui.theme.ScientificStyle
import com.example.fishid.ui.theme.tamilLineHeight
import kotlinx.coroutines.launch
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.asPaddingValues
import com.example.fishid.ui.StatusBarIcons
import com.example.fishid.ui.readableWidth

private const val COLLAPSED_FEATURES = 5

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    speciesId: String,
    userPhoto: String?,
    onBack: () -> Unit,
    onReport: (() -> Unit)?, // null when not opened from a scan: a report needs the user's photo
) {
    val context = LocalContext.current
    val lang = currentLang()
    val entry = remember(speciesId, lang) { Catalog.get(context).entry(speciesId, lang) }
    if (entry == null) {
        LaunchedEffect(Unit) { onBack() } // id no longer in the data (e.g. after a data update)
        return
    }
    val s = entry.species

    // Only sections with data get a tab: species still waiting for the professor's sheet show just what exists.
    val sections = buildList<Pair<Int, @Composable () -> Unit>> {
        add(R.string.tab_overview to @Composable { Section { Overview(entry) } })
        s.nutritionG?.let { n -> add(R.string.tab_nutrition to @Composable { Section(R.string.nutrition_title) { NutritionCard(n) } }) }
        if (entry.fieldMarks.isNotEmpty() || s.meristics != null) {
            add(R.string.tab_identify to @Composable { Section(R.string.morphology_field_marks.takeIf { entry.fieldMarks.isNotEmpty() }) { Identify(entry) } })
        }
        if (entry.distribution != null || s.habitat.isNotEmpty() || s.feeding != null || s.vertical != null || s.depthM != null || s.iucn != null) {
            add(R.string.tab_habitat to @Composable { Section(R.string.ecology_title) { Ecology(entry) } })
        }
    }
    val showTabs = sections.size > 1
    val firstSection = if (showTabs) 3 else 2 // list items: 0 photos, 1 names, (2 sticky tabs), then sections

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val headerPx = with(LocalDensity.current) { 52.dp.roundToPx() }
    // The section whose top has reached the bottom of the stuck tab bar; the last one once the list can't scroll.
    val selectedTab by remember(sections.size) {
        derivedStateOf {
            if (!listState.canScrollForward && listState.firstVisibleItemIndex >= firstSection) return@derivedStateOf sections.lastIndex
            val reached = listState.layoutInfo.visibleItemsInfo
                .filter { it.index in firstSection until firstSection + sections.size && it.offset <= headerPx + 8 }
                .maxOfOrNull { it.index }
            ((reached ?: firstSection) - firstSection).coerceIn(0, sections.lastIndex)
        }
    }
    val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }
    val shareText = stringResource(R.string.profile_share_text, entry.primaryName(), s.scientificName)
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Light icons over the dark photo band, theme-coloured icons once the white tabs reach the top.
    StatusBarIcons(light = atTop || isSystemInDarkTheme())

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(top = statusBar),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { PhotoHeader(entry, userPhoto, onBack, onShare = {
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shareText), null))
            }) }

            item {
                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.readableWidth().padding(16.dp, 20.dp, 16.dp, 16.dp)) {
                        NameStack(entry, MaterialTheme.typography.headlineMedium, MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium), MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Normal))
                        if (s.iucn != null || s.priceInrPerKg != null || s.habitat.isNotEmpty()) {
                            FlowRow(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                s.iucn?.let { IucnBadge(it) }
                                s.priceInrPerKg?.let { PriceTag(it) }
                                s.habitat.firstOrNull()?.let { Pill(codeLabel(it, ::habitatRes), MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.onSurface, border = MaterialTheme.colorScheme.outline) }
                            }
                        }
                    }
                }
            }

            if (showTabs) stickyHeader {
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 8.dp,
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    sections.forEachIndexed { i, (label, _) ->
                        Tab(
                            selected = selectedTab == i,
                            onClick = { scope.launch { listState.animateScrollToItem(firstSection + i, -headerPx) } },
                            text = { Text(stringResource(label), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)) },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.heightIn(min = 52.dp),
                        )
                    }
                }
            }

            sections.forEach { (_, content) -> item { content() } }
            item { Footer(entry, onReport) }
        }
        // Status-bar band: matches the photo at the top, the tab bar once scrolled.
        Box(Modifier.fillMaxWidth().height(statusBar).background(if (atTop) FishTheme.colors.photoBackdrop else MaterialTheme.colorScheme.surface))
    }
}

/** Sea: the white content sheet rises over the photo with rounded corners. Market: a hard ink rule under the photo. */
private val SheetLip = 24.dp

@Composable
private fun PhotoHeader(entry: FishEntry, userPhoto: String?, onBack: () -> Unit, onShare: () -> Unit) {
    val style = FishTheme.style
    val images = entry.species.images.indices.map { entry.imagePath(it) }
    var showUser by rememberSaveable { mutableStateOf(userPhoto != null) }
    val pager = rememberPagerState { images.size.coerceAtLeast(1) }
    val lip = if (style.isMarket) 0.dp else SheetLip
    Box(Modifier.fillMaxWidth().height(if (style.isMarket) 280.dp else 300.dp).background(style.colors.photoBackdrop)) {
        if (showUser && userPhoto != null) {
            FishImage(userPhoto, stringResource(R.string.label_your_photo), Modifier.fillMaxSize())
        } else {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                FishImage(images.getOrNull(page), stringResource(R.string.label_reference_photo) + ": " + entry.commonName, Modifier.fillMaxSize())
            }
            if (images.size > 1) {
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = lip + if (userPhoto != null) 62.dp else 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(images.size) { i ->
                        Box(Modifier.size(if (i == pager.currentPage) 20.dp else 8.dp, 8.dp).clip(CircleShape).background(if (i == pager.currentPage) Color.White else Color.White.copy(alpha = 0.5f)))
                    }
                }
            }
        }
        RoundIconButton(R.drawable.ic_back, stringResource(R.string.action_back), onBack, Modifier.align(Alignment.TopStart).padding(12.dp))
        RoundIconButton(R.drawable.ic_share, stringResource(R.string.action_share), onShare, Modifier.align(Alignment.TopEnd).padding(12.dp))
        if (userPhoto != null) {
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = lip + 14.dp).clip(RoundedCornerShape(50)).background(Color(0xB8080F1A)).padding(3.dp)) {
                PhotoToggle(stringResource(R.string.label_reference_photo), !showUser) { showUser = false }
                PhotoToggle(stringResource(R.string.label_your_photo), showUser) { showUser = true }
            }
        }
        if (style.isMarket) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(style.colors.border))
        } else {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(lip).clip(RoundedCornerShape(topStart = lip, topEnd = lip)).background(MaterialTheme.colorScheme.surface))
        }
    }
}

@Composable
private fun PhotoToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    val style = FishTheme.style
    val on = if (style.isMarket) style.colors.cta else Color.White
    Surface(onClick = onClick, shape = RoundedCornerShape(50), color = if (selected) on else Color.Transparent, contentColor = if (selected) Sea.Ink else Color.White) {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

@Composable
fun RoundIconButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val style = FishTheme.style
    Surface(
        onClick = onClick, shape = CircleShape, color = Color.White.copy(alpha = 0.92f), contentColor = Sea.Ink,
        border = if (style.isMarket) BorderStroke(2.dp, Color(0xFF1B1B1B)) else null,
        modifier = modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(painterResource(icon), description) }
    }
}

@Composable
private fun Section(title: Int? = null, content: @Composable () -> Unit) {
    Column(Modifier.readableWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        title?.let { Text(stringResource(it), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp)) }
        content()
    }
}

@Composable
private fun Overview(entry: FishEntry) {
    val s = entry.species
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Price first: it is what a shopper came for. Market paints the whole card as a turmeric signboard.
        val style = FishTheme.style
        val c = style.colors
        FishCard(
            Modifier.fillMaxWidth(),
            color = if (style.isMarket) c.cta else c.priceContainer,
            contentColor = if (style.isMarket) c.onCta else c.onPriceContainer,
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(painterResource(R.drawable.ic_price), null, tint = if (style.isMarket) c.onCta else c.accentInk, modifier = Modifier.size(24.dp))
                    Text(stringResource(R.string.price_title), style = MaterialTheme.typography.titleSmall)
                }
                if (s.priceInrPerKg != null) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Text(formatRupees(s.priceInrPerKg), style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"))
                        Text(stringResource(R.string.price_unit_per_kg), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
                    }
                } else {
                    Text(stringResource(R.string.price_unavailable), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                }
                if (s.priceInrPerKg != null) {
                    val note = listOfNotNull(s.priceUpdated?.let { stringResource(R.string.price_updated, it) }, stringResource(R.string.price_note)).joinToString(" · ")
                    Text(note, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        val rows = listOfNotNull(
            Triple(R.string.label_common_name, entry.commonName, false),
            s.localNameTa?.let { Triple(R.string.label_local_name, it, true) },
            Triple(R.string.label_scientific_name, s.scientificName, false),
            s.scientificNameTa?.let { Triple(R.string.label_scientific_name_tamil, it, true) },
        )
        // Common + scientific alone just repeat the header; the card earns its place once there are Tamil names to label.
        if (rows.size > 2) FishCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                rows.forEachIndexed { i, (label, value, tamil) ->
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val style = MaterialTheme.typography.titleMedium.let { if (tamil) tamilLineHeight(it) else it }
                        Text(value, style = if (label == R.string.label_scientific_name) style.merge(ScientificStyle).copy(fontWeight = FontWeight.Normal) else style)
                    }
                    if (i < rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun NutritionCard(n: Nutrition) {
    // Moisture dominates (~76 g), so separate bars would make protein look tiny: lead with protein, then one 100% bar.
    val series = FishTheme.colors.nutrition
    val parts = listOf(
        Triple(R.string.nutrition_protein, n.protein, series[0]),
        Triple(R.string.nutrition_fat, n.fat, series[1]),
        Triple(R.string.nutrition_moisture, n.moisture, series[2]),
        Triple(R.string.nutrition_ash, n.ash, series[3]),
        Triple(R.string.nutrition_carbohydrate, n.carbohydrate, series[4]),
    )
    val total = parts.sumOf { it.second }.takeIf { it > 0 } ?: 1.0
    val market = FishTheme.style.isMarket
    val barDescription = parts.map { (label, v, _) -> "${stringResource(label)} ${stringResource(R.string.nutrition_grams, formatGrams(v))}" }.joinToString(", ")
    FishCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.nutrition_protein_highlight, String.format(java.util.Locale.ROOT, "%.1f", n.protein)),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = if (market) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
            )
            val barShape = RoundedCornerShape(if (market) 4.dp else 9.dp)
            Row(
                Modifier.padding(top = 16.dp).fillMaxWidth().height(if (market) 22.dp else 18.dp).clip(barShape)
                    .then(if (market) Modifier.border(2.dp, FishTheme.colors.border, barShape) else Modifier)
                    .semantics { contentDescription = barDescription },
            ) {
                parts.forEach { (_, v, color) -> if (v > 0) Box(Modifier.weight((v / total).toFloat()).fillMaxHeight().background(color)) }
            }
            Spacer(Modifier.height(8.dp))
            parts.forEachIndexed { i, (label, v, color) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
                    Text(stringResource(label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 10.dp).weight(1f))
                    Text(stringResource(R.string.nutrition_grams, formatGrams(v)), style = MaterialTheme.typography.titleSmall)
                }
                if (i < parts.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun Identify(entry: FishEntry) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val marks = entry.fieldMarks
    val shown = if (expanded) marks else marks.take(COLLAPSED_FEATURES)
    if (marks.isNotEmpty()) {
        FishCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                shown.forEachIndexed { i, mark ->
                    Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(painterResource(R.drawable.ic_check), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Text(mark, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (i < shown.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        if (marks.size > COLLAPSED_FEATURES) {
            SecondaryButton(
                if (expanded) stringResource(R.string.profile_show_fewer_features) else stringResource(R.string.profile_show_all_features, marks.size),
                onClick = { expanded = !expanded },
                modifier = Modifier.padding(top = 12.dp),
                icon = if (expanded) null else R.drawable.ic_chevron_down,
            )
        }
    }
    entry.species.meristics?.let { m ->
        Text(stringResource(R.string.morphology_counts), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = if (marks.isEmpty()) 0.dp else 24.dp, bottom = 12.dp))
        val tiles = listOf(
            m.dorsalSpines to R.string.morphology_dorsal_spines, m.dorsalRays to R.string.morphology_dorsal_rays,
            m.analSpines to R.string.morphology_anal_spines, m.analRays to R.string.morphology_anal_rays,
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.chunked(2).forEach { row ->
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (count, label) ->
                        FishCard(Modifier.weight(1f).fillMaxHeight()) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(count.toString(), style = MaterialTheme.typography.displaySmall.copy(fontSize = 36.sp, lineHeight = 42.sp), color = if (FishTheme.style.isMarket) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary)
                                Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Ecology(entry: FishEntry) {
    val s = entry.species
    val habitat = s.habitat.map { codeLabel(it, ::habitatRes) }.joinToString(", ").takeIf { it.isNotEmpty() }
    val tiles: List<Triple<Int, Int, @Composable () -> Unit>> = listOfNotNull(
        entry.distribution?.let { Triple(R.drawable.ic_pin, R.string.ecology_distribution, @Composable { TileValue(it) }) },
        habitat?.let { Triple(R.drawable.ic_waves, R.string.ecology_habitat, @Composable { TileValue(it) }) },
        s.feeding?.let { Triple(R.drawable.ic_fish, R.string.ecology_feeding, @Composable { TileValue(codeLabel(it, ::feedingRes)) }) },
        s.vertical?.let { Triple(R.drawable.ic_layers, R.string.ecology_vertical, @Composable { TileValue(codeLabel(it, ::verticalRes)) }) },
        s.depthM?.takeIf { it.size == 2 }?.let { Triple(R.drawable.ic_depth, R.string.ecology_depth, @Composable { TileValue(stringResource(R.string.ecology_depth_value, it[0], it[1])) }) },
        s.iucn?.let { Triple(R.drawable.ic_shield, R.string.ecology_iucn, @Composable { Box(Modifier.padding(top = 6.dp)) { IucnBadge(it, long = false) } }) },
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (icon, label, value) ->
                    FishCard(Modifier.weight(1f).fillMaxHeight()) {
                        Column(Modifier.padding(16.dp)) {
                            IconBadge(icon)
                            Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                            value()
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TileValue(text: String) = Text(text, style = MaterialTheme.typography.titleSmall)

@Composable
private fun Footer(entry: FishEntry, onReport: (() -> Unit)?) {
    Column(Modifier.readableWidth().padding(16.dp).navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        entry.species.source?.let { Text(stringResource(R.string.profile_source, it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)) }
        if (onReport != null) SecondaryButton(stringResource(R.string.profile_report_link), onReport, Modifier.padding(top = 16.dp), icon = R.drawable.ic_flag)
        Spacer(Modifier.height(24.dp))
    }
}
