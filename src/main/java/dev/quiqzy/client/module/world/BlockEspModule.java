package dev.quiqzy.client.module.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Block ESP: markiert ausgewählte Blöcke (Erze, Kisten, Spawner ...) in geladenen Chunks
 * mit farbigen Boxen, optional durch Wände sichtbar.
 * Scannt wie Sus Chunk ein paar Chunks pro Tick und alle 5 Sekunden neu.
 */
public final class BlockEspModule extends Module {
	private static final int CHUNKS_PER_TICK = 18; // 3x so viel pro Tick
	private static final int RESCAN_INTERVAL_TICKS = 10; // alle ~0,5s neu scannen (Blockänderungen kommen sofort über onBlockChanged)
	private static final int MAX_PER_CHUNK = 256;
	private static final String PRESET_NONE = "Pick…";
	private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
			"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};

	/** Die Blöcke, die markiert werden. Im GUI frei wählbar (Klick -> Such-Menü mit allen Blöcken). */
	public final Setting.Blocks blocks = add(new Setting.Blocks("blocks", "Blocks",
			"minecraft:diamond_ore", "minecraft:deepslate_diamond_ore", "minecraft:ancient_debris",
			"minecraft:chest", "minecraft:spawner"));
	/** Fertige Block-Listen. Auswählen ersetzt die Liste oben, danach springt es zurück auf "Pick…". */
	public final Setting.Choice preset = add(new Setting.Choice("preset", "Preset", PRESET_NONE,
			PRESET_NONE, "Ores", "Rare Ores", "Storage", "Base Finder", "Spawners", "Clear"));
	/** Eigene Farbe pro Block (im Block-Menü per Rechtsklick einstellbar). */
	public final Setting.ColorMap blockColors = add(new Setting.ColorMap("block_colors", "Block Colors"));
	/** Zuletzt gewähltes Preset (nur fürs GUI, leuchtet orange). */
	public String lastPreset = "";
	public final Setting.Int radius = add(new Setting.Int("radius", "Radius", 6, 1, 16, " Ch"));
	public final Setting.Bool throughWalls = add(new Setting.Bool("through_walls", "Through Blocks", true));
	public final Setting.Bool tracers = add(new Setting.Bool("tracers", "Tracers", false));
	public final Setting.Bool outline = add(new Setting.Bool("outline", "Outline", true));

	// Aussehen
	public final Setting.Choice colorMode = add(new Setting.Choice("color_mode", "Color Mode", "Per Block", "Per Block", "Single Color", "Rainbow"));
	public final Setting.Color singleColor = add(new Setting.Color("single_color", "Single Color", 0x4DE8FF));
	public final Setting.Int fillOpacity = add(new Setting.Int("fill_opacity", "Fill Opacity", 22, 0, 100, " %"));
	public final Setting.Int lineOpacity = add(new Setting.Int("line_opacity", "Outline Opacity", 90, 0, 100, " %"));
	public final Setting.Int lineWidth = add(new Setting.Int("line_width", "Outline Width", 3, 1, 10));

	/** Farbe, mit der ein Treffer gezeichnet wird (Color Mode beachtet). */
	public int drawColor(Hit hit, int index) {
		return switch (colorMode.get()) {
			case "Single Color" -> singleColor.get();
			case "Rainbow" -> dev.quiqzy.client.module.visuals.EspColors.rainbow(3, (index % 24) / 24f);
			default -> hit.rgb();
		};
	}

	/** Ein gefundener Block mit seiner Farbe (ARGB ohne Alpha, 0xRRGGBB). */
	public record Hit(BlockPos pos, int rgb) {
	}

	private final Map<Long, List<Hit>> results = new HashMap<>();
	private final ArrayDeque<Long> queue = new ArrayDeque<>();
	private final Set<Long> queued = new HashSet<>();

	private ClientLevel lastLevel;
	private int rescanTimer;
	private int lastMask = -1;

	/** Block -> Farbe, wird neu gebaut wenn sich die Auswahl ändert. */
	private Map<Block, Integer> colors = Map.of();
	private int colorsVersion = -1;
	private int lastRadius = -1;

	public BlockEspModule() {
		super("block_esp", "Block ESP", "Highlights any blocks you pick", Category.WORLD);
	}

