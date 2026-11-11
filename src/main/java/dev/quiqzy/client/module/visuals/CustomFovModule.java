package dev.quiqzy.client.module.visuals;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Custom FOV: eigenes Sichtfeld über das Vanilla-Maximum hinaus; optional ohne Sprint-/Effekt-Zoom. */
public final class CustomFovModule extends Module {
	public final Setting.Int fov = add(new Setting.Int("fov", "FOV", 100, 30, 150, "°"));
	public final Setting.Bool staticFov = add(new Setting.Bool("static", "Static (no sprint zoom)", false));

	public CustomFovModule() {
		super("custom_fov", "Custom FOV", "Set any field of view, optionally without sprint zoom", Category.VISUALS);
	}
}
