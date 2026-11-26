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
import android.content.pm.PackageInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/** A newer Plate release, as described by release/latest.json on GitHub. */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val notes: List<String>,
    val apkUrl: String,
    /** The installer's SHA-256; the download must match it before it's installed. */
    val sha256: String = "",
)

/**
 * Plate is installed from its website, not a store, so it checks for new versions itself:
 * it reads release/latest.json, shows what's new, and installs the update from inside the app.
 * Test builds read the test branch's latest.json, so updates can be tried before a release.
 */
object AppUpdates {
    val URL_LATEST = "https://raw.githubusercontent.com/mrboora04/ICE3/${com.mrboora.plate.BuildConfig.UPDATE_BRANCH}/release/latest.json"
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
        UpdateInfo(o.getLong("versionCode"), o.getString("versionName"), notes, apk, o.optString("sha256"))
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

    // ---- One-tap updates, installed from inside Plate (the same way as Kitab) ----
    //
    // Plate downloads the update into its own private cache (never the Downloads folder), checks
    // its SHA-256 against latest.json, and hands it to Android's installer, which shows its own
    // "Install this update?" screen. Your diary is kept: it's an update, not a new app.

    sealed interface InstallState {
        data object None : InstallState
        data class Downloading(val progress: Float?) : InstallState
        /** Handed to Android's installer; its confirm screen is up. */
        data object Installing : InstallState
        data class Failed(val message: String) : InstallState
    }

    private val _install = MutableStateFlow<InstallState>(InstallState.None)
    val install: StateFlow<InstallState> = _install
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Action on the intent Android's installer sends back to Plate. */
    const val ACTION_STATUS = "com.mrboora.plate.UPDATE_STATUS"

    /** One tap from anywhere (the header badge, the card, the notification): download and install. */
    fun oneTap(context: Context, info: UpdateInfo) {
        if (_install.value is InstallState.Downloading) return
        val app = context.applicationContext
        scope.launch {
            _install.value = InstallState.Downloading(null)
            _install.value = runCatching { downloadAndInstall(app, info) }
                .fold({ InstallState.Installing }, { InstallState.Failed(it.message ?: "The update didn't download. Check your connection and try again.") })
        }
    }

    private fun downloadAndInstall(context: Context, info: UpdateInfo) {
        val apk = java.io.File(context.cacheDir, "plate-update.apk")
        try {
            download(info.apkUrl, apk) { _install.value = InstallState.Downloading(it) }
            // The file must be exactly the one latest.json describes.
            if (info.sha256.isNotBlank() && !info.sha256.equals(sha256(apk), ignoreCase = true)) {
                throw Exception("The download was damaged. Try again.")
            }
            val installer = context.packageManager.packageInstaller
            val params = android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                .apply { setAppPackageName(context.packageName) }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("plate.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val back = Intent(context, MainActivity::class.java).setAction(ACTION_STATUS).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                // Mutable: the installer adds its answer to this intent.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                session.commit(PendingIntent.getActivity(context, id, back, flags).intentSender)
            }
        } finally {
            apk.delete()
        }
    }

    private fun download(url: String, to: java.io.File, progress: (Float?) -> Unit) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        try {
            if (conn.responseCode != 200) throw Exception("The update isn't available right now. Try again later.")
            val total = conn.contentLengthLong
            if (total > 200L shl 20) throw Exception("The update is too large.")
            var done = 0L
            var last = -1
            conn.inputStream.use { input ->
                to.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 100 / total).toInt() else -1
                        if (pct != last) { last = pct; progress(if (total > 0) done.toFloat() / total else null) }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    fun sha256(file: java.io.File): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Android's installer reports back: ask the user to confirm, or say why it didn't work. */
    fun onStatus(activity: android.app.Activity, intent: Intent) {
        when (val st = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) runCatching { activity.startActivity(confirm) }
                    .onFailure { _install.value = InstallState.Failed("Android couldn't open its installer.") }
            }
            PackageInstaller.STATUS_SUCCESS -> _install.value = InstallState.None
            else -> _install.value = InstallState.Failed(
                when (st) {
                    PackageInstaller.STATUS_FAILURE_ABORTED -> "Update cancelled."
                    PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                        "This update can't replace the Plate on your phone (it was signed with a different key)."
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough space on the phone for the update."
                    PackageInstaller.STATUS_FAILURE_BLOCKED -> "Your phone blocked the install. Allow Plate to install updates, then try again."
                    else -> "The update didn't install" + (intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.let { " ($it)" } ?: "") + "."
                },
            )
        }
    }

    /** Try again after a failed or cancelled update. */
    fun reset() { _install.value = InstallState.None }

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun dm(c: Context) = c.getSystemService(android.app.DownloadManager::class.java)
    private const val KEY_ID = "download_id"
    private const val KEY_VERSION = "download_version"

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
