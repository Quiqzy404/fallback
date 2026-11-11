package dev.quiqzy.client.module.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.UiSounds;

/**
 * Base Radar (Anti-Xray-Bypass): Anti-Xray versteckt Blöcke unter Deepslate im Chunk-Paket, aber
 * Block-Änderungen kommen IMMER als Update-Paket an (Chunk-Updates sind vom Anti-Xray nicht betroffen).
 * Da landet jede Block-Änderung in Reichweite - auch wenn jemand unten gräbt/baut. Die Mod merkt sich
 * diese Positionen und zeichnet sie als leuchtende Spur (Dig Trail) - damit sieht man durch Deepslate,
 * WO gerade gearbeitet wird. Cluster mehrerer Updates = mögliche Base unter Deepslate.
 */
public final class BaseRadarModule extends Module {
	private static final int MAX_UPDATES = 2048;

	public final Setting.Bool deepOnly = add(new Setting.Bool("deep_only", "Only Below Deepslate", true));
	public final Setting.Int deepY = add(new Setting.Int("deep_y", "Deepslate Y", 0, -64, 200));
	public final Setting.Bool showNearSurface = add(new Setting.Bool("show_near_surface", "Show Near Surface", false));
	/** Neubelebte Spuren (z. B. Sand fällt) im selben Tick-Kranz ignorieren -> sonst Regen/Sand-Spam. */
	public final Setting.Int forget = add(new Setting.Int("forget", "Forget After", 15, 1, 120, " min"));
	public final Setting.Bool trails = add(new Setting.Bool("trails", "Dig Trails", true));
	public final Setting.Bool clusters = add(new Setting.Bool("clusters", "Cluster Highlight", true));
	/** Ab wie vielen Updates im selben Chunk gilt: Hier wird unter Deepslate gebaut -> Verdacht. */
	public final Setting.Int minCluster = add(new Setting.Int("min_cluster", "Min. Updates", 8, 1, 100));
	public final Setting.Bool throughWalls = add(new Setting.Bool("through_walls", "Through Blocks", true));
	public final Setting.Bool sound = add(new Setting.Bool("sound", "Sound on Cluster", true));

	/** Ein Block-Update des Servers: Position + neuer Block + wann. */
	public record Update(long pos, int x, int y, int z, String block, long at) {
	}

	/** Cluster: mehrere Updates im selben Chunk = wahrscheinliche Base unter Deepslate. */
	public static final class Cluster {
		public final ChunkPos pos;
		public int count;
		public long last;

		Cluster(ChunkPos pos) {
			this.pos = pos;
		}

		/** Kurze Zusammenfassung fürs HUD (nicht genutzt von Sound Chunks). */
		public String reason() {
			return count + " updates";
		}
	}

	private final Map<Long, Update> updates = new HashMap<>();
	private final Map<Long, Cluster> clusterMap = new HashMap<>();
	private long lastClusterAt;
	private int totalSeen;

	public BaseRadarModule() {
		super("base_radar", "Base Radar", "Tracks block updates (dig trails) under deepslate, even hidden by anti-xray", Category.WORLD);
	}

	// ------------------------------------------------------------------ Pakete (vom Mixin)

	/** Einzelner Block-Update (z. B. Abbau/Setzen, Feuer, Portal). */
	public void onBlockUpdate(BlockPos pos, BlockState state) {
		record(pos, state);
	}

	/** Mehrere Block-Updates in einem Chunk-Section-Paket. */
	public void onSectionBlocks(ClientboundSectionBlocksUpdatePacket packet) {
		packet.runUpdates((p, state) -> record(p, state));
	}

	private void record(BlockPos pos, BlockState state) {
		if (!isEnabled()) return;
		if (deepOnly.on() && pos.getY() >= deepY.get()) return;

		long now = System.currentTimeMillis();
		long key = pos.asLong();
		Update old = updates.get(key);
		int y = pos.getY();
		// Oberflächen-Spam ignorieren (Regen, Sand, Water), wenn nicht explizit angezeigt
		if (!showNearSurface.on() && y > deepY.get() + 8) return;

		updates.put(key, new Update(key, pos.getX(), y, pos.getZ(), state.getBlock().getName().toString(), now));
		if (old == null && updates.size() > MAX_UPDATES) {
			updates.remove(updates.keySet().iterator().next());
		}

		long chunk = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
		Cluster c = clusterMap.get(chunk);
		if (c == null) {
			c = new Cluster(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
			clusterMap.put(chunk, c);
		}
		c.count++;
		c.last = now;
		totalSeen++;

		if (c.count == minCluster.get()) {
			lastClusterAt = now;
			if (sound.on()) UiSounds.found();
		}
	}

	// ------------------------------------------------------------------ Tick

	@Override
	public void onTick(Minecraft mc) {
		// Spuren, die zu alt sind, wieder vergessen - sonst wächst die Liste endlos
		long ttl = forget.get() * 60_000L;
		long now = System.currentTimeMillis();
		Iterator<Map.Entry<Long, Update>> it = updates.entrySet().iterator();
		while (it.hasNext()) {
			if (now - it.next().getValue().at() > ttl) it.remove();
		}
		Iterator<Map.Entry<Long, Cluster>> cit = clusterMap.entrySet().iterator();
		while (cit.hasNext()) {
			if (now - cit.next().getValue().last > ttl) cit.remove();
		}
	}

	// ------------------------------------------------------------------ Abfragen

	public List<Update> trailUpdates() {
		return new ArrayList<>(updates.values());
	}

	public List<Cluster> suspiciousClusters() {
		List<Cluster> list = new ArrayList<>();
		for (Cluster c : clusterMap.values()) if (c.count >= minCluster.get()) list.add(c);
		return list;
	}

	public int totalSeen() {
		return totalSeen;
	}

	/** Frischer Verdacht (für HUD/Flash). */
	public boolean flashing() {
		return lastClusterAt != 0 && System.currentTimeMillis() - lastClusterAt < 3500;
	}

	@Override
	protected void onDisable() {
		updates.clear();
		clusterMap.clear();
		lastClusterAt = 0;
	}

	/** Chunk-Unload: alles vergessen, was nicht mehr geladen ist (damit alte Welt nicht mitschleppt). */
	public void onChunkUnload(ClientLevel level, net.minecraft.world.level.chunk.LevelChunk chunk) {
		long key = chunk.getPos().toLong();
		clusterMap.remove(key);
		int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
		updates.values().removeIf(u -> (u.x() >> 4) == (minX >> 4) && (u.z() >> 4) == (minZ >> 4));
	}
}
