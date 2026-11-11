package dev.quiqzy.client.hud;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.gui.engine.ZeroRender;
import dev.quiqzy.client.module.visuals.HitEffectsModule;

/**
 * Treffer-Effekte: Positionen leben in der Welt, gezeichnet wird mit der ZERO-Engine
 * (auf den Bildschirm projiziert) – dadurch weiche Glows, Ringe und Verläufe im Client-Stil.
 */
public final class HitFx {
	private static final Random RNG = new Random();
	private static final List<Fx> FX = new ArrayList<>();
	/** Bildschirm-Leuchten 0..1 (klingt ab). */
	private static float screenGlow;
	/** Kurzer heller Crit-Blitz über den ganzen Bildschirm. */
	private static float critFlash;
	private static int glowColor = 0xFFF0782C;

	private HitFx() {
	}

	private enum Kind { RIPPLE, SPARK, STAR, BEAM, EMBER, SHARD, GLOW, STREAK, POP, PETAL, DUST }

	private static final class Fx {
		final Kind kind;
		final long born;
		final float life;
		double x, y, z, vx, vy, vz;
		final float size;
		final float spin;
		final int c1, c2;
		/** Verzögerung in Sekunden, bevor der Effekt startet. */
		float delay;

		Fx(Kind kind, Vec3 p, float life, float size, int c1, int c2) {
			this.kind = kind;
			this.born = System.nanoTime();
			this.life = life;
			this.x = p.x;
			this.y = p.y;
			this.z = p.z;
			this.size = size;
			this.spin = (RNG.nextFloat() - 0.5f) * 8f;
			this.c1 = c1;
			this.c2 = c2;
		}

		float age() {
			return (System.nanoTime() - born) / 1.0e9f - delay;
		}
	}

	/** Schild mit der Axt gebrochen: Splitter + heller Ring. */
	public static void shieldBreak(Vec3 p, HitEffectsModule m) {
		int[] pal = m.palette();
		synchronized (FX) {
			FX.add(new Fx(Kind.RIPPLE, p, 0.4f, 0.8f, 0xFFFFFFFF, pal[1]));
			for (int i = 0; i < 16; i++) {
				Fx f = new Fx(Kind.SHARD, p, 0.6f + RNG.nextFloat() * 0.4f, 0.8f + RNG.nextFloat() * 0.8f,
						RNG.nextFloat() < 0.5f ? 0xFFE6E9EF : 0xFF9AA3B2, pal[1]);
				double yaw = RNG.nextDouble() * Math.PI * 2;
				double sp = 3.0 + RNG.nextDouble() * 3.5;
				f.vx = Math.cos(yaw) * sp;
				f.vz = Math.sin(yaw) * sp;
				f.vy = 1.5 + RNG.nextDouble() * 3.0;
				FX.add(f);
			}
		}
		screenGlow = Math.min(1f, screenGlow + 0.5f);
		glowColor = pal[1];
	}

	/** Afterglow: der Getötete löst sich in aufsteigendes Licht auf. */
	public static void afterglow(net.minecraft.world.phys.AABB box, HitEffectsModule m) {
		int[] pal = m.palette();
		synchronized (FX) {
			int n = Math.round(46 * m.strength());
			for (int i = 0; i < n; i++) {
				Vec3 p = new Vec3(
						box.minX + RNG.nextDouble() * (box.maxX - box.minX),
						box.minY + RNG.nextDouble() * (box.maxY - box.minY),
						box.minZ + RNG.nextDouble() * (box.maxZ - box.minZ));
				Fx f = new Fx(Kind.GLOW, p, 1.2f + RNG.nextFloat() * 1.0f, 0.7f + RNG.nextFloat() * 0.8f,
						RNG.nextFloat() < 0.4f ? 0xFFFFF4E6 : pal[0], pal[1]);
				f.vx = (RNG.nextDouble() - 0.5) * 0.5;
				f.vz = (RNG.nextDouble() - 0.5) * 0.5;
				f.vy = 0.8 + RNG.nextDouble() * 1.4;
				FX.add(f);
			}
		}
	}

