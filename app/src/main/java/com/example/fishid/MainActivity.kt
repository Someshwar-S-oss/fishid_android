package com.example.fishid

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.material3.Surface
import com.example.fishid.ui.themeMode
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.example.fishid.data.History
import com.example.fishid.data.ScanRecord
import com.example.fishid.ui.catalogue.FishGuideScreen
import com.example.fishid.ui.report.ReportScreen
import com.example.fishid.ui.scan.CompareScreen
import com.example.fishid.ui.scan.NoMatchScreen
import com.example.fishid.ui.history.HistoryScreen
import com.example.fishid.ui.onboarding.OnboardingScreen
import com.example.fishid.ui.scan.ScanScreen
import com.example.fishid.ui.settings.SettingsScreen
import com.example.fishid.ui.species.ProfileScreen
import com.example.fishid.ui.theme.FishIDTheme
import com.example.fishid.ui.theme.NightBackground
import com.example.fishid.ui.theme.NightInkMuted
import com.example.fishid.ui.theme.NightLine
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(themeMode().night) // the in-app Light/Dark/System choice
        super.onCreate(savedInstanceState)
        // Phones stay portrait: scanning is a one-handed portrait task and a market crowd rotates phones by accident.
        // Tablets and foldables rotate freely (Android 16+ ignores the lock there anyway) and get readable-width layouts.
        if (resources.configuration.smallestScreenWidthDp < 600) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        setContent {
            FishIDTheme {
                // Surface sets the default text colour to onBackground, so un-styled text is readable in dark mode.
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { FishIdApp() }
            }
        }
    }
}

/**
 * Screens above the tabs, as a saveable back stack of route strings.
 * ponytail: hand-rolled stack instead of navigation-compose; switch if deep links or transitions are needed.
 */
object Routes {
    const val SETTINGS = "settings"
    fun profile(id: String, scanId: String? = null) = "profile/$id" + (scanId?.let { "?scan=$it" } ?: "")
    fun compare(scanId: String) = "compare/$scanId"
    fun noMatch(scanId: String) = "nomatch/$scanId"
    fun report(scanId: String) = "report/$scanId"

    /** Where a past scan opens: its fish, the compare screen, or the no-match tips. */
    fun forScan(r: ScanRecord) = when {
        r.speciesId != null -> profile(r.speciesId, r.id)
        r.outcome == ScanRecord.Outcome.UNSURE -> compare(r.id)
        else -> noMatch(r.id)
    }
}

private fun Context.prefs() = getSharedPreferences("fishid", Context.MODE_PRIVATE)

@Composable
fun FishIdApp() {
    val context = LocalContext.current
    var onboarded by rememberSaveable { mutableStateOf(context.prefs().getBoolean("onboarded", false)) }
    if (!onboarded) {
        OnboardingScreen(onDone = {
            context.prefs().edit().putBoolean("onboarded", true).apply()
            onboarded = true
        })
        return
    }

    val stack: SnapshotStateList<String> = rememberSaveable(saver = listSaver({ it.toList() }, { it.toMutableStateList() })) { mutableStateListOf() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val open: (String) -> Unit = { stack.add(it) }
    val back: () -> Unit = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) } // removeLast() crashes below API 35
    BackHandler(enabled = stack.isNotEmpty(), onBack = back)

    val top = stack.lastOrNull()
    val arg = top?.substringAfter('/')
    when {
        top == null -> MainTabs(tab, onTab = { tab = it }, open = open)
        top.startsWith("profile/") -> {
            val id = arg!!.substringBefore("?scan=")
            val scan = arg.substringAfter("?scan=", "").ifEmpty { null }?.let { History.get(context, it) }
            ProfileScreen(id, scan?.photo, onBack = back, onReport = scan?.let { s -> { open(Routes.report(s.id)) } })
        }
        top.startsWith("compare/") -> CompareScreen(
            arg!!, onBack = back,
            onPicked = { id -> back(); open(Routes.profile(id, arg)) },
            onNone = { back(); open(Routes.report(arg)) },
        )
        top.startsWith("nomatch/") -> NoMatchScreen(arg!!, onBack = back, onReport = { back(); open(Routes.report(arg)) })
        top.startsWith("report/") -> ReportScreen(arg!!, onDone = back)
        top == Routes.SETTINGS -> SettingsScreen(onBack = back)
        else -> LaunchedEffect(top) { back() } // unknown route (e.g. from an older saved state)
    }
}

@Composable
private fun MainTabs(tab: Int, onTab: (Int) -> Unit, open: (String) -> Unit) {
    // Dark bar only while the viewfinder is actually showing (not on the light permission screen).
    var cameraShown by remember { mutableStateOf(false) }
    val onCamera = tab == 0 && cameraShown
    Column(Modifier.fillMaxSize().background(if (onCamera) NightBackground else MaterialTheme.colorScheme.background)) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> ScanScreen(
                    onProfile = { id, scan -> open(Routes.profile(id, scan)) },
                    onUnsure = { open(Routes.forScan(it)) },
                    onReport = { open(Routes.report(it)) },
                    onSettings = { open(Routes.SETTINGS) },
                    onCameraShown = { cameraShown = it },
                )
                1 -> FishGuideScreen(onOpen = { open(Routes.profile(it)) }, onSettings = { open(Routes.SETTINGS) })
                else -> HistoryScreen(onOpen = { open(Routes.forScan(it)) }, onSettings = { open(Routes.SETTINGS) })
            }
        }
        // The bar turns dark on the camera tab so it doesn't glare against the viewfinder.
        NavigationBar(containerColor = if (onCamera) NightBackground else MaterialTheme.colorScheme.surface) {
            val items = listOf(
                Triple(R.drawable.ic_scan, R.string.nav_scan, 0),
                Triple(R.drawable.ic_fish, R.string.nav_fish, 1),
                Triple(R.drawable.ic_history, R.string.nav_history, 2),
            )
            items.forEach { (icon, label, i) ->
                NavigationBarItem(
                    selected = tab == i,
                    onClick = { onTab(i) },
                    icon = { Icon(painterResource(icon), null) },
                    label = { Text(stringResource(label), style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                    colors = if (onCamera) NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White, selectedTextColor = Color.White,
                        indicatorColor = Color(0xFF1F4F58),
                        unselectedIconColor = NightInkMuted, unselectedTextColor = NightInkMuted,
                    ) else NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}
