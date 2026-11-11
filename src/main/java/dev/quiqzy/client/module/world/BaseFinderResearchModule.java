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
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Base Finder Research: untersucht client-sichtbare Chunk-Daten auf unterirdische
 * Spieler-Basen - als FORSCHUNG mit Confidence-Scores, keine erfundenen Funde.
 *
 * 1) Position & Chunk-Tracking:
 *    - Server-bestätigte Position (ClientboundPlayerPositionPacket = "Correction"/Rubberband)
 *    - Höhen-abhängige Chunk-Sichtbarkeit: bei jedem Chunk-Load wird Spieler-Y und die Menge
 *      an Untergrund-Blockdaten mitprotokolliert; daraus wird der Y-Bereich geschätzt, ab dem
 *      der Server Untergrund-Informationen mitschickt.
 *    - Block-State-Updates im Untergrund (Server schickt Änderungen an Teile des Chunks).
 *
 * 2) Chunk-Update-Analyse:
 *    - Load/Unload-Tracking, Rescan-Zyklus, Vergleiche (Untergrund-Sections nehmen zu =
 *      Daten sind nachgereicht worden).
 *
 * 3) Untergrund-Struktur-Erkennung (nur Sections unter der Oberfläche):
 *    - Rechteckige Räume: größter Luft-Rechteck pro Ebene (Histogramm-Algorithmus),
 *      Decke/Boden Probe-Solidität -> Conf
 *    - Tunnel: horizontale Luft-Korridore >= Mindestlänge, oben+unten fest
 *    - Indikator-Blöcke: Kisten/Öfen/Werkbank/Bett/Laternen/Leitern/Glas/Schilder...
 *    - Minen-Unterscheidung: viele Zäune+Schienen = Mineshaft -> Confidence gedeckelt
 *
 * 4) Dashboard (ZeroHud): Spieler-Y, Server-Position, Chunk/Section-Statistik,
 *    Struktur-Anzahl, Top-Confidence, geschätzter Daten-Y-Schwellenwert.
 */
public final class BaseFinderResearchModule extends Module {
	// Struktur-Typen (für Renderer-Farben)
	public static final int ROOM = 0, TUNNEL = 1, CLUSTER = 2;

	// ------------------------------------------------------------------ Settings
	public final Setting.Int scanRadius = add(new Setting.Int("scan_radius", "Scan Radius", 8, 2, 32, " Ch"));
	public final Setting.Int chunksPerTick = add(new Setting.Int("chunks_per_tick", "Scan Budget", 2, 1, 8, "/t"));
	public final Setting.Bool trackPosition = add(new Setting.Bool("track_position", "Track Position/Chunks", true));
	public final Setting.Bool detectRooms = add(new Setting.Bool("detect_rooms", "Rectangular Rooms", true));
	public final Setting.Bool detectTunnels = add(new Setting.Bool("detect_tunnels", "Tunnels", true));
	public final Setting.Bool detectBlocks = add(new Setting.Bool("detect_blocks", "Indicator Blocks", true));
	public final Setting.Int roomMinSize = add(new Setting.Int("room_min_size", "Min. Room Size", 5, 3, 14, " bl"));
	public final Setting.Int tunnelMinLength = add(new Setting.Int("tunnel_min_length", "Min. Tunnel Length", 6, 4, 24, " bl"));
	public final Setting.Int minConfidence = add(new Setting.Int("min_confidence", "Min. Confidence", 40, 5, 100, "%"));
	public final Setting.Bool overlay = add(new Setting.Bool("overlay", "Chunk Overlay", true));
	public final Setting.Bool throughWalls = add(new Setting.Bool("through_walls", "Through Blocks", true));
	public final Setting.Choice overlayMode = add(new Setting.Choice("overlay_mode", "Overlay Style", "Fill + Edge",
			"Fill + Edge", "Edges", "Fill"));
	public final Setting.Int overlayOpacity = add(new Setting.Int("overlay_opacity", "Overlay Opacity", 70, 10, 100, "%"));
	public final Setting.Color markColor = add(new Setting.Color("mark_color", "Mark Color", 0x8CF56B));
	public final Setting.Bool structureBoxes = add(new Setting.Bool("structure_boxes", "Structure Boxes", true));
	public final Setting.Bool dashboard = add(new Setting.Bool("dashboard", "Research Dashboard", true));

