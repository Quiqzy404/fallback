package dev.quiqzy.client.module.misc;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Keystrokes: zeigt W A S D, Leertaste und die Maustasten unten links. Gezeichnet in hud/InfoHud. */
public final class KeystrokesModule extends Module {
	/** Creamy Keys: weicher mechanischer Tastenklang beim Drücken. */
	public final Setting.Bool keySounds = add(new Setting.Bool("key_sounds", "Creamy Key Sounds", false));
	public final Setting.Int keyVolume = add(new Setting.Int("key_volume", "Key Volume", 60, 0, 100, " %"));

	public KeystrokesModule() {
		super("keystrokes", "Keystrokes", "Shows WASD, space and mouse clicks", Category.HUD);
	}
}
