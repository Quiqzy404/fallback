package dev.quiqzy.client.module.visuals;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Custom Crosshair im ZERO-Stil: Kreuz/Punkt/Kreis, Lücke, Größe, Farbe; weitet sich kurz beim Treffer, Angriffs-Cooldown als Bogen. */
public final class CrosshairModule extends Module {
	public final Setting.Choice style = add(new Setting.Choice("style", "Style", "Cross", "Cross", "Dot", "Circle", "Cross + Dot"));
	public final Setting.Int size = add(new Setting.Int("size", "Size", 6, 2, 16));
	public final Setting.Int gap = add(new Setting.Int("gap", "Gap", 3, 0, 10));
	public final Setting.Int thickness = add(new Setting.Int("thickness", "Thickness", 2, 1, 5));
	public final Setting.Color color = add(new Setting.Color("color", "Color", 0xFFB26E));
	public final Setting.Bool dynamic = add(new Setting.Bool("dynamic", "Expand on Hit", true));
	public final Setting.Bool cooldown = add(new Setting.Bool("cooldown", "Attack Cooldown", true));

	public CrosshairModule() {
		super("crosshair", "Crosshair", "Your own clean crosshair", Category.VISUALS);
	}
}
