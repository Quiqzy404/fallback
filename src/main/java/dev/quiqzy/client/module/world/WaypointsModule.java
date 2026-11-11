package dev.quiqzy.client.module.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;

import dev.quiqzy.client.config.QuiqzyConfig;
import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Waypoints: Punkte speichern (im GUI "Add here" oder per Taste), im HUD mit Richtung + Entfernung
 * und in der Welt als leuchtende Säule anzeigen. Nur Wegpunkte der aktuellen Dimension werden gezeigt.
 */
public final class WaypointsModule extends Module {
	private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

	public final Setting.Waypoints points = add(new Setting.Waypoints("points", "Waypoints"));
	public final Setting.Bool hud = add(new Setting.Bool("hud", "Show in HUD", true));
	public final Setting.Bool beam = add(new Setting.Bool("beam", "Beam in World", true));

	public record Shown(String name, int dist, String arrow, int dir, int rgb) {
	}

	public WaypointsModule() {
		super("waypoints", "Waypoints", "Save spots and see where they are", Category.WORLD);
	}

	/** Wegpunkt an der aktuellen Position hinzufügen. Leerer Name -> "Waypoint N". */
	public void addHere(Minecraft mc, String name) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null) return;
		String n = name == null ? "" : name.trim();
		if (n.length() > 24) n = n.substring(0, 24);
		if (n.isEmpty()) n = "Waypoint " + (points.get().size() + 1);
		points.add(new Setting.Waypoints.Point(n, p.getBlockX(), p.getBlockY(), p.getBlockZ(), dimensionId(mc.level)));
		QuiqzyConfig.save();
	}

	/** Die nächsten Wegpunkte der aktuellen Dimension fürs HUD. */
	public List<Shown> shown(Minecraft mc, LocalPlayer p, int max) {
		String dim = dimensionId(mc.level);
		List<Shown> out = new ArrayList<>();
		for (Setting.Waypoints.Point w : points.get()) {
			if (!w.dim().equals(dim)) continue;
			double dx = w.x() + 0.5 - p.getX();
			double dz = w.z() + 0.5 - p.getZ();
			double dy = w.y() - p.getY();
			int dist = (int) Math.sqrt(dx * dx + dy * dy + dz * dz);
			int dir = dirIndex(p, dx, dz);
			out.add(new Shown(w.name(), dist, ARROWS[dir], dir, colorOf(w.name())));
		}
		out.sort(Comparator.comparingInt(Shown::dist));
		return out.size() > max ? out.subList(0, max) : out;
	}

	public static String dimensionId(Level level) {
		if (level == null) return "";
		if (level.dimension() == Level.OVERWORLD) return "overworld";
		if (level.dimension() == Level.NETHER) return "nether";
		if (level.dimension() == Level.END) return "end";
		return level.dimension().toString();
	}

	/** Feste kräftige Farbe aus dem Namen. */
	public static int colorOf(String name) {
		float hue = (name.hashCode() & 0xFFFF) / 65535f;
		return net.minecraft.util.Mth.hsvToRgb(hue, 0.6f, 1f) & 0xFFFFFF;
	}

	private static int dirIndex(LocalPlayer p, double dx, double dz) {
		double target = Math.toDegrees(Math.atan2(dx, -dz));
		double facing = p.getYRot() + 180.0;
		double rel = ((target - facing) % 360 + 360) % 360;
		return (int) Math.round(rel / 45.0) % 8;
	}
}
