package dev.quiqzy.client.module.misc;

import dev.quiqzy.client.module.Module;

/** Potion HUD: aktive Effekte mit Icon, Stufe und Restzeit. */
public final class PotionHudModule extends Module {
	public PotionHudModule() {
		super("potion_hud", "Potion HUD", "Your active effects with time left", Category.HUD);
	}
}
