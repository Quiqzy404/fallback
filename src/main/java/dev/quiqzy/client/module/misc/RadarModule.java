package dev.quiqzy.client.module.misc;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Radar: runder Kompass, dreht sich mit dir; Spieler in der Nähe als Köpfe, Mobs als Punkte. */
public final class RadarModule extends Module {
	public final Setting.Int range = add(new Setting.Int("range", "Range", 48, 16, 128, " m"));
	public final Setting.Int size = add(new Setting.Int("size", "Size", 90, 60, 160));
	public final Setting.Bool mobs = add(new Setting.Bool("mobs", "Show Mobs", false));
	public final Setting.Bool names = add(new Setting.Bool("names", "Names", true));

	public RadarModule() {
		super("radar", "Radar", "Compass radar with nearby players", Category.HUD);
	}
}
