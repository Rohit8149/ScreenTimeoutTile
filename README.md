# Screen Timeout Tile ⏱️

A lightweight Android utility app that lets you temporarily extend your screen timeout directly from your Quick Settings panel.

When you're reading a long article, referencing a recipe, or showing a screen to a friend, your phone turning off every 1 minute is frustrating. This app solves that by letting you temporarily boost the timeout to 5, 10, or 30 minutes. When you're done, it automatically restores your exact original timeout.

## ✨ Features
* **Quick Settings Tile:** Toggle timeouts directly from your notification shade.
* **Smart Cycle:** Tap the tile to cycle through: `Your Original Timeout` → `5 min` → `10 min` → `30 min` → `Your Original Timeout`.
* **Zero Battery Drain:** The app does not run any background polling. It simply modifies the Android system setting and sleeps.
* **Swipe to Cancel:** A temporary notification keeps you informed. Simply swipe it away to instantly restore your original timeout.
* **No Ads, Open Source:** Completely free and private.

## 🚀 How to Install
Since this app modifies system settings, it is not on the Google Play Store. You can download it directly from GitHub:
1. Go to the [Actions tab](../../actions) in this repository.
2. Click on the latest successful build.
3. Scroll down to **Artifacts** and download the `ScreenTimeoutTile-App.zip` file.
4. Unzip the file and install the `app-debug.apk` on your Android device.

## 📱 How to Use
1. **Grant Permission:** Open the app once to grant the "Modify system settings" permission.
2. **Add the Tile:** Swipe down your notification shade, tap the edit (pencil) icon, and drag the "Timeout" tile to your active tiles.
3. **Tap to Cycle:** Tap the tile to extend your screen timeout. Tap again to increase the time.
4. **Restore:** To go back to your normal timeout, either tap the tile until it turns off, or simply swipe away the ongoing notification.

## 🛠️ Built With
* Kotlin
* Jetpack Compose (for the onboarding UI)
* Android TileService API
* Android Foreground Services (to ensure compatibility with Android 14+ notification rules)
