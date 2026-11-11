# Selling Nexora Printer on Google Play (subscription)

**Printing is free.** The subscription, **Nexora Printer Pro**, only removes the Nexora logos: the window that appears while a ticket
from a POS prints, and the logo shown when the app opens. The Play Store build is a normal build made with `-PrequireSubscription=true`; in it the logo window always
shows until the owner subscribes. Every other build (GitHub debug APK, sideloaded) is fully unlocked.

## What the app does

- Free: all printing, POS tickets, settings and history. A small window with the Nexora logo shows while a POS ticket prints.
- Pro (subscription): Settings -> *Show print window* can be switched off, so POS tickets print silently.
- Settings -> Subscription opens the plans screen (plans, prices and free trials come straight from Google Play), with
  Restore purchases and Manage / cancel (opens Google Play subscriptions), Terms and Privacy links.
- The last confirmed subscription is remembered for 3 days, so it keeps working when the tablet is offline.
- The logo shown when the app opens works the same way: always shown without Pro; Pro can switch it off (Settings -> *Show logo when the app opens*).

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

## Pro without a subscription (your own use, demos)

A license code unlocks Pro (both Nexora logos can be switched off) with no Google Play subscription.

- Make codes on your PC with `python tools/make_license.py code "Label" [--until yyyy-mm-dd]` (needs `pip install cryptography`).
- The **private key stays in `~/.nexora/license_private.pem`** and is never committed. The app only contains the public key, so a code
  cannot be forged without it. Back that file up: if you lose it you cannot make new codes, and if you recreate it, old codes stop working.
- In the app: Settings -> Subscription -> *Plans & subscription* -> **License code** -> *Activate license*.
- Codes are not tied to one device: anyone you give a code to can use it, so give them only to people you trust, and use `--until`
  for demos. To revoke a leaked code, make a new key pair (`init` after deleting the old key), put the new public key in
  `LicenseVerifier.PUBLIC_KEY` and release an update.
- Free builds (GitHub, sideloaded) are fully unlocked already, so you only need a code on the Google Play build.
- Google Play rule: use codes only for your own use, testing, demos and gifts. Do not sell codes outside Google Play to unlock features
  for customers: digital features sold inside a Play app must use Google Play Billing. For testing with the real purchase flow, use
  Play Console -> Settings -> License testing instead.
