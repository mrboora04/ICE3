package com.mrboora.plate.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class Sex(val label: String) { FEMALE("Female"), MALE("Male") }

enum class Activity(val label: String, val detail: String, val factor: Float) {
    SEDENTARY("Sedentary", "Desk job, little exercise", 1.2f),
    LIGHT("Lightly active", "Exercise 1–3 days a week", 1.375f),
    MODERATE("Moderately active", "Exercise 3–5 days a week", 1.55f),
    VERY("Very active", "Hard exercise 6–7 days a week", 1.725f),
    EXTRA("Extra active", "Physical job plus training", 1.9f),
}

enum class Goal(val label: String) { LOSE("Lose"), MAINTAIN("Maintain"), GAIN("Gain") }

enum class Meal(val label: String) { BREAKFAST("Breakfast"), LUNCH("Lunch"), DINNER("Dinner"), SNACK("Snacks") }

@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey val id: Int = 0,
    val name: String,
    val sex: Sex,
    val age: Int,
    val heightCm: Float,
    val weightKg: Float,
    val goalWeightKg: Float,
    val activity: Activity,
    val goal: Goal,
    val paceKgPerWeek: Float,
    val waterGoalMl: Int = 2000,
)

@Entity(tableName = "food_entry")
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val day: Long,
    val meal: Meal,
    val name: String,
    val brand: String,
    val grams: Float,
    val kcal: Float,
    val protein: Float,
    val carbs: Float,
    val fat: Float,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A food the user has created or used before. Values are per 100 g. */
@Entity(tableName = "saved_food")
data class SavedFood(
    @PrimaryKey val key: String,
    val name: String,
    val brand: String,
    val kcal100: Float,
    val protein100: Float,
    val carbs100: Float,
    val fat100: Float,
    val servingG: Float,
    val custom: Boolean,
    val lastUsed: Long,
)

@Entity(tableName = "weight")
data class WeightEntry(@PrimaryKey val day: Long, val kg: Float)

@Entity(tableName = "water")
data class Water(@PrimaryKey val day: Long, val ml: Int)

data class DayTotal(val day: Long, val kcal: Float)

/** Any food shown in search results, from any source. Values are per 100 g. */
data class FoodItem(
    val name: String,
    val brand: String = "",
    val kcal100: Float,
    val protein100: Float,
    val carbs100: Float,
    val fat100: Float,
    val servingG: Float = 100f,
    val servingLabel: String = "",
    val source: Source = Source.BUILT_IN,
) {
    enum class Source { BUILT_IN, SAVED, CUSTOM, ONLINE }

    val key: String get() = "${name.trim().lowercase()}|${brand.trim().lowercase()}"

    fun kcalFor(g: Float) = kcal100 * g / 100f
    fun proteinFor(g: Float) = protein100 * g / 100f
    fun carbsFor(g: Float) = carbs100 * g / 100f
    fun fatFor(g: Float) = fat100 * g / 100f
}

fun SavedFood.toItem() = FoodItem(
    name, brand, kcal100, protein100, carbs100, fat100, servingG,
    source = if (custom) FoodItem.Source.CUSTOM else FoodItem.Source.SAVED,
)
