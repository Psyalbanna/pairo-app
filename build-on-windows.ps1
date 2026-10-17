# Build APK P.A.I.R.O di Windows, tanpa Android Studio.
# Cara pakai: klik kanan berkas ini -> "Run with PowerShell"
# Atau dari terminal: powershell -ExecutionPolicy Bypass -File build-on-windows.ps1

$ErrorActionPreference = "Stop"
$root  = Split-Path -Parent $MyInvocation.MyCommand.Path
$tools = Join-Path $root ".buildtools"
New-Item -ItemType Directory -Force -Path $tools | Out-Null

# semua keluaran dicatat, supaya kalau gagal ada jejaknya
$log = Join-Path $root "build-log.txt"
try { Start-Transcript -Path $log -Force | Out-Null } catch { }

trap {
    Write-Host ""
    Write-Host "GAGAL: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Jejak lengkap ada di: $log"
    try { Stop-Transcript | Out-Null } catch { }
    Read-Host "Tekan Enter untuk menutup"
    exit 1
}

function Bagian($teks) { Write-Host ""; Write-Host "=== $teks ===" -ForegroundColor Cyan }

# ---------- 1. JDK 17 ----------
Bagian "JDK 17"
$jdkDir = Join-Path $tools "jdk17"
if (-not (Test-Path $jdkDir)) {
    Write-Host "Mengunduh JDK 17 (sekitar 180 MB)..."
    $zip = Join-Path $tools "jdk17.zip"
    Invoke-WebRequest -UseBasicParsing -Uri "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse" -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $jdkDir -Force
    Remove-Item $zip
}
$javaHome = (Get-ChildItem $jdkDir -Directory | Select-Object -First 1).FullName
$env:JAVA_HOME = $javaHome
$env:PATH = "$javaHome\bin;$env:PATH"
Write-Host "JAVA_HOME = $javaHome"

# ---------- 2. Android SDK ----------
Bagian "Android SDK"
$sdk = Join-Path $tools "android-sdk"
$sdkLatest = Join-Path $sdk "cmdline-tools\latest"
if (-not (Test-Path (Join-Path $sdkLatest "bin\sdkmanager.bat"))) {
    Write-Host "Mengunduh command-line tools (sekitar 130 MB)..."
    $zip = Join-Path $tools "cmdline.zip"
    Invoke-WebRequest -UseBasicParsing -Uri "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $tools -Force
    Remove-Item $zip
    New-Item -ItemType Directory -Force -Path $sdkLatest | Out-Null
    Move-Item (Join-Path $tools "cmdline-tools\*") $sdkLatest -Force
    Remove-Item (Join-Path $tools "cmdline-tools") -Recurse -Force -ErrorAction SilentlyContinue
}
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk

$sdkmanager = Join-Path $sdkLatest "bin\sdkmanager.bat"
Write-Host "Menerima lisensi..."
$lisensi = ("y`n" * 30)
$lisensi | & $sdkmanager --sdk_root=$sdk --licenses | Out-Null

Write-Host "Memasang platform 34 dan build-tools (sekitar 200 MB)..."
& $sdkmanager --sdk_root=$sdk "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# ---------- 3. Gradle 8.7 ----------
Bagian "Gradle 8.7"
$gradleDir = Join-Path $tools "gradle"
if (-not (Test-Path (Join-Path $gradleDir "bin\gradle.bat"))) {
    Write-Host "Mengunduh Gradle (sekitar 130 MB)..."
    $zip = Join-Path $tools "gradle.zip"
    Invoke-WebRequest -UseBasicParsing -Uri "https://services.gradle.org/distributions/gradle-8.7-bin.zip" -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $tools -Force
    Remove-Item $zip
    $dari = (Get-ChildItem $tools -Directory -Filter "gradle-8.7*" | Select-Object -First 1).FullName
    Move-Item $dari $gradleDir -Force
}
$gradle = Join-Path $gradleDir "bin\gradle.bat"

# ---------- 4. Build ----------
Bagian "Membangun APK"
Push-Location $root
& $gradle ":app:assembleDebug" --no-daemon
Pop-Location

$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apk) {
    $ukuran = [math]::Round((Get-Item $apk).Length / 1MB, 1)
    Write-Host ""
    Write-Host "SELESAI" -ForegroundColor Green
    Write-Host "APK: $apk ($ukuran MB)"
    Write-Host "Salin ke HP lalu pasang (izinkan sumber tidak dikenal)."
    Start-Process explorer.exe (Split-Path -Parent $apk)
} else {
    Write-Host "Build gagal - lihat pesan galat di atas." -ForegroundColor Red
}

try { Stop-Transcript | Out-Null } catch { }
Write-Host ""
Read-Host "Tekan Enter untuk menutup jendela ini"
