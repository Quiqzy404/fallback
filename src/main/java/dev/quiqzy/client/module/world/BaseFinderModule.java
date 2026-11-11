package dev.quiqzy.client.module.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.UiSounds;

/**
 * Base Finder: bewertet jeden geladenen Chunk nach Spuren von Spielern (Lager, Werkbänke, Redstone, gebaute Blöcke)
 * und markiert Chunks mit hoher Punktzahl als mögliche Base. Dörfer (Glocke) werden abgewertet.
 * Arbeitet nur mit Chunks, die dein Client ohnehin geladen hat.
 */
public final class BaseFinderModule extends Module {
	private static final int CHUNKS_PER_TICK = 6;
	private static final int RESCAN_TICKS = 100;

	public final Setting.Int minScore = add(new Setting.Int("min_score", "Min. Score", 40, 10, 300));
	public final Setting.Int radius = add(new Setting.Int("radius", "Radius", 12, 2, 32, " Ch"));
	public final Setting.Bool storage = add(new Setting.Bool("storage", "Storage", true));
	public final Setting.Bool utility = add(new Setting.Bool("utility", "Utility Blocks", true));
	public final Setting.Bool redstone = add(new Setting.Bool("redstone", "Redstone", true));
	public final Setting.Bool built = add(new Setting.Bool("built", "Built Blocks", true));
	public final Setting.Bool ignoreVillages = add(new Setting.Bool("ignore_villages", "Ignore Villages", true));
	public final Setting.Bool hud = add(new Setting.Bool("hud", "HUD List", true));
	public final Setting.Bool markers = add(new Setting.Bool("markers", "World Markers", true));
	public final Setting.Bool sound = add(new Setting.Bool("sound", "Sound on Find", true));
	public final Setting.Bool autoWaypoint = add(new Setting.Bool("auto_waypoint", "Auto Waypoint", false));
	// Anti-Xray: Block-Entities werden vom Server trotzdem geschickt -> Container unter Deepslate finden
	public final Setting.Bool blockEntities = add(new Setting.Bool("block_entities", "Scan Block Entities (anti-xray)", true));
	public final Setting.Bool onlyDeep = add(new Setting.Bool("only_deep", "Only Below Deepslate", true));
	public final Setting.Int deepY = add(new Setting.Int("deep_y", "Deepslate Y", 0, -64, 100));
	public final Setting.Int deepMult = add(new Setting.Int("deep_mult", "Deep Boost", 5, 1, 10, "x"));
	public final Setting.Bool smart = add(new Setting.Bool("smart", "Smart Filter (no noise)", true));
	public final Setting.Int minContainers = add(new Setting.Int("min_containers", "Min Containers", 4, 1, 20));

	/** Fund in einem Chunk: Punktzahl, Höhe der meisten Funde, Zählung pro Kategorie (für den Text). */
	public record Result(ChunkPos pos, int score, int y, Map<String, Integer> counts) {
		public String reason() {
			StringBuilder b = new StringBuilder();
			int n = 0;
			for (var e : counts.entrySet()) {
				if (n++ >= 2) break;
				if (b.length() > 0) b.append(" · ");
				b.append(e.getValue()).append(' ').append(e.getKey());
			}
			return b.toString();
		}
	}

	private final Map<Long, Result> results = new HashMap<>();
	private final ArrayDeque<Long> queue = new ArrayDeque<>();
	private final Set<Long> queued = new HashSet<>();
	private final Set<Long> announced = new HashSet<>();
	private final Map<Block, Object[]> weights = new HashMap<>(); // Block -> {Punkte, Kategorie-Name}
	// vom Server geschickte Block-Entities je Chunk (posLong -> Kategorie), auch wenn der Client sie verwirft
	private final java.util.concurrent.ConcurrentHashMap<Long, java.util.Map<Long, String>> packetBEs = new java.util.concurrent.ConcurrentHashMap<>();
	private ClientLevel lastLevel;
	private int timer;
	private int sig = -1;
	public long lastFoundAt;

	public BaseFinderModule() {
		super("base_finder", "Base Finder", "Finds player bases in loaded chunks", Category.WORLD);
	}

	// ------------------------------------------------------------------ Block-Entities aus dem Paket (Anti-Xray-Leak)

