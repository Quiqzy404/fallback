package dev.quiqzy.client.module.visuals;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Entity ESP: Boxen um Spieler, Monster, Tiere und Items. Aussehen komplett einstellbar. Gezeichnet vom EspRenderer. */
public final class EntityEspModule extends Module {
	// Was angezeigt wird
	public final Setting.Bool players = add(new Setting.Bool("players", "Players", true));
	public final Setting.Bool hostiles = add(new Setting.Bool("hostiles", "Hostile Mobs", true));
	public final Setting.Bool animals = add(new Setting.Bool("animals", "Animals / Others", false));
	public final Setting.Bool items = add(new Setting.Bool("items", "Items", false));
	public final Setting.Int range = add(new Setting.Int("range", "Range", 64, 8, 256, " m"));
	public final Setting.Bool throughWalls = add(new Setting.Bool("through_walls", "Through Blocks", true));

	// Aussehen
	public final Setting.Choice style = add(new Setting.Choice("style", "Style", "Fill + Outline", "Fill + Outline", "Outline", "Fill", "Corners"));
	public final Setting.Color playerColor = add(new Setting.Color("player_color", "Player Color", 0xFF5A6E));
	public final Setting.Color hostileColor = add(new Setting.Color("hostile_color", "Hostile Color", 0xFF9A3D));
	public final Setting.Color animalColor = add(new Setting.Color("animal_color", "Animal Color", 0x5CE68A));
	public final Setting.Color itemColor = add(new Setting.Color("item_color", "Item Color", 0xFFE066));
	public final Setting.Int fillOpacity = add(new Setting.Int("fill_opacity", "Fill Opacity", 18, 0, 100, " %"));
	public final Setting.Int lineOpacity = add(new Setting.Int("line_opacity", "Outline Opacity", 95, 0, 100, " %"));
	public final Setting.Int lineWidth = add(new Setting.Int("line_width", "Outline Width", 3, 1, 10));
	public final Setting.Int boxSize = add(new Setting.Int("box_size", "Box Padding", 0, 0, 30, " %"));
	public final Setting.Bool tracers = add(new Setting.Bool("tracers", "Tracers", false));
	public final Setting.Bool rainbow = add(new Setting.Bool("rainbow", "Rainbow", false));
	public final Setting.Int rainbowSpeed = add(new Setting.Int("rainbow_speed", "Rainbow Speed", 3, 1, 10));

	public EntityEspModule() {
		super("entity_esp", "Entity ESP", "Boxes around players, mobs and items", Category.VISUALS);
	}

	/** Farbe 0xRRGGBB für ein Entity, -1 = nicht zeichnen. */
	public int colorFor(Minecraft mc, Entity e) {
		int g = EspColors.group(mc, e, players.on(), hostiles.on(), animals.on(), items.on(), range.get());
		if (g == EspColors.NONE) return -1;
		if (rainbow.on()) return EspColors.rainbow(rainbowSpeed.get(), (e.getId() % 16) / 16f);
		return switch (g) {
			case EspColors.PLAYER -> playerColor.get();
			case EspColors.HOSTILE -> hostileColor.get();
			case EspColors.ANIMAL -> animalColor.get();
			default -> itemColor.get();
		};
	}

	public boolean drawFill() {
		return style.get().contains("Fill");
	}

	public boolean drawOutline() {
		return style.get().contains("Outline");
	}

	public boolean drawCorners() {
		return style.get().equals("Corners");
	}
}
