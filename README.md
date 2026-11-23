# Plate

Track calories and macros. No account, everything stays on your phone.

<p align="center">
  <a href="https://raw.githubusercontent.com/mrboora04/ICE3/main/release/Plate.apk"><img src="https://img.shields.io/badge/Download_Plate_for_Android-brightgreen?logo=android&style=for-the-badge" width="720" alt="Download Plate for Android"></a>
</p>

<p>
  <img src="docs/screenshots/today.png" width="24%" alt="Today screen">
  <img src="docs/screenshots/add_food.png" width="24%" alt="Add food screen">
  <img src="docs/screenshots/progress.png" width="24%" alt="Progress screen">
  <img src="docs/screenshots/profile.png" width="24%" alt="Profile screen">
</p>

## What you can do

- Daily calorie and macro target, set to your goal
- Log meals in a food diary, with a portion helper (spoons, cups, glasses, or your hand)
- Make-it-yourself builders (shake, pizza, chai & coffee, roti meal, sandwich, eggs, rice bowl): type what went in ("party size cheese pizza with paneer and onion", "1 banana, 30 g berries, 400 ml milk") ("1 banana, 30 g berries, a spoon of peanut butter, 400 ml milk") or pick it, and see the math; saved as one glass
- Search 6,000+ Canadian foods, or scan a barcode
- Browse 14 Canadian restaurant menus (2,100+ items) — can't find one? Request it and we'll add it
- Track water by your own bottle, glass or cup (one tap each), and weight, with a progress chart; a step-by-step weight forecast on Profile from today's real meals, water and logged workouts
- Selfie of the day (camera only): blurred until you tap it, before/after compare, optional screen lock. Photos never leave your phone and are left out of backups
- Works offline, light and dark themes

## Install

1. Tap the download button above to get `Plate.apk`.
2. Open it and allow install from unknown sources. Android 8.0+.

Plate checks for new versions itself: when one is out, the Today screen shows what's new and an **Update** button. The installer file is deleted automatically after the update; older Plate installers in Downloads can be removed from Profile → Clean up old Plate installers.

## Data and licences

- Contains information licensed under the [Open Government Licence – Canada](https://open.canada.ca/en/open-government-licence-canada): Health Canada, *Canadian Nutrient File, 2026*.
- Restaurant menus come from each chain's official Canadian nutrition info, with sources in `canada_restaurants.csv`. Restaurants that publish none (e.g. Amaya) are marked as estimates and shown with ≈. Plate isn't affiliated with any restaurant.
- Also uses USDA [FoodData Central](https://fdc.nal.usda.gov) (public domain) and [Open Food Facts](https://world.openfoodfacts.org) (Open Database License).

Calorie targets are estimates, not medical advice.

## For developers

```bash
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest   # tests, and renders docs/screenshots/*.png
```

**Releasing an update:** bump `versionCode` and `versionName` in `app/build.gradle.kts`, write two or three lines in `release/next-notes.md` (each starting with `- `), and push to `main`. GitHub Actions then tests, builds and signs Plate with the release key (kept only in the repo's encrypted secrets), checks the signature against `release/signing-cert-sha256.txt`, and publishes `release/Plate.apk` and `release/latest.json`. Installed copies then show the Update badge. Pushes that don't raise the version only run the tests.