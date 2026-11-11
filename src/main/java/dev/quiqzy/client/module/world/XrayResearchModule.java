package dev.quiqzy.client.module.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Xray Research: analysiert client-sichtbare Chunk-Daten und trennt sauber zwischen
 * tatsächlichen Blockinformationen (aus den geladenen Chunks gelesen) und statistischen
 * Vorhersagen (Modell auf Basis der Umgebungs-Dichte).
 *
 * - Chunk Data Analyzer: Chunk Load/Unload, Section-Scans mit Palette-Vorabcheck,
 *   Block-State-Update-Tracking (inkl. "Reveals": Stein -> Erz sichtbar geworden)
 * - Block Analysis: Erz-Verteilung pro Chunk + laufende Umgebungsstatistik (async)
 * - Research Overlay: Chunk-Markierung mit Confidence-Level (siehe XrayResearchRenderer)
 *
 * Performance: Palette-Vorabcheck überspringt fast alle Sections, Scan-Budget pro Tick,
 * Statistik-Aggregation läuft in einem eigenen Daemon-Thread (nur unveränderbare
 * Snapshots werden übergeben).
 */
public final class XrayResearchModule extends Module {
	// ------------------------------------------------------------------ Erz-Tabelle
	public static final int COAL = 0, IRON = 1, COPPER = 2, GOLD = 3, REDSTONE = 4, LAPIS = 5,
			DIAMOND = 6, EMERALD = 7, QUARTZ = 8, NETHER_GOLD = 9, DEBRIS = 10, ORE_COUNT = 11;

	public static final String[] ORE_NAMES = {"Coal", "Iron", "Copper", "Gold", "Redstone",
			"Lapis", "Diamond", "Emerald", "Quartz", "Nether Gold", "Debris"};
	public static final String[] ORE_SHORT = {"Co", "Fe", "Cu", "Au", "Rs", "Lp", "Di", "Em", "Qz", "Ng", "Db"};
	public static final int[] ORE_COLORS = {0xFF8C8C8C, 0xFFD8AF93, 0xFFC0703D, 0xFFFCEE4B, 0xFFE32213,
			0xFF2E5C9E, 0xFF4AEDD9, 0xFF17DD62, 0xFFEDE0D4, 0xFFF9A833, 0xFF6B4A3A};
	/** Seltene Erze, für die einzelne Boxen gerendert werden können. */
	private static final boolean[] RARE = {false, false, false, false, false, false, true, true, false, false, true};

	/** Grobe Vanilla-Erzdichte (Blöcke pro 1000) als Grundlage der statistischen Vorhersage. */
	private static final float[] DENSITY_OVERWORLD = {5.0f, 7.0f, 5.0f, 1.3f, 6.0f, 1.4f, 1.1f, 0.35f, 0f, 0f, 0f};
	private static final float[] DENSITY_NETHER = {0f, 0f, 0f, 1.3f, 0f, 0f, 0f, 0f, 15.0f, 8.0f, 0.15f};

