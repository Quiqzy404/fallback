# ZERO Client (Fabric 1.21.11)

Client-Mod mit Glas-HTML-GUI (Right Shift). Komplett auf Englisch.
Module: **Combat** (Stun Slam, Instant Launch, Anchor, Auto Ely Mace), **Player** (Fast XP, Auto Tool, Auto Eat, Auto Totem, Durability Warning),
**Visuals** (Ambience, Fullbright, Hand Shader, Entity ESP, Tracers, Zoom, No Overlay), **World** (Sus Chunk, Block ESP, Waypoints),
**Movement** (Elytra Recast), **Misc** (HUD, Keystrokes, Sounds).

## Bauen (Windows)

1. **Java 21** installieren (z. B. Temurin 21). Testen mit: `java -version`
2. Ordner entpacken und **`BAUEN.bat`** doppelklicken.
3. Die fertige Mod liegt danach im Ordner **`FERTIG`** (öffnet sich automatisch).
4. Diese JAR in den `mods`-Ordner legen. Dazu brauchst du **Fabric Loader 0.20.1+** und **Fabric API** (für 1.21.11).

⚠️ Niemals die JAR aus `build\devlibs\` (`…-dev.jar`) nehmen – die crasht im Spiel mit
`ClassNotFoundException: net.minecraft.client.gui.screens.Screen`.

Testen ohne Launcher: `gradlew.bat runClient`

## HTML-GUI (optional, über Rinku)

Das GUI ist eine HTML-Datei: `src/main/resources/assets/quiqzy/html/gui.html` (HTML/CSS/JS, frei änderbar).
Design: Nachbau von „GlassCraft UI“ (Base44) – Glas-Fenster, Kategorien, Suche, Modul-Karten, Keybinds.

- Linksklick auf ein Modul = an/aus, Rechtsklick = Einstellungen
- Keybind-Knopf anklicken → Taste drücken (Esc = abbrechen, Entf = Keybind löschen). Die Taste schaltet das Modul im Spiel an/aus.
Die Mod **Rinku** (Chromium im Spiel, von Keksuccino – Nachfolger von MCEF) ist **in die Quiqzy-JAR eingebaut**
und wird von Fabric automatisch mitgeladen. Du musst sie NICHT extra in den `mods`-Ordner legen
(falls doch eine drin liegt, nimmt Fabric einfach die neuere Version).
Beim ersten Start lädt Rinku Chromium (~200 MB) herunter – danach Spiel einmal neu starten.

Rinku ist unter der GNU LGPL 2.1 lizenziert (https://github.com/Keksuccino/mcef) und wird unverändert mitgeliefert.

Ohne Rinku – oder solange Rinku noch lädt – öffnet Right Shift automatisch das eingebaute GUI. Nichts crasht.

## Benutzung

- **Right Shift** öffnet/schließt das GUI (Taste änderbar unter Optionen → Steuerung → Quiqzy Client).
- Kleines rundes Fenster über dem Spiel – oben anfassen zum Verschieben.
- **Linksklick** auf ein Modul = an/aus, **Rechtsklick** = Einstellungen auf-/zuklappen.
- Schrift: Nunito (SIL Open Font License, siehe `assets/quiqzy/font/OFL.txt`).

### Stun Slam (Combat)

Nachbau der Mod „Mace Swap“ (maceswap-1.0.0.jar). Wenn du auf ein Lebewesen linksklickst:
- **blockt es mit Schild:** Axt aus der Hotbar nehmen und schlagen (bricht den Schild), nach der Verzögerung auf den Mace wechseln und nochmal schlagen
- **blockt es nicht:** direkt auf den Mace wechseln und schlagen

Die **Shield-Break-Items** wählst du frei (mehrere möglich): Rechtsklick/Linksklick auf Stun Slam → **+ Add item**
→ Menü mit allen Items aus Minecraft, oben suchen und anhaken. **Modus** (Dropdown):
- **Mace Combo:** Schild brechen + Mace (wie oben).
- **Shield Drain:** solange du eine schildende Person anschlägst und Angriff hältst, schlägt es mit vielen Hits pro Tick aufs Schild (einstellbar über **Drain Hits / Tick**, 10 ≈ 200 CPS), um es schnell zu zerstören. Mit **Hold to Drain** stellst du ein, ob du Angriff gedrückt halten musst (an) oder ob es automatisch beim Anvisieren draint (aus).

**Any Item:** ist dieser Schalter an, löst der Combo mit **jedem** Item in der Hand aus (die Item-Liste wird ignoriert).
Zum Schildbrechen wechselt das Modul **automatisch** auf ein Shield-Break-Item aus der Hotbar (eins der gewählten,
sonst irgendeine Axt – nur eine Axt bricht in Minecraft den Schild), danach kommt der Mace. Du kannst also mit
**jedem** Item zuschlagen, Hauptsache eine Axt (oder ein gewähltes Item) liegt irgendwo in der Hotbar.

**Wichtig:** Ist **Any Item** aus, löst der Combo nur aus, wenn du **eins der gewählten Items in der Hand hältst** und damit zuschlägst.
Hältst du etwas anderes (z. B. ein Totem), passiert nichts – dein Schlag geht ganz normal durch.
**Ist die Liste leer** (nichts ausgewählt), löst **jedes** Item in der Hand den Combo aus.
Standardmäßig sind alle Äxte gewählt. Der Mace muss in der Hotbar (Slot 1–9) liegen.

| Einstellung        | Standard        | Bereich               |
|--------------------|-----------------|-----------------------|
| Mode               | Mace Combo      | Mace Combo / Shield Drain |
| Any Item           | aus             | an/aus                |
| Shield Break Items | alle Äxte       | frei wählbar          |
| Drain Hits / Tick  | 10              | 1–20                  |
| Hold to Drain      | an              | an/aus                |
| Min. Verzögerung   | 1 Tick          | 0–40 Ticks   |
| Max. Verzögerung   | 1 Tick          | 0–40 Ticks   |

| Bind Mode          | Toggle          | Toggle / Hold (Modul nur an, solange Taste gehalten) |
| Horizontal Target Dist | 6.00        | 0–6 Blöcke (nur Ziele so nah, horizontal) |
| Min Fall Distance  | 0.00            | 0–50 Blöcke (erst ab dieser Fallhöhe Mace) |
| Stun Slam          | an              | aus = nur Mace-Swap, keine Axt |
| Stun Slam Mode     | Chance          | Chance / Hold Key (nur solange Keybind gehalten) |
| Chance             | 100 %           | 0–100 % pro Stun Slam |
| Scale With Fall    | aus             | Chance wächst mit Fallhöhe (voll ab +10 Blöcken) |
| Stun Min Fall      | 0.00            | 0–50 Blöcke (0 = Min Fall Distance) |
| Breach Check       | aus             | bevorzugt einen Mace mit Breach |

Zwischen Min und Max wird zufällig gewählt (wie `/maceswap delay <min> <max>` im Original).

### Instant Launch (Combat)

**Attribut-Swap mit Linksklick:** Auf Tastendruck wechselt das Modul sofort auf das **Spear-Item** und
**schlägt damit das Ziel im Fadenkreuz** (Linksklick). Durch den Swap gilt sofort der volle Attack-Wert
des Spears. Danach zurück auf den vorherigen Slot.

- **Launch Item:** wähle das Spear-Item (per „+ Add item", mehrere möglich – das erste in der Hotbar wird genommen).
- **Swap Back:** nach dem Schlag zurück zum vorherigen Slot.
- **Taste:** im GUI neben „Instant Launch" auf das Keybind-Feld klicken und eine Taste drücken. Diese Taste
  löst den Launch aus (Modul muss an sein). Über der Hotbar erscheint **„⚡ Instant Launch"** bzw. eine rote
  Warnung, wenn kein Spear in der Hotbar liegt.

Du musst beim Drücken **auf den Gegner zielen**, damit der Linksklick trifft.

### Anchor (Combat)

Respawn Anchor hinstellen und **rechtsklicken** → das Modul nimmt **Glowstone** aus der Hotbar und lädt den Anchor auf,
wechselt dann aufs **Totem** und klickt nochmal → der Anchor explodiert.
Ist der Anchor schon geladen, wird direkt gesprengt. Glowstone muss in der Hotbar liegen. Ohne Totem in der Hotbar
nimmt es einen leeren Slot oder irgendein anderes Item (alles außer Glowstone sprengt den Anchor).

| Einstellung  | Standard | Bereich    |
|--------------|----------|------------|
| Verzögerung  | 1 Tick   | 0–10 Ticks |
| Gedrückt halten | an    |            |
| Wiederholen alle | 4 Ticks | 1–20 Ticks |

**Gedrückt halten:** Rechtsklick auf dem Anchor halten → Aufladen + Sprengen wiederholt sich automatisch,
solange ein Anchor im Blick ist. Schaust du weg, bekommt Minecraft den Rechtsklick wieder (z. B. zum neuen Anchor setzen).

Hinweis: Im Nether explodiert ein Anchor nicht (Minecraft-Regel).

### Auto Ely Mace (Combat)

Am besten mit einem **Mace mit Wind Burst**.

1. Modul an → **fliegt einmal allein hoch**: springt ab, öffnet die Elytra, mit Raketen auf **Start Height**
   (abschaltbar mit **Fly Up On Start**)
   → danach **fliegst du selbst** (kein Lenken, keine Raketen)
2. kommt ein Spieler in **Range** → es übernimmt: zielt und fliegt **mit Raketen** runter (**Dive Rockets**)
3. am Ziel (**Elytra Swap**):
   - Tick 0: Elytra aus + Waffe in die Hand
   - **Schild oben:** Tick 1 = Axt-Hit (Schild bricht), **Swap Delay** (1 Tick) später = Mace-Hit
   - **kein Schild:** Tick 1 = Mace-Hit
   - **Re-equip Delay** (Standard 4 Ticks) nach dem letzten Hit: Elytra wieder an
4. **Wind Burst** schleudert dich hoch → am höchsten Punkt geht die Elytra wieder auf → **sofort wieder runter mit Raketen**
   (kommt nach **Wind Burst Wait** Ticks kein Schub: die Elytra geht auf, sobald du fällst)
5. Ziel tot/weg → du hast wieder die Kontrolle

Stehst du auf dem Boden, springt es ab und öffnet die Elytra selbst.
**Predict Movement:** zielt jedes Bild neu und hält vor – dahin, wo der Gegner sein wird, wenn du ankommst.
Braucht: Elytra an, **Mace** + **Raketen** in der Hotbar (+ **Axt** für Schilde, **Brustplatte oder leerer Slot** für den Swap).

| Einstellung        | Standard | Bereich     |
|--------------------|----------|-------------|
| Range              | 48 m     | 8–128 m     |
| Fly Up On Start    | an       |             |
| Start Height       | 25 m     | 5–80 m      |
| Dive Rockets       | an       |             |
| Dive Rocket Delay  | 10 Ticks | 2–40 Ticks  |
| Break Shield       | an       |             |
| Swap Delay         | 1 Tick   | 1–5 Ticks   |
| Elytra Swap        | an       |             |
| Re-equip Delay     | 4 Ticks  | 1–40 Ticks  |
| Wind Burst Wait    | 10 Ticks | 1–40 Ticks  |
| Predict Movement   | an       |             |
| Smooth Aim         | an       |             |
| Aim Speed          | 10       | 1–30        |
| Humanize           | an       |             |

### Fast XP (Player)

XP-Flaschen in die Hand (Main- oder Offhand) und **Rechtsklick gedrückt halten** → wirft jeden Tick Flaschen
(Vanilla nur alle 4 Ticks).

| Einstellung       | Standard | Bereich |
|-------------------|----------|---------|
| Bottles per Tick  | 1        | 1–5     |
| Look Down         | an       |         |
| Look Speed        | 12       | 1–30    |

**Look Down:** Beim Werfen dreht sich die Kamera weich, aber schnell nach unten (pro Bild, nicht pro Tick → kein Ruckeln).

### Visuals

- **Ambience** – eigene Tageszeit (Sunrise/Day/Noon/Sunset/Night/Midnight) und „Clear Weather“. Nur bei dir sichtbar.
- **Fullbright** – alles hell: Gamma-Trick (Helligkeit 1500 %, wird beim Ausschalten zurückgesetzt) + Nachtsicht-Effekt nur bei dir.
- **Hand Shader** – „Glint“ (Verzauberungs-Glanz auf allem in der Hand, passt zu Glint-Texturepacks),
  „Size“ (50–150 %) und „X/Y/Z Offset“ (Hände verschieben).

Ambience und Hand Shader klinken sich per Mixin in Minecraft ein (Signaturen aus Iris 1.21.11). Falls eine Methode
in deiner Version anders heißt, macht das Modul einfach nichts – es crasht nicht (`require = 0`).

### Configs

In der Seitenleiste unten gibt es **Configs**:
- **Save current config:** Namen eingeben → Save, speichert alle Module + Einstellungen als Profil (in `config/quiqzy-configs/`).
- Gespeicherte Configs mit **Load** laden oder **Delete** löschen.
- **Reset everything to default:** setzt alle Module, Einstellungen und Keybinds zurück (zweimal klicken zum Bestätigen).

### Block ESP

Markiert frei gewählte Blöcke in den Chunks um dich herum mit farbigen Boxen.
**Blöcke wählen:** Rechtsklick auf Block ESP → **+ Add block** → Menü mit allen Blöcken aus Minecraft,
oben suchen (z. B. „diamond“) und anklicken (Haken = an). Mit dem **×** am Block wieder entfernen.
Die Auswahl wird in `config/quiqzy.json` gespeichert. Geht nur im HTML-GUI (Rinku).
**Aussehen:** Color Mode (Per Block / Single Color / Rainbow), Single Color, Fill Opacity, Outline Opacity, Outline Width.
**Preset:** fertige Listen (Ores, Rare Ores, Storage, Base Finder, Spawners, Clear) – ersetzt die Auswahl.
Weitere Einstellungen: Radius (in Chunks), Through Blocks (durch Wände sichtbar) und Outline.
Scannt jetzt deutlich schneller (ca. 3× pro Tick) und alle ~1 Sekunde neu, maximal 2500 Boxen gleichzeitig.

### Entity ESP / Tracers

Farben wählst du im GUI über das Farbfeld: Palette, Farbton-Regler oder Hex-Code (z. B. `#FF5A6E`).

