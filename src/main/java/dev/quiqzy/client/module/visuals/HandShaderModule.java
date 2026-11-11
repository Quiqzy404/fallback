package dev.quiqzy.client.module.visuals;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Hand Shader: changes how your first-person hands look.
 * - Glint: enchantment shimmer on every held item (works with glint texture packs)
 * - Size / X / Y / Z: scale and move your hands
 * - Swing: replaces the vanilla swing animation with one of six custom styles (First..Sixth),
 *   each with intensity and swing speed (ported from the Gooba reference).
 * The actual work happens in ItemInHandRendererMixin.
 */
public final class HandShaderModule extends Module {
	public final Setting.Bool glint = add(new Setting.Bool("glint", "Glint", true));
	public final Setting.Bool glow = add(new Setting.Bool("glow", "Item Glow", true));
	public final Setting.Color glowColor = add(new Setting.Color("glow_color", "Glow Color", 0xA78BFA));
	public final Setting.Int glowOpacity = add(new Setting.Int("glow_opacity", "Glow Opacity", 80, 20, 100, "%"));
	public final Setting.Int glowWidth = add(new Setting.Int("glow_width", "Glow Size", 10, 2, 50, "%"));
	public final Setting.Int size = add(new Setting.Int("size", "Size", 100, 50, 150, "%"));
	public final Setting.Int offsetX = add(new Setting.Int("offset_x", "X Offset", 0, -50, 50, ""));
	public final Setting.Int offsetY = add(new Setting.Int("offset_y", "Y Offset", 0, -50, 50, ""));
	public final Setting.Int offsetZ = add(new Setting.Int("offset_z", "Z Offset", 0, -50, 50, ""));
	public final Setting.Choice swing = add(new Setting.Choice("swing", "Swing", "Off",
			"Off", "First", "Second", "Third", "Fourth", "Fifth", "Sixth"));
	public final Setting.Int intensity = add(new Setting.Int("intensity", "Intensity", 100, 10, 300, "%"));
	public final Setting.Int speed = add(new Setting.Int("speed", "Swing Speed", 100, 10, 400, "%"));

	public HandShaderModule() {
		super("hand_shader", "Hand Shader", "Glint, size, position and swing animation of your hands", Category.VISUALS);
	}

	/** True if the hand position/size differs from vanilla. */
	public boolean changesPose() {
		return size.get() != 100 || offsetX.get() != 0 || offsetY.get() != 0 || offsetZ.get() != 0;
	}

	/** True if a custom swing animation is active (module on + style not Off). */
	public boolean swingAnim() {
		return isEnabled() && !"Off".equals(swing.get());
	}

	/** Intensity like the original module: 100% = 6.0 */
	public float intensityValue() {
		return intensity.get() / 100f * 6f;
	}
}
