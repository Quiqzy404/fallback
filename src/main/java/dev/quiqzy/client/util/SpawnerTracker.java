package dev.quiqzy.client.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.world.WaypointsModule;

/**
 * Zentraler Tracker für gefundene Spawner:
 * Erkennt Spawner aus Chunk-Paketen (Anti-Xray-Bypass via BlockEntityTagOutput),
 * geladenen Chunks und Block-Updates.
 * Löst Entity-Typ und passendes Mob-Icon auf.
 */
public final class SpawnerTracker {
	public static final class SpawnerEntry {
		public final BlockPos pos;
		public final String dim;
		public EntityType<?> mobType;
		public String mobName;
		public ItemStack icon;
		public final long firstSeen;
		public boolean notified;

		public SpawnerEntry(BlockPos pos, String dim) {
			this.pos = pos;
			this.dim = dim;
			this.firstSeen = System.currentTimeMillis();
			this.mobName = "Spawner";
			this.icon = new ItemStack(Blocks.SPAWNER);
		}

		public String displayName() {
			if (mobName == null || mobName.isEmpty() || mobName.equalsIgnoreCase("Spawner")) {
				return "Spawner";
			}
			return mobName.toLowerCase(java.util.Locale.ROOT).endsWith("spawner") ? mobName : mobName + " Spawner";
		}
	}

	private static final Map<Long, SpawnerEntry> SPAWNERS = new ConcurrentHashMap<>();
	private static int scanTimer;

	private SpawnerTracker() {
	}

	public static void clear() {
		SPAWNERS.clear();
	}

	/** Registriert einen Spawner an der Position pos. */
	public static SpawnerEntry register(BlockPos pos, String dim, EntityType<?> typeHint) {
		if (pos == null || dim == null) return null;
		long key = pos.asLong();
		SpawnerEntry entry = SPAWNERS.get(key);
		boolean isNew = false;
		if (entry == null) {
			entry = new SpawnerEntry(pos.immutable(), dim);
			SPAWNERS.put(key, entry);
			isNew = true;
		}

		if (typeHint != null && entry.mobType == null) {
			entry.mobType = typeHint;
			entry.mobName = typeHint.getDescription().getString();
			entry.icon = getMobIcon(typeHint);
		}

		Minecraft mc = Minecraft.getInstance();
		if (entry.mobType == null && mc.level != null) {
			resolveMob(mc, entry);
		}

		if (isNew && ModuleManager.SPAWNER_NOTI.isEnabled()) {
			ModuleManager.SPAWNER_NOTI.onSpawnerFound(entry);
		}

		return entry;
	}

	/** Anti-Xray-Bypass: greift Spawner-Tags direkt aus dem Netzwerk-Paket ab. */
	public static void onServerBlockEntity(BlockPos pos, BlockEntityType<?> type, CompoundTag nbt) {
		if (type != BlockEntityType.MOB_SPAWNER && type != BlockEntityType.TRIAL_SPAWNER) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		String dim = WaypointsModule.dimensionId(mc.level);

		EntityType<?> typeHint = null;
		if (nbt != null) {
			CompoundTag spawnData = nbt.getCompoundOrEmpty("SpawnData");
			if (spawnData.isEmpty()) spawnData = nbt.getCompoundOrEmpty("spawn_data");
			CompoundTag entityTag = spawnData.getCompoundOrEmpty("entity");
			String idStr = entityTag.getStringOr("id", "");
			if (idStr.isEmpty()) idStr = nbt.getStringOr("EntityId", "");
			if (!idStr.isEmpty()) {
				Identifier id = Identifier.tryParse(idStr);
				if (id != null) {
					typeHint = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
				}
			}
		}

		register(pos, dim, typeHint);
	}

