# EMERGENCY / CLEANUP: removes SightBlocker as Device Owner from the computer, then uninstalls it.
# Works only for DEBUG builds (they are marked testOnly). A release build cannot be removed this way.
#
# PREFER the in-app button "DEV: release device owner" (debug builds). It undoes everything cleanly.
# Use this script when the app will not open.
#
# KNOWN LIMIT (verified on the emulator): if YouTube is suspended when ownership is removed from adb,
# it STAYS suspended and adb cannot undo it. Fix: run scripts\provision-emulator.ps1 again (or provision
# the phone again), open the app, tap "DEV: release device owner". That lifts the suspension.
#
# Usage:  powershell -ExecutionPolicy Bypass -File scripts\reset-owner.ps1

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$pkg = "com.example.sightblocker"

Write-Host "Removing device owner..."
& $adb shell dpm remove-active-admin "$pkg/.admin.SightBlockerAdminReceiver"
Write-Host "Owners now:"
& $adb shell dpm list-owners

Write-Host "Uninstalling app..."
& $adb shell pm uninstall $pkg

$yt = (& $adb shell pm dump com.google.android.youtube) -join "`n"
if ($yt -match "suspended=true") {
    Write-Warning "YouTube is still suspended. adb cannot lift this. Provision again and use the app's DEV release button (see top of this script)."
}
