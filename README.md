# SightBlocker

A self-control app that blocks YouTube in Chrome and resists being disabled or uninstalled.
It runs as the phone's **Device Owner** and pushes enterprise policy to Chrome.

## Status

Working on the emulator: one-page UI (status, block, unblock), 20 minute unlock wait with a typed
phrase at both ends, and a date/time lock. **Not yet tried on the Galaxy A16 / Knox.**

How unblocking works: type the phrase, wait 20 minutes, then type the phrase again within 5 minutes.
If the 5 minutes pass, the request expires and you start over. The phrase is not a secret, it is only friction. It lives in `engine/UnlockPhrase.kt`.

## Architecture

| Class | Responsibility |
|---|---|
| `engine/BlockingEngine` | All the rules (states, the wait, phrase checks). Knows nothing about Android. Unit tested. |
| `engine/Enforcer` | Interface for "how blocking is actually done". |
| `engine/BlockStore` | Remembers two values: locked, and when an unblock was requested (SharedPreferences). |
| `engine/UnlockPhrase` | The phrase and the matching rule. |
| `admin/DeviceOwnerEnforcer` | The Enforcer used today: Chrome policy, YouTube app suspension, tamper and clock locks. |
| `admin/PolicyController` | Only class that talks to `DevicePolicyManager`. |
| `admin/ChromePolicy`, `admin/BlockedApps` | Pure data: blocked sites and blocked app packages. |
| `admin/SightBlockerAdminReceiver` | Device Owner entry point. |
| `MainActivity` | The one page. Only shows state and forwards taps to the engine. |

A different mechanism later (e.g. a DNS filter) would be another `Enforcer`; the rules do not change.

Storage note: SharedPreferences is used instead of DataStore to avoid an extra dependency for two values.

## Clock lock

The wait uses the phone's clock, so while blocked the app turns on automatic time and forbids changing
date, time and time zone (`DISALLOW_CONFIG_DATE_TIME`). If the clock is somehow moved backwards, no time
is gained. Moving it forwards would skip the wait, which is why it is locked.

## Why Device Owner, and not an Accessibility Service

An Accessibility Service, or a plain Device Admin, can be switched off or removed in a few taps in Settings.
A Device Owner can block uninstall, force-stop and clear-data, and can push policy straight into Chrome.
Chrome's `URLBlocklist` policy is enforced inside Chrome, so we do not scrape the URL bar.

## Installing and provisioning (emulator or phone)

Device Owner can only be set by adb, on a device with **no accounts** (remove Google and Samsung accounts first).

1. Build and install: `./gradlew installDebug`
2. Provision:
   ```
   adb shell dpm set-device-owner com.example.sightblocker/.admin.SightBlockerAdminReceiver
   ```
3. Open the app. The screen should say `Device owner: true`.

**Emulator note (API 37 Play image):** the command above fails with "already some accounts" even when
`dumpsys account` lists none. Temporarily mark setup as incomplete, provision, then restore:
```
adb shell settings put global device_provisioned 0
adb shell settings put secure user_setup_complete 0
adb shell dpm set-device-owner com.example.sightblocker/.admin.SightBlockerAdminReceiver
adb shell settings put global device_provisioned 1
adb shell settings put secure user_setup_complete 1
```
On a real phone, remove accounts instead and try the plain command first.

## Getting out (development)

Debug builds from Android Studio are `testOnly`, so you can always run:
```
adb shell dpm remove-active-admin com.example.sightblocker/.admin.SightBlockerAdminReceiver
```
The "DEV: release device owner" button also works. A release-signed, non-testOnly build cannot be
removed this way. Only do that on the real phone once everything else is finished.

## Testing

1. Tap **Block YouTube in Chrome**. The status line should list `URLBlocklist` and `IncognitoModeAvailability`.
2. In Chrome open `chrome://policy`. Both policies should appear with source "Platform".
3. Browse to `m.youtube.com`. Expected: "m.youtube.com is blocked".
4. Tap **Unblock**. YouTube loads again.
5. Tap **Enable tamper protection**, then `adb shell pm uninstall com.example.sightblocker`.
   Expected: `DELETE_FAILED_DEVICE_POLICY_MANAGER`.

Logcat: `adb logcat -s SightBlocker`

## Permissions and platform restrictions

- No runtime permissions are requested. Device Owner status is granted via adb, not a permission dialog.
- `<queries>` for `com.android.chrome` is declared so the app can check that Chrome is installed.
- Policy only affects Chrome. Samsung Internet, other browsers and WebViews inside apps are not covered yet.
- adb itself can bypass some restrictions (e.g. `am force-stop` from the shell works).
- A hardware recovery-mode factory reset cannot be blocked by any app.
