# Installs the debug app on the running emulator and makes it Device Owner.
# Usage (from project root):  powershell -ExecutionPolicy Bypass -File scripts\provision-emulator.ps1
# Emulator ONLY. On a real phone, remove accounts and run the plain dpm command (see TESTING.md).

$ErrorActionPreference = "Stop"
$adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
$admin = "com.example.sightblocker/.admin.SightBlockerAdminReceiver"

& $adb get-state | Out-Null   # fails if no emulator is running

Write-Host "Building and installing..."
& .\gradlew.bat installDebug --console=plain | Select-String "BUILD|FAILED|error"

Write-Host "Provisioning Device Owner..."
& $adb shell settings put global device_provisioned 0
& $adb shell settings put secure user_setup_complete 0
& $adb shell dpm set-device-owner $admin
& $adb shell settings put global device_provisioned 1
& $adb shell settings put secure user_setup_complete 1

Write-Host "`nCurrent owners:"
& $adb shell dpm list-owners
Write-Host "`nDone. Open SightBlocker from the emulator's app list."