	// ------------------------------------------------------------------ Lifecycle

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
		lastLevel = null;
		rescanTimer = 0;
		lastMask = -1;
		lastRadius = -1;
	}

	public void onChunkLoad(ClientLevel level, LevelChunk chunk) {
		if (!isEnabled()) return;
		enqueue(chunk.getPos().toLong());
	}

	public void onChunkUnload(ClientLevel level, LevelChunk chunk) {
		long key = chunk.getPos().toLong();
		results.remove(key);
		queued.remove(key);
	}

	// ------------------------------------------------------------------ Tick / Scan

	/** Wenn im GUI ein Preset gewählt wurde: Liste ersetzen und zurück auf "Pick…". */
	public void applyPreset() {
		String p = preset.get();
		if (p.equals(PRESET_NONE)) return;
		lastPreset = p;
		List<String> ids = new ArrayList<>();
		switch (p) {
			case "Ores" -> {
				for (String ore : new String[]{"diamond", "emerald", "gold", "iron", "redstone", "lapis", "copper", "coal"}) {
					ids.add("minecraft:" + ore + "_ore");
					ids.add("minecraft:deepslate_" + ore + "_ore");
				}
				ids.add("minecraft:nether_gold_ore");
				ids.add("minecraft:nether_quartz_ore");
				ids.add("minecraft:ancient_debris");
			}
			case "Rare Ores" -> ids.addAll(List.of("minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
					"minecraft:emerald_ore", "minecraft:deepslate_emerald_ore", "minecraft:ancient_debris"));
			case "Storage" -> addStorage(ids);
			case "Base Finder" -> {
				addStorage(ids);
				ids.addAll(List.of("minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker", "minecraft:enchanting_table",
						"minecraft:anvil", "minecraft:beacon", "minecraft:respawn_anchor", "minecraft:hopper", "minecraft:crafter"));
				for (String c : COLORS) ids.add("minecraft:" + c + "_bed");
			}
			case "Spawners" -> ids.addAll(List.of("minecraft:spawner", "minecraft:trial_spawner", "minecraft:vault"));
			default -> {
			}
		}
		blocks.set(ids); // "Clear" -> leere Liste
		preset.set(PRESET_NONE);
	}

	private static void addStorage(List<String> ids) {
		ids.addAll(List.of("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", "minecraft:ender_chest", "minecraft:shulker_box"));
		for (String c : COLORS) ids.add("minecraft:" + c + "_shulker_box");
	}

	@Override
	public void onTick(Minecraft mc) {
		applyPreset();
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		if (level == null || player == null) return;

		int mask = mask();
		int r = radius.get();

		// Welt gewechselt oder Auswahl/Radius geändert -> alles neu
		if (level != lastLevel || mask != lastMask || r != lastRadius) {
			results.clear();
			queue.clear();
			queued.clear();
			lastLevel = level;
			lastMask = mask;
			lastRadius = r;
			rescanTimer = 0;
			enqueueAllInRange(level, player);
		}

		if (++rescanTimer >= RESCAN_INTERVAL_TICKS) {
			rescanTimer = 0;
			enqueueAllInRange(level, player);
		}

		ChunkPos center = player.chunkPosition();
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

		results.keySet().removeIf(key -> Math.abs(ChunkPos.getX(key) - center.x) > r || Math.abs(ChunkPos.getZ(key) - center.z) > r);
	}

	private void enqueueAllInRange(ClientLevel level, LocalPlayer player) {
		ChunkPos center = player.chunkPosition();
		int r = radius.get();
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
		if (lastMask == 0) {
			results.remove(key);
			return;
		}

		LevelChunkSection[] sections = chunk.getSections();
		List<Hit> hits = new ArrayList<>();
		int baseX = pos.getMinBlockX();
		int baseZ = pos.getMinBlockZ();

		outer:
		for (int i = 0; i < sections.length; i++) {
			LevelChunkSection section = sections[i];
			if (section == null || section.hasOnlyAir()) continue;
			// Schneller Check über die Palette
			if (!section.getStates().maybeHas(s -> colorFor(s) != 0)) continue;

			int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						int c = colorFor(section.getBlockState(x, y, z));
						if (c == 0) continue;
						hits.add(new Hit(new BlockPos(baseX + x, baseY + y, baseZ + z), c));
						if (hits.size() >= MAX_PER_CHUNK) break outer;
					}
				}
			}
		}

		if (hits.isEmpty()) results.remove(key);
		else results.put(key, Collections.unmodifiableList(hits));
	}

	/** Farbe für einen Block, 0 = nicht markieren. */
	private int colorFor(BlockState s) {
		return colors.getOrDefault(s.getBlock(), 0);
	}

	/** Baut die Farb-Tabelle neu, wenn sich die Auswahl geändert hat. Gibt die Auswahl-Version zurück. */
	private int mask() {
		int v = blocks.version() * 31 + blockColors.version();
		if (v != colorsVersion) {
			Map<Block, Integer> m = new HashMap<>();
			for (String id : blocks.get()) {
				Identifier key = Identifier.tryParse(id);
				if (key == null) continue;
				BuiltInRegistries.BLOCK.getOptional(key).ifPresent(b -> {
					if (b != Blocks.AIR) m.put(b, colorFor(id));
				});
			}
			colors = m;
			colorsVersion = v;
		}
		return colors.isEmpty() ? 0 : v + 1;
	}

	/** Farbe eines Blocks: eigene Farbe (Rechtsklick im Block-Menü), sonst Standardfarbe. */
	public int colorFor(String id) {
		Integer own = blockColors.color(id);
		return own != null ? own : colorOf(id);
	}

	/**
	 * Sofort-Update bei einer Blockänderung (Mixin auf ClientLevel.sendBlockUpdated):
	 * ändert nur diesen einen Treffer, statt auf den nächsten Scan zu warten.
	 */
	public void onBlockChanged(BlockPos pos, BlockState newState) {
		if (!isEnabled() || lastMask <= 0) return;
		long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
		List<Hit> old = results.get(key);
		int c = colorFor(newState);
		boolean had = false;
		if (old != null) {
			for (Hit h : old) {
				if (h.pos().equals(pos)) {
					had = true;
					break;
				}
			}
		}
		if (!had && c == 0) return;
		List<Hit> list = new ArrayList<>(old == null ? List.of() : old);
		list.removeIf(h -> h.pos().equals(pos));
		if (c != 0 && list.size() < MAX_PER_CHUNK) list.add(new Hit(pos.immutable(), c));
		if (list.isEmpty()) results.remove(key);
		else results.put(key, Collections.unmodifiableList(list));
	}

	/** Feste, passende Farben für Blöcke (z. B. Ender Chest violett, Chest orange), sonst individueller Farb-Hash. */
	public static int colorOf(String id) {
		String n = id.substring(id.indexOf(':') + 1).toLowerCase(java.util.Locale.ROOT);

		// Kisten & Lagerung
		if (n.equals("ender_chest")) return 0x9B51E0; // Magisches Violett
		if (n.equals("trapped_chest")) return 0xFF5722; // Gefahren-Rotorange
		if (n.contains("chest")) return 0xFFA733; // Warmes Kisten-Orange
		if (n.equals("barrel")) return 0x9E6838; // Fass-Holzbraun

		// Erze & Mineralien
		if (n.contains("diamond")) return 0x4DE8FF;
		if (n.contains("ancient_debris") || n.contains("netherite")) return 0xD9733F;
		if (n.contains("emerald")) return 0x3DF07A;
		if (n.contains("gold")) return 0xFFD23D;
		if (n.contains("iron")) return 0xE0B49A;
		if (n.contains("copper")) return 0xE0734A;
		if (n.contains("redstone")) return 0xFF2222;
		if (n.contains("lapis")) return 0x2563EB;
		if (n.contains("coal")) return 0x374151;
		if (n.contains("quartz")) return 0xF1EBE4;
		if (n.contains("amethyst")) return 0xC084FC;

		// Spawner & Trial Chambers
		if (n.contains("trial_spawner")) return 0xF59E0B;
		if (n.contains("spawner")) return 0xFF3B30;
		if (n.contains("vault")) return 0x06B6D4;

		// Gefärbte Blöcke (Shulker, Betten, Wolle, Beton, Glas)
		if (n.contains("white_")) return 0xF8FAFC;
		if (n.contains("orange_")) return 0xF97316;
		if (n.contains("magenta_")) return 0xD946EF;
		if (n.contains("light_blue_")) return 0x38BDF8;
		if (n.contains("yellow_")) return 0xFACC15;
		if (n.contains("lime_")) return 0x84CC16;
		if (n.contains("pink_")) return 0xF472B6;
		if (n.contains("gray_")) return 0x4B5563;
		if (n.contains("light_gray_")) return 0x9CA3AF;
		if (n.contains("cyan_")) return 0x06B6D4;
		if (n.contains("purple_")) return 0x8B5CF6;
		if (n.contains("blue_")) return 0x3B82F6;
		if (n.contains("brown_")) return 0x78350F;
		if (n.contains("green_")) return 0x16A34A;
		if (n.contains("red_")) return 0xDC2626;
		if (n.contains("black_")) return 0x18181B;

		if (n.contains("shulker_box")) return 0xC27BFF;

		// Maschinen & Blöcke
		if (n.equals("beacon")) return 0x38BDF8;
		if (n.equals("respawn_anchor")) return 0x7C3AED;
		if (n.equals("enchanting_table")) return 0x8B5CF6;
		if (n.equals("brewing_stand")) return 0xF59E0B;
		if (n.contains("anvil")) return 0x475569;
		if (n.equals("crafter")) return 0xC25E38;
		if (n.equals("furnace")) return 0x78716C;
		if (n.equals("blast_furnace")) return 0x334155;
		if (n.equals("smoker")) return 0x5C4033;
		if (n.equals("hopper")) return 0x64748B;
		if (n.equals("dispenser") || n.equals("dropper")) return 0x6B7280;
		if (n.equals("tnt")) return 0xEF4444;
		if (n.contains("portal")) return 0xA21CAF;
		if (n.contains("obsidian")) return 0x4C1D95;
		if (n.contains("sponge")) return 0xEAB308;
		if (n.contains("prismarine")) return 0x2DD4BF;
		if (n.contains("sea_lantern")) return 0xCCFBF1;
		if (n.contains("sculk")) return 0x0E7490;

		// Schöner deterministischer HSV-Farb-Hash für alle anderen Blöcke
		float hue = (Math.abs(n.hashCode()) % 360) / 360.0f;
		return net.minecraft.util.Mth.hsvToRgb(hue, 0.70f, 0.95f) & 0xFFFFFF;
	}

	// ------------------------------------------------------------------ Für den Renderer

	public Collection<List<Hit>> hits() {
		return results.values();
	}
}
