$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
& (Join-Path $ProjectRoot 'build.ps1')
java -cp "$(Join-Path $ProjectRoot 'out/main');$(Join-Path $ProjectRoot 'out/test')" fr.universalserverloader.AllTests
