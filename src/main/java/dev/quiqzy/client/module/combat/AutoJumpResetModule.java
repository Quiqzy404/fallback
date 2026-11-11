package dev.quiqzy.client.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.InputUtil;

/** Auto Jump Reset: springt genau wenn du getroffen wirst (und optional wenn du triffst) – nimmt weniger Rückstoß. */
public final class AutoJumpResetModule extends Module {
	public final Setting.Bool onHurt = add(new Setting.Bool("on_hurt", "When Hit", true));
	public final Setting.Bool onAttack = add(new Setting.Bool("on_attack", "When You Hit", false));
	public final Setting.Bool onlyWhileFighting = add(new Setting.Bool("fighting", "Only While Holding Attack", true));

	private int lastHurt;

	public AutoJumpResetModule() {
		super("auto_jump_reset", "Auto Jump Reset", "Jumps the moment you get hit to reduce knockback", Category.COMBAT);
	}

	public void onYouAttack() {
		Minecraft mc = Minecraft.getInstance();
		if (onAttack.on() && mc.player != null && mc.player.onGround()) mc.player.jumpFromGround();
	}

	@Override
	public void onStartTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.screen != null) return;
		if (onlyWhileFighting.on() && !InputUtil.isPhysicallyDown(mc, mc.options.keyAttack)) {
			lastHurt = p.hurtTime;
			return;
		}
		if (onHurt.on() && p.hurtTime > lastHurt && p.onGround() && !p.isInWater() && !p.isInLava()) {
			p.jumpFromGround();
		}
		lastHurt = p.hurtTime;
	}
}
