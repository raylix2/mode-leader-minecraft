$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$OutputRoot = Join-Path $ProjectRoot 'out'
$MainClasses = Join-Path $OutputRoot 'main'
$TestClasses = Join-Path $OutputRoot 'test'
$ExampleClasses = Join-Path $OutputRoot 'example-addon'
$PlmClasses = Join-Path $OutputRoot 'universal-plm'
$Distribution = Join-Path $ProjectRoot 'UniversalServer'

New-Item -ItemType Directory -Force -Path $MainClasses, $TestClasses, $ExampleClasses, $PlmClasses, $Distribution | Out-Null
$MainSources = Get-ChildItem (Join-Path $ProjectRoot 'src/main/java') -Recurse -Filter *.java | ForEach-Object FullName
javac --release 8 -encoding UTF-8 -d $MainClasses $MainSources
if ($LASTEXITCODE -ne 0) { throw 'Compilation des sources principales échouée.' }
$Manifest = Join-Path $OutputRoot 'manifest.mf'
Set-Content -LiteralPath $Manifest -Encoding Ascii -Value "Main-Class: fr.universalserverloader.Main`n"
jar cfm (Join-Path $Distribution 'universal-loader.jar') $Manifest -C $MainClasses .
if ($LASTEXITCODE -ne 0) { throw 'Création de universal-loader.jar échouée. Fermez l’interface si elle est ouverte.' }

$TestSources = Get-ChildItem (Join-Path $ProjectRoot 'src/test/java') -Recurse -Filter *.java | ForEach-Object FullName
javac --release 8 -encoding UTF-8 -cp $MainClasses -d $TestClasses $TestSources
if ($LASTEXITCODE -ne 0) { throw 'Compilation des tests échouée.' }

$ExampleSources = Get-ChildItem (Join-Path $ProjectRoot 'examples/example-addon/src') -Recurse -Filter *.java | ForEach-Object FullName
javac --release 8 -encoding UTF-8 -cp $MainClasses -d $ExampleClasses $ExampleSources
if ($LASTEXITCODE -ne 0) { throw 'Compilation de l’addon exemple échouée.' }
jar cf (Join-Path $ProjectRoot 'examples/example-addon/example-addon.jar') -C $ExampleClasses . -C (Join-Path $ProjectRoot 'examples/example-addon/resources') .
if ($LASTEXITCODE -ne 0) { throw 'Création du JAR addon échouée.' }

$PaperLibrariesRoot = Join-Path $Distribution 'libraries'
$PaperLibraries = if (Test-Path -LiteralPath $PaperLibrariesRoot) {
    @(Get-ChildItem $PaperLibrariesRoot -Recurse -Filter *.jar | ForEach-Object FullName)
} else { @() }
if ($PaperLibraries.Count -gt 0) {
    $PaperClasspath = $PaperLibraries -join ';'
    $PlmSources = Get-ChildItem (Join-Path $ProjectRoot 'plugins/universal-plm/src') -Recurse -Filter *.java | ForEach-Object FullName
    javac --release 21 -encoding UTF-8 -proc:none -cp $PaperClasspath -d $PlmClasses $PlmSources
    if ($LASTEXITCODE -ne 0) { throw 'Compilation du plugin UniversalPLM échouée.' }
    New-Item -ItemType Directory -Force -Path (Join-Path $Distribution 'plugins') | Out-Null
    jar cf (Join-Path $Distribution 'plugins/UniversalPLM.jar') -C $PlmClasses . -C (Join-Path $ProjectRoot 'plugins/universal-plm/resources') .
    if ($LASTEXITCODE -ne 0) { throw 'Création de UniversalPLM.jar échouée.' }
} else {
    Write-Warning 'Bibliothèques Paper absentes : compilation de UniversalPLM ignorée.'
}

Write-Output "Compilation terminée: UniversalServer/universal-loader.jar et plugins/UniversalPLM.jar"
