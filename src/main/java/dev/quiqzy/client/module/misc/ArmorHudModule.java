package dev.quiqzy.client.module.misc;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Armor HUD: Rüstung + Haltbarkeit als Panel neben der Hotbar. */
public final class ArmorHudModule extends Module {
	public final Setting.Bool percent = add(new Setting.Bool("percent", "Show Percent", true));
	public final Setting.Bool mainHand = add(new Setting.Bool("main_hand", "Include Main Hand", true));

	public ArmorHudModule() {
		super("armor_hud", "Armor HUD", "Armor and durability next to your hotbar", Category.HUD);
	}
}
