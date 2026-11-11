package dev.quiqzy.client.module.visuals;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Nametags: vollständige CatLean-Nametags über Spielern (optional Mobs, Items, Spawner).
 * Name, Ping, Leben mit Herz, Distanz, Health Bar, Ausrüstung mit Haltbarkeit & Verzauberungen, Trank-Badges.
 */
public final class NametagsModule extends Module {
	public final Setting.Bool players = add(new Setting.Bool("players", "Players", true));
	public final Setting.Bool mobs = add(new Setting.Bool("mobs", "Mobs", false));
	public final Setting.Bool items = add(new Setting.Bool("items", "Items on Ground", true));
	public final Setting.Bool spawners = add(new Setting.Bool("spawners", "Spawners", true));
	public final Setting.Bool health = add(new Setting.Bool("health", "Health", true));
	public final Setting.Bool healthBar = add(new Setting.Bool("health_bar", "Health Bar", true));
	public final Setting.Bool distance = add(new Setting.Bool("distance", "Distance", true));
	public final Setting.Bool ping = add(new Setting.Bool("ping", "Ping", true));
	public final Setting.Bool gamemode = add(new Setting.Bool("gamemode", "Gamemode", true));
	public final Setting.Bool equipment = add(new Setting.Bool("equipment", "Equipment", true));
	public final Setting.Bool enchantments = add(new Setting.Bool("enchantments", "Enchantments", true));
	public final Setting.Bool potions = add(new Setting.Bool("potions", "Potions", true));
	public final Setting.Int range = add(new Setting.Int("range", "Range", 64, 8, 256, " m"));
	public final Setting.Int scale = add(new Setting.Int("scale", "Scale", 100, 50, 200, " %"));

	public NametagsModule() {
		super("nametags", "Nametags", "Complete CatLean nametags with gear, enchantments and health bar", Category.VISUALS);
	}
}
