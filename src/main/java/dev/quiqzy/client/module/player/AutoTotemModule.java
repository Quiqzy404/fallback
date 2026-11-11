package dev.quiqzy.client.module.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Auto Totem: legt automatisch ein Totem aus dem Inventar in die Offhand (wie Taste F im Inventar). */
public final class AutoTotemModule extends Module {
	/** Button 40 bei ClickType.SWAP = mit der Offhand tauschen. */
	private static final int OFFHAND_BUTTON = 40;

	public final Setting.Int delay = add(new Setting.Int("delay", "Delay", 2, 0, 20, " t"));
	public final Setting.Bool replace = add(new Setting.Bool("replace", "Replace Other Items", true));

	private int cooldown;

	public AutoTotemModule() {
		super("auto_totem", "Auto Totem", "Keeps a totem in your offhand", Category.COMBAT);
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || mc.screen != null) return;
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		if (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
		if (!p.getOffhandItem().isEmpty() && !replace.on()) return;

		Inventory inv = p.getInventory();
		int slot = -1;
		for (int i = 0; i < 36; i++) {
			if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
				slot = i;
				break;
			}
		}
		if (slot == -1) return;

		int menuSlot = slot < 9 ? 36 + slot : slot; // Hotbar liegt im Inventar-Menü bei 36–44
		mc.gameMode.handleInventoryMouseClick(p.inventoryMenu.containerId, menuSlot, OFFHAND_BUTTON, ClickType.SWAP, p);
		cooldown = delay.get();
	}
}
