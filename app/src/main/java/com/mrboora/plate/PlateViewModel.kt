package com.mrboora.plate

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrboora.plate.data.CanadianFoods
import com.mrboora.plate.data.DayTotal
import com.mrboora.plate.data.FoodCatalog
import com.mrboora.plate.data.FoodEntry
import com.mrboora.plate.data.FoodItem
import com.mrboora.plate.data.Meal
import com.mrboora.plate.data.Nutrition
import com.mrboora.plate.data.OpenFoodFacts
import com.mrboora.plate.data.PlateDatabase
import com.mrboora.plate.data.Profile
import com.mrboora.plate.data.RestaurantFoods
import com.mrboora.plate.data.SavedFood
import com.mrboora.plate.data.Targets
import com.mrboora.plate.data.Water
import com.mrboora.plate.data.WeightEntry
import com.mrboora.plate.data.toItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed interface ProfileState {
    data object Loading : ProfileState
    data class Ready(val profile: Profile?) : ProfileState
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlateViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = PlateDatabase.get(app).dao()
    private val canadian = CanadianFoods(app)
    private val restaurants = RestaurantFoods(app)

    suspend fun restaurantChains(): List<Pair<String, Int>> = withContext(Dispatchers.Default) { restaurants.chains() }

    suspend fun restaurantMenu(chain: String): List<FoodItem> = withContext(Dispatchers.Default) { restaurants.menu(chain) }

    suspend fun shelfFoods(groups: Set<String>): List<FoodItem> = withContext(Dispatchers.Default) { canadian.inGroups(groups) }

    val profileState: StateFlow<ProfileState> = dao.profile()
        .map<Profile?, ProfileState> { ProfileState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ProfileState.Loading)

    private val _day = MutableStateFlow(today())
    val day: StateFlow<Long> = _day

    val entries: StateFlow<List<FoodEntry>> = _day.flatMapLatest { dao.entries(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val waterMl: StateFlow<Int> = _day.flatMapLatest { d -> dao.water(d).map { it?.ml ?: 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val weights: StateFlow<List<WeightEntry>> = dao.weights()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Last 7 days ending today, with zero-filled gaps. */
    val week: StateFlow<List<DayTotal>> = dao.dailyTotals(today() - 6, today())
        .map { totals ->
            val byDay = totals.associateBy { it.day }
            (today() - 6..today()).map { byDay[it] ?: DayTotal(it, 0f) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentFoods: StateFlow<List<FoodItem>> = dao.recentFoods().map { list -> list.map { it.toItem() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The latest logged weight, falling back to the profile's weight. */
    val currentWeight: StateFlow<Float?> = combine(profileState, weights) { s, w ->
        w.lastOrNull()?.kg ?: (s as? ProfileState.Ready)?.profile?.weightKg
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val targets: StateFlow<Targets?> = combine(profileState, currentWeight) { s, w ->
        val p = (s as? ProfileState.Ready)?.profile ?: return@combine null
        Nutrition.targets(if (w != null) p.copy(weightKg = w) else p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun shiftDay(by: Long) {
        _day.value = (_day.value + by).coerceAtMost(today())
    }

    fun goToToday() { _day.value = today() }

    fun saveProfile(profile: Profile, logWeight: Boolean) = viewModelScope.launch {
        dao.saveProfile(profile)
        if (logWeight) dao.saveWeight(WeightEntry(today(), profile.weightKg))
    }

    fun addEntry(meal: Meal, food: FoodItem, grams: Float) = viewModelScope.launch {
        dao.addEntry(
            FoodEntry(
                day = _day.value, meal = meal, name = food.name, brand = food.brand, grams = grams,
                unit = if (food.perItem) "item" else "g",
                kcal = food.kcalFor(grams), protein = food.proteinFor(grams),
                carbs = food.carbsFor(grams), fat = food.fatFor(grams),
            )
        )
        rememberFood(food)
    }

    fun deleteEntry(entry: FoodEntry) = viewModelScope.launch { dao.deleteEntry(entry) }

    fun restoreEntry(entry: FoodEntry) = viewModelScope.launch { dao.addEntry(entry.copy(id = 0)) }

    fun createFood(food: FoodItem) = viewModelScope.launch {
        dao.saveFood(food.toSaved(custom = true))
    }

    fun forgetFood(food: FoodItem) = viewModelScope.launch { dao.deleteFood(food.key) }

    private suspend fun rememberFood(food: FoodItem) {
        val existing = dao.savedFood(food.key)
        dao.saveFood(food.toSaved(custom = existing?.custom == true || food.source == FoodItem.Source.CUSTOM))
    }

    fun addWater(deltaMl: Int) = viewModelScope.launch {
        val d = _day.value
        val now = dao.waterNow(d)?.ml ?: 0
        dao.saveWater(Water(d, (now + deltaMl).coerceIn(0, 10_000)))
    }

    fun logWeight(kg: Float) = viewModelScope.launch { dao.saveWeight(WeightEntry(today(), kg)) }

    fun deleteWeight(entry: WeightEntry) = viewModelScope.launch { dao.deleteWeight(entry) }

    suspend fun searchLocal(query: String): List<FoodItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val saved = dao.searchSaved(q).map { it.toItem() }
        val keys = saved.map { it.key }.toMutableSet()
        val everyday = FoodCatalog.search(q).filter { keys.add(it.key) }
        val (chains, canada) = withContext(Dispatchers.Default) { restaurants.search(q) to canadian.search(q) }
        return saved + chains.filter { keys.add(it.key) } + everyday + canada.filter { keys.add(it.key) }
    }

    suspend fun searchOnline(query: String): Result<List<FoodItem>> = runCatching { OpenFoodFacts.search(query) }

    suspend fun lookupBarcode(code: String): Result<FoodItem?> = runCatching { OpenFoodFacts.barcode(code) }

    fun resetAll() = viewModelScope.launch {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            PlateDatabase.get(getApplication()).clearAllTables()
        }
    }

    private fun FoodItem.toSaved(custom: Boolean) = SavedFood(
        key, name.trim(), brand.trim(), kcal100, protein100, carbs100, fat100, servingG, custom,
        System.currentTimeMillis(), perItem,
    )

    companion object {
        fun today(): Long = LocalDate.now().toEpochDay()
    }
}
