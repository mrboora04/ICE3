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
 * it reads release/latest.json, shows what's new, and downloads the update through Android's
 * download manager, whose notification opens the installer.
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

    // ---- One-tap updates through Android's download manager ----
    //
    // Plate hands the download to the system DownloadManager. It shows progress and, when done,
    // a "Plate update ready" notification that opens Android's installer. Plate itself never
    // installs anything, so it needs no install permission.

    sealed interface DownloadState {
        data object None : DownloadState
        data class Running(val progress: Float?) : DownloadState
        data object Ready : DownloadState
        data object Failed : DownloadState
    }

    private const val KEY_ID = "download_id"
    private const val KEY_VERSION = "download_version"
    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun dm(c: Context) = c.getSystemService(android.app.DownloadManager::class.java)

    /** Starts the download, unless this version is already downloading or downloaded. */
    fun startDownload(context: Context, info: UpdateInfo) {
        val p = prefs(context)
        if (p.getLong(KEY_VERSION, 0) == info.versionCode) {
            when (state(context)) {
                is DownloadState.Running, DownloadState.Ready -> return
                else -> Unit
            }
        }
        clearDownload(context)
        val name = "Plate-${info.versionName}.apk"
        val req = android.app.DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("Plate ${info.versionName} update")
            .setDescription("Tap to install")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        if (Build.VERSION.SDK_INT >= 29) {
            // Public Downloads needs no storage permission from Android 10 on.
            req.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, name)
        } else {
            req.setDestinationInExternalFilesDir(context, android.os.Environment.DIRECTORY_DOWNLOADS, name)
        }
        val id = dm(context).enqueue(req)
        p.edit().putLong(KEY_ID, id).putLong(KEY_VERSION, info.versionCode).apply()
    }

    /** Where the current update download stands. */
    fun state(context: Context): DownloadState {
        val id = prefs(context).getLong(KEY_ID, -1)
        if (id < 0) return DownloadState.None
        val q = android.app.DownloadManager.Query().setFilterById(id)
        dm(context).query(q)?.use { cur ->
            if (!cur.moveToFirst()) return DownloadState.None
            val status = cur.getInt(cur.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS))
            val done = cur.getLong(cur.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cur.getLong(cur.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return when (status) {
                android.app.DownloadManager.STATUS_SUCCESSFUL -> DownloadState.Ready
                android.app.DownloadManager.STATUS_FAILED -> DownloadState.Failed
                else -> DownloadState.Running(if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null)
            }
        }
        return DownloadState.None
    }

    /**
     * Opens Android's Downloads screen, where the finished update is listed; tapping it installs.
     * (The "Plate update ready" notification does the same in one tap.)
     */
    fun openDownloads(context: Context) {
        val intent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure { openDownloadPage(context) }
    }

    /** Removes the downloaded update once it's installed (or to start over). */
    fun clearDownload(context: Context) {
        val p = prefs(context)
        val id = p.getLong(KEY_ID, -1)
        if (id >= 0) runCatching { dm(context).remove(id) }
        p.edit().remove(KEY_ID).remove(KEY_VERSION).apply()
    }

    /** Call when Plate starts: deletes a downloaded update that is now installed. */
    fun cleanUpInstalled(context: Context) {
        val v = prefs(context).getLong(KEY_VERSION, 0)
        if (v in 1..installedVersion(context)) clearDownload(context)
    }
}
