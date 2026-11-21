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
    Box(Modifier.fillMaxSize().background(Plate.colors.ground)) {
        when (val s = state) {
            ProfileState.Loading -> Unit
            is ProfileState.Ready ->
                if (s.profile == null) OnboardingScreen { vm.saveProfile(it, logWeight = true) }
                else Main(vm, s.profile)
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
    val week by vm.week.collectAsStateWithLifecycle()
    val recent by vm.recentFoods.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val updateCardHidden by vm.updateCardHidden.collectAsStateWithLifecycle()
    val currentKg = vm.currentWeight.collectAsStateWithLifecycle().value ?: profile.weightKg

    val context = androidx.compose.ui.platform.LocalContext.current
    val notifyPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }

    /** Saves the request (so we can notify when it's added) and opens a pre-filled email. */
    fun sendRequest(restaurant: String, item: String) {
        vm.saveRequest(restaurant, item)
        if (android.os.Build.VERSION.SDK_INT >= 33) notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        val what = listOf(restaurant, item).filter { it.isNotBlank() }.joinToString(" – ")
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        val email = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:"))
            .putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(com.mrboora.plate.data.Requests.EMAIL))
            .putExtra(android.content.Intent.EXTRA_SUBJECT, "Plate food request: $what")
            .putExtra(
                android.content.Intent.EXTRA_TEXT,
                "Please add this to Plate:\n\nRestaurant: ${restaurant.ifBlank { "-" }}\nFood/item: ${item.ifBlank { "-" }}\n\nPlate $version",
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
                    onUpdateTap = vm::showUpdate,
                    banner = {
                        update?.takeIf { !updateCardHidden }?.let { info ->
                            com.mrboora.plate.ui.components.UpdateCard(info, onLater = vm::dismissUpdate, modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    },
                )
            }
            composable(Tab.PROGRESS.route) {
                ProgressScreen(profile, targets, weights, week, content, vm::logWeight, vm::deleteWeight)
            }
            composable(Tab.PROFILE.route) {
                ProfileScreen(
                    profile, targets, currentKg, weights.firstOrNull()?.kg ?: profile.weightKg, content,
                    onEdit = { nav.navigate("edit") },
                    onReset = vm::resetAll,

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
                    didYouMean = vm::didYouMean,
                    onRequest = { restaurant, item -> sendRequest(restaurant, item) },
                    onCreate = { vm.createFood(it) },
                    onForget = { vm.forgetFood(it) },
                    onAdd = { food, g -> vm.addEntry(meal, food, g) },
                    onBack = { nav.popBackStack() },
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
                        ProfileForm(profile.copy(weightKg = currentKg), "Save changes") { updated ->
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
