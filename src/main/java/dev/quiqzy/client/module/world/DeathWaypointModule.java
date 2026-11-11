package dev.quiqzy.client.module.world;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import net.minecraft.client.Minecraft;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.util.UiSounds;

/** Death Waypoint: beim Tod wird automatisch ein Wegpunkt gesetzt ("Death 18:42"), damit du deine Items findest. */
public final class DeathWaypointModule extends Module {
	private boolean wasDead;

	public DeathWaypointModule() {
		super("death_waypoint", "Death Waypoint", "Saves a waypoint where you died", Category.WORLD);
		setEnabled(true);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null) return;
		boolean dead = mc.player.isDeadOrDying();
		if (dead && !wasDead) {
			String name = "Death " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
			if (!ModuleManager.WAYPOINTS.isEnabled()) ModuleManager.WAYPOINTS.setEnabled(true);
			ModuleManager.WAYPOINTS.addHere(mc, name);
			UiSounds.added();
		}
		wasDead = dead;
	}
}
