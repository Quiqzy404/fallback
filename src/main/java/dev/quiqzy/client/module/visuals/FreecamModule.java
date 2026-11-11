package dev.quiqzy.client.module.visuals;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.InputUtil;

/**
 * Freecam ("Gegenteil" der üblichen Freecam): NUR die Kamera fliegt frei.
 * Dein Spieler bleibt stehen, und alles, was du tust (Schlagen, Abbauen, Benutzen),
 * passiert weiter von deinem Spieler aus – mit seiner Blickrichtung, nicht von der Kamera.
 * WASD = fliegen, Leertaste = hoch, Shift = runter, Mausrad = Geschwindigkeit.
 */
public final class FreecamModule extends Module {
	public final Setting.Int speed = add(new Setting.Int("speed", "Speed", 10, 1, 60, " b/s"));
	public final Setting.Bool smooth = add(new Setting.Bool("smooth", "Smooth Movement", true));
	public final Setting.Bool speedHud = add(new Setting.Bool("speed_hud", "Speed Display", true));
	public final Setting.Bool hands = add(new Setting.Bool("hands", "Show Hands", true));
	public final Setting.Bool keepMovement = add(new Setting.Bool("keep_movement", "Keep Movement", true));
	public long enabledAt;
	private boolean keepForward, keepBack, keepLeft, keepRight, keepJump, keepShift, keepSprint;
	/** 100 % = genau deine Minecraft-Maus-Sensi. */
	public final Setting.Int sensitivity = add(new Setting.Int("sensitivity", "Look Sensitivity", 100, 25, 300, " %"));
	private boolean savedCull = true;

	public double x, y, z;
	public float yaw, pitch;
	private double vx, vy, vz;
	private long lastNanos;
	/** Zeitpunkt der letzten Mausrad-Änderung (für die Anzeige). */
	public long speedChanged;

	public FreecamModule() {
		super("freecam", "Freecam", "Fly the camera freely while your player stays and acts", Category.VISUALS);
	}

