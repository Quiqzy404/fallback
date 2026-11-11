package dev.quiqzy.client.module.misc;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Watermark: ZERO-Logo oben links mit Uhrzeit/FPS. */
public final class WatermarkModule extends Module {
	public final Setting.Bool time = add(new Setting.Bool("time", "Show Time", true));
	public final Setting.Bool fps = add(new Setting.Bool("fps", "Show FPS", true));

	public WatermarkModule() {
		super("watermark", "Watermark", "ZERO logo in the corner", Category.HUD);
	}
}
