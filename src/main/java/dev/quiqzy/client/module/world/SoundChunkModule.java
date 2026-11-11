package dev.quiqzy.client.module.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.UiSounds;

/**
 * Sound Chunks: liest eingehende Sound-Pakete aus und markiert Chunks, in denen Maschinen-
 * Sounds zu hören sind (Smoker, Crafter, Ofen, Hochofen, Lagerfeuer). Solche Sounds kommen
 * direkt von der Maschinen-Position -> versteckte Farmen/Basen verraten sich dadurch.
 * Jede Maschinen-Position wird gespeichert (Kästen in der Welt), je Chunk gibt es einen
 * Chunk-Kasten + Strahl, sobald genug verschiedene Machine-Sounds gehört wurden.
 */
public final class SoundChunkModule extends Module {
	/** Sonst wächst die Liste mit jedem Crack. */
	private static final int MAX_SOURCES = 256;

	public final Setting.Bool smoker = add(new Setting.Bool("smoker", "Smoker", true));
	public final Setting.Bool crafter = add(new Setting.Bool("crafter", "Crafter", true));
	public final Setting.Bool furnace = add(new Setting.Bool("furnace", "Furnace", false));
	public final Setting.Bool blastfurnace = add(new Setting.Bool("blast_furnace", "Blast Furnace", false));
	public final Setting.Bool campfire = add(new Setting.Bool("campfire", "Campfire", false));
	public final Setting.Bool dispenser = add(new Setting.Bool("dispenser", "Dispenser", false));
	public final Setting.Int minSounds = add(new Setting.Int("min_sounds", "Min. Sounds", 1, 1, 5));
	public final Setting.Int forget = add(new Setting.Int("forget", "Forget After", 10, 0, 60, " min"));
	public final Setting.Bool beams = add(new Setting.Bool("beams", "Chunk Highlight", true));
	public final Setting.Bool throughWalls = add(new Setting.Bool("through_walls", "Through Blocks", true));
	public final Setting.Bool sourceBoxes = add(new Setting.Bool("source_boxes", "Sound Boxes", true));
	public final Setting.Bool hud = add(new Setting.Bool("hud", "HUD", true));
	public final Setting.Bool sound = add(new Setting.Bool("sound", "Sound on Find", true));

	/** Eine Maschine, deren Sound der Client gehört hat. */
	public record Source(String kind, int x, int y, int z, long firstSeen, long lastSeen, int count) {
	}

	public static final class ChunkData {
		public final ChunkPos pos;
		public final Map<String, Source> sources = new LinkedHashMap<>();
		public double minX, minY, minZ, maxX, maxY, maxZ;

		ChunkData(ChunkPos pos) {
			this.pos = pos;
			resetBounds();
		}

		void resetBounds() {
			minX = maxX = pos.getMinBlockX() + 8.0;
			minY = maxY = 0.0;
			minZ = maxZ = pos.getMinBlockZ() + 8.0;
		}

		public List<Source> list() {
			return new ArrayList<>(sources.values());
		}

		/** Verschiedene Maschinen-Sounds in diesem Chunk (z. B. smoker + crafter = 2). */
		public int kinds() {
			return sources.size();
		}

		/** Wie oft gehört insgesamt. */
		public int total() {
			int n = 0;
			for (Source s : sources.values()) n += s.count();
			return n;
		}

		/** Kurzer Grund fürs HUD, z. B. "smoker x12 · crafter". */
		public String reason() {
			StringBuilder b = new StringBuilder();
			sources.forEach((kind, s) -> {
				if (b.length() > 0) b.append(" · ");
				b.append(kind);
				if (s.count() > 1) b.append(" x").append(s.count());
			});
			return b.toString();
		}

		int newestY() {
			int y = 0;
			for (Source s : sources.values()) y = Math.max(y, s.y());
			return y;
		}

		int lowestY() {
			int y = Integer.MAX_VALUE;
			for (Source s : sources.values()) y = Math.min(y, s.y());
			return y == Integer.MAX_VALUE ? 0 : y;
		}
	}

	private final Map<Long, ChunkData> chunks = new HashMap<>();

	private long flashUntil;
	private ChunkPos lastFound;

	public SoundChunkModule() {
		super("sound_chunks", "Sound Chunks", "Hears machine sounds (smoker, crafter, furnace) and marks their chunks", Category.WORLD);
	}

	// ------------------------------------------------------------------ Sound-Pakete

	/** Wird vom ClientPacketListenerMixin für jedes Sound-Paket aufgerufen. */
	public void onSoundPacket(ClientboundSoundPacket packet) {
		if (packet.getSource() != SoundSource.BLOCKS) return;
		String path = packet.getSound().value().location().getPath();
		onSound(path, packet.getX(), packet.getY(), packet.getZ());
	}

	/** Aufgerufen von ClientChunkEvents.CHUNK_UNLOAD. */
	public void onChunkUnload(net.minecraft.client.multiplayer.ClientLevel level, net.minecraft.world.level.chunk.LevelChunk chunk) {
		chunks.remove(chunk.getPos().toLong());
	}

	/** Passt der Sound zu einer eingeschalteten Maschine? (Client-Sounds: Ofen, Smoker, Lagerfeuer, ...) */
	public String kindFor(String path) {
		if (smoker.on() && path.startsWith("block.smoker.")) return "smoker";
		if (crafter.on() && path.startsWith("block.crafter.")) return "crafter";
		if (furnace.on() && path.startsWith("block.furnace.")) return "furnace";
		if (blastfurnace.on() && path.startsWith("block.blastfurnace.")) return "blast furnace";
		if (campfire.on() && path.startsWith("block.campfire.")) return "campfire";
		return null;
	}

