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
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val notes: List<String>,
    val apkUrl: String,
    /** SHA-256 of the APK, lowercase hex. The download is checked against it before it can be installed. */
    val sha256: String? = null,
)

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
        // A checksum, when present, must be a real SHA-256 (64 hex characters).
        val sha = o.optString("sha256", "").trim().lowercase().takeIf { it.isNotEmpty() }
        if (sha != null) require(sha.matches(Regex("^[0-9a-f]{64}$")))
        UpdateInfo(o.getLong("versionCode"), o.getString("versionName"), notes, apk, sha)
    }.getOrNull()

    /** SHA-256 of a stream, lowercase hex. */
    fun sha256(input: java.io.InputStream): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

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
        // Tapping the notification (or its Update button) opens Plate and starts the download straight away.
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_START_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Plate ${info.versionName} is available")
            .setContentText(info.notes.firstOrNull() ?: "Open Plate to update.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(info.notes.joinToString("\n") { "• $it" }))
            .setContentIntent(open)
            .addAction(0, "Update", open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(7001, n) }
        prefs.edit().putLong("notified", info.versionCode).apply()
    }

    /** Extra on the intent that opens Plate from the update notification: start downloading now. */
    const val EXTRA_START_UPDATE = "com.mrboora.plate.START_UPDATE"

    /**
     * One tap from anywhere (the header badge, the card, the notification): download the update,
     * or if it's already downloaded, open it for installing. Returns what happened.
     */
    fun oneTap(context: Context, info: UpdateInfo): DownloadState {
        when (state(context)) {
            DownloadState.Ready -> if (prefs(context).getLong(KEY_VERSION, 0) == info.versionCode) openDownloads(context) else startDownload(context, info)
            is DownloadState.Running -> Unit
            else -> startDownload(context, info)
        }
        return state(context)
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
        /** The download didn't match the published SHA-256, so it was deleted before it could be installed. */
        data object Damaged : DownloadState
    }

    private const val KEY_SHA = "download_sha256"
    private const val KEY_CHECKED = "download_checked"
    private const val KEY_BAD = "download_bad"
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
        // Every installer Plate downloads is remembered, so each one is deleted once it's installed.
        val all = p.getStringSet(KEY_ALL, emptySet()).orEmpty() + "$id:${info.versionCode}"
        p.edit().putLong(KEY_ID, id).putLong(KEY_VERSION, info.versionCode).putStringSet(KEY_ALL, all)
            .putString(KEY_SHA, info.sha256).remove(KEY_CHECKED).remove(KEY_BAD).apply()
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
                android.app.DownloadManager.STATUS_SUCCESSFUL -> if (verify(context)) DownloadState.Ready else DownloadState.Damaged
                android.app.DownloadManager.STATUS_FAILED -> DownloadState.Failed
                else -> DownloadState.Running(if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null)
            }
        }
        return if (prefs(context).getBoolean(KEY_BAD, false)) DownloadState.Damaged else DownloadState.None
    }

    /**
     * Checks the finished download against the SHA-256 published in latest.json (once per
     * download). On a mismatch the file is removed right away, so a damaged or swapped APK can't
     * be installed from the download notification either. Releases without a checksum pass.
     */
    fun verify(context: Context): Boolean {
        val p = prefs(context)
        val id = p.getLong(KEY_ID, -1)
        val expected = p.getString(KEY_SHA, null) ?: return true
        if (id < 0) return false
        if (p.getLong(KEY_CHECKED, -1) == id) return true
        val actual = runCatching {
            dm(context).openDownloadedFile(id).use { pfd -> java.io.FileInputStream(pfd.fileDescriptor).use { sha256(it) } }
        }.getOrNull()
        return if (actual == expected) {
            p.edit().putLong(KEY_CHECKED, id).apply()
            true
        } else {
            runCatching { dm(context).remove(id) }
            p.edit().remove(KEY_ID).remove(KEY_VERSION).putBoolean(KEY_BAD, true).apply()
            false
        }
    }

    /** Checks the update as soon as Android finishes downloading it. */
    class DownloadDone : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id >= 0 && id == prefs(context).getLong(KEY_ID, -1)) {
                val pending = goAsync()
                Thread { try { verify(context) } finally { pending.finish() } }.start()
            }
        }
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
        p.edit().remove(KEY_ID).remove(KEY_VERSION).remove(KEY_BAD).remove(KEY_CHECKED).apply()
    }

    private const val KEY_ALL = "download_all"

    /**
     * Call when Plate starts (and from the daily check): deletes every installer Plate downloaded
     * for a version that's now installed, or older. Returns the bytes freed (0 if nothing to do).
     */
    fun cleanUpInstalled(context: Context): Long {
        val p = prefs(context)
        val installed = installedVersion(context)
        var freed = 0L
        val keep = mutableSetOf<String>()
        p.getStringSet(KEY_ALL, emptySet()).orEmpty().forEach { entry ->
            val id = entry.substringBefore(':').toLongOrNull() ?: return@forEach
            val version = entry.substringAfter(':').toLongOrNull() ?: 0L
            if (version in 1..installed) {
                freed += runCatching {
                    dm(context).query(android.app.DownloadManager.Query().setFilterById(id))?.use { c ->
                        if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)) else 0L
                    } ?: 0L
                }.getOrDefault(0L)
                runCatching { dm(context).remove(id) }
            } else keep += entry
        }
        val v = p.getLong(KEY_VERSION, 0)
        val edit = p.edit().putStringSet(KEY_ALL, keep)
        if (v in 1..installed) {
            // The single "current download" from before 2.8 isn't in the list above.
            val id = p.getLong(KEY_ID, -1)
            if (id >= 0 && keep.none { it.startsWith("$id:") }) {
                freed += runCatching {
                    dm(context).query(android.app.DownloadManager.Query().setFilterById(id))?.use { c ->
                        if (c.moveToFirst()) c.getLong(c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)) else 0L
                    } ?: 0L
                }.getOrDefault(0L)
                runCatching { dm(context).remove(id) }
            }
            edit.remove(KEY_ID).remove(KEY_VERSION)
        }
        edit.apply()
        return freed
    }

    /** Only files called Plate….apk are ever deleted by "Clean up old installers". */
    fun isPlateInstaller(name: String): Boolean = Regex("""^plate.*\.apk$""", RegexOption.IGNORE_CASE).matches(name.trim())

    /**
     * Deletes the files the user picked in "Clean up old installers", but only ones named
     * Plate….apk. Returns (files deleted, bytes freed, files skipped).
     */
    fun deletePicked(context: Context, uris: List<Uri>): Triple<Int, Long, Int> {
        var count = 0
        var bytes = 0L
        var skipped = 0
        uris.forEach { uri ->
            val (name, size) = runCatching {
                context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) (c.getString(0) ?: "") to (if (c.isNull(1)) 0L else c.getLong(1)) else "" to 0L } ?: ("" to 0L)
            }.getOrDefault("" to 0L)
            if (!isPlateInstaller(name)) { skipped++; return@forEach }
            val ok = runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, uri) }.getOrDefault(false)
            if (ok) { count++; bytes += size } else skipped++
        }
        return Triple(count, bytes, skipped)
    }
}
