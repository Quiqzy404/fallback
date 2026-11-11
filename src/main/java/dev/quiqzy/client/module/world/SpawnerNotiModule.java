package dev.quiqzy.client.module.world;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.SpawnerTracker.SpawnerEntry;
import dev.quiqzy.client.util.UiSounds;

/**
 * Spawner Notification:
 * Erzeugt ein stilvolles On-Screen-Popup bei neu gefundenen Spawnern mit
 * Typ, Koordinaten, Distanz und Ton, und rendert einen Beacon Beam über dem Spawner
 * bis zum Build-Limit.
 */
public final class SpawnerNotiModule extends Module {
	public final Setting.Bool popup = add(new Setting.Bool("popup", "Screen Popup", true));
	public final Setting.Bool sound = add(new Setting.Bool("sound", "Sound Alert", true));
	public final Setting.Bool beam = add(new Setting.Bool("beam", "Beacon Beam", true));
	public final Setting.Choice beamTop = add(new Setting.Choice("beam_top", "Beam Top", "Build Limit",
			"Build Limit", "Player +64", "Fixed"));
	public final Setting.Int beamHeight = add(new Setting.Int("beam_height", "Fixed Beam Height", 64, 8, 384, " bl"));
	public final Setting.Int beamWidth = add(new Setting.Int("beam_width", "Beam Width", 100, 0, 300, "%"));
	public final Setting.Int beamOpacity = add(new Setting.Int("beam_opacity", "Beam Opacity", 60, 5, 100, "%"));
	public final Setting.Bool pulse = add(new Setting.Bool("pulse", "Pulse Animation", false));
	public final Setting.Int pulseSpeed = add(new Setting.Int("pulse_speed", "Pulse Speed", 100, 10, 300, "%"));
	public final Setting.Int pulseStrength = add(new Setting.Int("pulse_strength", "Pulse Strength", 60, 0, 100, "%"));
	public final Setting.Bool coreBeam = add(new Setting.Bool("core_beam", "Core Beam", true));
	public final Setting.Color coreColor = add(new Setting.Color("core_color", "Core Color", 0xFFD580));
	public final Setting.Int coreWidth = add(new Setting.Int("core_width", "Core Width", 10, 0, 50, "%"));
	public final Setting.Int coreOpacity = add(new Setting.Int("core_opacity", "Core Opacity", 85, 5, 100, "%"));
	public final Setting.Bool throughWalls = add(new Setting.Bool("through_walls", "Through Blocks", true));
	public final Setting.Bool box = add(new Setting.Bool("box", "Spawner Box", true));
	public final Setting.Color beamColor = add(new Setting.Color("beam_color", "Beam Color", 0xFFA040));
	public final Setting.Int range = add(new Setting.Int("range", "Notice Range", 128, 16, 256, " m"));
	public final Setting.Bool autoWaypoint = add(new Setting.Bool("auto_waypoint", "Auto Waypoint", false));

	public record SpawnerNotice(BlockPos pos, String mobName, ItemStack icon, double dist, long at) {
	}

	public final List<SpawnerNotice> notices = new ArrayList<>();

	public SpawnerNotiModule() {
		super("spawner_noti", "Spawner Noti", "Popups and beacon beams to build limit when spawners are found", Category.WORLD);
	}

	public void onSpawnerFound(SpawnerEntry entry) {
		if (!isEnabled() || entry.notified) return;
		entry.notified = true;

		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null) return;

		double dist = Math.sqrt(p.distanceToSqr(entry.pos.getX() + 0.5, entry.pos.getY() + 0.5, entry.pos.getZ() + 0.5));
		if (dist > range.get()) return;

		if (sound.on()) {
			UiSounds.found();
		}

		if (popup.on()) {
			notices.add(new SpawnerNotice(entry.pos, entry.displayName(), entry.icon, dist, System.currentTimeMillis()));
			while (notices.size() > 4) notices.remove(0);
		}

		if (autoWaypoint.on() && ModuleManager.WAYPOINTS.isEnabled()) {
			ModuleManager.WAYPOINTS.points.add(new Setting.Waypoints.Point(entry.displayName(), entry.pos.getX(), entry.pos.getY(), entry.pos.getZ(), entry.dim));
		}
	}

	@Override
	public void onTick(Minecraft mc) {
		notices.removeIf(n -> System.currentTimeMillis() - n.at() > 5000);
	}
}
