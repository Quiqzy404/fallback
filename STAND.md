# ZERO Client – aktueller Stand

**Zuletzt aktualisiert:** 2026-11-10

## Ziel
Eigene Fabric-Mod "ZERO Client" für Minecraft 1.21.11 (Package `dev.quiqzy.client`).
GUI mit eigener Render-Engine (Right Shift), Module in Combat / Player / Visuals / World / Movement / Misc.
Neue Features werden oft nach Video-Vorlagen (Screen-Recordings) nachgebaut.

## Wo liegt was
- **Aktueller Quellcode:** `C:\Users\jonas\Downloads\zero-client_56` (DIESER Ordner)
- **Fertige JAR:** `FERTIG\zero-client-1.0.0.jar` (in diesem Ordner)
- **Screen-Recordings (Vorlagen):** `C:\Users\jonas\AppData\Local\Packages\Microsoft.ScreenSketch_8wekyb3d8bbwe\TempState\Recordings\`
  - Frames ausziehen mit ffmpeg aus: `C:\Users\jonas\AppData\Local\Programs\Python\Python311\Lib\site-packages\imageio_ffmpeg\binaries\ffmpeg-win-x86_64-v7.1.exe`
- **Minecraft mods-Ordner:** `C:\Users\jonas\AppData\Roaming\.minecraft\mods`
- Alte Ordner `zero-client_1` … `_50` und alle Zips = VERALTET, nicht als Basis nehmen.

## Bauen
- Braucht **Java 25**: `JAVA_HOME=C:\Program Files\Microsoft\jdk-25.0.3.9-hotspot`
- `BAUEN.bat` doppelklicken (setzt Java 25 selbst) oder: `gradlew build`
- Ergebnis: `build\libs\zero-client-1.0.0.jar` → nach `FERTIG\` kopieren. Nie die `-dev.jar` nehmen.

## Wichtig zum Code
- Render-Engine: `src/main/java/dev/quiqzy/client/gui/engine/` + `src/main/resources/assets/quiqzy/shaders/`
- Haupt-GUI: `gui/ZeroGui.java` (kann Settings: Bool, Int, Choice, Color, Blocks, Items, Waypoints – **keine Kommazahlen**)
- Settings-Typen: `module/Setting.java`, Module: `module/ModuleManager.java`
- GUI/Engine/Config/HtmlGui sind dekompiliert (keine Kommentare) – funktioniert normal.

## Erledigt
- 2026-11-07: Stun Slam bekam Settings wie im Prestige-Video: Bind Mode (Toggle/Hold),
  Horizontal Target Dist, Min Fall Distance, Stun Slam an/aus, Stun Slam Mode (Chance/Hold Key),
  Chance, Scale With Fall, Stun Min Fall, Breach Check (bevorzugt Breach-Mace).
- 2026-11-07: Quellcode mit Render-Engine aus der JAR wiederhergestellt → zero-client_42.

- 2026-11-07 (_43): Grid-Ansicht: geöffnete Kachel wächst jetzt weich auf volle Breite (Karte bleibt erhalten),
  andere Kacheln gleiten mit. Orange Puls-Glow (wie beim Keybind setzen) zusätzlich auf: Keybind-Chip (Hover),
  Slider-Knopf (Hover/Ziehen), gewählte Choice-Option, Dropdown-Choice (Hover), Hex-Farbfeld (beim Tippen).

- 2026-11-07 (_43, Teil 2):
  - Sounds: eigener Java-Synth (`util/UiSounds.java`, javax.sound) statt HTML-HUD-Browser – vorher war oft nichts zu hören.
  - HUD komplett neu im Engine-Stil: `hud/ZeroHud.java` (Info, Waypoints, Sus Chunk, Keystrokes, Rüstung,
    Waypoint-Marker im Bild). Alte `QuiqzyHud`/`InfoHud`/HtmlHud werden nicht mehr gezeichnet.
  - Block ESP: Preset-Chips (gewähltes orange mit Verlauf + Puls), Picker-Reihenfolge bleibt beim Klicken stehen,
    Rechtsklick auf Block im Picker = eigene Farbe (Palette + Farbverlauf + Reset, gespeichert als `block_colors`),
    sofortige Updates bei Blockänderungen über `mixin/ClientLevelMixin` (sendBlockUpdated), Rescan alle 0,5 s.
  - Sus Chunk: Anzeichen Amethyst / Rotated Deepslate / Grown Kelp (je an/aus + Grenzwert), orange Darstellung,
    Sound bei neuem Fund, Grund im HUD (z. B. "6 amethyst · 3 deepslate").
  - Waypoints: schönere Zeilen im GUI (Farbpunkt, Entfernung), Marker im Bild, HUD mit Richtungspfeilen.
  - Neuer Setting-Typ `Setting.ColorMap` (unsichtbar im GUI, Config speichert als {id: "#RRGGBB"}).

- 2026-11-07 (_44): Block-Farbe per Rechtsklick ist jetzt ein ganzes Fenster: großes Farbfeld (Sättigung/Helligkeit,
  ziehbar), Hue-Balken (ziehbar), Vorschau + Hex, Schnellfarben, Reset, orange "Done"-Knopf. Speichert beim Loslassen.
  Code: `drawColorPopover` / `updateColorDrag` in `gui/ZeroGui.java`.

- 2026-11-07 (_44): Neues Modul **Avatar** (Visuals): verwandelt dich in die eigene Figur "Rook"
  (Hoodie, Cargo-Hose, Cap rückwärts, Sonnenbrille, Kette). Outfits Orange/Midnight/Ice, Hauttöne Light/Medium/Dark,
  "Apply to Others". Umsetzung: Mixin auf `AbstractClientPlayer.getSkin` (`mixin/AbstractClientPlayerMixin`),
  Texturen `assets/quiqzy/textures/avatar/*.png` erzeugt mit `tools/make_avatar.py` (Design dort anpassen + neu ausführen).
  Hinweis: Keine Nachbauten geschützter Figuren (z. B. aus GTA) – nur eigene Designs.

- 2026-11-07 (_45): Neues Modul **Hit Effects** (Visuals), inspiriert von Treffer-Effekt-Videos, eigener Look in Client-Farben:
  Ripple-Ring, Spark Splash, Crit Stars (nur echte Crits), Kill Beam (Lichtsäule wenn Ziel stirbt), Screen Glow (Rand-Leuchten),
  Hit Sounds (eigener Synth: hit/crit/kill). Farben Ember/Gold/Sunset, Intensity 30–200 %.
  Code: `module/visuals/HitEffectsModule`, Rendering `hud/HitFx` (Welt-Positionen -> Bildschirm projiziert, ZeroRender),
  Mixin `MultiPlayerGameModeMixin` (attack HEAD).

- 2026-11-07 (_45, Teil 2), alles eigene Designs in Client-Farben (Videos nur Inspiration):
  - **Weapon Skins** (Visuals): Schwerter (optional Äxte) als eigene 3D-Modelle Katana/Greatsword/Scythe/Karambit/Butterfly,
    orange Teile leuchten. Tricks (Dreh/Flip) beim Waffenwechsel + Taste R ("Weapon Trick"). Modelle: tools/make_weapons.py.
    Umsetzung: ITEM_MODEL-Komponente auf Kopie des Stacks (ItemInHandRendererMixin), Trick = PoseStack-Drehung in renderItem.
  - **Embers** (Visuals): schwebende Funken um dich herum (HitFx EMBER).
  - **Low Fire** (Visuals): Feuer-Overlay tiefer (ScreenEffectRendererMixin.renderFire).
  - **Target HUD** (Misc): Panel neben dem Gegner: Kopf, Name, HP, Waffe, Entfernung, HP-Balken mit Schadens-Nachzieher,
    Rüstung, Combo, Treffer gegeben-kassiert, ahead/behind, Schild-Cooldown (5 s nach Axt-Schildbruch), Real Position Ghost.
  - **Hit Effects** erweitert: Hit Color (Treffer-Aufblinken in Client-Farbe statt rot, OverlayTextureMixin),
    Shield Breaker (Splitter beim Schildbruch), Afterglow (Getöteter löst sich in Licht auf).
  - Nicht gemacht: animierter Energie-Himmel aus dem Video.

## Selbsttest (Claude kann das Spiel selbst starten und prüfen)
- Befehl (Git Bash, im Projektordner): JAVA_HOME auf Java 25, dann
  ZERO_SELFTEST=t ./gradlew runClient   (alles)
  ZERO_SELFTEST=t ZERO_SELFTEST_ONLY=ingame ./gradlew runClient   (nur im Spiel: HUD, Waffen, Treffer, Kill, Feuer)
  ZERO_SELFTEST=t ZERO_SELFTEST_ONLY=weapons ./gradlew runClient  (nur Waffenmodelle)
- Screenshots: run/screenshots/t_*.png, Log im Gradle-Output ([Selbsttest]-Zeilen). Spiel schließt sich selbst.
- Szenarien: gui/engine/ZeroGuiScenario.java (GUI) und gui/engine/InGameScenario.java (Spiel). Label "name@N" = N Ticks bis Screenshot.
- Während des Tests nicht ins Fenster klicken (verfälscht Treffer/Hand).
- 2026-11-07: Selbsttest bestanden – kein Crash, alle Mixins greifen, Effekte/Target HUD/Waffen/Low Fire/Farbfenster sichtbar.
  Waffen-Display wurde danach auf ~55 % verkleinert (vorher viel zu groß).

- 2026-11-07 (_45, Teil 3): Target HUD wackelt nicht mehr: Standard-Position fest rechts neben dem Fadenkreuz
  ("Next to Crosshair"), außerdem "Below Crosshair" und ruhiges "Follow Target" (Totzone 14 px).
  Hit Effects: normaler Hit dezent (kleiner Ring, 5 Funken, kaum Glow), Crit heftig (3 Schockwellen, 30 Funken,
  Lichtstreifen, Sterne, aufspringendes "CRIT", Bildschirm-Blitz). Per Selbsttest geprüft.
  Hinweis: Im Dev-Selbsttest kann Rinku (Chromium) mit INITIALIZATION_FAILED loggen – harmlos, nicht unser Code.

- 2026-11-07 (_45, Teil 4) – Liste vom Nutzer abgearbeitet + eigene Ideen (alles per Selbsttest geprüft):
  - Hit Effects ruhiger: kein großer Ring mehr, weicher Leuchtpunkt; normal 4 Funken, Crit 10 Funken + 5 Sterne.
  - HUD: Watermark, Module List (ArrayList, links/rechts), Potion HUD (Icons, Restzeit-Balken), Armor HUD (Haltbarkeit %),
    Item Counter (Totems/Perlen/Kristalle/Äpfel/Obsi/XP), Session Stats (Spielzeit/Kills/Tode/KD/Blöcke), CPS in Keystrokes.
  - Visuals: No Hurt Cam, Custom FOV, No Fog (FogRendererMixin, @ModifyArgs updateBuffer), Nametags (Name/HP/Distanz/Gear,
    Items am Boden; Vanilla-Namen aus), 2D ESP (Ecken/Box + Health Bar), Crosshair (Kreuz/Punkt/Kreis, weitet sich bei Treffer,
    Cooldown-Balken), Animations (Swing Speed, Low Shield, Low Offhand, Item Size).
  - Player: Toggle Sprint, Auto Respawn. World: Death Waypoint. Misc: Low HP Warning (roter Rand), Start Menu (ZERO-Logo im Hauptmenü).
  - Rand-Leuchten (Low HP / Screen Glow) jetzt aus gestapelten Streifen (HitFx.edges) – Verläufe/Glows am Rand waren unsichtbar.
  - Bewusst NICHT gebaut (unfairer Vorteil gg. andere Spieler / irreführend): Combat Aura, Triggerbot, AutoClicker, Reach,
    Hitboxes, Velocity, Criticals, Auto Crystals, NoFall, Scaffold, FastPlace, Fake Stats/Leaderboard.

- 2026-11-07 (_45, Teil 5) – Video "Setup" (eigene Umsetzung) + Freecam:
  - Accent Colour (Misc): ganzer Client in anderer Akzentfarbe – util/Theme.tint() dreht Orange-Töne, eingehängt in
    ZeroRender.shape() und ZeroRender.layout() (Text). Presets + Custom.
  - Status Bar (RAM/Uhrzeit/Server/FPS/Ping), Radar (Kompass + Spielerköpfe), Creamy Key Sounds (Keystrokes),
    Embers: Style Embers/Petals/Both + Stardust Trail.
  - Freecam (Visuals): NUR Kamera fliegt (WASD/Space/Shift), Spieler bleibt und handelt von sich aus (Gegenteil Meteor),
    Mausrad = Tempo. Mixins: FreecamMixins$CameraMixin/TurnMixin/ScrollMixin, Hand ausgeblendet.
  - Low Fire weiter abgesenkt. Selbsttest-Modi: ZERO_SELFTEST_ONLY=freecam / petals.
  - Arbeitsordner bleibt zero-client_45 (Platte war voll; alte Versionen _1.._44 gelöscht).
  - Nicht gemacht: animierter Energie-Himmel, Glasboden, HUD-Editor (Drag&Drop), Spotify-Player.

- 2026-11-07 (_45, Teil 6): Freecam startet im Kopf (Körper erst sichtbar ab 1,2 Blöcken Abstand, outsideBody()),
  Hände sichtbar (Option "Show Hands"), Tempo-Anzeige optional ("Speed Display"), klein unter dem Fadenkreuz, blendet aus.
  HUD lebendiger (Accent → "Animated HUD" + "Animation Speed"): wandernder Rand-Schimmer, gleitendes Glanzlicht oben,
  atmender Farbschleier, fließende Akzentfarben; jedes Panel eigene Phase (ZeroHud.panel/flow).

- 2026-11-07 (_45, Teil 7): Rinku/HTML-GUI komplett entfernt (build.gradle, fabric.mod.json, gui/html, assets/html) –
  JAR von ~1,4 MB auf ~0,6 MB. HUD-Rework: HUD Size (Accent, Standard 80 %), jedes Element eigener Stil (Info freistehend,
  Waypoints als Pillen, Tränke als runde Abzeichen mit Punktring, Session als Kacheln, Watermark nur Logo, kleinere Modulliste),
  Animation dezenter (Standard 60 %). HUD-Editor (hud/HudEditorScreen): Drag&Drop, Rechtsklick = Reset, Esc = fertig;
  Öffnen über GUI-Settings "Edit HUD" oder Taste "HUD Editor". Offsets in Accent.hudOffsets. Vanilla-Effekt-Icons aus bei Potion HUD.

- 2026-11-07 (_45, Teil 8) – "alles verbessern":
  - Alte GUIs (ClickGuiScreen, QuiqzyScreen, LiquidGui), alte HUDs (InfoHud, QuiqzyHud), GUI-Umschalt-Taste und WalksyLib entfernt
    (kein extra Mod mehr nötig). JAR ~0,57 MB.
  - Neue Kategorie HUD (Module.Category.HUD) mit allen Anzeige-Modulen; Misc hat nur noch Accent, Chat, Sounds, Start Menu.
  - Performance: Nametags/2D ESP/Radar nutzen Entity-Cache (ZeroHud.nearby, 20x/s, 256 Blöcke).
  - Großes Farbfenster jetzt für JEDE Farbeinstellung (Klick auf den großen Farbkreis), nicht nur Block ESP.
  - HUD-Editor: Andocken an Rändern/Mitte/anderen Elementen mit orangen Hilfslinien.
  - Config-Profile gab es schon (GUI-Seite "Configs").
  - Selbsttest-Modi: ZERO_SELFTEST_ONLY=hud / improve.

- 2026-11-07 (_45, Teil 9):
  - Text Replace (Misc): ersetzt Text im Scoreboard nur lokal (Server/andere sehen echte Werte), Farben bleiben.
    Regeln per Chat: /zreplace add <alt> => <neu> | list | remove <nr> | clear (util/ReplaceCommand, Fabric client commands).
    Option "Hide My Name" (Streamer-Modus). Mixin ScoreboardTextMixin (drawString + Font.width in displayScoreboardSidebar).
    Neuer Setting-Typ Setting.Strings (unsichtbar im GUI). Selbsttest: ZERO_SELFTEST_ONLY=replace.
  - Status Bar neu: einzelne schwebende Kapseln mit eigenen Icons (Speicher-Ring, Uhr, pulsierender Server-Punkt, FPS-Balken).
  - Keystrokes neu: große Buchstaben ohne Kästen, gedrückte Taste wird zur Akzent-Kachel, LMB/RMB mit CPS darunter,
    Leertaste als schmale Leiste. Radar: feiner wandernder Ring, Sichtkegel, Nord-Markierung, Spielerköpfe mit Akzentring.

- 2026-11-07 (_45, Teil 10): Text Replace hat jetzt im Modul eine LIVE-VORSCHAU des echten Server-Scoreboards
  (ZeroGui.scoreboardEditor): Zeile anklicken -> Textfeld (Field.REPLACE), Enter speichert, Rechtsklick = Original.
  &-Farbcodes werden zu §. Regeln pro ganzer Zeile (TextReplaceModule.setRule/ruleFor/serverLines). /zreplace geht weiterhin.
  Radar-Fix: Sichtkegel-Kasten und oranger Ring-Strich entfernt, eigener Pfeil mit dunklem Punkt in der Mitte.

- 2026-11-07 (_45, Teil 11): Neues Modul Visual FX (Visuals): Target Ring (wandernder Punktring um den Gegner),
  Target Outline (Vanilla-Glow-Umriss in Akzentfarbe, GlowMixins), Jump Circles, Kill Screen ("ELIMINATED" + Grau),
  Halo (goldener Ring über dem Kopf, in F5). Wichtiger Fix: Welt->Bildschirm-Projektion (HitFx.render, ZeroHud.markers)
  nutzt jetzt die ECHTE Kamera (gameRenderer.getMainCamera) – vorher falsch in F5/Freecam. Selbsttest: ZERO_SELFTEST_ONLY=vfx.

- 2026-11-07 (_45, Teil 12) – Render-Engine verbessert:
  - Neues Vertex-Element "Extra" (vec4) im SHAPE_FORMAT (ZeroPipelines.EXTRA), zero_shape.vsh/fsh erweitert.
  - Neue Grundformen: ZeroRender.arc(cx,cy,radius,width,startDeg,sweepDeg,from,to) (Bogen/Ring, runde Enden,
    Verlauf entlang des Bogens), ZeroRender.ring(...), ZeroRender.radial(...) (radialer Verlauf).
  - Glow fällt jetzt gauss-ähnlich ab (weicher, lichtartiger) statt linear.
  - Eingesetzt: Trank-Restzeit-Ringe, Speicher-Ring (Status Bar), Crosshair-Cooldown als Bogen ums Fadenkreuz.

- 2026-11-07 (_45, Teil 13): Neues Modul Base Finder (World): bewertet geladene Chunks nach Spieler-Spuren
  (Shulker 15, Beacon 25, Ench. Table 8, Ender Chest 6, Anvil 5, Redstone 3, Schilder/Banner 3, Truhen/Fässer 2,
  Glas/Wolle/Beton/Obsidian je wenig), Dörfer (Glocke) x0.35. HUD-Liste (Richtung, Distanz, Score-Bogen, Grund),
  Leuchtsäule + Ring in der Welt, Sound, optional Auto-Waypoint. Selbsttest ZERO_SELFTEST_ONLY=base (baut Test-Base).

- 2026-11-07 (_45, Teil 14): Promo-Edit "ZERO_BaseFinder_Edit.mp4" (im Projektordner). Footage per Selbsttest
  ZERO_SELFTEST_ONLY=edit ZERO_SELFTEST_SIZE=1280x720 (baut Test-Base, Freecam-Kamerafahrt, Screenshots edit_a/b/c_*.png),
  Schnitt mit tools/make_edit.py <Projekt> <Arbeitsordner> <ffmpeg.exe> (Titel, Zoom-Blur, Glow, eigener Synth-Sound).

- 2026-11-07 (_45, Teil 15): Zweites Edit "ZERO_Bases_Edit_NoMusic.mp4" (33,4 s, OHNE Musik, Schnitte auf Beat-Raster
  92,3 BPM / Drop 11,61 s / Outro 30,37 s; Schnittliste ZERO_Bases_Edit_Schnitte.txt). Footage: ZERO_SELFTEST_ONLY=edit2
  (baut 41x41 Mega-Base mit Türmen/Beacons + unterirdische 27x27 Base mit Schacht, 8 Kamerafahrten e2_*.png).
  Schnitt: tools/make_edit2.py (Buchstaben-Pop-Titel, ZERO-Logo, Punch-Zoom pro Beat, Zoom-Blur).
  Fremde Musik wird NICHT eingebaut – Nutzer legt den Song selbst in CapCut ab 0:00 darunter.

- 2026-11-07 (_45, Teil 16) – Effekte aus Showcase-Videos (eigene Umsetzung):
  - Glass (Visuals): Spieler durchscheinend (state.isInvisible + !isInvisibleToPlayer), weißer Umriss, Rüstung weg
    (GlassMixin + GlassAvatarMixin auf AvatarRenderer.extractRenderState RETURN, da Ausrüstung dort zuletzt gesetzt wird).
  - X-Ray (Visuals): animierte Skelette im HUD (ZeroHud.xray). Cyberspace (Visuals): Himmels-Gitter + Sternschnuppen.
  - Visual FX: Shockwave (Block-Umrisse beim Landen) + Hit Splash (Tinten-Spritzer am Boden). Welt-Renderer render/FxRenderer
    (debugFilledBox, mit Tiefentest). Rain FX (Visuals): Embers/Water/Neon-Regen mit Boden-Spritzern.
  - Weapon Skins: neue eigene Messer Bayonet/Flip/Gut/Bowie (tools/make_weapons.py) + Swing Trail (Bogen-Spur beim Schlagen).
  - Nicht gemacht: Chroma (Welt grau außer um dich – bräuchte Post-Shader), Planet am Himmel. CS:GO-Skins nicht kopiert.

- 2026-11-08 (_45, Teil 17) – Combat-Runde + Spawner Bot:
  - Neu (Combat): Kill Aura, Trigger Bot, Aim Assist, Auto Clicker, Velocity (VelocityMixin auf ClientPacketListener,
    skaliert Rückstoß nach dem Setzen), Hitboxes (HitboxesMixin auf Entity.getPickRadius), Criticals (Auto-Jump),
    W-Tap, Auto Weapon (Hook in MultiPlayerGameModeMixin.attack HEAD), Auto Gap, Auto Pot, Auto Armor, Crystal Aura.
    Auto Totem von Player nach Combat verschoben. Gemeinsame Ziel-/Blick-Hilfen: util/Targets.
  - Spawner Bot (World): markierte Spawner (Setting.Waypoints, GUI-Knöpfe "Mark spawner" / "Run now" in waypointRows)
    alle X min öffnen -> Knopf-Item (Standard Dropper) klicken -> schließen; dann /order <item> -> oberstes Item klicken ->
    alle Items per Shift-Klick ins Menü. Zustandsautomat in SpawnerBotModule. Spawner müssen in Reichweite sein.
  - Getestet (Einzelspieler, ZERO_SELFTEST_ONLY=combat): Kill Aura trifft, Auto Armor zieht Helm an, Bot-Ablauf läuft
    durch und bricht sauber ab (kein Spawner-Menü / kein /order im Einzelspieler). Auf DonutSMP-Server NOCH NICHT getestet.

- 2026-11-08 (_45, Teil 18) – Spawner Bot nach Server-Test (ArticSMP) robuster:
  - Spawner-Menü: bis zu 3 Öffnungsversuche, wartet bis der Knopf (Dropper) im Menü geladen ist.
  - CLOSE_ALL: Esc so oft (max. 6), bis ~10 Ticks lang kein Menü mehr aufgeht (Server öffnet nach Abliefern das
    Sell/Orders-Menü erneut -> vorher hing der Bot darin). Esc liefert die Order ab.
  - Abliefern: alle passenden Stapel auf einmal per Shift-Klick (wie Shift-Doppelklick).
  - Neu: "Collect Ground Items" + Radius – wartet 1,5 s, läuft zu gedroppten Items (Dropper = "Dropped current page loot"),
    springt bei Hängenbleiben, überspringt unerreichbare Items nach 2 s, läuft zurück zum Startplatz (schleicht am Ende). Test ZERO_SELFTEST_ONLY=bones: 48/48 Bones, 0,2 b vom Start.

- 2026-11-08 (_45, Teil 19) – Spawner Bot Abliefern-Fix (Medal-Clip ArticSMP: Bones blieben im Inventar):
  Ursache: alle 36 Shift-Klicks im selben Tick, bevor der Server das Liefer-Menü geschickt hatte -> verworfen.
  DEPOSIT wartet jetzt auf containerMenu.getStateId() != 0, verschiebt 1 Stapel pro 2 Ticks, prüft den Rest;
  geht ein Stapel 3x nicht weg -> Aufnehmen + in freien Menü-Slot legen. Stoppt bei vollem Menü. Test ZERO_SELFTEST_ONLY=deposit
  (Truhe als Ersatz): 1344/1344 Bones abgeliefert, Menü geschlossen, Status zurück auf IDLE.

- 2026-11-08 (_45, Teil 20):
  - Tracers 3D (Ich-Perspektive: aus dem Fadenkreuz): Startpunkt sonst echte Punkte am Spieler ("start_3d": Body/Eyes/Feet, Standard Body; Crosshair/Bottom
    nutzen die ECHTE Kamera -> richtig in Freecam). Tracer-Schalter in Entity ESP, 2D ESP und Block ESP (EspRenderer).
  - Freecam: smartCull aus solange aktiv (keine verschwindenden Chunks in/hinter Blöcken), "Look Sensitivity" (100 % =
    Minecraft-Sensi, gleiche Formel wie Entity.turn x0,15). Selbsttests: ZERO_SELFTEST_ONLY=tracer / fcull.

- 2026-11-08 (_45, Teil 21): Spawner Bot "Auto Mark Spawners" (Standard an) + "Mark Radius" (5 b): alle 2 s Würfel
  um den Spieler scannen, neue Spawner markieren; abgebaute (geladen, < 48 b, kein Spawner mehr) aus der Liste nehmen.
  GUI-Knopf "Mark all". Test ZERO_SELFTEST_ONLY=automark: Spawner in 5 b gefunden, der in 12 b nicht.

- 2026-11-08 (_45, Teil 22): Spotify HUD (HUD-Kategorie, verschiebbar "spotify"): util/MediaInfo startet
  assets/quiqzy/media/media_watch.ps1 (Windows GlobalSystemMediaTransportControls über PowerShell 5.1, kein Login),
  liest Titel/Künstler/Position/Dauer/Status/Cover alle 0,5 s; Steuerung media_control.ps1 (toggle/next/prev, noch ohne Knöpfe).
  Lyrics synchron von lrclib.net (/api/get, Fallback /api/search), Position "Above Hotbar"/Top/Under Widget, Größe, Offset.
  Optionen: Album Cover, Hide When Paused, Spotify Only. Selbsttest ZERO_SELFTEST_ONLY=spotify (MediaInfo.injectTest).
  Mit echtem Spotify NOCH NICHT getestet (lief nicht); Cover nur getestet: Chrome lieferte keins.

- 2026-11-08 (_45, Teil 23): Spotify im Anubis-Stil: Lyrics Karaoke (ZeroHud.lyrics) – Wörter gleiten gestaffelt rein,
  gesungene Wörter werden grün (Wortzeit nach Länge auf 90 % der Zeilendauer verteilt), aktives Wort mit Glow,
  Umbruch auf mehrere Zeilen, nächste Zeile grau darunter, "• • •" vor dem ersten Einsatz. Widget neu: größeres Cover,
  Schein in Cover-Durchschnittsfarbe, Equalizer, Fortschritt mit Knopf, "PAUSED".

- 2026-11-08 (_45, Teil 24) – Crystal PvP: Crystal Optimizer (Standard an; MultiPlayerGameModeMixin attack RETURN ->
  ClientLevel.removeEntity), Surround (util/Place, Obsidian 4 Seiten + Boden, Center, Rotate, Disable When Moving),
  Auto Double Hand (Totem in Haupthand bei HP/Kristall in Nähe, füllt Hotbar-Totem-Slot nach), Key Pearl (Keybind = Perle
  werfen + zurück), Hole ESP (Bedrock grün / Obsidian gelb, FxRenderer.holes). GUI-Filter "Crystal" erweitert.
  Test ZERO_SELFTEST_ONLY=cpvp: alles bestanden (Kristall sofort weg, 4/4 Obsidian, Totem in Hand, 2 Löcher erkannt).

- 2026-11-08 (_45, Teil 25) – Anchor PvP: Safe Anchor (Anchor-Modul "Safe Anchor", Standard an; util/AnchorUtil.placeShield:
  Glowstone zwischen dich und den Anchor, bei Anchor direkt neben dir auf den Anchor drauf), Anchor Aura (neben Gegner
  setzen -> laden -> Schild -> sprengen; Mobs-Schalter zählt auch Tiere), Hit Crystal (Rechtsklick mit Obsidian:
  Obsidian, Kristall, schlagen). Test ZERO_SELFTEST_ONLY=anchor bestanden. Hinweis Test: ingame_setup legt ein Schwert
  nur clientseitig in den aktiven Slot -> im Test Items auch clientseitig setzen.

- 2026-11-08 (_45, Teil 26): GUI 3D-Kippen (QuiqzyConfig.gui3d, Settings "3D tilt"): ZeroGui.tiltMatrix (Verkürzung +
  Scherung + Versatz zur Maus, affin, da GuiGraphics nur 2D-Posen kann), Maus per inverser Matrix (toPanel) zurückgerechnet
  in render/mouseClicked/mouseReleased; Schatten gegen die Neigung, Lichtreflex (drawGlare) folgt der Maus. Pausiert beim
  Ziehen von Fenster/Slider/Farbwähler. Test ZERO_SELFTEST_ONLY=tilt.

- 2026-11-08 (_45, Teil 27c): Triple Tap final nach Clip + Nutzer: Sprint-Hit mit Totem in der Hand -> Obsidian + Launch-
  Kristall -> Air Hit mit AXT (wartet hurtTime==0) -> 2 Kristalle auf dasselbe Obsidian (je hurtTime==0). Settings:
  "Sprint Hit (Totem)", "Air Axe Hit", "Wait For Damage Tick". Test: Reihenfolge stimmt (3 Kristalle, 1 Axtschlag).
- 2026-11-08 (_45, Teil 27b, ersetzt): Triple Tap umgebaut auf die Reihenfolge aus dem Video: Launch-Kristall -> Air Sword Hit ->
  2 Pop-Kristalle; jeder Schritt wartet auf target.hurtTime == 0 ("Wait For Damage Tick"), Timeout 10 s.
- 2026-11-08 (_45, Teil 27, alt): Triple Tap (Combat, Crystal-Filter): Keybind -> Schwertschlag, Obsidian neben Ziel
  (vorhandenes Obsidian/Bedrock bevorzugt), Kristall setzen+schlagen (Launch), dann "Extra Crystals" (2) mal wiederholen
  (Double Pop); wartet auf Server-Bestätigung von Obsidian/Kristall, Timeout 6 s, schaltet sich danach aus.
  Test ZERO_SELFTEST_ONLY=triple: 3 Kristalle, 1 Obsidian, Ziel 40 -> 1 HP.

- 2026-11-08 (_45, Teil 28c): Loader-Crash gefixt (Hover-Index -> mouse.Contains, vorher IndexOutOfRange -> halbe Malerei/"Striche").
  Look voller: Hero-Vorschaubild (tools/loader/preview.png aus t_ingame_setup) hinter großem ZERO-Banner, CLIENT + Tagline,
  Chips (ONE CLICK / FABRIC 1.21.11), Fenster 620x574. Build jetzt mit -resource:preview.png,preview.png.
- 2026-11-08 (_45, Teil 28b, alt): Loader-Style komplett neu (tools/loader/ZeroLoader.cs), ZERO-Look: dunkel, Ember-Glow,
  schwebende Funken, Glas-Karten-Liste mit LOADED-Badges + Akzentbalken, großer Load-Knopf mit Glow + Sheen, Ghost-Buttons,
  randloses Fenster. WICHTIG: System-csc ist C#5 (kein ?., kein $"-String, keine expression-bodied members).
  Inject-Machbarkeitstest (tools/inject, gelöscht) funktionierte (Tick-/HUD-Payload live), aber Mixins/Shader brauchen
  Neustart -> keine volle Inject-Version gebaut.
- 2026-11-08 (_45, Teil 28, alt): "ZERO Loader.exe" (FERTIG/, Quelle tools/loader/ZeroLoader.cs, C# WinForms, gebaut mit
  .NET-Framework-csc: csc -target:winexe -resource:<jar>,zero.jar -r:System.Management.dll ...). Findet Profile
  (Vanilla, NoRisk D:\minecraft norisk\profiles + AppData, Modrinth, Prism, Feather .dawn, eigene via "Other…"),
  Load = eingebettete JAR in mods kopieren (alte zero-client*.jar weg), Unload, Folder; erkennt laufendes Spiel (WMI).
  KEIN Injizieren in laufende Prozesse. Bei jedem neuen Build die EXE mit der neuen JAR neu kompilieren.

- 2026-11-08 (_45, Teil 29): Cart Aura (Combat, TNT-Minecart-PvP): Schiene neben Ziel -> TNT-Minecart drauf -> zünden
  (Schlag oder Feuerzeug). "Auto Ignite Nearby" zündet gesetzte Carts in Reichweite, "Safe (Obsidian)" setzt Block dazwischen.
  Braucht Schiene + TNT_MINECART in der Hotbar. cartAt-Box auf Rail-Höhe (nicht .above()). Zündung = gameMode.attack ->
  server hurtServer -> primeFuse. Test ZERO_SELFTEST_ONLY=cart (Gameregel tntExplodes muss an sein): Schwein 20->10.
  Hinweis: InjectEntry wieder entfernt (Nutzer: "scheiß auf inject"), ZeroRender hat jetzt Reflection-Fallback für
  zero$beginElement (beginElement per Signatur gesucht) - normaler Build nimmt weiter den Mixin-Pfad.

- 2026-11-08 (_45, Teil 30): Flame Cart (Combat): erkennt eigene (Flammen-)Pfeile, simuliert die Flugbahn (Schwerkraft 0,05,
  Luftwiderstand 0,99, level.clip) bis zum Einschlag und setzt dort Schiene + TNT-Minecart OHNE Blickdrehung
  (clickOn: Schiene auf Block darunter, Cart auf die Schiene; 4-Tick-Gap für Sync). Flammenpfeil zündet den Cart.
  Settings: Max Distance, Any Arrow, Hit Ignite Fallback. Weit-Platzieren geht nur auf Servern ohne Reichweiten-Check.
  Cart Refill (util/Refill.keep per SWAP-Klick ohne Inv zu öffnen) in Cart Aura + Flame Cart: hält Cart-/Schienen-Slot gefüllt.
  Test ZERO_SELFTEST_ONLY=flamecart: rails=1 carts=1 am Pfeil-Einschlag.

- 2026-11-08 (_45, Teil 30b): Flame Cart nach Server-Clip: Platzierung schneller (gap 1 statt 4), Rail/Cart-Slot wird
  WIRKLICH in die Main Hand genommen und erst nach ~3 Ticks zurückgewechselt (savedSlot/holdRevert), "Hit Ignite Fallback"
  Standard jetzt AUS (nur der Flammenpfeil zündet). Test weiter rails=1 carts=1.

- 2026-11-08 (_45, Teil 30c): Flame Cart: Platzierung jetzt im SELBEN Tick (Schiene+Cart, Server ordnet Pakete),
  "Totem Before Boom" (totem_swap/totem_range) in Flame Cart + Cart Aura: bei scharfem TNT-Minecart in Reichweite
  Totem in die Hand. WICHTIG: isPrimed()/getFuse() kommt nur beim Client an, wenn tntExplodes an ist UND der Cart
  schon in der Welt ist (erst addFreshEntity, dann primeFuse). Test: slot wechselt auf Totem, fuse zählt runter.

- 2026-11-08 (_45, Teil 31): Kit-Module (Combat): Auto Jump Reset (springt bei hurtTime-Anstieg / optional beim eigenen
  Schlag via MultiPlayerGameModeMixin), Shield Breaker (auf blockenden Gegner kurz Axt -> Schlag -> zurück), Auto Shield
  (hebt Offhand-Schild bei Kristall/Cart/Pfeil in Reichweite, keyUse). NICHT in-game getestet (nur kompiliert, Usage sparen).

- 2026-11-08 (_45, Teil 32): Mehr Module: Auto Pearl (Perle bei niedrigem HP), Auto Clutch (Wassereimer beim Fallen),
  Auto Drain (schnelle Axt-Schläge aufs Schild), Fast Bow (abfeuern bei voller Spannung + neu spannen). Nur kompiliert,
  nicht in-game getestet (Usage sparen). Auto Respawn gab es schon (Datei war überschrieben -> wiederhergestellt).

- 2026-11-08 (_45, Teil 33): Trajectories (Visuals, FxRenderer.trajectories, Billboard-Punkte + Landering, Physik je Item:
  Bogen/Armbrust/Dreizack 0.05 Gravity, Perle/Snowball/Egg/Pots 0.03) und Auto Sprint (Movement). Trajectory-Punkte
  im Test sichtbar (kurzer Bogen auf nahe Wand; senkrechte Strahlen im Bild = Sus Chunk, nicht Trajectory).

- 2026-11-09 (_45, Teil 34): Base Finder gegen Anti-Xray (DonutSMP zeigt nur bis Deepslate): Mixin ChunkBlockEntitySpyMixin
  (@ModifyArg auf ClientPacketListener.updateLevelChunk -> replaceWithPacketData Consumer, index 4) greift jede Block-Entity
  aus dem Chunk-Paket ab (onServerBlockEntity), auch versteckte unter Deepslate -> packetBEs je Chunk. scan() zählt diese
  mit (bePoints), kein Doppelzählen mit sichtbaren Blöcken. Settings: "Scan Block Entities", "Only Below Deepslate" (an),
  "Deepslate Y" (0), "Deep Boost" (5x). onlyDeep überspringt Sektionen/Blöcke/BEs über deepY. Regressionstest (base) ok vor onlyDeep.

- 2026-11-09 (_45, Teil 35): Base Finder "Only Below Deepslate" (an): onlyDeep überspringt Sektionen/Blöcke/BEs ab deepY (0).
  Smart Filter (an) gegen Rauschen: Chunk zählt nur mit Spieler-Signal (shulkers/beacons/anchors/anvils/ench.tables/brewing/redstone)
  ODER >= minContainers (4) Containern (chests+barrels+shulkers+ender+hoppers), sonst verworfen (einzelne natürliche Truhe = Rauschen).

- 2026-11-09 (_46): Neues Modul **Sound Chunks** (World): liest auf welchem Weg Maschinen-Sounds zum Client kommen und markiert
  die Chunks (orangener Chunk-Kasten + Beam + Maschinen-Boxen, wie Sus Chunks). Sound-Zugänge in 1.21.11:
  - Ofen/Smoker/Hochofen/Campfire: **kein Server-Paket** – Server spielt in animateTick CLIENTSEITIG via
    ClientLevel.playLocalSound → Mixin ClientLevelSoundSpyMixin (require=1; Level-Mixin reicht nicht, ClientLevel überschreibt
    ohne super). Erkennung per Sound-ID (block.smoker.smoke, block.furnace.fire_crackle, ...).
  - Crafter/Brewing/Dispenser: ClientboundLevelEventPacket (LevelEvent.SOUND_CRAFTER_CRAFT/FAIL, SOUND_DISPENSER_DISPENSE/
    PROJECTILE_LAUNCH, SOUND_BREWING_STAND_BREW) → Mixin SoundEventSpyMixin.handleLevelEvent.
  - Positionierte Server-Sounds: ClientboundSoundPacket → SoundEventSpyMixin.handleSoundEvent.
  - Dateien: module/world/SoundChunkModule.java, render/SoundChunkRenderer.java, hud "Sound Chunks" in ZeroHud.sounds(),
    Mixins SoundEventSpyMixin + ClientLevelSoundSpyMixin (+json). Selftest "sounds": Smoker+Ofen vor Spieler werden per
    Reflection gezündet (litTimeRemaining), Log "sound marked chunks/sus/kinds" → Verifiziert: 1 sus Chunk, smoker x16,
    HUD + Beam sichtbar (Screenshot t_snd1.png). Settings: smoker/crafter an, furnace/blast_furnace/campfire/dispenser aus,
    min_sounds 1, forget 10 min.

- 2026-11-09 (_48):
  - **Spawner Nametags** (Visuals / Nametags): Nametags über gefundenen Spawnern mit Mob-Icon (Schädel bei Skelett/Wither/Zombie/Creeper/Piglin/Drache, Drop-Items wie Blaze Rod/Spider Eye/Perle/Magma/Slime/Breeze, Spawn-Egg für alle weiteren Mobs), Typ ("Skeleton Spawner" etc.), Entfernung ("18m") und Koordinaten. Schalter "Spawners" auch im Nametags-Modul.
  - **Spawner Noti** (World): Popup-Benachrichtigung oben im Bildschirm bei neu entdeckten Spawnern ("SPAWNER GEFUNDEN! [Icon] Skeleton Spawner • X Y Z (Distanz)") mit Ton (UiSounds.found()).
  - **Beacon Beam**: Leuchtender Strahl direkt über dem Spawner bis zum Build-Limit (mc.level.getMaxY()), optional durch Wände sichtbar (SpawnerRenderer).
  - **Spawner Tracker**: erkennt Spawner aus Anti-Xray-Chunk-Paketen (ChunkBlockEntitySpyMixin), geladenen Chunks und Block-Updates (ClientLevelMixin).
  - Bugfix BaseRadarModule (Kompilierfehler Namenskonflikt clusters behoben).

- 2026-11-09 (_49):
  - **Individuelle Standardfarben in Block ESP**: Jeder Block hat beim Auswählen eine passende, individuelle Standardfarbe (z. B. Ender Chest violett #9B51E0, normale Chest warmes Orange #FFA733, Trapped Chest rotorange, Shulker Boxes nach Farbtyp, Erze/Metalle in Diamantcyan, Smaragdgrün, Goldgelb, Kupfer etc.). Eigene Farbanpassung per Rechtsklick bleibt erhalten und setzt sich beim Reset auf die individuelle Standardfarbe zurück.

- 2026-11-09 (_50):
  - **Freecam Keep Movement**: Neues Setting "Keep Movement" (Standard an) in Freecam. Wenn man läuft, sprintet, springt oder sneakt und Freecam aktiviert, läuft/sprintet/springt/sneakt der eigene Spieler-Körper nahtlos weiter, während die Kamera frei fliegt. Beim Ausschalten der Freecam wird die Bewegung wieder freigegeben.

- 2026-11-09 (_51):
  - **HUD & Visuals Rework (Apple Frosted Glass & Kompakt)**:
    - Alle HUD-Elemente und Panels auf kompaktes Apple-Glas-Design überarbeitet: transluzenter Frosted-Glass-Backdrop mit dezentem oberen Lichtreflex und feinen 0.5px-Rändern.
    - Notifies & Popups kompakter als schwebende Floating Pills (Dynamic Island Style, einzeilig, dezent und nicht mehr im Sichtfeld störend).
    - Nametags (Spieler, Mobs, Items, Spawner) verschlankt: ~25 % kompaktere Höhe, kleinere Schrift (7.0pt), subtileres Glas-Backplate.
    - Chunk- & Spawner-Markierungen verfeinert: Schlankere Beams (0.18b statt 0.35b) und feinere Kanten (0.025b), weichere Transparenz gegen Blendung.

- 2026-11-09 (_52):
  - **CatLean-inspirierte Nametags & HUD-Details**:
    - Nametags nach Tiefe/Distanz sortiert (nähere Spieler verdecken weiter entfernte, wie in CatLean).
    - Ping-Anzeige mit Farbcodierung (grün < 50ms, gelb < 120ms, rot >= 120ms), ein-/ausschaltbar im Nametags-Modul.
    - Ausrüstungs-Vorschau mit Minecraft-Item-Dekorationen (Haltbarkeitsbalken & Stack-Größe) über dem Nametag.
    - Aktive Trankeffekte (Potion Effects) als kompakte Glas-Badges unter dem Spielernamen (Name, Stufe, Dauer), ein-/ausschaltbar.
    - Vollständig abgestimmt auf den ZERO Client Frosted Glass- & Orange-Akzentstil.

- 2026-11-09 (_53):
  - **Volles CatLean Nametag-System & CatLean GUI/HUD-Stil**:
    - **Neuer GUI/Client-Style "CatLean"**: In `AccentModule` neuer Preset & HUD-Style "CatLean" (Signatur Electric Lean Violett #A855F7 & Neon Magenta #D946EF mit tiefem Obsidian-Glas).
    - **Vollständiges CatLean Nametag**:
      - Durchgehender Health-Balken direkt am unteren Nametag-Rand mit dynamischer Farbgebung.
      - Ausrüstung mit Verzauberungs-Badges über den Items (P4, U3, M, S5, Sharpness etc.) und Haltbarkeitsbalken.
      - Gamemode-Tag ([S], [C], [A], [SP]), Ping, Herz-Symbol (❤) und Pop-Counter.
      - Potion-Badges mit Farbpunkten passend zum Trankeffekt.
    - **Alle HUD-Elemente im CatLean-Stil**:
      - **Watermark**: Sleek Kapsel `[ • ZERO | v1.0.0 | User | FPS | Ping ]` mit pulsierendem Violett-Punkt und Gradient-Rand.
      - **ModuleList / ArrayList**: Modus-Suffixe in eckigen Klammern `[Mode]` (z. B. `KillAura [Smooth]`), fließender Neon-Farbverlauf und 1.5px Neon-Balken.
      - **TargetHUD**: Obsidian-Karte mit lila Glow, Kopf-Avatar, Distanz-Tag, sanfter Lebensbalken mit Schadens-Trail.
      - **Info HUD**: Kompakte Kapseln mit lilafarbenen Labels und weißen Werten.

- 2026-11-09 (_54):
  - **CatLean Grau-Design & 1:1 identische Spawner- und Player-Nametags**:
    - **Reines CatLean Dunkelgrau-Design**: `Theme.tint` wandelt warme/orange Clientfarben und bräunliche Hintergründe direkt in minimalistisches, cleanes Charcoal/Dunkelgrau (`#141418`), Slate-Ränder (`#3F3F46`) und Silber-Weiß (`#D4D4D8`/`#FFFFFF`) um.
    - **1:1 identische Nametags für Spawner & Player**:
      - Exakt selbe Abmessungen (13.5pt Höhe, 4.5pt Radius), dunkles Grau-Backplate (`0xD8141418`) und dezenter grauer 0.6px-Rand (`0x553F3F46`).
      - Spawner-Nametag hat nun dasselbe Pill-Layout mit Icon, fettem weißen Mob-Namen, Distanz in cleanem Grau (`#A1A1AA`), Koordinaten und dezenter Bodenlinie wie beim Player-Nametag.
      - Player-Nametag hat ebenfalls das cleane CatLean Dunkelgrau-Design (weißer Name, Ping, Gamemode, Health-Bar, Enchants).

- 2026-11-11 (_56): **Base Finder Research** (neues World-Modul, Forschungs-Modul mit Confidence-Scores):
  - Abgrenzung: klassisches **Base Finder** bleibt unverändert (Score-Scan); das hier ist die
    Forschungsvariante mit sauberen Confidence-Werten statt Fund-Behauptungen.
  - **1) Position & Chunk-Tracking**: neuer Mixin `ClientPacketListenerMixin`
    (`handleMovePlayer` HEAD, require 0) – `ClientboundPlayerPositionPacket` =
    server-bestätigte Position + Zähler für Korrekturen/Rubberbands. Bei jedem Chunk-Load wird
    Spieler-Y + Menge an Untergrund-Sections protokolliert → geschätzter **Data-Y-Schwellwert**
    (ab welchem Y der Server Untergrund-Daten mitschickt; Anzeige nur bei konsistenter Stichprobe).
    Rescan-Vergleiche zählen "Data-Gain"-Events (Untergrund-Sections nehmen zu).
  - **2) Chunk-Update-Analyse**: Load/Unload-Tracking + Block-Updates im Untergrund (y<0) über den
    erweiterten `ClientLevelMixin`-Hook.
  - **3) Untergrund-Struktur-Erkennung** (nur Sections unter Oberfläche, Palette-Vorabcheck):
    - Rechteckige Räume: größtes Luft-Rechteck pro Ebene (Histogramm-Algorithmus), Decke/Boden-
      Soliditäts-Probe (3x3) → Conf 0.30+0.45·above+0.25·below, <0.45 verworfen (vermutlich natürlich)
    - Tunnel: horizontale Luft-Korridore ≥ Mindestlänge, oben+unten fest, Conf nach Länge
    - Indikator-Blöcke mit Gewichten (Kiste 18, Bett 20, Öfen/Werkbank 15, Leitern 10, Fackeln 4 ...)
    - **Mineshaft-Unterscheidung**: ≥8 Zäune + ≥6 Schienen → Confidence auf 30 % gedeckelt
  - **4) Research Dashboard** (ZeroHud, grün): Client-Y + server-Position (frisch/alt) + Correction-
    Zähler, geladene Chunks + Section-Statistik (unter/total), Struktur-Anzahl + Top-Confidence,
    Data-Y-Schätzung + Gain-Events.
  - **Renderer** (`BaseFinderResearchRenderer`, eigene Through-Walls-Pipeline
    `pipeline/base_research_through_walls`): Chunks über Min. Confidence, Farbe nach Confidence
    (rot→grün), Struktur-Boxen: Raum türkis / Tunnel orange / Indikator-Cluster gelb.
  - Settings: Scan Radius, Scan Budget, Track Position/Chunks, Rectangular Rooms, Tunnels,
    Indicator Blocks, Min. Room Size, Min. Tunnel Length, Min. Confidence, Overlay, Through Blocks,
    Overlay Style, Overlay Opacity, Mark Color (0x8CF56B), Structure Boxes, Research Dashboard.
  - Neue Dateien: `module/world/BaseFinderResearchModule.java`, `render/BaseFinderResearchRenderer.java`,
    `mixin/ClientPacketListenerMixin.java` (+ mixins.json-Eintrag).
  - Geändert: `ModuleManager`, `QuiqzyClient`, `ClientLevelMixin` (+1 Zeile), `ZeroHud` (+Dashboard).

- 2026-11-11 (_56): **Activity Inspector** (neues World-Modul, Anti-Xray-Bypass über Spieler-Aktivität):
  - Idee: Anti-Xray versteckt Erze, aber **Luft wird nie ersetzt** – gegrabene Schächte sind echte
    Client-Daten und damit nicht versteckbar.
  - Analyse pro Chunk: 16×16 Spalten von der Oberfläche abwärts (Heightmap-Sprung, kein Sky-Walk),
    vertikale Luft-Runs zwischen festen Blöcken = "Loch nach unten" (jede Größe zählt, ab Mindesttiefe).
  - Klassifikation: Luft-Nachbarn an Mittel- + Unterzelle (8 Samples): ≤ 4 = **enger Schacht**
    (1x1 = 0 Nachbarn, 3x1/2x2 = 4), ≥ 5 = offenes Loch (Höhle/Ravin).
  - **Chunk wird markiert, wenn: mindestens 1 Loch UND insgesamt nur wenige Löcher ≤ Max. Holes**
    (Löcher egal welcher Größe zählen: 1x1/3x1-Schächte wie offene Löcher; kein natürliches
    Höhlensystem → verdächtig). Setting **Only 1x1/3x1 Shafts** (Standard aus) schärft auf enge
    Schächte; enge Schächte werden weiterhin separat gezählt und als Würfel markiert.
  - **Markierung wie Xray Research** (`ActivityInspectorRenderer`, eigene Through-Walls-Pipeline
    `pipeline/activity_inspector_through_walls`): Chunk-Box Fill + pulsierende Kanten über dem
    Tiefenbereich der Schächte + kleine Würfel an jeder Schacht-Oberkante.
  - Settings: Scan Radius, Scan Budget (Standard 2/t), Min. Shaft Depth (4), Max. Holes (6),
    Chunk Overlay, Through Blocks, Overlay Style, Overlay Opacity, Mark Color (0x4FC3F7, Cyan –
    unterscheidet sich vom Xray-Research-Lila), Shaft Boxes.
  - Neue Dateien: `module/world/ActivityInspectorModule.java`, `render/ActivityInspectorRenderer.java`.
  - Geändert: `ModuleManager` (+Feld), `QuiqzyClient` (+2 Chunk-Events, +Renderer-Registrierung).
  - Performance: Budget-Scan pro Tick, Rescan alle 20 s, Radius-Pruning; keine HUD-/Mixin-Änderungen.

- 2026-11-11 (_56): Xray Research Fix für Anti-Xray-Server (DonutSMP):
  - **Rare Ore Boxes + Reveal Boxes** werden jetzt in **allen** analysierten Chunks gezeichnet,
    nicht nur in Chunks über dem Highlight-Schwellwert (auf Anti-Xray-Servern kommen kaum
    Chunk-Dichten zustande, aber einzelne freiliegende Erze/Reveals trotzdem).
  - **Highlight Threshold** Standard 24 → 8 Erze.
  - Hinweis: Paper-Anti-Xray schickt nicht freiliegende Erze gar nicht erst an den Client –
    die Modul-Philosophie bleibt: nur tatsächliche Client-Daten anzeigen, keine erfundenen.

- 2026-11-11 (_56): **Xray Research** (neues World-Modul, selbstständig, keine bestehenden Module angetastet):
  - **Chunk Data Analyzer**: Chunk Load/Unload-Tracking (Fabric `ClientChunkEvents`), Section-Scans mit
    Palette-Vorabcheck (`maybeHas`, überspringt fast alles), Scan-Budget pro Tick (1–8, Standard 3),
    Rescan alle 20 s, Block-State-Update-Tracking über `ClientLevelMixin.sendBlockUpdated`
    (zusätzlicher Hook-Zeil; Spawner/Block-ESP unverändert) inkl. **Reveals** (Stein → Erz beobachtet).
  - **Block Analysis**: 11 Erz-Typen (Coal/Iron/Copper/Gold/Redstone/Lapis/Diamond/Emerald/Quartz/
    Nether Gold/Ancient Debris) pro Chunk gezählt + Rare-Positionen (Diamond/Emerald/Debris, max 64)
    + laufende Umgebungs-Dichte-Statistik (EMA) im eigenen Daemon-Thread (`ZERO Xray Research`),
    unveränderliche Snapshots via `ConcurrentHashMap` publiziert.
  - **Actual vs. Predicted**: gescannte Chunks = *tatsächliche Client-Daten* (100 % Confidence);
    geladene, nicht gescannte Chunks bekommen eine *statistische Vorhersage* (Vanilla-Dichte-Tabelle
    gemischt mit beobachteter Umgebungsdichte) und eine Confidence aus der Sample-Basis (25–95 %).
  - **Research Overlay** (`XrayResearchRenderer`, `WorldRenderEvents.BEFORE_TRANSLUCENT`, eigene
    Through-Walls-Pipeline `pipeline/xray_research_through_walls`): analysierte Chunks als Fill/Edges
    (Style Choice "Fill + Edge"/"Edges"/"Fill"), Farbe nach **Confidence** (rot→grün) oder Akzentfarbe,
    Vorhersagen gedämpft mit Puls, optionale Rare-Ore- und Reveal-Boxen, Overlay-Opacity 10–100 %.
  - **HUD-Panel** in `ZeroHud` (Xray-Research-Purpur `#A78BFA`): Top-3-Chunks mit Distanz, Top-2-Erzen
    und Confidence-% + 1 Vorhersage-Zeile ("predicted ~N ores") + Footer "Actual N · Predictions M · upd/s".
  - Settings: Scan Radius, Scan Budget, Chunk Overlay, Through Blocks, Overlay Style, Overlay Opacity,
    Highlight Threshold (min. Erze), Color By, Accent Color, Rare Ore Boxes, Show Predictions,
    Reveal Boxes, Track Block Updates, Info Panel.
  - Neue Dateien: `module/world/XrayResearchModule.java`, `render/XrayResearchRenderer.java`.
  - Geänderte Dateien: `ModuleManager` (Feld), `QuiqzyClient` (2 Chunk-Events + Renderer-Registrierung),
    `ClientLevelMixin` (1 Hook-Zeile), `ZeroHud` (Imports + Panel-Hook + `xr()`-Panel).

- 2026-11-11 (_56): Hand Shader > Item Glow (wie Gooba) – **Shell-Ansatz, sitzt auf dem Item**:
  - Erster Versuch (Vanilla-Outline-Pfad, `outlineColor` bei `ItemStackRenderState.submit`) war
    versetzt (Vanilla `ItemFeatureRenderer` zeichnet den Outline-Pass mit anderer Pose-Kette) → verworfen.
  - Neu: eigene Glow-"Huelle" in `HandGlowRenderer` – die `BakedQuad`s jeder Item-Ebene werden vom
    eigenen Zentrum aus aufgeblasen (Glow Size, %, 2–50, Standard 10) und als halbtransparente
    Position-Color-Geometrie mit **exakt derselben Pose** wie das Item gesubmitet
    (`submitCustomGeometry`, eigener RenderType/Pipeline aus `DEBUG_FILLED_SNIPPET`, kein
    Depth-Write, NO_DEPTH_TEST) – der Glow sitzt dadurch immer auf dem Item, für beide Hände und
    alle Items mit normalen Modellen. Redirect auf `ItemStackRenderState.submit` in
    `ItemInHandRenderer.renderItem` bleibt der Hook.
  - Settings: **Item Glow** (Bool), **Glow Color** (0xA78BFA), **Glow Opacity** (20–100 %, 80),
    **Glow Size** (2–50 %, 10).
  - Neue Mixins: `ItemStackRenderStateAccessor` (displayContext, layers, activeLayerCount) und
    `LayerRenderStateAccessor` (quads, transform) – in `quiqzy.mixins.json` registriert.
  - Kein Glow bei Special-Modell-Items (Schild, Elytra, Dreizack) – die haben keine BakedQuads.
- 2026-11-10 (_56): Hand Shader Swing-Styles (Gooba) + Spawner-Beam neu:
  - **Hand Shader > Swing** (Setting.Choice "Off/First..Sixth"): ersetzt die Vanilla-Swing-Animation
    der Hand. Dazu **Intensity** (%, Standard 100) und **Swing Speed** (%, Standard 100). Mixin-Redirects
    in `ItemInHandRendererMixin` (`swingArm` + `applyItemArmTransform` ordinal 4, keine MixinExtras)
    Formeln 1:1 aus der Gooba-Referenz-JAR (Ease: easeOutSine / linear / easeOutBack), Crossbows vanilla.
    (Wurde zwischendurch mal entfernt, danach auf Wunsch wieder drin.)
  - **Spawner Noti > Beam** (Vollausbau, alles einstellbar):
    - Beam Tooltip: **Beam Top** = "Build Limit" / "Player +64" / "Fixed" (+ **Fixed Beam Height** in Blöcken)
    - **Beam Width** (%, 100 % = exakt 1×1 Block, bis 300 %), **Beam Opacity** (5–100 %)
    - **Pulse Animation** (Bool) + **Pulse Speed** (10–300 %, Periode ~2,65 s bei 100 %) + **Pulse Strength** (0–100 %)
    - **Core Beam** (heller innerer Strahl wie beim echten Beacon) mit **Core Color**, **Core Width** (0–50 %) und **Core Opacity** (5–100 %)
    - Strahl startet an der Spawner-Blockoberkante; Box/Through Blocks unverändert.

## Offen / Nächste Schritte
- 2026-11-10 im Spiel testen: Hand Swing (6 Styles, Intensity/Speed) und neuen Spawner-Beam ansehen
  (Box/Walls-Rendering unverändert).
- Alles von heute im Spiel testen (noch nicht getestet): Grid-Animation, Glows, Sounds, neues HUD, Waypoint-Marker,
  Block-Farben per Rechtsklick, Sus-Chunk-Anzeichen.
- Stun Slam im Spiel testen (noch nicht getestet).
- Breach Check: genaue Bedeutung im Video war abgeschnitten – ggf. anpassen.
- Sound Chunks: Crafter/Brewing/Dispenser-Weg (Level-Events) ist implementiert, aber im Selbsttest noch nicht live verifiziert
  (nur Smoker-Weg getestet). Auf einem Server mit Crafter/Dispenser prüfen.
