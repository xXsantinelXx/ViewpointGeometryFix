<#
  VPGF RoofData - Dach-Fix Variante A (experimentell).

  Liest die tileGeometry.txt DEINES Spiels und schreibt eine Ergaenzungsdatei
  in den Ordner der Mod ViewpointGeometryFix:
      <Zomboid>\mods\ViewpointGeometryFix\42\media\tileGeometry.txt
  Darin bekommen Dach-Tiles ohne 3D-Form die Form des gleichen Tiles (gleiches xy)
  aus dem Schwester-Tileset:  roofs_02..05 <- roofs_01,  roofs_30_02..10 <- roofs_30_01.
  Die Spieldatei selbst wird NICHT veraendert. Rueckgaengig: VPGF-RoofData.bat -Remove

  Ob Spiel und Viewpoint diese Mod-Datei lesen, ist noch unbekannt - genau das testet sie.
#>
param(
    [string]$GameDir = "",
    [string]$Zomboid = "",
    [switch]$Remove
)
$ErrorActionPreference = 'Stop'

function Find-Game {
    $roots = New-Object System.Collections.Generic.List[string]
    foreach ($key in @('HKCU:\Software\Valve\Steam', 'HKLM:\SOFTWARE\WOW6432Node\Valve\Steam')) {
        try { $p = Get-ItemProperty -Path $key -ErrorAction Stop; foreach ($v in @($p.SteamPath, $p.InstallPath)) { if ($v) { $roots.Add(($v -replace '/', '\')) } } } catch { }
    }
    try { [IO.DriveInfo]::GetDrives() | Where-Object { $_.IsReady -and $_.DriveType -eq 'Fixed' } | ForEach-Object { $roots.Add([IO.Path]::Combine($_.RootDirectory.FullName, 'SteamLibrary')); $roots.Add([IO.Path]::Combine($_.RootDirectory.FullName, 'Steam')) } } catch { }
    $libs = New-Object System.Collections.Generic.List[string]
    foreach ($r in $roots) {
        if (-not (Test-Path -LiteralPath ([IO.Path]::Combine($r, 'steamapps')))) { continue }
        $libs.Add($r)
        $vdf = [IO.Path]::Combine($r, 'steamapps', 'libraryfolders.vdf')
        if (Test-Path -LiteralPath $vdf) {
            foreach ($m in [regex]::Matches((Get-Content -LiteralPath $vdf -Raw), '"path"\s+"([^"]+)"')) { $libs.Add(($m.Groups[1].Value -replace '\\\\', '\')) }
        }
    }
    foreach ($l in $libs) {
        $pz = [IO.Path]::Combine($l, 'steamapps', 'common', 'ProjectZomboid')
        if (Test-Path -LiteralPath ([IO.Path]::Combine($pz, 'media', 'tileGeometry.txt'))) { return $pz }
    }
    return $null
}

# Sibling rules (must match vpgeometryfix.fix.RoofFallback)
function Sibling([string]$tileset) {
    if ($tileset -match '^roofs_0[2-5]$') { return 'roofs_01' }
    if ($tileset -match '^roofs_30_(0[2-9]|10)$') { return 'roofs_30_01' }
    return $null
}

# Parses tilesets of interest into: name -> ordered list of @{ xy; shapes = [lines]; props = [lines] }
function Read-Tilesets([string[]]$lines, [string[]]$wanted) {
    $result = @{}
    $depth = 0; $tilesetDepth = -1; $tileDepth = -1; $name = $null; $pendingTileset = $false; $pendingTile = $false
    $tile = $null; $block = $null; $blockKind = $null
    foreach ($raw in $lines) {
        $t = $raw.Trim()
        if ($t -eq '{') {
            $depth++
            if ($pendingTileset) { $tilesetDepth = $depth; $pendingTileset = $false; $name = $null }
            elseif ($pendingTile) { $tileDepth = $depth; $pendingTile = $false }
            if ($null -ne $block) { $block.Add($raw) }
            continue
        }
        if ($t -eq '}') {
            if ($null -ne $block) { $block.Add($raw) }
            if ($null -ne $block -and $depth -eq $tileDepth + 1) {
                if ($blockKind -eq 'properties') { $tile.props = $block } else { $tile.shapes.AddRange($block) }
                $block = $null; $blockKind = $null
            } elseif ($depth -eq $tileDepth -and $null -ne $tile) {
                if ($name -and $wanted -contains $name) {
                    if (-not $result.ContainsKey($name)) { $result[$name] = New-Object System.Collections.Generic.List[object] }
                    $result[$name].Add($tile)
                }
                $tile = $null; $tileDepth = -1
            } elseif ($depth -eq $tilesetDepth) { $tilesetDepth = -1; $name = $null }
            $depth--
            continue
        }
        if ($null -ne $block) { $block.Add($raw); continue }
        if ($t -eq 'tileset') { $pendingTileset = $true; continue }
        if ($tilesetDepth -ge 0 -and $depth -eq $tilesetDepth) {
            if ($t.StartsWith('name = ')) { $name = $t.Substring(7).TrimEnd(',').Trim() }
            elseif ($t -eq 'tile') { $pendingTile = $true; $tile = @{ xy = $null; shapes = (New-Object System.Collections.Generic.List[string]); props = $null } }
            continue
        }
        if ($null -ne $tile -and $depth -eq $tileDepth) {
            if ($t.StartsWith('xy = ')) { $tile.xy = $t.Substring(5).TrimEnd(',').Trim() }
            elseif ($t -match '^[A-Za-z]+$') {
                $blockKind = $t
                $block = New-Object System.Collections.Generic.List[string]
                $block.Add($raw)
            }
        }
    }
    return $result
}

if (-not $Zomboid) { $Zomboid = [IO.Path]::Combine($env:USERPROFILE, 'Zomboid') }
$modMedia = [IO.Path]::Combine($Zomboid, 'mods', 'ViewpointGeometryFix', '42', 'media')
$out = [IO.Path]::Combine($modMedia, 'tileGeometry.txt')

if ($Remove) {
    if (Test-Path -LiteralPath $out) { Remove-Item -LiteralPath $out; Write-Host "Entfernt: $out" } else { Write-Host "Nichts zu entfernen: $out" }
    exit 0
}
if (-not (Test-Path -LiteralPath $modMedia)) { throw "Mod-Ordner nicht gefunden: $modMedia - zuerst ViewpointGeometryFix installieren." }
if (-not $GameDir) { $GameDir = Find-Game }
if (-not $GameDir) { throw 'Project Zomboid nicht gefunden. Start mit: VPGF-RoofData.bat -GameDir "F:\SteamLibrary\steamapps\common\ProjectZomboid"' }
$src = [IO.Path]::Combine($GameDir, 'media', 'tileGeometry.txt')
Write-Host "Lese $src ..."
$lines = [IO.File]::ReadAllLines($src)

$targets = @('roofs_02', 'roofs_03', 'roofs_04', 'roofs_05') + (2..10 | ForEach-Object { 'roofs_30_{0:D2}' -f $_ })
$wanted = @('roofs_01', 'roofs_30_01') + $targets
$sets = Read-Tilesets $lines $wanted

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine('tileGeometry')
[void]$sb.AppendLine('{')
[void]$sb.AppendLine('    VERSION = 2,')
$total = 0
foreach ($target in $targets) {
    $sib = Sibling $target
    if (-not $sets.ContainsKey($target) -or -not $sets.ContainsKey($sib)) { continue }
    $byXy = @{}
    foreach ($st in $sets[$sib]) { if ($st.xy -and $st.shapes.Count -gt 0) { $byXy[$st.xy] = $st } }
    $tiles = New-Object System.Collections.Generic.List[string]
    $count = 0
    foreach ($tt in $sets[$target]) {
        if (-not $tt.xy -or $tt.shapes.Count -gt 0 -or -not $byXy.ContainsKey($tt.xy)) { continue }
        $tiles.Add('')
        $tiles.Add("        /* $target xy=$($tt.xy) <- $sib (VPGeometryFix) */")
        $tiles.Add('        tile')
        $tiles.Add('        {')
        $tiles.Add("            xy = $($tt.xy),")
        $tiles.Add('')
        foreach ($l in $byXy[$tt.xy].shapes) { $tiles.Add($l) }
        if ($tt.props) { $tiles.Add(''); foreach ($l in $tt.props) { $tiles.Add($l) } }
        $tiles.Add('        }')
        $count++
    }
    if ($count -eq 0) { continue }
    [void]$sb.AppendLine('')
    [void]$sb.AppendLine('    tileset')
    [void]$sb.AppendLine('    {')
    [void]$sb.AppendLine("        name = $target,")
    foreach ($l in $tiles) { [void]$sb.AppendLine($l) }
    [void]$sb.AppendLine('    }')
    Write-Host ("  {0}: {1} Tiles von {2} uebernommen" -f $target, $count, $sib)
    $total += $count
}
[void]$sb.AppendLine('}')
[IO.File]::WriteAllText($out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host ''
Write-Host "Geschrieben: $out ($total Tiles). Spiel neu starten, um die Wirkung zu sehen."
Write-Host 'Rueckgaengig: VPGF-RoofData.bat -Remove'
