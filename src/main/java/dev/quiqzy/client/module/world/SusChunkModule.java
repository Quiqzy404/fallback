package dev.quiqzy.client.module.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Sus Chunk: sucht in geladenen Chunks nach Spuren, dass dort lange ein Spieler war.
 * Anzeichen (einzeln an/aus):
 *  - Amethyst: ausgewachsene Amethyst-Cluster (wachsen nur, wenn der Chunk lange geladen ist)
 *  - Rotated Deepslate: Deepslate, der nicht senkrecht steht (natürlich immer senkrecht -> von Spielern gesetzt)
 *  - Grown Kelp: voll ausgewachsene Kelp-Spitzen (Alter 25)
 * Erreicht ein Anzeichen seinen Grenzwert, wird der Chunk markiert (Kasten + Strahl in der Welt + HUD).
 */
public final class SusChunkModule extends Module {
	/** Wie viele Chunks pro Tick gescannt werden (verhindert Lags). */
	private static final int CHUNKS_PER_TICK = 4;
	/** Alle 10 Sekunden werden die Chunks in Reichweite erneut gescannt. */
	private static final int RESCAN_INTERVAL_TICKS = 200;
	/** Maximal gespeicherte Cluster-Positionen pro Chunk (nur für die Boxen). */
	private static final int MAX_STORED_POSITIONS = 128;

	public final Setting.Bool amethyst = add(new Setting.Bool("amethyst", "Amethyst Clusters", true));
	public final Setting.Int minClusters = add(new Setting.Int("min_clusters", "Min. Cluster", 4, 1, 30));
	public final Setting.Bool deepslate = add(new Setting.Bool("deepslate", "Rotated Deepslate", true));
	public final Setting.Int minDeepslate = add(new Setting.Int("min_deepslate", "Min. Deepslate", 2, 1, 30));
	public final Setting.Bool kelp = add(new Setting.Bool("kelp", "Grown Kelp", false));
	public final Setting.Int minKelp = add(new Setting.Int("min_kelp", "Min. Kelp", 12, 1, 60));
	public final Setting.Int radius = add(new Setting.Int("radius", "Radius", 8, 2, 32, " Ch"));
	public final Setting.Bool beams = add(new Setting.Bool("beams", "Chunk Highlight", true));
	public final Setting.Bool throughWalls = add(new Setting.Bool("through_walls", "Through Blocks", true));
	public final Setting.Bool clusterBoxes = add(new Setting.Bool("cluster_boxes", "Block Boxes", true));
	public final Setting.Bool hud = add(new Setting.Bool("hud", "HUD", true));
	public final Setting.Bool sound = add(new Setting.Bool("sound", "Sound on Find", true));

	private final Map<Long, ChunkResult> results = new HashMap<>();
	private final ArrayDeque<Long> queue = new ArrayDeque<>();
	private final Set<Long> queued = new HashSet<>();
	private final Set<Long> announced = new HashSet<>();

	private ClientLevel lastLevel;
	private int rescanTimer;
	private int lastSig = -1;

	/** Für den "Neu gefunden"-Flash im HUD. */
	private long flashUntil;
	private ChunkPos lastFound;

	public SusChunkModule() {
		super("sus_chunk", "Sus Chunk", "Marks chunks with lots of fully grown amethyst clusters", Category.WORLD);
	}

	/** Ergebnis eines Chunk-Scans. */
	public record ChunkResult(ChunkPos pos, int count, int deepslate, int kelp, int minY, int maxY, List<BlockPos> clusters) {
		/** Kurzer Grund fürs HUD, z. B. "6 amethyst · 3 deepslate". */
		public String reason() {
			StringBuilder b = new StringBuilder();
			if (count > 0) b.append(count).append(" amethyst");
			if (deepslate > 0) b.append(b.length() > 0 ? " · " : "").append(deepslate).append(" deepslate");
			if (kelp > 0) b.append(b.length() > 0 ? " · " : "").append(kelp).append(" kelp");
			return b.toString();
		}
	}

	/** Welche Blöcke gelten als Anzeichen (je nach Einstellung). */
	private int kind(BlockState st) {
		if (amethyst.on() && st.is(Blocks.AMETHYST_CLUSTER)) return 1;
		if (deepslate.on() && st.is(Blocks.DEEPSLATE) && st.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y) return 2;
		if (kelp.on() && st.is(Blocks.KELP) && st.getValue(KelpBlock.AGE) >= KelpBlock.MAX_AGE) return 3;
		return 0;
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
		results.clear();
		queue.clear();
		queued.clear();
		announced.clear();
		lastLevel = null;
		rescanTimer = 0;
		lastFound = null;
		flashUntil = 0;
	}

	/** Aufgerufen von ClientChunkEvents.CHUNK_LOAD. */
	public void onChunkLoad(ClientLevel level, LevelChunk chunk) {
		if (!isEnabled()) return;
		enqueue(chunk.getPos().toLong());
	}

	/** Aufgerufen von ClientChunkEvents.CHUNK_UNLOAD. */
	public void onChunkUnload(ClientLevel level, LevelChunk chunk) {
		long key = chunk.getPos().toLong();
		results.remove(key);
		queued.remove(key);
		// announced bleibt, damit derselbe Chunk nicht bei jedem Neuladen wieder "neu" blinkt
	}

