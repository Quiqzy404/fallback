package dev.quiqzy.client.render;

import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

import dev.quiqzy.client.module.ModuleManager;

/** Welt-Effekte mit Tiefentest (Blöcke verdecken sie): Shockwave, Hit Splash, Cyberspace-Himmel. */
public final class FxRenderer {
	private FxRenderer() {
	}

	private static final Random RNG = new Random();
	private static final float[][] STARS = new float[10][5]; // yaw, pitch, start, dauer, länge

	public static void render(WorldRenderContext context) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		var vfx = ModuleManager.VISUAL_FX;
		var cyber = ModuleManager.CYBERSPACE;
		boolean ground = vfx.isEnabled() && !vfx.ground.isEmpty();
		var rain = ModuleManager.RAIN_FX;
		var holeEsp = ModuleManager.HOLE_ESP;
		var traj = ModuleManager.TRAJECTORIES;
		if (!ground && !cyber.isEnabled() && !rain.isEnabled() && !holeEsp.isEnabled() && !traj.isEnabled()) {
			DROPS.clear();
			return;
		}
		Vec3 cam = context.worldState().cameraRenderState.pos;
		PoseStack ps = context.matrices();
		ps.pushPose();
		ps.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = ps.last().pose();
		VertexConsumer buf = context.consumers().getBuffer(RenderTypes.debugFilledBox());
		int[] pal = ModuleManager.HIT_EFFECTS.palette();
		long now = System.currentTimeMillis();
		if (ground) {
			for (var g : vfx.ground) {
				float t = (now - g.born()) / 1400f;
				if (g.kind() == 0) shockwave(mc, buf, m, g.pos(), t, pal);
				else splash(buf, m, g.pos(), t * 1.6f, g.seed(), pal);
			}
		}
		if (cyber.isEnabled()) cyberspace(buf, m, cam, now, pal, cyber);
		if (rain.isEnabled()) rain(mc, buf, m, cam, rain);
		if (holeEsp.isEnabled()) holes(buf, m, holeEsp);
		if (traj.isEnabled()) trajectories(mc, buf, m, cam, traj);
		else DROPS.clear();
		ps.popPose();
	}

	// ------------------------------------------------------------------ Hole ESP

	private static void holes(VertexConsumer b, Matrix4f m, dev.quiqzy.client.module.combat.HoleEspModule mod) {
		float h = mod.height.get() / 100f;
		for (var hole : mod.holes) {
			int rgb = hole.safe() ? mod.bedrock.get() : mod.obsidian.get();
			double x = hole.pos().getX(), y = hole.pos().getY() + 0.01, z = hole.pos().getZ(), e = 0.04;
			// gefüllter Boden + leuchtender Rand
			flat(b, m, x, z, x + 1, z + 1, y, argb(0.22f, rgb));
			flat(b, m, x, z, x + 1, z + e, y + 0.002, argb(0.9f, rgb));
			flat(b, m, x, z + 1 - e, x + 1, z + 1, y + 0.002, argb(0.9f, rgb));
			flat(b, m, x, z, x + e, z + 1, y + 0.002, argb(0.9f, rgb));
			flat(b, m, x + 1 - e, z, x + 1, z + 1, y + 0.002, argb(0.9f, rgb));
			if (h > 0.01f) {
				// halbtransparente Wände nach oben (verblassend)
				for (int i = 0; i < 4; i++) {
					float yy = (float) y + h * i / 4f, a = 0.16f * (1f - i / 4f);
					flat(b, m, x, z, x + 1, z + e, yy, argb(a, rgb));
					flat(b, m, x, z + 1 - e, x + 1, z + 1, yy, argb(a, rgb));
					flat(b, m, x, z, x + e, z + 1, yy, argb(a, rgb));
					flat(b, m, x + 1 - e, z, x + 1, z + 1, yy, argb(a, rgb));
				}
			}
		}
	}

	// ------------------------------------------------------------------ Rain FX
	/** Tropfen: x, y, z, Fallgeschwindigkeit, Bodenhöhe; Spritzer: x, y, z, Alter (negativ = Tropfen). */
	private static final java.util.List<double[]> DROPS = new java.util.ArrayList<>();
	private static final java.util.List<double[]> SPLASH = new java.util.ArrayList<>();
	private static long lastRain;

	private static void rain(Minecraft mc, VertexConsumer b, Matrix4f m, Vec3 cam, dev.quiqzy.client.module.visuals.RainFxModule mod) {
		long nowN = System.nanoTime();
		double dt = lastRain == 0 ? 0.016 : Math.min(0.1, (nowN - lastRain) / 1e9);
		lastRain = nowN;
		int[] col = mod.colors();
		int r = mod.range.get();
		Vec3 pp = mc.player.position();
		// neue Tropfen über dem Spieler verteilen
		int want = mod.amount.get();
		int spawn = Math.min(30, want - DROPS.size());
		for (int i = 0; i < spawn; i++) {
			double x = pp.x + (RNG.nextDouble() * 2 - 1) * r, z = pp.z + (RNG.nextDouble() * 2 - 1) * r;
			int gy = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
			double y = Math.max(gy, pp.y) + 6 + RNG.nextDouble() * 14;
			DROPS.add(new double[]{x, y, z, 14 + RNG.nextDouble() * 8, gy});
		}
		var it = DROPS.iterator();
		while (it.hasNext()) {
			double[] d = it.next();
			d[1] -= d[3] * dt;
			if (d[1] <= d[4]) {
				if (mod.splashes.on() && SPLASH.size() < 220) SPLASH.add(new double[]{d[0], d[4] + 0.02, d[2], 0});
				it.remove();
				continue;
			}
			if (Math.abs(d[0] - pp.x) > r + 4 || Math.abs(d[2] - pp.z) > r + 4) {
				it.remove();
				continue;
			}
			Vec3 head = new Vec3(d[0], d[1], d[2]);
			Vec3 tail = new Vec3(d[0], d[1] + 0.9, d[2]);
			line(b, m, cam, tail, head, 0.0018, argb(0.55f, col[1]));
			line(b, m, cam, new Vec3(d[0], d[1] + 0.3, d[2]), head, 0.0028, argb(1f, col[0]));
		}
		// Spritzer: kleiner Ring + Punkte, die kurz hochhüpfen
		var si = SPLASH.iterator();
		while (si.hasNext()) {
			double[] s = si.next();
			s[3] += dt;
			double t = s[3] / 0.45;
			if (t >= 1) {
				si.remove();
				continue;
			}
			float a = (float) (1 - t);
			double rr = 0.08 + t * 0.35;
			int seg = 10;
			for (int i = 0; i < seg; i++) {
				double a1 = i / (double) seg * Math.PI * 2, a2 = (i + 1) / (double) seg * Math.PI * 2;
				line(b, m, cam, new Vec3(s[0] + Math.cos(a1) * rr, s[1], s[2] + Math.sin(a1) * rr),
						new Vec3(s[0] + Math.cos(a2) * rr, s[1], s[2] + Math.sin(a2) * rr), 0.0012, argb(0.8f * a, col[0]));
			}
			for (int i = 0; i < 3; i++) {
				double ang = i * 2.1 + s[0] * 7;
				double hx = s[0] + Math.cos(ang) * t * 0.3, hz = s[2] + Math.sin(ang) * t * 0.3;
				double hy = s[1] + Math.sin(t * Math.PI) * 0.18;
				line(b, m, cam, new Vec3(hx, hy, hz), new Vec3(hx, hy + 0.04, hz), 0.002, argb(0.9f * a, col[1]));
			}
		}
	}

	private static int argb(float a, int rgb) {
		return (Math.max(0, Math.min(255, Math.round(a * 255))) << 24) | (rgb & 0xFFFFFF);
	}

	private static void flat(VertexConsumer b, Matrix4f m, double x1, double z1, double x2, double z2, double y, int c) {
		float fy = (float) y;
		b.addVertex(m, (float) x1, fy, (float) z1).setColor(c);
		b.addVertex(m, (float) x1, fy, (float) z2).setColor(c);
		b.addVertex(m, (float) x2, fy, (float) z2).setColor(c);
		b.addVertex(m, (float) x2, fy, (float) z1).setColor(c);
		b.addVertex(m, (float) x2, fy, (float) z1).setColor(c);
		b.addVertex(m, (float) x2, fy, (float) z2).setColor(c);
		b.addVertex(m, (float) x1, fy, (float) z2).setColor(c);
		b.addVertex(m, (float) x1, fy, (float) z1).setColor(c);
	}

	/** Shockwave: ein Ring aus leuchtenden Block-Umrissen läuft über den Boden nach außen. */
	private static void shockwave(Minecraft mc, VertexConsumer b, Matrix4f m, Vec3 c, float t, int[] pal) {
		if (t >= 1f) return;
		double r = 0.5 + t * 7.5;
		float fade = (1f - t) * (1f - t);
		int bx = (int) Math.floor(c.x), bz = (int) Math.floor(c.z), by = (int) Math.floor(c.y);
		int R = (int) Math.ceil(r) + 1;
		double e = 0.05;
		for (int x = -R; x <= R; x++) {
			for (int z = -R; z <= R; z++) {
				double d = Math.sqrt((x + 0.5 + bx - c.x) * (x + 0.5 + bx - c.x) + (z + 0.5 + bz - c.z) * (z + 0.5 + bz - c.z));
				double band = Math.abs(d - r);
				if (band > 1.6) continue;
				float k = (float) (1.0 - band / 1.6) * fade;
				// Oberfläche suchen
				int top = Integer.MIN_VALUE;
				for (int y = by + 1; y >= by - 3; y--) {
					BlockPos p = new BlockPos(bx + x, y, bz + z);
					if (!mc.level.getBlockState(p).isAir() && mc.level.getBlockState(p.above()).isAir()) {
						top = y;
						break;
					}
				}
				if (top == Integer.MIN_VALUE) continue;
				double y = top + 1.02, x0 = bx + x, z0 = bz + z;
				int edge = argb(0.95f * k, pal[0]);
				flat(b, m, x0, z0, x0 + 1, z0 + e, y, edge);
				flat(b, m, x0, z0 + 1 - e, x0 + 1, z0 + 1, y, edge);
				flat(b, m, x0, z0, x0 + e, z0 + 1, y, edge);
				flat(b, m, x0 + 1 - e, z0, x0 + 1, z0 + 1, y, edge);
				flat(b, m, x0 + e, z0 + e, x0 + 1 - e, z0 + 1 - e, y - 0.005, argb(0.18f * k, pal[1]));
			}
		}
	}

	/** Hit Splash: leuchtende Tinten-Spritzer strahlenförmig auf dem Boden unter dem Ziel. */
	private static void splash(VertexConsumer b, Matrix4f m, Vec3 c, float t, float seed, int[] pal) {
		if (t >= 1f) return;
		float grow = 1f - (1f - Math.min(1f, t / 0.25f)) * (1f - Math.min(1f, t / 0.25f));
		float fade = t < 0.5f ? 1f : (1f - t) / 0.5f;
		RNG.setSeed((long) (seed * 100000));
		double y = c.y + 0.03;
		// Kern
		double core = 0.45 * grow;
		flat(b, m, c.x - core, c.z - core, c.x + core, c.z + core, y, argb(0.35f * fade, pal[1]));
		int n = 16;
		for (int i = 0; i < n; i++) {
			double ang = (i + RNG.nextDouble() * 0.6) / n * Math.PI * 2;
			double len = (0.9 + RNG.nextDouble() * 1.6) * grow;
			double w = 0.05 + RNG.nextDouble() * 0.06;
			double cx = Math.cos(ang), cz = Math.sin(ang);
			// Strahl als Kette kleiner Quadrate, außen dünner
			int steps = 8;
			for (int s = 0; s < steps; s++) {
				double f = s / (double) steps;
				double px = c.x + cx * len * f, pz = c.z + cz * len * f;
				double ww = w * (1.2 - f);
				flat(b, m, px - ww, pz - ww, px + ww, pz + ww, y, argb(0.85f * fade * (1f - (float) f * 0.6f), f < 0.3 ? 0xFFFFFF : pal[0]));
			}
			// Tropfen am Ende
			double tx = c.x + cx * len * 1.08, tz = c.z + cz * len * 1.08, tw = w * 1.3;
			flat(b, m, tx - tw, tz - tw, tx + tw, tz + tw, y, argb(0.7f * fade, pal[0]));
		}
	}

	/** Linie als dünne, zur Kamera gedrehte Fläche. */
	private static void line(VertexConsumer buf, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, double px, int c) {
		Vec3 dir = b.subtract(a);
		Vec3 side = dir.cross(a.subtract(cam));
		if (side.lengthSqr() < 1e-8) return;
		side = side.normalize();
		Vec3 sa = side.scale(px * a.distanceTo(cam)), sb = side.scale(px * b.distanceTo(cam));
		Vec3 a1 = a.add(sa), a2 = a.subtract(sa), b1 = b.add(sb), b2 = b.subtract(sb);
		buf.addVertex(m, (float) a1.x, (float) a1.y, (float) a1.z).setColor(c);
		buf.addVertex(m, (float) a2.x, (float) a2.y, (float) a2.z).setColor(c);
		buf.addVertex(m, (float) b2.x, (float) b2.y, (float) b2.z).setColor(c);
		buf.addVertex(m, (float) b1.x, (float) b1.y, (float) b1.z).setColor(c);
		buf.addVertex(m, (float) b1.x, (float) b1.y, (float) b1.z).setColor(c);
		buf.addVertex(m, (float) b2.x, (float) b2.y, (float) b2.z).setColor(c);
		buf.addVertex(m, (float) a2.x, (float) a2.y, (float) a2.z).setColor(c);
		buf.addVertex(m, (float) a1.x, (float) a1.y, (float) a1.z).setColor(c);
	}

	private static void trajectories(Minecraft mc, VertexConsumer b, Matrix4f m, Vec3 cam, dev.quiqzy.client.module.visuals.TrajectoriesModule mod) {
		var p = mc.player;
		if (p == null) return;
		boolean usingBow = p.isUsingItem() && p.getUseItem().is(net.minecraft.world.item.Items.BOW);
		float charge = Math.min(1f, p.getTicksUsingItem() / 20f);
		double[] phys = mod.physics(p.getMainHandItem(), usingBow, Math.max(0.1f, charge));
		if (phys == null) phys = mod.physics(p.getOffhandItem(), usingBow, Math.max(0.1f, charge));
		if (phys == null) return;
		float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		double yaw = Math.toRadians(p.getViewYRot(pt)), pitch = Math.toRadians(p.getViewXRot(pt));
		Vec3 look = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
		Vec3 pos = p.getEyePosition(pt).add(look.scale(0.3));
		Vec3 v = look.scale(phys[0]);
		int col = mod.color.get();
		for (int i = 0; i < 90; i++) {
			Vec3 next = pos.add(v);
			var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(pos, next,
					net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
			if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
				Vec3 c = hit.getLocation();
				if (mod.landing.on()) worldRingMark(b, m, c.x, c.y, c.z, 0.4, col);
				dot(b, m, cam, c, col, 0.9f);
				return;
			}
			if ((i & 1) == 0) dot(b, m, cam, pos, col, 0.6f);
			pos = next;
			v = v.scale(0.99).subtract(0, phys[1], 0);
		}
	}

	/** Kleines, zur Kamera gedrehtes Quadrat an einem Weltpunkt. */
	private static void dot(VertexConsumer b, Matrix4f m, Vec3 cam, Vec3 pwp, int col, float scale) {
		double dist = Math.max(1.0, pwp.distanceTo(cam));
		double sz = 0.018 * dist * scale;
		Vec3 toCam = cam.subtract(pwp);
		Vec3 right = toCam.cross(new Vec3(0, 1, 0));
		if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
		right = right.normalize().scale(sz);
		Vec3 up = toCam.cross(right).normalize().scale(sz);
		int c = argb(0.95f, col);
		Vec3 a1 = pwp.subtract(right).subtract(up), a2 = pwp.add(right).subtract(up), a3 = pwp.add(right).add(up), a4 = pwp.subtract(right).add(up);
		b.addVertex(m, (float) a1.x, (float) a1.y, (float) a1.z).setColor(c);
		b.addVertex(m, (float) a2.x, (float) a2.y, (float) a2.z).setColor(c);
		b.addVertex(m, (float) a3.x, (float) a3.y, (float) a3.z).setColor(c);
		b.addVertex(m, (float) a4.x, (float) a4.y, (float) a4.z).setColor(c);
	}

	private static void worldRingMark(VertexConsumer b, Matrix4f m, double cx, double cy, double cz, double r, int col) {
		int seg = 16;
		for (int i = 0; i < seg; i++) {
			double a1 = i / (double) seg * Math.PI * 2, a2 = (i + 1) / (double) seg * Math.PI * 2;
			flat(b, m, cx + Math.cos(a1) * r, cz + Math.sin(a1) * r, cx + Math.cos(a2) * r + 0.02, cz + Math.sin(a2) * r + 0.02, cy + 0.02, argb(0.8f, col));
		}
	}

	private static Vec3 dome(Vec3 cam, double yaw, double pitch, double r) {
		return cam.add(Math.cos(pitch) * Math.cos(yaw) * r, Math.sin(pitch) * r, Math.cos(pitch) * Math.sin(yaw) * r);
	}

	/** Cyberspace: Gitter-Kuppel am Himmel + Sternschnuppen (Kuppel folgt der Kamera, also immer "unendlich" weit). */
	private static void cyberspace(VertexConsumer b, Matrix4f m, Vec3 cam, long now, int[] pal, dev.quiqzy.client.module.visuals.CyberspaceModule mod) {
		double R = 110;
		if (mod.grid.on()) {
			int n = mod.density.get();
			int col = argb(0.32f, pal[0]);
			int seg = 48;
			// Breitengrade
			for (int lat = 1; lat <= 6; lat++) {
				double p = Math.toRadians(lat * 12);
				for (int i = 0; i < seg; i++) {
					double a1 = i / (double) seg * Math.PI * 2, a2 = (i + 1) / (double) seg * Math.PI * 2;
					line(b, m, cam, dome(cam, a1, p, R), dome(cam, a2, p, R), 0.0011, lat == 1 ? argb(0.55f, pal[0]) : col);
				}
			}
			// Längengrade
			for (int i = 0; i < n * 2; i++) {
				double a = i / (double) (n * 2) * Math.PI * 2;
				for (int s = 0; s < 6; s++) {
					double p1 = Math.toRadians(12 + s * 12), p2 = Math.toRadians(12 + (s + 1) * 12);
					line(b, m, cam, dome(cam, a, p1, R), dome(cam, a, Math.min(p2, Math.toRadians(80)), R), 0.0011, col);
				}
			}
			// leuchtender Horizont-Streifen
			for (int i = 0; i < seg; i++) {
				double a1 = i / (double) seg * Math.PI * 2, a2 = (i + 1) / (double) seg * Math.PI * 2;
				line(b, m, cam, dome(cam, a1, Math.toRadians(4), R), dome(cam, a2, Math.toRadians(4), R), 0.006, argb(0.12f, pal[1]));
			}
		}
		if (mod.stars.on()) {
			float sec = (now % 100000L) / 1000f;
			for (float[] st : STARS) {
				if (st[3] == 0 || sec - st[2] > st[3] || sec < st[2]) {
					st[0] = (float) (RNG.nextDouble() * Math.PI * 2);
					st[1] = (float) Math.toRadians(25 + RNG.nextDouble() * 50);
					st[2] = sec + (float) RNG.nextDouble() * 3f;
					st[3] = 0.6f + (float) RNG.nextDouble() * 0.6f;
					st[4] = 0.18f + (float) RNG.nextDouble() * 0.2f;
					continue;
				}
				float k = (sec - st[2]) / st[3];
				double yaw = st[0] + k * 0.5, pitch = st[1] - k * 0.35;
				Vec3 head = dome(cam, yaw, pitch, R * 0.95);
				Vec3 tail = dome(cam, yaw - st[4], pitch + st[4] * 0.7, R * 0.95);
				float a = (float) Math.sin(Math.PI * k);
				line(b, m, cam, tail, head, 0.0016, argb(0.25f * a, 0xFFFFFF));
				line(b, m, cam, dome(cam, yaw - st[4] * 0.3, pitch + st[4] * 0.21, R * 0.95), head, 0.0028, argb(0.9f * a, 0xFFFFFF));
			}
		}
	}
}