	/** Embers-Modul: hält die Anzahl schwebender Funken um den Spieler. */
	public static void tickEmbers(Vec3 center, int target, int range, int[] pal) {
		synchronized (FX) {
			int n = 0;
			for (Fx f : FX) {
				if (f.kind == Kind.EMBER) {
					double dx = f.x - center.x, dz = f.z - center.z;
					if (dx * dx + dz * dz > range * range * 2.2) f.vy = -99; // zu weit weg -> ausblenden
					else n++;
				}
			}
			int spawn = Math.min(4, target - n);
			for (int i = 0; i < spawn; i++) {
				double a = RNG.nextDouble() * Math.PI * 2;
				double r = 1.5 + RNG.nextDouble() * range;
				Vec3 p = center.add(Math.cos(a) * r, -1.0 + RNG.nextDouble() * 6.0, Math.sin(a) * r);
				Fx f = new Fx(Kind.EMBER, p, 4.5f + RNG.nextFloat() * 4f, 0.6f + RNG.nextFloat() * 0.9f,
						RNG.nextFloat() < 0.35f ? 0xFFFFF4E6 : pal[0], pal[1]);
				f.vx = (RNG.nextDouble() - 0.5) * 0.35;
				f.vz = (RNG.nextDouble() - 0.5) * 0.35;
				f.vy = 0.05 + RNG.nextDouble() * 0.25;
				FX.add(f);
			}
		}
	}

	public static void clearEmbers() {
		synchronized (FX) {
			FX.removeIf(f -> f.kind == Kind.EMBER || f.kind == Kind.PETAL || f.kind == Kind.DUST);
		}
	}

	private static final int[] PETAL_COLORS = {0xFFFFB8D0, 0xFFFFC9DA, 0xFFFFD6B0, 0xFFF7A8C4, 0xFFFFE2EC};

	/** Ambience: Blütenblätter fallen langsam und drehen sich. */
	public static void tickPetals(Vec3 center, int target, int range) {
		synchronized (FX) {
			int n = 0;
			for (Fx f : FX) if (f.kind == Kind.PETAL) n++;
			int spawn = Math.min(3, target - n);
			for (int i = 0; i < spawn; i++) {
				double a = RNG.nextDouble() * Math.PI * 2;
				double r = 1.0 + RNG.nextDouble() * range;
				Vec3 p = center.add(Math.cos(a) * r, 3.0 + RNG.nextDouble() * 5.0, Math.sin(a) * r);
				Fx f = new Fx(Kind.PETAL, p, 6f + RNG.nextFloat() * 4f, 0.7f + RNG.nextFloat() * 0.6f,
						PETAL_COLORS[RNG.nextInt(PETAL_COLORS.length)], 0xFFFFFFFF);
				f.vx = 0.25 + RNG.nextDouble() * 0.35; // leichter Wind
				f.vz = (RNG.nextDouble() - 0.5) * 0.3;
				f.vy = -(0.45 + RNG.nextDouble() * 0.35);
				FX.add(f);
			}
		}
	}

	/** Stardust Trail: kleine Funken bleiben hinter dir zurück. */
	public static void trail(Vec3 feet, int[] pal) {
		synchronized (FX) {
			for (int i = 0; i < 2; i++) {
				Vec3 p = feet.add((RNG.nextDouble() - 0.5) * 0.5, 0.05 + RNG.nextDouble() * 0.2, (RNG.nextDouble() - 0.5) * 0.5);
				Fx f = new Fx(Kind.DUST, p, 0.9f + RNG.nextFloat() * 0.7f, 0.5f + RNG.nextFloat() * 0.5f,
						RNG.nextFloat() < 0.4f ? 0xFFFFF6EC : pal[0], pal[1]);
				f.vy = 0.15 + RNG.nextDouble() * 0.3;
				FX.add(f);
			}
		}
	}

	public static void clear() {
		synchronized (FX) {
			FX.clear();
		}
		screenGlow = 0f;
	}

	private static long lastHitNanos;

