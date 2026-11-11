package dev.quiqzy.client.module.combat;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.InputUtil;
import dev.quiqzy.client.util.Targets;

/** Aim Assist: zieht deinen Blick sanft zum nächsten Ziel (jedes Bild, damit es flüssig ist). */
public final class AimAssistModule extends Module {
	public final Setting.Int speed = add(new Setting.Int("speed", "Speed", 40, 5, 200, "\u00b0/s"));
	public final Setting.Int range = add(new Setting.Int("range", "Range", 5, 2, 8, " b"));
	public final Setting.Int fov = add(new Setting.Int("fov", "FOV", 90, 20, 360, "\u00b0"));
	public final Setting.Bool vertical = add(new Setting.Bool("vertical", "Vertical Too", false));
	public final Setting.Bool holdAttack = add(new Setting.Bool("hold", "Only While Clicking", true));
	public final Setting.Bool players = add(new Setting.Bool("players", "Players", true));
	public final Setting.Bool mobs = add(new Setting.Bool("mobs", "Mobs", false));

	private long last;

	public AimAssistModule() {
		super("aim_assist", "Aim Assist", "Smoothly pulls your aim onto the closest target", Category.COMBAT);
	}

	@Override
	public void onFrame(Minecraft mc) {
		long now = System.nanoTime();
		float dt = last == 0 ? 0f : Math.min(0.05f, (now - last) / 1e9f);
		last = now;
		LocalPlayer p = mc.player;
		if (p == null || mc.screen != null || dt <= 0f) return;
		if (holdAttack.on() && !InputUtil.isPhysicallyDown(mc, mc.options.keyAttack)) return;
		List<LivingEntity> list = Targets.find(mc, range.get(), fov.get(), players.on(), mobs.on(), false, false);
		if (list.isEmpty()) return;
		LivingEntity t = list.get(0);
		float[] a = Targets.angles(p, Targets.aimPoint(p, t));
		Targets.turn(p, a[0], a[1], speed.get() * dt, vertical.on());
	}
}
