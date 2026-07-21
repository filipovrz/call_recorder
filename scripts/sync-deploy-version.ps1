# Syncs deploy/public_html/version.json AND app assets version from Gradle versionName.
# Website prefers reading version from inside the APK; version.json is optional fallback.
$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
$gradle = Join-Path $Root "app\build.gradle.kts"
$outJson = Join-Path $Root "deploy\public_html\version.json"
$outAsset = Join-Path $Root "app\src\main\assets\app_version.txt"

if (-not (Test-Path $gradle)) {
    throw "Missing $gradle"
}

$content = Get-Content $gradle -Raw
if ($content -notmatch 'versionName\s*=\s*"([^"]+)"') {
    throw "versionName not found in app/build.gradle.kts"
}
$version = $Matches[1]
$stamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")

New-Item -ItemType Directory -Force -Path (Split-Path $outAsset -Parent) | Out-Null
[IO.File]::WriteAllText($outAsset, "$version`nminAndroid=8.0`n", [Text.UTF8Encoding]::new($false))

$payload = @{
    version    = $version
    minAndroid = "8.0"
    updated_at = $stamp
} | ConvertTo-Json
[IO.File]::WriteAllText($outJson, $payload + "`n", [Text.UTF8Encoding]::new($false))

Write-Host "Asset: $outAsset -> $version"
Write-Host "JSON fallback: $outJson -> $version (optional on host; APK is enough)"
