package com.mrboora.plate.data

import kotlin.math.max
import kotlin.math.roundToInt

data class Targets(val kcal: Int, val proteinG: Int, val carbsG: Int, val fatG: Int, val bmr: Int, val tdee: Int)

object Nutrition {
    /** About 7,700 kcal per kg of body fat. */
    private const val KCAL_PER_KG = 7700f

    /** Mifflin-St Jeor resting energy. */
    fun bmr(sex: Sex, weightKg: Float, heightCm: Float, age: Int): Float =
        10f * weightKg + 6.25f * heightCm - 5f * age + if (sex == Sex.MALE) 5f else -161f

    fun targets(p: Profile): Targets {
        val bmr = bmr(p.sex, p.weightKg, p.heightCm, p.age)
        val tdee = bmr * p.activity.factor
        val delta = p.paceKgPerWeek * KCAL_PER_KG / 7f
        val raw = when (p.goal) {
            Goal.LOSE -> tdee - delta
            Goal.MAINTAIN -> tdee
            Goal.GAIN -> tdee + delta
        }
        val floor = if (p.sex == Sex.MALE) 1500f else 1200f
        val kcal = (max(raw, floor) / 10f).roundToInt() * 10

        // Protein 1.6 g per kg (capped at 2.2 g/kg of goal weight), fat 28% of energy, carbs the rest.
        val proteinG = minOf(1.6f * p.weightKg, 2.2f * p.goalWeightKg).roundToInt()
        val fatG = (kcal * 0.28f / 9f).roundToInt()
        val carbsG = max(0, ((kcal - proteinG * 4 - fatG * 9) / 4f).roundToInt())
        return Targets(kcal, proteinG, carbsG, fatG, bmr.roundToInt(), tdee.roundToInt())
    }

    fun bmi(weightKg: Float, heightCm: Float): Float {
        val m = heightCm / 100f
        return if (m <= 0f) 0f else weightKg / (m * m)
    }

    fun bmiLabel(bmi: Float): String = when {
        bmi <= 0f -> "—"
        bmi < 18.5f -> "Underweight"
        bmi < 25f -> "Healthy range"
        bmi < 30f -> "Overweight"
        else -> "Obese range"
    }

    /** Weeks to reach the goal weight at the chosen pace, or null if not applicable. */
    fun weeksToGoal(p: Profile, currentKg: Float): Int? {
        if (p.goal == Goal.MAINTAIN || p.paceKgPerWeek <= 0f) return null
        val diff = if (p.goal == Goal.LOSE) currentKg - p.goalWeightKg else p.goalWeightKg - currentKg
        if (diff <= 0f) return 0
        return kotlin.math.ceil(diff / p.paceKgPerWeek).toInt()
    }
}
