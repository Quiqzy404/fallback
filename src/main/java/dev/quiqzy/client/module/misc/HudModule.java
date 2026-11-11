package dev.quiqzy.client.module.misc;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Info-HUD oben rechts + Rüstungsanzeige über der Hotbar. Gezeichnet in hud/InfoHud. */
public final class HudModule extends Module {
	public final Setting.Bool coords = add(new Setting.Bool("coords", "Coordinates", true));
	public final Setting.Bool nether = add(new Setting.Bool("nether", "Nether / Overworld Coords", true));
	public final Setting.Bool direction = add(new Setting.Bool("direction", "Direction", true));
	public final Setting.Bool fps = add(new Setting.Bool("fps", "FPS", true));
	public final Setting.Bool ping = add(new Setting.Bool("ping", "Ping", true));
	public final Setting.Bool totems = add(new Setting.Bool("totems", "Totem Counter", true));
	public final Setting.Bool armor = add(new Setting.Bool("armor", "Armor HUD", true));

	public HudModule() {
		super("hud", "HUD", "Coords, direction, FPS, ping, totems, armor", Category.HUD);
	}
}