	/** Block-Änderung: Spawner abgebaut oder neu platziert. */
	public static void onBlockChanged(BlockPos pos, BlockState newState) {
		if (newState.is(Blocks.SPAWNER) || newState.is(Blocks.TRIAL_SPAWNER)) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.level != null) {
				register(pos, WaypointsModule.dimensionId(mc.level), null);
			}
		} else {
			SPAWNERS.remove(pos.asLong());
		}
	}

	/** Chunk geladen: Block-Entities durchsuchen. */
	public static void onChunkLoad(ClientLevel level, LevelChunk chunk) {
		if (chunk == null || level == null) return;
		String dim = WaypointsModule.dimensionId(level);
		for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
			BlockEntity be = entry.getValue();
			if (be instanceof SpawnerBlockEntity || be instanceof TrialSpawnerBlockEntity) {
				register(entry.getKey(), dim, null);
			}
		}
	}

	/** Chunk entladen: nur alte Einträge verwalten, wenn gewünscht. */
	public static void onChunkUnload(ClientLevel level, LevelChunk chunk) {
		// Spawner-Koordinaten können optional erhalten bleiben, damit man sie nicht verliert
	}

	/** Regelmäßiger Tick: Mob-Typen auflösen und abgelaufene Spawner prüfen. */
	public static void tick(Minecraft mc) {
		if (mc.level == null || mc.player == null) return;
		String dim = WaypointsModule.dimensionId(mc.level);

		// Nicht aufgelöste Spawner nachladen
		for (SpawnerEntry entry : SPAWNERS.values()) {
			if (!entry.dim.equals(dim)) continue;
			if (entry.mobType == null) {
				resolveMob(mc, entry);
			}
		}

		// Gelegentlicher Scan um den Spieler (Radius 6 Chunks)
		if (++scanTimer >= 20) {
			scanTimer = 0;
			scanNearbyChunks(mc, dim);
			pruneMissing(mc, dim);
		}
	}

	private static void scanNearbyChunks(Minecraft mc, String dim) {
		ChunkPos center = mc.player.chunkPosition();
		int r = 6;
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				LevelChunk chunk = mc.level.getChunkSource().getChunk(center.x + dx, center.z + dz, ChunkStatus.FULL, false);
				if (chunk == null) continue;
				for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
					BlockEntity be = e.getValue();
					if (be instanceof SpawnerBlockEntity || be instanceof TrialSpawnerBlockEntity) {
						register(e.getKey(), dim, null);
					}
				}
			}
		}
	}

	private static void pruneMissing(Minecraft mc, String dim) {
		for (SpawnerEntry entry : new ArrayList<>(SPAWNERS.values())) {
			if (!entry.dim.equals(dim)) continue;
			if (mc.level.isLoaded(entry.pos)) {
				BlockState st = mc.level.getBlockState(entry.pos);
				if (!st.is(Blocks.SPAWNER) && !st.is(Blocks.TRIAL_SPAWNER)) {
					SPAWNERS.remove(entry.pos.asLong());
				}
			}
		}
	}

	public static void resolveMob(Minecraft mc, SpawnerEntry entry) {
		if (entry == null || mc.level == null || !mc.level.isLoaded(entry.pos)) return;
		try {
			BlockEntity be = mc.level.getBlockEntity(entry.pos);
			if (be instanceof SpawnerBlockEntity sbe) {
				Entity ent = sbe.getSpawner().getOrCreateDisplayEntity(mc.level, entry.pos);
				if (ent != null) {
					entry.mobType = ent.getType();
					entry.mobName = ent.getType().getDescription().getString();
					entry.icon = getMobIcon(entry.mobType);
					return;
				}
			} else if (be instanceof TrialSpawnerBlockEntity tsbe) {
				var ts = tsbe.getTrialSpawner();
				Entity ent = ts.getStateData().getOrCreateDisplayEntity(ts, mc.level, ts.getState());
				if (ent != null) {
					entry.mobType = ent.getType();
					entry.mobName = "Trial " + ent.getType().getDescription().getString();
					entry.icon = getMobIcon(entry.mobType);
					return;
				}
				entry.mobName = "Trial Spawner";
				entry.icon = new ItemStack(Blocks.TRIAL_SPAWNER);
				return;
			}
		} catch (Throwable ignored) {
		}
	}

	/** Liefert das passende Minecraft-Item/Icon für einen Mob-Typ. */
	public static ItemStack getMobIcon(EntityType<?> type) {
		if (type == null) return new ItemStack(Blocks.SPAWNER);

		// Klassische Mobs: Schädel und markante Drops
		if (type == EntityType.SKELETON) return new ItemStack(Items.SKELETON_SKULL);
		if (type == EntityType.WITHER_SKELETON) return new ItemStack(Items.WITHER_SKELETON_SKULL);
		if (type == EntityType.ZOMBIE) return new ItemStack(Items.ZOMBIE_HEAD);
		if (type == EntityType.CREEPER) return new ItemStack(Items.CREEPER_HEAD);
		if (type == EntityType.PIGLIN) return new ItemStack(Items.PIGLIN_HEAD);
		if (type == EntityType.ENDER_DRAGON) return new ItemStack(Items.DRAGON_HEAD);

		if (type == EntityType.BLAZE) return new ItemStack(Items.BLAZE_ROD);
		if (type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER) return new ItemStack(Items.SPIDER_EYE);
		if (type == EntityType.ENDERMAN) return new ItemStack(Items.ENDER_PEARL);
		if (type == EntityType.MAGMA_CUBE) return new ItemStack(Items.MAGMA_CREAM);
		if (type == EntityType.SLIME) return new ItemStack(Items.SLIME_BALL);
		if (type == EntityType.GHAST) return new ItemStack(Items.GHAST_TEAR);
		if (type == EntityType.BREEZE) return new ItemStack(Items.BREEZE_ROD);
		if (type == EntityType.BOGGED) return new ItemStack(Items.POISONOUS_POTATO);
		if (type == EntityType.DROWNED) return new ItemStack(Items.NAUTILUS_SHELL);
		if (type == EntityType.STRAY) return new ItemStack(Items.TIPPED_ARROW);
		if (type == EntityType.WITCH) return new ItemStack(Items.POTION);
		if (type == EntityType.SILVERFISH) return new ItemStack(Items.INFESTED_STONE);
		if (type == EntityType.IRON_GOLEM) return new ItemStack(Items.IRON_INGOT);

		// Spawn-Egg Fallback (jede Mob-Art in Minecraft besitzt ein Spawn-Egg mit passenden Farben)
		SpawnEggItem egg = SpawnEggItem.byId(type);
		if (egg != null) return new ItemStack(egg);

		return new ItemStack(Blocks.SPAWNER);
	}

	public static List<SpawnerEntry> getActiveSpawners(String dim) {
		List<SpawnerEntry> list = new ArrayList<>();
		for (SpawnerEntry e : SPAWNERS.values()) {
			if (dim == null || e.dim.equals(dim)) {
				list.add(e);
			}
		}
		return list;
	}
}
