# Testing SightBlocker yourself

## A. Run an emulator

**Android Studio (easiest)**
1. Open **Tools > Device Manager**.
2. Select `Medium_Phone_API_37.0` and press the play button. Wait until the home screen appears.
   - Needs a "Google Play" image so Chrome and YouTube are installed. The one already here has them.
   - To make another: **Create Device**, choose a phone, choose a system image whose target says *Google Play*.

**Command line**
```
%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe -avd Medium_Phone_API_37.0
```
Do not close the emulator window while testing. Closing it without a clean shutdown lost the Device Owner state once.

## B. Install and make it Device Owner (emulator)

From the project root:
```
powershell -ExecutionPolicy Bypass -File scripts\provision-emulator.ps1
```
Then open SightBlocker yourself from the app list. With nothing blocked it shows **UNBLOCKED** and the
Block button is enabled. If it says "Not Device Owner", provisioning did not work.
(The script uses a setup-flag workaround that the emulator needs; see README.)

## C. Test checklist

| # | Step | Expected |
|---|---|---|
| 1 | Tap **Block YouTube now** | Status turns red **BLOCKED**. The Block button greys out. |
| 2 | Open Chrome, go to `m.youtube.com` | "m.youtube.com is blocked" |
| 3 | In Chrome go to `chrome://policy` | `URLBlocklist` and `IncognitoModeAvailability` listed, source Platform |
| 4 | Open the YouTube app | "Blocked by work policy" |
| 5 | In the Unblock card, type the phrase wrong | **Request unblock** stays greyed out |
| 6 | Try to paste the phrase (copy it from the screen first) | Paste is refused or the text is removed. Not tested on a real keyboard yet. |
| 7 | Type the phrase correctly, tap **Request unblock** | Status shows "Unblock available in 19:5x", button says Waiting, Cancel appears |
| 8 | Close and reopen the app | Countdown continues where it was |
| 9 | Settings > System > Date & time | Greyed out / cannot be changed. Automatic time stays on. Not tried through the Settings screen yet. |
| 10 | After 20 minutes, reopen the app | "Wait is over. Type the phrase again within 4:xx", button says **Confirm unblock** |
| 11 | Type the phrase again, tap **Confirm unblock** | Status turns green **UNBLOCKED**. Chrome loads YouTube, the app opens, date/time can be changed again. |
| 11b | After the wait, do NOT confirm. Wait 5 more minutes | The request expires: back to BLOCKED, "Step 1 of 2". A new phrase and a new 20 minute wait are needed. |
| 12 | Tap **Cancel unblock** during the wait | Back to BLOCKED. A new request needs a new 20 minute wait. |
| 13 | Settings > Apps > SightBlocker | Uninstall, Force stop, Clear storage greyed out |
| 14 | While blocked: `adb shell am force-stop com.example.sightblocker` | Does nothing (the process survives). The same command kills Chrome. |
| 15 | Install any other app from Play Store, then uninstall it | Works normally |
| 16 | Block, fully close Chrome, reopen and go to YouTube immediately | Still blocked (cold-start case, not verified) |

To skip the 20 minute wait in a debug build, stop the app, back-date `unlock_requested_at` in
`shared_prefs/sightblocker_state.xml` via `adb shell run-as com.example.sightblocker`, and reopen.
Note `adb shell am force-stop` will NOT stop the app while blocked (step 14); use
`adb shell run-as com.example.sightblocker kill <pid>` instead.

Watch the log while you test: `adb logcat -s SightBlocker`

## D. Reset

```
powershell -ExecutionPolicy Bypass -File scripts\reset-owner.ps1
```
Removes Device Owner and the app. You can also wipe the emulator in Device Manager (down arrow > **Wipe Data**).

## E. Trying it on the Galaxy A16 (this is the Knox / Android 16 test)

Do this only with the **debug build** from Android Studio. It is `testOnly`, so you can always remove it
with `reset-owner.ps1`. Do not use a release build until the debug build has worked on the phone.

1. **Back up first.** Provisioning needs accounts removed, and a failed attempt could leave you resetting.
2. Enable Developer options (Settings > About phone > Software information > tap *Build number* 7 times) and turn on **USB debugging**.
3. Remove **all accounts**: Settings > Accounts and backup > Manage accounts (Google, Samsung, any others). Also remove any Secure Folder or work profile, since extra users can block provisioning.
4. Connect the phone by USB, accept the debugging prompt, and check `adb devices` lists it.
5. In Android Studio, run the app on the phone, or `gradlew installDebug`.
6. Provision:
   ```
   adb shell dpm set-device-owner com.example.sightblocker/.admin.SightBlockerAdminReceiver
   ```
   - Success: `Device owner set to package ...`.
   - "already some accounts": an account is still present. Check Samsung account, Google, and Secure Folder.
   - Anything mentioning Knox or a security exception: copy the whole message to me.
7. Run checklist C steps 1 to 7 on the phone.
8. Re-add your Google account (Settings > Accounts) and confirm that works and the block still holds.
9. When finished testing, run `scripts\reset-owner.ps1` if you want it gone.

What to watch for on Samsung, because we can't know in advance:
- Whether `set-device-owner` is accepted at all.
- Whether Chrome receives the policy (`chrome://policy`).
- Whether Samsung Internet is still open as a way round (expected, not yet handled).
- Whether the YouTube app can be suspended (it is preinstalled on Samsung).
- Whether re-adding the accounts afterwards is allowed.
