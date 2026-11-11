package dev.quiqzy.client.module.misc;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Session Stats: echte Werte dieser Sitzung – Spielzeit, Kills (Spieler/Mobs), Tode, K/D, abgebaute Blöcke. */
public final class SessionStatsModule extends Module {
	public final Setting.Bool mobs = add(new Setting.Bool("mobs", "Count Mob Kills", false));

	public long start = System.currentTimeMillis();
	public int kills;
	public int deaths;
	public int blocks;
	private boolean wasDead;

	private record Hit(Entity e, long until) {
	}

	private final List<Hit> recent = new ArrayList<>();

	public SessionStatsModule() {
		super("session_stats", "Session Stats", "Playtime, kills, deaths and blocks this session", Category.HUD);
	}

	@Override
	protected void onEnable() {
		start = System.currentTimeMillis();
		kills = deaths = blocks = 0;
	}

	public void onAttack(Entity e) {
		if (!isEnabled() || !(e instanceof LivingEntity)) return;
		if (!(e instanceof Player) && !mobs.on()) return;
		recent.removeIf(h -> h.e() == e);
		recent.add(new Hit(e, System.currentTimeMillis() + 3000));
	}

	public void onBlockBroken() {
		if (isEnabled()) blocks++;
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null) return;
		long now = System.currentTimeMillis();
		recent.removeIf(h -> {
			if (h.e() instanceof LivingEntity le && le.isDeadOrDying()) {
				kills++;
				return true;
			}
			return now > h.until();
		});
		boolean dead = mc.player.isDeadOrDying();
		if (dead && !wasDead) deaths++;
		wasDead = dead;
	}
}
