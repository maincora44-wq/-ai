# Investment OS v2.0 — Native Samsung Home-Screen Widget

A free Kotlin Android AppWidget that keeps private portfolio data local while automatically refreshing a **public, non-personal market feed**.

## v2 architecture

```
Private JSON on Samsung
        ↓
App-private SharedPreferences
        ↓
Portfolio structure ───────────────┐
                                   ├─ Native Android AppWidget
Public market-data.json ← GitHub ──┘
          ↑
GitHub Actions hourly updater
          ↑
Generic public market symbols only
```

### Privacy boundary

Private data stays on the phone:

- portfolio snapshot JSON
- account balances
- holdings
- employee-share value
- privacy/display preference

The app does **not** upload these values. Android backup remains disabled.

The app now has the `INTERNET` permission only so it can download:
`https://maincora44-wq.github.io/-ai/market-data.json`

That request contains no portfolio fields. The public feed contains generic market data only.

## Widget v2

The 4×3 widget shows:

- observed portfolio subtotal
- active account / retirement / ISA / employee-share buckets
- largest exposure and share of observed assets
- US market regime from S&P 500 vs 200-day moving average
- Korea market regime from KOSPI vs 200-day moving average
- USD/KRW
- US 10-year yield
- portfolio date and public-market timestamp

Portfolio amounts remain hidden by default until the user explicitly enables them.

## Automatic market data

`.github/workflows/market-data-refresh.yml` runs hourly on weekdays at minute 17 and can also be run manually.

`scripts/update_market_data.py` fetches generic public symbols:

- S&P 500: `^GSPC`
- KOSPI: `^KS11`
- USD/KRW: `KRW=X`
- US 10Y yield: `^TNX`

It calculates the latest value, daily change, 200-day moving average, distance from the 200DMA, and a simple regime:

- **RISK-ON**: > +1% above 200DMA
- **NEUTRAL**: between -1% and +1%
- **RISK-OFF**: < -1% below 200DMA

The source is a best-effort public Yahoo Finance chart endpoint. It is not a broker-grade or guaranteed real-time data service. If a symbol fails, the updater keeps stale data where available and marks the record stale.

Android requests a widget refresh every 30 minutes. Android/Samsung battery management may delay actual execution. The widget uses the most recently published public feed and preserves cached data when the network is unavailable.

## Install v2 from GitHub Actions

1. Open **Actions → Build Android Investment Widget APK**.
2. Open the latest successful run.
3. Download the artifact named **investment-os-v2-debug-apk**.
4. Extract the ZIP in Samsung **My Files**.
5. Install `app-debug.apk`.
6. Android should upgrade the existing app because the application ID is unchanged.
7. Open **Investment OS v2.0**.
8. Your existing local snapshot should normally remain after an in-place upgrade. If not, import the JSON again.
9. Tap **Update public market data now** once.
10. Long-press the home screen → **Widgets → Investment OS**. Remove/re-add or resize the old widget if the new 4×3 layout is not picked up immediately.

## Build workflow

`.github/workflows/android-widget-apk.yml` builds with JDK 17, Android SDK 35, Gradle 8.9 and uploads a debug APK artifact.

The workflow intentionally uses the preinstalled Android SDK on GitHub's Ubuntu runner and resolves `sdkmanager` by absolute path because the older setup action attempted to install the obsolete Android `tools` package.

## Security notes

- Never commit private portfolio JSON to this public repository.
- The debug APK is for personal sideload testing.
- Market quotes are informational snapshots, not execution prices.
- The market regime is a simple technical heuristic, not an investment recommendation.
- No broker credentials, analytics SDK, ad SDK, account login, or portfolio cloud sync are included.
