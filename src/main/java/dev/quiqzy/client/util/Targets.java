package dev.quiqzy.client.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Gemeinsame Ziel-Suche und Blick-Drehung für die Combat-Module. */
public final class Targets {
	private Targets() {
	}

	public static boolean valid(LocalPlayer p, Entity e, boolean players, boolean mobs, boolean animals) {
		if (!(e instanceof LivingEntity le) || e == p || !le.isAlive() || le instanceof ArmorStand) return false;
		if (e instanceof Player pl) return players && !pl.isSpectator() && !pl.isCreative();
		if (e instanceof Enemy) return mobs;
		if (e instanceof Animal) return animals;
		return mobs;
	}

	/** Gültige Ziele in Reichweite und Sichtfeld, nach Abstand sortiert. */
	public static List<LivingEntity> find(Minecraft mc, double range, int fov, boolean players, boolean mobs, boolean animals, boolean walls) {
		LocalPlayer p = mc.player;
		List<LivingEntity> out = new ArrayList<>();
		if (p == null || mc.level == null) return out;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!valid(p, e, players, mobs, animals)) continue;
			if (distTo(p, e) > range) continue;
			if (fov < 360 && Math.abs(yawDiff(p, e)) > fov / 2f) continue;
			if (!walls && !p.hasLineOfSight(e)) continue;
			out.add((LivingEntity) e);
		}
		out.sort(Comparator.comparingDouble(e -> distTo(p, e)));
		return out;
	}

	/** Abstand vom Auge zum nächsten Punkt der Hitbox (so misst auch der Server die Reichweite). */
	public static double distTo(LocalPlayer p, Entity e) {
		Vec3 eye = p.getEyePosition();
		var box = e.getBoundingBox();
		double x = Mth.clamp(eye.x, box.minX, box.maxX), y = Mth.clamp(eye.y, box.minY, box.maxY), z = Mth.clamp(eye.z, box.minZ, box.maxZ);
		return eye.distanceTo(new Vec3(x, y, z));
	}

	/** {yaw, pitch} vom Auge zum Punkt. */
	public static float[] angles(LocalPlayer p, Vec3 to) {
		Vec3 eye = p.getEyePosition();
		double dx = to.x - eye.x, dy = to.y - eye.y, dz = to.z - eye.z;
		float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		return new float[]{yaw, Mth.clamp(pitch, -90f, 90f)};
	}

	/** Zielpunkt: Brusthöhe, aber auf die Hitbox begrenzt. */
	public static Vec3 aimPoint(LocalPlayer p, Entity e) {
		var box = e.getBoundingBox();
		double y = Mth.clamp(p.getEyeY(), box.minY + 0.2, box.maxY - 0.2);
		return new Vec3((box.minX + box.maxX) / 2, y, (box.minZ + box.maxZ) / 2);
	}

	public static float yawDiff(LocalPlayer p, Entity e) {
		return Mth.wrapDegrees(angles(p, aimPoint(p, e))[0] - p.getYRot());
	}

	/** Blick drehen; maxStep in Grad (<= 0 = sofort). */
	public static void turn(LocalPlayer p, float yaw, float pitch, float maxStep, boolean pitchToo) {
		float dy = Mth.wrapDegrees(yaw - p.getYRot());
		float dp = pitch - p.getXRot();
		if (maxStep > 0) {
			dy = Mth.clamp(dy, -maxStep, maxStep);
			dp = Mth.clamp(dp, -maxStep, maxStep);
		}
		p.setYRot(p.getYRot() + dy);
		if (pitchToo) p.setXRot(Mth.clamp(p.getXRot() + dp, -90f, 90f));
	}

	public static boolean cooled(LocalPlayer p, int percent) {
		return p.getAttackStrengthScale(0.5f) * 100f >= percent;
	}

	public static void hit(Minecraft mc, Entity e) {
		mc.gameMode.attack(mc.player, e);
		mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
	}

	/** Hotbar-Platz mit passendem Item (oder -1). */
	public static int hotbar(LocalPlayer p, java.util.function.Predicate<net.minecraft.world.item.ItemStack> test) {
		for (int i = 0; i < 9; i++) if (test.test(p.getInventory().getItem(i))) return i;
		return -1;
	}
}
