# Beobachtete Fehlerbilder (Nutzer-Screenshots, 2026-10-05)

Umgebung laut VPGF Doctor 0.2.1: PZ 42.21.0 (projectzomboid.jar = auditierter
Pin), Viewpoint 0.1.5a-hotfix, ZombieBuddy aktiv (Workshop 3619862853),
Viewpoint meldet „compat: all 61 patch targets and 61 private members found“.
Zusätzlich aktiv u. a.: PZVoxelStudioViewpoint (Modellpakete, ~10 000 Modelle für
Sprites), Viewpoint2Dto3D, ViewpointTurbo, ViewpointOpenContainers („tile
geometry“). Alle Ursachen unten sind **[H]** – Hypothesen, bis Inspektionsberichte
und Viewpoints Klassen sie bestätigen.

| # | Bild | Beobachtung | Fehlerart |
|---|---|---|---|
| A | Holzhaus grau, aus dem Auto | Eine Dachfläche schwebt **oberhalb** des eigentlichen Dachs, schräg versetzt | Dachfläche mit falscher Höhe/Ebene (z oder Höhen-Offset) |
| B | Holzhaus grau + gelb | **Giebelkanten** (helle Dachrand-Leisten) ragen als dünne „^“-Linien über Dach/Giebel hinaus, teils frei in der Luft | Dachkanten-Tiles mit falscher Neigung/Länge oder falschem Ankerpunkt |
| C | Gelbes Haus (vgl. isometrisches Bild) | Über dem vorderen Giebel/Vorbau fehlt die Dachfläche; nur die Kantenleisten sind da. Isometrisch ist das Dach vollständig | Dach-Tiles nicht gerendert (Culling oder fehlende Geometrie für diesen Sprite-Typ) |
| D | Motel-Arkaden | Vordach aus einzelnen Kacheln mit **Stufen/Sägezahn** und Lücken statt durchgehender Fläche | Pro-Tile-Neigungen passen an Tile-Grenzen nicht zusammen (Ecken/Übergangs-Sprites) |
| E | Gelbes Haus, linke Seite | Rechteckiges Wandstück steht vor der Fassade / Wandlücke | Wand-Overlay oder Wandteil mit Versatz |

## Nächste Daten, die gebraucht werden

1. Viewpoint-Klassenliste mit Stichworten (Doctor Abschnitt 4) – um die Klasse
   zu finden, die aus Dach-Sprites Geometrie baut.
2. Pro Fehlerart ein Inspektionsbericht (Fenster → Ziel fixieren auf das Tile
   unter der fehlerhaften Stelle → Tile untersuchen). Wichtig sind Sprite-Name
   und `spriteType` (IsoObjectType, z. B. WestRoofB/M/T) der Objekte in z+1/z+2.
3. Gegenprobe: dieselbe Stelle ohne PZVoxelStudioViewpoint / Viewpoint2Dto3D,
   um Fehler der Modellpakete von Fehlern in Viewpoint selbst zu trennen.
