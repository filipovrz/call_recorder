# Ensures gradle-wrapper.jar exists (downloads Gradle distribution once).
$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
$WrapperDir = Join-Path $Root "gradle\wrapper"
$Jar = Join-Path $WrapperDir "gradle-wrapper.jar"
New-Item -ItemType Directory -Force -Path $WrapperDir | Out-Null

if (Test-Path $Jar) {
    Write-Host "gradle-wrapper.jar already present."
    exit 0
}

$GradleVer = "8.7"
$Zip = Join-Path $env:TEMP "gradle-$GradleVer-bin.zip"
$Url = "https://services.gradle.org/distributions/gradle-$GradleVer-bin.zip"
$Extract = Join-Path $env:TEMP "gradle-$GradleVer-extract"

Write-Host "Downloading Gradle $GradleVer..."
Invoke-WebRequest -Uri $Url -OutFile $Zip
if (Test-Path $Extract) { Remove-Item $Extract -Recurse -Force }
Expand-Archive -Path $Zip -DestinationPath $Extract -Force

$DistHome = Join-Path $Extract "gradle-$GradleVer"
$env:Path = "$(Join-Path $DistHome 'bin');" + $env:Path

# Prefer JDK 17
$hit = Get-Item "${env:ProgramFiles}\Microsoft\jdk-17*\bin\java.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($hit) {
    $env:JAVA_HOME = Split-Path (Split-Path $hit.FullName -Parent) -Parent
    $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
}

Set-Location $Root
& (Join-Path $DistHome "bin\gradle.bat") wrapper --gradle-version $GradleVer
if (-not (Test-Path $Jar)) { throw "Failed to create gradle-wrapper.jar" }
Write-Host "Created $Jar"
