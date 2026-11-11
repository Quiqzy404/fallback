package dev.quiqzy.client.module.visuals;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Tracers: Linien zu Spielern/Mobs/Items und optional zu den Block-ESP-Treffern. Aussehen komplett einstellbar. */
public final class TracersModule extends Module {
	// Was angezeigt wird
	public final Setting.Bool players = add(new Setting.Bool("players", "Players", true));
	public final Setting.Bool hostiles = add(new Setting.Bool("hostiles", "Hostile Mobs", false));
	public final Setting.Bool animals = add(new Setting.Bool("animals", "Animals / Others", false));
	public final Setting.Bool items = add(new Setting.Bool("items", "Items", false));
	public final Setting.Bool blocks = add(new Setting.Bool("blocks", "Block ESP Blocks", true));
	public final Setting.Int range = add(new Setting.Int("range", "Range", 96, 8, 256, " m"));

	// Aussehen
	/** Body/Eyes/Feet = echte 3D-Linie aus deinem Körper (sieht man in F5/Freecam), Crosshair = flach aus der Bildmitte. */
	public final Setting.Choice start = add(new Setting.Choice("start_3d", "Start From", "Body", "Body", "Eyes", "Feet", "Crosshair", "Bottom of Screen"));
	public final Setting.Choice end = add(new Setting.Choice("end", "Aim At", "Body", "Body", "Head", "Feet"));
	public final Setting.Choice colorMode = add(new Setting.Choice("color_mode", "Color Mode", "Per Type", "Per Type", "Distance", "Rainbow", "Single Color"));
	public final Setting.Color playerColor = add(new Setting.Color("player_color", "Player Color", 0xFF5A6E));
	public final Setting.Color hostileColor = add(new Setting.Color("hostile_color", "Hostile Color", 0xFF9A3D));
	public final Setting.Color animalColor = add(new Setting.Color("animal_color", "Animal Color", 0x5CE68A));
	public final Setting.Color itemColor = add(new Setting.Color("item_color", "Item Color", 0xFFE066));
	public final Setting.Color singleColor = add(new Setting.Color("single_color", "Single Color", 0x93B6FF));
	public final Setting.Choice blockColor = add(new Setting.Choice("block_color_mode", "Block Line Color", "Block Color", "Block Color", "Same as Mode"));
	public final Setting.Int width = add(new Setting.Int("width", "Line Width", 3, 1, 10));
	public final Setting.Int opacity = add(new Setting.Int("opacity", "Opacity", 85, 5, 100, " %"));
	public final Setting.Int rainbowSpeed = add(new Setting.Int("rainbow_speed", "Rainbow Speed", 3, 1, 10));

	public TracersModule() {
		super("tracers", "Tracers", "Lines to players, mobs and Block ESP finds", Category.VISUALS);
	}

	/** Farbe 0xRRGGBB für ein Entity, -1 = keine Linie. */
	public int colorFor(Minecraft mc, Entity e) {
		int g = EspColors.group(mc, e, players.on(), hostiles.on(), animals.on(), items.on(), range.get());
		if (g == EspColors.NONE) return -1;
		return switch (colorMode.get()) {
			case "Distance" -> EspColors.byDistance(Math.sqrt(mc.player.distanceToSqr(e)), range.get());
			case "Rainbow" -> EspColors.rainbow(rainbowSpeed.get(), (e.getId() % 16) / 16f);
			case "Single Color" -> singleColor.get();
			default -> switch (g) {
				case EspColors.PLAYER -> playerColor.get();
				case EspColors.HOSTILE -> hostileColor.get();
				case EspColors.ANIMAL -> animalColor.get();
				default -> itemColor.get();
			};
		};
	}

	/** Farbe für eine Linie zu einem Block-ESP-Treffer. */
	public int blockColorFor(int blockRgb, double dist, int index) {
		if (blockColor.get().equals("Block Color")) return blockRgb;
		return switch (colorMode.get()) {
			case "Distance" -> EspColors.byDistance(dist, range.get());
			case "Rainbow" -> EspColors.rainbow(rainbowSpeed.get(), (index % 16) / 16f);
			case "Single Color" -> singleColor.get();
			default -> blockRgb;
		};
	}
}
