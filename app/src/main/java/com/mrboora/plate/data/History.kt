package com.mrboora.plate.data

/** How a day went against the calorie target. */
enum class DayStatus { ON_TARGET, OVER, UNDER, NOT_LOGGED }

/** One day of the diary, summarised for the Progress screen. */
data class DaySummary(
    val day: Long,
    val kcal: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val waterMl: Int,
    val weightKg: Float?,
    /** Foods eaten that day, biggest first (same food added up). */
    val foods: List<Pair<String, Float>>,
    val status: DayStatus,
)

object History {
    /**
     * On target: from 70% of the goal up to 5% over it. Over: more than 5% over.
     * Under: less than 70%, since eating far too little isn't the goal either.
     */
    fun status(kcal: Float, target: Int): DayStatus = when {
        kcal <= 0f || target <= 0 -> DayStatus.NOT_LOGGED
        kcal > target * 1.05f -> DayStatus.OVER
        kcal < target * 0.70f -> DayStatus.UNDER
        else -> DayStatus.ON_TARGET
    }

    fun summarise(
        entries: List<FoodEntry>,
        water: List<Water>,
        weights: List<WeightEntry>,
        target: Int,
    ): List<DaySummary> {
        val waterByDay = water.associate { it.day to it.ml }
        val weightByDay = weights.associate { it.day to it.kg }
        return entries.groupBy { it.day }.map { (day, list) ->
            val kcal = list.sumOf { it.kcal.toDouble() }.toFloat()
            DaySummary(
                day = day,
                kcal = kcal,
                protein = list.sumOf { it.protein.toDouble() }.toFloat(),
                carbs = list.sumOf { it.carbs.toDouble() }.toFloat(),
                fat = list.sumOf { it.fat.toDouble() }.toFloat(),
                waterMl = waterByDay[day] ?: 0,
                weightKg = weightByDay[day],
                foods = list.groupBy { it.name }.map { (n, es) -> n to es.sumOf { it.kcal.toDouble() }.toFloat() }
                    .sortedByDescending { it.second },
                status = status(kcal, target),
            )
        }.sortedByDescending { it.day }
    }

    /**
     * On-target days in a row, counting back from today. Days with nothing logged are skipped
     * (they don't break it); an over or under day ends it.
     */
    fun streak(days: List<DaySummary>): Int {
        var n = 0
        for (d in days.sortedByDescending { it.day }) {
            when (d.status) {
                DayStatus.ON_TARGET -> n++
                DayStatus.NOT_LOGGED -> continue
                else -> return n
            }
        }
        return n
    }
}
