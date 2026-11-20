package com.mrboora.plate.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mrboora.plate.MainActivity
import com.mrboora.plate.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A newer Plate release, as described by release/latest.json on GitHub. */
data class UpdateInfo(val versionCode: Long, val versionName: String, val notes: List<String>, val apkUrl: String)

/**
 * Plate is installed from its website, not a store, so it checks for new versions itself:
 * it reads release/latest.json and shows what's new. Updating opens the download page.
 */
object AppUpdates {
    const val URL_LATEST = "https://raw.githubusercontent.com/mrboora04/ICE3/main/release/latest.json"
    private const val CHANNEL = "app_updates"
    private const val PREFS = "plate_updates"

    fun installedVersion(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    fun parse(json: String): UpdateInfo? = runCatching {
        val o = JSONObject(json)
        val notes = o.optJSONArray("notes")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
        val apk = o.getString("apk")
        // Only ever download from this repository over HTTPS.
        require(apk.startsWith("https://raw.githubusercontent.com/mrboora04/ICE3/") || apk.startsWith("https://github.com/mrboora04/ICE3/"))
        UpdateInfo(o.getLong("versionCode"), o.getString("versionName"), notes, apk)
    }.getOrNull()

    /** The newer release, or null if this is the latest (or the check failed). */
    suspend fun check(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val text = runCatching {
            val conn = URL(URL_LATEST).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            try { if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null } finally { conn.disconnect() }
        }.getOrNull() ?: return@withContext null
        parse(text)?.takeIf { it.versionCode > installedVersion(context) }
    }

    /** Notifies once per new version. */
    fun notify(context: Context, info: UpdateInfo) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong("notified", 0) >= info.versionCode) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Plate ${info.versionName} is available")
            .setContentText(info.notes.firstOrNull() ?: "Open Plate to update.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(info.notes.joinToString("\n") { "• $it" }))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(7001, n) }
        prefs.edit().putLong("notified", info.versionCode).apply()
    }

    /** The download page; Plate doesn't install APKs itself (so it needs no install permission). */
    const val DOWNLOAD_PAGE = "https://mrboora04.github.io/ICE3/"

    fun openDownloadPage(context: Context) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD_PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
