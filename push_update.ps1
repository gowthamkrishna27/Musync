param(
    [Parameter(Mandatory=$false)]
    [string]$Version = "1.0.1",

    [Parameter(Mandatory=$false)]
    [string]$Changelog = "Telugu song suggestions, Hardware Equalizer & DSP engine, 24/7 Railway cloud streaming, and performance enhancements."
)

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Musync In-App OTA Update Publisher" -ForegroundColor Green
Write-Host "  Releasing: v$Version" -ForegroundColor Yellow
Write-Host "==========================================" -ForegroundColor Cyan

# 1. Update app/build.gradle.kts version
$gradleFile = "app\build.gradle.kts"
if (Test-Path $gradleFile) {
    Write-Host "-> Updating version in $gradleFile to $Version..." -ForegroundColor Cyan
    $content = Get-Content $gradleFile -Raw
    $content = $content -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$Version`""
    
    # Increment versionCode
    if ($content -match 'versionCode\s*=\s*(\d+)') {
        $oldCode = [int]$matches[1]
        $newCode = $oldCode + 1
        $content = $content -replace 'versionCode\s*=\s*\d+', "versionCode = $newCode"
        Write-Host "-> Incremented versionCode to $newCode" -ForegroundColor Cyan
    }
    Set-Content -Path $gradleFile -Value $content -NoNewline
}

# 2. Compile APK
Write-Host "-> Compiling update APK..." -ForegroundColor Cyan
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Gradle compilation failed!" -ForegroundColor Red
    exit 1
}

# 3. Copy APK artifact
Copy-Item -Path "app\build\outputs\apk\debug\app-debug.apk" -Destination "Musync.apk" -Force
Write-Host "-> Prepared Musync.apk ($( (Get-Item 'Musync.apk').Length / 1MB | ForEach-Object { '{0:N2} MB' -f $_ } ))" -ForegroundColor Green

# 4. Commit and Push Git changes
Write-Host "-> Committing version bump to Git..." -ForegroundColor Cyan
git add app/build.gradle.kts Musync.apk
git commit -m "release: bump version to v$Version"
git push origin main

# 5. Create / Upload GitHub Release
Write-Host "-> Publishing release v$Version to GitHub..." -ForegroundColor Cyan
gh release create "v$Version" "Musync.apk" --title "Musync v$Version" --notes "$Changelog" --latest

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "==========================================" -ForegroundColor Green
    Write-Host " SUCCESS! Release v$Version is now LIVE!" -ForegroundColor Green
    Write-Host " All Musync apps will now prompt users to" -ForegroundColor Yellow
    Write-Host " update directly inside the app!" -ForegroundColor Yellow
    Write-Host "==========================================" -ForegroundColor Green
} else {
    Write-Host "Note: If gh release failed, you can upload Musync.apk directly to GitHub Releases at https://github.com/gowthamkrishna27/Musync/releases/new" -ForegroundColor Yellow
}
