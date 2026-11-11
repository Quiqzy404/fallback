package dev.quiqzy.client.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Auto Armor: zieht automatisch die beste Rüstung aus deinem Inventar an. */
public final class AutoArmorModule extends Module {
	public final Setting.Int delay = add(new Setting.Int("delay", "Delay", 4, 1, 20, " t"));
	public final Setting.Bool keepElytra = add(new Setting.Bool("keep_elytra", "Keep Elytra", true));

	private int timer;
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	public AutoArmorModule() {
		super("auto_armor", "Auto Armor", "Puts on your best armor automatically", Category.COMBAT);
	}

	private static int rank(ItemStack s) {
		if (s.isEmpty()) return 0;
		String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
		int r = id.startsWith("netherite") ? 60 : id.startsWith("diamond") ? 50 : id.startsWith("iron") ? 40
				: id.startsWith("chainmail") || id.startsWith("turtle") ? 30 : id.startsWith("golden") ? 20 : id.startsWith("leather") ? 10 : 5;
		return r + (s.isEnchanted() ? 3 : 0);
	}

	private static EquipmentSlot slotOf(ItemStack s) {
		var eq = s.get(DataComponents.EQUIPPABLE);
		return eq == null ? null : eq.slot();
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null) return;
		if (mc.screen != null && !(mc.screen instanceof InventoryScreen)) return;
		if (++timer < delay.get()) return;
		timer = 0;
		var menu = p.inventoryMenu;
		for (int a = 0; a < 4; a++) {
			EquipmentSlot slot = SLOTS[a];
			ItemStack worn = p.getItemBySlot(slot);
			if (slot == EquipmentSlot.CHEST && keepElytra.on() && worn.is(Items.ELYTRA)) continue;
			int bestRank = rank(worn), bestIdx = -1;
			for (int i = 9; i <= 44; i++) {
				ItemStack s = menu.getSlot(i).getItem();
				if (slotOf(s) != slot || s.is(Items.ELYTRA)) continue;
				if (rank(s) > bestRank) {
					bestRank = rank(s);
					bestIdx = i;
				}
			}
			if (bestIdx < 0) continue;
			int armorSlot = 5 + a;
			if (worn.isEmpty()) {
				mc.gameMode.handleInventoryMouseClick(menu.containerId, bestIdx, 0, ClickType.QUICK_MOVE, p);
			} else {
				mc.gameMode.handleInventoryMouseClick(menu.containerId, bestIdx, 0, ClickType.PICKUP, p);
				mc.gameMode.handleInventoryMouseClick(menu.containerId, armorSlot, 0, ClickType.PICKUP, p);
				mc.gameMode.handleInventoryMouseClick(menu.containerId, bestIdx, 0, ClickType.PICKUP, p);
			}
			return; // ein Teil pro Durchgang
		}
	}
}