	// ------------------------------------------------------------------ Tick / Scan

	@Override
	public void onTick(Minecraft mc) {
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		if (level == null || player == null) return;

		// Welt / Dimension gewechselt -> alles neu
		if (level != lastLevel) {
			reset();
			lastLevel = level;
			enqueueAllInRange(level, player);
		}

		// Anzeichen an/aus geändert -> alles neu scannen
		int sig = (amethyst.on() ? 1 : 0) | (deepslate.on() ? 2 : 0) | (kelp.on() ? 4 : 0);
		if (sig != lastSig) {
			lastSig = sig;
			results.clear();
			queue.clear();
			queued.clear();
			enqueueAllInRange(level, player);
		}

		if (++rescanTimer >= RESCAN_INTERVAL_TICKS) {
			rescanTimer = 0;
			enqueueAllInRange(level, player);
		}

		ChunkPos center = player.chunkPosition();
		int r = radius.get();
		int budget = CHUNKS_PER_TICK;

		while (budget > 0 && !queue.isEmpty()) {
			long key = queue.poll();
			queued.remove(key);

			int cx = ChunkPos.getX(key);
			int cz = ChunkPos.getZ(key);
			if (Math.abs(cx - center.x) > r || Math.abs(cz - center.z) > r) {
				results.remove(key);
				continue;
			}

			LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
			if (chunk == null) {
				results.remove(key);
				continue;
			}

			scan(chunk);
			budget--;
		}

		// Ergebnisse außerhalb des Radius rauswerfen
		results.values().removeIf(res -> Math.abs(res.pos().x - center.x) > r || Math.abs(res.pos().z - center.z) > r);
	}

	private void enqueueAllInRange(ClientLevel level, LocalPlayer player) {
		ChunkPos center = player.chunkPosition();
		int r = radius.get();

		// von innen nach außen, damit nahe Chunks zuerst dran sind
		for (int ring = 0; ring <= r; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
					int cx = center.x + dx;
					int cz = center.z + dz;
					if (level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) != null) {
						enqueue(ChunkPos.asLong(cx, cz));
					}
				}
			}
		}
	}

	private void enqueue(long key) {
		if (queued.add(key)) queue.add(key);
	}

	private void scan(LevelChunk chunk) {
		ChunkPos pos = chunk.getPos();
		long key = pos.toLong();

		LevelChunkSection[] sections = chunk.getSections();
		int count = 0;
		int deep = 0;
		int kelps = 0;
		int minY = Integer.MAX_VALUE;
		int maxY = Integer.MIN_VALUE;
		List<BlockPos> positions = new ArrayList<>();

		int baseX = pos.getMinBlockX();
		int baseZ = pos.getMinBlockZ();

		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];
			if (section == null || section.hasOnlyAir()) continue;

			// Schneller Vorab-Check über die Palette: kommt der Block in der Section überhaupt vor?
			if (!section.getStates().maybeHas(state -> kind(state) != 0)) continue;

			int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));

			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						int k = kind(section.getBlockState(x, y, z));
						if (k == 0) continue;

						int wy = baseY + y;
						if (k == 1) count++;
						else if (k == 2) deep++;
						else kelps++;
						minY = Math.min(minY, wy);
						maxY = Math.max(maxY, wy);
						if (positions.size() < MAX_STORED_POSITIONS) {
							positions.add(new BlockPos(baseX + x, wy, baseZ + z));
						}
					}
				}
			}
		}

		if (count == 0 && deep == 0 && kelps == 0) {
			results.remove(key);
			return;
		}

		ChunkResult result = new ChunkResult(pos, count, deep, kelps, minY, maxY, Collections.unmodifiableList(positions));
		results.put(key, result);

		if (isSus(result) && announced.add(key)) {
			lastFound = pos;
			flashUntil = System.currentTimeMillis() + 3500;
			if (sound.on()) dev.quiqzy.client.util.UiSounds.found();
		}
	}

	// ------------------------------------------------------------------ Abfragen für Render/HUD/GUI

	public boolean isSus(ChunkResult result) {
		return (amethyst.on() && result.count() >= minClusters.get())
				|| (deepslate.on() && result.deepslate() >= minDeepslate.get())
				|| (kelp.on() && result.kelp() >= minKelp.get());
	}

	/** Alle aktuell markierten Chunks. */
	public List<ChunkResult> susChunks() {
		List<ChunkResult> list = new ArrayList<>();
		for (ChunkResult res : results.values()) {
			if (isSus(res)) list.add(res);
		}
		return list;
	}

	public int scannedWithClusters() {
		return results.size();
	}

	public int pendingScans() {
		return queue.size();
	}

	public boolean isFlashing() {
		return lastFound != null && System.currentTimeMillis() < flashUntil;
	}

	public long flashUntil() {
		return flashUntil;
	}

	public ChunkPos lastFound() {
		return lastFound;
	}

	/** Bei Radius-Änderung im GUI sofort neu einsammeln. */
	public void requestRescan() {
		rescanTimer = RESCAN_INTERVAL_TICKS;
	}
}
