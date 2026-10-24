package com.example.fishid.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fishid.R
import com.example.fishid.ui.FishImage
import com.example.fishid.ui.LinkButton
import com.example.fishid.ui.PrimaryButton
import com.example.fishid.ui.ScrollFill
import androidx.compose.foundation.layout.aspectRatio
import com.example.fishid.ui.IconBadge
import com.example.fishid.ui.hardShadow
import com.example.fishid.ui.setAppLanguage
import com.example.fishid.ui.theme.FishTheme
import com.example.fishid.ui.theme.isTamil
import com.example.fishid.ui.theme.tamilLineHeight

private const val SAMPLE_PHOTO = "data/images/scolopsis_bimaculata_1.jpg"

/** Step 0 = language, 1..3 = Scan / Identify / Learn. Saved, so a language switch (activity recreate) keeps the step. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().padding(16.dp),
    ) {
        if (step == 0) LanguageStep { step = 1 } else IntroStep(step, onNext = { if (step < 3) step++ else onDone() }, onSkip = onDone)
    }
}

@Composable
private fun LanguageStep(onNext: () -> Unit) {
    val current = if (isTamil()) "ta" else "en"
    var choice by rememberSaveable { mutableStateOf(current) }
    ScrollFill(Modifier.fillMaxSize(), top = {
        Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AppMark()
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 14.dp))
        }
        Text(
            stringResource(R.string.onboarding_language_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 24.dp),
        )
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LanguageCard("அ", stringResource(R.string.language_tamil), choice == "ta") { choice = "ta" }
            LanguageCard("A", stringResource(R.string.language_english), choice == "en") { choice = "en" }
        }
        Text(
            stringResource(R.string.onboarding_language_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        )
    }, bottom = {
        PrimaryButton(stringResource(R.string.onboarding_next), onClick = {
            onNext()
            if (choice != current) setAppLanguage(choice) // recreates the activity; step is saved
        }, trailingIcon = R.drawable.ic_chevron_right)
    })
}

@Composable
private fun LanguageCard(glyph: String, name: String, selected: Boolean, onClick: () -> Unit) {
    val s = FishTheme.style
    val c = s.colors
    val shape = RoundedCornerShape(s.cardRadius)
    // Selected: tinted fill + heavy border + check, so the choice reads without relying on colour.
    val fill = when {
        !selected -> MaterialTheme.colorScheme.surface
        s.isMarket -> c.cta
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val edge = when {
        s.isMarket -> c.border
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = shape,
        color = fill,
        contentColor = if (selected && s.isMarket) c.onCta else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(if (selected || s.isMarket) 2.5.dp else 1.5.dp, edge),
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp)
            .hardShadow(shape, if (selected) s.hardShadow else 0.dp, c.shadow)
            .selectable(selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val dot = when {
                s.isMarket -> if (selected) MaterialTheme.colorScheme.surface else c.cta
                selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.background
            }
            val dotText = when {
                s.isMarket -> if (selected) MaterialTheme.colorScheme.onSurface else c.onCta
                selected -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurface
            }
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(dot)
                    .then(if (s.isMarket) Modifier.border(2.dp, c.border, CircleShape) else Modifier),
                contentAlignment = Alignment.Center,
            ) { Text(glyph, style = MaterialTheme.typography.titleLarge, color = dotText) }
            Text(name, style = tamilLineHeight(MaterialTheme.typography.headlineMedium), modifier = Modifier.weight(1f))
            if (selected) Icon(painterResource(R.drawable.ic_check), null, tint = if (s.isMarket) c.onCta else MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun IntroStep(step: Int, onNext: () -> Unit, onSkip: () -> Unit) {
    val s = FishTheme.style
    val c = s.colors
    val titles = listOf(R.string.onboarding_scan_title, R.string.onboarding_identify_title, R.string.onboarding_learn_title)
    val bodies = listOf(R.string.onboarding_scan_body, R.string.onboarding_identify_body, R.string.onboarding_learn_body)
    val i = step - 1
    ScrollFill(Modifier.fillMaxSize(), top = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            LinkButton(stringResource(R.string.onboarding_skip), onClick = onSkip)
        }
        val frame = RoundedCornerShape(if (s.isMarket) 18.dp else 28.dp)
        Box(
            Modifier.padding(top = 12.dp).fillMaxWidth().aspectRatio(1.15f)
                .hardShadow(frame, s.hardShadow, c.shadow)
                .clip(frame).background(c.photoBackdrop)
                .then(if (s.isMarket) Modifier.border(2.dp, c.border, frame) else Modifier),
        ) {
            FishImage(SAMPLE_PHOTO, null, Modifier.fillMaxSize())
            if (i < 2) Brackets(if (i == 0) c.accent else c.lock, stroke = s.bracketStroke)
            if (i == 1) Box(Modifier.align(Alignment.Center).size(68.dp).clip(CircleShape).background(c.lock), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_check), null, tint = Color.White, modifier = Modifier.size(38.dp))
            }
            if (i == 2) Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp), shape = RoundedCornerShape(s.cardRadius), border = if (s.isMarket) BorderStroke(2.dp, c.border) else null) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconBadge(R.drawable.ic_price)
                    IconBadge(R.drawable.ic_waves)
                    IconBadge(R.drawable.ic_shield)
                }
            }
        }
        Row(Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            titles.forEachIndexed { n, t ->
                val on = n == i
                Surface(
                    shape = RoundedCornerShape(50),
                    color = when {
                        !on -> MaterialTheme.colorScheme.surface
                        s.isMarket -> c.cta
                        else -> MaterialTheme.colorScheme.primary
                    },
                    contentColor = when {
                        !on -> MaterialTheme.colorScheme.onSurfaceVariant
                        s.isMarket -> c.onCta
                        else -> MaterialTheme.colorScheme.onPrimary
                    },
                    border = if (on && !s.isMarket) null else BorderStroke(if (s.isMarket) 1.5.dp else 1.dp, if (s.isMarket) c.border else MaterialTheme.colorScheme.outline),
                ) { Text("${n + 1} · ${stringResource(t)}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) }
            }
        }
        Text(stringResource(titles[i]), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        Text(stringResource(bodies[i]), style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }, bottom = {
        Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.Center) {
            repeat(3) { n ->
                val dot = when {
                    n != i -> MaterialTheme.colorScheme.outlineVariant
                    s.isMarket -> MaterialTheme.colorScheme.onBackground
                    else -> MaterialTheme.colorScheme.primary
                }
                Box(Modifier.padding(horizontal = 4.dp).size(if (n == i) 24.dp else 8.dp, 8.dp).clip(CircleShape).background(dot))
            }
        }
        PrimaryButton(
            stringResource(if (i == 2) R.string.onboarding_start else R.string.onboarding_next),
            onClick = onNext,
            trailingIcon = if (i == 2) R.drawable.ic_scan else R.drawable.ic_chevron_right,
        )
    })
}

/**
 * Four rounded scan corners filling the parent with [inset] margin. A soft dark halo under each stroke
 * keeps the corners visible over white fish, ice and bright tarpaulin.
 */
