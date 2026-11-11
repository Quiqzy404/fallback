package dev.quiqzy.client.module.visuals;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Trajectories: zeigt die Flugbahn deines Wurfgegenstands/Pfeils und den Einschlagpunkt. */
public final class TrajectoriesModule extends Module {
	public final Setting.Color color = add(new Setting.Color("color", "Colour", 0x4DE8FF));
	public final Setting.Bool landing = add(new Setting.Bool("landing", "Landing Marker", true));

	public TrajectoriesModule() {
		super("trajectories", "Trajectories", "Shows the arc of what you're about to throw", Category.VISUALS);
	}

	/** {Startgeschwindigkeit, Schwerkraft} oder null, wenn das Item nicht geworfen wird. */
	public double[] physics(ItemStack s, boolean usingBow, float bowCharge) {
		if (s.is(Items.BOW) && usingBow) return new double[]{3.0 * bowCharge, 0.05};
		if (s.is(Items.CROSSBOW)) return new double[]{3.15, 0.05};
		if (s.is(Items.TRIDENT)) return new double[]{2.5, 0.05};
		if (s.is(Items.ENDER_PEARL) || s.is(Items.SNOWBALL) || s.is(Items.EGG)
				|| s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION) || s.is(Items.EXPERIENCE_BOTTLE)) {
			return new double[]{s.is(Items.EXPERIENCE_BOTTLE) ? 0.7 : 1.5, s.is(Items.EXPERIENCE_BOTTLE) ? 0.07 : 0.03};
		}
		return null;
	}
}
