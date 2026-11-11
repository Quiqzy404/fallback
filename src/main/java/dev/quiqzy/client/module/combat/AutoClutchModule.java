package dev.quiqzy.client.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.Targets;

/** Auto Clutch: setzt beim Fallen einen Wassereimer unter dich, um Fallschaden zu verhindern. */
public final class AutoClutchModule extends Module {
	public final Setting.Int minFall = add(new Setting.Int("min_fall", "Min Fall", 5, 3, 30, " b"));

	private int restore = -1;
	private float rYaw, rPitch;

	public AutoClutchModule() {
		super("auto_clutch", "Auto Clutch", "Places water below you when falling", Category.COMBAT);
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null) return;
		if (restore >= 0) {
			p.getInventory().setSelectedSlot(restore);
			p.setYRot(rYaw);
			p.setXRot(rPitch);
			restore = -1;
		}
		if (mc.screen != null || p.onGround() || p.getDeltaMovement().y > -0.3 || p.fallDistance < minFall.get()) return;
		if (p.isInWater() || p.isInLava()) return;
		int slot = Targets.hotbar(p, s -> s.is(Items.WATER_BUCKET));
		if (slot < 0) return;
		restore = p.getInventory().getSelectedSlot();
		rYaw = p.getYRot();
		rPitch = p.getXRot();
		p.getInventory().setSelectedSlot(slot);
		p.setXRot(90f);
		mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
		p.swing(InteractionHand.MAIN_HAND);
	}
}
