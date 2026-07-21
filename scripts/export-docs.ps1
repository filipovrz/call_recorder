# Exports project save docs to Word 97/2003-friendly .doc (HTML Word format).
# Opens in Microsoft Office 2003 Word and later.
$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent
$OutDir = Join-Path $Root "docs"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Convert-ToWordHtmlDoc {
    param(
        [Parameter(Mandatory = $true)][string]$SourcePath,
        [Parameter(Mandatory = $true)][string]$DestPath,
        [Parameter(Mandatory = $true)][string]$Title
    )
    if (-not (Test-Path -LiteralPath $SourcePath)) {
        Write-Warning "Skip missing: $SourcePath"
        return
    }
    $text = [IO.File]::ReadAllText($SourcePath, [Text.Encoding]::UTF8)
    $encoded = [System.Net.WebUtility]::HtmlEncode($text)
    $encoded = $encoded -replace "`r`n", "<br>`r`n" -replace "`n", "<br>`r`n" -replace "`r", "<br>`r`n"
    $when = Get-Date -Format "yyyy-MM-dd HH:mm"
    $html = @"
<html xmlns:o="urn:schemas-microsoft-com:office:office"
xmlns:w="urn:schemas-microsoft-com:office:word"
xmlns="http://www.w3.org/TR/REC-html40">
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8">
<meta name="ProgId" content="Word.Document">
<meta name="Generator" content="Evtinko export-docs">
<title>$Title</title>
<!--[if gte mso 9]>
<xml>
<w:WordDocument>
<w:View>Print</w:View>
<w:Zoom>100</w:Zoom>
<w:DoNotOptimizeForBrowser/>
</w:WordDocument>
</xml>
<![endif]-->
<style>
body { font-family: "Times New Roman", Times, serif; font-size: 12pt; }
.body { white-space: pre-wrap; font-family: "Courier New", Courier, monospace; font-size: 10pt; }
h1 { font-family: Arial, sans-serif; font-size: 16pt; }
</style>
</head>
<body>
<h1>$Title</h1>
<p>Auctions Evtinko Ltd. / Evtinko Call Recorder / export $when</p>
<div class="body">$encoded</div>
</body>
</html>
"@
    $utf8 = New-Object System.Text.UTF8Encoding $false
    [IO.File]::WriteAllText($DestPath, $html, $utf8)
    Write-Host "Wrote $DestPath"
}

Convert-ToWordHtmlDoc -SourcePath (Join-Path $Root "CHECKPOINTS.md") -DestPath (Join-Path $OutDir "CHECKPOINTS.doc") -Title "CHECKPOINTS"
Convert-ToWordHtmlDoc -SourcePath (Join-Path $Root "README.md") -DestPath (Join-Path $OutDir "README.doc") -Title "README"
Convert-ToWordHtmlDoc -SourcePath (Join-Path $Root "HOSTING.md") -DestPath (Join-Path $OutDir "HOSTING.doc") -Title "HOSTING"
Convert-ToWordHtmlDoc -SourcePath (Join-Path $Root "BUILD.md") -DestPath (Join-Path $OutDir "BUILD.doc") -Title "BUILD"

# History file has Cyrillic name — find by extension/prefix in root.
$history = Get-ChildItem -LiteralPath $Root -File | Where-Object { $_.Name -like "*.txt" -and $_.Name -ne "robots.txt" } | Select-Object -First 1
# Prefer the long Bulgarian history name if present
$historyCandidates = Get-ChildItem -LiteralPath $Root -Filter "*.txt" -File -ErrorAction SilentlyContinue
foreach ($c in $historyCandidates) {
    if ($c.Length -gt 1000) { $history = $c; break }
}
if ($history) {
    Convert-ToWordHtmlDoc -SourcePath $history.FullName -DestPath (Join-Path $OutDir "Istoriya_na_zadachite.doc") -Title "Istoriya na zadachite"
} else {
    Write-Warning "History txt not found"
}

Write-Host "Done. Open docs\*.doc with Office 2003 Word."
