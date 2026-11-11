package dev.quiqzy.client.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.gui.Draw;
import dev.quiqzy.client.gui.ZeroGui;
import dev.quiqzy.client.gui.engine.ZeroFont;
import dev.quiqzy.client.gui.engine.ZeroRender;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.module.misc.HudModule;
import dev.quiqzy.client.module.world.SusChunkModule;
import dev.quiqzy.client.module.world.SoundChunkModule;
import dev.quiqzy.client.module.world.XrayResearchModule;
import dev.quiqzy.client.module.world.XrayResearchModule.ChunkData;
import dev.quiqzy.client.module.world.XrayResearchModule.Prediction;
import dev.quiqzy.client.module.world.ActivityInspectorModule;
import dev.quiqzy.client.module.world.BaseFinderResearchModule;
import dev.quiqzy.client.module.world.WaypointsModule;
import dev.quiqzy.client.util.InputUtil;
import dev.quiqzy.client.util.SpawnerTracker;

/**
 * HUD im Stil der ZERO-GUI (eigene Render-Engine): Glas-Panels, Nunito-Schrift, Orange-Akzent, weiche Animationen.
 * Oben links Sus Chunk, oben rechts Info + Waypoints, unten links Keystrokes, Rüstung über der Hotbar,
 * und Waypoint-Marker direkt im Bild.
 */
public final class ZeroHud {
	private static final int ORANGE = 0xFFFFB26E;   // hell
	private static final int ORANGE2 = 0xFFEC5F1C;  // dunkel
	private static final int TEXT = 0xFFF4EEE8;
	private static final int MUTED = 0xFF9C8F86;
	private static final int FAINT = 0xFF6E6560;
	private static final String[] DIRS = {"South", "West", "North", "East"};
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private static final Map<Object, Float> ANIM = new HashMap<>();
	private static long lastNanos;
	private static float dt;
	private static float time;

	private ZeroHud() {
	}