	// ------------------------------------------------------------------ Daten
	/** Erkannte Struktur mit Confidence (Forschungsergebnis, keine Behauptung). */
	public record Structure(int type, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, float conf) {
		public float centerX() { return (minX + maxX + 1) * 0.5f; }
		public float centerY() { return (minY + maxY + 1) * 0.5f; }
		public float centerZ() { return (minZ + maxZ + 1) * 0.5f; }
	}

	/** Analyse eines Chunks. */
	public static final class ChunkInfo {
		public final ChunkPos pos;
		public float loadedAtY = Float.NaN; // Spieler-Y beim Chunk-Load (Höhen-Forschung)
		public int sectionsTotal, sectionsUnder;
		public int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
		public int playerBlocks;
		public final List<BlockPos> strongBlocks = new ArrayList<>(); // Kiste/Öfen/Bett etc.
		public final List<Structure> structures = new ArrayList<>();
		public float confidence;
		public boolean mineshaftLike;
		public int underUpdates; // Block-Updates im Untergrund
		public long firstSeen;

		ChunkInfo(ChunkPos pos) {
			this.pos = pos;
		}
	}

	private final Map<Long, ChunkInfo> data = new ConcurrentHashMap<>();
	private final ArrayDeque<Long> queue = new ArrayDeque<>();
	private final Set<Long> queued = new HashSet<>();

	// Position/Chunk-Tracking (mixin & tick thread, volatile)
	private volatile double srvX, srvY, srvZ;
	private volatile long lastServerPosMs;
	private volatile int corrections;
	private volatile int loadedCount;
	private volatile double clientY = Double.NaN;

	// Höhen-Forschung: Y-Bereich, ab dem Untergrund-Daten ankommen
	private volatile int highEmptyY = Integer.MIN_VALUE; // höchster Load-Y ohne Untergrund-Daten
	private volatile int lowFilledY = Integer.MAX_VALUE;  // niedrigster Load-Y mit Untergrund-Daten
	private volatile int infoGainedEvents;                // Rescans, bei denen Untergrund-Daten dazukamen

	private ClientLevel lastLevel;
	private int rescanTimer;

	public BaseFinderResearchModule() {
		super("base_research", "Base Finder Research", "Researches underground bases: position/chunk tracking, structure detection with confidence scores", Category.WORLD);
	}

	// ------------------------------------------------------------------ Lifecycle

	@Override
	protected void onEnable() {
		reset();
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null && mc.player != null) {
			lastLevel = mc.level;
			clientY = mc.player.getY();
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
		loadedCount = 0;
		highEmptyY = Integer.MIN_VALUE;
		lowFilledY = Integer.MAX_VALUE;
		infoGainedEvents = 0;
		lastLevel = null;
		rescanTimer = 0;
	}

	/** Von ClientChunkEvents.CHUNK_LOAD. */
	public void onChunkLoad(ClientLevel level, LevelChunk chunk) {
		if (!isEnabled()) return;
		loadedCount++;
		LocalPlayer p = Minecraft.getInstance().player;
		float y = p != null ? (float) p.getY() : Float.NaN;

		// Höhen-Forschung: wie viel Untergrund-Info hat der Chunk bei diesem Spieler-Y?
		if (!Float.isNaN(y) && trackPosition.on()) {
			int under = countUndergroundSections(chunk, level);
			if (under == 0) highEmptyY = Math.max(highEmptyY, (int) y);
			else lowFilledY = Math.min(lowFilledY, (int) y);
		}

		long key = chunk.getPos().toLong();
		ChunkInfo info = new ChunkInfo(chunk.getPos());
		info.loadedAtY = y;
		info.firstSeen = System.currentTimeMillis();
		ChunkInfo old = data.put(key, info);
		if (old == null) enqueue(key);
	}

	/** Von ClientChunkEvents.CHUNK_UNLOAD. */
	public void onChunkUnload(ClientLevel level, LevelChunk chunk) {
		if (!isEnabled()) return;
		loadedCount = Math.max(0, loadedCount - 1);
		long key = chunk.getPos().toLong();
		data.remove(key);
		queued.remove(key);
	}

