package com.example.fishid

import android.app.Application
import com.example.fishid.bridge.FishIdPackage
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.common.assets.ReactFontManager
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost

class MainApplication : Application(), ReactApplication {
    override val reactHost: ReactHost by lazy {
        getDefaultReactHost(
            context = applicationContext,
            packageList = PackageList(this).packages.apply { add(FishIdPackage()) },
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Bundled offline fonts: Hind Madurai draws Latin and Tamil as one family; Noto Serif Italic is for scientific names.
        ReactFontManager.getInstance().addCustomFont(this, "HindMadurai", R.font.hind_madurai)
        ReactFontManager.getInstance().addCustomFont(this, "NotoSerifItalic", R.font.noto_serif_italic)
        loadReactNative(this)
    }
}
