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

/** Key Pearl: Taste drücken = Perle werfen und sofort zurück auf dein Item. Leg dir einen Keybind drauf. */
public final class KeyPearlModule extends Module {
	public KeyPearlModule() {
		super("key_pearl", "Key Pearl", "Throws a pearl on keypress and switches back", Category.COMBAT);
	}

	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p != null && mc.gameMode != null) {
			int slot = Targets.hotbar(p, s -> s.is(Items.ENDER_PEARL));
			if (slot >= 0) {
				int prev = p.getInventory().getSelectedSlot();
				p.getInventory().setSelectedSlot(slot);
				mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
				p.swing(InteractionHand.MAIN_HAND);
				p.getInventory().setSelectedSlot(prev);
			}
		}
		setEnabled(false); // einmalige Aktion
	}
}
