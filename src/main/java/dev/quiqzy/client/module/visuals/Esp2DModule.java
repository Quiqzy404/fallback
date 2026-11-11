package dev.quiqzy.client.module.visuals;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** 2D ESP: flache Rahmen um Spieler/Mobs mit seitlichem Lebensbalken (Health Bars). */
public final class Esp2DModule extends Module {
	public final Setting.Bool players = add(new Setting.Bool("players", "Players", true));
	public final Setting.Bool hostiles = add(new Setting.Bool("hostiles", "Hostile Mobs", true));
	public final Setting.Bool animals = add(new Setting.Bool("animals", "Animals", false));
	public final Setting.Choice style = add(new Setting.Choice("style", "Style", "Corners", "Corners", "Full Box"));
	public final Setting.Bool healthBar = add(new Setting.Bool("health_bar", "Health Bar", true));
	public final Setting.Bool tracers = add(new Setting.Bool("tracers", "Tracers", false));
	public final Setting.Int range = add(new Setting.Int("range", "Range", 64, 8, 256, " m"));

	public Esp2DModule() {
		super("esp_2d", "2D ESP", "Flat 2D boxes with health bars", Category.VISUALS);
	}
}
