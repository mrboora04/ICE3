# Play Protect appeal: ready for the public release

**When:** with the public release build, not test builds. The form asks for the APK's SHA-256, so it covers one exact build. If a later update is flagged again, send a new appeal with that build's SHA-256.

**Where:** https://support.google.com/googleplay/android-developer/contact/protectappeals
(submitted by the developer, mrboora04; Google doesn't reply, so check by installing the APK on a phone afterwards)

## What to fill in

| Field | Value |
|---|---|
| App name | Plate |
| Package name | `com.mrboora.plate` |
| APK SHA-256 | *the release APK's: `sha256sum release/Plate.apk`, and also `"sha256"` in `release/latest.json`* |
| Signing certificate SHA-256 (API 28+) | `4ff4df10da1c05d1ac83506f80945ab28cee00c93daa142b1f8b52b32457c3a3` |
| Download link | https://raw.githubusercontent.com/mrboora04/ICE3/main/release/Plate.apk |
| Website | https://mrboora04.github.io/ICE3/ |
| Source code | https://github.com/mrboora04/ICE3 (public) |

## Why the app was flagged, and why it's safe (paste as the explanation)

> Plate is a free, offline calorie and water tracker, distributed from its own website and GitHub (not the Play Store). Its source code is public.
>
> Plate uses REQUEST_INSTALL_PACKAGES only to update itself. When a newer version is listed in the app's own release file (https://raw.githubusercontent.com/mrboora04/ICE3/main/release/latest.json), Plate downloads that APK from the same GitHub repository over HTTPS, checks its SHA-256 against the release file, and hands it to Android's PackageInstaller with setAppPackageName set to its own package. Android then asks the user to confirm. It never installs any other app, never downloads from anywhere else, and every update is signed with the same key as the installed app.
>
> Plate has no ads, no tracking and no account. Food diary data, weight and selfies stay on the phone. It only connects to the internet to read its own release and menu files on GitHub and to search Open Food Facts when the user asks.

## Checklist at release

- [ ] Release APK built without `-Pplate.updateBranch` (so it checks `main`)
- [ ] `release/latest.json` → `apk` points to `main/release/Plate.apk`, with that APK's `sha256`
- [ ] Fill in the APK SHA-256 above and submit the form
- [ ] A day or two later: install the release APK on a phone and see whether Play Protect still warns
- [ ] Later: Android developer verification (Android Developer Console), the lasting fix for Plate and Kitab
