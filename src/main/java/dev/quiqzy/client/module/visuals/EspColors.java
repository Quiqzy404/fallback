package dev.quiqzy.client.module.visuals;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

/** Gemeinsame Logik für Entity ESP und Tracers: zu welcher Gruppe gehört ein Entity, Rainbow-Farben. */
public final class EspColors {
	public static final int NONE = -1, PLAYER = 0, HOSTILE = 1, ANIMAL = 2, ITEM = 3;

	private EspColors() {
	}

	/** Gruppe des Entitys oder NONE, wenn es nicht angezeigt werden soll. */
	public static int group(Minecraft mc, Entity e, boolean players, boolean hostiles, boolean animals, boolean items, int range) {
		if (e == mc.player || e.isRemoved()) return NONE;
		if (mc.player.distanceToSqr(e) > (double) range * range) return NONE;
		if (e instanceof Player p) return players && !p.isSpectator() ? PLAYER : NONE;
		if (e instanceof ItemEntity) return items ? ITEM : NONE;
		if (e instanceof LivingEntity living) {
			if (!living.isAlive()) return NONE;
			if (e instanceof Enemy) return hostiles ? HOSTILE : NONE;
			return animals ? ANIMAL : NONE;
		}
		return NONE;
	}

	/** Regenbogenfarbe, läuft mit der Zeit durch; offset verschiebt sie pro Entity. */
	public static int rainbow(int speed, float offset) {
		float period = 6000f / Math.max(1, speed);
		float hue = ((System.currentTimeMillis() % (long) period) / period + offset) % 1f;
		return Mth.hsvToRgb(hue, 0.75f, 1f) & 0xFFFFFF;
	}

	/** Farbe nach Entfernung: nah = rot, mittel = gelb, weit = grün. */
	public static int byDistance(double dist, double max) {
		float t = (float) Math.min(1.0, dist / Math.max(1.0, max));
		return Mth.hsvToRgb(t * 0.33f, 0.85f, 1f) & 0xFFFFFF;
	}
}
