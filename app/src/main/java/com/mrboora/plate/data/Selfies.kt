package com.mrboora.plate.data

import android.Manifest
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mrboora.plate.MainActivity
import com.mrboora.plate.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Selfie of the day. One photo per day, kept only in the app's private storage on this phone
 * (`files/selfies/<epoch day>.jpg`). It's left out of Android backups and device transfers
 * (see res/xml backup rules), never uploaded, and deleted with "Reset".
 */
object Selfies {
    private const val DIR = "selfies"
    private const val PREFS = "plate_selfies"
    private const val CHANNEL = "selfie_reminder"
    private const val WORK = "selfie-reminder"
    /** Longest side of a saved selfie, in pixels. Plenty for a phone screen, about 150 KB. */
    const val MAX_SIDE = 1440

    fun dir(c: Context) = File(c.filesDir, DIR).apply { mkdirs() }
    fun file(c: Context, day: Long) = File(dir(c), "$day.jpg")

    /** Days that have a selfie, oldest first. */
    fun days(c: Context): List<Long> =
        dir(c).listFiles().orEmpty().mapNotNull { it.name.removeSuffix(".jpg").toLongOrNull() }.sorted()

    // ---- Taking a photo -------------------------------------------------------------------

    /** Where the camera app writes the photo before we shrink it; cleared after saving. */
    private fun captureFile(c: Context) = File(File(c.cacheDir, "capture").apply { mkdirs() }, "selfie.jpg")

    fun captureUri(c: Context): Uri =
        FileProvider.getUriForFile(c, "${c.packageName}.capture", captureFile(c).apply { delete() })

    /** Saves the last camera capture as [day]'s selfie. */
    suspend fun saveCapture(c: Context, day: Long): Boolean =
        save(c, Uri.fromFile(captureFile(c)), day).also { captureFile(c).delete() }

    /** Shrinks, turns upright and saves the photo at [source] as [day]'s selfie. */
    suspend fun save(c: Context, source: Uri, day: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(source)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) return@runCatching false
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val raw = c.contentResolver.openInputStream(source)!!.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching false
            val rotation = c.contentResolver.openInputStream(source)!!.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
            val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(raw.width, raw.height))
            val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
            val upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            val tmp = File(dir(c), "$day.tmp")
            // Re-encoding drops every bit of metadata (location, camera, time) from the original.
            tmp.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            if (upright !== raw) upright.recycle()
            raw.recycle()
            tmp.renameTo(file(c, day))
        }.getOrDefault(false)
    }

    fun delete(c: Context, day: Long) = file(c, day).delete()

    fun deleteAll(c: Context) {
        dir(c).deleteRecursively()
        File(c.cacheDir, "capture").deleteRecursively()
    }

    // ---- Showing --------------------------------------------------------------------------

    /** Decodes a selfie no wider than [maxPx]. A tiny [maxPx] (about 24) gives the soft blurred cover. */
    fun load(c: Context, day: Long, maxPx: Int): Bitmap? {
        val f = file(c, day)
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        val b = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = maxPx.toFloat() / maxOf(b.width, b.height)
        if (scale >= 1f) return b
        return Bitmap.createScaledBitmap(b, (b.width * scale).toInt().coerceAtLeast(1), (b.height * scale).toInt().coerceAtLeast(1), true)
    }

    /**
     * The frosted cover shown before a selfie is revealed: a 48 px copy, box-blurred three times
     * (close to a Gaussian). Nothing of the face survives, on any Android version.
     */
    fun cover(c: Context, day: Long): Bitmap? {
        val small = load(c, day, 48) ?: return null
        val w = small.width
        val h = small.height
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        repeat(3) { boxBlur(px, w, h, 4) }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /** One horizontal and one vertical box-blur pass with the given radius, in place. */
    internal fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        fun pass(src: IntArray, dst: IntArray, len: Int, lines: Int, at: (line: Int, i: Int) -> Int) {
            for (line in 0 until lines) {
                for (i in 0 until len) {
                    var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
                    for (k in -r..r) {
                        val j = (i + k).coerceIn(0, len - 1)
                        val p = src[at(line, j)]
                        a += p ushr 24; rr += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
                    }
                    dst[at(line, i)] = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
                }
            }
        }
        pass(px, tmp, w, h) { y, x -> y * w + x }
        pass(tmp, px, h, w) { x, y -> y * w + x }
    }

    // ---- Settings -------------------------------------------------------------------------

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Ask for the phone's screen lock (PIN, pattern, fingerprint) before showing a selfie. */
    fun locked(c: Context) = prefs(c).getBoolean("lock", false)
    fun setLocked(c: Context, on: Boolean) = prefs(c).edit().putBoolean("lock", on).apply()

    /** The phone has a screen lock we can ask for. */
    fun canLock(c: Context) = c.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    @Suppress("DEPRECATION")
    fun unlockIntent(c: Context): Intent? = c.getSystemService(KeyguardManager::class.java)
        ?.createConfirmDeviceCredentialIntent("Your selfies", "Unlock to see your selfies")

    fun reminder(c: Context) = prefs(c).getBoolean("reminder", false)
    fun reminderHour(c: Context) = prefs(c).getInt("reminder_hour", 9)

    fun setReminder(c: Context, on: Boolean, hour: Int = reminderHour(c)) {
        prefs(c).edit().putBoolean("reminder", on).putInt("reminder_hour", hour).apply()
        val wm = WorkManager.getInstance(c)
        if (!on) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(LocalTime.of(hour, 0))
        if (!next.isAfter(now)) next = next.plusDays(1)
        val work = PeriodicWorkRequestBuilder<Reminder>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, work)
    }

    /** Reminds once a day, only if today's selfie hasn't been taken. */
    class Reminder(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val c = applicationContext
            if (!reminder(c) || file(c, LocalDate.now().toEpochDay()).exists()) return Result.success()
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return Result.success()
            val nm = c.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Selfie reminder", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(
                c, 7, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Selfie of the day")
                .setContentText("A quick photo for today. It stays on your phone.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(c).notify(7007, n) }
            return Result.success()
        }
    }
}