- **Entity ESP:** Boxen um Spieler, Monster, Tiere/Rest und Items.
  Einstellbar: welche Gruppen, Range, Through Blocks, **Style** (Fill + Outline / Outline / Fill / Corners),
  **eigene Farbe pro Gruppe**, Fill Opacity, Outline Opacity, Outline Width, Box Padding (Box optisch größer),
  Rainbow + Rainbow Speed.
- **Tracers:** Linien zu denselben Gruppen, optional zu den Block-ESP-Treffern.
  Einstellbar: **Start From** (Crosshair / Feet / Bottom of Screen), **Aim At** (Body / Head / Feet),
  **Color Mode** (Per Type / Distance = nah rot, weit grün / Rainbow / Single Color), Farben pro Gruppe,
  Block Line Color, Line Width, Opacity, Rainbow Speed.

### Zoom

Modul an, dann **C halten** = reinzoomen (Taste in Optionen → Steuerung → ZERO Client änderbar).
Einstellbar: Zoom FOV, Smooth Camera.

### No Overlay

- **No Hurtcam:** setzt die Minecraft-Option „Damage Tilt“ auf 0 % (wird beim Ausschalten zurückgesetzt).
- **No Fire Overlay:** kein Feuer vor der Kamera (nur optisch, Schaden bekommst du trotzdem).