	/** Sekunden seit dem letzten eigenen Treffer (für das Fadenkreuz). */
	public static float sinceHit() {
		return lastHitNanos == 0 ? 99f : (System.nanoTime() - lastHitNanos) / 1.0e9f;
	}

	/** Treffer registrieren, auch wenn Hit Effects aus ist. */
	public static void markHit() {
		lastHitNanos = System.nanoTime();
	}

	/** Neuer Treffer: heller Aufprall-Punkt + ein paar Funken; Crit zusätzlich ein paar Sterne. */
	public static void hit(Vec3 p, boolean crit, HitEffectsModule m) {
		int[] pal = m.palette();
		float s = m.strength();
		synchronized (FX) {
			if (m.ripple.on()) {
				// weicher Leuchtpunkt am Treffpunkt (keine harten Ringe)
				FX.add(new Fx(Kind.RIPPLE, p, crit ? 0.42f : 0.26f, crit ? 0.75f : 0.45f, pal[0], pal[1]));
			}
			if (m.splash.on()) {
				int n = Math.round((crit ? 10 : 4) * s);
				for (int i = 0; i < n; i++) {
					Fx f = new Fx(Kind.SPARK, p, 0.3f + RNG.nextFloat() * 0.3f, 0.5f + RNG.nextFloat() * 0.5f,
							RNG.nextBoolean() ? pal[0] : 0xFFFFF4E6, pal[1]);
					double sp = crit ? 3.0 + RNG.nextDouble() * 3.0 : 1.5 + RNG.nextDouble() * 1.8;
					double yaw = RNG.nextDouble() * Math.PI * 2;
					double up = RNG.nextDouble() * 0.9 + 0.1;
					f.vx = Math.cos(yaw) * sp * (1 - up * 0.5);
					f.vz = Math.sin(yaw) * sp * (1 - up * 0.5);
					f.vy = up * sp;
					FX.add(f);
				}
			}
			if (crit && m.critStars.on()) {
				int n = Math.max(3, Math.round(5 * s));
				for (int i = 0; i < n; i++) {
					Fx f = new Fx(Kind.STAR, p.add(0, 0.15, 0), 0.6f + RNG.nextFloat() * 0.3f, 0.8f + RNG.nextFloat() * 0.5f, 0xFFFFF4E0, pal[0]);
					double yaw = (i + RNG.nextDouble() * 0.7) / n * Math.PI * 2;
					double sp = 1.4 + RNG.nextDouble() * 1.2;
					f.vx = Math.cos(yaw) * sp;
					f.vz = Math.sin(yaw) * sp;
					f.vy = 2.2 + RNG.nextDouble() * 1.4;
					FX.add(f);
				}
			}
		}
		if (m.screenGlow.on()) {
			screenGlow = Math.min(1f, screenGlow + (crit ? 0.35f : 0.06f) * s);
			glowColor = pal[1];
		}
	}

	/** Ziel ist gestorben: Lichtsäule. */
	public static void kill(Vec3 feet, HitEffectsModule m) {
		int[] pal = m.palette();
		synchronized (FX) {
			FX.add(new Fx(Kind.BEAM, feet, 1.4f, 1f, pal[0], pal[1]));
			FX.add(new Fx(Kind.RIPPLE, feet.add(0, 0.3, 0), 0.6f, 1.0f, pal[0], pal[1]));
		}
		if (m.screenGlow.on()) {
			screenGlow = 1f;
			glowColor = pal[1];
		}
	}

	// ------------------------------------------------------------------ Zeichnen

	/** Kamera für einen Frame (Projektion Welt -> virtueller Bildschirm). */
	private static double ex, ey, ez, fx, fy, fz, rx, rz, ux, uy, uz, tanHalf, aspect;
	private static float vw, vh;

	/** {x, y, pixelsProBlock} oder null, wenn hinter der Kamera. */
	private static float[] project(double x, double y, double z) {
		double dx = x - ex, dy = y - ey, dz = z - ez;
		double d = dx * fx + dy * fy + dz * fz;
		if (d < 0.3) return null;
		double px = dx * rx + dz * rz;
		double py = dx * ux + dy * uy + dz * uz;
		float sx = (float) (vw / 2.0 * (1.0 + px / (d * tanHalf * aspect)));
		float sy = (float) (vh / 2.0 * (1.0 - py / (d * tanHalf)));
		float ppb = (float) (vh / 2.0 / (d * tanHalf));
		return new float[]{sx, sy, ppb};
	}

