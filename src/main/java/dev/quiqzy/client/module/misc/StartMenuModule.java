package dev.quiqzy.client.module.misc;

import dev.quiqzy.client.module.Module;

/** Custom Start Menu: ZERO-Logo und warmer Leucht-Hintergrund im Hauptmenü. */
public final class StartMenuModule extends Module {
	public StartMenuModule() {
		super("start_menu", "Start Menu", "ZERO logo and glow on the title screen", Category.MISC);
		setEnabled(true);
	}
}