### Player: Auto Tool, Auto Eat, Auto Totem, Durability Warning

- **Auto Tool:** beim Abbauen das schnellste Werkzeug aus der Hotbar, danach zurück. Anti Break lässt fast kaputte Tools liegen.
- **Auto Eat:** isst unter der eingestellten Hungergrenze Essen aus der Hotbar (kein Rotten Flesh usw., Golden Apples nur wenn an).
  Startet nicht, während du Maustasten drückst.
- **Auto Totem:** legt ein Totem aus dem Inventar in die Offhand (Delay in Ticks, Replace Other Items).
- **Durability Warning:** Hinweis + Ton, wenn Rüstung oder Item in der Hand unter X % Haltbarkeit ist.

### HUD / Keystrokes (Misc)

Das HUD wird im **gleichen Glas-Style wie das GUI** gezeichnet (über Rinku, `hud.html`).
Ohne Rinku gibt es ein einfacheres Java-HUD als Rückfall.

- **HUD:** oben rechts Koordinaten, Nether-/Overworld-Koordinaten, Richtung, FPS, Ping, Totems; Rüstung über der Hotbar
  (die Rüstung bleibt als Item-Icons, das kann HTML nicht zeichnen).
- **Keystrokes:** W A S D, Maustasten und Leertaste unten links.
- Sus Chunk und Waypoints erscheinen als eigene Panels im selben Style.