	public static void render(GuiGraphics g, Minecraft mc, LocalPlayer p, float vwIn, float vhIn, float pt, float dt) {
		HitEffectsModule m = dev.quiqzy.client.module.ModuleManager.HIT_EFFECTS;
		vw = vwIn;
		vh = vhIn;
		// echte Kamera (stimmt auch in F5 und Freecam)
		var cam = mc.gameRenderer.getMainCamera();
		Vec3 eye = cam.position();
		ex = eye.x;
		ey = eye.y;
		ez = eye.z;
		double yaw = Math.toRadians(cam.yRot());
		double pitch = Math.toRadians(cam.xRot());
		fx = -Math.sin(yaw) * Math.cos(pitch);
		fy = -Math.sin(pitch);
		fz = Math.cos(yaw) * Math.cos(pitch);
		rx = -Math.cos(yaw);
		rz = -Math.sin(yaw);
		ux = -rz * fy;
		uy = rz * fx - rx * fz;
		uz = rx * fy;
		tanHalf = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
		aspect = vw / vh;
		float s = m.isEnabled() ? m.strength() : 1f;

		synchronized (FX) {
			Iterator<Fx> it = FX.iterator();
			while (it.hasNext()) {
				Fx f = it.next();
				float age = f.age();
				if (age >= f.life || (f.kind == Kind.EMBER && f.vy < -50)) {
					it.remove();
					continue;
				}
				if (age < 0f) continue; // noch nicht gestartet
				float t = age / f.life;
				// Bewegung (Funken/Sterne mit Schwerkraft + Luftwiderstand)
				if (f.kind == Kind.EMBER) {
					// sanftes Schweben mit seitlichem Wiegen
					f.x += (f.vx + Math.sin(age * 1.3 + f.spin) * 0.12) * dt;
					f.y += f.vy * dt;
					f.z += (f.vz + Math.cos(age * 1.1 + f.spin) * 0.12) * dt;
				}
				if (f.kind == Kind.PETAL) {
					f.x += (f.vx + Math.sin(age * 1.6 + f.spin) * 0.35) * dt;
					f.y += f.vy * dt;
					f.z += (f.vz + Math.cos(age * 1.3 + f.spin) * 0.25) * dt;
				}
				if (f.kind == Kind.DUST) {
					f.y += f.vy * dt;
					f.vy *= Math.pow(0.4, dt);
				}
				if (f.kind == Kind.POP) {
					f.y += f.vy * dt;
					f.vy *= Math.pow(0.05, dt);
				}
				if (f.kind == Kind.GLOW) {
					f.x += (f.vx + Math.sin(age * 2.0 + f.spin) * 0.15) * dt;
					f.y += f.vy * dt;
					f.z += (f.vz + Math.cos(age * 1.7 + f.spin) * 0.15) * dt;
				}
				if (f.kind == Kind.SHARD) {
					f.x += f.vx * dt;
					f.y += f.vy * dt;
					f.z += f.vz * dt;
					f.vy -= 14.0 * dt;
					double drag = Math.pow(0.2, dt);
					f.vx *= drag;
					f.vz *= drag;
				}
				if (f.kind == Kind.SPARK || f.kind == Kind.STAR) {
					f.x += f.vx * dt;
					f.y += f.vy * dt;
					f.z += f.vz * dt;
					f.vy -= (f.kind == Kind.STAR ? 7.0 : 11.0) * dt;
					double drag = Math.pow(f.kind == Kind.STAR ? 0.25 : 0.12, dt);
					f.vx *= drag;
					f.vz *= drag;
				}
				switch (f.kind) {
					case RIPPLE -> ripple(g, f, t, s);
					case SPARK -> spark(g, f, t, s);
					case STAR -> star(g, f, t, s, age);
					case BEAM -> beam(g, f, t, s);
					case EMBER -> ember(g, f, t, age);
					case GLOW -> ember(g, f, t, age);
					case SHARD -> shard(g, f, t, age);
					case STREAK -> streak(g, f, t);
					case PETAL -> petal(g, f, t, age);
					case DUST -> ember(g, f, t, age);
					case POP -> pop(g, f, t);
				}
			}
		}

		if (critFlash > 0.005f) {
			ZeroRender.rect(g, 0f, 0f, vw, vh, 0f, ZeroRender.alpha(0xFFFFE2C2, 0.16f * critFlash));
			critFlash = Math.max(0f, critFlash - dt * 6f);
		}
		if (screenGlow > 0.005f) {
			edgeGlow(g, screenGlow * Math.min(1f, s));
			screenGlow = Math.max(0f, screenGlow - dt * 2.2f);
		}
	}

