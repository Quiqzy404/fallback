package dev.quiqzy.client.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Items;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.InputUtil;

/** Fast Bow: feuert den Bogen bei voller Spannung sofort ab und spannt gleich neu – schnelles Nachschießen. */
public final class FastBowModule extends Module {
	public final Setting.Int charge = add(new Setting.Int("charge", "Charge Ticks", 20, 6, 20, " t"));

	public FastBowModule() {
		super("fast_bow", "Fast Bow", "Fires the bow at full draw and redraws instantly", Category.COMBAT);
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || mc.screen != null) return;
		if (!p.getUseItem().is(Items.BOW) || !p.isUsingItem()) return;
		if (p.getTicksUsingItem() >= charge.get()) {
			mc.gameMode.releaseUsingItem(p); // abfeuern
			// gehaltener Rechtsklick spannt automatisch neu
			if (InputUtil.isPhysicallyDown(mc, mc.options.keyUse)) mc.options.keyUse.setDown(true);
		}
	}
}