	public static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.options.hideGui || mc.screen instanceof ZeroGui) return;
		editing = mc.screen instanceof HudEditorScreen;

		long now = System.nanoTime();
		dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
		lastNanos = now;
		time += dt;

		// gleicher virtueller Raum wie die GUI (540 "Pixel" hoch), dazu die HUD-Größe
		Window window = mc.getWindow();
		float pixels = Math.max(1.0f, window.getHeight() / 540.0f);
		float k = (float) window.getGuiScale() / pixels;
		float hs = ModuleManager.ACCENT.hudScale.get() / 100f;
		float vw = g.guiWidth() * k / hs;
		float vh = g.guiHeight() * k / hs;
		float pt = delta.getGameTimeDeltaPartialTick(true);
		lastK = k;
		lastScale = hs;
		BOUNDS.clear();

		g.pose().pushMatrix();
		g.pose().scale(hs / k);

		HitFx.render(g, mc, p, vw, vh, pt, dt);
		if (ModuleManager.VISUAL_FX.isEnabled()) visualFx(g, mc, p, vw, vh, pt);
		if (ModuleManager.XRAY.isEnabled()) xray(g, mc, p, pt);
		var ws = ModuleManager.WEAPON_SKINS;
		if (ws.swingTrail.on() && ws.applies(p.getMainHandItem()) && mc.options.getCameraType().isFirstPerson()) swingTrail(g, p, vw, vh, pt);
		if (ModuleManager.BASE_FINDER.isEnabled() && ModuleManager.BASE_FINDER.markers.on()) baseMarkers(g, p, pt);
		if (ModuleManager.ESP_2D.isEnabled()) esp2d(g, mc, p, pt);
		if (ModuleManager.NAMETAGS.isEnabled()) nametags(g, mc, p, pt);
		spawnerNametags(g, mc, p, pt);
		targetHud(g, mc, p, vw, vh, pt);
		if (ModuleManager.WAYPOINTS.isEnabled() && ModuleManager.WAYPOINTS.hud.on()) markers(g, mc, p, vw, vh, pt);

		float right = vw - 8f;
		float y = 8f;
		if (ModuleManager.HUD.isEnabled()) { begin(g, "info"); y = info(g, mc, p, right, y); end(g); }
		if (ModuleManager.WAYPOINTS.isEnabled() && ModuleManager.WAYPOINTS.hud.on()) { begin(g, "waypoints"); y = waypoints(g, mc, p, right, y); end(g); }
		if (ModuleManager.POTION_HUD.isEnabled()) { begin(g, "potions"); y = potions(g, p, right, y); end(g); }
		if (ModuleManager.SESSION_STATS.isEnabled()) { begin(g, "session"); y = sessionStats(g, right, y); end(g); }
		boolean listRight = ModuleManager.MODULE_LIST.side.get().equals("Right");
		if (ModuleManager.MODULE_LIST.isEnabled() && listRight) { begin(g, "modules"); y = moduleList(g, right, y, true); end(g); }

		float ly = 8f;
		if (ModuleManager.WATERMARK.isEnabled()) { begin(g, "watermark"); ly = watermark(g, mc, 8f, ly); end(g); }
		if (ModuleManager.RADAR.isEnabled()) { begin(g, "radar"); ly = radar(g, mc, p, 8f, ly, pt); end(g); }
		if (ModuleManager.MODULE_LIST.isEnabled() && !listRight) { begin(g, "modules"); ly = moduleList(g, 8f, ly, false); end(g); }
		SusChunkModule sus = ModuleManager.SUS_CHUNK;
		if (sus.isEnabled() && sus.hud.on()) { begin(g, "sus"); ly = sus(g, p, sus, 8f, ly); end(g); }
		SoundChunkModule soundChunk = ModuleManager.SOUND_CHUNK;
		if (soundChunk.isEnabled() && soundChunk.hud.on()) { begin(g, "sounds"); ly = sounds(g, p, soundChunk, 8f, ly); end(g); }
		var bf = ModuleManager.BASE_FINDER;
		if (bf.isEnabled() && bf.hud.on()) { begin(g, "bases"); ly = bases(g, p, bf, 8f, ly); end(g); }
		var xr = ModuleManager.XRAY_RESEARCH;
		if (xr.isEnabled() && xr.infoPanel.on()) { begin(g, "xrayres"); ly = xr(g, p, xr, 8f, ly); end(g); }
		var br = ModuleManager.BASE_RESEARCH;
		if (br.isEnabled() && br.dashboard.on()) { begin(g, "baseres"); ly = baseResearch(g, p, br, 8f, ly); end(g); }

		if (ModuleManager.KEYSTROKES.isEnabled()) { begin(g, "keystrokes"); keystrokes(g, mc, 8f, vh - 8f); end(g); }
		if (ModuleManager.ARMOR_HUD.isEnabled()) { begin(g, "armor"); armorHud(g, p, vw, vh, k / hs); end(g); }
		if (ModuleManager.ITEM_COUNTER.isEnabled()) { begin(g, "items"); itemCounter(g, p, vw, vh, k / hs); end(g); }
		if (ModuleManager.POP_COUNTER.isEnabled()) popNotices(g, vw);
		if (ModuleManager.SPAWNER_NOTI.isEnabled()) spawnerNotices(g, vw);
		if (ModuleManager.STATUS_BAR.isEnabled()) { begin(g, "statusbar"); statusBar(g, mc, p, vw); end(g); }
		if (ModuleManager.FREECAM.active() && ModuleManager.FREECAM.speedHud.on()) freecamHint(g, vw, vh);
		if (ModuleManager.SPOTIFY.isEnabled()) { begin(g, "spotify"); spotify(g, mc, vw, vh); end(g); }
		if (ModuleManager.CROSSHAIR.isEnabled() && mc.options.getCameraType().isFirstPerson()) crosshair(g, p, vw, vh);
		if (ModuleManager.LOW_HP.isEnabled()) lowHp(g, p, vw, vh);

		g.pose().popMatrix();

		if (ModuleManager.HUD.isEnabled() && ModuleManager.HUD.armor.on() && !ModuleManager.ARMOR_HUD.isEnabled()) armor(g, mc, p);
	}

	// ------------------------------------------------------------------ Entity-Cache (Performance)

	private static final List<net.minecraft.world.entity.Entity> NEARBY = new ArrayList<>();
	private static long nearbyAt;

	/** Entities in der Nähe, höchstens 20x pro Sekunde neu gesammelt (statt jedes Bild). */
	private static List<net.minecraft.world.entity.Entity> nearby(Minecraft mc) {
		long now = System.currentTimeMillis();
		if (now - nearbyAt > 50L) {
			nearbyAt = now;
			NEARBY.clear();
			for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
				if (e.distanceToSqr(mc.player) < 256.0 * 256.0) NEARBY.add(e);
			}
		}
		NEARBY.removeIf(e -> e.isRemoved());
		return NEARBY;
	}

	// ------------------------------------------------------------------ Verschiebbare Elemente (HUD-Editor)

	/** Zuletzt gezeichnete Rahmen der Elemente (virtuelle Koordinaten, inkl. Verschiebung). */
	public static final Map<String, float[]> BOUNDS = new java.util.LinkedHashMap<>();
	public static float lastK = 1f, lastScale = 1f;
	public static boolean editing;
	private static String curId;
	private static float[] curB;
	private static float[] curOff;

	private static void begin(GuiGraphics g, String id) {
		curId = id;
		curB = null;
		curOff = ModuleManager.ACCENT.offset(id);
		g.pose().pushMatrix();
		g.pose().translate(curOff[0], curOff[1]);
	}

	private static void end(GuiGraphics g) {
		g.pose().popMatrix();
		if (curId != null && curB != null) {
			BOUNDS.put(curId, new float[]{curB[0] + curOff[0], curB[1] + curOff[1], curB[2] + curOff[0], curB[3] + curOff[1]});
		}
		curId = null;
	}

	/** Bereich zum aktuellen Element hinzufügen (für das Ziehen im Editor). */
	private static void mark(float x1, float y1, float x2, float y2) {
		if (curId == null) return;
		if (curB == null) curB = new float[]{x1, y1, x2, y2};
		else {
			curB[0] = Math.min(curB[0], x1);
			curB[1] = Math.min(curB[1], y1);
			curB[2] = Math.max(curB[2], x2);
			curB[3] = Math.max(curB[3], y2);
		}
	}

	// ------------------------------------------------------------------ Bausteine

	private static float anim(Object key, float target, float speed) {
		float v = ANIM.getOrDefault(key, target);
		v = Draw.approach(v, target, speed, dt);
		ANIM.put(key, v);
		return v;
	}

	/** Glas-Panel mit Schatten, feiner Kante und orangem Akzentstrich oben. */
	/** Akzent, der langsam zwischen hell und kräftig hin- und herfließt (pro Panel eigene Phase). */
	private static int flow(float phase) {
		float t = 0.5f + 0.5f * (float) Math.sin(animTime() * 0.9 + phase);
		return ZeroRender.lerp(ORANGE, ORANGE2, t);
	}

	private static float animTime() {
		var a = ModuleManager.ACCENT;
		return a.animated.on() ? time * a.animSpeed.get() / 100f : 0f;
	}

	private static void panel(GuiGraphics g, float x1, float y1, float x2, float y2, float glow) {
		mark(x1, y1, x2, y2);
		boolean alive = ModuleManager.ACCENT.animated.on();
		float t = animTime();
		float phase = (x1 * 0.013f + y1 * 0.021f);
		ZeroRender.glow(g, x1, y1 + 1.5f, x2, y2 + 1.5f, 6f, 7f, 0x40000000);
		if (glow > 0.01f) {
			float pulse = 0.55f + 0.45f * (float) Math.sin(time * 6.0);
			ZeroRender.glow(g, x1, y1, x2, y2, 6f, 6f, ZeroRender.alpha(0xB0F0782C, glow * pulse));
		}
		ZeroRender.gradient(g, x1, y1, x2, y2, 6f, 0xB8141110, 0x980D0B0A, 90f);
		ZeroRender.rect(g, x1 + 3f, y1 + 0.5f, x2 - 3f, y1 + 1.0f, 0.5f, 0x18FFFFFF);
		float rot = alive ? (t * 30f + phase * 90f) % 360f : 80f;
		ZeroRender.strokeGradient(g, x1, y1, x2, y2, 6f, 0.5f,
				ZeroRender.lerp(alive ? ZeroRender.alpha(flow(phase), 0.35f) : 0x28FFFFFF, 0xB0FFA060, glow), 0x08FFFFFF, rot);
	}
	private static float w(ZeroFont f, String s, float size) {
		return f.width(s, size, 0f);
	}

	/** Pfeil (Chevron) in Richtung relativ zur Blickrichtung. 0° = geradeaus. */
	private static void arrow(GuiGraphics g, float cx, float cy, double relDeg, float size, int color) {
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate((float) Math.toRadians(relDeg - 90.0));
		ZeroRender.icon(g, '', 0f, 0f, size, color);
		g.pose().popMatrix();
	}

	private static double relAngle(LocalPlayer p, double tx, double tz) {
		double dx = tx - p.getX();
		double dz = tz - p.getZ();
		double target = Math.toDegrees(Math.atan2(dx, -dz));
		double facing = p.getYRot() + 180.0;
		return ((target - facing) % 360 + 360) % 360;
	}

	// ------------------------------------------------------------------ Spotify + Lyrics

	private static final net.minecraft.resources.Identifier COVER_ID = net.minecraft.resources.Identifier.fromNamespaceAndPath("quiqzy", "spotify/cover");
	private static String coverPath;
	private static boolean coverOk;
	private static int coverColor = 0xFF1ED760;

	private static void loadCover(Minecraft mc, String path) {
		if (java.util.Objects.equals(path, coverPath)) return;
		coverPath = path;
		coverOk = false;
		if (path == null) return;
		try {
			byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path));
			java.awt.image.BufferedImage src = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
			if (src == null) return;
			int s = 64;
			com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(s, s, false);
			int side = Math.min(src.getWidth(), src.getHeight());
			long sr = 0, sg = 0, sb = 0, n = 0;
			int ox = (src.getWidth() - side) / 2, oy = (src.getHeight() - side) / 2;
			for (int y = 0; y < s; y++) for (int x = 0; x < s; x++) {
				int rgb = src.getRGB(ox + x * side / s, oy + y * side / s);
				img.setPixel(x, y, 0xFF000000 | (rgb & 0xFFFFFF));
				sr += (rgb >> 16) & 255;
				sg += (rgb >> 8) & 255;
				sb += rgb & 255;
				n++;
			}
			mc.getTextureManager().register(COVER_ID, new net.minecraft.client.renderer.texture.DynamicTexture(() -> "zero spotify cover", img));
			coverOk = true;
			if (n > 0) {
				// kräftigere Version der Durchschnittsfarbe
				float[] hsv = java.awt.Color.RGBtoHSB((int) (sr / n), (int) (sg / n), (int) (sb / n), null);
				coverColor = 0xFF000000 | (java.awt.Color.HSBtoRGB(hsv[0], Math.min(1f, hsv[1] * 1.4f + 0.15f), Math.max(0.55f, hsv[2])) & 0xFFFFFF);
			}
		} catch (Throwable t) {
			coverOk = false;
		}
	}

	private static String clock(long ms) {
		long s = Math.max(0, ms / 1000);
		return (s / 60) + ":" + String.format("%02d", s % 60);
	}

	private static String clip(ZeroFont f, String s, float size, float max) {
		if (w(f, s, size) <= max) return s;
		String t = s;
		while (t.length() > 1 && w(f, t + "...", size) > max) t = t.substring(0, t.length() - 1);
		return t + "...";
	}

	private static final int SPOTIFY_GREEN = 0xFF1ED760;

	private static void spotify(GuiGraphics g, Minecraft mc, float vw, float vh) {
		var m = ModuleManager.SPOTIFY;
		var t = dev.quiqzy.client.util.MediaInfo.track;
		float fade = anim("sp:vis", t != null && !(m.hidePaused.on() && !t.playing()) ? 1f : 0f, 6f);
		if (fade < 0.01f || t == null) {
			if (editing) {
				panel(g, 8f, vh - 140f, 190f, vh - 92f, 0f);
				ZeroRender.text(g, ZeroFont.BOLD, "Spotify", 18f, vh - 122f, 8f, MUTED);
			}
			return;
		}
		if (m.cover.on()) loadCover(mc, t.cover());
		int accent = m.cover.on() && coverOk ? coverColor : SPOTIFY_GREEN;
		long pos = t.position();
		float x1 = 8f, w = 190f, h = 48f;
		float y1 = vh - 142f, x2 = x1 + w, y2 = y1 + h;

		// Karte: dunkles Glas mit weichem Schein in der Cover-Farbe
		ZeroRender.glow(g, x1, y1 + 2f, x2, y2 + 2f, 12f, 14f, ZeroRender.alpha(0xFF000000, 0.55f * fade));
		ZeroRender.glow(g, x1 - 2f, y1 - 2f, x1 + h + 8f, y2 + 2f, 14f, 18f, ZeroRender.alpha(accent, 0.22f * fade));
		ZeroRender.gradient(g, x1, y1, x2, y2, 12f, ZeroRender.alpha(0xFF141414, 0.9f * fade), ZeroRender.alpha(0xFF0A0A0A, 0.9f * fade), 0f);
		ZeroRender.gradient(g, x1, y1, x2, y2, 12f, ZeroRender.alpha(accent, 0.16f * fade), 0x00000000, 0f);
		ZeroRender.stroke(g, x1, y1, x2, y2, 12f, 0.6f, ZeroRender.alpha(0xFFFFFFFF, 0.08f * fade));

		float cs = h - 12f, cx0 = x1 + 6f, cy0 = y1 + 6f;
		float tx = x1 + 12f;
		if (m.cover.on()) {
			if (coverOk) {
				g.pose().pushMatrix();
				g.pose().translate(cx0, cy0);
				g.pose().scale(cs / 64f, cs / 64f);
				g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, COVER_ID, 0, 0, 0f, 0f, 64, 64, 64, 64);
				g.pose().popMatrix();
				// abgerundete Ecken: dunkler Rahmen drüber
				ZeroRender.stroke(g, cx0 - 1f, cy0 - 1f, cx0 + cs + 1f, cy0 + cs + 1f, 7f, 2.2f, ZeroRender.alpha(0xFF101010, fade));
			} else {
				ZeroRender.gradient(g, cx0, cy0, cx0 + cs, cy0 + cs, 7f, SPOTIFY_GREEN, 0xFF0E8A3C, 45f);
			}
			tx = cx0 + cs + 9f;
		}
		// Spotify-Logo-Punkt + animierter Equalizer oben rechts
		float ex = x2 - 10f;
		for (int b = 0; b < 3; b++) {
			float amp = t.playing() ? 0.5f + 0.5f * (float) Math.sin(time * (5.0 + b) + b * 1.9) : 0.15f;
			float bh = 2f + 6f * amp;
			float bx = ex - (2 - b) * 3f;
			ZeroRender.rect(g, bx - 1f, y1 + 15f - bh, bx + 1f, y1 + 15f, 0.8f, ZeroRender.alpha(accent, fade));
		}
		float maxW = ex - 12f - tx;
		ZeroRender.text(g, ZeroFont.BOLD, clip(ZeroFont.BOLD, t.title(), 8.4f, maxW), tx, y1 + 7f, 8.4f, ZeroRender.alpha(TEXT, fade));
		ZeroRender.text(g, ZeroFont.REGULAR, clip(ZeroFont.REGULAR, t.artist(), 6.6f, maxW), tx, y1 + 18f, 6.6f, ZeroRender.alpha(MUTED, fade));
		// Fortschritt mit Knopf
		float frac = t.durMs() > 0 ? Math.min(1f, pos / (float) t.durMs()) : 0f;
		float bx1 = tx, bx2 = x2 - 10f, by = y1 + 33f;
		ZeroRender.rect(g, bx1, by, bx2, by + 2.4f, 1.2f, ZeroRender.alpha(0xFFFFFFFF, 0.14f * fade));
		float kx = bx1 + (bx2 - bx1) * frac;
		ZeroRender.gradient(g, bx1, by, kx, by + 2.4f, 1.2f, ZeroRender.alpha(accent, fade), ZeroRender.alpha(ZeroRender.lerp(accent, 0xFFFFFFFF, 0.35f), fade), 0f);
		ZeroRender.glow(g, kx - 3f, by - 2f, kx + 3f, by + 4.4f, 3f, 3f, ZeroRender.alpha(accent, 0.6f * fade));
		ZeroRender.circle(g, kx, by + 1.2f, 2.6f, ZeroRender.alpha(0xFFFFFFFF, fade));
		ZeroRender.text(g, ZeroFont.BOLD, clock(pos), bx1, by + 4.6f, 5f, ZeroRender.alpha(MUTED, fade));
		ZeroRender.textRight(g, ZeroFont.BOLD, t.playing() ? clock(t.durMs()) : "PAUSED", bx2, by + 4.6f, 5f, ZeroRender.alpha(t.playing() ? MUTED : accent, fade));
		mark(x1, y1, x2, y2);

		if (m.lyrics.on()) lyrics(g, m, t, pos, vw, vh, x1, w, y2, fade);
	}

	/** Lyrics im Karaoke-Stil: Wörter kommen nacheinander animiert rein, Gesungenes wird grün. */
	private static void lyrics(GuiGraphics g, dev.quiqzy.client.module.misc.SpotifyModule m, dev.quiqzy.client.util.MediaInfo.Track t,
			long pos, float vw, float vh, float wx, float ww, float wy2, float fade) {
		var lines = dev.quiqzy.client.util.MediaInfo.lyrics;
		float sz = 11f * m.lyricsSize.get() / 100f;
		boolean under = m.lyricsPos.get().equals("Under Widget");
		float ly = switch (m.lyricsPos.get()) {
			case "Top" -> 34f;
			case "Under Widget" -> wy2 + 8f;
			default -> (g.guiHeight() - 64f) * (vh / g.guiHeight()) - sz * 2.6f;
		};
		float cx = under ? wx + ww / 2f : vw / 2f;
		float maxLine = under ? ww + 80f : vw * 0.62f;
		if (lines.isEmpty()) {
			String st = dev.quiqzy.client.util.MediaInfo.lyricsState;
			if (!st.isEmpty()) ZeroRender.textCentered(g, ZeroFont.REGULAR, st, cx, ly, sz * 0.6f, ZeroRender.alpha(MUTED, 0.8f * fade));
			return;
		}
		long lp = pos + m.offset.get();
		int idx = dev.quiqzy.client.util.MediaInfo.currentLine(lp);
		if (idx < 0) {
			// vor der ersten Zeile: kleine "..."-Vorschau
			if (lines.get(0).timeMs() - lp < 4000) {
				float a = 0.5f + 0.5f * (float) Math.sin(time * 4.0);
				ZeroRender.textCentered(g, ZeroFont.BOLD, "• • •", cx, ly, sz * 0.8f, ZeroRender.alpha(MUTED, a * fade));
			}
			return;
		}
		var line = lines.get(idx);
		long start = line.timeMs();
		long end = idx + 1 < lines.size() ? lines.get(idx + 1).timeMs() : start + 4000;
		// Singzeit = 90 % des Abstands (am Ende meist Pause), höchstens 7 s
		long sing = Math.max(400, Math.min(7000, (long) ((end - start) * 0.9)));
		String[] words = line.text().split("\\s+");
		// Zeit pro Wort nach Länge verteilen
		float[] wStart = new float[words.length];
		float total = 0f;
		for (String wd : words) total += 1.5f + wd.length();
		float acc = 0f;
		for (int i = 0; i < words.length; i++) {
			wStart[i] = acc / total;
			acc += 1.5f + words[i].length();
		}
		float lineT = (lp - start) / (float) sing; // 0..1 über die Zeile
		float sinceStart = (lp - start) / 1000f;

		// in Zeilen umbrechen (maxLine)
		float space = w(ZeroFont.BOLD, " ", sz);
		java.util.List<int[]> rows = new java.util.ArrayList<>(); // {von, bis}
		float rowW = 0f;
		int from = 0;
		for (int i = 0; i < words.length; i++) {
			float ww2 = w(ZeroFont.BOLD, words[i], sz);
			if (i > from && rowW + space + ww2 > maxLine) {
				rows.add(new int[]{from, i});
				from = i;
				rowW = 0f;
			}
			rowW += (i > from ? space : 0f) + ww2;
		}
		rows.add(new int[]{from, words.length});

		float rowH = sz + 3f;
		float blockH = rows.size() * rowH;
		// dunkler, weicher Hintergrund hinter dem Text
		float widest = 0f;
		for (int[] r : rows) {
			float rw = 0f;
			for (int i = r[0]; i < r[1]; i++) rw += (i > r[0] ? space : 0f) + w(ZeroFont.BOLD, words[i], sz);
			widest = Math.max(widest, rw);
		}
		ZeroRender.glow(g, cx - widest / 2f - 16f, ly - 5f, cx + widest / 2f + 16f, ly + blockH + 3f, 14f, 16f, ZeroRender.alpha(0xC0000000, 0.75f * fade));

		float yy = ly;
		for (int[] r : rows) {
			float rw = 0f;
			for (int i = r[0]; i < r[1]; i++) rw += (i > r[0] ? space : 0f) + w(ZeroFont.BOLD, words[i], sz);
			float xx = cx - rw / 2f;
			for (int i = r[0]; i < r[1]; i++) {
				String wd = words[i];
				float wwid = w(ZeroFont.BOLD, wd, sz);
				// Reinkommen: jedes Wort kurz nacheinander (gestaffelt), gleitet von unten hoch und blendet ein
				float appear = Math.max(0f, Math.min(1f, (sinceStart - i * 0.045f) / 0.22f));
				float ease = 1f - (1f - appear) * (1f - appear) * (1f - appear);
				// Karaoke: Wort wird grün, wenn es dran ist (weicher Übergang über die Wortlänge)
				float wEnd = i + 1 < words.length ? wStart[i + 1] : 1f;
				float sung = Math.max(0f, Math.min(1f, (lineT - wStart[i]) / Math.max(0.0001f, wEnd - wStart[i])));
				int col = ZeroRender.lerp(0xFFFFFFFF, SPOTIFY_GREEN, sung);
				float lift = (1f - ease) * 6f;
				// aktives Wort leicht hervorheben
				boolean active = sung > 0f && sung < 1f;
				if (active) ZeroRender.glow(g, xx - 2f, yy - 1f + lift, xx + wwid + 2f, yy + sz + 1f + lift, 4f, 6f, ZeroRender.alpha(SPOTIFY_GREEN, 0.25f * fade));
				ZeroRender.text(g, ZeroFont.BOLD, wd, xx, yy + lift, sz, ZeroRender.alpha(col, ease * fade));
				xx += wwid + space;
			}
			yy += rowH;
		}
		// nächste Zeile klein darunter
		if (idx + 1 < lines.size()) {
			String nx = clip(ZeroFont.REGULAR, lines.get(idx + 1).text(), sz * 0.66f, maxLine);
			ZeroRender.textCentered(g, ZeroFont.REGULAR, nx, cx, yy + 2f, sz * 0.66f, ZeroRender.alpha(MUTED, 0.7f * fade));
		}
	}

	// ------------------------------------------------------------------ Base Finder

	private static float bases(GuiGraphics g, LocalPlayer p, dev.quiqzy.client.module.world.BaseFinderModule m, float x1, float y) {
		var list = m.bases();
		float fl = anim("bf:flash", System.currentTimeMillis() - m.lastFoundAt < 3000 ? 1f : 0f, 6f);
		int lines = Math.max(1, Math.min(4, list.size()));
		float w = 196f, rowH = 13f;
		float y2 = y + 24f + lines * rowH + 2f;
		panel(g, x1, y, x1 + w, y2, fl);
		ZeroRender.icon(g, '\ue115', x1 + 13f, y + 11.5f, 8.5f, ORANGE);
		ZeroRender.text(g, ZeroFont.BOLD, "Base Finder", x1 + 22f, y + 7f, 8.2f, TEXT);
		String n = String.valueOf(list.size());
		float cw = w(ZeroFont.BOLD, n, 7f) + 11f;
		ZeroRender.gradient(g, x1 + w - 9f - cw, y + 6f, x1 + w - 9f, y + 17f, 5.5f, list.isEmpty() ? 0x30FFFFFF : ORANGE, list.isEmpty() ? 0x20FFFFFF : ORANGE2, 20f);
		ZeroRender.textCentered(g, ZeroFont.BOLD, n, x1 + w - 9f - cw / 2f, y + 8.2f, 7f, list.isEmpty() ? MUTED : 0xFF1E120A);
		ZeroRender.rect(g, x1 + 10f, y + 20f, x1 + w - 10f, y + 20.6f, 0f, 0x18FFFFFF);
		float ty = y + 25f;
		if (list.isEmpty()) {
			ZeroRender.text(g, ZeroFont.REGULAR, "No bases in loaded chunks", x1 + 11f, ty, 7.6f, MUTED);
		} else {
			for (int i = 0; i < Math.min(4, list.size()); i++) {
				var r = list.get(i);
				double cx = r.pos().getMiddleBlockX() + 0.5, cz = r.pos().getMiddleBlockZ() + 0.5;
				double dx = cx - p.getX(), dz = cz - p.getZ();
				int dist = (int) Math.sqrt(dx * dx + dz * dz);
				arrow(g, x1 + 14f, ty + 5f, relAngle(p, cx, cz), 8f, ORANGE);
				ZeroRender.text(g, ZeroFont.BOLD, dist + "m", x1 + 23f, ty, 7.6f, TEXT);
				// Punktzahl als kleiner Bogen
				float sc = Math.min(1f, r.score() / 200f);
				ZeroRender.ring(g, x1 + 62f, ty + 4.6f, 3.6f, 1.2f, 0x26FFFFFF);
				ZeroRender.arc(g, x1 + 62f, ty + 4.6f, 3.6f, 1.2f, -90f, 360f * sc, ORANGE2, ORANGE);
				ZeroRender.text(g, ZeroFont.BOLD, String.valueOf(r.score()), x1 + 69f, ty + 0.6f, 6.6f, ORANGE);
				ZeroRender.textRight(g, ZeroFont.REGULAR, r.reason(), x1 + w - 10f, ty + 0.6f, 6.6f, MUTED);
				ty += rowH;
			}
		}
		return y2 + 7f;
	}

	/** Markierung in der Welt: leuchtende Säule über dem Fund + Ring + Schild mit Punktzahl. */
	private static void baseMarkers(GuiGraphics g, LocalPlayer p, float pt) {
		var list = ModuleManager.BASE_FINDER.bases();
		for (int i = 0; i < Math.min(8, list.size()); i++) {
			var r = list.get(i);
			double cx = r.pos().getMiddleBlockX() + 0.5, cz = r.pos().getMiddleBlockZ() + 0.5;
			float[] bot = HitFx.screen(cx, r.y(), cz);
			float[] top = HitFx.screen(cx, r.y() + 30, cz);
			if (bot == null || top == null) continue;
			float pulse = 0.6f + 0.4f * (float) Math.sin(time * 2.5 + i);
			HitFx.line(g, bot[0], bot[1], top[0], top[1], Math.max(1.2f, Math.min(5f, 0.25f * bot[2])), ZeroRender.alpha(0xFFF0782C, 0.35f * pulse));
			HitFx.line(g, bot[0], bot[1], top[0], top[1], Math.max(0.6f, Math.min(2f, 0.08f * bot[2])), ZeroRender.alpha(0xFFFFD0A0, 0.8f));
			worldRing(g, cx, r.y(), cz, 2.5, 0xFFF0782C, 0.8f, 0.6f, time * 1.2f, 32);
			String label = "Base · " + r.score();
			float tw = w(ZeroFont.BOLD, label, 7.4f);
			float lx = top[0], ly = top[1] - 14f;
			ZeroRender.rect(g, lx - tw / 2f - 7f, ly, lx + tw / 2f + 7f, ly + 13f, 6.5f, 0xD8120E0C);
			ZeroRender.stroke(g, lx - tw / 2f - 7f, ly, lx + tw / 2f + 7f, ly + 13f, 6.5f, 0.6f, 0xA0FFB26E);
			ZeroRender.textCentered(g, ZeroFont.BOLD, label, lx, ly + 2.8f, 7.4f, TEXT);
		}
	}

	// ------------------------------------------------------------------ Swing Trail

	/** Halbtransparente Bewegungsspur der Klinge beim Schlagen (rechts im Bild). */
	private static void swingTrail(GuiGraphics g, LocalPlayer p, float vw, float vh, float pt) {
		float a = p.getAttackAnim(pt);
		float v = anim("swing", a > 0.01f && a < 0.95f ? 1f : 0f, a > 0.01f ? 30f : 6f);
		if (v < 0.01f) return;
		float cx = vw * 0.78f, cy = vh * 0.9f, r = vh * 0.42f;
		float sweep = 40f + 120f * Math.min(1f, a * 1.3f);
		int[] pal = ModuleManager.HIT_EFFECTS.palette();
		for (int i = 0; i < 6; i++) {
			float rr = r * (0.72f + i * 0.055f);
			float al = v * (0.10f + 0.05f * (5 - Math.abs(i - 3)));
			ZeroRender.arc(g, cx, cy, rr, r * 0.05f, -160f, sweep, ZeroRender.alpha(0xFFFFFFFF, 0f), ZeroRender.alpha(i % 2 == 0 ? 0xFFE8F0FF : pal[0], al));
		}
	}

	// ------------------------------------------------------------------ X-Ray Skelette

	private static void bone(GuiGraphics g, Vec3 a, Vec3 b, float w, int col) {
		float[] s1 = HitFx.screen(a.x, a.y, a.z), s2 = HitFx.screen(b.x, b.y, b.z);
		if (s1 == null || s2 == null) return;
		float ww = Math.max(0.8f, Math.min(4f, w * s1[2]));
		HitFx.line(g, s1[0], s1[1], s2[0], s2[1], ww * 3.2f, ZeroRender.alpha(col, 0.22f));
		HitFx.line(g, s1[0], s1[1], s2[0], s2[1], ww, 0xF0FFFFFF);
		ZeroRender.circle(g, s1[0], s1[1], ww * 0.75f, 0xF0FFFFFF);
		ZeroRender.circle(g, s2[0], s2[1], ww * 0.75f, 0xF0FFFFFF);
	}

	private static void xray(GuiGraphics g, Minecraft mc, LocalPlayer p, float pt) {
		var m = ModuleManager.XRAY;
		int col = ModuleManager.HIT_EFFECTS.palette()[0];
		double r2 = (double) m.range.get() * m.range.get();
		boolean fp = mc.options.getCameraType().isFirstPerson() && !ModuleManager.FREECAM.active();
		for (net.minecraft.world.entity.Entity e : nearby(mc)) {
			if (!(e instanceof net.minecraft.world.entity.LivingEntity le) || !le.isAlive()) continue;
			boolean isPlayer = e instanceof net.minecraft.world.entity.player.Player;
			if (e == p ? (!m.self.on() || fp) : (isPlayer ? !m.players.on() : !(m.mobs.on() && e instanceof net.minecraft.world.entity.Mob))) continue;
			if (e.distanceToSqr(p) > r2) continue;
			double s = le.getBbHeight() / 1.8;
			Vec3 o = e.getPosition(pt);
			double yaw = Math.toRadians(net.minecraft.util.Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot));
			// Blickrichtung (vorne) und Seite des Körpers
			double fx = -Math.sin(yaw), fz = Math.cos(yaw), sx = Math.cos(yaw), sz = Math.sin(yaw);
			float pos = le.walkAnimation.position(pt), spd = Math.min(1f, le.walkAnimation.speed(pt));
			double swing = Math.cos(pos * 0.6662) * 1.2 * spd;
			java.util.function.BiFunction<Double, Double[], Vec3> at = (h, off) -> o.add(sx * off[0] * s + fx * off[1] * s, h * s, sz * off[0] * s + fz * off[1] * s);
			Vec3 pelvis = at.apply(0.78, new Double[]{0.0, 0.0}), neck = at.apply(1.42, new Double[]{0.0, 0.0});
			Vec3 head = at.apply(1.72, new Double[]{0.0, 0.0});
			bone(g, pelvis, neck, 0.05f, col);
			// Rippen
			for (int i = 0; i < 3; i++) {
				double h = 1.12 + i * 0.1;
				bone(g, at.apply(h, new Double[]{-0.18 + i * 0.02, 0.0}), at.apply(h, new Double[]{0.18 - i * 0.02, 0.0}), 0.025f, col);
			}
			bone(g, at.apply(1.42, new Double[]{-0.3, 0.0}), at.apply(1.42, new Double[]{0.3, 0.0}), 0.04f, col);
			bone(g, at.apply(0.78, new Double[]{-0.14, 0.0}), at.apply(0.78, new Double[]{0.14, 0.0}), 0.04f, col);
			// Beine (gegenläufig) und Arme
			for (int side = -1; side <= 1; side += 2) {
				double a = swing * side;
				Vec3 hip = at.apply(0.78, new Double[]{0.12 * side, 0.0});
				Vec3 foot = at.apply(0.78 - Math.cos(a) * 0.76, new Double[]{0.12 * side, Math.sin(a) * 0.76});
				Vec3 knee = at.apply(0.78 - Math.cos(a) * 0.38, new Double[]{0.12 * side, Math.sin(a) * 0.38 + 0.03});
				bone(g, hip, knee, 0.04f, col);
				bone(g, knee, foot, 0.04f, col);
				double b = -swing * side * 0.9;
				Vec3 sh = at.apply(1.42, new Double[]{0.3 * side, 0.0});
				Vec3 elbow = at.apply(1.42 - Math.cos(b) * 0.33, new Double[]{0.33 * side, Math.sin(b) * 0.33});
				Vec3 hand = at.apply(1.42 - Math.cos(b) * 0.66, new Double[]{0.34 * side, Math.sin(b) * 0.66});
				bone(g, sh, elbow, 0.035f, col);
				bone(g, elbow, hand, 0.035f, col);
			}
			// Schädel
			float[] hs = HitFx.screen(head.x, head.y, head.z);
			if (hs != null) {
				float hr = (float) (0.2 * s * hs[2]);
				ZeroRender.glow(g, hs[0] - hr * 1.6f, hs[1] - hr * 1.6f, hs[0] + hr * 1.6f, hs[1] + hr * 1.6f, hr * 1.6f, hr, ZeroRender.alpha(col, 0.35f));
				ZeroRender.ring(g, hs[0], hs[1], hr, Math.max(0.8f, hr * 0.22f), 0xF0FFFFFF);
				ZeroRender.circle(g, hs[0] - hr * 0.35f, hs[1] - hr * 0.05f, hr * 0.18f, 0xF0FFFFFF);
				ZeroRender.circle(g, hs[0] + hr * 0.35f, hs[1] - hr * 0.05f, hr * 0.18f, 0xF0FFFFFF);
			}
		}
	}

	// ------------------------------------------------------------------ Visual FX

	/** Kreis in der Welt (waagerecht) als leuchtende Punktkette auf den Bildschirm. */
	private static void worldRing(GuiGraphics g, double cx, double cy, double cz, double radius, int color, float alpha, float dotSize, float spin, int n) {
		for (int i = 0; i < n; i++) {
			double a = spin + i / (double) n * Math.PI * 2;
			float[] s = HitFx.screen(cx + Math.cos(a) * radius, cy, cz + Math.sin(a) * radius);
			if (s == null) continue;
			float r = Math.max(0.6f, Math.min(3.2f, dotSize * 0.05f * s[2]));
			// heller Kopf, der um den Ring läuft
			float head = (float) Math.pow(0.5 + 0.5 * Math.cos(a - spin * 3.0), 6.0);
			ZeroRender.glow(g, s[0] - r * 2.2f, s[1] - r * 2.2f, s[0] + r * 2.2f, s[1] + r * 2.2f, r * 2.2f, r * 2f,
					ZeroRender.alpha(color, alpha * (0.25f + 0.45f * head)));
			ZeroRender.circle(g, s[0], s[1], r * (0.8f + 0.5f * head), ZeroRender.alpha(ZeroRender.lerp(color, 0xFFFFFFFF, head * 0.6f), alpha));
		}
	}

	private static void visualFx(GuiGraphics g, Minecraft mc, LocalPlayer p, float vw, float vh, float pt) {
		var m = ModuleManager.VISUAL_FX;
		int[] pal = ModuleManager.HIT_EFFECTS.palette();
		float t = time;
		// Target Ring: schwebt am Körper auf und ab
		if (m.targetRing.on() && m.target != null && !m.target.isRemoved()) {
			Vec3 pos = m.target.getPosition(pt);
			double h = m.target.getBbHeight();
			double y = pos.y + h * (0.5 + 0.4 * Math.sin(t * 2.2));
			double r = m.target.getBbWidth() * 0.95 + 0.15;
			float a = anim("vfx:ring", 1f, 8f);
			worldRing(g, pos.x, y, pos.z, r, pal[1], a, 0.75f, t * 2.4f, 60);
		} else {
			anim("vfx:ring", 0f, 8f);
		}
		// Jump Circles: Ring am Boden, wächst und verblasst
		long now = System.currentTimeMillis();
		for (var c : m.circles) {
			float k = (now - c.born()) / 900f;
			if (k > 1f) continue;
			float e = 1f - (1f - k) * (1f - k) * (1f - k);
			worldRing(g, c.pos().x, c.pos().y + 0.02, c.pos().z, 0.25 + e * 1.1, pal[0], (1f - k) * 0.9f, 0.9f, 0f, 28);
		}
		// Halo über deinem Kopf (nur sichtbar, wenn die Kamera nicht in dir ist)
		if (m.halo.on() && !mc.options.getCameraType().isFirstPerson()) {
			Vec3 pos = p.getPosition(pt);
			double y = pos.y + p.getBbHeight() + 0.35 + 0.05 * Math.sin(t * 2.0);
			// weicher Schein + dichter goldener Ring
			float[] c = HitFx.screen(pos.x, y, pos.z);
			if (c != null) {
				float gr = (float) (0.42 * c[2]);
				ZeroRender.glow(g, c[0] - gr, c[1] - gr * 0.35f, c[0] + gr, c[1] + gr * 0.35f, gr * 0.35f, gr * 0.5f, 0x55FFD27A);
			}
			worldRing(g, pos.x, y, pos.z, 0.36, 0xFFFFE7A0, 1f, 1.0f, t * 1.5f, 48);
		}
		// Kill Screen: kurzes Grau + Schriftzug
		float since = (now - m.killAt) / 1000f;
		if (m.killScreen.on() && m.killAt > 0 && since < 2.2f) {
			float in = Math.min(1f, since / 0.15f);
			float out = since > 1.6f ? 1f - (since - 1.6f) / 0.6f : 1f;
			float a = in * out;
			ZeroRender.rect(g, 0f, 0f, vw, vh, 0f, ZeroRender.alpha(0xFF1A1A1C, 0.55f * a));
			HitFx.edges(g, vw, vh, ZeroRender.alpha(0xFF000000, 0.7f * a), 120f);
			float pop = 1f + 0.25f * (1f - Math.min(1f, since / 0.25f));
			float size = 26f * pop;
			String txt = "ELIMINATED";
			float tw = ZeroFont.BOLD.width(txt, size, 1.2f);
			float ty = vh * 0.36f;
			ZeroRender.glow(g, vw / 2f - tw / 2f - 20f, ty - 6f, vw / 2f + tw / 2f + 20f, ty + size + 6f, 20f, 26f, ZeroRender.alpha(pal[1], 0.35f * a));
			ZeroRender.text(g, ZeroFont.BOLD, txt, vw / 2f - tw / 2f, ty, size, 1.2f, ZeroRender.alpha(pal[0], a), ZeroRender.alpha(pal[1], a));
			ZeroRender.textCentered(g, ZeroFont.BOLD, m.killName, vw / 2f, ty + size + 6f, 9f, ZeroRender.alpha(TEXT, a));
		}
	}

	// ------------------------------------------------------------------ 2D ESP + Health Bars

	/** Bildschirm-Rechteck {minX, minY, maxX, maxY} der Hitbox oder null (hinter der Kamera). */
	private static float[] screenBox(net.minecraft.world.entity.Entity e, float pt) {
		Vec3 pos = e.getPosition(pt);
		double hw = e.getBbWidth() / 2.0, h = e.getBbHeight();
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			double x = pos.x + ((i & 1) == 0 ? -hw : hw);
			double y = pos.y + ((i & 2) == 0 ? 0 : h);
			double z = pos.z + ((i & 4) == 0 ? -hw : hw);
			float[] s = HitFx.screen(x, y, z);
			if (s == null) return null;
			minX = Math.min(minX, s[0]);
			maxX = Math.max(maxX, s[0]);
			minY = Math.min(minY, s[1]);
			maxY = Math.max(maxY, s[1]);
		}
		return new float[]{minX, minY, maxX, maxY};
	}

	private static void esp2d(GuiGraphics g, Minecraft mc, LocalPlayer p, float pt) {
		dev.quiqzy.client.module.visuals.Esp2DModule m = ModuleManager.ESP_2D;
		int[] pal = ModuleManager.HIT_EFFECTS.palette();
		double r2 = (double) m.range.get() * m.range.get();
		for (net.minecraft.world.entity.Entity e : nearby(mc)) {
			if (e == p || !(e instanceof net.minecraft.world.entity.LivingEntity le) || !le.isAlive()) continue;
			boolean isPlayer = e instanceof net.minecraft.world.entity.player.Player;
			boolean hostile = e instanceof net.minecraft.world.entity.monster.Enemy;
			if (isPlayer ? !m.players.on() : hostile ? !m.hostiles.on() : !m.animals.on()) continue;
			if (e.distanceToSqr(p) > r2) continue;
			float[] b = screenBox(e, pt);
			if (b == null) continue;
			int col = isPlayer ? pal[1] : hostile ? 0xFFFF5A5A : 0xFF7BE07B;
			float w = b[2] - b[0], h = b[3] - b[1];
			if (w < 2f || h < 2f) continue;
			if (m.style.get().equals("Full Box")) {
				ZeroRender.stroke(g, b[0] - 0.6f, b[1] - 0.6f, b[2] + 0.6f, b[3] + 0.6f, 1.5f, 2.2f, 0x90000000);
				ZeroRender.stroke(g, b[0], b[1], b[2], b[3], 1.2f, 1.0f, col);
			} else {
				float cl = Math.min(w, h) * 0.28f;
				float t = 1.2f;
				float[][] seg = {
						{b[0], b[1], b[0] + cl, b[1] + t}, {b[0], b[1], b[0] + t, b[1] + cl},
						{b[2] - cl, b[1], b[2], b[1] + t}, {b[2] - t, b[1], b[2], b[1] + cl},
						{b[0], b[3] - t, b[0] + cl, b[3]}, {b[0], b[3] - cl, b[0] + t, b[3]},
						{b[2] - cl, b[3] - t, b[2], b[3]}, {b[2] - t, b[3] - cl, b[2], b[3]}};
				for (float[] sgm : seg) {
					ZeroRender.rect(g, sgm[0] - 0.6f, sgm[1] - 0.6f, sgm[2] + 0.6f, sgm[3] + 0.6f, 0.6f, 0x90000000);
					ZeroRender.rect(g, sgm[0], sgm[1], sgm[2], sgm[3], 0.4f, col);
				}
			}
			if (m.healthBar.on()) {
				float frac = Math.max(0f, Math.min(1f, le.getHealth() / Math.max(1f, le.getMaxHealth())));
				float shown = anim("e2d:" + e.getId(), frac, 10f);
				float bx2 = b[0] - 2.5f, bx1 = bx2 - 2.2f;
				ZeroRender.rect(g, bx1 - 0.6f, b[1] - 0.6f, bx2 + 0.6f, b[3] + 0.6f, 1f, 0xB0000000);
				int hc = shown > 0.5f ? ZeroRender.lerp(0xFFF2C14E, 0xFF7BE07B, (shown - 0.5f) * 2f) : ZeroRender.lerp(0xFFFF4A4A, 0xFFF2C14E, shown * 2f);
				ZeroRender.rect(g, bx1, b[3] - (b[3] - b[1]) * shown, bx2, b[3], 1f, hc);
			}
		}
	}

	// ------------------------------------------------------------------ Nametags

		private static List<String> getEnchTags(ItemStack st) {
		var enchs = st.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
		if (enchs == null || enchs.isEmpty()) return java.util.Collections.emptyList();
		List<String> list = new ArrayList<>();
		for (var entry : enchs.entrySet()) {
			int lvl = entry.getIntValue();
			String raw = entry.getKey().getRegisteredName();
			if (raw == null) continue;
			String id = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;
			String tag = switch (id) {
				case "protection" -> "P" + lvl;
				case "blast_protection" -> "BP" + lvl;
				case "projectile_protection" -> "PP" + lvl;
				case "fire_protection" -> "FP" + lvl;
				case "feather_falling" -> "FF" + lvl;
				case "respiration" -> "R" + lvl;
				case "aqua_affinity" -> "AA";
				case "thorns" -> "Th" + lvl;
				case "depth_strider" -> "DS" + lvl;
				case "frost_walker" -> "FW" + lvl;
				case "soul_speed" -> "SS" + lvl;
				case "swift_sneak" -> "SwS" + lvl;
				case "sharpness" -> "S" + lvl;
				case "smite" -> "Sm" + lvl;
				case "bane_of_arthropods" -> "BA" + lvl;
				case "knockback" -> "Kb" + lvl;
				case "fire_aspect" -> "FA" + lvl;
				case "looting" -> "L" + lvl;
				case "sweeping_edge" -> "SE" + lvl;
				case "unbreaking" -> "U" + lvl;
				case "mending" -> "M";
				case "efficiency" -> "E" + lvl;
				case "silk_touch" -> "ST";
				case "fortune" -> "F" + lvl;
				case "power" -> "Pw" + lvl;
				case "punch" -> "Pn" + lvl;
				case "flame" -> "Fl";
				case "infinity" -> "Inf";
				case "loyalty" -> "Loy" + lvl;
				case "channeling" -> "Ch";
				case "riptide" -> "Rip" + lvl;
				case "impaling" -> "Imp" + lvl;
				case "multishot" -> "MS";
				case "quick_charge" -> "QC" + lvl;
				case "piercing" -> "Prc" + lvl;
				case "density" -> "Den" + lvl;
				case "breach" -> "Br" + lvl;
				case "wind_burst" -> "WB" + lvl;
				default -> id.length() > 2 ? Character.toUpperCase(id.charAt(0)) + String.valueOf(lvl) : id;
			};
			list.add(tag);
			if (list.size() >= 3) break;
		}
		return list;
	}

	private static void nametags(GuiGraphics g, Minecraft mc, LocalPlayer p, float pt) {
		dev.quiqzy.client.module.visuals.NametagsModule m = ModuleManager.NAMETAGS;
		double r2 = (double) m.range.get() * m.range.get();
		float sc = m.scale.get() / 100f;
		int[] pal = ModuleManager.HIT_EFFECTS.palette();
		List<net.minecraft.world.entity.Entity> sorted = new ArrayList<>(nearby(mc));
		sorted.sort((e1, e2) -> Double.compare(e2.distanceToSqr(p), e1.distanceToSqr(p)));
		for (net.minecraft.world.entity.Entity e : sorted) {
			if (e == p || e.distanceToSqr(p) > r2) continue;
			if (e instanceof net.minecraft.world.entity.item.ItemEntity ie) {
				if (!m.items.on()) continue;
				Vec3 pos = e.getPosition(pt);
				float[] s = HitFx.screen(pos.x, pos.y + 0.75, pos.z);
				if (s == null) continue;
				ItemStack st = ie.getItem();
				String label = st.getHoverName().getString() + (st.getCount() > 1 ? " x" + st.getCount() : "");
				float size = 7f * sc;
				float tw = w(ZeroFont.BOLD, label, size);
				float x1 = s[0] - tw / 2f - 6f * sc, x2 = s[0] + tw / 2f + 6f * sc, y1 = s[1] - 13f * sc, y2 = s[1];
				ZeroRender.rect(g, x1, y1, x2, y2, 5f * sc, 0xC8120E0C);
				ZeroRender.stroke(g, x1, y1, x2, y2, 5f * sc, 0.5f, st.getRarity() == net.minecraft.world.item.Rarity.COMMON ? 0x30FFFFFF : 0xA0FFB26E);
				ZeroRender.textCentered(g, ZeroFont.BOLD, label, s[0], y1 + 3f * sc, size, TEXT);
				continue;
			}
			if (!(e instanceof net.minecraft.world.entity.LivingEntity le) || !le.isAlive()) continue;
			boolean isPlayer = e instanceof net.minecraft.world.entity.player.Player;
			if (isPlayer ? !m.players.on() : !m.mobs.on()) continue;
			if (!isPlayer && !(e instanceof net.minecraft.world.entity.Mob)) continue;
			Vec3 pos = e.getPosition(pt);
			float[] s = HitFx.screen(pos.x, pos.y + e.getBbHeight() + 0.35, pos.z);
			if (s == null) continue;

			String name = e.getName().getString();
			float hp = le.getHealth() + le.getAbsorptionAmount();
			String hpTxt = String.format(java.util.Locale.ROOT, hp >= 10 ? "%.0f" : "%.1f", hp) + " ❤";
			String dist = String.format(java.util.Locale.ROOT, "%.0fm", e.distanceTo(p));
			int ping = -1;
			String gmTxt = "";
			if (isPlayer && mc.getConnection() != null) {
				PlayerInfo info = mc.getConnection().getPlayerInfo(e.getUUID());
				if (info != null) {
					ping = info.getLatency();
					if (m.gamemode.on() && info.getGameMode() != null) {
						gmTxt = switch (info.getGameMode()) {
							case SURVIVAL -> "[S]";
							case CREATIVE -> "[C]";
							case ADVENTURE -> "[A]";
							case SPECTATOR -> "[SP]";
						};
					}
				}
			}
			String pingTxt = (m.ping.on() && ping >= 0) ? ping + "ms" : "";
			float size = 7.0f * sc;
			float pingW = !pingTxt.isEmpty() ? w(ZeroFont.REGULAR, pingTxt, size * 0.85f) + 5f * sc : 0f;
			float gmW = !gmTxt.isEmpty() ? w(ZeroFont.REGULAR, gmTxt, size * 0.82f) + 4f * sc : 0f;
			float nameW = w(ZeroFont.BOLD, name, size);
			float hpW = m.health.on() ? w(ZeroFont.BOLD, hpTxt, size) + 5f * sc : 0f;
			float dW = m.distance.on() ? w(ZeroFont.REGULAR, dist, size * 0.88f) + 4f * sc : 0f;
			float total = pingW + gmW + nameW + hpW + dW + 10f * sc;
			float x1 = s[0] - total / 2f, x2 = s[0] + total / 2f;
			float y2 = s[1], y1 = y2 - (m.healthBar.on() ? 13.5f : 12.0f) * sc;
			ZeroRender.glow(g, x1, y1 + 1f, x2, y2 + 1f, 5f * sc, 6f, 0x40000000);
			ZeroRender.rect(g, x1, y1, x2, y2, 4.5f * sc, 0xD8141418);
			ZeroRender.rect(g, x1 + 2f * sc, y1 + 0.5f, x2 - 2f * sc, y1 + 1.0f, 0.5f, 0x18FFFFFF);
			ZeroRender.stroke(g, x1, y1, x2, y2, 4.5f * sc, 0.6f, 0x553F3F46);
			
			float tx = x1 + 4.5f * sc;
			if (!pingTxt.isEmpty()) {
				int pc = ping < 50 ? 0xFF7BE07B : ping < 120 ? 0xFFF2C14E : 0xFFFF5A5A;
				ZeroRender.text(g, ZeroFont.REGULAR, pingTxt, tx, y1 + 3.1f * sc, size * 0.85f, pc);
				tx += pingW;
			}
			if (!gmTxt.isEmpty()) {
				ZeroRender.text(g, ZeroFont.REGULAR, gmTxt, tx, y1 + 3.2f * sc, size * 0.82f, 0xFFA098B0);
				tx += gmW;
			}
			ZeroRender.text(g, ZeroFont.BOLD, name, tx, y1 + 2.8f * sc, size, 0xFFFFFFFF);
			tx += nameW + 5f * sc;
			float frac = Math.max(0f, Math.min(1f, le.getHealth() / Math.max(1f, le.getMaxHealth())));
			if (m.health.on()) {
				int hc = frac > 0.5f ? 0xFF2ECC71 : frac > 0.25f ? 0xFFF1C40F : 0xFFE74C3C;
				ZeroRender.text(g, ZeroFont.BOLD, hpTxt, tx, y1 + 2.8f * sc, size, hc);
				tx += hpW;
			}
			if (m.distance.on()) {
				ZeroRender.text(g, ZeroFont.REGULAR, dist, tx, y1 + 3.1f * sc, size * 0.88f, 0xFF9E95A8);
			}
			
			// CatLean Health Bar am unteren Rand
			if (m.healthBar.on()) {
				float barY = y2 - 1.4f * sc;
				int bcol1 = frac > 0.5f ? 0xFF2ECC71 : frac > 0.25f ? 0xFFF1C40F : 0xFFE74C3C;
				int bcol2 = frac > 0.5f ? 0xFF27AE60 : frac > 0.25f ? 0xFFE67E22 : 0xFFC0392B;
				ZeroRender.rect(g, x1 + 2f * sc, barY, x2 - 2f * sc, y2 - 0.4f * sc, 0.5f, 0x50000000);
				float bw = (total - 4f * sc) * frac;
				if (bw > 0.5f) {
					ZeroRender.gradient(g, x1 + 2f * sc, barY, x1 + 2f * sc + bw, y2 - 0.4f * sc, 0.5f, bcol1, bcol2, 0f);
				}
			}

			if (isPlayer && ModuleManager.POP_COUNTER.isEnabled()) {
				int pops = ModuleManager.POP_COUNTER.pops((net.minecraft.world.entity.player.Player) e);
				if (pops > 0) {
					String pt2 = pops + (pops == 1 ? " pop" : " pops");
					float pw = w(ZeroFont.BOLD, pt2, size * 0.85f) + 10f * sc;
					ZeroRender.gradient(g, x2 + 3f * sc, y1 + 1.5f * sc, x2 + 3f * sc + pw, y2 - 1.5f * sc, 4f * sc, 0xFF71717A, 0xFF3F3F46, 20f);
					ZeroRender.text(g, ZeroFont.BOLD, pt2, x2 + 8f * sc, y1 + 3.8f * sc, size * 0.85f, 0xFFFFFFFF);
				}
			}

			// Ausrüstung als Icons über dem Schild mit Haltbarkeit & Verzauberungs-Badges
			if (m.equipment.on() && isPlayer) {
				List<ItemStack> gear = new ArrayList<>();
				if (!le.getMainHandItem().isEmpty()) gear.add(le.getMainHandItem());
				for (EquipmentSlot slot : ARMOR) {
					ItemStack st = le.getItemBySlot(slot);
					if (!st.isEmpty()) gear.add(st);
				}
				if (!le.getOffhandItem().isEmpty()) gear.add(le.getOffhandItem());
				float icon = 11.5f * sc;
				float gx = s[0] - gear.size() * (icon + 3f * sc) / 2f;
				for (ItemStack st : gear) {
					List<String> enchs = m.enchantments.on() ? getEnchTags(st) : java.util.Collections.emptyList();
					float ey = y1 - icon - 3f * sc - enchs.size() * 5.8f * sc;
					for (String tag : enchs) {
						int tc = tag.startsWith("P") || tag.startsWith("S") ? 0xFFFF6B6B : tag.startsWith("M") ? 0xFF7BE07B : 0xFF7AE0FF;
						ZeroRender.textCentered(g, ZeroFont.BOLD, tag, gx + icon / 2f, ey, 5.2f * sc, tc);
						ey += 5.6f * sc;
					}
					g.pose().pushMatrix();
					g.pose().translate(gx, y1 - icon - 2.5f * sc);
					g.pose().scale(icon / 16f, icon / 16f);
					g.renderItem(st, 0, 0);
					g.renderItemDecorations(mc.font, st, 0, 0);
					g.pose().popMatrix();
					gx += icon + 3f * sc;
				}
			}

			// Aktive Trankeffekte unter dem Nametag (CatLean-Stil)
			if (m.potions.on() && isPlayer) {
				var effects = le.getActiveEffects();
				if (!effects.isEmpty()) {
					List<String> effs = new ArrayList<>();
					List<Integer> effCols = new ArrayList<>();
					for (var eff : effects) {
						String effName = eff.getEffect().value().getDisplayName().getString();
						int durSec = eff.getDuration() / 20;
						String dur = (durSec / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", durSec % 60);
						int amp = eff.getAmplifier();
						effs.add(effName + (amp > 0 ? " " + (amp + 1) : "") + " " + dur);
						effCols.add(0xFF000000 | eff.getEffect().value().getColor());
						if (effs.size() >= 3) break;
					}
					float ey = y2 + 2.5f * sc;
					for (int ei = 0; ei < effs.size(); ei++) {
						String es = effs.get(ei);
						int ec = effCols.get(ei);
						float ew = w(ZeroFont.REGULAR, es, 6.0f * sc);
						float ex1 = s[0] - ew / 2f - 5f * sc, ex2 = s[0] + ew / 2f + 5f * sc;
						ZeroRender.rect(g, ex1, ey, ex2, ey + 8.5f * sc, 3.5f * sc, 0xD00A0B12);
						ZeroRender.stroke(g, ex1, ey, ex2, ey + 8.5f * sc, 3.5f * sc, 0.5f, ZeroRender.alpha(ec, 0.45f));
						ZeroRender.circle(g, ex1 + 3.8f * sc, ey + 4.25f * sc, 1.3f * sc, ec);
						ZeroRender.text(g, ZeroFont.REGULAR, es, ex1 + 6.8f * sc, ey + 1.2f * sc, 6.0f * sc, 0xFFE0D8D0);
						ey += 9.5f * sc;
					}
				}
			}
		}
	}

	
	// ------------------------------------------------------------------ Spawner Nametags

	private static void spawnerNametags(GuiGraphics g, Minecraft mc, LocalPlayer p, float pt) {
		boolean tagModule = ModuleManager.SPAWNER_NAMETAGS.isEnabled();
		boolean vanillaNametagSpawners = ModuleManager.NAMETAGS.isEnabled() && ModuleManager.NAMETAGS.spawners.on();
		if (!tagModule && !vanillaNametagSpawners) return;

		var mod = ModuleManager.SPAWNER_NAMETAGS;
		double maxDist = tagModule ? mod.range.get() : ModuleManager.NAMETAGS.range.get();
		double maxDistSq = maxDist * maxDist;
		float sc = (tagModule ? (mod.scale.get() / 100f) : (ModuleManager.NAMETAGS.scale.get() / 100f));

		boolean showIcon = !tagModule || mod.showIcon.on();
		boolean showDist = !tagModule || mod.showDistance.on();
		boolean showCoords = !tagModule || mod.showCoords.on();

		String dim = WaypointsModule.dimensionId(mc.level);
		for (var entry : SpawnerTracker.getActiveSpawners(dim)) {
			double d2 = p.distanceToSqr(entry.pos.getX() + 0.5, entry.pos.getY() + 0.5, entry.pos.getZ() + 0.5);
			if (d2 > maxDistSq) continue;

			float[] s = HitFx.screen(entry.pos.getX() + 0.5, entry.pos.getY() + 1.25, entry.pos.getZ() + 0.5);
			if (s == null) continue;

			String name = entry.displayName();
			double dist = Math.sqrt(d2);
			String distTxt = String.format(java.util.Locale.ROOT, "%.0fm", dist);
			String coordsTxt = entry.pos.getX() + ", " + entry.pos.getY() + ", " + entry.pos.getZ();

			float size = 7.0f * sc;
			float nameW = w(ZeroFont.BOLD, name, size);
			float iconW = showIcon ? 12f * sc : 0f;
			float dW = showDist ? w(ZeroFont.REGULAR, distTxt, size * 0.9f) + 6f * sc : 0f;
			float cW = showCoords ? w(ZeroFont.REGULAR, coordsTxt, size * 0.8f) + 6f * sc : 0f;

			float total = iconW + nameW + dW + cW + 11f * sc;
			float x1 = s[0] - total / 2f, x2 = s[0] + total / 2f;
			float y2 = s[1], y1 = y2 - 13f * sc;

			ZeroRender.glow(g, x1, y1 + 1f, x2, y2 + 1f, 5f * sc, 6f, 0x40000000);
			ZeroRender.rect(g, x1, y1, x2, y2, 4.5f * sc, 0xD8141418);
			ZeroRender.rect(g, x1 + 2f * sc, y1 + 0.5f, x2 - 2f * sc, y1 + 1.0f, 0.5f, 0x18FFFFFF);
			ZeroRender.stroke(g, x1, y1, x2, y2, 4.5f * sc, 0.6f, 0x553F3F46);

			float tx = x1 + 4.5f * sc;
			if (showIcon && entry.icon != null) {
				g.pose().pushMatrix();
				g.pose().translate(tx, y1 + 1.2f * sc);
				float iconScale = (10.5f * sc) / 16f;
				g.pose().scale(iconScale, iconScale);
				g.renderItem(entry.icon, 0, 0);
				g.pose().popMatrix();
				tx += iconW + 3f * sc;
			}

			ZeroRender.text(g, ZeroFont.BOLD, name, tx, y1 + 3.0f * sc, size, 0xFFFFFFFF);
			tx += nameW + 5f * sc;

			if (showDist) {
				ZeroRender.text(g, ZeroFont.REGULAR, distTxt, tx, y1 + 3.2f * sc, size * 0.88f, 0xFFA1A1AA);
				tx += dW;
			}

			if (showCoords) {
				ZeroRender.text(g, ZeroFont.REGULAR, coordsTxt, tx, y1 + 3.4f * sc, size * 0.8f, 0xFF71717A);
			}

			// Clean bottom indicator line (wie beim Player-Nametag)
			ZeroRender.rect(g, x1 + 2f * sc, y2 - 1.4f * sc, x2 - 2f * sc, y2 - 0.5f * sc, 0.5f, 0x5071717A);
		}
	}

	// ------------------------------------------------------------------ Spawner Noti Popups

	private static void spawnerNotices(GuiGraphics g, float vw) {
		var list = ModuleManager.SPAWNER_NOTI.notices;
		if (list.isEmpty()) return;

		float y = 52f;
		long now = System.currentTimeMillis();
		for (int i = list.size() - 1; i >= 0; i--) {
			var n = list.get(i);
			float age = (now - n.at()) / 1000f;
			float a = Math.min(1f, Math.min(age / 0.2f, (5.0f - age) / 0.6f));
			if (a <= 0f) continue;

			String title = "Spawner gefunden";
			String sub = n.mobName() + " · " + n.pos().getX() + " " + n.pos().getY() + " " + n.pos().getZ() + String.format(java.util.Locale.ROOT, " (%.0fm)", n.dist());

			float tW = w(ZeroFont.BOLD, title, 7.0f);
			float sW = w(ZeroFont.REGULAR, sub, 7.0f);
			float boxW = 22f + tW + 8f + sW + 10f;
			float x1 = vw / 2f - boxW / 2f, x2 = vw / 2f + boxW / 2f;
			float yy = y - (1f - Math.min(1f, age / 0.2f)) * 5f;
			float h = 18f;

			ZeroRender.glow(g, x1, yy, x2, yy + h, 6f, 6f, ZeroRender.alpha(0xFFF0782C, 0.30f * a));
			ZeroRender.rect(g, x1, yy, x2, yy + h, 6f, ZeroRender.alpha(0xC0100D0B, a));
			ZeroRender.rect(g, x1 + 3f, yy + 0.5f, x2 - 3f, yy + 1.0f, 0.5f, ZeroRender.alpha(0x18FFFFFF, a));
			ZeroRender.stroke(g, x1, yy, x2, yy + h, 6f, 0.5f, ZeroRender.alpha(ORANGE, 0.65f * a));

			if (n.icon() != null) {
				g.pose().pushMatrix();
				g.pose().translate(x1 + 4.5f, yy + 2.5f);
				g.pose().scale(13f / 16f, 13f / 16f);
				g.renderItem(n.icon(), 0, 0);
				g.pose().popMatrix();
			}

			ZeroRender.text(g, ZeroFont.BOLD, title, x1 + 22f, yy + 4.6f, 7.0f, ZeroRender.alpha(ORANGE, a));
			ZeroRender.text(g, ZeroFont.REGULAR, sub, x1 + 22f + tW + 6f, yy + 4.6f, 7.0f, ZeroRender.alpha(TEXT, a));

			y += 22f;
		}
	}

	// ------------------------------------------------------------------ Target HUD (neben dem Gegner)

	private static net.minecraft.world.entity.LivingEntity shownTarget;
	private static float tx = Float.NaN, ty = Float.NaN;

	private static void targetHud(GuiGraphics g, Minecraft mc, LocalPlayer p, float vw, float vh, float pt) {
		dev.quiqzy.client.module.misc.TargetHudModule m = ModuleManager.TARGET_HUD;
		net.minecraft.world.entity.LivingEntity t = m.target(mc);
		float vis = anim("th:vis", t != null ? 1f : 0f, t != null ? 14f : 8f);
		if (t != null) shownTarget = t;
		net.minecraft.world.entity.LivingEntity e = shownTarget;
		if (e == null || vis < 0.01f) {
			if (vis < 0.01f) shownTarget = null;
			return;
		}
		int[] pal = ModuleManager.HIT_EFFECTS.palette();
		if (m.ghost.on()) HitFx.ghost(g, e, pt, pal[1]);

		float w = 168f, h = 74f;
		// Standard: fest rechts neben dem Fadenkreuz -> steht beim Zielen direkt neben dem Gegner, wackelt aber nie
		float gx = vw / 2f + 46f;
		float gy = vh / 2f - h / 2f - 6f;
		boolean follow = m.position.get().equals("Follow Target");
		if (m.position.get().equals("Below Crosshair")) {
			gx = vw / 2f - w / 2f;
			gy = vh / 2f + 34f;
		}
		if (follow) {
			Vec3 pos = e.getPosition(pt);
			float[] sp = HitFx.screen(pos.x, pos.y + e.getBbHeight() * 0.85, pos.z);
			if (sp != null) {
				gx = sp[0] + (float) (e.getBbWidth() * 0.5 + 0.45) * sp[2];
				gy = sp[1] - 10f;
			}
		}
		gx = Math.max(8f, Math.min(vw - w - 8f, gx));
		gy = Math.max(8f, Math.min(vh - h - 8f, gy));
		if (Float.isNaN(tx) || vis < 0.05f) {
			tx = gx;
			ty = gy;
		}
		if (follow) {
			// ruhig folgen: kleine Bewegungen ignorieren, große weich nachziehen
			if (Math.abs(gx - tx) > 14f || Math.abs(gy - ty) > 14f) {
				tx = Draw.approach(tx, gx, 6f, dt);
				ty = Draw.approach(ty, gy, 6f, dt);
			}
		} else {
			tx = gx;
			ty = gy;
		}
		float x1 = tx, y1 = ty + (1f - vis) * 6f, x2 = x1 + w, y2 = y1 + h;
		float a = vis;

		// Panel (CatLean Look)
		boolean cat = ModuleManager.ACCENT.isCatLean();
		ZeroRender.glow(g, x1, y1 + 3f, x2, y2 + 3f, 8f, 12f, ZeroRender.alpha(cat ? 0x50A855F7 : 0x90000000, a));
		ZeroRender.gradient(g, x1, y1, x2, y2, 8f, ZeroRender.alpha(cat ? 0xDC0B0C14 : 0xE017120F, a), ZeroRender.alpha(cat ? 0xEA07080E : 0xE00E0B0A, a), 90f);
		ZeroRender.strokeGradient(g, x1, y1, x2, y2, 8f, 0.7f, ZeroRender.alpha(cat ? 0x70A855F7 : 0x50FFA060, a), ZeroRender.alpha(cat ? 0x50D946EF : 0x14FFFFFF, a), 80f);

		// Kopf
		float hx = x1 + 8f, hy = y1 + 8f, hs = 20f;
		ZeroRender.glow(g, hx, hy, hx + hs, hy + hs, 5f, 5f, ZeroRender.alpha(pal[1], 0.35f * a));
		ZeroRender.rect(g, hx - 1f, hy - 1f, hx + hs + 1f, hy + hs + 1f, 5f, ZeroRender.alpha(0xFF2A221C, a));
		if (e instanceof net.minecraft.client.player.AbstractClientPlayer acp) {
			net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, acp.getSkin(), Math.round(hx), Math.round(hy), Math.round(hs));
		} else {
			String n0 = e.getName().getString();
			ZeroRender.textCentered(g, ZeroFont.BOLD, n0.isEmpty() ? "?" : n0.substring(0, 1).toUpperCase(java.util.Locale.ROOT), hx + hs / 2f, hy + 5f, 10f, ZeroRender.alpha(ORANGE, a));
		}

		// Name + Leben
		String name = e.getName().getString();
		if (name.length() > 14) name = name.substring(0, 14) + "...";
		ZeroRender.text(g, ZeroFont.BOLD, name, hx + hs + 7f, y1 + 8.5f, 9f, ZeroRender.alpha(TEXT, a));
		float hp = e.getHealth() + e.getAbsorptionAmount();
		String hpText = String.format(java.util.Locale.ROOT, hp >= 10 ? "%.0f" : "%.1f", hp);
		float hcx = hx + hs + 10f, hcy = y1 + 23.5f;
		ZeroRender.glow(g, hcx - 3.5f, hcy - 3.5f, hcx + 3.5f, hcy + 3.5f, 3.5f, 2.5f, ZeroRender.alpha(pal[1], 0.5f * a));
		ZeroRender.circle(g, hcx, hcy, 2.6f, ZeroRender.alpha(pal[0], a));
		ZeroRender.text(g, ZeroFont.BOLD, hpText, hcx + 6f, y1 + 19.5f, 8f, ZeroRender.alpha(TEXT, a));

		// Waffe + Entfernung rechts
		String wpn = dev.quiqzy.client.module.misc.TargetHudModule.weapon(e);
		float ww = w(ZeroFont.BOLD, wpn, 6.6f) + 12f;
		ZeroRender.gradient(g, x2 - 8f - ww, y1 + 7f, x2 - 8f, y1 + 18f, 5.5f, ZeroRender.alpha(ORANGE, a), ZeroRender.alpha(ORANGE2, a), 20f);
		ZeroRender.textCentered(g, ZeroFont.BOLD, wpn, x2 - 8f - ww / 2f, y1 + 9.3f, 6.6f, ZeroRender.alpha(0xFF1E120A, a));
		String dist = String.format(java.util.Locale.ROOT, "%.1f m", e.distanceTo(p));
		ZeroRender.textRight(g, ZeroFont.REGULAR, dist, x2 - 9f, y1 + 21f, 7.4f, ZeroRender.alpha(MUTED, a));

		// Lebensbalken (mit nachziehendem Schaden) + Ruestungsbalken
		float bx1 = x1 + 8f, bx2 = x2 - 8f, by = y1 + 35f;
		float frac = Math.max(0f, Math.min(1f, e.getHealth() / Math.max(1f, e.getMaxHealth())));
		float shown = anim("th:hp:" + e.getId(), frac, 10f);
		float trail = Math.max(anim("th:trail:" + e.getId(), frac, 2.5f), shown);
		ZeroRender.rect(g, bx1, by, bx2, by + 4.5f, 2.25f, ZeroRender.alpha(0xFF2A2420, a));
		ZeroRender.rect(g, bx1, by, bx1 + (bx2 - bx1) * trail, by + 4.5f, 2.25f, ZeroRender.alpha(0xB0FFE2C8, a));
		ZeroRender.glow(g, bx1, by, bx1 + (bx2 - bx1) * shown, by + 4.5f, 2.25f, 4f, ZeroRender.alpha(pal[1], 0.5f * a));
		ZeroRender.gradient(g, bx1, by, Math.max(bx1 + 4.5f, bx1 + (bx2 - bx1) * shown), by + 4.5f, 2.25f, ZeroRender.alpha(pal[1], a), ZeroRender.alpha(pal[0], a), 0f);
		if (e.getAbsorptionAmount() > 0f) {
			float af = Math.min(1f, e.getAbsorptionAmount() / Math.max(1f, e.getMaxHealth()));
			ZeroRender.rect(g, bx1, by + 5.5f, bx1 + (bx2 - bx1) * af, by + 6.5f, 0.5f, ZeroRender.alpha(0xFFF2C14E, a));
		}
		float armor = Math.min(1f, e.getArmorValue() / 20f);
		ZeroRender.rect(g, bx1, by + 8f, bx2, by + 10f, 1f, ZeroRender.alpha(0xFF2A2420, a));
		ZeroRender.rect(g, bx1, by + 8f, bx1 + (bx2 - bx1) * armor, by + 10f, 1f, ZeroRender.alpha(0xFFB9C3D1, a));

		// Statistik-Chips: Combo, Treffer, Vorsprung
		float diff = (p.getHealth() + p.getAbsorptionAmount()) - hp;
		String[] chips = {
				m.combo + " combo",
				m.hitsGiven + "-" + m.hitsTaken + " hits",
				(diff >= 0 ? "+" : "") + Math.round(diff) + (diff >= 0 ? " ahead" : " behind")};
		float cy = by + 15f;
		float cw = (bx2 - bx1 - 8f) / 3f;
		for (int i = 0; i < 3; i++) {
			float cx = bx1 + i * (cw + 4f);
			boolean hot = i == 2 ? diff < 0 : (i == 0 && m.combo >= 3);
			ZeroRender.rect(g, cx, cy, cx + cw, cy + 12f, 5f, ZeroRender.alpha(0x18FFFFFF, a));
			if (hot) ZeroRender.stroke(g, cx, cy, cx + cw, cy + 12f, 5f, 0.6f, ZeroRender.alpha(i == 2 ? 0xC0FF5A5A : pal[1], a));
			int tc = i == 2 ? (diff >= 0 ? pal[0] : 0xFFFF7A7A) : TEXT;
			ZeroRender.textCentered(g, ZeroFont.BOLD, chips[i], cx + cw / 2f, cy + 2.6f, 6.8f, ZeroRender.alpha(tc, a));
		}

		// Schild-Cooldown nach Axt-Treffer
		long left = m.shieldUntil - System.currentTimeMillis();
		if (left > 0) {
			String sh = String.format(java.util.Locale.ROOT, "Shield %.1fs", left / 1000f);
			float sw = w(ZeroFont.BOLD, sh, 7.4f) + 24f;
			float sx = (x1 + x2) / 2f - sw / 2f, sy = y2 + 5f;
			float pulse = 0.55f + 0.45f * (float) Math.sin(time * 6.0);
			ZeroRender.glow(g, sx, sy, sx + sw, sy + 15f, 7.5f, 7f, ZeroRender.alpha(pal[1], 0.45f * pulse * a));
			ZeroRender.rect(g, sx, sy, sx + sw, sy + 15f, 7.5f, ZeroRender.alpha(0xE017120F, a));
			ZeroRender.stroke(g, sx, sy, sx + sw, sy + 15f, 7.5f, 0.6f, ZeroRender.alpha(pal[1], a));
			float prog = left / 5000f;
			ZeroRender.stroke(g, sx + 5f, sy + 3.5f, sx + 13f, sy + 11.5f, 4f, 1.4f, ZeroRender.alpha(0x40FFFFFF, a));
			ZeroRender.circle(g, sx + 9f, sy + 7.5f, 2.2f * prog + 0.6f, ZeroRender.alpha(pal[0], a));
			ZeroRender.text(g, ZeroFont.BOLD, sh, sx + 17f, sy + 3.6f, 7.4f, ZeroRender.alpha(TEXT, a));
		}
	}

	// ------------------------------------------------------------------ Info (oben rechts)

	private static float info(GuiGraphics g, Minecraft mc, LocalPlayer p, float right, float y) {
		HudModule h = ModuleManager.HUD;
		List<String[]> rows = new ArrayList<>();
		int bx = p.getBlockX(), by = p.getBlockY(), bz = p.getBlockZ();
		if (h.coords.on()) rows.add(new String[]{"XYZ", bx + " " + by + " " + bz});
		if (h.nether.on()) {
			if (mc.level.dimension() == Level.NETHER) rows.add(new String[]{"OW", (bx * 8) + " " + (bz * 8)});
			else if (mc.level.dimension() == Level.OVERWORLD) rows.add(new String[]{"NETHER", Math.floorDiv(bx, 8) + " " + Math.floorDiv(bz, 8)});
		}
		if (h.direction.on()) rows.add(new String[]{"FACING", DIRS[Math.floorMod(Math.round(p.getYRot() / 90f), 4)]});
		if (h.fps.on()) rows.add(new String[]{"FPS", String.valueOf(mc.getFps())});
		if (h.ping.on() && mc.getConnection() != null) {
			PlayerInfo info = mc.getConnection().getPlayerInfo(p.getUUID());
			if (info != null) rows.add(new String[]{"PING", info.getLatency() + "ms"});
		}
		if (h.totems.on()) rows.add(new String[]{"TOTEMS", String.valueOf(countTotems(p.getInventory()))});
		if (rows.isEmpty()) return y;
		float ty = y;
		boolean isCat = ModuleManager.ACCENT.isCatLean();
		for (int i = 0; i < rows.size(); i++) {
			String[] r = rows.get(i);
			float vwid = w(ZeroFont.BOLD, r[1], 7.2f);
			float lwid = w(ZeroFont.BOLD, r[0], 5.6f);
			float x1 = right - vwid - lwid - 14f;
			if (isCat) {
				ZeroRender.rect(g, x1 - 4f, ty - 1.2f, right + 2f, ty + 9.8f, 3.5f, 0xDC141418);
				ZeroRender.stroke(g, x1 - 4f, ty - 1.2f, right + 2f, ty + 9.8f, 3.5f, 0.5f, 0x553F3F46);
				ZeroRender.text(g, ZeroFont.BOLD, r[0], x1, ty + 1.6f, 5.6f, 0xFFA1A1AA);
			} else {
				ZeroRender.gradient(g, x1 - 10f, ty - 1.5f, right + 2f, ty + 9.5f, 4f, 0x00000000, 0x9A0E0B0A, 0f);
				ZeroRender.text(g, ZeroFont.BOLD, r[0], x1, ty + 1.6f, 5.6f, ZeroRender.alpha(flow(i * 0.6f), 0.95f));
			}
			ZeroRender.textRight(g, ZeroFont.BOLD, r[1], right, ty, 7.2f, TEXT);
			mark(x1 - 4f, ty - 1.5f, right + 2f, ty + 9.8f);
			ty += 11.5f;
		}
		return ty + 5f;
	}
	private static int countTotems(Inventory inv) {
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(Items.TOTEM_OF_UNDYING)) n += s.getCount();
		}
		return n;
	}

	// ------------------------------------------------------------------ Waypoints (Liste)

	private static float waypoints(GuiGraphics g, Minecraft mc, LocalPlayer p, float right, float y) {
		List<WaypointsModule.Shown> list = ModuleManager.WAYPOINTS.shown(mc, p, 5);
		if (list.isEmpty()) return y;
		String dim = WaypointsModule.dimensionId(mc.level);
		float ty = y;
		for (WaypointsModule.Shown s : list) {
			int rgb = 0xFF000000 | s.rgb();
			double rel = s.dir() * 45.0;
			for (Setting.Waypoints.Point pt : ModuleManager.WAYPOINTS.points.get()) {
				if (pt.name().equals(s.name()) && pt.dim().equals(dim)) {
					rel = relAngle(p, pt.x() + 0.5, pt.z() + 0.5);
					break;
				}
			}
			String d = s.dist() + "m";
			float tw = w(ZeroFont.BOLD, s.name(), 7f) + w(ZeroFont.REGULAR, d, 6.6f) + 30f;
			float x1 = right - tw, y2 = ty + 11.5f;
			ZeroRender.rect(g, x1, ty, right, y2, 5.5f, 0xA8100C0B);
			ZeroRender.rect(g, x1 + 2f, ty + 0.5f, right - 2f, ty + 1.0f, 0.5f, 0x16FFFFFF);
			ZeroRender.stroke(g, x1, ty, right, y2, 5.5f, 0.5f, 0x20FFFFFF);
			ZeroRender.rect(g, x1, ty, x1 + 12f, y2, 5.5f, ZeroRender.alpha(rgb, 0.22f));
			arrow(g, x1 + 6f, ty + 5.75f, rel, 6.5f, rgb);
			ZeroRender.text(g, ZeroFont.BOLD, s.name(), x1 + 15f, ty + 2.2f, 6.8f, TEXT);
			ZeroRender.textRight(g, ZeroFont.REGULAR, d, right - 5f, ty + 2.4f, 6.4f, MUTED);
			mark(x1, ty, right, y2);
			ty += 14f;
		}
		return ty + 4f;
	}
	/** Wegpunkt-Marker direkt im Bild: leuchtender Punkt + Name + Entfernung. */
	private static void markers(GuiGraphics g, Minecraft mc, LocalPlayer p, float vw, float vh, float pt) {
		String dim = WaypointsModule.dimensionId(mc.level);
		var cam = mc.gameRenderer.getMainCamera();
		Vec3 eye = cam.position();
		double yaw = Math.toRadians(cam.yRot());
		double pitch = Math.toRadians(cam.xRot());
		// Kamera-Achsen
		double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
		double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
		double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
		double tanHalf = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
		double aspect = vw / vh;

		for (Setting.Waypoints.Point w : ModuleManager.WAYPOINTS.points.get()) {
			if (!w.dim().equals(dim)) continue;
			double dx = w.x() + 0.5 - eye.x, dy = w.y() + 1.0 - eye.y, dz = w.z() + 0.5 - eye.z;
			double z = dx * fx + dy * fy + dz * fz;
			if (z < 0.5) continue;
			double x = dx * rx + dz * rz;
			double y = dx * ux + dy * uy + dz * uz;
			float sx = (float) (vw / 2.0 * (1.0 + x / (z * tanHalf * aspect)));
			float sy = (float) (vh / 2.0 * (1.0 - y / (z * tanHalf)));
			if (sx < -50 || sx > vw + 50 || sy < -50 || sy > vh + 50) continue;
			int dist = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
			int rgb = 0xFF000000 | WaypointsModule.colorOf(w.name());
			// nahe am Fadenkreuz = deutlicher
			float center = (float) Math.hypot(sx - vw / 2.0, sy - vh / 2.0);
			float focus = anim("mk:" + w.name(), center < 60f ? 1f : 0f, 10f);
			String label = w.name() + " · " + dist + "m";
			float size = 6.8f;
			float tw = w(ZeroFont.BOLD, label, size);
			float a = 0.70f + 0.30f * focus;
			ZeroRender.glow(g, sx - 4f, sy - 4f, sx + 4f, sy + 4f, 4f, 4f, ZeroRender.alpha(rgb, 0.45f * a));
			ZeroRender.circle(g, sx, sy, 2.4f + 0.6f * focus, ZeroRender.alpha(rgb, a));
			ZeroRender.circle(g, sx, sy, 1.0f, ZeroRender.alpha(0xFFFFFFFF, a));
			float lx1 = sx - tw / 2f - 5.5f, lx2 = sx + tw / 2f + 5.5f, ly1 = sy - 17f, ly2 = sy - 7.5f;
			ZeroRender.glow(g, lx1, ly1 + 1f, lx2, ly2 + 1f, 4.5f, 5f, ZeroRender.alpha(0x40000000, a));
			ZeroRender.rect(g, lx1, ly1, lx2, ly2, 4.5f, ZeroRender.alpha(0xB0120E0C, a));
			ZeroRender.rect(g, lx1 + 2f, ly1 + 0.5f, lx2 - 2f, ly1 + 1.0f, 0.5f, ZeroRender.alpha(0x18FFFFFF, a));
			ZeroRender.stroke(g, lx1, ly1, lx2, ly2, 4.5f, 0.5f, ZeroRender.alpha(rgb, 0.30f + 0.35f * focus));
			ZeroRender.textCentered(g, ZeroFont.BOLD, label, sx, ly1 + 2.0f, size, ZeroRender.alpha(TEXT, a));
		}
	}

	// ------------------------------------------------------------------ Sus Chunk (oben links)

	private static float sus(GuiGraphics g, LocalPlayer p, SusChunkModule sus, float x1, float y) {
		List<SusChunkModule.ChunkResult> list = sus.susChunks();
		list.sort(Comparator.comparingDouble(r -> distSq(p, r)));
		boolean flash = sus.isFlashing();
		float fl = anim("sus:flash", flash ? 1f : 0f, 8f);
		int lines = Math.max(1, Math.min(3, list.size()));
		float width = 190f;
		float x2 = x1 + width;
		float rowH = 13f;
		float y2 = y + 25f + lines * rowH + fl * 14f + 2f;
		panel(g, x1, y, x2, y2, fl);

		// Kopf
		float pulse = 0.5f + 0.5f * (float) Math.sin(time * 3.0);
		ZeroRender.glow(g, x1 + 9f, y + 8f, x1 + 17f, y + 16f, 4f, 4f, ZeroRender.alpha(0xFFF0782C, 0.4f + 0.4f * pulse));
		ZeroRender.circle(g, x1 + 13f, y + 12f, 2.6f, ORANGE);
		ZeroRender.text(g, ZeroFont.BOLD, "Sus Chunk", x1 + 22f, y + 7.5f, 8.6f, TEXT);
		String n = String.valueOf(list.size());
		float cw = w(ZeroFont.BOLD, n, 7.6f) + 12f;
		ZeroRender.gradient(g, x2 - 10f - cw, y + 6f, x2 - 10f, y + 18f, 6f, list.isEmpty() ? 0x30FFFFFF : ORANGE, list.isEmpty() ? 0x20FFFFFF : ORANGE2, 20f);
		ZeroRender.textCentered(g, ZeroFont.BOLD, n, x2 - 10f - cw / 2f, y + 8.4f, 7.6f, list.isEmpty() ? MUTED : 0xFF1E120A);
		ZeroRender.rect(g, x1 + 10f, y + 21f, x2 - 10f, y + 21.6f, 0f, 0x18FFFFFF);

		float ty = y + 26f;
		if (list.isEmpty()) {
			String s = sus.pendingScans() > 0 ? "Scanning chunks… (" + sus.pendingScans() + ")" : "Nothing found nearby";
			ZeroRender.text(g, ZeroFont.REGULAR, s, x1 + 11f, ty, 8f, MUTED);
			ty += rowH;
		} else {
			for (int i = 0; i < Math.min(3, list.size()); i++) {
				SusChunkModule.ChunkResult r = list.get(i);
				double cx = r.pos().getMiddleBlockX() + 0.5;
				double cz = r.pos().getMiddleBlockZ() + 0.5;
				int dist = (int) Math.sqrt(distSq(p, r));
				arrow(g, x1 + 14f, ty + 5f, relAngle(p, cx, cz), 8.5f, ORANGE);
				ZeroRender.text(g, ZeroFont.BOLD, dist + "m", x1 + 24f, ty, 8f, TEXT);
				ZeroRender.textRight(g, ZeroFont.REGULAR, r.reason(), x2 - 11f, ty, 7.4f, MUTED);
				ty += rowH;
			}
		}
		if (fl > 0.01f) {
			ZeroRender.text(g, ZeroFont.BOLD, "+ New sus chunk found", x1 + 11f, ty + 1f, 8f, ZeroRender.alpha(ORANGE, fl));
		}
		return y2 + 7f;
	}

	// ------------------------------------------------------------------ Xray Research (oben links)

	private static final int XPURPLE = 0xFFA78BFA;  // hell
	private static final int XPURPLE2 = 0xFF8B6FE8; // dunkel

	private static float xr(GuiGraphics g, LocalPlayer p, XrayResearchModule m, float x1, float y) {
		List<ChunkData> act = m.highlights();
		act.sort(Comparator.comparingDouble(d -> XrayResearchModule.distSq(p, d.pos)));
		List<Prediction> preds = new ArrayList<>(m.predictions());
		preds.sort(Comparator.comparingDouble(pr -> XrayResearchModule.distSq(p, pr.pos())));

		float width = 205f;
		float x2 = x1 + width;
		float rowH = 13f;
		int rows = Math.min(3, act.size());
		int predRows = rows < 3 && !preds.isEmpty() ? 1 : 0;
		float y2 = y + 25f + (rows + predRows) * rowH + 14f + 2f;
		panel(g, x1, y, x2, y2, 0f);

		// Kopf
		float pulse = 0.5f + 0.5f * (float) Math.sin(time * 3.0);
		ZeroRender.glow(g, x1 + 9f, y + 8f, x1 + 17f, y + 16f, 4f, 4f, ZeroRender.alpha(XPURPLE, 0.35f + 0.35f * pulse));
		ZeroRender.circle(g, x1 + 13f, y + 12f, 2.6f, XPURPLE);
		ZeroRender.text(g, ZeroFont.BOLD, "Xray Research", x1 + 22f, y + 7.5f, 8.6f, TEXT);
		String n = String.valueOf(m.analyzedCount());
		float cw = w(ZeroFont.BOLD, n, 7.6f) + 12f;
		ZeroRender.gradient(g, x2 - 10f - cw, y + 6f, x2 - 10f, y + 18f, 6f, XPURPLE, XPURPLE2, 20f);
		ZeroRender.textCentered(g, ZeroFont.BOLD, n, x2 - 10f - cw / 2f, y + 8.4f, 7.6f, 0xFF1E1230);
		ZeroRender.rect(g, x1 + 10f, y + 21f, x2 - 10f, y + 21.6f, 0f, 0x18FFFFFF);

		float ty = y + 26f;
		if (rows == 0 && predRows == 0) {
			String s = m.pendingScans() > 0 ? "Analyzing chunks… (" + m.pendingScans() + ")" : "No ore data in range";
			ZeroRender.text(g, ZeroFont.REGULAR, s, x1 + 11f, ty, 8f, MUTED);
			ty += rowH;
		} else {
			// Tatsächliche Daten (aus den geladenen Chunks gelesen)
			for (int i = 0; i < rows; i++) {
				ChunkData d = act.get(i);
				double cx = d.pos.getMiddleBlockX() + 0.5;
				double cz = d.pos.getMiddleBlockZ() + 0.5;
				int dist = (int) Math.sqrt(XrayResearchModule.distSq(p, d.pos));
				arrow(g, x1 + 14f, ty + 5f, relAngle(p, cx, cz), 8.5f, XPURPLE);
				ZeroRender.text(g, ZeroFont.BOLD, dist + "m", x1 + 24f, ty, 8f, TEXT);
				ZeroRender.text(g, ZeroFont.BOLD, d.summary(), x1 + 58f, ty, 7.4f, ZeroRender.alpha(XPURPLE, 0.92f));
				ZeroRender.textRight(g, ZeroFont.BOLD, (int) d.confidence + "%", x2 - 11f, ty, 7.4f, MUTED);
				ty += rowH;
			}
			// Statistische Vorhersage (Modell, noch nicht gescannt)
			if (predRows > 0) {
				Prediction pr = preds.get(0);
				double cx = pr.pos().getMiddleBlockX() + 0.5;
				double cz = pr.pos().getMiddleBlockZ() + 0.5;
				int dist = (int) Math.sqrt(XrayResearchModule.distSq(p, pr.pos()));
				arrow(g, x1 + 14f, ty + 5f, relAngle(p, cx, cz), 8.5f, ZeroRender.alpha(XPURPLE, 0.5f));
				ZeroRender.text(g, ZeroFont.BOLD, dist + "m", x1 + 24f, ty, 8f, ZeroRender.alpha(TEXT, 0.6f));
				ZeroRender.text(g, ZeroFont.REGULAR, "predicted ~" + (int) pr.expected() + " ores", x1 + 58f, ty, 7.4f, ZeroRender.alpha(XPURPLE, 0.55f));
				ZeroRender.textRight(g, ZeroFont.BOLD, (int) pr.confidence() + "%", x2 - 11f, ty, 7.4f, ZeroRender.alpha(MUTED, 0.7f));
				ty += rowH;
			}
		}

		// Fußzeile: saubere Trennung Actual vs Predicted
		ZeroRender.text(g, ZeroFont.REGULAR,
				"Actual " + m.analyzedCount() + " · Predictions " + preds.size() + " · " + m.updatesPerSec() + " upd/s",
				x1 + 11f, ty, 7f, MUTED);
		return y2 + 7f;
	}

	// ------------------------------------------------------------------ Base Finder Research (Dashboard)

	private static final int BGRN = 0xFF7BE6A8;  // hell
	private static final int BGRN2 = 0xFF4BBF8A; // dunkel

	private static float baseResearch(GuiGraphics g, LocalPlayer p, BaseFinderResearchModule m, float x1, float y) {
		float width = 205f;
		float x2 = x1 + width;
		float rowH = 13f;
		float y2 = y + 25f + 4 * rowH + 2f;
		panel(g, x1, y, x2, y2, 0f);

		// Kopf
		float pulse = 0.5f + 0.5f * (float) Math.sin(time * 3.0);
		ZeroRender.glow(g, x1 + 9f, y + 8f, x1 + 17f, y + 16f, 4f, 4f, ZeroRender.alpha(BGRN, 0.35f + 0.35f * pulse));
		ZeroRender.circle(g, x1 + 13f, y + 12f, 2.6f, BGRN);
		ZeroRender.text(g, ZeroFont.BOLD, "Base Research", x1 + 22f, y + 7.5f, 8.6f, TEXT);
		String n = String.valueOf(m.analyzedCount());
		float cw = w(ZeroFont.BOLD, n, 7.6f) + 12f;
		ZeroRender.gradient(g, x2 - 10f - cw, y + 6f, x2 - 10f, y + 18f, 6f, BGRN, BGRN2, 20f);
		ZeroRender.textCentered(g, ZeroFont.BOLD, n, x2 - 10f - cw / 2f, y + 8.4f, 7.6f, 0xFF12271B);
		ZeroRender.rect(g, x1 + 10f, y + 21f, x2 - 10f, y + 21.6f, 0f, 0x18FFFFFF);

		float ty = y + 26f;
		java.util.Locale L = java.util.Locale.ROOT;

		// 1) Position: Client-Y vs. server-bestätigte Position + Korrekturen
		double[] srv = m.serverPos();
		long age = m.lastServerPosAge();
		String srvTxt = age < 0 ? "srv -" : (age < 8000 ? String.format(L, "srv %.0f/%.0f", srv[0], srv[2]) : "srv old");
		ZeroRender.text(g, ZeroFont.REGULAR, String.format(L, "Y %.1f · %s · corr %d",
				p.getY(), srvTxt, m.corrections()), x1 + 11f, ty, 7.6f, TEXT);
		ty += rowH;

		// 2) Chunk-Tracking: geladene Chunks + verfügbare Block-Information
		int[] tot = m.infoTotals();
		ZeroRender.text(g, ZeroFont.REGULAR, String.format(L, "Chunks %d · Sec %dk (%d under)",
				m.loadedChunks(), tot[0] / 1000, tot[1]), x1 + 11f, ty, 7.6f, TEXT);
		ty += rowH;

		// 3) Untergrund-Strukturen + höchste Confidence
		float[] st = m.structureStats();
		ZeroRender.text(g, ZeroFont.REGULAR, String.format(L, "Structures %d · Top %d%%",
				(int) st[0], (int) st[1]), x1 + 11f, ty, 7.6f, st[1] >= m.minConfidence.get() ? BGRN : MUTED);
		ty += rowH;

		// 4) Höhen-Forschung: ab welchem Y schickt der Server Untergrund-Daten?
		double thr = m.dataThresholdY();
		String thrTxt = Double.isNaN(thr)
				? "Data-Y: sample more" + (m.infoGainedEvents() > 0 ? " (+" + m.infoGainedEvents() + ")" : "")
				: String.format(L, "Data-Y ≈ %.0f (gain +%d)", thr, m.infoGainedEvents());
		ZeroRender.text(g, ZeroFont.REGULAR, thrTxt, x1 + 11f, ty, 7.6f, Double.isNaN(thr) ? FAINT : MUTED);
		ty += rowH;

		return y2 + 7f;
	}

	// ------------------------------------------------------------------ Status Bar (oben Mitte)

	private static void statusBar(GuiGraphics g, Minecraft mc, LocalPlayer p, float vw) {
		dev.quiqzy.client.module.misc.StatusBarModule m = ModuleManager.STATUS_BAR;
		List<String[]> parts = new ArrayList<>(); // {art, text}
		float memFrac = 0f;
		if (m.memory.on()) {
			Runtime rt = Runtime.getRuntime();
			memFrac = (rt.totalMemory() - rt.freeMemory()) / (float) rt.maxMemory();
			parts.add(new String[]{"mem", Math.round(memFrac * 100) + "%"});
		}
		if (m.clock.on()) parts.add(new String[]{"clock", java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))});
		if (m.server.on()) {
			String sv = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : (mc.hasSingleplayerServer() ? "Singleplayer" : "-");
			if (sv.length() > 20) sv = sv.substring(0, 20);
			parts.add(new String[]{"srv", sv});
		}
		if (m.fps.on()) parts.add(new String[]{"fps", String.valueOf(mc.getFps())});
		if (m.ping.on() && mc.getConnection() != null && mc.getCurrentServer() != null) {
			var info = mc.getConnection().getPlayerInfo(p.getUUID());
			if (info != null) parts.add(new String[]{"ping", info.getLatency() + "ms"});
		}
		if (parts.isEmpty()) return;
		float size = 6.8f, h = 15f, gap = 4f;
		float[] widths = new float[parts.size()];
		float total = 0f;
		for (int i = 0; i < parts.size(); i++) {
			widths[i] = 17f + w(ZeroFont.BOLD, parts.get(i)[1], size) + 7f + (parts.get(i)[0].equals("fps") ? 14f : 0f);
			total += widths[i] + (i > 0 ? gap : 0f);
		}
		float x = vw / 2f - total / 2f, y1 = 6f;
		for (int i = 0; i < parts.size(); i++) {
			String[] pt = parts.get(i);
			float x2 = x + widths[i];
			float cy = y1 + h / 2f;
			int acc = flow(i * 0.8f);
			// Kapsel: dunkles Glas, feiner Rand, Akzent-Punkt links
			ZeroRender.glow(g, x, y1 + 1.5f, x2, y1 + h + 1.5f, h / 2f, 6f, 0x60000000);
			ZeroRender.rect(g, x, y1, x2, y1 + h, h / 2f, 0xCC0F0C0B);
			ZeroRender.stroke(g, x, y1, x2, y1 + h, h / 2f, 0.5f, 0x22FFFFFF);
			float ix = x + 8f;
			ZeroRender.circle(g, ix, cy, 5.2f, ZeroRender.alpha(acc, 0.18f));
			switch (pt[0]) {
				case "mem" -> {
					// kleiner Ring-Füllstand
					ZeroRender.ring(g, ix, cy, 2.9f, 1.1f, 0x30FFFFFF);
					ZeroRender.arc(g, ix, cy, 2.9f, 1.1f, -90f, 360f * Math.min(1f, memFrac), memFrac > 0.85f ? 0xFFFF5A5A : acc, memFrac > 0.85f ? 0xFFFF9A7A : ORANGE);
				}
				case "clock" -> {
					ZeroRender.stroke(g, ix - 3.2f, cy - 3.2f, ix + 3.2f, cy + 3.2f, 3.2f, 0.9f, acc);
					ZeroRender.rect(g, ix - 0.4f, cy - 2.2f, ix + 0.4f, cy + 0.2f, 0.3f, acc);
					ZeroRender.rect(g, ix - 0.2f, cy - 0.4f, ix + 1.8f, cy + 0.4f, 0.3f, acc);
				}
				case "srv" -> {
					float pulse = 0.5f + 0.5f * (float) Math.sin(animTime() * 3.0);
					ZeroRender.glow(g, ix - 3f, cy - 3f, ix + 3f, cy + 3f, 3f, 2.5f, ZeroRender.alpha(0xFF7BE07B, 0.4f * pulse));
					ZeroRender.circle(g, ix, cy, 2.2f, 0xFF7BE07B);
				}
				case "fps" -> {
					// drei kleine Balken
					for (int b = 0; b < 3; b++) {
						float bh = 1.6f + b * 1.2f;
						ZeroRender.rect(g, ix - 2.6f + b * 2f, cy + 2.4f - bh, ix - 1.4f + b * 2f, cy + 2.4f, 0.4f, acc);
					}
				}
				default -> ZeroRender.circle(g, ix, cy, 2f, acc);
			}
			ZeroRender.text(g, ZeroFont.BOLD, pt[1], x + 16f, y1 + 3.7f, size, TEXT);
			if (pt[0].equals("fps")) {
				float fw = w(ZeroFont.BOLD, pt[1], size);
				ZeroRender.text(g, ZeroFont.BOLD, "FPS", x + 16f + fw + 2f, y1 + 5.3f, 4.6f, MUTED);
				widths[i] += 0f;
			}
			mark(x, y1, x2, y1 + h);
			x = x2 + gap;
		}
	}
	// ------------------------------------------------------------------ Freecam-Anzeige

	/** Kleine Tempo-Anzeige: erscheint nur kurz beim Einschalten und wenn du am Mausrad drehst. */
	private static void freecamHint(GuiGraphics g, float vw, float vh) {
		var m = ModuleManager.FREECAM;
		long since = System.currentTimeMillis() - Math.max(m.speedChanged, m.enabledAt);
		float vis = anim("fc:vis", since < 1400 ? 1f : 0f, since < 1400 ? 14f : 4f);
		if (vis < 0.01f) return;
		float pop = anim("fc:pop", System.currentTimeMillis() - m.speedChanged < 400 ? 1f : 0f, 12f);
		String txt = m.speed.get() + " b/s";
		float size = 8.6f + pop * 1.2f;
		float tw = w(ZeroFont.BOLD, txt, size);
		float x1 = vw / 2f - tw / 2f - 18f, x2 = vw / 2f + tw / 2f + 10f;
		float y2 = vh / 2f + 52f + (1f - vis) * 6f, y1 = y2 - 18f;
		ZeroRender.glow(g, x1, y1 + 2f, x2, y2 + 2f, 9f, 10f, ZeroRender.alpha(0x80000000, vis));
		ZeroRender.rect(g, x1, y1, x2, y2, 9f, ZeroRender.alpha(0xD0120E0C, vis));
		ZeroRender.stroke(g, x1, y1, x2, y2, 9f, 0.6f, ZeroRender.alpha(flow(0f), (0.4f + 0.6f * pop) * vis));
		// kleiner Tempo-Balken als Symbol
		float frac = (m.speed.get() - 1) / 59f;
		ZeroRender.rect(g, x1 + 7f, y1 + 7.5f, x1 + 13f, y1 + 10.5f, 1.5f, ZeroRender.alpha(0x30FFFFFF, vis));
		ZeroRender.rect(g, x1 + 7f, y1 + 7.5f, x1 + 7f + 6f * Math.max(0.15f, frac), y1 + 10.5f, 1.5f, ZeroRender.alpha(flow(0f), vis));
		ZeroRender.text(g, ZeroFont.BOLD, txt, x1 + 16f, y1 + 4.4f, size, ZeroRender.alpha(ZeroRender.lerp(TEXT, ORANGE, pop), vis));
	}

	// ------------------------------------------------------------------ Radar

	private static float radar(GuiGraphics g, Minecraft mc, LocalPlayer p, float x1, float y, float pt) {
		dev.quiqzy.client.module.misc.RadarModule m = ModuleManager.RADAR;
		float size = m.size.get();
		float r = size / 2f;
		float cx = x1 + r + 4f, cy = y + r + 4f;
		// Hintergrund-Scheibe
		ZeroRender.glow(g, cx - r, cy - r + 2f, cx + r, cy + r + 2f, r, 10f, 0x70000000);
		ZeroRender.gradient(g, cx - r, cy - r, cx + r, cy + r, r, 0xB8141110, 0xB80C0A09, 90f);

		mark(cx - r, cy - r, cx + r, cy + r + 6f);
		ZeroRender.stroke(g, cx - r, cy - r, cx + r, cy + r, r, 0.6f, 0x30FFFFFF);
		ZeroRender.stroke(g, cx - r * 0.55f, cy - r * 0.55f, cx + r * 0.55f, cy + r * 0.55f, r * 0.55f, 0.5f, 0x18FFFFFF);
		double yaw = Math.toRadians(p.getViewYRot(pt));
		// Himmelsrichtungen (drehen sich mit)
		String[] dirs = {"N", "E", "S", "W"};
		double[] base = {Math.PI, -Math.PI / 2, 0, Math.PI / 2}; // Welt-Richtungen (Minecraft: Süden = +Z)
		for (int i = 0; i < 4; i++) {
			double ang = base[i] - yaw + Math.PI;
			float dx = (float) -Math.sin(ang) * (r - 7f), dy = (float) Math.cos(ang) * (r - 7f);
			if (i == 0) {
				ZeroRender.circle(g, cx + dx, cy + dy, 4.6f, ZeroRender.alpha(flow(0f), 0.25f));
			}
			ZeroRender.textCentered(g, ZeroFont.BOLD, dirs[i], cx + dx, cy + dy - 3f, 6f, i == 0 ? ORANGE : MUTED);
		}
		// Entities
		double range = m.range.get();
		Vec3 me = p.getPosition(pt);
		for (net.minecraft.world.entity.Entity e : nearby(mc)) {
			if (e == p) continue;
			boolean isPlayer = e instanceof net.minecraft.world.entity.player.Player;
			if (!isPlayer && !(m.mobs.on() && e instanceof net.minecraft.world.entity.Mob)) continue;
			Vec3 ep = e.getPosition(pt);
			double dx = ep.x - me.x, dz = ep.z - me.z;
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist > range) continue;
			// in Blickrichtung drehen: vorne = oben
			double rx = dx * Math.cos(yaw) + dz * Math.sin(yaw);
			double rz = dz * Math.cos(yaw) - dx * Math.sin(yaw);
			float sx = cx - (float) (rx / range) * (r - 12f);
			float sy = cy - (float) (rz / range) * (r - 12f);
			if (isPlayer && e instanceof net.minecraft.client.player.AbstractClientPlayer acp) {
				float hs = 9f;
				ZeroRender.circle(g, sx, sy, hs / 2f + 1.6f, flow(1f));
				net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, acp.getSkin(), Math.round(sx - hs / 2f), Math.round(sy - hs / 2f), Math.round(hs));
				if (m.names.on()) ZeroRender.textCentered(g, ZeroFont.BOLD, e.getName().getString(), sx, sy + 6.5f, 5f, TEXT);
			} else {
				int c = e instanceof net.minecraft.world.entity.monster.Enemy ? 0xFFFF5A5A : 0xFF7BE07B;
				ZeroRender.circle(g, sx, sy, 1.8f, c);
			}
		}
		// du in der Mitte: dunkler Punkt + Pfeil nach oben, damit er zwischen den Punkten sichtbar bleibt
		ZeroRender.circle(g, cx, cy, 5.5f, 0xE0100C0B);
		ZeroRender.stroke(g, cx - 5.5f, cy - 5.5f, cx + 5.5f, cy + 5.5f, 5.5f, 0.7f, ZeroRender.alpha(flow(0f), 0.8f));
		arrow(g, cx, cy, 0.0, 8f, ORANGE);
		return y + size + 15f;
	}

	// ------------------------------------------------------------------ Totem-Pop-Hinweise (oben Mitte)

	private static void popNotices(GuiGraphics g, float vw) {
		var list = ModuleManager.POP_COUNTER.notices;
		float y = 34f;
		long now = System.currentTimeMillis();
		for (int i = list.size() - 1; i >= 0; i--) {
			var n = list.get(i);
			float age = (now - n.at()) / 1000f;
			float a = Math.min(1f, Math.min(age / 0.15f, (3.5f - age) / 0.5f));
			if (a <= 0f) continue;
			float tw = w(ZeroFont.BOLD, n.text(), 7.2f);
			float x1 = vw / 2f - tw / 2f - 14f, x2 = vw / 2f + tw / 2f + 10f;
			float yy = y - (1f - Math.min(1f, age / 0.15f)) * 5f;
			ZeroRender.glow(g, x1, yy, x2, yy + 15f, 6f, 6f, ZeroRender.alpha(0xFFF0782C, 0.25f * a));
			ZeroRender.rect(g, x1, yy, x2, yy + 15f, 6f, ZeroRender.alpha(0xB8120E0C, a));
			ZeroRender.stroke(g, x1, yy, x2, yy + 15f, 6f, 0.5f, ZeroRender.alpha(ORANGE, 0.55f * a));
			ZeroRender.circle(g, x1 + 8f, yy + 7.5f, 2.2f, ZeroRender.alpha(0xFFF2C14E, a));
			ZeroRender.text(g, ZeroFont.BOLD, n.text(), x1 + 14f, yy + 3.8f, 7.2f, ZeroRender.alpha(TEXT, a));
			y += 18f;
		}
	}

	// ------------------------------------------------------------------ Item Counter (links neben der Hotbar)

	private static void itemCounter(GuiGraphics g, LocalPlayer p, float vw, float vh, float k) {
		var list = ModuleManager.ITEM_COUNTER.entries(p.getInventory());
		if (list.isEmpty()) return;
		float cell = 28f;
		float x2 = vw / 2f - 91f * k - 10f;
		float x1 = x2 - list.size() * cell - 8f;
		float y2 = vh - 4f, y1 = y2 - 34f;
		panel(g, x1, y1, x2, y2, 0f);
		for (int i = 0; i < list.size(); i++) {
			var e = list.get(i);
			float cx = x1 + 4f + i * cell;
			g.pose().pushMatrix();
			g.pose().translate(cx + 6f, y1 + 4f);
			g.renderItem(e.icon(), 0, 0);
			g.pose().popMatrix();
			int c = e.count() == 0 ? 0xFFFF5A5A : TEXT;
			ZeroRender.textCentered(g, ZeroFont.BOLD, String.valueOf(e.count()), cx + cell / 2f, y1 + 22f, 7f, c);
		}
	}

	// ------------------------------------------------------------------ Session Stats

	private static float sessionStats(GuiGraphics g, float right, float y) {
		dev.quiqzy.client.module.misc.SessionStatsModule m = ModuleManager.SESSION_STATS;
		long sec = (System.currentTimeMillis() - m.start) / 1000L;
		String play = (sec / 3600) > 0 ? String.format("%d:%02d", sec / 3600, (sec / 60) % 60) : String.format("%d:%02d", sec / 60, sec % 60);
		String kd = m.deaths == 0 ? String.valueOf(m.kills) : String.format(java.util.Locale.ROOT, "%.1f", m.kills / (float) m.deaths);
		String[][] tiles = {{play, "TIME"}, {String.valueOf(m.kills), "KILLS"}, {String.valueOf(m.deaths), "DEATHS"},
				{kd, "K/D"}, {String.valueOf(m.blocks), "BLOCKS"}};
		float tw = 30f, th = 22f, gap = 2f;
		int cols = 5;
		float x1 = right - cols * tw - (cols - 1) * gap;
		for (int i = 0; i < tiles.length; i++) {
			float tx = x1 + i * (tw + gap);
			boolean acc = i == 1 || i == 3;
			ZeroRender.rect(g, tx, y, tx + tw, y + th, 5f, 0xC8100C0B);
			if (acc) ZeroRender.rect(g, tx + 6f, y, tx + tw - 6f, y + 1.2f, 0.6f, flow(i));
			ZeroRender.textCentered(g, ZeroFont.BOLD, tiles[i][0], tx + tw / 2f, y + 3.8f, 7.6f, acc ? ORANGE : TEXT);
			ZeroRender.textCentered(g, ZeroFont.BOLD, tiles[i][1], tx + tw / 2f, y + 14.2f, 4.6f, MUTED);
		}
		mark(x1, y, right, y + th);
		return y + th + 7f;
	}
	// ------------------------------------------------------------------ Crosshair

	private static void crosshair(GuiGraphics g, LocalPlayer p, float vw, float vh) {
		dev.quiqzy.client.module.visuals.CrosshairModule m = ModuleManager.CROSSHAIR;
		float cx = vw / 2f, cy = vh / 2f;
		int col = 0xFF000000 | m.color.get();
		float expand = m.dynamic.on() ? anim("ch:exp", HitFx.sinceHit() < 0.12f ? 1f : 0f, 14f) : 0f;
		float th = m.thickness.get();
		float gap = m.gap.get() + expand * 3f;
		float len = m.size.get();
		String st = m.style.get();
		int shadow = 0x90000000;
		if (st.startsWith("Cross")) {
			float[][] arms = {
					{cx - gap - len, cy - th / 2f, cx - gap, cy + th / 2f},
					{cx + gap, cy - th / 2f, cx + gap + len, cy + th / 2f},
					{cx - th / 2f, cy - gap - len, cx + th / 2f, cy - gap},
					{cx - th / 2f, cy + gap, cx + th / 2f, cy + gap + len}};
			for (float[] a : arms) ZeroRender.rect(g, a[0] - 0.7f, a[1] - 0.7f, a[2] + 0.7f, a[3] + 0.7f, th / 2f, shadow);
			for (float[] a : arms) ZeroRender.rect(g, a[0], a[1], a[2], a[3], th / 2f, col);
		}
		if (st.equals("Dot") || st.endsWith("Dot")) {
			ZeroRender.circle(g, cx, cy, th * 0.9f + 0.7f, shadow);
			ZeroRender.circle(g, cx, cy, th * 0.9f, col);
		}
		if (st.equals("Circle")) {
			float r = gap + len * 0.6f;
			ZeroRender.stroke(g, cx - r, cy - r, cx + r, cy + r, r, th + 1.2f, shadow);
			ZeroRender.stroke(g, cx - r, cy - r, cx + r, cy + r, r, th, col);
			ZeroRender.circle(g, cx, cy, 1.2f, col);
		}
		if (expand > 0.01f) {
			ZeroRender.glow(g, cx - 8f, cy - 8f, cx + 8f, cy + 8f, 8f, 6f, ZeroRender.alpha(col, 0.35f * expand));
		}
		if (m.cooldown.on()) {
			float s = p.getAttackStrengthScale(0f);
			float vis = anim("ch:cd", s < 0.99f ? 1f : 0f, 16f);
			if (vis > 0.01f) {
				float rr = gap + len + 4f;
				ZeroRender.ring(g, cx, cy, rr, 1.4f, ZeroRender.alpha(0x50000000, vis));
				ZeroRender.arc(g, cx, cy, rr, 1.4f, -90f, 360f * s, ZeroRender.alpha(col, vis * 0.6f), ZeroRender.alpha(col, vis));
			}
		}
	}

	// ------------------------------------------------------------------ Low HP Warning

	private static boolean lowWasOn;
	private static int lowDbg;

	private static void lowHp(GuiGraphics g, LocalPlayer p, float vw, float vh) {
		float hp = p.getHealth() + p.getAbsorptionAmount();
		boolean low = hp <= ModuleManager.LOW_HP.threshold.get() && !p.isCreative() && !p.isSpectator() && p.isAlive();
		float v = anim("lowhp", low ? 1f : 0f, 5f);
		if (low && !lowWasOn && ModuleManager.LOW_HP.sound.on()) dev.quiqzy.client.util.UiSounds.removed();
		lowWasOn = low;
		if (v < 0.01f) return;
		float pulse = 0.5f + 0.5f * (float) Math.sin(time * 6.5);
		HitFx.edges(g, vw, vh, ZeroRender.alpha(0xFFE0302C, (0.45f + 0.25f * pulse) * v), 110f);
	}

	// ------------------------------------------------------------------ Watermark (oben links)

	private static float watermark(GuiGraphics g, Minecraft mc, float x1, float y) {
		dev.quiqzy.client.module.misc.WatermarkModule m = ModuleManager.WATERMARK;
		if (ModuleManager.ACCENT.isCatLean()) {
			String user = mc.getUser() != null ? mc.getUser().getName() : "Player";
			int fps = mc.getFps();
			int ping = -1;
			if (mc.getConnection() != null && mc.player != null) {
				PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
				if (info != null) ping = info.getLatency();
			}
			String wText = " | v1.0.0 | " + user + " | " + fps + " fps" + (ping >= 0 ? " | " + ping + "ms" : "");
			float fsize = 6.8f;
			float tw = w(ZeroFont.BOLD, "ZERO", fsize) + w(ZeroFont.REGULAR, wText, fsize);
			float pillW = tw + 20f;
			float pillH = 14f;
			float x2 = x1 + pillW, y2 = y + pillH;
			ZeroRender.glow(g, x1, y, x2, y2, 4f, 8f, 0x30000000);
			ZeroRender.rect(g, x1, y, x2, y2, 4f, 0xDC141418);
			ZeroRender.stroke(g, x1, y, x2, y2, 4f, 0.6f, 0x553F3F46);
			float pulse = 0.6f + 0.4f * (float) Math.sin(animTime() * 2.2);
			ZeroRender.circle(g, x1 + 7f, y + pillH / 2f, 2.0f, ZeroRender.alpha(0xFFD4D4D8, pulse));
			float tx = x1 + 13f;
			ZeroRender.text(g, ZeroFont.BOLD, "ZERO", tx, y + 3.0f, fsize, 0xFFFFFFFF);
			tx += w(ZeroFont.BOLD, "ZERO", fsize);
			ZeroRender.text(g, ZeroFont.REGULAR, wText, tx, y + 3.0f, fsize, 0xFFE0D8D0);
			mark(x1, y, x2, y2);
			return y2 + 6f;
		}
		float logo = 17f;
		float lw = ZeroFont.LOGO.width("ZERO", logo, 0f);
		float pulse = 0.55f + 0.45f * (float) Math.sin(animTime() * 1.6);
		ZeroRender.glow(g, x1, y + 2f, x1 + lw, y + logo, 8f, 10f, ZeroRender.alpha(0xFFF0782C, 0.22f + 0.12f * pulse));
		ZeroRender.text(g, ZeroFont.LOGO, "ZERO", x1, y, logo, 0f, flow(0f), flow(2.2f));
		StringBuilder info = new StringBuilder();
		if (m.time.on()) info.append(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
		if (m.fps.on()) info.append(info.length() > 0 ? "  " : "").append(mc.getFps()).append(" fps");
		float bottom = y + logo + 2f;
		if (info.length() > 0) {
			ZeroRender.text(g, ZeroFont.BOLD, info.toString(), x1 + lw + 6f, y + 8f, 6.4f, MUTED);
		}
		float lx = x1 + (lw - 14f) * (0.5f + 0.5f * (float) Math.sin(animTime() * 1.1));
		ZeroRender.rect(g, lx, bottom, lx + 14f, bottom + 1f, 0.5f, flow(1f));
		mark(x1, y, x1 + lw + (info.length() > 0 ? w(ZeroFont.BOLD, info.toString(), 6.4f) + 6f : 0f), bottom + 1f);
		return bottom + 8f;
	}
	// ------------------------------------------------------------------ Modulliste (ArrayList)

	private static float moduleList(GuiGraphics g, float edge, float y, boolean right) {
		boolean hideVis = ModuleManager.MODULE_LIST.hideVisuals.on();
		List<dev.quiqzy.client.module.Module> mods = new ArrayList<>();
		for (dev.quiqzy.client.module.Module m : ModuleManager.all()) {
			boolean show = m.isEnabled() && m != ModuleManager.MODULE_LIST && m != ModuleManager.WATERMARK
					&& !(hideVis && m.category() == dev.quiqzy.client.module.Module.Category.VISUALS);
			float a = anim("ml:" + m.id(), show ? 1f : 0f, show ? 12f : 9f);
			if (a > 0.01f) mods.add(m);
		}
		float size = 6.8f;
		boolean isCat = ModuleManager.ACCENT.isCatLean();
		Map<dev.quiqzy.client.module.Module, String[]> labels = new HashMap<>();
		for (dev.quiqzy.client.module.Module m : mods) {
			String suffix = "";
			if (isCat) {
				for (Setting<?> s : m.settings()) {
					if (s instanceof Setting.Choice cs) {
						suffix = " [" + cs.get() + "]";
						break;
					}
				}
			}
			labels.put(m, new String[]{m.name(), suffix});
		}
		mods.sort((a, b) -> {
			String[] la = labels.get(a), lb = labels.get(b);
			float wa = w(ZeroFont.BOLD, la[0], size) + (la[1].isEmpty() ? 0 : w(ZeroFont.REGULAR, la[1], size * 0.9f));
			float wb = w(ZeroFont.BOLD, lb[0], size) + (lb[1].isEmpty() ? 0 : w(ZeroFont.REGULAR, lb[1], size * 0.9f));
			return Float.compare(wb, wa);
		});
		float rowH = 11f;
		for (int i = 0; i < mods.size(); i++) {
			dev.quiqzy.client.module.Module m = mods.get(i);
			float a = ANIM.getOrDefault("ml:" + m.id(), 0f);
			String[] lbl = labels.get(m);
			float nameW = w(ZeroFont.BOLD, lbl[0], size);
			float sufW = lbl[1].isEmpty() ? 0f : w(ZeroFont.REGULAR, lbl[1], size * 0.9f) + 2f;
			float tw = nameW + sufW;
			float bw = tw + 12f;
			float slide = (1f - a) * (bw + 10f);
			float x1 = right ? edge - bw + slide : edge - slide;
			float x2 = x1 + bw;
			float y1 = y;
			ZeroRender.rect(g, x1, y1, x2, y1 + rowH, 2.5f, ZeroRender.alpha(0xDC0B0C12, a));
			mark(x1, y1, x2, y1 + rowH);
			float wave = (float) ((Math.sin(time * 2.0 + i * 0.35) + 1.0) * 0.5);
			int acc = isCat ? 0xFFFFFFFF : ZeroRender.lerp(ORANGE2, ORANGE, wave);
			int barAcc = isCat ? 0xFFA1A1AA : acc;
			if (right) {
				ZeroRender.rect(g, x2 - 1.8f, y1, x2, y1 + rowH, 0.8f, ZeroRender.alpha(barAcc, a));
			} else {
				ZeroRender.rect(g, x1, y1, x1 + 1.8f, y1 + rowH, 0.8f, ZeroRender.alpha(barAcc, a));
			}
			float tx = x1 + 5.5f;
			ZeroRender.text(g, ZeroFont.BOLD, lbl[0], tx, y1 + 2.0f, size, ZeroRender.alpha(acc, a));
			if (!lbl[1].isEmpty()) {
				ZeroRender.text(g, ZeroFont.REGULAR, lbl[1], tx + nameW + 2f, y1 + 2.2f, size * 0.88f, ZeroRender.alpha(0xFFA098B0, a));
			}
			y += rowH * Math.min(1f, a * 1.3f);
		}
		return mods.isEmpty() ? y : y + 7f;
	}

	// ------------------------------------------------------------------ Potion HUD

	private static float potions(GuiGraphics g, LocalPlayer p, float right, float y) {
		var effects = new ArrayList<>(p.getActiveEffects());
		if (effects.isEmpty()) return y;
		effects.sort((a, b) -> Integer.compare(b.getDuration(), a.getDuration()));
		float cell = 24f;
		float x = right - cell;
		for (var e : effects) {
			int col = 0xFF000000 | e.getEffect().value().getColor();
			float cx = x + cell / 2f, cy = y + 10f;
			boolean low = !e.isInfiniteDuration() && e.getDuration() < 200;
			float blink = low ? 0.55f + 0.45f * (float) Math.sin(time * 8.0) : 1f;
			ZeroRender.glow(g, cx - 10f, cy - 10f, cx + 10f, cy + 10f, 10f, 5f, ZeroRender.alpha(col, 0.25f));
			ZeroRender.circle(g, cx, cy, 10f, 0xD0100C0B);
			// Restzeit als Ring aus Punkten (voll = viel Zeit)
			float frac = e.isInfiniteDuration() ? 1f : Math.min(1f, e.getDuration() / 1200f);
			ZeroRender.ring(g, cx, cy, 9f, 1.4f, 0x22FFFFFF);
			ZeroRender.arc(g, cx, cy, 9f, 1.4f, -90f, 360f * frac, ZeroRender.alpha(col, blink), ZeroRender.alpha(ZeroRender.lerp(col, 0xFFFFFFFF, 0.4f), blink));
			g.pose().pushMatrix();
			g.pose().translate(cx - 6f, cy - 6f);
			g.pose().scale(12f / 18f, 12f / 18f);
			try {
				g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,
						net.minecraft.client.gui.Gui.getMobEffectSprite(e.getEffect()), 0, 0, 18, 18);
			} catch (Throwable ignored) {
			}
			g.pose().popMatrix();
			if (e.getAmplifier() > 0) {
				String lv = roman(e.getAmplifier() + 1);
				ZeroRender.circle(g, cx + 7f, cy - 7f, 3.6f, ORANGE2);
				ZeroRender.textCentered(g, ZeroFont.BOLD, lv, cx + 7f, cy - 9.6f, 4.6f, 0xFF1E120A);
			}
			String t = e.isInfiniteDuration() ? "∞" : fmtTime(e.getDuration());
			ZeroRender.textCentered(g, ZeroFont.BOLD, t, cx, y + 22.5f, 5.8f, ZeroRender.alpha(low ? ORANGE : MUTED, blink));
			mark(x, y, x + cell, y + 29f);
			x -= cell + 2f;
		}
		return y + 34f;
	}
	private static String fmtTime(int ticks) {
		int s = ticks / 20;
		return (s / 60) + ":" + String.format("%02d", s % 60);
	}

	private static String roman(int n) {
		String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
	}

	// ------------------------------------------------------------------ Armor HUD (neben der Hotbar)

	private static void armorHud(GuiGraphics g, LocalPlayer p, float vw, float vh, float k) {
		dev.quiqzy.client.module.misc.ArmorHudModule m = ModuleManager.ARMOR_HUD;
		List<ItemStack> items = new ArrayList<>();
		for (EquipmentSlot slot : ARMOR) {
			ItemStack s = p.getItemBySlot(slot);
			if (!s.isEmpty()) items.add(s);
		}
		if (m.mainHand.on() && !p.getMainHandItem().isEmpty() && p.getMainHandItem().isDamageableItem()) items.add(p.getMainHandItem());
		if (items.isEmpty()) return;
		float cell = 30f;
		float x1 = vw / 2f + 91f * k + 10f;
		float y2 = vh - 4f;
		float y1 = y2 - 40f;
		float x2 = x1 + items.size() * cell + 8f;
		panel(g, x1, y1, x2, y2, 0f);
		for (int i = 0; i < items.size(); i++) {
			ItemStack s = items.get(i);
			float cx = x1 + 4f + i * cell;
			g.pose().pushMatrix();
			g.pose().translate(cx + 7f, y1 + 4f);
			g.pose().scale(16f / 16f * 1f, 1f);
			g.renderItem(s, 0, 0);
			g.pose().popMatrix();
			if (s.isDamageableItem()) {
				float frac = 1f - s.getDamageValue() / (float) Math.max(1, s.getMaxDamage());
				int c = frac > 0.5f ? 0xFF7BE07B : (frac > 0.2f ? ORANGE : 0xFFFF5A5A);
				ZeroRender.rect(g, cx + 4f, y1 + 24f, cx + cell - 4f, y1 + 26f, 1f, 0x30FFFFFF);
				ZeroRender.rect(g, cx + 4f, y1 + 24f, cx + 4f + (cell - 8f) * frac, y1 + 26f, 1f, c);
				if (m.percent.on()) {
					ZeroRender.textCentered(g, ZeroFont.BOLD, Math.round(frac * 100f) + "%", cx + cell / 2f, y1 + 28f, 6.4f, c);
				}
			}
		}
	}

	private static double distSq(LocalPlayer p, SusChunkModule.ChunkResult r) {
		double dx = r.pos().getMiddleBlockX() + 0.5 - p.getX();
		double dz = r.pos().getMiddleBlockZ() + 0.5 - p.getZ();
		return dx * dx + dz * dz;
	}

	// ------------------------------------------------------------------ Sound Chunks (Maschinen-Sounds)

	private static float sounds(GuiGraphics g, LocalPlayer p, SoundChunkModule sc, float x1, float y) {
		List<SoundChunkModule.ChunkData> list = sc.susChunks();
		list.sort(Comparator.comparingDouble(r -> distSqSound(p, r)));
		boolean flash = sc.isFlashing();
		float fl = anim("snd:flash", flash ? 1f : 0f, 8f);
		int lines = Math.max(1, Math.min(3, list.size()));
		float width = 190f;
		float x2 = x1 + width;
		float rowH = 13f;
		float y2 = y + 25f + lines * rowH + fl * 14f + 2f;
		panel(g, x1, y, x2, y2, fl);

		// Kopf
		float pulse = 0.5f + 0.5f * (float) Math.sin(time * 3.4);
		ZeroRender.glow(g, x1 + 9f, y + 8f, x1 + 17f, y + 16f, 4f, 4f, ZeroRender.alpha(0xFFF0782C, 0.4f + 0.4f * pulse));
		ZeroRender.circle(g, x1 + 13f, y + 12f, 2.6f, ORANGE);
		ZeroRender.text(g, ZeroFont.BOLD, "Sound Chunks", x1 + 22f, y + 7.5f, 8.6f, TEXT);
		String n = String.valueOf(list.size());
		float cw = w(ZeroFont.BOLD, n, 7.6f) + 12f;
		ZeroRender.gradient(g, x2 - 10f - cw, y + 6f, x2 - 10f, y + 18f, 6f, list.isEmpty() ? 0x30FFFFFF : ORANGE, list.isEmpty() ? 0x20FFFFFF : ORANGE2, 20f);
		ZeroRender.textCentered(g, ZeroFont.BOLD, n, x2 - 10f - cw / 2f, y + 8.4f, 7.6f, list.isEmpty() ? MUTED : 0xFF1E120A);
		ZeroRender.rect(g, x1 + 10f, y + 21f, x2 - 10f, y + 21.6f, 0f, 0x18FFFFFF);

		float ty = y + 26f;
		if (list.isEmpty()) {
			ZeroRender.text(g, ZeroFont.REGULAR, "Listening for machine sounds…", x1 + 11f, ty, 8f, MUTED);
			ty += rowH;
		} else {
			for (int i = 0; i < Math.min(3, list.size()); i++) {
				SoundChunkModule.ChunkData d = list.get(i);
				double cx = d.pos.getMiddleBlockX() + 0.5;
				double cz = d.pos.getMiddleBlockZ() + 0.5;
				int dist = (int) Math.sqrt(distSqSound(p, d));
				arrow(g, x1 + 14f, ty + 5f, relAngle(p, cx, cz), 8.5f, ORANGE);
				ZeroRender.text(g, ZeroFont.BOLD, dist + "m", x1 + 24f, ty, 8f, TEXT);
				ZeroRender.textRight(g, ZeroFont.REGULAR, d.reason(), x2 - 11f, ty, 7.4f, MUTED);
				ty += rowH;
			}
		}
		if (fl > 0.01f) {
			ZeroRender.text(g, ZeroFont.BOLD, "+ New sound chunk found", x1 + 11f, ty + 1f, 8f, ZeroRender.alpha(ORANGE, fl));
		}
		return y2 + 7f;
	}

	private static double distSqSound(LocalPlayer p, SoundChunkModule.ChunkData d) {
		double dx = d.pos.getMiddleBlockX() + 0.5 - p.getX();
		double dz = d.pos.getMiddleBlockZ() + 0.5 - p.getZ();
		return dx * dx + dz * dz;
	}

	// ------------------------------------------------------------------ Keystrokes (unten links)

	private static void keystrokes(GuiGraphics g, Minecraft mc, float x, float bottom) {
		float s = 22f, gap = 2f;
		float full = s * 3 + gap * 2;
		float y = bottom - (s * 2 + gap * 3 + 24f + 8f);
		var o = mc.options;
		key(g, mc, o.keyUp, null, x + s + gap, y, s, s);
		key(g, mc, o.keyLeft, null, x, y + s + gap, s, s);
		key(g, mc, o.keyDown, null, x + s + gap, y + s + gap, s, s);
		key(g, mc, o.keyRight, null, x + (s + gap) * 2, y + s + gap, s, s);
		float half = (full - gap) / 2f;
		int lc = cps(mc, o.keyAttack, LMB_CLICKS), rc = cps(mc, o.keyUse, RMB_CLICKS);
		mouseKey(g, mc, o.keyAttack, "LMB", lc, x, y + (s + gap) * 2, half, 24f);
		mouseKey(g, mc, o.keyUse, "RMB", rc, x + half + gap, y + (s + gap) * 2, half, 24f);
		// Leertaste als schmale Leiste
		boolean sp = InputUtil.isPhysicallyDown(mc, o.keyJump);
		float d = anim("space", sp ? 1f : 0f, 22f);
		float sy = y + (s + gap) * 2 + 24f + gap + 2f;
		float sw = full * (0.55f + 0.1f * d);
		float sx = x + (full - sw) / 2f;
		if (d > 0.01f) ZeroRender.glow(g, sx, sy, sx + sw, sy + 3f, 1.5f, 5f, ZeroRender.alpha(flow(0f), 0.5f * d));
		ZeroRender.rect(g, sx, sy, sx + sw, sy + 3f, 1.5f, ZeroRender.lerp(0x50FFFFFF, flow(0f), d));
		Boolean was = KEY_WAS.put(o.keyJump, sp);
		if (sp && (was == null || !was)) dev.quiqzy.client.util.UiSounds.key(false);
		mark(x, y, x + full, sy + 3f);
	}

	/** Maustaste: Name groß, CPS klein darunter. */
	private static void mouseKey(GuiGraphics g, Minecraft mc, KeyMapping k, String label, int cps, float x, float y, float w, float h) {
		boolean down = InputUtil.isPhysicallyDown(mc, k);
		Boolean was = KEY_WAS.put(k, down);
		if (down && (was == null || !was)) dev.quiqzy.client.util.UiSounds.key(true);
		float d = anim(k, down ? 1f : 0f, 22f);
		if (d > 0.01f) {
			ZeroRender.glow(g, x, y, x + w, y + h, 7f, 7f, ZeroRender.alpha(flow(0f), 0.45f * d));
			ZeroRender.gradient(g, x, y, x + w, y + h, 7f, ZeroRender.alpha(ORANGE, d), ZeroRender.alpha(ORANGE2, d), 35f);
		} else {
			ZeroRender.rect(g, x, y, x + w, y + h, 7f, 0x55100C0B);
		}
		int tc = ZeroRender.lerp(TEXT, 0xFF1E120A, d);
		ZeroRender.textCentered(g, ZeroFont.BOLD, label, x + w / 2f, y + 4.5f, 8f, tc);
		ZeroRender.textCentered(g, ZeroFont.BOLD, cps + " CPS", x + w / 2f, y + 14.5f, 5.2f, ZeroRender.lerp(MUTED, 0xFF3A2414, d));
	}
	private static final java.util.ArrayDeque<Long> LMB_CLICKS = new java.util.ArrayDeque<>();
	private static final java.util.ArrayDeque<Long> RMB_CLICKS = new java.util.ArrayDeque<>();
	private static final Map<Object, Boolean> WAS_DOWN = new HashMap<>();
	private static final Map<Object, Boolean> KEY_WAS = new HashMap<>();

	/** Klicks der letzten Sekunde (Flanken pro Frame gezählt). */
	private static int cps(Minecraft mc, KeyMapping k, java.util.ArrayDeque<Long> q) {
		boolean down = InputUtil.isPhysicallyDown(mc, k);
		long now = System.currentTimeMillis();
		if (down && !WAS_DOWN.getOrDefault(k, false)) q.addLast(now);
		WAS_DOWN.put(k, down);
		while (!q.isEmpty() && now - q.peekFirst() > 1000L) q.pollFirst();
		return q.size();
	}

	private static void key(GuiGraphics g, Minecraft mc, KeyMapping k, String label, float x, float y, float w, float h) {
		if (label == null) {
			label = k.getTranslatedKeyMessage().getString().toUpperCase(java.util.Locale.ROOT);
			if (label.length() > 3) label = label.substring(0, 3);
		}
		boolean down = InputUtil.isPhysicallyDown(mc, k);
		Boolean was = KEY_WAS.put(k, down);
		if (down && (was == null || !was)) dev.quiqzy.client.util.UiSounds.key(false);
		float d = anim(k, down ? 1f : 0f, 22f);
		mark(x, y, x + w, y + h);
		// nicht gedrückt: nur weicher Schatten hinter dem Buchstaben; gedrückt: Akzent-Kachel springt rein
		if (d > 0.01f) {
			float in = 1.5f * (1f - d);
			ZeroRender.glow(g, x + in, y + in, x + w - in, y + h - in, 7f, 7f, ZeroRender.alpha(flow(0f), 0.45f * d));
			ZeroRender.gradient(g, x + in, y + in, x + w - in, y + h - in, 7f, ZeroRender.alpha(ORANGE, d), ZeroRender.alpha(ORANGE2, d), 35f);
		} else {
			ZeroRender.rect(g, x, y, x + w, y + h, 7f, 0x40100C0B);
		}
		float size = 10f;
		ZeroRender.textCentered(g, ZeroFont.BOLD, label, x + w / 2f, y + h / 2f - size * 0.46f, size, ZeroRender.lerp(TEXT, 0xFF1E120A, d));
	}
	// ------------------------------------------------------------------ Rüstung (Vanilla-Koordinaten)

	private static void armor(GuiGraphics g, Minecraft mc, LocalPlayer p) {
		int x = g.guiWidth() / 2 + 10;
		int y = g.guiHeight() - 66;
		if (p.isCreative() || p.isSpectator()) y += 26;
		for (EquipmentSlot slot : ARMOR) {
			ItemStack s = p.getItemBySlot(slot);
			if (!s.isEmpty()) {
				g.renderItem(s, x, y);
				g.renderItemDecorations(mc.font, s, x, y);
			}
			x += 18;
		}
	}
}