	private static float easeOut(float t) {
		float u = 1f - t;
		return 1f - u * u * u;
	}

	private static void ripple(GuiGraphics g, Fx f, float t, float s) {
		float[] sp = project(f.x, f.y, f.z);
		if (sp == null) return;
		float ppb = sp[2];
		// schnell aufblühen, dann weich verblassen
		float bloom = easeOut(Math.min(1f, t / 0.35f));
		float a = (1f - t) * (1f - t);
		float halo = Math.max(3f, Math.min(60f, f.size * ppb * (0.55f + 0.45f * bloom)));
		ZeroRender.glow(g, sp[0] - halo, sp[1] - halo, sp[0] + halo, sp[1] + halo, halo, halo * 0.9f, ZeroRender.alpha(f.c2, 0.45f * a));
		float core = Math.max(2f, Math.min(22f, f.size * 0.35f * ppb * (1f - t * 0.5f)));
		ZeroRender.glow(g, sp[0] - core, sp[1] - core, sp[0] + core, sp[1] + core, core, core * 0.8f, ZeroRender.alpha(f.c1, 0.8f * a));
		ZeroRender.circle(g, sp[0], sp[1], Math.max(1.2f, core * 0.35f), ZeroRender.alpha(0xFFFFFAF2, a));
	}

	private static void spark(GuiGraphics g, Fx f, float t, float s) {
		float[] sp = project(f.x, f.y, f.z);
		if (sp == null) return;
		// kurzer Schweif: Position ein Stück entgegen der Bewegung
		float[] tail = project(f.x - f.vx * 0.035, f.y - f.vy * 0.035, f.z - f.vz * 0.035);
		float a = 1f - t;
		float r = Math.max(0.8f, f.size * 0.06f * sp[2] * (1f - t * 0.6f));
		r = Math.min(r, 5f);
		ZeroRender.glow(g, sp[0] - r * 2.2f, sp[1] - r * 2.2f, sp[0] + r * 2.2f, sp[1] + r * 2.2f, r * 2.2f, r * 2f, ZeroRender.alpha(f.c2, 0.45f * a));
		if (tail != null) {
			float tx = tail[0], ty = tail[1];
			int steps = 3;
			for (int i = 1; i <= steps; i++) {
				float k = i / (float) (steps + 1);
				float x = sp[0] + (tx - sp[0]) * k, y = sp[1] + (ty - sp[1]) * k;
				float rr = r * (1f - k * 0.7f);
				ZeroRender.circle(g, x, y, rr, ZeroRender.alpha(f.c1, a * (1f - k)));
			}
		}
		ZeroRender.circle(g, sp[0], sp[1], r, ZeroRender.alpha(0xFFFFF6EC, a));
	}

