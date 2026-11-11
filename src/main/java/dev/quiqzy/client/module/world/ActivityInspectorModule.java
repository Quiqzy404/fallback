package dev.quiqzy.client.module.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Activity Inspector: findet Spieler-Aktivität, die Anti-Xray nicht verstecken kann.
 *
 * Anti-Xray ersetzt Erze durch Stein - aber LUFT wird nie ersetzt. Ein gegrabener
 * Schacht (1x1 runter, 3x1 runter, ...) ist damit echte Client-Daten.
 *
 * Analyse pro Chunk:
 * - Spalten (x/z) von der Oberfläche abwärts: vertikale Luft-Runs zwischen festen Blöcken
 *   = "Loch nach unten" (ab Mindesttiefe, jede Größe zählt)
 * - Enger Schacht (1x1 / 3x1): an Mittel- und Unterzelle max. 2 von 4 Luft-Nachbarn
 * - Offenes Loch (Höhle/Ravin): mehr Luft-Nachbarn
 *
 * Ein Chunk wird markiert, wenn er ENGE SCHÄCHTE hat und insgesamt nur WENIGE Löcher -
 * natürliches Terrain hat viele/keine engen Schächte, gegrabene Basen genau 1-2.
 */
public final class ActivityInspectorModule extends Module {
	public final Setting.Int scanRadius = add(new Setting.Int("scan_radius", "Scan Radius", 8, 2, 32, " Ch"));
	public final Setting.Int chunksPerTick = add(new Setting.Int("chunks_per_tick", "Scan Budget", 2, 1, 8, "/t"));
	public final Setting.Int minDepth = add(new Setting.Int("min_depth", "Min. Shaft Depth", 4, 2, 24, " bl"));
	public final Setting.Int maxHoles = add(new Setting.Int("max_holes", "Max. Holes (Few)", 6, 1, 40, " holes"));
	public final Setting.Bool onlyNarrow = add(new Setting.Bool("only_narrow", "Only 1x1/3x1 Shafts", false));
	public final Setting.Bool smartFilter = add(new Setting.Bool("smart_filter", "Smart Filter", true));
	public final Setting.Bool overlay = add(new Setting.Bool("overlay", "Chunk Overlay", true));
	public final Setting.Bool throughWalls = add(new Setting.Bool("through_walls", "Through Blocks", true));
	public final Setting.Choice overlayMode = add(new Setting.Choice("overlay_mode", "Overlay Style", "Fill + Edge",
			"Fill + Edge", "Edges", "Fill"));
	public final Setting.Int overlayOpacity = add(new Setting.Int("overlay_opacity", "Overlay Opacity", 70, 10, 100, "%"));
	public final Setting.Color markColor = add(new Setting.Color("mark_color", "Mark Color", 0x4FC3F7));
	public final Setting.Bool shaftBoxes = add(new Setting.Bool("shaft_boxes", "Shaft Boxes", true));

	/** Analyse-Ergebnis eines Chunks. */
	public static final class ChunkMark {
		public final ChunkPos pos;
		public int holes, narrowShafts, openHoles;
		public int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		public final List<BlockPos> shafts = new ArrayList<>();

		ChunkMark(ChunkPos pos) {
			this.pos = pos;
		}

		public String summary() {
			return narrowShafts + " shaft · " + holes + " holes";
		}
	}

	private final Map<Long, ChunkMark> marks = new ConcurrentHashMap<>();
	private final ArrayDeque<Long> queue = new ArrayDeque<>();
	private final Set<Long> queued = new HashSet<>();

	private ClientLevel lastLevel;
	private int rescanTimer;

	public ActivityInspectorModule() {
		super("activity_inspector", "Activity Inspector", "Finds player-dug shafts (1x1/3x1 holes) in chunks with few holes - invisible to Anti-Xray", Category.WORLD);
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
		marks.clear();
		queue.clear();
		queued.clear();
		lastLevel = null;
		rescanTimer = 0;
	}

	/** Von ClientChunkEvents.CHUNK_LOAD. */
	public void onChunkLoad(ClientLevel level, LevelChunk chunk) {
		if (!isEnabled()) return;
		enqueue(chunk.getPos().toLong());
	}

