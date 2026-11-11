package dev.quiqzy.client.module.combat;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;

/** Auto Weapon: wechselt direkt vor dem Schlag auf die beste Waffe in der Hotbar (Axt bei Schild). */
public final class AutoWeaponModule extends Module {
	public final Setting.Choice prefer = add(new Setting.Choice("prefer", "Prefer", "Sword", "Sword", "Axe", "Mace"));
	public final Setting.Bool axeOnShield = add(new Setting.Bool("axe_on_shield", "Axe vs. Shield", true));

	public AutoWeaponModule() {
		super("auto_weapon", "Auto Weapon", "Switches to your best weapon when you hit", Category.COMBAT);
	}

	public static boolean isWeapon(ItemStack s) {
		return s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(Items.MACE) || s.is(Items.TRIDENT) || s.is(ItemTags.SPEARS);
	}

	private static int score(ItemStack s, String kind) {
		boolean match = switch (kind) {
			case "Axe" -> s.is(ItemTags.AXES);
			case "Mace" -> s.is(Items.MACE);
			default -> s.is(ItemTags.SWORDS);
		};
		if (!match) return -1;
		return s.getMaxDamage() + (s.isEnchanted() ? 5000 : 0); // mehr Haltbarkeit = besseres Material
	}

	public void beforeAttack(LocalPlayer p, Entity target) {
		if (!isEnabled() || !(target instanceof LivingEntity le)) return;
		String kind = axeOnShield.on() && le.isBlocking() ? "Axe" : prefer.get();
		int best = -1, bestScore = -1;
		for (int i = 0; i < 9; i++) {
			int sc = score(p.getInventory().getItem(i), kind);
			if (sc > bestScore) {
				bestScore = sc;
				best = i;
			}
		}
		if (best < 0 && !kind.equals("Sword")) {
			for (int i = 0; i < 9; i++) {
				int sc = score(p.getInventory().getItem(i), "Sword");
				if (sc > bestScore) {
					bestScore = sc;
					best = i;
				}
			}
		}
		if (best >= 0) p.getInventory().setSelectedSlot(best);
	}
}
