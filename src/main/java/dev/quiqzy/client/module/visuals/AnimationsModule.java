package dev.quiqzy.client.module.visuals;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Animations (nur optisch, nur bei dir): Schwunggeschwindigkeit, tieferes Schild / tiefere Offhand für freie Sicht,
 * Item-Größe in der Ego-Sicht.
 */
public final class AnimationsModule extends Module {
	public final Setting.Int swingSpeed = add(new Setting.Int("swing_speed", "Swing Speed", 100, 40, 250, " %"));
	public final Setting.Int lowShield = add(new Setting.Int("low_shield", "Low Shield", 30, 0, 80, " %"));
	public final Setting.Int lowOffhand = add(new Setting.Int("low_offhand", "Low Offhand (Totem)", 20, 0, 80, " %"));
	public final Setting.Int itemSize = add(new Setting.Int("item_size", "Item Size", 100, 50, 150, " %"));

	public AnimationsModule() {
		super("animations", "Animations", "Swing speed, lower shield/offhand, item size", Category.VISUALS);
	}

	/** Verschiebt/skaliert das Item in der Ego-Sicht. */
	public void apply(PoseStack pose, ItemStack stack, boolean leftHand, boolean offhand) {
		float s = itemSize.get() / 100f;
		if (s != 1f) pose.scale(s, s, s);
		if (stack.is(Items.SHIELD)) {
			pose.translate(0f, -0.45f * lowShield.get() / 100f, 0f);
		} else if (offhand) {
			pose.translate(0f, -0.45f * lowOffhand.get() / 100f, 0f);
		}
	}
}
