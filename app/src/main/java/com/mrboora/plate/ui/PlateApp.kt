package com.mrboora.plate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mrboora.plate.PlateViewModel
import com.mrboora.plate.ProfileState
import com.mrboora.plate.data.Meal
import com.mrboora.plate.data.Nutrition
import com.mrboora.plate.data.Profile
import com.mrboora.plate.ui.screens.AddFoodScreen
import com.mrboora.plate.ui.screens.OnboardingScreen
import com.mrboora.plate.ui.screens.ProfileForm
import com.mrboora.plate.ui.screens.ProfileScreen
import com.mrboora.plate.ui.screens.ProgressScreen
import com.mrboora.plate.ui.screens.TodayScreen
import com.mrboora.plate.ui.screens.dayLabel
import com.mrboora.plate.ui.theme.Plate
import kotlinx.coroutines.launch

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Rounded.Restaurant),
    PROGRESS("progress", "Progress", Icons.AutoMirrored.Rounded.ShowChart),
    PROFILE("profile", "Profile", Icons.Rounded.Person),
}

@Composable
fun PlateApp(vm: PlateViewModel = viewModel()) {
    val state by vm.profileState.collectAsStateWithLifecycle()
    val units by vm.units.collectAsStateWithLifecycle()
    androidx.compose.runtime.CompositionLocalProvider(com.mrboora.plate.ui.components.LocalUnits provides units) {
        Box(Modifier.fillMaxSize().background(Plate.colors.ground)) {
            when (val s = state) {
                ProfileState.Loading -> Unit
                is ProfileState.Ready ->
                    if (s.profile == null) OnboardingScreen(onUnits = vm::setUnits) { vm.saveProfile(it, logWeight = true) }
                    else Main(vm, s.profile)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Main(vm: PlateViewModel, profile: Profile) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val targets = vm.targets.collectAsStateWithLifecycle().value ?: Nutrition.targets(profile)
    val day by vm.day.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val water by vm.waterMl.collectAsStateWithLifecycle()
    val weights by vm.weights.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val recent by vm.recentFoods.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val updateCardHidden by vm.updateCardHidden.collectAsStateWithLifecycle()
    val currentKg = vm.currentWeight.collectAsStateWithLifecycle().value ?: profile.weightKg
    val waterContainers by vm.waterContainers.collectAsStateWithLifecycle()
    val units by vm.units.collectAsStateWithLifecycle()
    val todayMeals by vm.todayEntries.collectAsStateWithLifecycle()
    val workoutsByDay by vm.workoutsByDay.collectAsStateWithLifecycle()
    val todayWater by vm.todayWaterMl.collectAsStateWithLifecycle()
    val todayWorkouts by vm.todayWorkouts.collectAsStateWithLifecycle()
    val selfieDays by vm.selfieDays.collectAsStateWithLifecycle()
    val selfieVersion by vm.selfieVersion.collectAsStateWithLifecycle()
    val selfiesShown by vm.selfiesUnlocked.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    val notifyPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }

    // One-off messages from the app (e.g. "removed the old installer").
    val notice by vm.notice.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(notice) {
        notice?.let { snackbar.showSnackbar(it, withDismissAction = true); vm.noticeShown() }
    }

    // "Clean up old Plate installers": Android lets Plate delete only files you pick, so open the
    // file picker on Downloads; only files named Plate….apk are deleted.
    val pickInstallers = androidx.activity.compose.rememberLauncherForActivityResult(
        object : androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments() {
            override fun createIntent(context: android.content.Context, input: Array<String>) = super.createIntent(context, input).apply {
                putExtra(
                    android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                    android.provider.DocumentsContract.buildRootUri("com.android.providers.downloads.documents", "downloads"),
                )
            }
        },
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val (count, bytes, skipped) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.mrboora.plate.data.AppUpdates.deletePicked(context, uris)
            }
            val mb = "%.0f MB".format(bytes / 1_048_576.0)
            snackbar.showSnackbar(
                when {
                    count == 0 && skipped > 0 -> "Nothing deleted: only files named Plate….apk can be removed here."
                    skipped > 0 -> "Deleted $count old installer${if (count == 1) "" else "s"} ($mb). Skipped $skipped other file${if (skipped == 1) "" else "s"}."
                    else -> "Deleted $count old installer${if (count == 1) "" else "s"}, $mb freed."
                },
                withDismissAction = true,
            )
        }
    }

    // Opened from the update notification: start the download right away.
    val updateRequested by com.mrboora.plate.MainActivity.updateRequested
    androidx.compose.runtime.LaunchedEffect(updateRequested, update) {
        val info = update
        if (updateRequested && info != null) {
            com.mrboora.plate.MainActivity.updateRequested.value = false
            vm.showUpdate()
            com.mrboora.plate.data.AppUpdates.oneTap(context, info)
        }
    }

    // ---- Selfie of the day: blurred until asked, behind the screen lock if that's on, hidden again when Plate leaves the screen.
    var selfieLock by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.mrboora.plate.data.Selfies.locked(context)) }
    var selfieReminder by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.mrboora.plate.data.Selfies.reminder(context)) }
    var selfieHour by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(com.mrboora.plate.data.Selfies.reminderHour(context)) }
    var comparing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { vm.lockSelfies(); comparing = false }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val revealSelfies = com.mrboora.plate.ui.components.rememberSelfieUnlock(selfieLock) { vm.unlockSelfies() }
    var compareAfterUnlock by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(selfiesShown, compareAfterUnlock) {
        if (selfiesShown && compareAfterUnlock) { compareAfterUnlock = false; comparing = true }
    }
    fun selfieSaved(ok: Boolean) {
        if (ok) vm.unlockSelfies() // you just took it: show it
        else scope.launch { snackbar.showSnackbar("Couldn't save that photo. Try another one.") }
    }
    val takers = com.mrboora.plate.ui.components.rememberSelfieTakers(
        onCaptured = { vm.saveSelfie(day, done = ::selfieSaved) },
        onNoCamera = { scope.launch { snackbar.showSnackbar("No camera app found. Pick a photo instead.") } },
    )
    if (comparing) com.mrboora.plate.ui.components.SelfieCompare(selfieDays, selfieVersion) { comparing = false }

    // Long-press shortcuts on the Plate icon.
    var openWeightLog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val shortcut by com.mrboora.plate.MainActivity.shortcut
    androidx.compose.runtime.LaunchedEffect(shortcut) {
        val s = shortcut ?: return@LaunchedEffect
        com.mrboora.plate.MainActivity.shortcut.value = null
        vm.goToToday()
        when (s) {
            com.mrboora.plate.widget.Shortcuts.WATER -> {
                val b = com.mrboora.plate.data.WaterContainers.buttons(waterContainers, units).first()
                vm.addWater(b.ml)
                snackbar.showSnackbar("Added ${b.ml} ml of water", withDismissAction = true)
            }
            com.mrboora.plate.widget.Shortcuts.FOOD -> nav.navigate("add/${Meal.forNow().name}")
            com.mrboora.plate.widget.Shortcuts.SELFIE -> takers.takePhoto()
            com.mrboora.plate.widget.Shortcuts.WEIGHT -> {
                openWeightLog = true
                nav.navigate(Tab.PROGRESS.route) { launchSingleTop = true }
            }
        }
    }

    /** Saves the request (so we can notify when it's added) and opens a pre-filled email. */
    fun sendRequest(restaurant: String, item: String, details: String) {
        vm.saveRequest(restaurant, item, details)
        if (android.os.Build.VERSION.SDK_INT >= 33) notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        val what = listOf(restaurant, item).filter { it.isNotBlank() }.joinToString(" – ").ifBlank { details.take(40) }
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        val email = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:"))
            .putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(com.mrboora.plate.data.Requests.EMAIL))
            .putExtra(android.content.Intent.EXTRA_SUBJECT, "Plate food request: $what")
            .putExtra(
                android.content.Intent.EXTRA_TEXT,
                "Please add this to Plate:\n\nRestaurant: ${restaurant.ifBlank { "-" }}\nFood/item: ${item.ifBlank { "-" }}" +
                    (if (details.isNotBlank()) "\nDetails: ${details.trim()}" else "") + "\n\nPlate $version",
            )
        try {
            context.startActivity(email)
        } catch (e: android.content.ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar("No email app found. Request saved; email ${com.mrboora.plate.data.Requests.EMAIL}.") }
        }
    }

    val showBar = Tab.entries.any { it.route == route }
    Scaffold(
        containerColor = Plate.colors.ground,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = Plate.colors.card) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Plate.colors.track),
                        )
                    }
                }
            }
        },
    ) { pad ->
        val content = PaddingValues(top = pad.calculateTopPadding(), bottom = pad.calculateBottomPadding() + 8.dp)
        NavHost(nav, startDestination = Tab.TODAY.route) {
            composable(Tab.TODAY.route) {
                TodayScreen(
                    profile, targets, day, entries, water, content,
                    onShiftDay = vm::shiftDay, onToday = vm::goToToday,
                    onAdd = { nav.navigate("add/${it.name}") },
                    onDelete = { e ->
                        vm.deleteEntry(e)
                        scope.launch {
                            if (snackbar.showSnackbar("Removed ${e.name}", "Undo", withDismissAction = true) == SnackbarResult.ActionPerformed) {
                                vm.restoreEntry(e)
                            }
                        }
                    },
                    onWater = vm::addWater,
                    onAddPortion = vm::addPortion,
                    onSetAmount = vm::setAmount,
                    updateVersion = update?.versionName,
                    onUpdateTap = { update?.let { vm.showUpdate(); com.mrboora.plate.data.AppUpdates.oneTap(context, it) } },
                    waterContainers = waterContainers,
                    onSaveContainers = vm::saveWaterContainers,
                    selfie = {
                        val has = day in selfieDays
                        // Past days only show a card when there's a photo, to keep old diaries tidy.
                        if (has || day == PlateViewModel.today()) {
                            com.mrboora.plate.ui.components.SelfieCard(
                                day = day, has = has, version = selfieVersion, revealed = selfiesShown, locked = selfieLock,
                                canCompare = selfieDays.size >= 2, takers = takers,
                                onReveal = revealSelfies, onHide = vm::lockSelfies, onCompare = { comparing = true },
                                onDelete = { vm.deleteSelfie(day) },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    },
                    banner = {
                        update?.takeIf { !updateCardHidden }?.let { info ->
                            com.mrboora.plate.ui.components.UpdateCard(info, onLater = vm::dismissUpdate, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    },
                )
            }
            composable(Tab.PROGRESS.route) {
                // The weight dialog opens once from the shortcut, then the flag is cleared.
                androidx.compose.runtime.LaunchedEffect(Unit) { openWeightLog = false }
                ProgressScreen(
                    profile, targets, weights, history, content, vm::logWeight, vm::deleteWeight,
                    startLogging = openWeightLog,
                    selfies = {
                        com.mrboora.plate.ui.components.SelfieProgressCard(
                            selfieDays,
                            onCompare = { if (selfiesShown) comparing = true else { compareAfterUnlock = true; revealSelfies() } },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    },
                    onOpenDay = { d ->
                        vm.showDay(d)
                        nav.navigate(Tab.TODAY.route) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Tab.PROFILE.route) {
                ProfileScreen(
                    profile, targets, currentKg, weights.firstOrNull()?.kg ?: profile.weightKg, content,
                    onEdit = { nav.navigate("edit") },
                    onReset = vm::resetAll,
                    onUnits = vm::setUnits,
                    onCleanInstallers = {
                        runCatching { pickInstallers.launch(arrayOf("application/vnd.android.package-archive")) }
                            .onFailure { scope.launch { snackbar.showSnackbar("No file manager found to pick the files.") } }
                    },
                    forecast = {
                        com.mrboora.plate.ui.screens.ForecastCard(
                            profile, currentKg, todayMeals, todayWater, todayWorkouts, weights, PlateViewModel.today(),
                            onAddWorkout = vm::addWorkout, onDeleteWorkout = vm::deleteWorkout,
                            modifier = Modifier.padding(horizontal = 16.dp),
                            history = history, workoutsByDay = workoutsByDay,
                        )
                    },
                    extra = {
                        com.mrboora.plate.ui.components.SelfieSettings(
                            count = selfieDays.size, locked = selfieLock, canLock = com.mrboora.plate.data.Selfies.canLock(context),
                            reminder = selfieReminder, reminderHour = selfieHour,
                            onLock = { on ->
                                com.mrboora.plate.data.Selfies.setLocked(context, on); selfieLock = on
                                if (on) vm.lockSelfies()
                            },
                            onReminder = { on, h ->
                                if (on && android.os.Build.VERSION.SDK_INT >= 33) notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                com.mrboora.plate.data.Selfies.setReminder(context, on, h); selfieReminder = on; selfieHour = h
                            },
                            onDeleteAll = {
                                vm.lockSelfies()
                                scope.launch {
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.mrboora.plate.data.Selfies.deleteAll(context) }
                                    vm.refreshSelfies()
                                }
                            },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    },
                )
            }
            composable("add/{meal}") { entry ->
                val meal = entry.arguments?.getString("meal")?.let { runCatching { Meal.valueOf(it) }.getOrNull() } ?: Meal.SNACK
                AddFoodScreen(
                    meal = meal,
                    dayText = dayLabel(day),
                    recent = recent,
                    searchLocal = vm::searchLocal,
                    searchOnline = vm::searchOnline,
                    lookupBarcode = vm::lookupBarcode,
                    restaurantChains = vm::restaurantChains,
                    restaurantMenu = vm::restaurantMenu,
                    shelfFoods = vm::shelfFoods,
                    foodTypes = vm::foodTypes,
                    didYouMean = vm::didYouMean,
                    onRequest = { restaurant, item, details -> sendRequest(restaurant, item, details) },
                    onCreate = { vm.createFood(it) },
                    onForget = { vm.forgetFood(it) },
                    onAdd = { food, g -> vm.addEntry(meal, food, g) },
                    onBack = { nav.popBackStack() },
                    mixIngredients = vm::mixIngredients,
                    searchIngredients = vm::searchIngredients,
                    onSaveMix = { food, count -> vm.saveMix(meal, food, count) },
                    parseMix = vm::parseMix,
                    onRemember = vm::rememberReading,
                )
            }
            composable("edit") {
                Scaffold(
                    containerColor = Plate.colors.ground,
                    topBar = {
                        TopAppBar(
                            title = { Text("Edit profile") },
                            navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Plate.colors.ground),
                        )
                    },
                ) { p ->
                    Column(Modifier.padding(p).imePadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
                        ProfileForm(profile.copy(weightKg = currentKg), "Save changes", onUnits = vm::setUnits) { updated ->
                            // A changed weight on this form counts as today's weigh-in.
                            vm.saveProfile(updated, logWeight = updated.weightKg != currentKg)
                            nav.popBackStack()
                        }
                    }
                }
            }
        }
    }
}
