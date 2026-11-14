# SightBlocker: where we are

Read this first in a new session, then README.md (architecture) and TESTING.md (how to test).
Last updated after the emulator work; the Galaxy A16 has NOT been tested yet.

## Goal
A self-control app to stop the owner's YouTube addiction. Blocks YouTube (website in Chrome, and the
YouTube app) and makes it hard to undo: can't uninstall the app, can't change settings to cheat.
Only YouTube for now. More sites/apps later. The owner trusts the design to be strong, not unbreakable.

## Decisions already made (do not re-litigate)
- **Device Owner only.** Accessibility Service and Device Admin were rejected: too easy to switch off.
- **Chrome managed policy** (`URLBlocklist`) is the website blocker. No URL scraping.
- **YouTube app is suspended** (`setPackagesSuspended`). YouTube Music is NOT blocked, on purpose.
- **Unblock flow:** type phrase -> wait 20 min -> type phrase again within 5 min or it expires.
  The phrase is not a secret, just friction (`engine/UnlockPhrase.kt`). Owner holds it.
- **One page UI**, 3 cards: status, block, unblock. Block disabled when blocked; unblock disabled when not.
- **Block anytime, unblock has friction.**
- **Date/time lock** while blocked so the wait can't be skipped by moving the clock.
- **Tamper protection covers THIS app only.** No device-wide restrictions: the owner must still be able
  to install and uninstall other apps. (An early version blocked all uninstalls. That was wrong.)
- **SharedPreferences, not DataStore** (no extra dependency for two values). Views, not Compose.
- The app must **never start by itself.** Owner opens it manually.
- Target phone: **Galaxy A16, Android 16, Knox.** Test on the emulator first (done), then the phone.

## Verified on the emulator (stock Android 17, no Knox)
Provisioning (needs a setup-flag workaround, see README), Chrome blocks YouTube, YouTube app suspended,
Incognito disabled, unblock flow incl. wait/expiry/persistence, state re-applied when the app opens,
date/time lock active, `adb force-stop` can't kill the app while protected, 21 unit tests pass,
both escape routes (in-app Device Owner card, `adb dpm remove-active-admin`).

## NOT verified
- **Anything on the real Galaxy A16 / Knox.** Whether `set-device-owner` is accepted at all is unknown.
- Paste blocking in the unlock box with a real keyboard.
- Changing the date through the Settings screen (restriction is active, screen not tried).
- Chrome cold-start right after Block (once, YouTube loaded before the policy arrived).
- Whether uninstalling then reinstalling YouTube escapes the suspension.
- Samsung Internet and other browsers are NOT blocked (known hole).

## Gotchas learned the hard way
- Provisioning needs **no accounts** on the device. On the emulator it also needs the
  `device_provisioned` / `user_setup_complete` flag trick (scripts\provision-emulator.ps1).
- Only builds installed as **testOnly** can be removed with adb. Debug builds now are (see
  `app/src/debug/AndroidManifest.xml`). Ownership must be provisioned WITH the testOnly build installed.
  Release builds can only be undone by the app or a factory reset: do not use on the phone yet.
- If ownership is removed via adb while YouTube is suspended, YouTube **stays suspended** and adb cannot
  undo it. Recover: provision again, open the app, use the Device Owner card.
- The emulator's Android Studio quick-boot snapshot makes it reopen on whatever app was showing. Press Home
  before closing it, or use Cold Boot Now. Closing it badly lost Device Owner once.
- `Tamper protection` mostly adds nothing in Settings (Android already protects the owner app); what it does
  add is blocking `adb am force-stop` on our app.
- The Device Owner card releases ownership with one pop-up, no wait (only while unblocked, so it is behind the unblock friction).
- Emulator UI automation (adb taps) is flaky: the launcher steals focus. `monkey -p <pkg> -c
  android.intent.category.LAUNCHER 1` opens the app reliably.

## Next steps
1. Real-phone test, following TESTING.md section E (debug build, back up first, prove the escape works
   before pressing Block).
2. Report what Samsung/Knox does with set-device-owner, Chrome policy, app suspension, date lock.
3. Then: Samsung Internet / other browsers, hardening (safe boot, USB debugging, factory reset limits),
   release build, nothing left to decide on the old DEV button (removed).

## Git
Repo: https://github.com/tylerBVT/SightBlocker (branch main). First commit: "Youtube block on emulator works".
