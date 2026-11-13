# Removes SightBlocker as Device Owner and uninstalls it. Works because debug builds are testOnly.
# Usage:  powershell -ExecutionPolicy Bypass -File scripts\reset-owner.ps1

$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$pkg = "com.example.sightblocker"

# Undo restrictions we may have set, before giving up ownership.
& $adb shell pm unsuspend com.google.android.youtube 2>$null
& $adb shell dpm remove-active-admin "$pkg/.admin.SightBlockerAdminReceiver"
& $adb shell pm uninstall $pkg
& $adb shell dpm list-owners
