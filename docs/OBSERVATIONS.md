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

## Präzisierung durch den Nutzer (2026-10-05)

Das Hauptproblem sind **Dächer allgemein**: oft fehlt **eine Dachhälfte**, und
**Dachteile/Dachkanten hängen in der Luft**. Stufen (D) sind nur ein Nebenaspekt.
Andere schwebende Objekte (Möbel, Wände, Pflanzen) wurden nicht genannt.

Arbeitshypothese [H, Zahlen V1]: Die steilen Dächer (`roofs_01`, 39,2°) sind genau
so geneigt, dass nach Nord/West abfallende Hälften in der Iso-Ansicht nur als Strich
erscheinen. Für diese Hälften hat das Spiel keine Form (in den Daten hat keine nach
hinten geneigte 39°-Fläche eine Form). Viewpoint bekommt dafür nichts (Hälfte fehlt,
z. B. `roofs_01_11/12/69`) oder die Form eines anderen Tiles mit falscher Neigung
(`roofs_01_14` = `roofs_01_4`, schwebende Fläche). Dazu kommen die Giebelleisten auf
senkrechten 2,25 × 3,45-Platten (Kanten in der Luft). Nächster Schritt laut Nutzer:
erst Daten mit 0.2.3-test („Dach-Daten“ am kaputten Haus), dann ggf. Freigabe für
einen schaltbaren Rückseiten-Fix.

## Zuordnung nach den Formwerten (0.2.2-test, siehe RESEARCH.md Update 4)

| # | wahrscheinlichste Ursache [H] | Sicherheit |
|---|---|---|
| A | falsch platzierte Form trägt die Sprite-Grafik (Stufenblock-Oberkante 0.21–0.25 über der Schräge, geliehene Form eines anderen Tiles) oder doppelte Darstellung durch Modellpakete | gering |
| B | Giebelleisten liegen auf senkrechten 2.25 × 3.45-Platten statt in der Dachschräge | mittel |
| C | Dach-Sprites ohne Form (`roofs_01_11/12/69`) bzw. nach Nord/West fallende 39°-Flächen ohne Iso-Grafik | mittel-gering |
| D | Stufenblöcke (`roofs_30_01_40…45` und davon geliehene Formen) | mittel |
| E | nicht durch Dachdaten erklärt; evtl. Leisten-Platte ohne Alpha-Schnitt oder Modellpaket | sehr gering |

## Was eine Inspektion jetzt zeigt (Mod 0.1.4-diag)

Jede TILE-Zeile enthält Viewpoints Quellgeometrie, z. B.
`TILE 101,200,1 Objects#0 IsoObject sprite=roofs_01_12 kind~ROOF spriteType=… vpGeom=2(Polygon,Box) rise=0.0`.
`vpGeom=0` bei einem Dach-Sprite ⇒ Kandidat für Fehlerbild C (fehlendes Dach).
Der Bericht enthält zusätzlich die Punkte/Maße jeder Form.

## Nächste Daten, die gebraucht werden

1. Viewpoint-Klassenliste mit Stichworten (Doctor Abschnitt 4) – um die Klasse
   zu finden, die aus Dach-Sprites Geometrie baut.
2. Pro Fehlerart ein Inspektionsbericht (Fenster → Ziel fixieren auf das Tile
   unter der fehlerhaften Stelle → Tile untersuchen). Wichtig sind Sprite-Name
   und `spriteType` (IsoObjectType, z. B. WestRoofB/M/T) der Objekte in z+1/z+2.
3. Gegenprobe: dieselbe Stelle ohne PZVoxelStudioViewpoint / Viewpoint2Dto3D,
   um Fehler der Modellpakete von Fehlern in Viewpoint selbst zu trennen.
