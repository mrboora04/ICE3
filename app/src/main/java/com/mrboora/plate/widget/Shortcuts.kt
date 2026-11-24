package com.mrboora.plate.widget

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.mrboora.plate.MainActivity
import com.mrboora.plate.R
import com.mrboora.plate.data.WaterContainers

/**
 * Long-press shortcuts on the Plate icon: add your usual water, add food, take today's selfie,
 * log weight. The water one is named after the user's own first container ("+ Bottle 500 ml").
 */
object Shortcuts {
    const val WATER = "com.mrboora.plate.shortcut.WATER"
    const val FOOD = "com.mrboora.plate.shortcut.FOOD"
    const val SELFIE = "com.mrboora.plate.shortcut.SELFIE"
    const val WEIGHT = "com.mrboora.plate.shortcut.WEIGHT"

    fun publish(context: Context) {
        val first = WaterContainers.load(context).first()
        val icon = IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        fun make(id: String, short: String, long: String) = ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(short).setLongLabel(long).setIcon(icon)
            .setIntent(Intent(context, MainActivity::class.java).setAction(id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            .build()
        val list = listOf(
            make(WATER, "+ ${first.name} ${first.ml} ml", "Add ${first.ml} ml of water"),
            make(FOOD, "Add food", "Add food"),
            make(SELFIE, "Selfie of the day", "Take today's selfie"),
            make(WEIGHT, "Log weight", "Log today's weight"),
        )
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, list) }
    }
}
