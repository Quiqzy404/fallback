package dev.quiqzy.client.module.visuals;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Visual FX (nur bei dir sichtbar):
 *  - Target Ring: leuchtender, schwebender Ring um deinen Gegner
 *  - Target Outline: Gegner leuchtet mit Umriss in der Akzentfarbe (auch durch Wände)
 *  - Jump Circles: Ring am Boden, wenn du abspringst/landest
 *  - Kill Screen: Bild wird kurz grau + großer "ELIMINATED"-Schriftzug
 *  - Halo: leuchtender Ring über deinem Kopf (in F5 sichtbar)
 */
public final class VisualFxModule extends Module {
	public final Setting.Bool targetRing = add(new Setting.Bool("target_ring", "Target Ring", true));
	public final Setting.Bool targetOutline = add(new Setting.Bool("target_outline", "Target Outline", true));
	public final Setting.Bool jumpCircles = add(new Setting.Bool("jump_circles", "Jump Circles", true));
	public final Setting.Bool killScreen = add(new Setting.Bool("kill_screen", "Kill Screen", true));
	public final Setting.Bool halo = add(new Setting.Bool("halo", "Halo", true));
	public final Setting.Bool shockwave = add(new Setting.Bool("shockwave", "Shockwave", true));
	public final Setting.Bool hitSplash = add(new Setting.Bool("hit_splash", "Hit Splash", true));

	/** Boden-Effekte: Position, Startzeit, Art (0 = Shockwave, 1 = Hit Splash), Zufallswert. */
	public record Ground(net.minecraft.world.phys.Vec3 pos, long born, int kind, float seed) {
	}

	public final List<Ground> ground = new ArrayList<>();

	public Entity target;
	private long targetUntil;
	public long killAt;
	public String killName = "";

	public record Circle(Vec3 pos, long born) {
	}

	public final List<Circle> circles = new ArrayList<>();
	private boolean wasOnGround = true;

	public VisualFxModule() {
		super("visual_fx", "Visual FX", "Target ring, outline, jump circles, kill screen, halo", Category.VISUALS);
	}

	public void onAttack(Entity e) {
		if (!isEnabled() || !(e instanceof LivingEntity)) return;
		target = e;
		targetUntil = System.currentTimeMillis() + 6000;
		if (hitSplash.on()) {
			ground.add(new Ground(e.position(), System.currentTimeMillis(), 1, (float) Math.random()));
			if (ground.size() > 10) ground.remove(0);
		}
	}

	public boolean isTarget(Entity e) {
		return isEnabled() && e != null && e == target;
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null) return;
		long now = System.currentTimeMillis();
		if (target != null) {
			boolean dead = target instanceof LivingEntity le && le.isDeadOrDying();
			if (dead) {
				if (killScreen.on()) {
					killAt = now;
					killName = target.getName().getString();
				}
				target = null;
			} else if (target.isRemoved() || now > targetUntil || target.distanceTo(mc.player) > 30) {
				target = null;
			}
		}
		boolean ground = mc.player.onGround();
		if (shockwave.on() && ground && !wasOnGround && mc.player.fallDistance >= 0f) {
			this.ground.add(new Ground(mc.player.position(), now, 0, 0f));
			if (this.ground.size() > 10) this.ground.remove(0);
		}
		this.ground.removeIf(gr -> now - gr.born() > 1400);
		if (jumpCircles.on() && ground != wasOnGround) {
			circles.add(new Circle(mc.player.position(), now));
			if (circles.size() > 8) circles.remove(0);
		}
		wasOnGround = ground;
		circles.removeIf(c -> now - c.born() > 900);
	}
}
