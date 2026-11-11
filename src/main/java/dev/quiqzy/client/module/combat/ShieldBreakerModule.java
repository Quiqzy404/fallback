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
import dev.quiqzy.client.util.Targets;

/** Shield Breaker: zielst du auf einen blockenden Gegner, wechselt es kurz auf die Axt, schlägt (bricht das Schild) und zurück. */
public final class ShieldBreakerModule extends Module {
	public final Setting.Int cooldown = add(new Setting.Int("cooldown", "Charge", 90, 0, 100, " %"));
	public final Setting.Bool onlyAttack = add(new Setting.Bool("only_attack", "Only While Clicking", true));

	private int restore = -1;

	public ShieldBreakerModule() {
		super("shield_breaker", "Shield Breaker", "Swaps to an axe to break a blocking enemy's shield", Category.COMBAT);
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null) return;
		if (restore >= 0) {
			p.getInventory().setSelectedSlot(restore);
			restore = -1;
		}
		if (mc.screen != null) return;
		if (onlyAttack.on() && !InputUtil.isPhysicallyDown(mc, mc.options.keyAttack)) return;
		if (!(mc.hitResult instanceof EntityHitResult ehr) || !(ehr.getEntity() instanceof LivingEntity le) || !le.isBlocking()) return;
		if (!Targets.cooled(p, cooldown.get())) return;
		if (p.getMainHandItem().is(ItemTags.AXES)) {
			Targets.hit(mc, le);
			return;
		}
		int axe = Targets.hotbar(p, s -> s.is(ItemTags.AXES));
		if (axe < 0) return;
		restore = p.getInventory().getSelectedSlot();
		p.getInventory().setSelectedSlot(axe);
		Targets.hit(mc, le);
	}
}
