package dev.quiqzy.client.module.visuals;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.util.Reflect;

/**
 * Fullbright: everything bright, only for you.
 * 1. Gamma trick: brightness option set to 1500 % (Minecraft normally blocks anything over 100 %,
 *    so the value is written directly into the option). Restored when you turn the module off.
 * 2. Additionally a client-side Night Vision effect (no blinking).
 */
public final class FullbrightModule extends Module {
	private static final double GAMMA = 15.0;
	private static final int INFINITE = -1;

	/** OptionInstance#value – intermediary: class_7172.field_37868 */
	private static final Field OPTION_VALUE = Reflect.field("net.minecraft.class_7172", "field_37868", "Ljava/lang/Object;");

	/** Your real brightness, to restore it afterwards. */
	private Double savedGamma;

	public FullbrightModule() {
		super("fullbright", "Fullbright", "Full brightness in every cave", Category.VISUALS);
	}

	@Override
	public void onTick(Minecraft mc) {
		OptionInstance<Double> gamma = mc.options.gamma();
		Double current = gamma.get();
		if (current == null || current != GAMMA) {
			if (savedGamma == null && current != null && current <= 1.0) savedGamma = current;
			setRaw(gamma, GAMMA);
		}

		if (mc.player != null && !mc.player.hasEffect(MobEffects.NIGHT_VISION)) {
			mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, INFINITE, 0, false, false, false));
		}
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		setRaw(mc.options.gamma(), savedGamma != null ? savedGamma : 0.5);
		savedGamma = null;
		if (mc.player != null) mc.player.removeEffect(MobEffects.NIGHT_VISION);
	}

	private static void setRaw(OptionInstance<Double> option, double value) {
		if (OPTION_VALUE == null) return;
		try {
			OPTION_VALUE.set(option, value);
		} catch (Throwable ignored) {
		}
	}
}
