# Selling Nexora Printer on Google Play (subscription)

The Play Store build is a normal build made with `-PrequireSubscription=true`. In that build printing needs the
**Nexora Printer Pro** subscription; every other build (GitHub debug APK, sideloaded) is free and fully unlocked.

## What the app does

- Opens the plans screen on launch when there is no active subscription (Settings -> Subscription also opens it).
- Reads plans, prices and free trials straight from Google Play (nothing is hard-coded except the product id).
- Remembers the last confirmed subscription for 3 days, so printing keeps working when the tablet is offline.
- Setup screens, history and settings stay available without a subscription; printing (including POS tickets) is locked.
- Restore purchases, manage / cancel (opens Google Play subscriptions), terms and privacy links are on the plans screen.

## One-time setup in Google Play Console

1. Create the app (package `nexora.printer`) and complete the store listing, content rating and data safety form.
2. **Monetise -> Products -> Subscriptions -> Create subscription**
   - Product id: `nexora_printer_pro` (must match `SUBSCRIPTION_PRODUCT_ID` in `app/build.gradle.kts`).
   - Add base plans, for example `monthly` (1 month) and `yearly` (1 year), then activate them.
   - Add an offer with a free trial (for example 7 days) to the base plan if you want one.
3. Add licence testers (Settings -> License testing) so you can buy for free while testing.
4. Upload a signed Android App Bundle to an internal testing track first. Billing only works for a build uploaded to Play.

## Build the Play bundle

Add the signing secrets described in the README, then run the **Android Release (manual)** workflow. It produces
`Nexora-Printer-play-release.aab` (with the subscription switched on) and the free APK.

Locally:

```bash
./gradlew bundleRelease -PrequireSubscription=true
# app/build/outputs/bundle/release/app-release.aab
```

## Before the first release (checklist)

- Target API level: Google requires a recent target (35 or higher when this was written; check the Play Console policy page
  for the current number). The project currently targets 34, so raise `compileSdk` / `targetSdk` (and the Android Gradle
  Plugin to 8.6 or newer) and test on a new Android version first.
- Privacy policy URL (the app only stores printer settings on the device; it sends nothing to a server) and the Terms URL.
  The plans screen links to `https://www.nexoraittechnologies.com/terms` and `/privacy`: publish those pages or change the links in
  `SubscriptionScreen.kt`.
- Declare the Bluetooth / USB permissions' purpose in the Play Console "App content" forms.
- Use Play App Signing; keep the upload keystore safe (never commit it).
- Subscription rules: show the price, period and renewal terms clearly (the plans screen does), and let people cancel in Google Play.
