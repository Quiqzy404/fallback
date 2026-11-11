package dev.quiqzy.client.module.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.InputUtil;

/** Auto Tool: beim Abbauen automatisch das schnellste Werkzeug aus der Hotbar nehmen. */
public final class AutoToolModule extends Module {
	public final Setting.Bool switchBack = add(new Setting.Bool("switch_back", "Switch Back", true));
	public final Setting.Bool antiBreak = add(new Setting.Bool("anti_break", "Anti Break", true));

	private int previousSlot = -1;

	public AutoToolModule() {
		super("auto_tool", "Auto Tool", "Picks the best tool while mining", Category.PLAYER);
	}

	@Override
	public void onStartTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.level == null || mc.gameMode == null) return;

		boolean mining = mc.screen == null && InputUtil.isPhysicallyDown(mc, mc.options.keyAttack)
				&& mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;

		if (!mining) {
			if (previousSlot != -1 && switchBack.on() && mc.screen == null) p.getInventory().setSelectedSlot(previousSlot);
			previousSlot = -1;
			return;
		}

		BlockState state = mc.level.getBlockState(((BlockHitResult) mc.hitResult).getBlockPos());
		if (state.isAir()) return;

		Inventory inv = p.getInventory();
		int current = inv.getSelectedSlot();
		float best = speed(inv.getItem(current), state);
		int bestSlot = -1;
		for (int i = 0; i < 9; i++) {
			ItemStack s = inv.getItem(i);
			if (antiBreak.on() && almostBroken(s)) continue;
			float sp = speed(s, state);
			if (sp > best + 0.01f) {
				best = sp;
				bestSlot = i;
			}
		}
		if (bestSlot != -1 && bestSlot != current) {
			if (previousSlot == -1) previousSlot = current;
			inv.setSelectedSlot(bestSlot);
		}
	}

	@Override
	protected void onDisable() {
		previousSlot = -1;
	}

	private float speed(ItemStack s, BlockState state) {
		if (antiBreak.on() && almostBroken(s)) return 0f;
		return s.getDestroySpeed(state);
	}

	private static boolean almostBroken(ItemStack s) {
		return s.isDamageableItem() && s.getMaxDamage() - s.getDamageValue() <= 5;
	}
}