	private static void star(GuiGraphics g, Fx f, float t, float s, float age) {
		float[] sp = project(f.x, f.y, f.z);
		if (sp == null) return;
		float pop = t < 0.15f ? t / 0.15f : 1f;
		float a = t > 0.6f ? 1f - (t - 0.6f) / 0.4f : 1f;
		float size = Math.min(14f, Math.max(3f, f.size * 0.16f * sp[2])) * pop;
		float twinkle = 0.75f + 0.25f * (float) Math.sin(age * 30f + f.spin * 3f);
		ZeroRender.glow(g, sp[0] - size, sp[1] - size, sp[0] + size, sp[1] + size, size, size * 0.8f, ZeroRender.alpha(f.c2, 0.5f * a));
		g.pose().pushMatrix();
		g.pose().translate(sp[0], sp[1]);
		g.pose().rotate(age * f.spin);
		float len = size * twinkle;
		float th = Math.max(0.7f, size * 0.16f);
		// 4-zackiger Funkelstern: zwei schmale Strahlen + kurze Diagonalen
		ZeroRender.rect(g, -len, -th, len, th, th, ZeroRender.alpha(f.c1, a));
		ZeroRender.rect(g, -th, -len, th, len, th, ZeroRender.alpha(f.c1, a));
		g.pose().rotate((float) Math.PI / 4f);
		float d = len * 0.45f;
		ZeroRender.rect(g, -d, -th * 0.7f, d, th * 0.7f, th * 0.7f, ZeroRender.alpha(f.c1, 0.8f * a));
		ZeroRender.rect(g, -th * 0.7f, -d, th * 0.7f, d, th * 0.7f, ZeroRender.alpha(f.c1, 0.8f * a));
		g.pose().popMatrix();
		ZeroRender.circle(g, sp[0], sp[1], Math.max(0.8f, th * 1.3f), ZeroRender.alpha(0xFFFFFFFF, a));
	}

	private static void beam(GuiGraphics g, Fx f, float t, float s) {
		float rise = easeOut(Math.min(1f, t / 0.25f));
		float[] base = project(f.x, f.y, f.z);
		float[] top = project(f.x, f.y + 3.0 + 9.0 * rise, f.z);
		if (base == null || top == null) return;
		float a = t < 0.7f ? 1f : 1f - (t - 0.7f) / 0.3f;
		float halfW = Math.max(2f, 0.42f * base[2] * (1f - t * 0.6f));
		float cx = base[0], x2 = top[0];
		float y1 = Math.min(base[1], top[1]), y2 = Math.max(base[1], top[1]);
		float mx = (cx + x2) / 2f;
		// äußeres Leuchten, Kern, heller Mittelstrich
		ZeroRender.glow(g, mx - halfW * 2.2f, y1, mx + halfW * 2.2f, y2, halfW * 2f, halfW * 2.4f, ZeroRender.alpha(f.c2, 0.45f * a));
		ZeroRender.gradient(g, mx - halfW, y1, mx + halfW, y2, halfW, ZeroRender.alpha(f.c1, 0f), ZeroRender.alpha(f.c1, 0.85f * a), 90f);
		ZeroRender.gradient(g, mx - halfW * 0.3f, y1, mx + halfW * 0.3f, y2, halfW * 0.3f, ZeroRender.alpha(0xFFFFFFFF, 0f), ZeroRender.alpha(0xFFFFF8EE, a), 90f);
		// Bodenglühen
		float gr = halfW * 3.5f;
		ZeroRender.glow(g, cx - gr, base[1] - gr * 0.35f, cx + gr, base[1] + gr * 0.35f, gr * 0.35f, gr * 0.5f, ZeroRender.alpha(f.c2, 0.6f * a));
	}

	private static void ember(GuiGraphics g, Fx f, float t, float age) {
		float[] sp = project(f.x, f.y, f.z);
		if (sp == null) return;
		float fade = Math.min(1f, Math.min(t / 0.15f, (1f - t) / 0.25f));
		float twinkle = 0.6f + 0.4f * (float) Math.sin(age * (3.0f + f.spin) + f.spin * 5f);
		float a = fade * twinkle;
		float r = Math.max(0.6f, Math.min(4.5f, f.size * 0.05f * sp[2]));
		ZeroRender.glow(g, sp[0] - r * 3f, sp[1] - r * 3f, sp[0] + r * 3f, sp[1] + r * 3f, r * 3f, r * 2.5f, ZeroRender.alpha(f.c2, 0.4f * a));
		ZeroRender.circle(g, sp[0], sp[1], r, ZeroRender.alpha(f.c1, a));
	}

