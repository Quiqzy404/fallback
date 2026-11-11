package dev.quiqzy.client.module.visuals;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.player.LocalPlayer;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.Reflect;

/**
 * No Overlay:
 *  - No Hurtcam: setzt die Minecraft-Option "Damage Tilt" (Barrierefreiheit) auf 0 %, stellt sie danach wieder her.
 *  - No Fire Overlay: der Client vergisst, dass du brennst -> kein Feuer vor der Kamera.
 *    Nur optisch, der Server rechnet Feuerschaden ganz normal.
 */
public final class NoOverlayModule extends Module {
	private static final Field OPTION_VALUE = Reflect.field("net.minecraft.class_7172", "field_37868", "Ljava/lang/Object;");

	public final Setting.Bool hurtcam = add(new Setting.Bool("hurtcam", "No Hurtcam", true));
	public final Setting.Bool fire = add(new Setting.Bool("fire", "No Fire Overlay", true));

	private Double savedTilt;

	public NoOverlayModule() {
		super("no_overlay", "No Overlay", "No hurt camera shake, no fire on screen", Category.VISUALS);
	}

	@Override
	public void onTick(Minecraft mc) {
		OptionInstance<Double> tilt = mc.options.damageTiltStrength();
		if (hurtcam.on()) {
			Double v = tilt.get();
			if (v != null && v != 0.0) {
				if (savedTilt == null) savedTilt = v;
				setRaw(tilt, 0.0);
			}
		} else {
			restoreTilt(mc);
		}
		clearFire(mc);
	}

	@Override
	public void onFrame(Minecraft mc) {
		clearFire(mc);
	}

	@Override
	protected void onDisable() {
		restoreTilt(Minecraft.getInstance());
	}

	private void clearFire(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (!fire.on() || p == null) return;
		if (p.isOnFire()) {
			p.setSharedFlagOnFire(false);
			p.setRemainingFireTicks(0);
		}
	}

	private void restoreTilt(Minecraft mc) {
		if (savedTilt == null) return;
		setRaw(mc.options.damageTiltStrength(), savedTilt);
		savedTilt = null;
	}

	private static void setRaw(OptionInstance<Double> option, double value) {
		if (OPTION_VALUE == null) return;
		try {
			OPTION_VALUE.set(option, value);
		} catch (Throwable ignored) {
		}
	}
}
