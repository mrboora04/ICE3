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
import kotlinx.coroutines.flow.flowOn
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

    /** A newer Plate release, if there is one. Checked when the app opens. */
    private val _update = MutableStateFlow<com.mrboora.plate.data.UpdateInfo?>(null)
    val update: StateFlow<com.mrboora.plate.data.UpdateInfo?> = _update

    /** One-off messages for the snackbar (declared before init, which uses it), e.g. after cleaning up installers. */
    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice
    fun showNotice(text: String) { _notice.value = text }
    fun noticeShown() { _notice.value = null }

    init {
        val freed = com.mrboora.plate.data.AppUpdates.cleanUpInstalled(getApplication())
        if (freed > 0) _notice.value = "Updated. Removed the old installer (%.0f MB freed)".format(freed / 1_048_576.0)
        viewModelScope.launch { _update.value = com.mrboora.plate.data.AppUpdates.check(getApplication()) }
    }

    /** "Later" hides the card; the update badge on Today stays until the app is updated. */
    private val _updateCardHidden = MutableStateFlow(false)
    val updateCardHidden: StateFlow<Boolean> = _updateCardHidden
    fun dismissUpdate() { _updateCardHidden.value = true }
    fun showUpdate() { _updateCardHidden.value = false }

    @Volatile private var vocab: Set<String>? = null

    /** A corrected query for typos ("osmaos" -> "osmows"), or null if the words are already known. */
    suspend fun didYouMean(query: String): String? = withContext(Dispatchers.Default) {
        val v = vocab ?: (restaurants.names() + canadian.names() + FoodCatalog.foods.map { it.name })
            .flatMap(com.mrboora.plate.data.Spelling::words).toSet().also { vocab = it }
        com.mrboora.plate.data.Spelling.suggest(query, v)
    }

    fun saveRequest(restaurant: String, item: String) {
        com.mrboora.plate.data.Requests.add(getApplication(), restaurant, item)
    }

    suspend fun restaurantChains(): List<com.mrboora.plate.data.Chain> = withContext(Dispatchers.Default) { restaurants.chains() }

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

    // Today's real numbers for the forecast on Profile, whatever day the diary is showing.
    val todayEntries: StateFlow<List<FoodEntry>> = dao.entries(today())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val todayWaterMl: StateFlow<Int> = dao.water(today()).map { it?.ml ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val todayWorkouts: StateFlow<List<com.mrboora.plate.data.Workout>> = dao.workouts(today())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addWorkout(kind: String, minutes: Int, kcal: Int) = viewModelScope.launch {
        dao.addWorkout(com.mrboora.plate.data.Workout(day = today(), kind = kind, minutes = minutes, kcal = kcal))
    }
    fun deleteWorkout(w: com.mrboora.plate.data.Workout) = viewModelScope.launch { dao.deleteWorkout(w) }

    val weights: StateFlow<List<WeightEntry>> = dao.weights()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Last 7 days ending today, with zero-filled gaps. */
    val week: StateFlow<List<DayTotal>> = dao.dailyTotals(today() - 6, today())
        .map { totals ->
            val byDay = totals.associateBy { it.day }
            (today() - 6..today()).map { byDay[it] ?: DayTotal(it, 0f) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentFoods: StateFlow<List<FoodItem>> = dao.recentFoods().map { list -> list.map(::current) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * A saved food as it should be shown now. Restaurant items use today's menu numbers (so a
     * correction, like A&W carbs, reaches foods logged before it); foods people made stay as entered.
     */
    private fun current(saved: SavedFood): FoodItem {
        val item = saved.toItem()
        if (saved.custom) return item
        return restaurants.find(saved.key)?.copy(source = FoodItem.Source.SAVED) ?: item
    }

    /** The latest logged weight, falling back to the profile's weight. */
    val currentWeight: StateFlow<Float?> = combine(profileState, weights) { s, w ->
        w.lastOrNull()?.kg ?: (s as? ProfileState.Ready)?.profile?.weightKg
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val targets: StateFlow<Targets?> = combine(profileState, currentWeight) { s, w ->
        val p = (s as? ProfileState.Ready)?.profile ?: return@combine null
        Nutrition.targets(if (w != null) p.copy(weightKg = w) else p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The last 120 days, one summary per logged day, newest first. */
    val history: StateFlow<List<com.mrboora.plate.data.DaySummary>> = combine(
        dao.entriesBetween(today() - 120, today()),
        dao.waterBetween(today() - 120, today()),
        weights,
        targets,
    ) { e, w, kg, t -> com.mrboora.plate.data.History.summarise(e, w, kg, t?.kcal ?: 0) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun showDay(day: Long) { _day.value = day.coerceAtMost(today()) }

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
                portion = grams,
                kcal = food.kcalFor(grams), protein = food.proteinFor(grams),
                carbs = food.carbsFor(grams), fat = food.fatFor(grams),
            )
        )
        rememberFood(food)
    }

    fun deleteEntry(entry: FoodEntry) = viewModelScope.launch { dao.deleteEntry(entry) }

    /** One more of the same portion (another glass, another slice) on the same row. */
    fun addPortion(entry: FoodEntry) = viewModelScope.launch { dao.updateEntry(entry.withAmount(entry.grams + entry.step)) }

    /** Sets an exact amount; calories and macros scale with it. */
    fun setAmount(entry: FoodEntry, amount: Float) = viewModelScope.launch {
        if (amount > 0f) dao.updateEntry(entry.withAmount(amount))
    }

    fun restoreEntry(entry: FoodEntry) = viewModelScope.launch { dao.addEntry(entry.copy(id = 0)) }

    fun createFood(food: FoodItem) = viewModelScope.launch {
        dao.saveFood(food.toSaved(custom = true))
    }

    // ---- Make-it-yourself (shake builder) ----

    /** The builder's ingredients by exact Health Canada name, with mixed berries and water worked out. */
    suspend fun mixIngredients(): Map<String, FoodItem> = withContext(Dispatchers.Default) {
        val names = (com.mrboora.plate.data.Mix.FRUITS + com.mrboora.plate.data.Mix.EXTRAS + com.mrboora.plate.data.Mix.BASES + com.mrboora.plate.data.Mix.TOPPINGS)
            .map { it.second }.toSet() + com.mrboora.plate.data.Mix.BERRY_PARTS + com.mrboora.plate.data.Mix.CRUST_FOODS + com.mrboora.plate.data.Templates.foodNames
        // Health Canada first; Plate's own list for things it lacks (paneer).
        val found = names.mapNotNull { n -> (canadian.byName(n) ?: FoodCatalog.foods.firstOrNull { it.name == n })?.let { n to it } }.toMap().toMutableMap()
        com.mrboora.plate.data.Mix.mixedBerries(com.mrboora.plate.data.Mix.BERRY_PARTS.mapNotNull { found[it] })
            ?.let { found[com.mrboora.plate.data.Mix.MIXED_BERRIES] = it }
        found[com.mrboora.plate.data.Mix.WATER] = com.mrboora.plate.data.Mix.water
        found
    }

    /** Any Health Canada food as an ingredient (per 100 g, so restaurant items are left out). */
    suspend fun searchIngredients(query: String): List<FoodItem> =
        withContext(Dispatchers.Default) { canadian.search(query.trim(), 12) }

    /** Reads a homemade mix typed in plain English ("1 banana with 30 g berries and 400 ml milk"). */
    suspend fun parseMix(text: String): com.mrboora.plate.data.MixParser.Draft {
        val foods = mixIngredients()
        return withContext(Dispatchers.Default) {
            com.mrboora.plate.data.MixParser.parse(text, foods) { name -> canadian.search(name, 1).firstOrNull() }
        }
    }

    /** Saves the mix as one of your foods and logs one glass of it. */
    fun saveMix(meal: Meal, food: FoodItem, count: Float = 1f) = viewModelScope.launch {
        dao.saveFood(food.toSaved(custom = true))
        addEntry(meal, food, count)
    }

    fun forgetFood(food: FoodItem) = viewModelScope.launch { dao.deleteFood(food.key) }

    private suspend fun rememberFood(food: FoodItem) {
        val existing = dao.savedFood(food.key)
        dao.saveFood(food.toSaved(custom = existing?.custom == true || food.source == FoodItem.Source.CUSTOM))
    }

    private val _waterContainers = MutableStateFlow(com.mrboora.plate.data.WaterContainers.load(app))
    val waterContainers: StateFlow<List<com.mrboora.plate.data.WaterContainers.Container>> = _waterContainers
    fun saveWaterContainers(list: List<com.mrboora.plate.data.WaterContainers.Container>) {
        com.mrboora.plate.data.WaterContainers.save(getApplication(), list)
        _waterContainers.value = com.mrboora.plate.data.WaterContainers.load(getApplication())
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
        val saved = withContext(Dispatchers.Default) { dao.searchSaved(q).map(::current) }
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
            com.mrboora.plate.data.Selfies.deleteAll(getApplication())
            com.mrboora.plate.data.Selfies.setLocked(getApplication(), false)
            com.mrboora.plate.data.Selfies.setReminder(getApplication(), false)
        }
        refreshSelfies()
    }

    // ---- Selfie of the day ----------------------------------------------------------------

    private val _selfieDays = kotlinx.coroutines.flow.MutableStateFlow<List<Long>>(emptyList())
    /** Days with a selfie, oldest first. */
    val selfieDays: kotlinx.coroutines.flow.StateFlow<List<Long>> = _selfieDays

    /** Bumped whenever a selfie changes, so pictures on screen reload. */
    private val _selfieVersion = kotlinx.coroutines.flow.MutableStateFlow(0)
    val selfieVersion: kotlinx.coroutines.flow.StateFlow<Int> = _selfieVersion

    /** With the lock on: true once the phone's screen lock was confirmed, until Plate leaves the screen. */
    private val _selfiesUnlocked = kotlinx.coroutines.flow.MutableStateFlow(false)
    val selfiesUnlocked: kotlinx.coroutines.flow.StateFlow<Boolean> = _selfiesUnlocked
    fun unlockSelfies() { _selfiesUnlocked.value = true }
    fun lockSelfies() { _selfiesUnlocked.value = false }

    fun refreshSelfies() = viewModelScope.launch {
        _selfieDays.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.mrboora.plate.data.Selfies.days(getApplication())
        }
        _selfieVersion.value++
    }

    /** Saves the camera's photo, or [picked] from the gallery, as [day]'s selfie. */
    fun saveSelfie(day: Long, picked: android.net.Uri? = null, done: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val ok = if (picked == null) com.mrboora.plate.data.Selfies.saveCapture(getApplication(), day)
        else com.mrboora.plate.data.Selfies.save(getApplication(), picked, day)
        refreshSelfies()
        done(ok)
    }

    fun deleteSelfie(day: Long) = viewModelScope.launch {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.mrboora.plate.data.Selfies.delete(getApplication(), day) }
        refreshSelfies()
    }

    // Declared after the selfie state above, so it exists when this runs.
    init {
        refreshSelfies()
    }

    private fun FoodItem.toSaved(custom: Boolean) = SavedFood(
        key, name.trim(), brand.trim(), kcal100, protein100, carbs100, fat100, servingG, custom,
        System.currentTimeMillis(), perItem, estimated,
    )

    companion object {
        fun today(): Long = LocalDate.now().toEpochDay()
    }
}
