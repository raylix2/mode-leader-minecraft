param([switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$Version = '0.3.8'
$ReleaseRoot = Join-Path $ProjectRoot 'release'
$StageRoot = Join-Path $ProjectRoot "out/release/UniversalServerLoader-$Version"
$Template = Join-Path $ProjectRoot 'distribution/UniversalServer'

if (-not $SkipBuild) { & (Join-Path $ProjectRoot 'build.ps1') }

if (Test-Path -LiteralPath $StageRoot) {
    throw "Le dossier de staging existe déjà : $StageRoot"
}

New-Item -ItemType Directory -Force -Path $StageRoot, $ReleaseRoot | Out-Null
Copy-Item -LiteralPath $Template -Destination (Join-Path $StageRoot 'UniversalServer') -Recurse
Copy-Item -LiteralPath (Join-Path $ProjectRoot 'LANCER-SERVEUR.bat') -Destination $StageRoot
Copy-Item -LiteralPath (Join-Path $ProjectRoot 'UniversalServer/universal-loader.jar') -Destination (Join-Path $StageRoot 'UniversalServer/universal-loader.jar')

$PlmJar = Join-Path $ProjectRoot 'UniversalServer/plugins/UniversalPLM.jar'
if (Test-Path -LiteralPath $PlmJar) {
    Copy-Item -LiteralPath $PlmJar -Destination (Join-Path $StageRoot 'UniversalServer/plugins/UniversalPLM.jar')
}

$Archive = Join-Path $ReleaseRoot "UniversalServerLoader-$Version.zip"
if (Test-Path -LiteralPath $Archive) { throw "L'archive existe déjà : $Archive" }
Compress-Archive -Path (Join-Path $StageRoot '*') -DestinationPath $Archive -CompressionLevel Optimal
Write-Output "Release créée : $Archive"
