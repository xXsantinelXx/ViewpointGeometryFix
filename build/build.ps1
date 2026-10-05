# Builds the installable mod folder and zip on Windows.
# Needs only a JDK 17+ (javac/jar on PATH or JAVA_HOME). No game JAR, no network.
#   powershell -ExecutionPolicy Bypass -File build\build.ps1
#   powershell -ExecutionPolicy Bypass -File build\build.ps1 -Install   # also copies to %USERPROFILE%\Zomboid\mods
param([switch]$Install)
$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $PSScriptRoot
$Version = (Get-Content "$Root\VERSION" -Raw).Trim()
$Out = "$Root\build\out"
$Mod = "$Out\mod\ViewpointGeometryFix"
$Dist = "$Root\build\dist"

function Tool($name) {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\$name.exe")) { return "$env:JAVA_HOME\bin\$name.exe" }
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if (-not $cmd) { throw "$name not found. Install a JDK 17+ (e.g. Temurin) and set JAVA_HOME." }
    return $cmd.Source
}
$javac = Tool 'javac'
$jar = Tool 'jar'

if (Test-Path $Out) { Remove-Item -Recurse -Force $Out }
New-Item -ItemType Directory -Force "$Out\stubs", "$Out\classes", $Mod, $Dist | Out-Null

Write-Host '[build] compile stubs (compile-only, not packaged)'
$stubs = Get-ChildItem -Recurse "$Root\src\stubs\java" -Filter *.java | ForEach-Object FullName
& $javac --release 17 -nowarn -d "$Out\stubs" @stubs
if ($LASTEXITCODE) { throw 'stub compilation failed' }

Write-Host '[build] compile mod sources'
$srcs = Get-ChildItem -Recurse "$Root\src\main\java" -Filter *.java | ForEach-Object FullName
& $javac --release 17 -Xlint:all -Werror -cp "$Out\stubs" -d "$Out\classes" @srcs
if ($LASTEXITCODE) { throw 'compilation failed' }

Write-Host '[build] assemble mod folder'
Copy-Item -Recurse -Force "$Root\resources\mod\*" $Mod
$info = "$Mod\42\mod.info"
(Get-Content $info) -replace '^modversion=.*', "modversion=$Version" | Set-Content -Encoding ASCII $info
New-Item -ItemType Directory -Force "$Mod\42\media\java\client" | Out-Null
"Implementation-Title: ViewpointGeometryFix`nImplementation-Version: $Version`n" | Set-Content -Encoding ASCII "$Out\MANIFEST.MF"
& $jar --create --date="2026-01-01T00:00:00Z" --file "$Mod\42\media\java\client\ViewpointGeometryFix.jar" --manifest "$Out\MANIFEST.MF" -C "$Out\classes" .
if ($LASTEXITCODE) { throw 'jar failed' }
Copy-Item "$Root\LICENSE" "$Mod\LICENSE.txt"

$zip = "$Dist\ViewpointGeometryFix-$Version.zip"
if (Test-Path $zip) { Remove-Item $zip }
Compress-Archive -Path $Mod -DestinationPath $zip
Write-Host "[build] done: $zip"
Get-FileHash -Algorithm SHA256 "$Mod\42\media\java\client\ViewpointGeometryFix.jar" | Format-List

if ($Install) {
    $target = Join-Path $env:USERPROFILE 'Zomboid\mods\ViewpointGeometryFix'
    if (Get-Process -Name 'ProjectZomboid64' -ErrorAction SilentlyContinue) { throw 'Close Project Zomboid before installing (the JAR is locked while the game runs).' }
    if (Test-Path $target) { Remove-Item -Recurse -Force $target }
    Copy-Item -Recurse $Mod $target
    Write-Host "[build] installed to $target"
}
