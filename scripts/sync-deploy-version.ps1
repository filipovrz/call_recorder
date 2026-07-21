# Syncs deploy/public_html/version.json from app/build.gradle.kts versionName.
# Run after bumping the app version, and always before uploading a new APK.
$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
$gradle = Join-Path $Root "app\build.gradle.kts"
$out = Join-Path $Root "deploy\public_html\version.json"

if (-not (Test-Path $gradle)) {
    throw "Missing $gradle"
}

$content = Get-Content $gradle -Raw
if ($content -notmatch 'versionName\s*=\s*"([^"]+)"') {
    throw "versionName not found in app/build.gradle.kts"
}
$version = $Matches[1]

$payload = @{
    version    = $version
    minAndroid = "8.0"
    updated_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
} | ConvertTo-Json

# UTF-8 without BOM
[IO.File]::WriteAllText($out, $payload + "`n", [Text.UTF8Encoding]::new($false))
Write-Host "Wrote $out → version $version"
