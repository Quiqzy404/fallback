package dev.quiqzy.client.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Items;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.Targets;

/** Auto Gap: isst automatisch einen Goldapfel, wenn deine Leben niedrig sind, und wechselt danach zurück. */
public final class AutoGapModule extends Module {
	public final Setting.Int health = add(new Setting.Int("health", "Health", 10, 2, 20, " HP"));
	public final Setting.Bool preferEnchanted = add(new Setting.Bool("enchanted", "Prefer Enchanted", true));

	private int eatTicks = -1;
	private int prevSlot = -1;

	public AutoGapModule() {
		super("auto_gap", "Auto Gap", "Eats a golden apple when your health is low", Category.COMBAT);
	}

	@Override
	protected void onDisable() {
		stop(Minecraft.getInstance());
	}

	private void stop(Minecraft mc) {
		if (eatTicks >= 0 && mc.options != null) mc.options.keyUse.setDown(false);
		if (prevSlot >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(prevSlot);
		eatTicks = -1;
		prevSlot = -1;
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null) return;
		if (eatTicks >= 0) {
			eatTicks++;
			boolean holding = p.getMainHandItem().is(Items.GOLDEN_APPLE) || p.getMainHandItem().is(Items.ENCHANTED_GOLDEN_APPLE);
			if (!holding || eatTicks > 45) stop(mc);
			else mc.options.keyUse.setDown(true);
			return;
		}
		if (mc.screen != null || p.getHealth() + p.getAbsorptionAmount() > health.get()) return;
		int slot = -1;
		if (preferEnchanted.on()) slot = Targets.hotbar(p, s -> s.is(Items.ENCHANTED_GOLDEN_APPLE));
		if (slot < 0) slot = Targets.hotbar(p, s -> s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE));
		if (slot < 0) return;
		prevSlot = p.getInventory().getSelectedSlot();
		p.getInventory().setSelectedSlot(slot);
		mc.options.keyUse.setDown(true);
		eatTicks = 0;
	}
}
