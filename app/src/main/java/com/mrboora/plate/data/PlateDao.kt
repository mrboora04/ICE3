package com.mrboora.plate.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlateDao {
    @Query("SELECT * FROM profile WHERE id = 0")
    fun profile(): Flow<Profile?>

    @Upsert
    suspend fun saveProfile(profile: Profile)

    @Query("SELECT * FROM food_entry WHERE day = :day ORDER BY createdAt")
    fun entries(day: Long): Flow<List<FoodEntry>>

    @Insert
    suspend fun addEntry(entry: FoodEntry)

    @Delete
    suspend fun deleteEntry(entry: FoodEntry)

    @androidx.room.Update
    suspend fun updateEntry(entry: FoodEntry)

    @Query("SELECT day, SUM(kcal) AS kcal FROM food_entry WHERE day BETWEEN :from AND :to GROUP BY day")
    fun dailyTotals(from: Long, to: Long): Flow<List<DayTotal>>

    @Query(
        "SELECT * FROM saved_food WHERE name LIKE '%' || :q || '%' OR brand LIKE '%' || :q || '%' " +
            "ORDER BY custom DESC, lastUsed DESC LIMIT 30"
    )
    suspend fun searchSaved(q: String): List<SavedFood>

    @Query("SELECT * FROM saved_food ORDER BY lastUsed DESC LIMIT 20")
    fun recentFoods(): Flow<List<SavedFood>>

    @Query("SELECT * FROM saved_food WHERE `key` = :key")
    suspend fun savedFood(key: String): SavedFood?

    @Upsert
    suspend fun saveFood(food: SavedFood)

    @Query("DELETE FROM saved_food WHERE `key` = :key")
    suspend fun deleteFood(key: String)

    @Query("SELECT * FROM weight ORDER BY day")
    fun weights(): Flow<List<WeightEntry>>

    @Upsert
    suspend fun saveWeight(entry: WeightEntry)

    @Delete
    suspend fun deleteWeight(entry: WeightEntry)

    @Query("SELECT * FROM water WHERE day = :day")
    fun water(day: Long): Flow<Water?>

    @Query("SELECT * FROM water WHERE day = :day")
    suspend fun waterNow(day: Long): Water?

    @Upsert
    suspend fun saveWater(water: Water)
}
