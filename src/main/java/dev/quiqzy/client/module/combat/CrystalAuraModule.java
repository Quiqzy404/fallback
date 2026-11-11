package dev.quiqzy.client.module.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.InputUtil;

/** Crystal Aura: setzt Kristalle schnell auf Obsidian/Bedrock (rechts halten) und zerschlägt sie sofort. */
public final class CrystalAuraModule extends Module {
	public final Setting.Bool autoPlace = add(new Setting.Bool("place", "Fast Place", true));
	public final Setting.Bool autoBreak = add(new Setting.Bool("break", "Auto Break", true));
	public final Setting.Int range = add(new Setting.Int("range", "Break Range", 4, 2, 6, " b"));
	public final Setting.Int breakDelay = add(new Setting.Int("break_delay", "Break Delay", 0, 0, 10, " t"));
	public final Setting.Int placeDelay = add(new Setting.Int("place_delay", "Place Delay", 0, 0, 10, " t"));

	private int breakTimer, placeTimer;

	public CrystalAuraModule() {
		super("crystal_aura", "Crystal Aura", "Fast crystal placing and instant breaking", Category.COMBAT);
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.screen != null || mc.gameMode == null) return;
		boolean crystal = p.getMainHandItem().is(Items.END_CRYSTAL) || p.getOffhandItem().is(Items.END_CRYSTAL);
		if (!crystal) return;
		if (autoBreak.on() && breakTimer-- <= 0) {
			Entity best = null;
			if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof EndCrystal) best = ehr.getEntity();
			if (best == null) {
				double bd = range.get();
				for (Entity e : mc.level.entitiesForRendering()) {
					if (e instanceof EndCrystal && e.distanceTo(p) <= bd && p.hasLineOfSight(e)) {
						bd = e.distanceTo(p);
						best = e;
					}
				}
			}
			if (best != null) {
				mc.gameMode.attack(p, best);
				p.swing(InteractionHand.MAIN_HAND);
				breakTimer = breakDelay.get();
			}
		}
		if (autoPlace.on() && placeTimer-- <= 0 && InputUtil.isPhysicallyDown(mc, mc.options.keyUse)
				&& mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
			BlockHitResult bhr = (BlockHitResult) mc.hitResult;
			var st = mc.level.getBlockState(bhr.getBlockPos());
			if ((st.is(Blocks.OBSIDIAN) || st.is(Blocks.BEDROCK)) && mc.level.isEmptyBlock(bhr.getBlockPos().above())) {
				InteractionHand hand = p.getMainHandItem().is(Items.END_CRYSTAL) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
				mc.gameMode.useItemOn(p, hand, bhr);
				p.swing(hand);
				placeTimer = placeDelay.get();
			}
		}
	}
}
