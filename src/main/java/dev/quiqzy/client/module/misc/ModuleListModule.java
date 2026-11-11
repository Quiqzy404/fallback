package dev.quiqzy.client.module.misc;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Modulliste (ArrayList): alle aktiven Module als animierte Liste. */
public final class ModuleListModule extends Module {
	public final Setting.Choice side = add(new Setting.Choice("side", "Side", "Left", "Left", "Right"));
	public final Setting.Bool hideVisuals = add(new Setting.Bool("hide_visuals", "Hide Visuals", false));

	public ModuleListModule() {
		super("module_list", "Module List", "Shows your active modules on screen", Category.HUD);
	}
}
