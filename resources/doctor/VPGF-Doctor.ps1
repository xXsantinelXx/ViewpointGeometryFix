<#
  VPGF Doctor - prueft die Installation ausserhalb des Spiels. Nur lesend.
  Braucht kein Java: laeuft mit der in Windows enthaltenen PowerShell (5.1+).

  Start: VPGF-Doctor.bat doppelklicken.
  Optional: VPGF-Doctor.bat -SteamLib "D:\SteamLibrary" -Zomboid "C:\Users\Name\Zomboid"
#>
param(
    [string[]]$SteamLib = @(),
    [string]$Zomboid = "",
    [string]$Out = ""
)

$ErrorActionPreference = 'Continue'
$Version = '0.3.8'
$ModId = 'ViewpointGeometryFix'
$Pins = @{
    'e1a69eb743ede60b213a0fe7f8b83d4fcab773036d256cc4543a336f3b058a33' = 'projectzomboid.jar 42.21.0'
    '94fedda302ab6c17ba1b38495789e4c9781d52823fb8204214c85402e3cab41f' = 'Viewpoint 0.1.5a-hotfix'
    '6dd95cedce60f03bf8b8cefd0d19eb156230e0d54bffa07de9da5212a06c7be6' = 'ZombieBuddy 2.3.2 (original)'
    'dd13e6e06e64be0e832a4f13508c6872de36c9c7b2290023c5884a7f74467283' = 'ZombieBuddy 2.3.2 (B42.21 temporary fix)'
}
$Keywords = @('render', 'cull', 'visib', 'mesh', 'vertex', 'model', 'roof', 'wall', 'tile', 'sprite', 'chunk',
    'room', 'floor', 'shell', 'far', 'pick', 'building', 'geometry', 'cutaway', 'batch', 'scene', 'world',
    'occlu', 'stair', 'depth', 'bake')

$script:Report = New-Object System.Collections.Generic.List[string]
$script:Findings = New-Object System.Collections.Generic.List[string]

function Line([string]$s) { $script:Report.Add($s) }
function Section([string]$t) { $script:Report.Add(''); $script:Report.Add("=== $t ==="); Write-Host "=== $t" }
function Finding([string]$s) { $script:Findings.Add($s) }

function Read-ModInfo([string]$path) {
    $h = @{}
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { return $null }
    foreach ($raw in (Get-Content -LiteralPath $path -ErrorAction SilentlyContinue)) {
        $l = $raw.Trim()
        $eq = $l.IndexOf('=')
        if ($l.StartsWith('#') -or $eq -le 0) { continue }
        $k = $l.Substring(0, $eq).Trim()
        if (-not $h.ContainsKey($k)) { $h[$k] = $l.Substring($eq + 1).Trim() }
    }
    return $h
}

function Clean-Id($id) { if ($null -eq $id) { return '' } return ($id -replace '\\', '').Trim() }

function Describe-Jar([string]$path) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { return 'FEHLT' }
    $sha = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLower()
    $label = $Pins[$sha]
    if (-not $label) { $label = 'kein auditierter Build' }
    return ('sha256 {0}... -> {1}' -f $sha.Substring(0, 16), $label)
}

