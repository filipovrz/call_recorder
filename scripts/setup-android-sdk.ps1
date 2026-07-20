# Downloads Android command-line tools (free) and installs platform packages needed to build.
$ErrorActionPreference = "Stop"

$SdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$CmdlineZip = Join-Path $env:TEMP "commandlinetools-win.zip"
$CmdlineUrl = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"

Write-Host "SDK root: $SdkRoot"
New-Item -ItemType Directory -Force -Path $SdkRoot | Out-Null

if (-not (Test-Path (Join-Path $SdkRoot "cmdline-tools\latest\bin\sdkmanager.bat"))) {
    Write-Host "Downloading Android command-line tools..."
    Invoke-WebRequest -Uri $CmdlineUrl -OutFile $CmdlineZip
    $ExtractTo = Join-Path $env:TEMP "android-cmdline-extract"
    if (Test-Path $ExtractTo) { Remove-Item $ExtractTo -Recurse -Force }
    Expand-Archive -Path $CmdlineZip -DestinationPath $ExtractTo -Force

    $Latest = Join-Path $SdkRoot "cmdline-tools\latest"
    New-Item -ItemType Directory -Force -Path $Latest | Out-Null
    Copy-Item -Path (Join-Path $ExtractTo "cmdline-tools\*") -Destination $Latest -Recurse -Force
}

$SdkManager = Join-Path $SdkRoot "cmdline-tools\latest\bin\sdkmanager.bat"
$packages = @(
    "platform-tools",
    "platforms;android-34",
    "build-tools;34.0.0"
)

Write-Host "Installing SDK packages (accepting licenses)..."
$yes = ("y`n" * 80)
$yes | & $SdkManager --sdk_root=$SdkRoot --licenses | Out-Null
& $SdkManager --sdk_root=$SdkRoot $packages

$LocalProps = Join-Path (Split-Path $PSScriptRoot -Parent) "local.properties"
"sdk.dir=$($SdkRoot -replace '\\','/')" | Set-Content -Path $LocalProps -Encoding ASCII
Write-Host "Wrote $LocalProps"
Write-Host "Android SDK setup done."
