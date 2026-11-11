package dev.quiqzy.client.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.Targets;

/** Auto Pearl: wirft bei niedrigen Leben automatisch eine Enderperle (zum Rausporten). */
public final class AutoPearlModule extends Module {
	public final Setting.Int health = add(new Setting.Int("health", "Health", 8, 1, 20, " HP"));
	public final Setting.Int pitch = add(new Setting.Int("pitch", "Throw Pitch", -40, -90, 20, "°"));
	public final Setting.Int cooldown = add(new Setting.Int("cooldown", "Cooldown", 40, 10, 200, " t"));

	private int cd, restore = -1;
	private float restorePitch;

	public AutoPearlModule() {
		super("auto_pearl", "Auto Pearl", "Throws an ender pearl when your health is low", Category.COMBAT);
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null) return;
		if (restore >= 0) {
			p.getInventory().setSelectedSlot(restore);
			p.setXRot(restorePitch);
			restore = -1;
		}
		if (cd > 0) { cd--; return; }
		if (mc.screen != null || p.getHealth() + p.getAbsorptionAmount() > health.get()) return;
		int slot = Targets.hotbar(p, s -> s.is(Items.ENDER_PEARL));
		if (slot < 0) return;
		restore = p.getInventory().getSelectedSlot();
		restorePitch = p.getXRot();
		p.getInventory().setSelectedSlot(slot);
		p.setXRot(pitch.get());
		mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
		p.swing(InteractionHand.MAIN_HAND);
		cd = cooldown.get();
	}
}