	private static void shard(GuiGraphics g, Fx f, float t, float age) {
		float[] sp = project(f.x, f.y, f.z);
		if (sp == null) return;
		float a = 1f - t * t;
		float s = Math.max(1.2f, Math.min(6f, f.size * 0.07f * sp[2]));
		g.pose().pushMatrix();
		g.pose().translate(sp[0], sp[1]);
		g.pose().rotate(age * f.spin * 2f);
		ZeroRender.glow(g, -s * 1.6f, -s * 1.6f, s * 1.6f, s * 1.6f, s, s, ZeroRender.alpha(f.c2, 0.35f * a));
		ZeroRender.rect(g, -s, -s * 0.55f, s, s * 0.55f, 0.6f, ZeroRender.alpha(f.c1, a));
		g.pose().popMatrix();
	}

	/** Ein Blütenblatt: kleines, rotierendes Oval, das beim Drehen "flattert". */
	private static void petal(GuiGraphics g, Fx f, float t, float age) {
		float[] sp = project(f.x, f.y, f.z);
		if (sp == null) return;
		float fade = Math.min(1f, Math.min(t / 0.1f, (1f - t) / 0.2f));
		float s = Math.max(1.2f, Math.min(7f, f.size * 0.09f * sp[2]));
		float flip = Math.abs((float) Math.cos(age * 2.4 + f.spin));
		g.pose().pushMatrix();
		g.pose().translate(sp[0], sp[1]);
		g.pose().rotate(age * f.spin * 0.4f);
		ZeroRender.rect(g, -s, -s * 0.55f * (0.35f + 0.65f * flip), s, s * 0.55f * (0.35f + 0.65f * flip), s * 0.5f, ZeroRender.alpha(f.c1, 0.9f * fade));
		g.pose().popMatrix();
	}

	/** Crit: heller Lichtstreifen, schießt vom Treffpunkt nach außen. */
	private static void streak(GuiGraphics g, Fx f, float t) {
		float[] sp = project(f.x, f.y, f.z);
		if (sp == null) return;
		float ang = (float) f.vx;
		float ppb = sp[2];
		float r1 = (0.15f + easeOut(t) * f.size) * ppb;
		float r2 = r1 + (1f - t) * 0.9f * ppb;
		float cx = (float) Math.cos(ang), cy = (float) Math.sin(ang);
		float a = 1f - t;
		float w = Math.max(1f, Math.min(3.5f, 0.06f * ppb)) * (1f - t * 0.5f);
		line(g, sp[0] + cx * r1, sp[1] + cy * r1, sp[0] + cx * r2, sp[1] + cy * r2, w * 2.4f, ZeroRender.alpha(f.c2, 0.35f * a));
		line(g, sp[0] + cx * r1, sp[1] + cy * r1, sp[0] + cx * r2, sp[1] + cy * r2, w, ZeroRender.alpha(f.c1, a));
	}

	/** Crit: "CRIT" springt am Treffpunkt hoch und verblasst. */
	private static void pop(GuiGraphics g, Fx f, float t) {
		float[] sp = project(f.x, f.y, f.z);
		if (sp == null) return;
		float in = Math.min(1f, t / 0.12f);
		float scale = 1f + 0.5f * (1f - easeOut(in)) ;
		float a = t > 0.55f ? 1f - (t - 0.55f) / 0.45f : 1f;
		float size = 22f * scale;
		String txt = "CRIT";
		float w = dev.quiqzy.client.gui.engine.ZeroFont.BOLD.width(txt, size, 0f);
		ZeroRender.glow(g, sp[0] - w / 2f - 6f, sp[1] - 4f, sp[0] + w / 2f + 6f, sp[1] + size + 2f, 8f, 10f, ZeroRender.alpha(f.c2, 0.55f * a));
		ZeroRender.text(g, dev.quiqzy.client.gui.engine.ZeroFont.BOLD, txt, sp[0] - w / 2f, sp[1], size, 0f,
				ZeroRender.alpha(0xFFFFF6EC, a), ZeroRender.alpha(f.c1, a));
	}