function Get-SteamLibraries {
    $roots = New-Object System.Collections.Generic.List[string]
    foreach ($key in @('HKCU:\Software\Valve\Steam', 'HKLM:\SOFTWARE\WOW6432Node\Valve\Steam', 'HKLM:\SOFTWARE\Valve\Steam')) {
        try {
            $p = Get-ItemProperty -Path $key -ErrorAction Stop
            foreach ($v in @($p.SteamPath, $p.InstallPath)) { if ($v) { $roots.Add(($v -replace '/', '\')) } }
        } catch { }
    }
    $drives = @()
    try { $drives = @([IO.DriveInfo]::GetDrives() | Where-Object { $_.IsReady -and $_.DriveType -eq 'Fixed' } | ForEach-Object { $_.RootDirectory.FullName }) } catch { }
    foreach ($d in $drives) {
        foreach ($sub in @('SteamLibrary', 'Steam', 'Games\Steam', 'Games\SteamLibrary', 'Program Files (x86)\Steam', 'Program Files\Steam')) {
            $roots.Add([IO.Path]::Combine($d, $sub))
        }
    }
    $libs = New-Object System.Collections.Generic.List[string]
    foreach ($r in $roots) {
        if (-not (Test-Path -LiteralPath ([IO.Path]::Combine($r, 'steamapps')))) { continue }
        if (-not $libs.Contains($r)) { $libs.Add($r) }
        $vdf = [IO.Path]::Combine($r, 'steamapps', 'libraryfolders.vdf')
        if (Test-Path -LiteralPath $vdf) {
            foreach ($m in [regex]::Matches((Get-Content -LiteralPath $vdf -Raw), '"path"\s+"([^"]+)"')) {
                $lib = $m.Groups[1].Value -replace '\\\\', '\'
                if ((Test-Path -LiteralPath ([IO.Path]::Combine($lib, 'steamapps'))) -and -not $libs.Contains($lib)) { $libs.Add($lib) }
            }
        }
    }
    return $libs
}

function Get-WorkshopMods {
    $result = New-Object System.Collections.Generic.List[string]
    foreach ($lib in $script:Libs) {
        $ws = [IO.Path]::Combine($lib, 'steamapps', 'workshop', 'content', '108600')
        if (-not (Test-Path -LiteralPath $ws)) { continue }
        Get-ChildItem -LiteralPath $ws -Recurse -Depth 6 -Filter 'mod.info' -File -ErrorAction SilentlyContinue |
            ForEach-Object { $result.Add($_.DirectoryName) }
    }
    return $result
}

function Get-Jars([string]$dir) {
    return @(Get-ChildItem -LiteralPath $dir -Recurse -Depth 8 -Filter '*.jar' -File -ErrorAction SilentlyContinue | Sort-Object FullName)
}

# ---------------------------------------------------------------- class files
# Minimal reader for the public JVM class-file format (JVMS chapter 4): class,
# super class, field and method names with their types. No bytecode, no
# decompilation - only the signatures needed to find hook points.
$GeometryRx = '^viewpoint\.(world|visibility)\.|(?i)(mesh|shell|facade|recipe|roof|wall|tile|edge|cook|gather|slope)'

function U1 { $v = [int]$script:cb[$script:cp]; $script:cp += 1; return $v }
function U2 { $v = ([int]$script:cb[$script:cp] -shl 8) -bor [int]$script:cb[$script:cp + 1]; $script:cp += 2; return $v }
function Skip([int]$n) { $script:cp += $n }

function Type-Name([string]$d, [ref]$i) {
    $dims = ''
    while ($d[$i.Value] -eq '[') { $dims += '[]'; $i.Value++ }
    $c = $d[$i.Value]; $i.Value++
    switch ($c) {
        'B' { return 'byte' + $dims } 'C' { return 'char' + $dims } 'D' { return 'double' + $dims }
        'F' { return 'float' + $dims } 'I' { return 'int' + $dims } 'J' { return 'long' + $dims }
        'S' { return 'short' + $dims } 'Z' { return 'boolean' + $dims } 'V' { return 'void' }
        'L' {
            $end = $d.IndexOf(';', $i.Value)
            $n = $d.Substring($i.Value, $end - $i.Value).Replace('/', '.')
            $i.Value = $end + 1
            $n = $n -replace '^java\.lang\.', ''
            return $n + $dims
        }
    }
    return '?'
}

function Format-Member([string]$name, [string]$desc, [bool]$isStatic) {
    $pre = ''
    if ($isStatic) { $pre = 'static ' }
    if ($desc.StartsWith('(')) {
        $i = 1; $params = @()
        while ($desc[$i] -ne ')') { $params += Type-Name $desc ([ref]$i) }
        $i++
        $ret = Type-Name $desc ([ref]$i)
        return ('  M {0}{1} {2}({3})' -f $pre, $ret, $name, ($params -join ', '))
    }
    $j = 0
    return ('  F {0}{1} {2}' -f $pre, (Type-Name $desc ([ref]$j)), $name)
}

function Read-ClassFile([byte[]]$bytes) {
    $script:cb = $bytes; $script:cp = 8
    $count = U2
    $utf = @{}; $cls = @{}
    for ($k = 1; $k -lt $count; $k++) {
        $tag = U1
        switch ($tag) {
            1 { $len = U2; $utf[$k] = [Text.Encoding]::UTF8.GetString($script:cb, $script:cp, $len); Skip $len }
            7 { $cls[$k] = U2 }
            { $_ -in 3, 4 } { Skip 4 }
            { $_ -in 5, 6 } { Skip 8; $k++ }
            { $_ -in 8, 16, 19, 20 } { Skip 2 }
            { $_ -in 9, 10, 11, 12, 17, 18 } { Skip 4 }
            15 { Skip 3 }
            default { throw "unknown constant pool tag $tag" }
        }
    }
    Skip 2
    $thisName = $utf[$cls[(U2)]].Replace('/', '.')
    $superIdx = U2
    $super = ''
    if ($superIdx -ne 0) { $super = $utf[$cls[$superIdx]].Replace('/', '.') }
    $ifaces = U2; Skip (2 * $ifaces)
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("$thisName extends $super")
    foreach ($kind in 'F', 'M') {
        $n = U2
        for ($m = 0; $m -lt $n; $m++) {
            $acc = U2; $name = $utf[(U2)]; $desc = $utf[(U2)]
            $attrs = U2
            for ($a = 0; $a -lt $attrs; $a++) {
                Skip 2
                $alen = ([long](U2) -shl 16) -bor (U2)
                Skip $alen
            }
            if (($acc -band 0x1000) -ne 0) { continue }  # synthetic (lambdas, bridges)
            $lines.Add((Format-Member $name $desc (($acc -band 0x0008) -ne 0)))
        }
    }
    return $lines
}

# ---------------------------------------------------------------- setup
if (-not $Zomboid) { $Zomboid = [IO.Path]::Combine($env:USERPROFILE, 'Zomboid') }
if (-not $Out) { $Out = [IO.Path]::Combine((Get-Location).Path, 'VPGF-Report.txt') }
if ($SteamLib.Count -gt 0) { $script:Libs = $SteamLib } else { $script:Libs = Get-SteamLibraries }
$workshop = Get-WorkshopMods

Line ("VPGF Doctor $Version - " + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
Line 'Nur lesend. Diesen Bericht und VPGF-Viewpoint-Geometry.txt an den Entwickler schicken (beide nicht oeffentlich posten).'

Section '1 Pfade'
$zState = ''
if (-not (Test-Path -LiteralPath $Zomboid)) { $zState = '  (FEHLT)' }
Line "Zomboid-Benutzerordner: $Zomboid$zState"
foreach ($l in $script:Libs) { Line "Steam-Bibliothek: $l" }
Line ("PowerShell: " + $PSVersionTable.PSVersion.ToString())
if ($script:Libs.Count -eq 0) { Finding 'Keine Steam-Bibliothek gefunden. Start mit: VPGF-Doctor.bat -SteamLib "D:\SteamLibrary"' }

# ---------------------------------------------------------------- game
Section '2 Project Zomboid'
$pz = $null
foreach ($l in $script:Libs) {
    $cand = [IO.Path]::Combine($l, 'steamapps', 'common', 'ProjectZomboid')
    if (Test-Path -LiteralPath $cand) { $pz = $cand; break }
}
if (-not $pz) {
    Line 'Installation nicht gefunden.'
    Finding 'Project Zomboid nicht gefunden - Steam-Bibliothek mit -SteamLib angeben.'
} else {
    Line "Installation: $pz"
    Line ('projectzomboid.jar: ' + (Describe-Jar ([IO.Path]::Combine($pz, 'projectzomboid.jar'))))
    $agentFound = $false
    foreach ($json in @('ProjectZomboid64.json', 'ProjectZomboid64ShowConsole.json')) {
        $f = [IO.Path]::Combine($pz, $json)
        if (-not (Test-Path -LiteralPath $f)) { continue }
        $lines = @(Get-Content -LiteralPath $f | Where-Object { $_ -match 'javaagent' })
        if ($lines.Count -gt 0) { $agentFound = $true; Line "${json}: javaagent eingetragen"; $lines | ForEach-Object { Line ('    ' + $_.Trim()) } }
        else { Line "${json}: javaagent NICHT eingetragen" }
    }
    # ZombieBuddy can also start through other files (e.g. a native loader); list them.
    Get-ChildItem -LiteralPath $pz -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '(?i)zombiebuddy|^zb|zbnative' } |
        ForEach-Object { Line ('ZombieBuddy-Datei im Spielordner: ' + $_.Name) }
}

# ---------------------------------------------------------------- zombiebuddy
Section '3 ZombieBuddy'
$zbJars = New-Object System.Collections.Generic.List[string]
if ($pz -and (Test-Path -LiteralPath ([IO.Path]::Combine($pz, 'ZombieBuddy.jar')))) { $zbJars.Add(([IO.Path]::Combine($pz, 'ZombieBuddy.jar'))) }
foreach ($m in $workshop) {
    $mi = Read-ModInfo ([IO.Path]::Combine($m, 'mod.info'))
    if ($mi -and (Clean-Id $mi['id']) -eq 'ZombieBuddy') {
        Line ("Workshop-Mod: $m (modversion " + $mi['modversion'] + ')')
        Get-Jars $m | ForEach-Object { $zbJars.Add($_.FullName) }
    }
}
if ($zbJars.Count -eq 0) { Line 'ZombieBuddy.jar nicht im Spielordner/Workshop gefunden (Abschnitt 6 zeigt, ob ZombieBuddy trotzdem laeuft).' }
foreach ($j in $zbJars) { Line ("$j : " + (Describe-Jar $j)) }

# ---------------------------------------------------------------- viewpoint
Section '4 Viewpoint'
$addons = New-Object System.Collections.Generic.List[string]
$seenRoots = @{}
$classOut = [IO.Path]::Combine((Split-Path -Parent ([IO.Path]::GetFullPath($Out))), 'VPGF-Viewpoint-Classes.txt')
$vpFound = $false
foreach ($m in $workshop) {
    $mi = Read-ModInfo ([IO.Path]::Combine($m, 'mod.info'))
    if (-not $mi) { continue }
    $id = Clean-Id $mi['id']
    if ($id -notmatch 'viewpoint' -or $id -eq $ModId) { continue }
    if ($id -ne 'Viewpoint') {
        $a = "$id " + $mi['modversion']
        if (-not $addons.Contains($a)) { $addons.Add($a) }
        continue
    }
    # The JAR may sit in another version folder (42, 42.21, common) than mod.info: search the whole mod root.
    $root = Split-Path -Parent $m
    if ($seenRoots.ContainsKey($root)) { continue }
    $seenRoots[$root] = $true
    $vpFound = $true
    Line ("Mod: id=$id name=" + $mi['name'] + ' modversion=' + $mi['modversion'])
    Line "    Ordner: $root"
    Line ('    javaJarFile=' + $mi['javaJarFile'] + ' javaPkgName=' + $mi['javaPkgName'] + ' require=' + $mi['require'])
    $vpJars = @(Get-Jars $root)
    if ($vpJars.Count -eq 0) { Line '    Kein JAR im Mod-Ordner gefunden.' }
    foreach ($jar in $vpJars) {
        Line ('    ' + $jar.FullName.Substring($root.Length).TrimStart('\', '/') + ': ' + (Describe-Jar $jar.FullName))
        try {
            Add-Type -AssemblyName System.IO.Compression.FileSystem
            $zip = [System.IO.Compression.ZipFile]::OpenRead($jar.FullName)
            $names = @($zip.Entries | Where-Object { $_.FullName.EndsWith('.class') } |
                ForEach-Object { $_.FullName.Substring(0, $_.FullName.Length - 6).Replace('/', '.') } | Sort-Object)
            # Signatures of geometry/visibility classes (named inner classes included, lambdas/anonymous excluded)
            $geo = New-Object System.Collections.Generic.List[string]
            $geo.Add('# Viewpoint-Geometrie-Klassen: Signaturen aus ' + $jar.FullName)
            $geo.Add('# Nur zur Analyse - nicht veroeffentlichen.')
            $geoCount = 0
            foreach ($e in ($zip.Entries | Sort-Object FullName)) {
                if (-not $e.FullName.EndsWith('.class')) { continue }
                $cn = $e.FullName.Substring(0, $e.FullName.Length - 6).Replace('/', '.')
                if ($cn -notmatch $GeometryRx -or $cn -match '\$\d') { continue }
                try {
                    $st = $e.Open(); $ms = New-Object System.IO.MemoryStream
                    $st.CopyTo($ms); $st.Dispose()
                    $geo.Add('')
                    foreach ($gl in (Read-ClassFile $ms.ToArray())) { $geo.Add($gl) }
                    $geoCount++
                } catch {
                    $geo.Add("$cn : nicht lesbar (" + $_.Exception.Message + ')')
                }
            }
            $zip.Dispose()
            $geoOut = [IO.Path]::Combine((Split-Path -Parent ([IO.Path]::GetFullPath($Out))), 'VPGF-Viewpoint-Geometry.txt')
            $geo | Set-Content -LiteralPath $geoOut -Encoding UTF8
            Line ("    Signaturen von $geoCount Geometrie-/Sichtbarkeits-Klassen: $geoOut")
            $hits = @($names | Where-Object { $n = $_.ToLower(); @($Keywords | Where-Object { $n.Contains($_) }).Count -gt 0 })
            Line ("    Klassen gesamt: {0}, davon mit Geometrie-/Render-Stichwort: {1}" -f $names.Count, $hits.Count)
            $hits | Where-Object { $_ -notmatch '\$' } | ForEach-Object { Line "      $_" }
            $names | Set-Content -LiteralPath $classOut -Encoding UTF8
            Line "    Alle Klassennamen: $classOut"
        } catch {
            Line ('    Klassen nicht lesbar: ' + $_.Exception.Message)
        }
    }
}
if (-not $vpFound) { Line 'Keine Viewpoint-Mod im Workshop-Ordner gefunden.'; Finding 'Viewpoint nicht gefunden (nur Workshop-Ordner durchsucht).' }
if ($addons.Count -gt 0) { Line ('Viewpoint-Add-ons im Workshop-Ordner (' + $addons.Count + '): ' + ($addons -join ', ')) }

# ---------------------------------------------------------------- tile geometry data
# Viewpoint builds near-world tile shapes from the game's tile geometry
# (TileMeshes.geometryFor -> zombie.tileDepth.TileGeometryFile, [V1] signatures).
# List the data files that may hold it, in the game and in Workshop mods.
Section '4b Tile-Geometrie-Daten (Quelle der 3D-Formen)'
$geoRx = '(?i)(tile.?geometry|tile.?depth|tiledepth|geometry\.txt)'
$geoFiles = New-Object System.Collections.Generic.List[string]
if ($pz) {
    $media = [IO.Path]::Combine($pz, 'media')
    if (Test-Path -LiteralPath $media) {
        Get-ChildItem -LiteralPath $media -Recurse -File -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName.Substring($media.Length) -match $geoRx } |
            ForEach-Object { $geoFiles.Add(('Spiel: {0} ({1} KB)' -f $_.FullName.Substring($pz.Length).TrimStart('\', '/'), [Math]::Ceiling($_.Length / 1KB))) }
    }
}
$wsRoots = @{}
foreach ($m in $workshop) { $wsRoots[(Split-Path -Parent $m)] = $true }
foreach ($root in $wsRoots.Keys) {
    Get-ChildItem -LiteralPath $root -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName.Substring($root.Length) -match $geoRx } |
        ForEach-Object { $geoFiles.Add(('Mod {0}: {1} ({2} KB)' -f (Split-Path -Leaf $root), $_.FullName.Substring($root.Length).TrimStart('\', '/'), [Math]::Ceiling($_.Length / 1KB))) }
}
$localMods = [IO.Path]::Combine($Zomboid, 'mods')
if (Test-Path -LiteralPath $localMods) {
    Get-ChildItem -LiteralPath $localMods -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName.Substring($localMods.Length) -match $geoRx } |
        ForEach-Object { $geoFiles.Add(('Lokal {0} ({1} KB)' -f $_.FullName.Substring($localMods.Length).TrimStart('\', '/'), [Math]::Ceiling($_.Length / 1KB))) }
}
if ($geoFiles.Count -eq 0) { Line 'Keine Dateien mit tileGeometry/tileDepth im Namen gefunden.' }
$geoFiles | Sort-Object | Select-Object -First 80 | ForEach-Object { Line $_ }
if ($geoFiles.Count -gt 80) { Line ('(' + ($geoFiles.Count - 80) + ' weitere ausgelassen)') }

# ---------------------------------------------------------------- tile geometry format
# Short excerpts only: the file format (first lines), the entries of roof
# tilesets, and how the vanilla debug editor saves/loads (mod support?).
Section '4c tileGeometry.txt: Aufbau und Dach-Eintraege'
if ($pz) {
    $tg = [IO.Path]::Combine($pz, 'media', 'tileGeometry.txt')
    if (Test-Path -LiteralPath $tg) {
        $tgLines = @(Get-Content -LiteralPath $tg -ErrorAction SilentlyContinue)
        Line ('Datei: ' + $tg + ', ' + $tgLines.Count + ' Zeilen')
        Line '--- erste 40 Zeilen'
        $tgLines | Select-Object -First 40 | ForEach-Object { Line ('  ' + $_) }
        # first roof entry with surrounding block (up to 60 lines), plus how many roof mentions exist
        $roofIdx = @()
        for ($i = 0; $i -lt $tgLines.Count; $i++) { if ($tgLines[$i] -match '(?i)roofs_') { $roofIdx += $i } }
        Line ('--- Zeilen mit "roofs_": ' + $roofIdx.Count)
        if ($roofIdx.Count -gt 0) {
            $start = [Math]::Max(0, $roofIdx[0] - 5)
            $end = [Math]::Min($tgLines.Count - 1, $roofIdx[0] + 55)
            Line ('--- erster Dach-Eintrag (Zeilen {0}-{1})' -f ($start + 1), ($end + 1))
            for ($i = $start; $i -le $end; $i++) { Line ('  ' + $tgLines[$i]) }
            $names = @($roofIdx | ForEach-Object { if ($tgLines[$_] -match '(?i)(roofs_[a-z0-9_]+)') { $Matches[1] } } | Select-Object -Unique)
            Line ('--- verschiedene Dach-Tilesets/Sprites (erste 40 von ' + $names.Count + '): ' + (($names | Select-Object -First 40) -join ', '))
        }
        # Statistics: which roof tiles actually carry shapes (box/polygon/cylinder)?
        $stats = @{}; $tileset = ''; $tileName = ''; $tileStart = -1; $shapes = 0; $example = -1
        $finish = {
            if ($tileStart -ge 0 -and $tileset) {
                if (-not $stats.ContainsKey($tileset)) { $stats[$tileset] = @(0, 0, 0) }
                $st = $stats[$tileset]; $st[0]++
                if ($shapes -gt 0) { $st[1]++; $st[2] += $shapes
                    if ($example -lt 0 -and $tileset -like 'roofs_*') { $script:exampleStart = $tileStart; $example = $tileStart } }
            }
        }
        $script:exampleStart = -1
        for ($i = 0; $i -lt $tgLines.Count; $i++) {
            $t = ([string]$tgLines[$i]).Trim()
            if ($t -eq 'tileset') { . $finish; $tileStart = -1; $tileset = ''; continue }
            if ($t.StartsWith('name = ') -and $tileStart -lt 0) { $tileset = $t.Substring(7).TrimEnd(',').Trim(); continue }
            if ($t -eq 'tile') { . $finish; $tileStart = $i; $shapes = 0; continue }
            if ($t -eq 'box' -or $t -eq 'polygon' -or $t -eq 'cylinder') { $shapes++ }
        }
        . $finish
        $roofSets = @($stats.Keys | Where-Object { $_ -like 'roofs_*' } | Sort-Object)
        $roofTiles = 0; $roofShaped = 0
        foreach ($k in $roofSets) { $roofTiles += $stats[$k][0]; $roofShaped += $stats[$k][1] }
        $allTiles = 0; $allShaped = 0
        foreach ($k in $stats.Keys) { $allTiles += $stats[$k][0]; $allShaped += $stats[$k][1] }
        Line ('--- Statistik: alle Tiles {0}, davon mit Form {1}; Dach-Tilesets {2} mit {3} Tiles, davon mit Form {4}' -f $allTiles, $allShaped, $roofSets.Count, $roofTiles, $roofShaped)
        foreach ($k in $roofSets) { Line ('  {0}: {1} Tiles, {2} mit Form ({3} Formen)' -f $k, $stats[$k][0], $stats[$k][1], $stats[$k][2]) }
        if ($script:exampleStart -ge 0) {
            Line '--- Beispiel: erstes Dach-Tile MIT Form'
            $end = [Math]::Min($tgLines.Count - 1, $script:exampleStart + 45)
            for ($i = $script:exampleStart - 1; $i -le $end; $i++) { Line ('  ' + $tgLines[$i]) }
        }
    } else { Line 'tileGeometry.txt nicht gefunden.' }
    $editor = [IO.Path]::Combine($pz, 'media', 'lua', 'client', 'DebugUIs', 'TileGeometryEditor')
    if (Test-Path -LiteralPath $editor) {
        Line '--- TileGeometryEditor: Zeilen zu Laden/Speichern/Mods (max. 60)'
        $hits = @(Get-ChildItem -LiteralPath $editor -Filter '*.lua' -File | ForEach-Object {
            $f = $_.Name; $n = 0
            Get-Content -LiteralPath $_.FullName | ForEach-Object {
                $n++
                if ($_ -match '(?i)(modid|getmod|\bmods?\b|save|write|reload|load\w*\(|tilegeometry\.txt|filename|path)') { '{0}:{1}: {2}' -f $f, $n, $_.Trim() }
            }
        })
        $hits | Select-Object -First 60 | ForEach-Object { Line ('  ' + $_) }
        if ($hits.Count -gt 60) { Line ('  (' + ($hits.Count - 60) + ' weitere)') }
    }
}

# ---------------------------------------------------------------- depth assignments
# In 0.2.2 Viewpoint returned the shape of a DIFFERENT tile for some roof sprites
# (e.g. roofs_01_14 = roofs_01_4). Candidate source: the game's tile-to-tile
# depth assignment table. Read-only search for the affected names.
Section '4d tileDepthTextureAssignments.txt: Dach-Zuordnungen'
$assignNames = @('roofs_01_14', 'roofs_01_71', 'roofs_02_14', 'roofs_30_01_77', 'roofs_30_02_80', 'roofs_30_08_107',
    'roofs_accents_30_01_22', 'roofs_accents_01_4', 'roofs_01_118', 'roofs_02_118', 'roofs_01_11', 'roofs_01_12', 'roofs_01_69')
if ($pz) {
    $tda = [IO.Path]::Combine($pz, 'media', 'tileDepthTextureAssignments.txt')
    if (Test-Path -LiteralPath $tda -PathType Leaf) {
        $tdaLines = @(Get-Content -LiteralPath $tda -ErrorAction SilentlyContinue)
        $roofCount = @($tdaLines | Where-Object { $_ -like '*roofs_*' }).Count
        Line ("Datei: $tda, {0} Zeilen, davon mit roofs_: {1}" -f $tdaLines.Count, $roofCount)
        Line '--- erste 12 Zeilen (Aufbau)'
        $tdaLines | Select-Object -First 12 | ForEach-Object { Line ('  ' + $_) }
        foreach ($n in $assignNames) {
            $rx = '(?<![A-Za-z0-9_])' + [Regex]::Escape($n) + '(?![0-9])'
            $hits = @()
            for ($i = 0; $i -lt $tdaLines.Count; $i++) {
                if ($tdaLines[$i] -match $rx) { $hits += $i }
            }
            if ($hits.Count -eq 0) { Line ("--- $n" + ': nicht enthalten'); continue }
            Line ("--- $n" + ': ' + $hits.Count + ' Treffer')
            foreach ($h in ($hits | Select-Object -First 3)) {
                $from = [Math]::Max(0, $h - 2)
                $to = [Math]::Min($tdaLines.Count - 1, $h + 2)
                for ($j = $from; $j -le $to; $j++) { Line ('  ' + ($j + 1) + ': ' + $tdaLines[$j]) }
            }
        }
    } else { Line "Nicht vorhanden: $tda" }
}

# ---------------------------------------------------------------- this mod
Section '5 ViewpointGeometryFix (diese Mod)'
$mods = [IO.Path]::Combine($Zomboid, 'mods')
$expected = [IO.Path]::Combine($mods, $ModId, '42', 'mod.info')
$infos = New-Object System.Collections.Generic.List[string]
if (Test-Path -LiteralPath $mods) {
    Get-ChildItem -LiteralPath $mods -Recurse -Depth 6 -Filter 'mod.info' -File -ErrorAction SilentlyContinue | ForEach-Object {
        $mi = Read-ModInfo $_.FullName
        if ($mi -and (Clean-Id $mi['id']) -eq $ModId) { $infos.Add($_.FullName) }
    }
} else { Line "Ordner fehlt: $mods" }
foreach ($m in $workshop) {
    $mi = Read-ModInfo ([IO.Path]::Combine($m, 'mod.info'))
    if ($mi -and (Clean-Id $mi['id']) -eq $ModId) { $infos.Add(([IO.Path]::Combine($m, 'mod.info'))) }
}
if ($infos.Count -eq 0) {
    Line 'Nicht installiert.'
    Finding "Mod nicht gefunden. Erwartet: $expected"
}
foreach ($p in $infos) {
    $mi = Read-ModInfo $p
    $dir = Split-Path -Parent $p
    Line ("mod.info: $p (modversion " + $mi['modversion'] + ')')
    $jarRel = $mi['javaJarFile']
    if ($jarRel) {
        $jar = [IO.Path]::Combine($dir, $jarRel)
        if (Test-Path -LiteralPath $jar) { Line '    JAR: vorhanden' } else { Line "    JAR: FEHLT ($jar)" }
    }
    $lua = [IO.Path]::Combine($dir, 'media', 'lua', 'client', 'VPGeometryFix_Main.lua')
    if (Test-Path -LiteralPath $lua) { Line '    Lua: vorhanden' } else { Line "    Lua: FEHLT ($lua)"; Finding 'Lua-Datei der Mod fehlt - ZIP neu entpacken.' }
    $common = [IO.Path]::Combine((Split-Path -Parent $dir), 'common')
    if (Test-Path -LiteralPath $common) { Line '    common-Ordner: vorhanden' } else { Line '    common-Ordner: FEHLT' }
    $roofData = [IO.Path]::Combine($dir, 'media', 'tileGeometry.txt')
    if (Test-Path -LiteralPath $roofData -PathType Leaf) {
        $rd = @(Get-Content -LiteralPath $roofData -ErrorAction SilentlyContinue)
        $rdSets = @($rd | Where-Object { $_ -match '^\s*name\s*=\s*(roofs_[^,\s]+)' } | ForEach-Object { $Matches[1] })
        $rdTiles = @($rd | Where-Object { $_ -like '*(VPGeometryFix)*' }).Count
        Line ('    Dach-Fix A (tileGeometry.txt): installiert, ' + $rdTiles + ' Tiles in ' + $rdSets.Count + ' Tilesets, geaendert ' + (Get-Item -LiteralPath $roofData).LastWriteTime)
    } else { Line '    Dach-Fix A (tileGeometry.txt): nicht installiert' }
    if (($p -ne $expected) -and $p.StartsWith($mods)) { Finding "Mod liegt an falscher Stelle: $p - richtig waere $expected" }
}
if ($infos.Count -gt 1) { Finding "Mod mehrfach installiert - alle Kopien ausser $expected loeschen." }

# ---------------------------------------------------------------- own log
function Add-Block([string]$title, $items, [int]$max, [bool]$fromEnd, [int]$width = 400) {
    Line ''
    Line ("--- $title (" + @($items).Count + ')')
    $arr = @($items)
    if ($arr.Count -gt $max) {
        if ($fromEnd) { Line ('(' + ($arr.Count - $max) + ' aeltere ausgelassen)'); $arr = $arr[($arr.Count - $max)..($arr.Count - 1)] }
        else { $arr = $arr[0..($max - 1)] }
    }
    foreach ($s in $arr) {
        if ($s.Length -gt $width) { $s = $s.Substring(0, $width) + ' ...' }
        Line $s
    }
}
Section '5b VPGeometryFix.log (Dach-Fix, letzter Start)'
$ownDir = [IO.Path]::Combine($Zomboid, 'VPGeometryFix')
$cfg = [IO.Path]::Combine($ownDir, 'config.properties')
if (Test-Path -LiteralPath $cfg) {
    Line 'config.properties:'
    Get-Content -LiteralPath $cfg -ErrorAction SilentlyContinue | Where-Object { $_ -and -not $_.StartsWith('#') } | ForEach-Object { Line ('  ' + $_) }
} else { Line 'config.properties: keine (Standardwerte, Dach-Fix B an)' }
$ownLog = [IO.Path]::Combine($ownDir, 'VPGeometryFix.log')
$roofLines = @()
$script:ownLogLoaded = $false
if (-not (Test-Path -LiteralPath $ownLog)) {
    Line "Nicht vorhanden: $ownLog"
} else {
    $logAll = @(Get-Content -LiteralPath $ownLog -ErrorAction SilentlyContinue)
    $start = 0
    for ($i = $logAll.Count - 1; $i -ge 0; $i--) {
        if ($logAll[$i] -like '*Java component loaded*') { $start = $i; break }
    }
    $session = @()
    if ($logAll.Count -gt 0) { $session = @($logAll[$start..($logAll.Count - 1)]) }
    Line ("Datei: $ownLog, letzter Start ab Zeile {0}, geaendert {1}" -f ($start + 1), (Get-Item -LiteralPath $ownLog).LastWriteTime)
    $roofLines = @($session | Where-Object { $_ -match 'roof|Java component loaded' })
    $script:ownLogLoaded = @($session | Where-Object { $_ -like '*Java component loaded*' }).Count -gt 0
    Add-Block 'Dach-Zeilen' $roofLines 700 $false 800
    $stats = @($session | Where-Object { $_ -like '*roof fix B stats*' })
    $seenEmpty = @($session | Where-Object { $_ -like '*roof seen:*no shape*' })
    if ($stats.Count -eq 0) {
        Finding 'VPGeometryFix.log hat keine Dach-Statistik: der Patch auf TileMeshes.geometryFor wurde in diesem Start nie aufgerufen (oder die Mod ist aelter als 0.2.1). Mit Viewpoint in die Naehe von Haeusern gehen und den Bericht danach erneut erzeugen.'
    } elseif (@($session | Where-Object { $_ -like '*roof fix B: * <- *' }).Count -eq 0) {
        $ts = @($seenEmpty | ForEach-Object { if ($_ -match 'roof seen: (roofs_.+?)_\d+ ') { $Matches[1] } } | Sort-Object -Unique)
        Finding ('Dach-Fix B hat nichts ersetzt. Dach-Tilesets ohne Form, die Viewpoint wirklich angefragt hat: ' + ($ts -join ', ') + ' (Details Abschnitt 5b).')
    }
}

# ---------------------------------------------------------------- own reports
Section '5c Dach-Daten und Tile-Untersuchungen (neueste)'
$inspDir = [IO.Path]::Combine($Zomboid, 'VPGeometryFix', 'inspect')
if (-not (Test-Path -LiteralPath $inspDir)) {
    Line 'Noch keine Berichte (Fenster: "Dach-Daten" bzw. "Tile untersuchen").'
} else {
    $rdFile = Get-ChildItem -LiteralPath $inspDir -Filter 'roofdata_*.txt' -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($rdFile) {
        Line ('--- ' + $rdFile.Name + ' (' + $rdFile.LastWriteTime + ')')
        $rd = @(Get-Content -LiteralPath $rdFile.FullName -ErrorAction SilentlyContinue)
        $rdCap = 1200
        $rd | Select-Object -First $rdCap |
            ForEach-Object { $t = $_; if ($t.Length -gt 800) { $t = $t.Substring(0, 800) + ' ...' }; Line $t }
        if ($rd.Count -gt $rdCap) { Line ('(' + ($rd.Count - $rdCap) + ' weitere Zeilen ausgelassen, ganze Datei: ' + $rdFile.FullName + ')') }
    } else { Line 'Kein Dach-Daten-Bericht (Fenster: "Dach-Daten").' }
    $insp = @(Get-ChildItem -LiteralPath $inspDir -Filter 'inspect_*.txt' -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 10)
    Line ('--- Tile-Untersuchungen: ' + $insp.Count + ' (neueste zuerst, je Zusammenfassung und kompakte Formen)')
    foreach ($f in $insp) {
        Line ('  ' + $f.Name)
        $c = @(Get-Content -LiteralPath $f.FullName -ErrorAction SilentlyContinue)
        $inSummary = $false
        foreach ($l in $c) {
            if ($l -eq '--- summary') { $inSummary = $true; continue }
            if ($inSummary -or $l -like '*compact:*') {
                $t = $l.Trim(); if ($t.Length -gt 800) { $t = $t.Substring(0, 800) + ' ...' }
                Line ('    ' + $t)
            }
        }
    }
}

# ---------------------------------------------------------------- console.txt
Section '6 console.txt (letzter Spielstart)'
$console = [IO.Path]::Combine($Zomboid, 'console.txt')
if (-not (Test-Path -LiteralPath $console)) {
    Line "Nicht vorhanden: $console"
    Finding 'console.txt fehlt - Spiel einmal starten und bis ins Hauptmenue laufen lassen.'
} else {
    $all = @(Get-Content -LiteralPath $console -ErrorAction SilentlyContinue)
    Line ("Datei: $console, {0} Zeilen, geaendert {1}" -f $all.Count, (Get-Item -LiteralPath $console).LastWriteTime)
    $errorRx = '(?i)(exception|error|stack trace|attempted index|non-table|nil value)'
    $spamRx = '\[Viewpoint\] (slow frame|\d+ fps|video memory|floor pages)|\[ViewpointTurbo/'
    $ours = New-Object System.Collections.Generic.List[string]
    $zb = New-Object System.Collections.Generic.List[string]
    $vp = New-Object System.Collections.Generic.List[string]
    $javaMods = $false
    $addedLines = @{}
    for ($i = 0; $i -lt $all.Count; $i++) {
        $l = [string]$all[$i]
        $tag = '{0}: {1}' -f ($i + 1), $l
        if ($l -match 'VPGeometryFix|TileMeshes') {
            $ours.Add($tag)
            # error lines that directly follow one of ours (e.g. Lua stack traces), each only once
            for ($k = $i + 1; $k -lt [Math]::Min($all.Count, $i + 6); $k++) {
                $n = [string]$all[$k]
                if ($n -match $errorRx -and $n -notmatch 'VPGeometryFix' -and -not $addedLines.ContainsKey($k)) {
                    $addedLines[$k] = $true
                    $ours.Add(('{0}:   {1}' -f ($k + 1), $n))
                }
            }
            continue
        }
        if ($l -match '(?i)zombiebuddy|\[ZB') { $zb.Add($tag); continue }
        if ($l -match '\[Viewpoint') {
            $javaMods = $true
            if ($l -notmatch $spamRx) { $vp.Add($tag) }
        }
    }
    $text = $all -join "`n"
    $luaLoaded = $text.Contains('[VPGeometryFix] Lua loaded')
    $startBlock = $text.Contains('[VPGeometryFix] Loaded')
    # Lua fallback (no Java part) ends its startup block with "Debug mode: ... (Lua only)"
    $luaOnly = $text -match '\[VPGeometryFix\] Debug mode: \w+ \(Lua only\)'
    $javaLoaded = $startBlock -and -not $luaOnly
    $ourErrors = @($ours | Where-Object { $_ -match '\[VPGeometryFix\] ERROR' })
    $zbActive = ($zb.Count -gt 0) -or $javaMods
    function JaNein($b) { if ($b) { 'ja' } else { 'NEIN' } }
    Line ('Mod-Lua geladen: {0}, Startblock: {1}, Java-Teil der Mod: {2}, ZombieBuddy aktiv: {3}, Fehlerzeilen der Mod: {4}' -f `
        (JaNein $luaLoaded), (JaNein $startBlock), (JaNein $javaLoaded), (JaNein $zbActive), $ourErrors.Count)

    Add-Block 'Zeilen dieser Mod' $ours 120 $true
    Add-Block 'ZombieBuddy' $zb 60 $false
    Add-Block 'Viewpoint (ohne Leistungsmeldungen)' $vp 40 $false

    # console.txt without any startup line (no "[ZB]" loader lines, no "f:0>" frames) was cut/rotated while the
    # game ran; then our own log (section 5b) is the better evidence.
    $consoleCut = ($zb.Count -eq 0) -and -not ($text -match 'f:0>')
    if ($consoleCut) {
        Line 'Hinweis: console.txt beginnt mitten im Spiel (keine Startzeilen) - der Ladezustand steht in Abschnitt 5b (VPGeometryFix.log).'
    }
    if ($consoleCut -and $script:ownLogLoaded) {
        # loaded according to VPGeometryFix.log: no finding
    } elseif (-not $luaLoaded -and -not $startBlock) {
        Finding 'console.txt enthaelt keine Zeile dieser Mod: das Spiel hat sie NICHT geladen. Im Hauptmenue unter Mods aktivieren (B42: auch in der Mod-Auswahl des Spielstands) und Ordner pruefen (Abschnitt 5).'
    } elseif (-not $javaLoaded) {
        Finding 'Der Lua-Teil der Mod laeuft, der Java-Teil nicht. ZombieBuddy muss das JAR von ViewpointGeometryFix freigeben (Abfrage beim Spielstart, siehe Block ZombieBuddy).'
    }
    if ($ourErrors.Count -gt 0) { Finding ('Die Mod meldet ' + $ourErrors.Count + ' Fehler - siehe Block "Zeilen dieser Mod".') }
    if (-not $zbActive -and -not $consoleCut) { Finding 'Weder ZombieBuddy- noch Viewpoint-Java-Zeilen in console.txt: ZombieBuddy laeuft nicht. ZombieBuddy-Installer erneut ausfuehren.' }
}

Section 'ERGEBNIS'
if ($script:Findings.Count -eq 0) { Line 'Keine Installationsprobleme gefunden.' }
foreach ($f in $script:Findings) { Line "* $f" }

$script:Report | Set-Content -LiteralPath $Out -Encoding UTF8
Write-Host ''
Write-Host "Bericht geschrieben: $Out"
