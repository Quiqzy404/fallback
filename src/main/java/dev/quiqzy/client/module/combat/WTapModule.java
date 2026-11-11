package dev.quiqzy.client.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** W-Tap: setzt nach jedem Treffer kurz den Sprint zurück, damit der nächste Treffer wieder vollen Rückstoß gibt. */
public final class WTapModule extends Module {
	public final Setting.Int pause = add(new Setting.Int("pause", "Pause", 1, 1, 5, " t"));

	private int ticks = -1;

	public WTapModule() {
		super("w_tap", "W-Tap", "Resets sprint after each hit for more knockback", Category.COMBAT);
	}

	public void onAttack(LocalPlayer p) {
		if (isEnabled() && p.isSprinting()) ticks = pause.get();
	}

	@Override
	public void onStartTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || ticks < 0) return;
		if (ticks > 0) {
			p.setSprinting(false);
			ticks--;
		} else {
			if (mc.options.keyUp.isDown()) p.setSprinting(true);
			ticks = -1;
		}
	}
}
