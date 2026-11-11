package dev.quiqzy.client.util;

import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/** Hält einen Hotbar-Slot aus dem restlichen Inventar gefüllt – ohne das Inventar zu öffnen. */
public final class Refill {
	private Refill() {
	}

	/**
	 * @param hotbar 0..8 (Slot in der Hotbar)
	 * @param match  welches Item nachgelegt werden soll
	 * @return true, wenn ein Swap-Klick gesendet wurde
	 */
	public static boolean keep(Minecraft mc, int hotbar, Predicate<ItemStack> match) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null) return false;
		if (mc.screen != null && !(mc.screen instanceof InventoryScreen)) return false;
		if (hotbar < 0 || hotbar > 8) return false;
		ItemStack cur = p.getInventory().getItem(hotbar);
		if (match.test(cur) && cur.getCount() > 1) return false; // schon genug da
		for (int i = 9; i < 36; i++) {
			if (match.test(p.getInventory().getItem(i))) {
				mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, i, hotbar, ClickType.SWAP, p);
				return true;
			}
		}
		return false;
	}
}
