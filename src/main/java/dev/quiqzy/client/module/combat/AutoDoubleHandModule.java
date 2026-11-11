package dev.quiqzy.client.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.Place;
import dev.quiqzy.client.util.Targets;

/** Auto Double Hand: nimmt bei niedrigen Leben oder einem Kristall in der Nähe den Totem in die Haupthand. */
public final class AutoDoubleHandModule extends Module {
	public final Setting.Int health = add(new Setting.Int("health", "Health", 8, 2, 20, " HP"));
	public final Setting.Bool onCrystal = add(new Setting.Bool("on_crystal", "When Crystal Nearby", true));
	public final Setting.Int crystalRange = add(new Setting.Int("crystal_range", "Crystal Range", 4, 2, 8, " b"));
	public final Setting.Bool refill = add(new Setting.Bool("refill", "Refill Hotbar Totem", true));
	public final Setting.Int refillSlot = add(new Setting.Int("refill_slot", "Totem Slot", 9, 1, 9));

	private int refillCooldown;

	public AutoDoubleHandModule() {
		super("auto_double_hand", "Auto Double Hand", "Switches to a hotbar totem when you're in danger", Category.COMBAT);
	}

	private boolean danger(Minecraft mc, LocalPlayer p) {
		if (p.getHealth() + p.getAbsorptionAmount() <= health.get()) return true;
		if (!onCrystal.on()) return false;
		double r2 = (double) crystalRange.get() * crystalRange.get();
		for (var e : mc.level.entitiesForRendering()) {
			if (e instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal && e.distanceToSqr(p) <= r2) return true;
		}
		return false;
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null) return;
		// Hotbar-Totem nachfüllen (aus dem Inventar in den gewählten Slot)
		if (refill.on() && mc.screen == null && --refillCooldown <= 0) {
			int hs = refillSlot.get() - 1;
			if (!p.getInventory().getItem(hs).is(Items.TOTEM_OF_UNDYING)) {
				for (int i = 9; i < 36; i++) {
					if (p.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) {
						mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, i, hs, net.minecraft.world.inventory.ClickType.SWAP, p);
						refillCooldown = 5;
						break;
					}
				}
			}
		}
		if (p.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || !danger(mc, p)) return;
		int slot = Targets.hotbar(p, s -> s.is(Items.TOTEM_OF_UNDYING));
		if (slot >= 0) p.getInventory().setSelectedSlot(slot);
	}
}
