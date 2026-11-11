package dev.quiqzy.client.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.Items;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.InputUtil;

/** Auto Shield: hebt automatisch das Schild (Offhand), wenn ein Kristall/Cart/Pfeil in der Nähe gefährlich wird. Senkt beim Angreifen. */
public final class AutoShieldModule extends Module {
	public final Setting.Bool crystals = add(new Setting.Bool("crystals", "Crystals & Carts", true));
	public final Setting.Bool arrows = add(new Setting.Bool("arrows", "Incoming Arrows", true));
	public final Setting.Int range = add(new Setting.Int("range", "Range", 6, 2, 12, " b"));

	private boolean holding;

	public AutoShieldModule() {
		super("auto_shield", "Auto Shield", "Raises your shield against crystals, carts and arrows", Category.COMBAT);
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (holding && mc.options != null) mc.options.keyUse.setDown(false);
		holding = false;
	}

	private boolean threat(Minecraft mc, LocalPlayer p) {
		double r2 = (double) range.get() * range.get();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (crystals.on() && (e instanceof EndCrystal || (e instanceof MinecartTNT m && m.isPrimed())) && e.distanceToSqr(p) <= r2) return true;
			if (arrows.on() && e instanceof AbstractArrow a && a.getDeltaMovement().lengthSqr() > 0.1 && e.distanceToSqr(p) <= 100
					&& e.getDeltaMovement().dot(p.position().subtract(e.position())) > 0) return true;
		}
		return false;
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.screen != null) return;
		boolean hasShield = p.getOffhandItem().is(Items.SHIELD) || p.getMainHandItem().is(Items.SHIELD);
		boolean attacking = InputUtil.isPhysicallyDown(mc, mc.options.keyAttack);
		boolean want = hasShield && !attacking && threat(mc, p);
		if (want && !holding) {
			mc.options.keyUse.setDown(true);
			holding = true;
		} else if (!want && holding) {
			mc.options.keyUse.setDown(false);
			holding = false;
		}
	}

}
