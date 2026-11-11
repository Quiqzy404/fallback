package dev.quiqzy.client.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Auto Sprint: lässt dich automatisch sprinten, sobald du vorwärts gehst. */
public final class AutoSprintModule extends Module {
	public final Setting.Bool keepBackwards = add(new Setting.Bool("omni", "Sprint Any Direction", false));

	public AutoSprintModule() {
		super("auto_sprint", "Auto Sprint", "Sprints automatically while you move", Category.MOVEMENT);
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.screen != null) return;
		if (p.getFoodData().getFoodLevel() <= 6 || p.isUsingItem()) return;
		boolean moving = keepBackwards.on()
				? (mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown())
				: mc.options.keyUp.isDown();
		if (moving && !p.isSprinting()) p.setSprinting(true);
	}
}
