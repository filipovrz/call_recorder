# Builds a debug APK without Android Studio.
$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
Set-Location $Root

$jdkCandidates = @(
    "${env:ProgramFiles}\Microsoft\jdk-17*\bin\java.exe",
    "${env:ProgramFiles}\Eclipse Adoptium\jdk-17*\bin\java.exe",
    "${env:LOCALAPPDATA}\Programs\Eclipse Adoptium\jdk-17*\bin\java.exe"
)
$java17 = $null
foreach ($pattern in $jdkCandidates) {
    $hit = Get-Item $pattern -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($hit) { $java17 = $hit; break }
}
if ($java17) {
    $env:JAVA_HOME = Split-Path (Split-Path $java17.FullName -Parent) -Parent
    $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
    Write-Host "Using JAVA_HOME=$env:JAVA_HOME"
} else {
    Write-Host "WARNING: JDK 17 not found in default locations. Current java:"
    java -version
}

$LocalProps = Join-Path $Root "local.properties"
if (-not (Test-Path $LocalProps)) {
    Write-Host "local.properties missing - running SDK setup..."
    & (Join-Path $PSScriptRoot "setup-android-sdk.ps1")
}

$wrapperJar = Join-Path $Root "gradle\wrapper\gradle-wrapper.jar"
if (-not (Test-Path $wrapperJar)) {
    throw "gradle-wrapper.jar missing. Run scripts\bootstrap-gradle-wrapper.ps1 first."
}

Write-Host "Building debug APK..."
& .\gradlew.bat :app:assembleDebug --stacktrace
if ($LASTEXITCODE -ne 0) {
    throw "Gradle build failed with exit code $LASTEXITCODE"
}

$apkDir = Join-Path $Root "app\build\outputs\apk\debug"
$apk = Get-ChildItem -Path $apkDir -Filter *.apk | Select-Object -First 1
if (-not $apk) {
    throw "APK not found after build."
}

$destDir = Join-Path $Root "deploy\public_html\downloads"
New-Item -ItemType Directory -Force -Path $destDir | Out-Null
$dest = Join-Path $destDir "evtinko-call-recorder.apk"
Copy-Item $apk.FullName $dest -Force

Write-Host ""
Write-Host "OK: $($apk.FullName)"
Write-Host "Copied for hosting upload: $dest"
Write-Host "Upload that file to public_html/downloads/evtinko-call-recorder.apk"
