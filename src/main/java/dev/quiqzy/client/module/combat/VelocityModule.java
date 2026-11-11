package dev.quiqzy.client.module.combat;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Velocity: verringert den Rückstoß, den du bekommst (Treffer und Explosionen). */
public final class VelocityModule extends Module {
	public final Setting.Int horizontal = add(new Setting.Int("horizontal", "Horizontal", 0, 0, 100, " %"));
	public final Setting.Int vertical = add(new Setting.Int("vertical", "Vertical", 100, 0, 100, " %"));
	public final Setting.Bool explosions = add(new Setting.Bool("explosions", "Explosions", true));

	public VelocityModule() {
		super("velocity", "Velocity", "Reduces the knockback you take", Category.COMBAT);
	}
}
