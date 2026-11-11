package dev.quiqzy.client.module.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.InputUtil;

/**
 * Auto Eat: wenn der Hunger unter die Grenze fällt, nimmt es Essen aus der Hotbar, isst es
 * und wechselt danach zurück. Startet nicht, während du klickst (z. B. im Kampf).
 */
public final class AutoEatModule extends Module {
	private static final int TIMEOUT_TICKS = 80;

	public final Setting.Int hunger = add(new Setting.Int("hunger", "Eat Below (Food)", 14, 1, 19));
	public final Setting.Bool goldenApples = add(new Setting.Bool("golden_apples", "Use Golden Apples", false));

	private int previousSlot = -1;
	private int eatTicks = -1; // -1 = isst gerade nicht

	public AutoEatModule() {
		super("auto_eat", "Auto Eat", "Eats from the hotbar when hungry", Category.PLAYER);
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null) return;

		if (eatTicks >= 0) {
			eatTicks++;
			boolean done = (eatTicks > 3 && !p.isUsingItem()) || eatTicks > TIMEOUT_TICKS || mc.screen != null;
			if (done) stop(mc, p);
			else mc.options.keyUse.setDown(true); // weiter festhalten, sonst bricht Minecraft das Essen ab
			return;
		}

		if (mc.screen != null || p.isUsingItem() || p.isCreative() || p.isSpectator()) return;
		if (p.getFoodData().getFoodLevel() > hunger.get() || !p.canEat(false)) return;
		if (InputUtil.isPhysicallyDown(mc, mc.options.keyAttack) || InputUtil.isPhysicallyDown(mc, mc.options.keyUse)) return;

		Inventory inv = p.getInventory();
		int slot = bestFood(inv);
		if (slot == -1) return;

		previousSlot = inv.getSelectedSlot();
		inv.setSelectedSlot(slot);
		mc.gameMode.useItem(p, InteractionHand.MAIN_HAND);
		mc.options.keyUse.setDown(true);
		eatTicks = 0;
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (eatTicks >= 0 && mc.player != null) stop(mc, mc.player);
	}

	private void stop(Minecraft mc, LocalPlayer p) {
		if (!InputUtil.isPhysicallyDown(mc, mc.options.keyUse)) mc.options.keyUse.setDown(false);
		if (previousSlot != -1) p.getInventory().setSelectedSlot(previousSlot);
		previousSlot = -1;
		eatTicks = -1;
	}

	/** Hotbar-Slot mit dem nahrhaftesten "guten" Essen, -1 = keins. */
	private int bestFood(Inventory inv) {
		int best = -1;
		int bestValue = 0;
		for (int i = 0; i < 9; i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty() || isBad(s)) continue;
			FoodProperties food = s.get(DataComponents.FOOD);
			if (food == null) continue;
			if (food.nutrition() > bestValue) {
				bestValue = food.nutrition();
				best = i;
			}
		}
		return best;
	}

	private boolean isBad(ItemStack s) {
		if (s.is(Items.GOLDEN_APPLE) || s.is(Items.ENCHANTED_GOLDEN_APPLE)) return !goldenApples.on();
		return s.is(Items.ROTTEN_FLESH) || s.is(Items.SPIDER_EYE) || s.is(Items.POISONOUS_POTATO)
				|| s.is(Items.PUFFERFISH) || s.is(Items.CHORUS_FRUIT) || s.is(Items.SUSPICIOUS_STEW);
	}
}
