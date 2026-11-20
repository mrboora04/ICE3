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
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.mrboora.plate.MainActivity
import com.mrboora.plate.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A newer Plate release, as described by release/latest.json on GitHub. */
data class UpdateInfo(val versionCode: Long, val versionName: String, val notes: List<String>, val apkUrl: String)

/**
 * Plate is installed from GitHub, not a store, so it checks for new versions itself:
 * it reads release/latest.json, shows what's new, downloads the APK and opens Android's installer.
 * Android only installs it if it's signed with the same key as the installed app.
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

    /** Whether Android lets Plate open an APK; if not, [installSettings] shows the switch. */
    fun canInstall(context: Context) = context.packageManager.canRequestPackageInstalls()

    fun installSettings(context: Context) = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Downloads the APK into the app's cache, reporting progress 0..1. */
    suspend fun download(context: Context, info: UpdateInfo, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "Plate-${info.versionName}.apk")
        val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        try {
            if (conn.responseCode != 200) throw java.io.IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 }
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total != null) progress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        out
    }

    /** Opens Android's installer for a downloaded APK. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
