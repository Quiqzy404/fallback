package dev.quiqzy.client.module.combat;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/**
 * Instant Launch – Attribut-Swap + Linksklick: auf Tastendruck (Keybind im GUI) sofort auf das
 * gewählte Item ("Spear") wechseln, den Server über den Wechsel informieren und einen ECHTEN
 * Linksklick auslösen (Minecraft schlägt den Gegner im Fadenkreuz, sonst Luft-Swing für die
 * Launch-Fähigkeit). Danach zurück auf den vorherigen Slot.
 *
 * Keybind: im GUI neben "Instant Launch" auf das Keybind-Feld klicken und eine Taste drücken.
 */
public final class InstantLaunchModule extends Module {
	/** Das "Spear"-Item (frei wählbar, mehrere möglich – das erste in der Hotbar wird genommen). */
	public final Setting.Items items = add(new Setting.Items("items", "Launch Item"));
	/** Nach dem Schlag zurück auf den vorherigen Slot. */
	public final Setting.Bool swapBack = add(new Setting.Bool("swap_back", "Swap Back", true));

	private int prevSlot = -1;
	private int swapBackIn = 0;

	private Set<Item> itemSet = new HashSet<>();
	private int itemsVersion = -1;

	public InstantLaunchModule() {
		super("instant_launch", "Instant Launch", "Attribute-swap to the spear and left-click", Category.COMBAT);
	}

	@Override
	protected void onDisable() {
		swapBackIn = 0;
		prevSlot = -1;
	}

	/** Vom Keybind aufgerufen: auf den Spear wechseln und einen echten Linksklick auslösen. */
	public void launch(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || mc.screen != null) return;

		Inventory inv = p.getInventory();
		int slot = findSlot(inv);
		if (slot == -1) {
			msg(p, "Instant Launch: no launch item in hotbar", true);
			return;
		}

		prevSlot = inv.getSelectedSlot();
		inv.setSelectedSlot(slot);                     // Attribut-Swap auf den Spear
		sendSlot(mc, slot);                            // Server sofort informieren (auch für Luft-Linksklick)

		// Echten Linksklick auslösen: Minecraft verarbeitet ihn im nächsten Tick
		// (Gegner im Fadenkreuz schlagen ODER Luft-Swing -> Launch-Fähigkeit).
		KeyMapping.click(KeyBindingHelper.getBoundKeyOf(mc.options.keyAttack));
		msg(p, "⚡ Instant Launch", false);

		swapBackIn = swapBack.on() ? 4 : 0;            // ein paar Ticks warten, dann zurück
	}

	@Override
	public void onTick(Minecraft mc) {
		if (swapBackIn > 0 && --swapBackIn == 0 && prevSlot != -1 && mc.player != null) {
			mc.player.getInventory().setSelectedSlot(prevSlot);
			sendSlot(mc, prevSlot);
			prevSlot = -1;
		}
	}

	private static void sendSlot(Minecraft mc, int slot) {
		ClientPacketListener c = mc.getConnection();
		if (c != null) c.send(new ServerboundSetCarriedItemPacket(slot));
	}

	private int findSlot(Inventory inv) {
		for (int i = 0; i < 9; i++) {
			if (isLaunchItem(inv.getItem(i))) return i;
		}
		return -1;
	}

	private boolean isLaunchItem(ItemStack stack) {
		int v = items.version();
		if (v != itemsVersion) {
			Set<Item> set = new HashSet<>();
			for (String id : items.get()) {
				Identifier key = Identifier.tryParse(id);
				if (key != null) BuiltInRegistries.ITEM.getOptional(key).ifPresent(set::add);
			}
			itemSet = set;
			itemsVersion = v;
		}
		return !itemSet.isEmpty() && itemSet.contains(stack.getItem());
	}

	private static void msg(LocalPlayer p, String text, boolean warn) {
		p.displayClientMessage(Component.literal(text).withStyle(warn ? ChatFormatting.RED : ChatFormatting.AQUA), true);
	}
}
