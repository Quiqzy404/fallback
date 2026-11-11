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

/** Trigger Bot: schlägt, sobald dein Fadenkreuz auf einem Ziel liegt und der Schlag aufgeladen ist. */
public final class TriggerBotModule extends Module {
	public final Setting.Int cooldown = add(new Setting.Int("cooldown", "Charge", 95, 0, 100, " %"));
	public final Setting.Int minDelay = add(new Setting.Int("min_delay", "Min Delay", 0, 0, 10, " t"));
	public final Setting.Int maxDelay = add(new Setting.Int("max_delay", "Max Delay", 2, 0, 10, " t"));
	public final Setting.Bool onlyCrits = add(new Setting.Bool("only_crits", "Only Crits When Falling", false));
	public final Setting.Bool players = add(new Setting.Bool("players", "Players", true));
	public final Setting.Bool mobs = add(new Setting.Bool("mobs", "Mobs", true));
	public final Setting.Bool animals = add(new Setting.Bool("animals", "Animals", false));
	public final Setting.Bool holdAttack = add(new Setting.Bool("hold", "Only While Holding Attack", false));

	private int wait = -1;

	public TriggerBotModule() {
		super("trigger_bot", "Trigger Bot", "Attacks when your crosshair is on a target", Category.COMBAT);
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.screen != null || p.isUsingItem()) return;
		if (holdAttack.on() && !InputUtil.isPhysicallyDown(mc, mc.options.keyAttack)) return;
		if (!(mc.hitResult instanceof EntityHitResult ehr) || !Targets.valid(p, ehr.getEntity(), players.on(), mobs.on(), animals.on())) {
			wait = -1;
			return;
		}
		if (!Targets.cooled(p, cooldown.get())) return;
		if (onlyCrits.on() && !p.onGround() && p.fallDistance <= 0f) return;
		if (wait < 0) {
			int lo = Math.min(minDelay.get(), maxDelay.get()), hi = Math.max(minDelay.get(), maxDelay.get());
			wait = lo + (int) (Math.random() * (hi - lo + 1));
		}
		if (wait-- > 0) return;
		wait = -1;
		Targets.hit(mc, ehr.getEntity());
	}
}