	/** Wird vom Mixin für jede Block-Entity im Chunk-Paket aufgerufen – auch für versteckte unter Deepslate. */
	public void onServerBlockEntity(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.entity.BlockEntityType<?> type) {
		if (!isEnabled() || !blockEntities.on()) return;
		String name = beName(type);
		if (name == null) return;
		long chunk = net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
		packetBEs.computeIfAbsent(chunk, k -> new java.util.concurrent.ConcurrentHashMap<>()).put(pos.asLong(), name);
	}

	private static String beName(net.minecraft.world.level.block.entity.BlockEntityType<?> t) {
		var T = net.minecraft.world.level.block.entity.BlockEntityType.class;
		if (t == net.minecraft.world.level.block.entity.BlockEntityType.SHULKER_BOX) return "shulkers";
		if (t == net.minecraft.world.level.block.entity.BlockEntityType.BEACON) return "beacons";
		if (t == net.minecraft.world.level.block.entity.BlockEntityType.ENCHANTING_TABLE) return "ench. tables";
		if (t == net.minecraft.world.level.block.entity.BlockEntityType.MOB_SPAWNER || t == net.minecraft.world.level.block.entity.BlockEntityType.TRIAL_SPAWNER) return "spawners";
		if (t == net.minecraft.world.level.block.entity.BlockEntityType.ENDER_CHEST) return "ender chests";
		if (t == net.minecraft.world.level.block.entity.BlockEntityType.BREWING_STAND) return "brewing";
		if (t == net.minecraft.world.level.block.entity.BlockEntityType.HOPPER) return "hoppers";
		if (t == net.minecraft.world.level.block.entity.BlockEntityType.DISPENSER || t == net.minecraft.world.level.block.entity.BlockEntityType.DROPPER) return "redstone";
		if (t == net.minecraft.world.level.block.entity.BlockEntityType.SIGN || t == net.minecraft.world.level.block.entity.BlockEntityType.HANGING_SIGN) return "signs";
		if (t == net.minecraft.world.level.block.entity.BlockEntityType.CHEST || t == net.minecraft.world.level.block.entity.BlockEntityType.TRAPPED_CHEST) return "chests";
		if (t == net.minecraft.world.level.block.entity.BlockEntityType.BARREL) return "barrels";
		if (t == net.minecraft.world.level.block.entity.BlockEntityType.FURNACE || t == net.minecraft.world.level.block.entity.BlockEntityType.BLAST_FURNACE || t == net.minecraft.world.level.block.entity.BlockEntityType.SMOKER) return "workstations";
		return null;
	}

	private static double bePoints(String name) {
		return switch (name) {
			case "beacons" -> 25; case "shulkers" -> 15; case "ench. tables" -> 8;
			case "spawners", "ender chests" -> 6; case "brewing" -> 4;
			case "hoppers", "redstone", "signs" -> 3; default -> 2;
		};
	}

	// ------------------------------------------------------------------ Gewichtung

	private Object[] weightOf(BlockState st) {
		Block b = st.getBlock();
		Object[] w = weights.get(b);
		if (w == null) {
			w = compute(b, st);
			weights.put(b, w);
		}
		return w;
	}

	private Object[] compute(Block b, BlockState st) {
		String id = BuiltInRegistries.BLOCK.getKey(b).getPath();
		if (storage.on()) {
			if (b instanceof ShulkerBoxBlock) return w(15, "shulkers");
			if (b == Blocks.ENDER_CHEST) return w(6, "ender chests");
			if (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST) return w(2, "chests");
			if (b == Blocks.BARREL) return w(2, "barrels");
			if (b == Blocks.HOPPER) return w(3, "hoppers");
		}
		if (utility.on()) {
			if (b == Blocks.BEACON) return w(25, "beacons");
			if (b == Blocks.ENCHANTING_TABLE) return w(8, "ench. tables");
			if (b == Blocks.ANVIL || b == Blocks.CHIPPED_ANVIL || b == Blocks.DAMAGED_ANVIL) return w(5, "anvils");
			if (b == Blocks.RESPAWN_ANCHOR) return w(8, "anchors");
			if (b == Blocks.BREWING_STAND) return w(4, "brewing");
			if (b instanceof BedBlock) return w(2, "beds");
			if (b == Blocks.CRAFTING_TABLE || b == Blocks.FURNACE || b == Blocks.BLAST_FURNACE || b == Blocks.SMOKER) return w(1, "workstations");
		}
		if (redstone.on()) {
			if (b == Blocks.PISTON || b == Blocks.STICKY_PISTON || b == Blocks.OBSERVER || b == Blocks.REPEATER
					|| b == Blocks.COMPARATOR || b == Blocks.DROPPER || b == Blocks.DISPENSER) return w(3, "redstone");
		}
		if (built.on()) {
			if (st.is(BlockTags.ALL_SIGNS)) return w(3, "signs");
			if (st.is(BlockTags.BANNERS)) return w(3, "banners");
			if (b == Blocks.NETHER_PORTAL) return w(1, "portal");
			if (b == Blocks.OBSIDIAN) return w(0.6, "obsidian");
			if (id.endsWith("concrete")) return w(0.5, "concrete");
			if (st.is(BlockTags.WOOL)) return w(0.3, "wool");
			if (id.equals("glass") || id.endsWith("stained_glass") || id.endsWith("glass_pane")) return w(0.3, "glass");
		}
		if (b == Blocks.BELL) return w(0, "bell");
		return null;
	}

