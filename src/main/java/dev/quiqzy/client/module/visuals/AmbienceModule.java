package dev.quiqzy.client.module.visuals;

import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.Reflect;

/**
 * Ambience: fixed time of day and clear weather – client-side only (only you see it).
 *
 * In 1.21.11 the sky/light comes from the new "timeline" system, which reads the day time stored in
 * the client's level data. So we write our time directly into that level data every tick AND every frame
 * (so the server's time update never shows). LevelMixin additionally overrides getDayTime/getRainLevel.
 */
public final class AmbienceModule extends Module {
	public final Setting.Choice time = add(new Setting.Choice("time", "Time",
			"Sunset", "Sunrise", "Day", "Noon", "Sunset", "Night", "Midnight"));
	public final Setting.Bool clearWeather = add(new Setting.Bool("clear_weather", "Clear Weather", true));

	/** ClientLevel.ClientLevelData#setDayTime(long) – intermediary: class_638$class_5271.method_165 */
	private static final Method SET_DAY_TIME = Reflect.method("net.minecraft.class_638$class_5271", "method_165", "(J)V", long.class);

	public AmbienceModule() {
		super("ambience", "Ambience", "Sets your own time of day and weather", Category.VISUALS);
	}

	/** Minecraft day time in ticks for the selected option. */
	public long dayTime() {
		return switch (time.get()) {
			case "Sunrise" -> 23_200L;
			case "Day" -> 1_000L;
			case "Noon" -> 6_000L;
			case "Night" -> 13_800L;
			case "Midnight" -> 18_000L;
			default -> 12_300L; // Sunset
		};
	}

	@Override
	public void onTick(Minecraft mc) {
		apply(mc);
	}

	@Override
	public void onFrame(Minecraft mc) {
		apply(mc);
	}

	private void apply(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null) return;

		if (SET_DAY_TIME != null) {
			try {
				SET_DAY_TIME.invoke(level.getLevelData(), dayTime());
			} catch (Throwable ignored) {
			}
		}

		if (clearWeather.on()) {
			level.setRainLevel(0f);
			level.setThunderLevel(0f);
		}
	}
}
