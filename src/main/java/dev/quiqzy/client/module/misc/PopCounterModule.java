package dev.quiqzy.client.module.misc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Totem Pop Counter: zählt, wie oft andere Spieler ein Totem verbrauchen (Nametag, Target HUD, kurzer Hinweis). */
public final class PopCounterModule extends Module {
	public final Setting.Bool notify = add(new Setting.Bool("notify", "On-Screen Notice", true));

	private final Map<UUID, Integer> pops = new HashMap<>();

	/** Kurze Hinweise oben in der Mitte: Text + Zeitpunkt. */
	public record Notice(String text, long at) {
	}

	public final List<Notice> notices = new ArrayList<>();

	public PopCounterModule() {
		super("pop_counter", "Totem Pops", "Counts totem pops of other players", Category.HUD);
	}

	public int pops(Player p) {
		return pops.getOrDefault(p.getUUID(), 0);
	}

	/** Vom Mixin: Entity-Event 35 (Totem ausgelöst). */
	public void onPop(Player p) {
		if (!isEnabled()) return;
		int n = pops.merge(p.getUUID(), 1, Integer::sum);
		if (notify.on() && p != Minecraft.getInstance().player) {
			notices.add(new Notice(p.getName().getString() + " popped " + (n == 1 ? "a totem" : n + " totems"), System.currentTimeMillis()));
			while (notices.size() > 4) notices.remove(0);
		}
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.level == null) return;
		// Tote Spieler zurücksetzen
		for (Player pl : mc.level.players()) {
			if (pl.isDeadOrDying()) pops.remove(pl.getUUID());
		}
		notices.removeIf(n -> System.currentTimeMillis() - n.at() > 3500);
	}
}