	private static Object[] w(double points, String name) {
		return new Object[]{points, name};
	}

	// ------------------------------------------------------------------ Lifecycle / Scan

	@Override
	protected void onEnable() {
		reset();
	}

	@Override
	protected void onDisable() {
		reset();
	}

	private void reset() {
		results.clear();
		queue.clear();
		queued.clear();
		weights.clear();
		packetBEs.clear();
		lastLevel = null;
		timer = 0;
	}

	public void onChunkLoad(ClientLevel level, LevelChunk chunk) {
		if (isEnabled() && queued.add(chunk.getPos().toLong())) queue.add(chunk.getPos().toLong());
	}

	public void onChunkUnload(ClientLevel level, LevelChunk chunk) {
		results.remove(chunk.getPos().toLong());
	}

	@Override
	public void onTick(Minecraft mc) {
		ClientLevel level = mc.level;
		LocalPlayer p = mc.player;
		if (level == null || p == null) return;
		int s = (storage.on() ? 1 : 0) | (utility.on() ? 2 : 0) | (redstone.on() ? 4 : 0) | (built.on() ? 8 : 0);
		if (level != lastLevel || s != sig) {
			reset();
			lastLevel = level;
			sig = s;
			enqueueAll(level, p);
		}
		if (++timer >= RESCAN_TICKS) {
			timer = 0;
			enqueueAll(level, p);
		}
		ChunkPos c = p.chunkPosition();
		int r = radius.get();
		int budget = CHUNKS_PER_TICK;
		while (budget > 0 && !queue.isEmpty()) {
			long key = queue.poll();
			queued.remove(key);
			int cx = ChunkPos.getX(key), cz = ChunkPos.getZ(key);
			if (Math.abs(cx - c.x) > r || Math.abs(cz - c.z) > r) continue;
			LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
			if (chunk == null) continue;
			scan(level, chunk);
			budget--;
		}
		results.values().removeIf(res -> Math.abs(res.pos().x - c.x) > r + 2 || Math.abs(res.pos().z - c.z) > r + 2);
		packetBEs.keySet().removeIf(k -> Math.abs(net.minecraft.world.level.ChunkPos.getX(k) - c.x) > r + 2 || Math.abs(net.minecraft.world.level.ChunkPos.getZ(k) - c.z) > r + 2);
	}

