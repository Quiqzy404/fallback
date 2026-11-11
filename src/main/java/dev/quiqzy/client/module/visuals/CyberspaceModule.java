package dev.quiqzy.client.module.visuals;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Cyberspace: leuchtendes Gitter am Himmel und Sternschnuppen – Berge und Häuser verdecken es ganz normal. */
public final class CyberspaceModule extends Module {
	public final Setting.Bool grid = add(new Setting.Bool("grid", "Sky Grid", true));
	public final Setting.Bool stars = add(new Setting.Bool("stars", "Shooting Stars", true));
	public final Setting.Int density = add(new Setting.Int("density", "Grid Density", 12, 6, 24));

	public CyberspaceModule() {
		super("cyberspace", "Cyberspace", "Glowing sky grid and shooting stars", Category.VISUALS);
	}
}
