package dev.quiqzy.client.module.combat;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.AnchorUtil;
import dev.quiqzy.client.util.InputUtil;
import dev.quiqzy.client.util.Place;
import dev.quiqzy.client.util.Targets;

/**
 * Hit Crystal: Rechtsklick mit Obsidian auf einen Block -> Obsidian setzen, Kristall draufsetzen, Kristall schlagen.
 * Hast du schon auf Obsidian/Bedrock gezielt, wird nur der Kristall gesetzt und geschlagen.
 */
public final class HitCrystalModule extends Module {
	public final Setting.Int delay = add(new Setting.Int("delay", "Step Delay", 1, 0, 6, " t"));
	public final Setting.Bool autoHit = add(new Setting.Bool("auto_hit", "Hit Crystal", true));

	private int stepTick = -1;
	private BlockPos base;
	private int prevSlot;

	public HitCrystalModule() {
		super("hit_crystal", "Hit Crystal", "Right-click with obsidian: obsidian, crystal, hit", Category.COMBAT);
	}

	@Override
	public void onStartTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || mc.screen != null) return;
		if (stepTick >= 0) {
			while (mc.options.keyUse.consumeClick()) {
			}
			if (stepTick-- > 0) return;
			run(mc, p);
			return;
		}
		if (!p.getMainHandItem().is(Items.OBSIDIAN)) return;
		if (!(mc.hitResult instanceof BlockHitResult bhr) || bhr.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return;
		boolean clicked = mc.options.keyUse.consumeClick() || testClick;
		testClick = false;
		if (!clicked) return;
		while (mc.options.keyUse.consumeClick()) {
		}
		prevSlot = p.getInventory().getSelectedSlot();
		var st = mc.level.getBlockState(bhr.getBlockPos());
		if (st.is(Blocks.OBSIDIAN) || st.is(Blocks.BEDROCK)) {
			base = bhr.getBlockPos();
		} else {
			mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, bhr);
			p.swing(InteractionHand.MAIN_HAND);
			base = bhr.getBlockPos().relative(bhr.getDirection());
		}
		phase = 1;
		stepTick = delay.get();
	}

	private int phase;
	/** Selbsttest: einen Rechtsklick vortäuschen. */
	public boolean testClick;

	private void run(Minecraft mc, LocalPlayer p) {
		if (phase == 1) {
			int c = Targets.hotbar(p, s -> s.is(Items.END_CRYSTAL));
			if (c < 0 || base == null) {
				finish(p);
				return;
			}
			p.getInventory().setSelectedSlot(c);
			mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(base).add(0, 0.5, 0), Direction.UP, base, false));
			p.swing(InteractionHand.MAIN_HAND);
			phase = 2;
			stepTick = delay.get();
			if (!autoHit.on()) finish(p);
			return;
		}
		// Kristall über dem Block schlagen
		for (var e : mc.level.entitiesForRendering()) {
			if (e instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal && e.blockPosition().equals(base.above())) {
				mc.gameMode.attack(p, e);
				p.swing(InteractionHand.MAIN_HAND);
				break;
			}
		}
		finish(p);
	}

	private void finish(LocalPlayer p) {
		p.getInventory().setSelectedSlot(prevSlot);
		stepTick = -1;
		phase = 0;
	}
}
