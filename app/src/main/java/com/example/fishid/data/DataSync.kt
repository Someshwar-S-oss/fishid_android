package com.example.fishid.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Pulls the species catalog the admin site (admin/) publishes to a public GitHub repo. Static file on GitHub's CDN:
 * always up, no server to keep awake. The current data stays in use until a newer, valid catalog has fully downloaded.
 */
object DataSync {
    // Set to the data repo the admin site commits to (admin/README.md). Raw GitHub caches for ~5 minutes.
    private const val URL_CATALOG = "https://raw.githubusercontent.com/Someshwar-S-oss/fishid-data/main/catalog.json"
    private const val MIN_GAP_MS = 5 * 60_000L
    private const val TAG = "DataSync"

    @Volatile private var lastCheck = 0L

    /** Called on a background thread after a newer catalog is live. */
    @Volatile var onUpdate: (() -> Unit)? = null

    fun file(context: Context) = File(context.filesDir, "catalog.json")

    /** Checks for new data whenever the phone gets a connection (also fires at once if already online). */
    fun watchNetwork(context: Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) = refresh(context)
            })
        }
    }

    /** At most one check per 5 minutes unless [force]d; an unchanged file costs one 304 response. */
    fun refresh(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        synchronized(this) {
            val now = System.currentTimeMillis()
            if (!force && now - lastCheck < MIN_GAP_MS) return
            lastCheck = now
        }
        thread(name = TAG) { runCatching { fetch(app) }.onFailure { Log.i(TAG, "update skipped: ${it.message}") } }
    }

    private fun fetch(context: Context) {
        val prefs = context.getSharedPreferences("fishid_sync", Context.MODE_PRIVATE)
        val conn = URL(URL_CATALOG).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            prefs.getString("etag", null)?.let { conn.setRequestProperty("If-None-Match", it) }
            when (conn.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> return
                HttpURLConnection.HTTP_OK -> {}
                else -> error("HTTP ${conn.responseCode}")
            }
            val text = conn.inputStream.use { it.readBytes().decodeToString() }
            val next = Catalog.parse(text)
            synchronized(Catalog) {
                val current = Catalog.get(context)
                if (next.dataVersion > current.dataVersion) {
                    check(next.replaces(current)) { "data ${next.dataVersion} drops species the model knows" }
                    val tmp = File(context.filesDir, "catalog.json.tmp")
                    tmp.writeText(text)
                    check(tmp.renameTo(file(context))) { "could not replace catalog.json" }
                    Catalog.set(next)
                    onUpdate?.invoke()
                }
            }
            prefs.edit().putString("etag", conn.getHeaderField("ETag")).apply()
        } finally {
            conn.disconnect()
        }
    }
}
