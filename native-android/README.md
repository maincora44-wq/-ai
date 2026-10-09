# Investment OS — Native Samsung Home-Screen Widget

A free **real Android AppWidget** written in Kotlin, added alongside the existing Portfolio Lab PWA.

## Privacy and architecture

- Imports the private snapshot JSON with the schema `portfolio-lab-private-snapshot-v1` using Android's system file picker.
- Validates five brokerage account totals, holding subtotals, and observed investments before storing.
- Stores JSON only in the app's private SharedPreferences. Android backup is disabled.
- No INTERNET permission, analytics, tracking, login, broker API or cloud storage.
- Widget displays the observed investment subtotal and five-account subtotal, with amounts **hidden by default**.
- Snapshot date and provisional flag are displayed. No fictional live quotes or regime signals.
- The existing PWA localStorage is **not** shared with the native app; import the JSON again.
- Repository and GitHub Actions are public: **never commit personal JSON or account numbers**.
- Debug APK is intended for personal testing. Android may warn about sideloaded apps. Install only your own verified GitHub build.

## Build free APK on GitHub (phone only)

1. Visit the GitHub repository, open **Actions**.
2. Choose **Build Android Investment Widget APK**.
3. Tap **Run workflow** → **Run workflow**.
4. Open the completed green workflow run.
5. Under **Artifacts**, download `investment-os-debug-apk` (ZIP). Sign in to GitHub if requested.
6. In Samsung **My Files → Downloads**, extract ZIP and tap `app-debug.apk`.
7. If Android blocks installation, follow the system prompt to permit installation from the specific app used to open the APK. Disable that permission afterward.
8. Launch **Investment OS**, tap **Import local JSON**, select the downloaded private snapshot file.
9. Tap **Show values on widget** if desired.
10. Long-press an empty home-screen area → **Widgets** → **Investment OS** → add the widget. Resize as needed.

If the Actions workflow fails, check its build log. Do not assume the APK exists until the build shows success.

## Build in Android Studio (optional)

Open `native-android` as the project folder. Install Android SDK 35 and JDK 17. Sync Gradle, choose `app`, and select **Build → Build APK(s)**. APK path: `app/build/outputs/apk/debug/app-debug.apk`.

## Scope and limitations

- AppWidget uses native `RemoteViews`; it appears directly on the home screen.
- Widget refreshes after import and privacy-toggle changes; no background market feed.
- No historical FX performance calculation in this native widget yet.
- No actual Android-device or CI build test is claimed until the workflow succeeds.
- Date-stamped portfolio snapshots are **not live account valuations**.