### Sounds (Misc, standardmäßig an)

Klänge nur für dich (Minecraft-eigene Sounds, keine Extra-Dateien):
Modul an (hoher Ton) / aus (tiefer Ton), GUI auf/zu, Klicks bei Einstellungen und Tabs,
Block/Waypoint hinzugefügt bzw. entfernt. Einstellbar: Style (Pling, Chime, Click, Bell), Volume und welche Klänge an sind.
Lautstärke hängt zusätzlich am Minecraft-Regler „Interface“.

### Waypoints

Rechtsklick auf Waypoints → Name eingeben (optional) → **Add here** speichert deine Position.
Anzeige im HUD (Richtungspfeil + Entfernung) und als Säule in der Welt, nur in der passenden Dimension.
Mit **×** löschen. Optional Taste „Add Waypoint“ in den Steuerungen belegen.

### Sus Chunk

Scannt die geladenen Chunks um dich herum nach **ausgewachsenen Amethyst-Clustern**
(`minecraft:amethyst_cluster`, keine Buds). Wenn ein Chunk mindestens *Min. Cluster* davon hat,
wird er markiert:

- leuchtender Kasten um den ganzen Chunk (auf Höhe der Cluster)
- dünner Strahl aus der Mitte nach oben mit schwebendem Würfel
- **durch Blöcke sichtbar** (Einstellung „Durch Blöcke“)
- optional Boxen um jeden Cluster
- HUD oben links: Anzahl, die 3 nächsten Chunks mit Pfeil, Entfernung, Clusterzahl und Koordinaten