	private void enqueueAll(ClientLevel level, LocalPlayer p) {
		ChunkPos c = p.chunkPosition();
		int r = radius.get();
		for (int ring = 0; ring <= r; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
					long key = ChunkPos.asLong(c.x + dx, c.z + dz);
					if (level.getChunkSource().getChunk(c.x + dx, c.z + dz, ChunkStatus.FULL, false) != null && queued.add(key)) queue.add(key);
				}
			}
		}
	}

	private void scan(ClientLevel level, LevelChunk chunk) {
		ChunkPos pos = chunk.getPos();
		long key = pos.toLong();
		LevelChunkSection[] sections = chunk.getSections();
		double score = 0;
		boolean bell = false;
		boolean overworld = level.dimension() == Level.OVERWORLD;
		Map<String, Integer> counts = new HashMap<>();
		Map<Integer, Double> yScore = new HashMap<>();
		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];
			if (section == null || section.hasOnlyAir()) continue;
			if (!section.getStates().maybeHas(st -> weightOf(st) != null)) continue;
			int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
			if (onlyDeep.on() && baseY >= deepY.get()) continue; // ganze Sektion über Deepslate überspringen
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						if (onlyDeep.on() && baseY + y >= deepY.get()) continue;
						Object[] w = weightOf(section.getBlockState(x, y, z));
						if (w == null) continue;
						String name = (String) w[1];
						if (name.equals("bell")) {
							bell = true;
							continue;
						}
						if (name.equals("portal") && !overworld) continue;
						double pts = (Double) w[0];
						score += pts;
						counts.merge(name, 1, Integer::sum);
						yScore.merge(baseY + y, pts, Double::sum);
					}
				}
			}
		}
		var bes = packetBEs.get(key);
		if (blockEntities.on() && bes != null) {
			for (var e : bes.entrySet()) {
				net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.of(e.getKey());
				if (onlyDeep.on() && bp.getY() >= deepY.get()) continue;
				if (weightOf(level.getBlockState(bp)) != null) continue; // schon über den Block-Zustand gezählt
				double pts = bePoints(e.getValue());
				if (bp.getY() < deepY.get()) pts *= deepMult.get();
				score += pts;
				counts.merge(e.getValue(), 1, Integer::sum);
				yScore.merge(bp.getY(), pts, Double::sum);
			}
		}
		if (bell && ignoreVillages.on()) score *= 0.35;
		if (score < 1) {
			results.remove(key);
			return;
		}
		if (smart.on()) {
			// Spieler-Signal: kommt natürlich praktisch nie (oder nicht gehäuft) vor
			boolean signature = counts.containsKey("shulkers") || counts.containsKey("beacons") || counts.containsKey("anchors")
					|| counts.containsKey("anvils") || counts.containsKey("ench. tables") || counts.containsKey("brewing") || counts.containsKey("redstone");
			int containers = counts.getOrDefault("chests", 0) + counts.getOrDefault("barrels", 0) + counts.getOrDefault("shulkers", 0)
					+ counts.getOrDefault("ender chests", 0) + counts.getOrDefault("hoppers", 0);
			if (!signature && containers < minContainers.get()) {
				results.remove(key);
				return; // Rauschen (einzelne natürliche Truhe o. Ä.)
			}
		}
		int bestY = 64;
		double best = -1;
		for (var e : yScore.entrySet()) if (e.getValue() > best) { best = e.getValue(); bestY = e.getKey(); }
		// Kategorien nach Bedeutung sortieren (Punkte * Anzahl)
		Map<String, Integer> sorted = new LinkedHashMap<>();
		counts.entrySet().stream()
				.sorted((a, b) -> Integer.compare(b.getValue() * rank(b.getKey()), a.getValue() * rank(a.getKey())))
				.forEach(e -> sorted.put(e.getKey(), e.getValue()));
		Result res = new Result(pos, (int) Math.round(score), bestY, sorted);
		results.put(key, res);
		if (res.score() >= minScore.get() && announced.add(key)) {
			lastFoundAt = System.currentTimeMillis();
			if (sound.on()) UiSounds.found();
			if (autoWaypoint.on()) {
				Minecraft mc = Minecraft.getInstance();
				var wp = ModuleManager.WAYPOINTS;
				if (!wp.isEnabled()) wp.setEnabled(true);
				wp.points.add(new Setting.Waypoints.Point("Base " + res.score(), pos.getMiddleBlockX(), bestY, pos.getMiddleBlockZ(),
						WaypointsModule.dimensionId(mc.level)));
				dev.quiqzy.client.config.QuiqzyConfig.save();
			}
		}
	}

	private static int rank(String name) {
		return switch (name) {
			case "beacons" -> 25;
			case "shulkers" -> 15;
			case "ench. tables", "anchors" -> 8;
			case "ender chests" -> 6;
			case "anvils" -> 5;
			case "brewing" -> 4;
			case "hoppers", "redstone", "signs", "banners" -> 3;
			case "chests", "barrels", "beds" -> 2;
			default -> 1;
		};
	}

	/** Alle Funde über der Mindestpunktzahl, nach Punkten sortiert. */
	public List<Result> bases() {
		List<Result> out = new ArrayList<>();
		for (Result r : results.values()) if (r.score() >= minScore.get()) out.add(r);
		out.sort((a, b) -> Integer.compare(b.score(), a.score()));
		return out;
	}
}
