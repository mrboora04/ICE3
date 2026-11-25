package com.mrboora.plate.data

import kotlin.math.abs

/**
 * Body-weight forecast, worked out offline from the diary, step by step:
 *
 * 1. **You eat**: average kcal over your recent fully logged days (today is left out; it isn't over).
 * 2. **You burn**: resting energy (Mifflin-St Jeor) × your activity factor, at today's weight.
 * 3. **Balance**: eat − burn, per day.
 * 4. **Pace**: about 7,700 kcal is 1 kg of body weight, so balance × 7 ÷ 7,700 = kg per week.
 * 5. **Forecast**: simulated day by day. As you get lighter you burn a little less (and more when
 *    heavier), so weight curves toward a **settling point**, where burn equals what you eat.
 * 6. **Scale check**: the trend of your actual weigh-ins, to compare with what the log predicts.
 */
object Forecast {
    const val KCAL_PER_KG = 7700f
    /** Days of diary used for "you eat". */
    const val WINDOW_DAYS = 14
    /** Fewer fully logged days than this and the average isn't trusted. */
    const val MIN_DAYS = 3
    private const val MAX_DAYS = 730

    data class Result(
        val currentKg: Float,
        /** kcal per day used for the forecast (your average, or the what-if value). */
        val intake: Float,
        val burn: Float,
        val balance: Float,
        val kgPerWeek: Float,
        val in4Weeks: Float,
        val in12Weeks: Float,
        /** Day the goal is reached at this intake, or null if it isn't. */
        val goalDay: Long?,
        /** Where weight levels off at this intake. */
        val settlesKg: Float,
        /** Weight at the end of each week for 12 weeks (index 0 = now). */
        val curve: List<Float>,
    )

    /** Average kcal over recent fully logged days before [today], and how many days that was. */
    fun averageIntake(history: List<DaySummary>, today: Long, window: Int = WINDOW_DAYS): Pair<Float, Int>? {
        val days = history.filter { it.day in (today - window) until today && it.kcal > 0f }
        if (days.size < MIN_DAYS) return null
        return days.map { it.kcal }.average().toFloat() to days.size
    }

    fun burn(p: Profile, kg: Float): Float = Nutrition.bmr(p.sex, kg, p.heightCm, p.age) * p.activity.factor

    /** Weight at which burn equals [intake]: solve (10·w + 6.25·h − 5·age + s) × activity = intake. */
    fun settlingKg(p: Profile, intake: Float): Float {
        val s = if (p.sex == Sex.MALE) 5f else -161f
        return (intake / p.activity.factor - 6.25f * p.heightCm + 5f * p.age - s) / 10f
    }

    /** What the forecast is based on: average kcal eaten and workout kcal per logged day, and how many days. */
    data class Basis(val intake: Float, val workout: Float, val days: Int) {
        /** No finished day logged yet, so it's today so far. */
        val todayOnly: Boolean get() = days == 0
    }

    /**
     * The average of every finished day logged so far (today isn't counted until it's over: an
     * unfinished day always looks too low). With no finished day yet, today so far.
     */
    fun basis(history: List<DaySummary>, workoutsByDay: Map<Long, Int>, today: Long, todayEaten: Float, todayWorkout: Float): Basis {
        val past = history.filter { it.day < today && it.kcal > 0f }
        if (past.isEmpty()) return Basis(todayEaten, todayWorkout, 0)
        return Basis(
            intake = past.map { it.kcal }.average().toFloat(),
            workout = past.sumOf { workoutsByDay[it.day] ?: 0 }.toFloat() / past.size,
            days = past.size,
        )
    }

    /** Everyday movement without exercise: resting energy × 1.2. Workouts are added on top from the log. */
    const val DAILY_LIFE = 1.2f
    fun dailyLife(p: Profile, kg: Float): Float = Nutrition.bmr(p.sex, kg, p.heightCm, p.age) * DAILY_LIFE

    /**
     * "If every day were like today": today's real intake against resting × 1.2 plus today's
     * logged workouts. No activity-level guess.
     */
    fun today(p: Profile, currentKg: Float, eaten: Float, workoutKcal: Float, today: Long): Result =
        run(p, currentKg, eaten, today) { w -> dailyLife(p, w) + workoutKcal }

    fun run(p: Profile, currentKg: Float, intake: Float, today: Long, burnOf: (Float) -> Float = { burn(p, it) }): Result {
        val burnNow = burnOf(currentKg)
        val balance = intake - burnNow
        var w = currentKg
        val curve = mutableListOf(w)
        var goalDay: Long? = null
        val losing = p.goalWeightKg < currentKg
        for (d in 1..MAX_DAYS) {
            w += (intake - burnOf(w)) / KCAL_PER_KG
            if (goalDay == null && p.goal != Goal.MAINTAIN && abs(p.goalWeightKg - currentKg) >= 0.1f &&
                (if (losing) w <= p.goalWeightKg else w >= p.goalWeightKg)
            ) goalDay = today + d
            if (d % 7 == 0 && d / 7 <= 12) curve += w
            if (d >= 84 && goalDay != null) break
        }
        return Result(
            currentKg = currentKg, intake = intake, burn = burnNow, balance = balance,
            kgPerWeek = balance * 7f / KCAL_PER_KG,
            in4Weeks = curve[4], in12Weeks = curve[12], goalDay = goalDay,
            settlesKg = settlingKg(p, intake), curve = curve,
        )
    }

    /**
     * What the scale says: kg per week from a straight-line fit through weigh-ins of the
     * last [days] days. Needs at least two weigh-ins at least a week apart.
     */
    fun scaleTrend(weights: List<WeightEntry>, today: Long, days: Int = 42): Float? {
        val recent = weights.filter { it.day > today - days }
        if (recent.size < 2 || recent.maxOf { it.day } - recent.minOf { it.day } < 7) return null
        val xs = recent.map { it.day.toDouble() }
        val ys = recent.map { it.kg.toDouble() }
        val mx = xs.average()
        val my = ys.average()
        val num = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        val den = xs.sumOf { (it - mx) * (it - mx) }
        if (den == 0.0) return null
        return (num / den * 7).toFloat()
    }
}
