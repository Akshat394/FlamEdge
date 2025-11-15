# Quick Script to Run App on Emulator
# Usage: .\run-on-emulator.ps1

$SDK_PATH = "C:\Users\dell\AppData\Local\Android\Sdk"
$ADB = "$SDK_PATH\platform-tools\adb.exe"
$EMULATOR = "$SDK_PATH\emulator\emulator.exe"
$PROJECT_DIR = "E:\Flam Assignment\edge-viewer"

Write-Host "=== EdgeViewer - Emulator Helper ===" -ForegroundColor Cyan
Write-Host ""

# Check if ADB exists
if (-not (Test-Path $ADB)) {
    Write-Host "ERROR: ADB not found at $ADB" -ForegroundColor Red
    Write-Host "Please install Android SDK Platform-Tools" -ForegroundColor Yellow
    exit 1
}

# Check if Emulator exists
if (-not (Test-Path $EMULATOR)) {
    Write-Host "ERROR: Emulator not found at $EMULATOR" -ForegroundColor Red
    Write-Host "Please install Android Emulator from SDK Manager" -ForegroundColor Yellow
    exit 1
}

Write-Host "Step 1: Checking for running emulators..." -ForegroundColor Yellow
$devices = & $ADB devices 2>&1 | Select-String "device$"
if ($devices) {
    Write-Host "✓ Found running emulator: $($devices.Line)" -ForegroundColor Green
} else {
    Write-Host "✗ No emulator detected" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please start an emulator first:" -ForegroundColor Yellow
    Write-Host "  Option 1: Open Android Studio → Device Manager → Click Play button" -ForegroundColor White
    Write-Host "  Option 2: Run this command manually:" -ForegroundColor White
    Write-Host "    $EMULATOR -avd YOUR_AVD_NAME" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "To see available emulators, run:" -ForegroundColor Yellow
    Write-Host "  $EMULATOR -list-avds" -ForegroundColor Cyan
    exit 1
}

Write-Host ""
Write-Host "Step 2: Building and installing app..." -ForegroundColor Yellow
Set-Location $PROJECT_DIR
& .\gradlew.bat installDebug

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "✓ App installed successfully!" -ForegroundColor Green
    Write-Host ""
    Write-Host "The app should appear on your emulator." -ForegroundColor Cyan
    Write-Host "If it doesn't open automatically, find 'EdgeViewer' in the app drawer." -ForegroundColor White
} else {
    Write-Host ""
    Write-Host "✗ Installation failed" -ForegroundColor Red
    Write-Host "Please check the error messages above" -ForegroundColor Yellow
}

