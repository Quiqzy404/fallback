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

/** Surround: setzt Obsidian um deine Füße (4 Seiten + darunter), damit dich Kristalle kaum treffen. */
public final class SurroundModule extends Module {
	public final Setting.Int perTick = add(new Setting.Int("per_tick", "Blocks / Tick", 2, 1, 4));
	public final Setting.Bool center = add(new Setting.Bool("center", "Center Player", true));
	public final Setting.Bool floor = add(new Setting.Bool("floor", "Fill Floor", true));
	public final Setting.Bool rotate = add(new Setting.Bool("rotate", "Rotate", true));
	public final Setting.Bool disableOnMove = add(new Setting.Bool("disable_move", "Disable When Moving", true));

	private BlockPos start;

	public SurroundModule() {
		super("surround", "Surround", "Places obsidian around your feet", Category.COMBAT);
	}

	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		start = mc.player == null ? null : mc.player.blockPosition();
		if (mc.player != null && center.on()) {
			// in die Blockmitte schieben, damit kein Block wegen der Hitbox scheitert
			mc.player.setPos(start.getX() + 0.5, mc.player.getY(), start.getZ() + 0.5);
			mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0);
		}
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || start == null) return;
		if (disableOnMove.on() && !p.blockPosition().equals(start) && p.blockPosition().getY() == start.getY()) {
			setEnabled(false);
			return;
		}
		int slot = Targets.hotbar(p, s -> s.is(Items.OBSIDIAN));
		if (slot < 0) return;
		BlockPos feet = p.blockPosition();
		BlockPos[] targets = floor.on()
				? new BlockPos[]{feet.below(), feet.north(), feet.south(), feet.east(), feet.west()}
				: new BlockPos[]{feet.north(), feet.south(), feet.east(), feet.west()};
		int prev = p.getInventory().getSelectedSlot(), placed = 0;
		for (BlockPos t : targets) {
			if (placed >= perTick.get()) break;
			if (!Place.canPlace(mc, t)) continue;
			p.getInventory().setSelectedSlot(slot);
			if (Place.at(mc, t, rotate.on())) placed++;
		}
		p.getInventory().setSelectedSlot(prev);
	}
}
