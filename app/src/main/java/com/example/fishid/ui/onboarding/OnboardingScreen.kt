package com.example.fishid.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.example.fishid.R
import com.example.fishid.ui.FishImage
import com.example.fishid.ui.ScrollFill
import androidx.compose.foundation.layout.aspectRatio
import com.example.fishid.ui.setAppLanguage
import com.example.fishid.ui.theme.LockGreen
import com.example.fishid.ui.theme.Marigold
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
        Button(onClick = {
            onNext()
            if (choice != current) setAppLanguage(choice) // recreates the activity; step is saved
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Text(stringResource(R.string.onboarding_next)) }
    })
}

@Composable
private fun LanguageCard(glyph: String, name: String, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 2.5.dp else 1.5.dp, if (selected) primary else MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).selectable(selected, role = Role.RadioButton, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(if (selected) primary else MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) { Text(glyph, style = MaterialTheme.typography.titleLarge, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
            Text(name, style = tamilLineHeight(MaterialTheme.typography.headlineMedium), modifier = Modifier.weight(1f))
            if (selected) Icon(painterResource(R.drawable.ic_check), null, tint = primary, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun IntroStep(step: Int, onNext: () -> Unit, onSkip: () -> Unit) {
    val titles = listOf(R.string.onboarding_scan_title, R.string.onboarding_identify_title, R.string.onboarding_learn_title)
    val bodies = listOf(R.string.onboarding_scan_body, R.string.onboarding_identify_body, R.string.onboarding_learn_body)
    val i = step - 1
    ScrollFill(Modifier.fillMaxSize(), top = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.onboarding_skip)) }
        }
        Box(Modifier.padding(top = 12.dp).fillMaxWidth().aspectRatio(1.15f).clip(RoundedCornerShape(28.dp)).background(Color(0xFF1B252E))) {
            FishImage(SAMPLE_PHOTO, null, Modifier.fillMaxSize())
            if (i < 2) Brackets(if (i == 0) Marigold else LockGreen)
            if (i == 1) Box(Modifier.align(Alignment.Center).size(64.dp).clip(CircleShape).background(LockGreen), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_check), null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            if (i == 2) Surface(Modifier.align(Alignment.BottomCenter).padding(16.dp), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(painterResource(R.drawable.ic_price), null, tint = MaterialTheme.colorScheme.primary)
                    Icon(painterResource(R.drawable.ic_waves), null, tint = MaterialTheme.colorScheme.primary)
                    Icon(painterResource(R.drawable.ic_shield), null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Row(Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            titles.forEachIndexed { n, t ->
                val on = n == i
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    border = if (on) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                ) { Text("${n + 1} · ${stringResource(t)}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) }
            }
        }
        Text(stringResource(titles[i]), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        Text(stringResource(bodies[i]), style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }, bottom = {
        Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.Center) {
            repeat(3) { n ->
                Box(Modifier.padding(horizontal = 4.dp).size(if (n == i) 24.dp else 8.dp, 8.dp).clip(CircleShape).background(if (n == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
            }
        }
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(if (i == 2) R.string.onboarding_start else R.string.onboarding_next))
        }
    })
}

/** Four rounded scan corners filling the parent with [inset] margin. */
@Composable
fun Brackets(color: Color, inset: androidx.compose.ui.unit.Dp = 34.dp, stroke: androidx.compose.ui.unit.Dp = 5.dp, arm: androidx.compose.ui.unit.Dp = 44.dp) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val i = inset.toPx(); val s = stroke.toPx(); val a = arm.toPx(); val r = 12.dp.toPx()
        val w = size.width; val h = size.height
        val style = androidx.compose.ui.graphics.drawscope.Stroke(width = s, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(x, y + dy * a)
                lineTo(x, y + dy * r)
                quadraticTo(x, y, x + dx * r, y)
                lineTo(x + dx * a, y)
            }
            drawPath(p, color, style = style)
        }
        corner(i, i, 1f, 1f); corner(w - i, i, -1f, 1f); corner(i, h - i, 1f, -1f); corner(w - i, h - i, -1f, -1f)
    }
}

@Composable
fun AppMark(size: androidx.compose.ui.unit.Dp = 80.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.275f)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size * 0.6f)) { Brackets(Marigold, inset = 2.dp, stroke = 3.dp, arm = size * 0.14f) }
        Icon(painterResource(R.drawable.ic_fish), null, tint = Color.White, modifier = Modifier.size(size * 0.42f))
    }
}