	public boolean active() {
		Minecraft mc = Minecraft.getInstance();
		return isEnabled() && mc.player != null && mc.level != null;
	}

	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		// genau in deinem Kopf starten – dein Körper wird erst sichtbar, wenn die Kamera rausfliegt
		Vec3 eye = mc.player.getEyePosition();
		yaw = mc.player.getYRot();
		pitch = mc.player.getXRot();
		x = eye.x;
		y = eye.y;
		z = eye.z;
		enabledAt = System.currentTimeMillis();
		// Höhlen-Culling aus, sonst verschwinden Chunks, wenn die Kamera in/hinter Blöcken ist (wie im Zuschauermodus)
		savedCull = mc.smartCull;
		mc.smartCull = false;
		if (mc.levelRenderer != null) mc.levelRenderer.needsUpdate();
		vx = vy = vz = 0;
		lastNanos = 0;
		if (keepMovement.on()) {
			var o = mc.options;
			keepForward = o.keyUp.isDown() || InputUtil.isPhysicallyDown(mc, o.keyUp);
			keepBack = o.keyDown.isDown() || InputUtil.isPhysicallyDown(mc, o.keyDown);
			keepLeft = o.keyLeft.isDown() || InputUtil.isPhysicallyDown(mc, o.keyLeft);
			keepRight = o.keyRight.isDown() || InputUtil.isPhysicallyDown(mc, o.keyRight);
			keepJump = o.keyJump.isDown() || InputUtil.isPhysicallyDown(mc, o.keyJump);
			keepShift = o.keyShift.isDown() || InputUtil.isPhysicallyDown(mc, o.keyShift);
			keepSprint = o.keySprint.isDown() || InputUtil.isPhysicallyDown(mc, o.keySprint) || (mc.player != null && mc.player.isSprinting());
		} else {
			keepForward = keepBack = keepLeft = keepRight = keepJump = keepShift = keepSprint = false;
			releaseKeys(mc);
		}
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		keepForward = keepBack = keepLeft = keepRight = keepJump = keepShift = keepSprint = false;
		if (mc.options != null) releaseKeys(mc);
		mc.smartCull = savedCull;
		if (mc.levelRenderer != null) mc.levelRenderer.needsUpdate();
	}

	private static void releaseKeys(Minecraft mc) {
		var o = mc.options;
		o.keyUp.setDown(false);
		o.keyDown.setDown(false);
		o.keyLeft.setDown(false);
		o.keyRight.setDown(false);
		o.keyJump.setDown(false);
		o.keyShift.setDown(false);
	}

	/** Ist die Kamera schon weit genug weg, um den eigenen Körper zu zeigen? */
	public boolean outsideBody() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return false;
		Vec3 eye = mc.player.getEyePosition();
		double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
		return dx * dx + dy * dy + dz * dz > 1.2 * 1.2;
	}

	/** Mausbewegung dreht nur die Kamera (vom Entity.turn-Mixin). */
	public void turn(double dx, double dy) {
		// gleiche Formel wie Minecraft (Entity.turn): Maus-Delta (schon mit deiner Sensi verrechnet) x 0,15
		float k = 0.15f * sensitivity.get() / 100f;
		yaw += (float) dx * k;
		pitch = Math.max(-90f, Math.min(90f, pitch + (float) dy * k));
	}

	/** Mausrad: Geschwindigkeit ändern. */
	public void scroll(double amount) {
		int s = speed.get();
		int step = s < 10 ? 1 : s < 30 ? 2 : 5;
		speed.set(s + (amount > 0 ? step : -step));
		speedChanged = System.currentTimeMillis();
	}

	@Override
	public void onStartTick(Minecraft mc) {
		if (mc.player == null) {
			setEnabled(false);
			return;
		}
		if (mc.screen == null) {
			if (keepMovement.on()) {
				var o = mc.options;
				o.keyUp.setDown(keepForward);
				o.keyDown.setDown(keepBack);
				o.keyLeft.setDown(keepLeft);
				o.keyRight.setDown(keepRight);
				o.keyJump.setDown(keepJump);
				o.keyShift.setDown(keepShift);
				if (keepSprint) {
					o.keySprint.setDown(true);
					mc.player.setSprinting(true);
				}
			} else {
				releaseKeys(mc);
			}
		}
	}

	/** Jedes Bild: Kamera bewegen (flüssig, unabhängig von den Ticks). */
	@Override
	public void onFrame(Minecraft mc) {
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1.0e9f);
		lastNanos = now;
		if (mc.player == null || dt <= 0f) return;
		if (mc.smartCull) {
			mc.smartCull = false;
			mc.levelRenderer.needsUpdate();
		}
		double fwd = 0, side = 0, up = 0;
		if (mc.screen == null) {
			var o = mc.options;
			if (InputUtil.isPhysicallyDown(mc, o.keyUp)) fwd += 1;
			if (InputUtil.isPhysicallyDown(mc, o.keyDown)) fwd -= 1;
			if (InputUtil.isPhysicallyDown(mc, o.keyLeft)) side += 1;
			if (InputUtil.isPhysicallyDown(mc, o.keyRight)) side -= 1;
			if (InputUtil.isPhysicallyDown(mc, o.keyJump)) up += 1;
			if (InputUtil.isPhysicallyDown(mc, o.keyShift)) up -= 1;
		}
		double len = Math.sqrt(fwd * fwd + side * side);
		if (len > 1) {
			fwd /= len;
			side /= len;
		}
		double r = Math.toRadians(yaw);
		double sin = Math.sin(r), cos = Math.cos(r);
		double sp = speed.get();
		double tx = (-sin * fwd + cos * side) * sp;
		double tz = (cos * fwd + sin * side) * sp;
		double ty = up * sp;
		if (smooth.on()) {
			double k = 1 - Math.exp(-dt * 10.0);
			vx += (tx - vx) * k;
			vy += (ty - vy) * k;
			vz += (tz - vz) * k;
		} else {
			vx = tx;
			vy = ty;
			vz = tz;
		}
		x += vx * dt;
		y += vy * dt;
		z += vz * dt;
	}
}
