package dev.quiqzy.client.module.visuals;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Low Fire: niedrige, weiche Flammen am Bildschirm statt der großen Vanilla-Flammen. */
public final class LowFireModule extends Module {
	public final Setting.Int height = add(new Setting.Int("height", "Flame Height", 35, 0, 100, " %"));

	public LowFireModule() {
		super("low_fire", "Low Fire", "Low, soft flames so burning doesn't block your view", Category.VISUALS);
	}
}