| Einstellung    | Standard | Bereich |
|----------------|----------|---------|
| Min. Cluster   | 4        | 1–30    |
| Radius (Chunks)| 8        | 2–32    |
| Chunk-Markierung | an     |         |
| Durch Blöcke   | an       |         |
| Cluster-Boxen  | an       |         |
| HUD-Anzeige    | an       |         |

Einstellungen werden in `config/quiqzy.json` gespeichert.

## Hinweise

- Auf Servern mit Anti-Xray können Cluster für den Client versteckt sein, dann findet der Scan nichts.

### Elytra Recast (Movement)

Kern-Logik aus dem Original-Mod **Elytra Recast** von InLieuOfLuna (MIT-Lizenz, Copyright (c) 2021 Luna,
https://github.com/InLieuOfLuna/elytra-recast), Extras wie in CatLean / ThunderHack Recode.

- **Recast beim Landen:** in dem Moment, in dem das Gleiten aufhört, wird die Elytra sofort neu geöffnet – auch am Boden
- **Jump Cooldown** (Standard 2 Ticks, wie im Original) – du springst direkt wieder ab
- **Auto Jump / Auto Walk** – hält Springen und Vorwärts für dich gedrückt
- **Change Pitch / Pitch (55°)** – für Flugphysik und Server schaust du auf 55°, die Kamera bleibt normal
- **Exploit** – None / Strict (Yaw wackelt) / Strong (Pitch wackelt)
- **Allow Broken** – auch mit (fast) kaputter Elytra
