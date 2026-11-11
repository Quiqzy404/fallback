package dev.quiqzy.client.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.Targets;

/** Auto Pot: wirft bei niedrigen Leben einen Heiltrank-Splash vor deine Füße. */
public final class AutoPotModule extends Module {
	public final Setting.Int health = add(new Setting.Int("health", "Health", 10, 2, 20, " HP"));
	public final Setting.Int delay = add(new Setting.Int("delay", "Delay", 20, 5, 60, " t"));

	private int cooldown;
	private int restore = -1;
	private int prevSlot;
	private float prevPitch;

	public AutoPotModule() {
		super("auto_pot", "Auto Pot", "Throws a healing splash potion when low", Category.COMBAT);
	}

	private static boolean healing(ItemStack s) {
		if (!s.is(Items.SPLASH_POTION)) return false;
		var c = s.get(DataComponents.POTION_CONTENTS);
		if (c == null) return false;
		for (var e : c.getAllEffects()) if (e.getEffect().equals(MobEffects.INSTANT_HEALTH)) return true;
		return false;
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null) return;
		if (restore >= 0) {
			p.getInventory().setSelectedSlot(prevSlot);
			p.setXRot(prevPitch);
			restore = -1;
		}
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		if (mc.screen != null || p.getHealth() > health.get()) return;
		int slot = Targets.hotbar(p, AutoPotModule::healing);
		if (slot < 0) return;
		prevSlot = p.getInventory().getSelectedSlot();
		prevPitch = p.getXRot();
		p.getInventory().setSelectedSlot(slot);
		p.setXRot(90f);
		mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
		restore = 1;
		cooldown = delay.get();
	}
}