	/** Linie zwischen zwei Bildschirmpunkten (als gedrehtes Rechteck). */
	public static void line(GuiGraphics g, float x1, float y1, float x2, float y2, float w, int color) {
		float dx = x2 - x1, dy = y2 - y1;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 0.5f) return;
		g.pose().pushMatrix();
		g.pose().translate((x1 + x2) / 2f, (y1 + y2) / 2f);
		g.pose().rotate((float) Math.atan2(dy, dx));
		ZeroRender.rect(g, -len / 2f, -w / 2f, len / 2f, w / 2f, w / 2f, color);
		g.pose().popMatrix();
	}

	/** Ghost: leuchtender Kasten, wo das Ziel laut Server wirklich steht (nur wenn es von der Anzeige abweicht). */
	public static void ghost(GuiGraphics g, net.minecraft.world.entity.LivingEntity e, float pt, int color) {
		Vec3 real = e.getInterpolation() != null && e.getInterpolation().hasActiveInterpolation()
				? e.getInterpolation().position() : e.position();
		Vec3 shown = e.getPosition(pt);
		double diff = real.distanceTo(shown);
		float a = (float) Math.min(1.0, Math.max(0.0, (diff - 0.04) / 0.25));
		if (a <= 0.01f) return;
		double hw = e.getBbWidth() / 2.0, h = e.getBbHeight();
		double[][] c = {
				{-hw, 0, -hw}, {hw, 0, -hw}, {hw, 0, hw}, {-hw, 0, hw},
				{-hw, h, -hw}, {hw, h, -hw}, {hw, h, hw}, {-hw, h, hw}};
		float[][] s = new float[8][];
		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			s[i] = project(real.x + c[i][0], real.y + c[i][1], real.z + c[i][2]);
			if (s[i] == null) return;
			minX = Math.min(minX, s[i][0]);
			maxX = Math.max(maxX, s[i][0]);
			minY = Math.min(minY, s[i][1]);
			maxY = Math.max(maxY, s[i][1]);
		}
		ZeroRender.glow(g, minX, minY, maxX, maxY, 6f, 10f, ZeroRender.alpha(color, 0.18f * a));
		ZeroRender.rect(g, minX, minY, maxX, maxY, 4f, ZeroRender.alpha(color, 0.08f * a));
		int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
		int lc = ZeroRender.alpha(color, 0.85f * a);
		for (int[] ed : edges) line(g, s[ed[0]][0], s[ed[0]][1], s[ed[1]][0], s[ed[1]][1], 1.1f, lc);
	}

	/** Welt -> Bildschirm für andere HUD-Teile (Kamera aus dem letzten render()). */
	public static float[] screen(double x, double y, double z) {
		return project(x, y, z);
	}

	/** Weiches Leuchten an allen vier Rändern – komplett im Bild (Verläufe von außen nach innen). */
	public static void edges(GuiGraphics g, float vw, float vh, int color, float depth) {
		// gestapelte Streifen: außen kräftig, nach innen weich auslaufend
		int steps = 12;
		float a0 = (color >>> 24) / 255f;
		float strip = depth / steps;
		for (int i = 0; i < steps; i++) {
			float k = 1f - i / (float) steps;
			int c = ZeroRender.alpha(color | 0xFF000000, a0 * k * k * 0.5f);
			float d = strip * (i + 1);
			ZeroRender.rect(g, 0f, 0f, vw, d, 0f, c);
			ZeroRender.rect(g, 0f, vh - d, vw, vh, 0f, c);
			ZeroRender.rect(g, 0f, d, d, vh - d, 0f, c);
			ZeroRender.rect(g, vw - d, d, vw, vh - d, 0f, c);
		}
	}

	/** Weiches Leuchten an allen vier Bildschirmrändern. */
	private static void edgeGlow(GuiGraphics g, float k) {
		edges(g, vw, vh, ZeroRender.alpha(glowColor, 0.42f * k), 70f);
	}
}
