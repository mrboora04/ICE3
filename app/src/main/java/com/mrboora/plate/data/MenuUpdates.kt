package com.mrboora.plate.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mrboora.plate.MainActivity
import com.mrboora.plate.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Keeps restaurant menus current without an app update: once a day, download the menu file
 * from the public GitHub repo, and tell the user when something they requested has arrived.
 */
object MenuUpdates {
    const val URL_MENUS =
        "https://raw.githubusercontent.com/mrboora04/ICE3/main/app/src/main/assets/canada_restaurants.csv"
    const val FILE = "canada_restaurants.csv"
    private const val CHANNEL = "menu_updates"

    fun schedule(context: Context) {
        val work = PeriodicWorkRequestBuilder<Worker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("menu-updates", ExistingPeriodicWorkPolicy.KEEP, work)
    }

    /** The downloaded menu file, if there is one. */
    fun localFile(context: Context) = File(context.filesDir, FILE)

    /** Downloads menus; returns the requests that are now available. */
    suspend fun refresh(context: Context): List<FoodRequest> = withContext(Dispatchers.IO) {
        val text = download(URL_MENUS) ?: return@withContext emptyList()
        val rows = text.lineSequence().mapNotNull(RestaurantFoods::parseCanadianLine).toList()
        // Refuse anything that doesn't look like the real file (empty, an error page, a truncated download).
        if (rows.size < 1000) return@withContext emptyList()
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.writeText(text)
        tmp.renameTo(localFile(context))
        val found = fulfilled(Requests.all(context), rows)
        Requests.markDone(context, found.map { it.id }.toSet())
        found
    }

    /** Pending requests whose words all appear in some restaurant row (chain + item). */
    fun fulfilled(requests: List<FoodRequest>, rows: List<FoodItem>): List<FoodRequest> {
        val haystacks = rows.map { Spelling.words("${it.brand} ${it.name}").toSet() }
        return requests.filter { r ->
            !r.done && r.terms.isNotEmpty() && haystacks.any { words -> r.terms.all { t -> words.any { it.startsWith(t) } } }
        }
    }

    private fun download(url: String): String? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        try {
            if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    fun notify(context: Context, found: List<FoodRequest>) {
        if (found.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Menu updates", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (found.size == 1) "${found[0].label} has been added" else "${found.size} foods you asked for have been added"
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("Check it out in Plate, or add the food you wanted.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(found.first().id.toInt(), n) }
    }

    class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            notify(applicationContext, refresh(applicationContext))
            return Result.success()
        }
    }
}