	// ------------------------------------------------------------------ Settings
	public final Setting.Int scanRadius = add(new Setting.Int("scan_radius", "Scan Radius", 8, 2, 32, " Ch"));
	public final Setting.Int chunksPerTick = add(new Setting.Int("chunks_per_tick", "Scan Budget", 3, 1, 8, "/t"));
	public final Setting.Bool overlay = add(new Setting.Bool("overlay", "Chunk Overlay", true));
	public final Setting.Bool throughWalls = add(new Setting.Bool("through_walls", "Through Blocks", true));
	public final Setting.Choice overlayMode = add(new Setting.Choice("overlay_mode", "Overlay Style", "Fill + Edge",
			"Fill + Edge", "Edges", "Fill"));
	public final Setting.Int overlayOpacity = add(new Setting.Int("overlay_opacity", "Overlay Opacity", 70, 10, 100, "%"));
	public final Setting.Int minOres = add(new Setting.Int("min_ores", "Highlight Threshold", 8, 1, 200, " ores"));
	public final Setting.Choice colorMode = add(new Setting.Choice("color_mode", "Color By", "Confidence",
			"Confidence", "Accent"));
	public final Setting.Color accentColor = add(new Setting.Color("accent_color", "Accent Color", 0xA78BFA));
	public final Setting.Bool rareBoxes = add(new Setting.Bool("rare_boxes", "Rare Ore Boxes", true));
	public final Setting.Bool predicted = add(new Setting.Bool("predicted", "Show Predictions", true));
	public final Setting.Bool revealBoxes = add(new Setting.Bool("reveal_boxes", "Reveal Boxes", true));
	public final Setting.Bool trackUpdates = add(new Setting.Bool("track_updates", "Track Block Updates", true));
	public final Setting.Bool infoPanel = add(new Setting.Bool("info_panel", "Info Panel", true));

	// ------------------------------------------------------------------ Daten
	/** Analyse-Ergebnis eines Chunks (nur Tick-Thread schreibt, via ConcurrentHashMap publiziert). */
	public static final class ChunkData {
		public final ChunkPos pos;
		public final int[] counts = new int[ORE_COUNT];
		public final List<BlockPos> rarePos = new ArrayList<>();
		public final List<BlockPos> reveals = new ArrayList<>();
		public int sectionsScanned, sectionsTotal, totalOres;
		public int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		public int updates, revealsCount;
		public long firstSeen, lastSeen;
		/** 0..100: wie viel des Chunks direkt beobachtet wurde (async gesetzt). */
		public volatile float confidence;

		ChunkData(ChunkPos pos) {
			this.pos = pos;
		}

		/** Kurze Zusammenfassung der Top-2-Erze, z. B. "128 Fe · 44 Co". */
		public String summary() {
			int a = -1, b = -1;
			for (int i = 0; i < ORE_COUNT; i++) {
				if (counts[i] <= 0) continue;
				if (a < 0 || counts[i] > counts[a]) {
					b = a;
					a = i;
				} else if (b < 0 || counts[i] > counts[b]) {
					b = i;
				}
			}
			if (a < 0) return "-";
			StringBuilder s = new StringBuilder().append(counts[a]).append(' ').append(ORE_SHORT[a]);
			if (b >= 0) s.append(" · ").append(counts[b]).append(' ').append(ORE_SHORT[b]);
			return s.toString();
		}
	}

	/** Statistische Vorhersage für einen noch nicht gescannten Chunk. */
	public record Prediction(ChunkPos pos, float expected, float confidence) {
	}

	private final Map<Long, ChunkData> data = new ConcurrentHashMap<>();
	/** Vorhersagen für geladene, aber noch nicht gescannte Chunks (Tick-Thread, volatile Publikation). */
	private volatile List<Prediction> predictions = List.of();

	private final ArrayDeque<Long> queue = new ArrayDeque<>();
	private final Set<Long> queued = new HashSet<>();

	/** Laufende Umgebungsstatistik (async gepflegt, volatile gelesen). */
	private volatile float[] density = new float[ORE_COUNT];
	private volatile int samples;

	private ClientLevel lastLevel;
	private int rescanTimer;
	private int predictionTimer;
	/** Block-Updates seit dem letzten Tick (für die Rate im Panel). */
	private int recentUpdates;
	private int updatesPerSec;

