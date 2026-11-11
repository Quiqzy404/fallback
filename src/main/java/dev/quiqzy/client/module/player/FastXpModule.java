package dev.quiqzy.client.module.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.InputUtil;

/**
 * Fast XP: hold right-click with XP bottles in your hand -> throws them every tick
 * (vanilla only every 4 ticks).
 * Look Down: while throwing, the camera turns smoothly but quickly to the ground.
 *
 * Throwing runs at the START of the tick (before Minecraft's own right-click handling),
 * the camera turn runs every frame so it is smooth.
 */
public final class FastXpModule extends Module {
	public final Setting.Int bottles = add(new Setting.Int("bottles", "Bottles per Tick", 1, 1, 5, ""));
	public final Setting.Bool lookDown = add(new Setting.Bool("look_down", "Look Down", true));
	public final Setting.Int lookSpeed = add(new Setting.Int("look_speed", "Look Speed", 12, 1, 30, ""));

	/** True while bottles are being thrown (used by the per-frame camera turn). */
	private boolean throwing;
	private long lastFrame;

	public FastXpModule() {
		super("fast_xp", "Fast XP", "Throws XP bottles really fast while holding right-click", Category.PLAYER);
	}

	@Override
	protected void onDisable() {
		throwing = false;
	}

	@Override
	public void onStartTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		throwing = false;
		if (player == null || mc.gameMode == null || mc.screen != null) return;

		InteractionHand hand = xpHand(player);
		if (hand == null) return;
		if (!InputUtil.isPhysicallyDown(mc, mc.options.keyUse)) return;

		// Minecraft's own (slow) right-click repeat off, we throw ourselves
		while (mc.options.keyUse.consumeClick()) {
		}
		mc.options.keyUse.setDown(false);

		throwing = true;
		for (int i = 0; i < bottles.get(); i++) {
			if (!player.getItemInHand(hand).is(Items.EXPERIENCE_BOTTLE)) break; // ran out
			mc.gameMode.useItem(player, hand);
		}
		player.swing(hand);
	}

	@Override
	public void onFrame(Minecraft mc) {
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;

		LocalPlayer player = mc.player;
		if (!throwing || !lookDown.on() || player == null || mc.screen != null) return;

		float pitch = player.getXRot();
		if (pitch >= 89.9f) return;

		// smooth but fast: approach 90° (straight down), framerate independent
		float k = 1f - (float) Math.exp(-lookSpeed.get() * dt);
		float step = (90f - pitch) * k;
		if (90f - pitch < 0.5f) step = 90f - pitch;

		// turn() is what mouse movement uses -> no stutter (it multiplies by 0.15)
		player.turn(0.0, step / 0.15);
	}

	/** Hand holding XP bottles (main hand first), or null. */
	private static InteractionHand xpHand(LocalPlayer player) {
		if (player.getMainHandItem().is(Items.EXPERIENCE_BOTTLE)) return InteractionHand.MAIN_HAND;
		if (player.getOffhandItem().is(Items.EXPERIENCE_BOTTLE)) return InteractionHand.OFF_HAND;
		return null;
	}
}
