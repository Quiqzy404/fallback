package dev.quiqzy.client.module.misc;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Low HP Warning: Bildschirmrand pulsiert rot, wenn dein Leben niedrig ist. */
public final class LowHpWarningModule extends Module {
	public final Setting.Int threshold = add(new Setting.Int("threshold", "Below", 6, 1, 20, " HP"));
	public final Setting.Bool sound = add(new Setting.Bool("sound", "Warning Sound", false));

	public LowHpWarningModule() {
		super("low_hp", "Low HP Warning", "Red pulsing edges when you're about to die", Category.HUD);
	}
}
