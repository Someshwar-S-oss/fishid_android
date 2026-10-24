package com.example.fishid

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultReactActivityDelegate

class MainActivity : ReactActivity() {
    override fun getMainComponentName() = "FishID"

    override fun createReactActivityDelegate(): ReactActivityDelegate = DefaultReactActivityDelegate(this, mainComponentName)

    override fun onCreate(savedInstanceState: Bundle?) {
        // The in-app theme (light unless chosen otherwise), applied before the first frame so there is no flash.
        AppCompatDelegate.setDefaultNightMode(
            when (getSharedPreferences("fishid", MODE_PRIVATE).getString("theme", null)?.lowercase()) {
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                else -> AppCompatDelegate.MODE_NIGHT_NO
            },
        )
        // Phones stay portrait: scanning is a one-handed portrait task and a market crowd rotates phones by accident.
        if (resources.configuration.smallestScreenWidthDp < 600) requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        super.onCreate(null) // RN restores its own state; passing the bundle would re-create stale fragments
    }
}