	private static final ExecutorService ANALYSIS = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "ZERO Xray Research");
		t.setDaemon(true);
		return t;
	});

	public XrayResearchModule() {
		super("xray_research", "Xray Research", "Analyzes chunk data: ore distribution, block updates and statistical predictions", Category.WORLD);
	}

	// ------------------------------------------------------------------ Lifecycle

	@Override
	protected void onEnable() {
		reset();
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null && mc.player != null) {
			lastLevel = mc.level;
			enqueueAllInRange(mc.level, mc.player);
		}
	}

	@Override
	protected void onDisable() {
		reset();
	}

	private void reset() {
		data.clear();
		queue.clear();
		queued.clear();
		predictions = List.of();
		density = new float[ORE_COUNT];
		samples = 0;
		lastLevel = null;
		rescanTimer = 0;
		recentUpdates = 0;
		updatesPerSec = 0;
	}

	/** Von ClientChunkEvents.CHUNK_LOAD. */
	public void onChunkLoad(ClientLevel level, LevelChunk chunk) {
		if (!isEnabled()) return;
		enqueue(chunk.getPos().toLong());
	}

	/** Von ClientChunkEvents.CHUNK_UNLOAD. */
	public void onChunkUnload(ClientLevel level, LevelChunk chunk) {
		long key = chunk.getPos().toLong();
		data.remove(key);
		queued.remove(key);
	}

	/** Von ClientLevelMixin.sendBlockUpdated: Block-State-Updates tracken + Reveals erkennen. */
	public void onBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState) {
		if (!isEnabled() || !trackUpdates.on()) return;
		ChunkData d = data.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
		if (d == null) return;
		d.updates++;
		recentUpdates++;
		int oldOre = oreIndex(oldState);
		int newOre = oreIndex(newState);
		if (newOre >= 0 && oldOre < 0) {
			d.revealsCount++;
			if (d.reveals.size() < 32) d.reveals.add(pos.immutable());
		}
		d.lastSeen = System.currentTimeMillis();
	}

	// ------------------------------------------------------------------ Tick / Scan

	@Override
	public void onTick(Minecraft mc) {
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		if (level == null || player == null) return;

		if (level != lastLevel) {
			reset();
			lastLevel = level;
			enqueueAllInRange(level, player);
		}

		ChunkPos center = player.chunkPosition();
		int r = scanRadius.get();

		if (++rescanTimer >= 400) { // alle 20 s Chunks in Reichweite erneut einsammeln
			rescanTimer = 0;
			enqueueAllInRange(level, player);
		}

		int budget = chunksPerTick.get();
		while (budget > 0 && !queue.isEmpty()) {
			long key = queue.poll();
			queued.remove(key);

			int cx = ChunkPos.getX(key);
			int cz = ChunkPos.getZ(key);
			if (Math.abs(cx - center.x) > r || Math.abs(cz - center.z) > r) continue;

			LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
			if (chunk == null) continue;

			scan(chunk);
			budget--;
		}

		// Outside-Radius aufräumen
		data.values().removeIf(d -> Math.abs(d.pos.x - center.x) > r || Math.abs(d.pos.z - center.z) > r);

		// Update-Rate (geglättet, für das Panel)
		updatesPerSec = (updatesPerSec + recentUpdates * 20) / 2;
		recentUpdates = 0;

		// Vorhersagen alle 40 Ticks neu berechnen (billig, aber nicht pro Frame)
		if (++predictionTimer >= 40) {
			predictionTimer = 0;
			rebuildPredictions(level, center, r);
		}
	}

	private void enqueueAllInRange(ClientLevel level, LocalPlayer player) {
		ChunkPos center = player.chunkPosition();
		int r = scanRadius.get();
		for (int ring = 0; ring <= r; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
					int cx = center.x + dx;
					int cz = center.z + dz;
					long key = ChunkPos.asLong(cx, cz);
					if (!data.containsKey(key) && level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) != null) {
						enqueue(key);
					}
				}
			}
		}
	}

	private void enqueue(long key) {
		if (queued.add(key)) queue.add(key);
	}

	/** Scannt einen Chunk: Palette-Vorabcheck pro Section, dann Zählung. */
	private void scan(LevelChunk chunk) {
		ChunkPos pos = chunk.getPos();
		long key = pos.toLong();
		ChunkData d = new ChunkData(pos);
		d.firstSeen = System.currentTimeMillis();
		d.lastSeen = d.firstSeen;

		LevelChunkSection[] sections = chunk.getSections();
		int baseX = pos.getMinBlockX();
		int baseZ = pos.getMinBlockZ();

		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];
			if (section == null || section.hasOnlyAir()) continue;
			d.sectionsTotal++;
			if (!section.getStates().maybeHas(XrayResearchModule::isOreState)) continue;
			d.sectionsScanned++;

			int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
			boolean counted = false;
			for (int y = 0; y < 16 && d.totalOres < 4096; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						BlockState st = section.getBlockState(x, y, z);
						int ore = oreIndex(st);
						if (ore < 0) continue;

						d.counts[ore]++;
						d.totalOres++;
						int wy = baseY + y;
						d.minY = Math.min(d.minY, wy);
						d.maxY = Math.max(d.maxY, wy);
						if (RARE[ore] && d.rarePos.size() < 64) {
							d.rarePos.add(new BlockPos(baseX + x, wy, baseZ + z));
						}
					}
				}
			}
		}

		// Immer speichern (auch ore-freie Chunks), damit sie nicht immer wieder gescannt werden
		d.confidence = 100f; // direkte Beobachtung = tatsächliche Client-Daten
		data.put(key, d);

		// Async: Umgebungsstatistik (Dichte) aus dem Snapshot fortschreiben
		submitStats(d, sections.length);
	}

	/** Nur auf unveränderlichen Werten: Dichte-EMA im Analysis-Thread aktualisieren. */
	private void submitStats(ChunkData snapshot, int sectionCount) {
		int[] counts = snapshot.counts.clone();
		int sections = Math.max(1, sectionCount);
		ANALYSIS.execute(() -> {
			float[] cur = density;
			int s = samples;
			float volume = sections * 4096f / 1000f;
			float w = 1f / (s + 1f);
			for (int i = 0; i < ORE_COUNT; i++) {
				float per1000 = counts[i] / volume;
				cur[i] = cur[i] * (1f - w) + per1000 * w;
			}
			samples = Math.min(s + 1, 256);
			density = cur;
		});
	}

	// ------------------------------------------------------------------ Vorhersagen

	/** Erwartete Erz-Anzahl für einen Chunk im Y-Band des Spielers (statistisches Modell). */
	private float expected(ChunkPos pos, ClientLevel level, LocalPlayer player, boolean nether) {
		float[] table = nether ? DENSITY_NETHER : DENSITY_OVERWORLD;
		int bandMin = Math.max(level.getMinY(), player.blockPosition().getY() - 48);
		int bandMax = Math.min(level.getMaxY(), player.blockPosition().getY() + 48);
		float h = Math.max(16f, bandMax - bandMin);
		float volume = 16f * 16f * h / 1000f;
		float sum = 0f;
		float[] cur = density;
		int s = samples;
		// Mischung: Modell-Tabelle + beobachtete Umgebungs-Dichte (je mehr Samples, desto mehr Gewicht)
		float modelW = 4f / (4f + s);
		for (int i = 0; i < ORE_COUNT; i++) {
			float per1000 = table[i] * modelW + (s > 0 ? cur[i] * (1f - modelW) : 0f);
			sum += per1000 * volume;
		}
		return sum;
	}

	private void rebuildPredictions(ClientLevel level, ChunkPos center, int r) {
		LocalPlayer player = level.players().isEmpty() ? null : Minecraft.getInstance().player;
		if (player == null) {
			predictions = List.of();
			return;
		}
		boolean nether = level.dimensionType().hasSkyLight() == false && level.getMaxY() < 200;
		List<Prediction> list = new ArrayList<>();
		float[] cur = density;
		int s = samples;
		// Confidence der Vorhersage aus der Sample-Basis: mehr gescannte Nachbarn = verlässlicher
		float conf = 25f + 70f * (1f - 1f / (1f + s / 6f));
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				long key = ChunkPos.asLong(center.x + dx, center.z + dz);
				if (data.containsKey(key)) continue;
				if (level.getChunkSource().getChunk(center.x + dx, center.z + dz, ChunkStatus.FULL, false) == null) continue;
				float exp = expected(new ChunkPos(center.x + dx, center.z + dz), level, player, nether);
				if (exp < minOres.get() * 0.5f) continue;
				list.add(new Prediction(new ChunkPos(center.x + dx, center.z + dz), exp, conf));
			}
		}
		predictions = List.copyOf(list);
	}

	// ------------------------------------------------------------------ Abfragen für Render/HUD

	/** Chunks mit tatsächlichen Erz-Daten über dem Schwellwert. */
	public List<ChunkData> highlights() {
		List<ChunkData> list = new ArrayList<>();
		for (ChunkData d : data.values()) {
			if (d.totalOres >= minOres.get()) list.add(d);
		}
		return list;
	}

	/** Alle analysierten Chunks (auch unter dem Schwellwert) – für Rare/Reveal-Boxen. */
	public List<ChunkData> allData() {
		return new ArrayList<>(data.values());
	}

	/** Aktuelle statistische Vorhersagen. */
	public List<Prediction> predictions() {
		return predictions;
	}

	/** Chunk-Daten an Position (für Block-Updates etc.). */
	public ChunkData dataAt(ChunkPos pos) {
		return data.get(pos.toLong());
	}

	public int analyzedCount() {
		return data.size();
	}

	public int pendingScans() {
		return queue.size();
	}

	public int updatesPerSec() {
		return updatesPerSec;
	}

	public int sampleCount() {
		return samples;
	}

	/** Distanz-Quadrat vom Spieler zur Chunk-Mitte (fürs HUD-Sorting). */
	public static double distSq(LocalPlayer p, ChunkPos pos) {
		double cx = pos.getMiddleBlockX() + 0.5;
		double cz = pos.getMiddleBlockZ() + 0.5;
		double dx = p.getX() - cx;
		double dz = p.getZ() - cz;
		return dx * dx + dz * dz;
	}

	// ------------------------------------------------------------------ Erz-Erkennung

	public static boolean isOreState(BlockState st) {
		return oreIndex(st) >= 0;
	}

	/** Index in die Erz-Tabellen, -1 wenn kein Erz. */
	public static int oreIndex(BlockState st) {
		if (st.is(Blocks.COAL_ORE) || st.is(Blocks.DEEPSLATE_COAL_ORE)) return COAL;
		if (st.is(Blocks.IRON_ORE) || st.is(Blocks.DEEPSLATE_IRON_ORE)) return IRON;
		if (st.is(Blocks.COPPER_ORE) || st.is(Blocks.DEEPSLATE_COPPER_ORE)) return COPPER;
		if (st.is(Blocks.GOLD_ORE) || st.is(Blocks.DEEPSLATE_GOLD_ORE) || st.is(Blocks.NETHER_GOLD_ORE)) return GOLD;
		if (st.is(Blocks.REDSTONE_ORE) || st.is(Blocks.DEEPSLATE_REDSTONE_ORE)) return REDSTONE;
		if (st.is(Blocks.LAPIS_ORE) || st.is(Blocks.DEEPSLATE_LAPIS_ORE)) return LAPIS;
		if (st.is(Blocks.DIAMOND_ORE) || st.is(Blocks.DEEPSLATE_DIAMOND_ORE)) return DIAMOND;
		if (st.is(Blocks.EMERALD_ORE) || st.is(Blocks.DEEPSLATE_EMERALD_ORE)) return EMERALD;
		if (st.is(Blocks.NETHER_QUARTZ_ORE)) return QUARTZ;
		if (st.is(Blocks.ANCIENT_DEBRIS)) return DEBRIS;
		return -1;
	}
}
