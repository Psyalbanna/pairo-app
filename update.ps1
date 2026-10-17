$ErrorActionPreference = 'Stop'
Start-Transcript -Path (Join-Path $PSScriptRoot 'update-log.txt') -Force | Out-Null
$base = 'https://raw.githubusercontent.com/Psyalbanna/pairo-app/main'
$files = @(
  'app/build.gradle.kts',
  'app/src/main/AndroidManifest.xml',
  'app/src/main/java/ai/pairo/voice/MainActivity.kt',
  'app/src/main/java/ai/pairo/voice/MicService.kt',
  'app/src/main/java/ai/pairo/voice/Speech.kt',
  'app/src/main/res/values/strings.xml'
)
try {
  Write-Host "Memperbarui berkas dari server...`n"
  foreach ($f in $files) {
    $tujuan = Join-Path $PSScriptRoot ($f -replace '/', '\')
    $folder = Split-Path $tujuan -Parent
    if (!(Test-Path $folder)) { New-Item -ItemType Directory -Path $folder -Force | Out-Null }
    $tmp = "$tujuan.baru"
    Invoke-WebRequest -Uri "$base/$f" -OutFile $tmp -UseBasicParsing
    Move-Item -Path $tmp -Destination $tujuan -Force
    Write-Host "  diperbarui: $f"
  }
  $versi = Select-String -Path (Join-Path $PSScriptRoot 'app\build.gradle.kts') -Pattern 'versionName' | Select-Object -First 1
  Write-Host "`nSELESAI. $($versi.Line.Trim())"
  Write-Host "Langkah berikut: klik dua kali build.bat"
} catch {
  Write-Host "`nGAGAL: $($_.Exception.Message)"
} finally {
  Stop-Transcript | Out-Null
}
Read-Host "Tekan Enter untuk menutup"
