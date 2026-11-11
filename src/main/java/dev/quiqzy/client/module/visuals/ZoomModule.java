package dev.quiqzy.client.module.visuals;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

import dev.quiqzy.client.QuiqzyClient;
import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.Reflect;

/**
 * Zoom: solange die Zoom-Taste (Standard C, in den Minecraft-Steuerungen änderbar) gedrückt ist,
 * wird das Sichtfeld kleiner. Gleicher Trick wie Fullbright: der FOV-Wert wird direkt gesetzt
 * und beim Loslassen wiederhergestellt. Kein Mixin nötig.
 */
public final class ZoomModule extends Module {
	/** OptionInstance#value – intermediary: class_7172.field_37868 (wie bei Fullbright) */
	private static final Field OPTION_VALUE = Reflect.field("net.minecraft.class_7172", "field_37868", "Ljava/lang/Object;");

	public final Setting.Int zoomFov = add(new Setting.Int("fov", "Zoom FOV", 15, 5, 50, "°"));
	public final Setting.Bool smooth = add(new Setting.Bool("smooth", "Smooth Camera", true));

	private Integer savedFov;
	private boolean savedSmooth;
	private float current = -1f; // aktuelles animiertes FOV, -1 = nicht gezoomt

	public ZoomModule() {
		super("zoom", "Zoom", "Hold C to zoom in (key changeable in Controls)", Category.VISUALS);
	}

	@Override
	public void onFrame(Minecraft mc) {
		boolean held = mc.screen == null && QuiqzyClient.ZOOM.isDown();
		OptionInstance<Integer> fov = mc.options.fov();

		if (held && savedFov == null) {
			savedFov = fov.get();
			savedSmooth = mc.options.smoothCamera;
			current = savedFov;
		}
		if (savedFov == null) return;

		float target = held ? zoomFov.get() : savedFov;
		current += (target - current) * 0.35f;

		if (!held && Math.abs(current - savedFov) < 0.6f) {
			restore(mc);
			return;
		}
		setRaw(fov, Math.round(current));
		mc.options.smoothCamera = held && smooth.on() || savedSmooth;
	}

	@Override
	protected void onDisable() {
		restore(Minecraft.getInstance());
	}

	private void restore(Minecraft mc) {
		if (savedFov == null) return;
		setRaw(mc.options.fov(), savedFov);
		mc.options.smoothCamera = savedSmooth;
		savedFov = null;
		current = -1f;
	}

	private static void setRaw(OptionInstance<Integer> option, int value) {
		if (OPTION_VALUE == null) return;
		try {
			OPTION_VALUE.set(option, value);
		} catch (Throwable ignored) {
		}
	}
}
