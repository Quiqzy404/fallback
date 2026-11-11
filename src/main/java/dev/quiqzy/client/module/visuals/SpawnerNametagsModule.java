package dev.quiqzy.client.module.visuals;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Spawner Nametags: zeigt schwebende Nametags über gefundenen Spawnern mit
 * passendem Mob-Icon, Spawner-Typ, Entfernung und Koordinaten.
 */
public final class SpawnerNametagsModule extends Module {
	public final Setting.Int range = add(new Setting.Int("range", "Range", 128, 16, 256, " m"));
	public final Setting.Int scale = add(new Setting.Int("scale", "Scale", 100, 50, 200, " %"));
	public final Setting.Bool showIcon = add(new Setting.Bool("icon", "Mob Icon", true));
	public final Setting.Bool showDistance = add(new Setting.Bool("distance", "Distance", true));
	public final Setting.Bool showCoords = add(new Setting.Bool("coords", "Coordinates", true));
	public final Setting.Bool throughWalls = add(new Setting.Bool("through_walls", "Through Blocks", true));

	public SpawnerNametagsModule() {
		super("spawner_nametags", "Spawner Nametags", "Displays stylish nametags with mob icons above spawners", Category.VISUALS);
	}
}
