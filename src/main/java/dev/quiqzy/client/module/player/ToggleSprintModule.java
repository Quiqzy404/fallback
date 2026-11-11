package dev.quiqzy.client.module.player;

import net.minecraft.client.Minecraft;

import dev.quiqzy.client.module.Module;

/** Toggle Sprint: du sprintest automatisch, sobald du vorwärts läufst (wie Sprint-Taste gedrückt halten). */
public final class ToggleSprintModule extends Module {
	public ToggleSprintModule() {
		super("toggle_sprint", "Toggle Sprint", "Always sprint while walking forward", Category.PLAYER);
	}

	@Override
	public void onStartTick(Minecraft mc) {
		if (mc.player == null) return;
		mc.options.keySprint.setDown(true);
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options != null) mc.options.keySprint.setDown(false);
	}
}