	/** Von ClientChunkEvents.CHUNK_UNLOAD. */
	public void onChunkUnload(ClientLevel level, LevelChunk chunk) {
		long key = chunk.getPos().toLong();
		marks.remove(key);
		queued.remove(key);
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

		if (++rescanTimer >= 400) {
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

			LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
			if (chunk == null) continue;

			scan(chunk, level);
			budget--;
		}

		marks.values().removeIf(mk -> Math.abs(mk.pos.x - center.x) > r || Math.abs(mk.pos.z - center.z) > r);
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
					if (level.getChunkSource().getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) != null) {
						enqueue(ChunkPos.asLong(cx, cz));
					}
				}
			}
		}
	}

	private void enqueue(long key) {
		if (queued.add(key)) queue.add(key);
	}

	/**
	 * Scannt alle 256 Spalten: von der Oberfläche abwärts Luft-Runs finden,
	 * klassifizieren und den Chunk bei wenigen Löchern + engem Schacht markieren.
	 */
	private void scan(LevelChunk chunk, ClientLevel level) {
		ChunkPos pos = chunk.getPos();
		int minX = pos.getMinBlockX();
		int minZ = pos.getMinBlockZ();
		int worldMinY = level.getMinY();
		LevelChunkSection[] sections = chunk.getSections();

		ChunkMark mark = new ChunkMark(pos);

		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
				if (top <= worldMinY + minDepth.get()) continue;

				int wx = minX + x;
				int wz = minZ + z;
				int runTop = Integer.MAX_VALUE; // Oberkante des aktuellen Luft-Runs

				for (int y = top - 1; y >= worldMinY; y--) {
					LevelChunkSection s = sections[chunk.getSectionIndex(y)];
					boolean air = s == null || s.getBlockState(x, y & 15, z).isAir();

					if (air) {
						if (runTop == Integer.MAX_VALUE) runTop = y;
					} else {
						if (runTop != Integer.MAX_VALUE) {
							addRun(mark, level, wx, wz, runTop, runTop - y, minDepth.get());
							runTop = Integer.MAX_VALUE;
						}
					}
				}
				// Run läuft bis zum Weltboden offen (selten): abschließen
				if (runTop != Integer.MAX_VALUE) {
					addRun(mark, level, wx, wz, runTop, runTop - worldMinY + 1, minDepth.get());
				}
			}
		}

		long key = pos.toLong();
		// Markieren: der Chunk hat Löcher, aber nur WENIGE (kein natürliches Höhlensystem).
		// Löcher gleich welcher Größe (1x1, 3x1, offene Löcher) zählen. Optional: nur enge Schächte.
		boolean few = mark.holes >= 1 && mark.holes <= maxHoles.get();
		boolean ok = onlyNarrow.on() ? few && mark.narrowShafts >= 1 : few;
		if (ok) {
			marks.put(key, mark);
		} else {
			marks.remove(key);
		}
	}

	/** Vertikalen Luft-Run klassifizieren: enger Schacht (1x1/3x1) oder offenes Loch. */
	private void addRun(ChunkMark mark, ClientLevel level, int wx, int wz, int topY, int depth, int minDepth) {
		if (depth < minDepth) return;
		mark.holes++;

		// Luft-Nachbarn an Mittel- und Unterzelle zählen (8 Samples):
		// 1x1 = 0, 3x1/2x2 = 4, Höhle/Ravin = >= 5
		int airNeighbors = 0;
		int yBottom = topY - depth + 1;
		int yMid = topY - depth / 2;
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (int yy : new int[]{yMid, yBottom}) {
			for (int[] d : dirs) {
				if (level.getBlockState(new BlockPos(wx + d[0], yy, wz + d[1])).isAir()) airNeighbors++;
			}
		}

		if (airNeighbors <= 4) {
			mark.narrowShafts++;
			if (mark.shafts.size() < 48) mark.shafts.add(new BlockPos(wx, topY, wz));
		} else {
			mark.openHoles++;
		}

		mark.minY = Math.min(mark.minY, yBottom);
		mark.maxY = Math.max(mark.maxY, topY);
	}

	// ------------------------------------------------------------------ Abfragen für Render

	/** Alle aktuell markierten Chunks. */
	public List<ChunkMark> marked() {
		return new ArrayList<>(marks.values());
	}

	public int markedCount() {
		return marks.size();
	}

	public int pendingScans() {
		return queue.size();
	}

	/** Distanz-Quadrat vom Spieler zur Chunk-Mitte. */
	public static double distSq(LocalPlayer p, ChunkPos pos) {
		double dx = p.getX() - (pos.getMiddleBlockX() + 0.5);
		double dz = p.getZ() - (pos.getMiddleBlockZ() + 0.5);
		return dx * dx + dz * dz;
	}
}
