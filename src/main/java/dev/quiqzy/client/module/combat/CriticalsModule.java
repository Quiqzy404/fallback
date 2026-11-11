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

/** Criticals: springt automatisch, wenn du ein Ziel anvisierst und dein Schlag bereit ist – so wird er zum Crit. */
public final class CriticalsModule extends Module {
	public final Setting.Bool holdAttack = add(new Setting.Bool("hold", "Only While Holding Attack", true));
	public final Setting.Int cooldown = add(new Setting.Int("cooldown", "Charge", 90, 50, 100, " %"));

	public CriticalsModule() {
		super("criticals", "Criticals", "Auto-jumps so your hits land as crits", Category.COMBAT);
	}

	@Override
	public void onStartTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.screen != null || !p.onGround() || p.isInWater() || p.isInLava()) return;
		if (holdAttack.on() && !InputUtil.isPhysicallyDown(mc, mc.options.keyAttack)) return;
		if (!(mc.hitResult instanceof EntityHitResult ehr) || !(ehr.getEntity() instanceof LivingEntity)) return;
		if (Targets.cooled(p, cooldown.get())) p.jumpFromGround();
	}
}
