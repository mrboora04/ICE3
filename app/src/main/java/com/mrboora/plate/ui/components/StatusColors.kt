package com.mrboora.plate.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.mrboora.plate.data.DayStatus
import com.mrboora.plate.ui.theme.PlateColors

/**
 * A day's colours, the same everywhere (day cards, the month calendar, Today): green on target,
 * red over, blue under. [strong] is for bars, badges and text; [soft] fills a whole card with a
 * light wash of it, mixed into the card colour so it works in light and dark mode.
 */
object StatusColors {
    /** How much of the strong colour goes into a card: light enough to read on. */
    const val WASH = 0.22f

    fun strong(s: DayStatus, c: PlateColors): Color = when (s) {
        DayStatus.ON_TARGET -> c.good
        DayStatus.OVER -> c.over
        DayStatus.UNDER -> c.water
        DayStatus.NOT_LOGGED -> c.muted
    }

    fun soft(s: DayStatus, c: PlateColors): Color =
        if (s == DayStatus.NOT_LOGGED) c.card else lerp(c.card, strong(s, c), WASH)
}