	/** Client-Sound abzeichnen (animateTick zwischen Ofen/Smoker/Lagerfeuer spielt selbst). */
	public void onLocalSound(SoundEvent sound, SoundSource source, double x, double y, double z) {
		if (!isEnabled() || source != SoundSource.BLOCKS) return;
		String path = sound.location().getPath();
		String kind = kindFor(path);
		if (kind == null) return;
		onSoundKind(kind, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
	}

	/** Level-Events (Server): Crafter, Brewing Stand, Dispenser -> Maschine an Position. */
	public String kindForEvent(int type) {
		int evCrafter = net.minecraft.world.level.block.LevelEvent.SOUND_CRAFTER_CRAFT;
		int evCrafterFail = net.minecraft.world.level.block.LevelEvent.SOUND_CRAFTER_FAIL;
		int evDispense = net.minecraft.world.level.block.LevelEvent.SOUND_DISPENSER_DISPENSE;
		int evShoot = net.minecraft.world.level.block.LevelEvent.SOUND_DISPENSER_PROJECTILE_LAUNCH;
		if (crafter.on() && (type == evCrafter || type == evCrafterFail)) return "crafter";
		if (dispenser.on() && (type == evDispense || type == evShoot)) return "dispenser";
		return null;
	}

	public void onLevelEvent(int type, int bx, int by, int bz) {
		if (!isEnabled()) return;
		String kind = kindForEvent(type);
		if (kind == null) return;
		onSoundKind(kind, bx, by, bz);
	}

	private void onSound(String path, double x, double y, double z) {
		if (!isEnabled()) return;
		String kind = kindFor(path);
		if (kind == null) return;
		onSoundKind(kind, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
	}

	/** Maschinen-Sound an Block-Position registrieren. */
	public void onSoundKind(String kind, int bx, int by, int bz) {
		if (!isEnabled()) return;
		long key = ChunkPos.asLong(bx >> 4, bz >> 4);
		long now = System.currentTimeMillis();

		ChunkData data = chunks.get(key);
		if (data == null) {
			data = new ChunkData(new ChunkPos(bx >> 4, bz >> 4));
			chunks.put(key, data);
		}

		Source found = data.sources.get(kind);
		if (found != null && Math.abs(found.x - bx) + Math.abs(found.y - by) + Math.abs(found.z - bz) <= 2) {
			data.sources.put(kind, new Source(kind, bx, by, bz, found.firstSeen, now, found.count() + 1));
		} else if (found == null && data.sources.size() < MAX_SOURCES) {
			data.sources.put(kind, new Source(kind, bx, by, bz, now, now, 1));
		}

		// Grenzen des Chunk-Kastens neu ziehen
		data.resetBounds();
		double x0 = data.pos.getMinBlockX(), z0 = data.pos.getMinBlockZ();
		data.minX = x0;
		data.maxX = x0 + 16.0;
		data.minZ = z0;
		data.maxZ = z0 + 16.0;
		data.minY = data.lowestY() - 0.5;
		data.maxY = data.newestY() + 1.5;
		if (data.maxY - data.minY < 4.0) {
			data.minY -= 2.0;
			data.maxY += 2.0;
		}

		boolean sus = isSus(data);
		if (sus && (lastFound == null || lastFound.toLong() != key) && flashUntil < now - 3000) {
			lastFound = data.pos;
			flashUntil = now + 3500;
			if (sound.on()) UiSounds.found();
		}
	}

	// ------------------------------------------------------------------ Tick

	@Override
	public void onTick(Minecraft mc) {
		long now = System.currentTimeMillis();
		long ttl = forget.get() * 60_000L;
		if (ttl <= 0) return;

		Iterator<Map.Entry<Long, ChunkData>> it = chunks.entrySet().iterator();
		while (it.hasNext()) {
			long newest = 0;
			for (Source s : it.next().getValue().sources.values()) newest = Math.max(newest, s.lastSeen());
			// Maschine hörbar auf -> lastSeen frisch -> bleibt; sonst nach Ablauf vergessen
			if (now - newest > ttl) it.remove();
		}
	}

	// ------------------------------------------------------------------ Abfragen

	public boolean isSus(ChunkData d) {
		return d != null && d.kinds() >= minSounds.get();
	}

	/** Alle markierten Chunks (Chunk-Anzeige: nur "sus" ab Min. Sounds, Rest nur Kästen). */
	public List<ChunkData> marked() {
		return new ArrayList<>(chunks.values());
	}

	public List<ChunkData> susChunks() {
		List<ChunkData> list = new ArrayList<>();
		for (ChunkData d : chunks.values()) if (isSus(d)) list.add(d);
		return list;
	}

	public double distSq(LocalPlayer p, ChunkData d) {
		double cx = d.pos.getMiddleBlockX() + 0.5 - p.getX();
		double cz = d.pos.getMiddleBlockZ() + 0.5 - p.getZ();
		return cx * cx + cz * cz;
	}

	public boolean isFlashing() {
		return lastFound != null && System.currentTimeMillis() < flashUntil;
	}

	public void clear() {
		chunks.clear();
		lastFound = null;
		flashUntil = 0;
	}

	@Override
	protected void onDisable() {
		clear();
	}
}
