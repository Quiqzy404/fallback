package dev.quiqzy.client.module.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Auto Respawn: respawnt sofort nach dem Tod. */
public final class AutoRespawnModule extends Module {
	public final Setting.Int delay = add(new Setting.Int("delay", "Delay", 2, 0, 40, " t"));

	private int t;

	public AutoRespawnModule() {
		super("auto_respawn", "Auto Respawn", "Respawns instantly when you die", Category.PLAYER);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player != null && mc.screen instanceof DeathScreen) {
			if (t++ >= delay.get()) {
				mc.player.respawn();
				mc.setScreen(null);
				t = 0;
			}
		} else {
			t = 0;
		}
	}
}
