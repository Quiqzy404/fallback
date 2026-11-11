package dev.quiqzy.client.module.misc;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Item Counter: wie viele wichtige Items du dabeihast (Totems, Perlen, Kristalle, Äpfel, Obsidian, XP). */
public final class ItemCounterModule extends Module {
	public final Setting.Bool totems = add(new Setting.Bool("totems", "Totems", true));
	public final Setting.Bool pearls = add(new Setting.Bool("pearls", "Ender Pearls", true));
	public final Setting.Bool crystals = add(new Setting.Bool("crystals", "End Crystals", true));
	public final Setting.Bool gapples = add(new Setting.Bool("gapples", "Golden Apples", true));
	public final Setting.Bool obsidian = add(new Setting.Bool("obsidian", "Obsidian", false));
	public final Setting.Bool xp = add(new Setting.Bool("xp", "XP Bottles", true));
	public final Setting.Bool hideZero = add(new Setting.Bool("hide_zero", "Hide Empty", true));

	public ItemCounterModule() {
		super("item_counter", "Item Counter", "Counts totems, pearls, crystals and more", Category.HUD);
	}

	public record Entry(ItemStack icon, int count) {
	}

	public List<Entry> entries(Inventory inv) {
		List<Entry> out = new ArrayList<>();
		if (totems.on()) add(out, inv, Items.TOTEM_OF_UNDYING);
		if (pearls.on()) add(out, inv, Items.ENDER_PEARL);
		if (crystals.on()) add(out, inv, Items.END_CRYSTAL);
		if (gapples.on()) {
			add(out, inv, Items.ENCHANTED_GOLDEN_APPLE);
			add(out, inv, Items.GOLDEN_APPLE);
		}
		if (obsidian.on()) add(out, inv, Items.OBSIDIAN);
		if (xp.on()) add(out, inv, Items.EXPERIENCE_BOTTLE);
		return out;
	}

	private void add(List<Entry> out, Inventory inv, Item item) {
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item)) n += s.getCount();
		}
		if (n == 0 && hideZero.on()) return;
		out.add(new Entry(new ItemStack(item), n));
	}
}