	/** Von ClientPacketListenerMixin: Server-Positionskorrektur (Rubberband/Teleport). */
	public void onServerPosition(Vec3 pos) {
		if (!trackPosition.on()) return;
		srvX = pos.x;
		srvY = pos.y;
		srvZ = pos.z;
		lastServerPosMs = System.currentTimeMillis();
		corrections++;
	}

	/** Von ClientLevelMixin: Block-Updates tracken (Untergrund-Aktivität). */
	public void onBlockUpdate(BlockPos pos, BlockState newState) {
		if (!isEnabled() || pos.getY() >= 0) return; // Untergrund grob: y < 0
		ChunkInfo info = data.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
		if (info != null) info.underUpdates++;
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
			clientY = player.getY();
			enqueueAllInRange(level, player);
		}
		clientY = player.getY();

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

			LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
			if (chunk == null) continue;

			scan(chunk, level, key);
			budget--;
		}

		data.values().removeIf(i -> Math.abs(i.pos.x - center.x) > r || Math.abs(i.pos.z - center.z) > r);
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
					if (level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) != null && !data.containsKey(key)) {
						enqueue(key);
					}
				}
			}
		}
	}

	private void enqueue(long key) {
		if (queued.add(key)) queue.add(key);
	}

	// ------------------------------------------------------------------ Analyse

	/** Nicht-Luft-Sections unter der Oberfläche (Höhen-Forschung). */
	private int countUndergroundSections(LevelChunk chunk, ClientLevel level) {
		int surfaceMin = surfaceMinY(chunk);
		int count = 0;
		LevelChunkSection[] sections = chunk.getSections();
		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection s = sections[i];
			if (s == null || s.hasOnlyAir()) continue;
			int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
			if (baseY + 15 < surfaceMin - 3) count++;
		}
		return count;
	}

	/** Niedrigste Oberfläche im Chunk (konservativ für "Untergrund"). */
	private int surfaceMinY(LevelChunk chunk) {
		int min = Integer.MAX_VALUE;
		for (int x = 0; x < 16; x += 3) {
			for (int z = 0; z < 16; z += 3) {
				min = Math.min(min, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
			}
		}
		return min == Integer.MAX_VALUE ? 0 : min;
	}

	/** Vollständiger Chunk-Scan: Untergrund-Sections, Strukturen, Indikatoren, Confidence. */
	private void scan(LevelChunk chunk, ClientLevel level, long key) {
		ChunkPos pos = chunk.getPos();
		ChunkInfo existing = data.get(key);
		int prevUnder = existing != null ? existing.sectionsUnder : -1;
		float loadedAtY = existing != null ? existing.loadedAtY : Float.NaN;
		long firstSeen = existing != null ? existing.firstSeen : System.currentTimeMillis();
		int underUpdates = existing != null ? existing.underUpdates : 0;

		ChunkInfo info = new ChunkInfo(pos);
		info.loadedAtY = loadedAtY;
		info.firstSeen = firstSeen;
		info.underUpdates = underUpdates;

		int surfaceMin = surfaceMinY(chunk);
		LevelChunkSection[] sections = chunk.getSections();
		int baseX = pos.getMinBlockX();
		int baseZ = pos.getMinBlockZ();

		List<Structure> structures = new ArrayList<>();

		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection s = sections[i];
			if (s == null || s.hasOnlyAir()) continue;
			info.sectionsTotal++;

			int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
			if (baseY + 15 >= surfaceMin - 3) continue; // Oberfläche/nah dran: kein Untergrund
			info.sectionsUnder++;
			info.minY = Math.min(info.minY, baseY);
			info.maxY = Math.max(info.maxY, baseY + 15);

			// Palette-Vorabcheck: nur Sections scannen, die Luft ODER Indikatoren enthalten
			boolean hasAir = s.getStates().maybeHas(BlockState::isAir);
			boolean hasInd = detectBlocks.on() && s.getStates().maybeHas(BaseFinderResearchModule::isIndicator);
			if (!hasAir && !hasInd) continue;

			int fenceCount = 0, railCount = 0, planksCount = 0, torchCount = 0;
			int[] blockWeights = new int[8]; // gesammelte Indikator-Gewichte
			// Luft-Raster pro Ebene: bit x + z*16 (16x16 Grid)
			long[][] grid = hasAir ? new long[16][16] : null; // [layer][zRow]

			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						BlockState st = s.getBlockState(x, y, z);
						if (st.isAir()) {
							if (hasAir) grid[y][z] |= (1L << x);
							continue;
						}
						if (!detectBlocks.on()) continue;
						int w = indicatorWeight(st);
						if (w <= 0) continue;
						info.playerBlocks++;
						if (st.is(Blocks.OAK_FENCE) || st.is(Blocks.SPRUCE_FENCE) || st.is(Blocks.DARK_OAK_FENCE)) fenceCount++;
						else if (st.is(BlockTags.RAILS)) railCount++;
						else if (isPlanks(st)) planksCount++;
						else if (st.is(Blocks.TORCH) || st.is(Blocks.WALL_TORCH) || st.is(Blocks.LANTERN)) torchCount++;
						if (w >= 10 && info.strongBlocks.size() < 16) {
							info.strongBlocks.add(new BlockPos(baseX + x, baseY + y, baseZ + z));
						}
						blockWeights[idxWeight(st)] += w;
					}
				}
			}

			if (fenceCount >= 8 && railCount >= 6) info.mineshaftLike = true;
			int strongSum = 0;
			for (int w : blockWeights) strongSum += w;

			// --- Räume: größtes Luft-Rechteck pro Ebene
			if (hasAir && detectRooms.on()) {
				for (int y = 1; y < 15; y++) {
					int[] rect = largestRect(grid[y]);
					if (rect == null) continue;
					int w = rect[2], h = rect[3];
					if (w < roomMinSize.get() || h < roomMinSize.get()) continue;

					// Decke/Boden: Probe-Punkte (3x3) ober-/unterhalb der Ebene
					float above = solidRatio(s, rect, y + 1);
					float below = solidRatio(s, rect, y - 1);
					float conf = 0.30f + 0.45f * above + 0.25f * below;
					if (conf < 0.45f) continue; // irregulär = vermutlich natürlich
					structures.add(new Structure(ROOM,
							baseX + rect[0], baseY + y, baseZ + rect[1],
							baseX + rect[0] + w - 1, baseY + y, baseZ + rect[1] + h - 1, conf));
					if (structures.size() >= 8) break;
				}
			}

			// --- Tunnel: horizontale Luft-Korridore (x-Richtung und z-Richtung)
			if (hasAir && detectTunnels.on() && structures.size() < 8) {
				scanTunnels(grid, s, baseX, baseY, baseZ, structures);
			}

			if (hasInd && strongSum > 0 && structures.size() < 8) {
				// Indikator-Cluster als Struktur um die stärksten Blöcke
				for (BlockPos p : info.strongBlocks) {
					structures.add(new Structure(CLUSTER, p.getX(), p.getY(), p.getZ(),
							p.getX(), p.getY(), p.getZ(), Math.min(1f, strongSum / 60f)));
					if (structures.size() >= 8) break;
				}
			}
		}

		// --- Confidence aus allen Indikatoren (Forschung, keine Behauptung)
		float conf = 0f;
		conf += Math.min(40f, structures.size() * 6f); // Struktur-Anzahl (Raum/Tunnel)
		float roomConf = 0f;
		int roomCount = 0;
		for (Structure st : structures) {
			if (st.type() == ROOM) {
				roomConf += st.conf();
				roomCount++;
			}
		}
		if (roomCount > 0) conf += (roomConf / roomCount) * 25f;
		conf += Math.min(20f, info.playerBlocks * 1.5f);
		conf += Math.min(10f, info.underUpdates * 2f);
		if (info.mineshaftLike) conf = Math.min(conf, 30f); // Mineshaft-Optik -> stark abwerten
		info.confidence = Math.max(0f, Math.min(100f, conf));
		info.structures.addAll(structures);

		// Rescan-Vergleich: Untergrund-Daten sind nachgereicht worden (Höhen-Forschung)
		if (prevUnder >= 0 && info.sectionsUnder > prevUnder) {
			infoGainedEvents++;
		}

		data.put(key, info);
	}

	/** Horizontale Korridore im 16x16x16-Luftraster finden (x-Richtung + z-Richtung). */
	private void scanTunnels(long[][] grid, LevelChunkSection s, int baseX, int baseY, int baseZ, List<Structure> out) {
		int minLen = tunnelMinLength.get();
		for (int y = 1; y < 15; y++) {
			// x-Richtung: für jede z-Zeile Luft-Runs
			for (int z = 0; z < 16 && out.size() < 8; z++) {
				int run = 0;
				for (int x = 0; x <= 16; x++) {
					boolean air = x < 16 && (grid[y][z] & (1L << x)) != 0;
					if (air) {
						run++;
					} else {
						if (run >= minLen) {
							int x0 = x - run, x1 = x - 1;
							int mx = (x0 + x1) / 2;
							boolean ceilSolid = !s.getBlockState(mx, y + 1, z).isAir();
							boolean floorSolid = !s.getBlockState(mx, y - 1, z).isAir();
							if (ceilSolid && floorSolid) {
								float conf = 0.4f + 0.3f * Math.min(1f, (run - minLen) / 8f);
								out.add(new Structure(TUNNEL, baseX + x0, baseY + y, baseZ + z,
										baseX + x1, baseY + y, baseZ + z, conf));
							}
						}
						run = 0;
					}
				}
			}
			// z-Richtung: für jede x-Spalte Luft-Runs
			for (int x = 0; x < 16 && out.size() < 8; x++) {
				int run = 0;
				for (int z = 0; z <= 16; z++) {
					boolean air = z < 16 && (grid[y][z] & (1L << x)) != 0;
					if (air) {
						run++;
					} else {
						if (run >= minLen) {
							int z0 = z - run, z1 = z - 1;
							int mz = (z0 + z1) / 2;
							boolean ceilSolid = !s.getBlockState(x, y + 1, mz).isAir();
							boolean floorSolid = !s.getBlockState(x, y - 1, mz).isAir();
							if (ceilSolid && floorSolid) {
								float conf = 0.4f + 0.3f * Math.min(1f, (run - minLen) / 8f);
								out.add(new Structure(TUNNEL, baseX + x, baseY + y, baseZ + z0,
										baseX + x, baseY + y, baseZ + z1, conf));
							}
						}
						run = 0;
					}
				}
			}
		}
	}

	/** Größtes Rechteck voller Luft in einem 16x16-Bit-Raster (Histogramm-Methode). */
	private int[] largestRect(long[] rows) {
		int[] heights = new int[16];
		int best = 0;
		int[] bestRect = null;
		for (int z = 0; z < 16; z++) {
			for (int x = 0; x < 16; x++) {
				heights[x] = (rows[z] & (1L << x)) != 0 ? heights[x] + 1 : 0;
			}
			// Histogramm: größtes Rechteck unter den Höhen
			java.util.Deque<int[]> stack = new java.util.ArrayDeque<>();
			for (int x = 0; x <= 16; x++) {
				int h = x < 16 ? heights[x] : 0;
				int start = x;
				while (!stack.isEmpty() && stack.peek()[1] >= h) {
					int[] top = stack.pop();
					int area = top[1] * (x - top[0]);
					if (area > best && top[1] >= 2 && (x - top[0]) >= 2) {
						best = area;
						bestRect = new int[]{top[0], z - top[1] + 1, x - top[0], top[1]};
					}
					start = top[0];
				}
				stack.push(new int[]{start, h});
			}
		}
		return bestRect;
	}

	/** Solidität-Probe über ein Rechteck in einer Ebene (3x3 Punkte, normierte [0,1]-Angabe). */
	private float solidRatio(LevelChunkSection s, int[] rect, int y) {
		if (y < 0 || y > 15) return 0.5f; // außerhalb der Section: unbekannt
		int x0 = rect[0], z0 = rect[1], w = rect[2], h = rect[3];
		int solid = 0, total = 0;
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				int x = Math.min(15, x0 + (w - 1) * i / 2);
				int z = Math.min(15, z0 + (h - 1) * j / 2);
				total++;
				if (!s.getBlockState(x, y, z).isAir()) solid++;
			}
		}
		return total == 0 ? 0f : solid / (float) total;
	}

	// ------------------------------------------------------------------ Indikator-Blöcke

	private static boolean isIndicator(BlockState st) {
		return indicatorWeight(st) > 0;
	}

	private static int idxWeight(BlockState st) {
		return Math.min(7, indicatorWeight(st) / 10);
	}

	/** Gewicht eines Indikator-Blocks (0 = kein Indikator). Bewusst konservativ. */
	private static int indicatorWeight(BlockState st) {
		if (st.is(Blocks.CHEST) || st.is(Blocks.TRAPPED_CHEST) || st.is(Blocks.BARREL)) return 18;
		if (st.is(Blocks.FURNACE) || st.is(Blocks.BLAST_FURNACE) || st.is(Blocks.SMOKER)) return 15;
		if (st.is(Blocks.CRAFTING_TABLE) || st.is(Blocks.CARTOGRAPHY_TABLE) || st.is(Blocks.SMITHING_TABLE)) return 15;
		if (st.is(Blocks.BREWING_STAND) || st.is(Blocks.ENCHANTING_TABLE) || st.is(Blocks.ANVIL)) return 18;
		if (st.is(BlockTags.BEDS)) return 20;
		if (st.is(Blocks.RESPAWN_ANCHOR) || st.is(Blocks.LODESTONE) || st.is(Blocks.BEACON)) return 18;
		if (st.is(Blocks.HOPPER) || st.is(Blocks.DROPPER) || st.is(Blocks.DISPENSER) || st.is(Blocks.OBSERVER)) return 12;
		if (st.is(Blocks.LADDER)) return 10;
		if (st.is(BlockTags.DOORS) || st.is(BlockTags.TRAPDOORS)) return 8;
		if (st.is(Blocks.GLASS) || st.is(Blocks.GLASS_PANE) || st.is(Blocks.TINTED_GLASS)) return 6;
		if (st.is(BlockTags.SIGNS)) return 8;
		if (st.is(Blocks.TORCH) || st.is(Blocks.WALL_TORCH) || st.is(Blocks.LANTERN) || st.is(Blocks.SOUL_LANTERN)) return 4;
		if (isPlanks(st)) return 3;
		if (st.is(Blocks.CAULDRON) || st.is(Blocks.WATER_CAULDRON)) return 12;
		return 0;
	}

	private static boolean isPlanks(BlockState st) {
		return st.is(BlockTags.PLANKS);
	}

	// ------------------------------------------------------------------ Abfragen für Render/HUD

	/** Chunks über dem Confidence-Schwellwert. */
	public List<ChunkInfo> findings() {
		List<ChunkInfo> list = new ArrayList<>();
		for (ChunkInfo i : data.values()) {
			if (i.confidence >= minConfidence.get()) list.add(i);
		}
		return list;
	}

	public int analyzedCount() {
		return data.size();
	}

	public int pendingScans() {
		return queue.size();
	}

	public int loadedChunks() {
		return loadedCount;
	}

	public int corrections() {
		return corrections;
	}

	public int infoGainedEvents() {
		return infoGainedEvents;
	}

	public long lastServerPosAge() {
		return lastServerPosMs == 0 ? -1 : System.currentTimeMillis() - lastServerPosMs;
	}

	/** {srvX, srvY, srvZ} der letzten Server-Position. */
	public double[] serverPos() {
		return new double[]{srvX, srvY, srvZ};
	}

	/** {sectionsTotal, sectionsUnder, playerBlocks} über alle analysierten Chunks. */
	public int[] infoTotals() {
		int t = 0, u = 0, b = 0;
		for (ChunkInfo i : data.values()) {
			t += i.sectionsTotal;
			u += i.sectionsUnder;
			b += i.playerBlocks;
		}
		return new int[]{t, u, b};
	}

	/** Geschätzter Y-Schwellenwert, ab dem der Server Untergrund-Daten schickt (oder NaN). */
	public double dataThresholdY() {
		if (highEmptyY == Integer.MIN_VALUE || lowFilledY == Integer.MAX_VALUE) return Double.NaN;
		if (lowFilledY < highEmptyY) return Double.NaN; // inkonsistente Stichprobe
		return (highEmptyY + lowFilledY) / 2.0;
	}

	/** {structureCount, topConfidence} über alle Chunks. */
	public float[] structureStats() {
		int count = 0;
		float top = 0f;
		for (ChunkInfo i : data.values()) {
			count += i.structures.size();
			top = Math.max(top, i.confidence);
		}
		return new float[]{count, top};
	}

	public double distSq(LocalPlayer p, ChunkPos pos) {
		double dx = p.getX() - (pos.getMiddleBlockX() + 0.5);
		double dz = p.getZ() - (pos.getMiddleBlockZ() + 0.5);
		return dx * dx + dz * dz;
	}
}
