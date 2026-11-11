package dev.quiqzy.client.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.InputUtil;

/** Auto Drain: hämmert mit einer Axt viele schnelle Schläge auf ein blockendes Schild, bis es bricht. */
public final class AutoDrainModule extends Module {
	public final Setting.Int hitsPerTick = add(new Setting.Int("hits", "Hits / Tick", 8, 1, 20));
	public final Setting.Bool axeOnly = add(new Setting.Bool("axe_only", "Axe Only", true));
	public final Setting.Bool holdAttack = add(new Setting.Bool("hold", "Only While Clicking", true));

	public AutoDrainModule() {
		super("auto_drain", "Auto Drain", "Rapidly hits a blocking shield until it breaks", Category.COMBAT);
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || mc.screen != null) return;
		if (holdAttack.on() && !InputUtil.isPhysicallyDown(mc, mc.options.keyAttack)) return;
		if (axeOnly.on() && !p.getMainHandItem().is(ItemTags.AXES)) return;
		if (!(mc.hitResult instanceof EntityHitResult ehr) || !(ehr.getEntity() instanceof LivingEntity le) || !le.isBlocking()) return;
		for (int i = 0; i < hitsPerTick.get(); i++) {
			mc.gameMode.attack(p, le);
		}
		p.swing(InteractionHand.MAIN_HAND);
	}
}
