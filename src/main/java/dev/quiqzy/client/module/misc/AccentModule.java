package dev.quiqzy.client.module.misc;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Accent Colour: färbt den ganzen Client (GUI, HUD, Effekte) in einer anderen Akzentfarbe.
 * Unterstützt CatLean Preset & HUD-Style.
 */
public final class AccentModule extends Module {
	public final Setting.Choice preset = add(new Setting.Choice("preset", "Accent", "CatLean",
			"CatLean", "Ember", "Rose", "Crimson", "Violet", "Ocean", "Aqua", "Mint", "Gold", "Custom"));
	public final Setting.Choice hudStyle = add(new Setting.Choice("hud_style", "HUD Style", "CatLean",
			"CatLean", "ZERO Glass", "Classic"));
	public final Setting.Color custom = add(new Setting.Color("custom", "Custom Colour", 0x7B6BFF));
	/** HUD lebendig: wandernder Rand-Schimmer, gleitendes Glanzlicht, Farbübergänge. */
	public final Setting.Bool animated = add(new Setting.Bool("animated", "Animated HUD", true));
	public final Setting.Int animSpeed = add(new Setting.Int("anim_speed", "Animation Speed", 60, 20, 300, " %"));
	/** Größe des ganzen HUD. */
	public final Setting.Int hudScale = add(new Setting.Int("hud_scale", "HUD Size", 80, 50, 150, " %"));
	/** Verschiebungen der HUD-Elemente (HUD-Editor), gepackt als 12+12 Bit (±2048). Unsichtbar im GUI. */
	public final Setting.ColorMap hudOffsets = add(new Setting.ColorMap("hud_offsets", "HUD Offsets"));

	public boolean isCatLean() {
		return isEnabled() && ("CatLean".equals(preset.get()) || "CatLean".equals(hudStyle.get()));
	}

	public float[] offset(String id) {
		Integer v = hudOffsets.color(id);
		if (v == null) return new float[]{0f, 0f};
		return new float[]{((v >> 12) & 0xFFF) - 2048, (v & 0xFFF) - 2048};
	}

	public void setOffset(String id, float dx, float dy) {
		int x = Math.max(0, Math.min(4095, Math.round(dx) + 2048));
		int y = Math.max(0, Math.min(4095, Math.round(dy) + 2048));
		if (x == 2048 && y == 2048) hudOffsets.remove(id);
		else hudOffsets.put(id, (x << 12) | y);
	}

	public AccentModule() {
		super("accent", "Accent Colour", "Recolour the whole client with one click", Category.MISC);
		setEnabled(true);
	}

	/** Ziel-Farbton 0..1, oder -1 = Original-Orange lassen. */
	public float hue() {
		if (!isEnabled()) return -1f;
		return switch (preset.get()) {
			case "CatLean" -> 0.765f; // Electric Lean Violet / Purple (~275°)
			case "Rose" -> 0.94f;
			case "Crimson" -> 0.99f;
			case "Violet" -> 0.74f;
			case "Ocean" -> 0.60f;
			case "Aqua" -> 0.50f;
			case "Mint" -> 0.40f;
			case "Gold" -> 0.13f;
			case "Custom" -> rgbHue(custom.get());
			default -> -1f;
		};
	}

	private static float rgbHue(int rgb) {
		float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
		if (d <= 0f) return 0.07f;
		float h = max == r ? ((g - b) / d) % 6f : max == g ? (b - r) / d + 2f : (r - g) / d + 4f;
		h /= 6f;
		return h < 0f ? h + 1f : h;
	}
}