@Composable
fun Brackets(color: Color, inset: Dp = 34.dp, stroke: Dp = 5.dp, arm: Dp = 44.dp, halo: Boolean = true) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val i = inset.toPx(); val s = stroke.toPx(); val a = arm.toPx(); val r = 12.dp.toPx()
        val w = size.width; val h = size.height
        val style = androidx.compose.ui.graphics.drawscope.Stroke(width = s, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val haloStyle = androidx.compose.ui.graphics.drawscope.Stroke(width = s + 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(x, y + dy * a)
                lineTo(x, y + dy * r)
                quadraticTo(x, y, x + dx * r, y)
                lineTo(x + dx * a, y)
            }
            if (halo) drawPath(p, Color.Black.copy(alpha = 0.28f), style = haloStyle)
            drawPath(p, color, style = style)
        }
        corner(i, i, 1f, 1f); corner(w - i, i, -1f, 1f); corner(i, h - i, 1f, -1f); corner(w - i, h - i, -1f, -1f)
    }
}

/** App mark. Sea: ocean tile, coral corners, white fish. Market: turmeric tile with an ink border, ink fish and corners. */
@Composable
fun AppMark(size: Dp = 80.dp) {
    val s = FishTheme.style
    val c = s.colors
    val shape = RoundedCornerShape(size * 0.275f)
    val tile = if (s.isMarket) c.cta else MaterialTheme.colorScheme.primary
    val fish = if (s.isMarket) c.onCta else Color.White
    val corners = if (s.isMarket) c.onCta else c.accent
    Box(
        Modifier.size(size).hardShadow(shape, s.hardShadow, c.shadow).clip(shape).background(tile)
            .then(if (s.isMarket) Modifier.border(2.5.dp, c.border, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size * 0.6f)) { Brackets(corners, inset = 2.dp, stroke = 3.dp, arm = size * 0.14f, halo = false) }
        Icon(painterResource(R.drawable.ic_fish), null, tint = fish, modifier = Modifier.size(size * 0.42f))
    }
}
