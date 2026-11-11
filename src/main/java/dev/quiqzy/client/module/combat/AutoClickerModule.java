package dev.quiqzy.client.module.combat;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.InputUtil;
import dev.quiqzy.client.util.Targets;

/** Auto Clicker: solange du Angriff hältst, wird automatisch geklickt (CPS-Bereich oder im Takt des Aufladens). */
public final class AutoClickerModule extends Module {
	public final Setting.Choice mode = add(new Setting.Choice("mode", "Mode", "Charge Sync", "Charge Sync", "CPS"));
	public final Setting.Int minCps = add(new Setting.Int("min_cps", "Min CPS", 8, 1, 20));
	public final Setting.Int maxCps = add(new Setting.Int("max_cps", "Max CPS", 12, 1, 20));
	public final Setting.Int cooldown = add(new Setting.Int("cooldown", "Charge", 92, 50, 100, " %"));

	private long nextClick;

	public AutoClickerModule() {
		super("auto_clicker", "Auto Clicker", "Clicks for you while you hold attack", Category.COMBAT);
	}

	@Override
	public void onFrame(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.screen != null || mc.gameMode == null || p.isUsingItem()) return;
		if (!InputUtil.isPhysicallyDown(mc, mc.options.keyAttack)) return;
		if (!(mc.hitResult instanceof EntityHitResult ehr) || !(ehr.getEntity() instanceof LivingEntity)) {
			return; // Blöcke abbauen macht Minecraft beim Halten selbst
		}
		long now = System.currentTimeMillis();
		if (mode.get().equals("Charge Sync")) {
			if (Targets.cooled(p, cooldown.get())) Targets.hit(mc, ehr.getEntity());
			return;
		}
		if (now < nextClick) return;
		int lo = Math.min(minCps.get(), maxCps.get()), hi = Math.max(minCps.get(), maxCps.get());
		double cps = lo + Math.random() * (hi - lo);
		nextClick = now + (long) (1000.0 / cps);
		Targets.hit(mc, ehr.getEntity());
	}
}
