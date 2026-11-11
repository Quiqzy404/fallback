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
 * Anchor Aura: setzt neben einen Gegner einen Respawn Anchor, lädt ihn mit Glowstone, setzt (Safe) einen Schutzblock
 * vor dich und sprengt ihn. Braucht Anchor + Glowstone in der Hotbar. Explodiert nicht im Nether.
 */
public final class AnchorAuraModule extends Module {
	public final Setting.Int range = add(new Setting.Int("range", "Target Range", 5, 2, 6, " b"));
	public final Setting.Int delay = add(new Setting.Int("delay", "Step Delay", 2, 0, 10, " t"));
	public final Setting.Bool safe = add(new Setting.Bool("safe", "Safe Anchor", true));
	public final Setting.Bool rotate = add(new Setting.Bool("rotate", "Rotate", true));
	public final Setting.Bool players = add(new Setting.Bool("players", "Players", true));
	public final Setting.Bool mobs = add(new Setting.Bool("mobs", "Mobs", false));
	public final Setting.Int minHealth = add(new Setting.Int("min_health", "Pause Below Health", 6, 0, 20, " HP"));

	private enum Step { FIND, CHARGE, SHIELD, BOOM }

	private Step step = Step.FIND;
	private BlockPos anchor;
	private int wait;

	public AnchorAuraModule() {
		super("anchor_aura", "Anchor Aura", "Places, charges and blows up anchors next to enemies", Category.COMBAT);
	}

	@Override
	protected void onEnable() {
		step = Step.FIND;
		anchor = null;
	}

	private BlockHitResult hitOn(BlockPos pos) {
		return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || mc.level == null || mc.screen != null) return;
		if (mc.level.dimension() == net.minecraft.world.level.Level.NETHER) return; // dort explodiert nichts
		if (p.getHealth() + p.getAbsorptionAmount() < minHealth.get()) return;
		if (wait-- > 0) return;
		int anchorSlot = Targets.hotbar(p, s -> s.is(Items.RESPAWN_ANCHOR));
		int glowSlot = Targets.hotbar(p, s -> s.is(Items.GLOWSTONE));
		if (glowSlot < 0) return;
		int prev = p.getInventory().getSelectedSlot();
		switch (step) {
			case FIND -> {
				if (anchorSlot < 0) return;
				List<LivingEntity> ts = Targets.find(mc, range.get() + 1, 360, players.on(), mobs.on(), mobs.on(), false);
				if (ts.isEmpty()) return;
				LivingEntity t = ts.get(0);
				// freier Platz direkt neben dem Ziel (auf Fußhöhe), der nicht in dir liegt
				BlockPos tf = t.blockPosition(), best = null;
				double bd = Double.MAX_VALUE;
				for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
					BlockPos c = tf.relative(d);
					if (!Place.canPlace(mc, c) || p.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(c))) continue;
					double dist = p.getEyePosition().distanceTo(Vec3.atCenterOf(c));
					if (dist > p.blockInteractionRange()) continue;
					// lieber die Seite vom Ziel, die von dir weg zeigt (Ziel steht zwischen dir und Anchor)
					double score = -dist;
					if (score < bd) {
						bd = score;
						best = c;
					}
				}
				if (best == null) return;
				p.getInventory().setSelectedSlot(anchorSlot);
				boolean ok = Place.at(mc, best, rotate.on());
				p.getInventory().setSelectedSlot(prev);
				if (ok) {
					anchor = best;
					step = Step.CHARGE;
					wait = delay.get();
				}
			}
			case CHARGE -> {
				if (anchor == null || !mc.level.getBlockState(anchor).is(Blocks.RESPAWN_ANCHOR)) {
					step = Step.FIND;
					return;
				}
				if (mc.level.getBlockState(anchor).getValue(RespawnAnchorBlock.CHARGE) == 0) {
					p.getInventory().setSelectedSlot(glowSlot);
					mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, hitOn(anchor));
					p.swing(InteractionHand.MAIN_HAND);
					p.getInventory().setSelectedSlot(prev);
				}
				step = safe.on() ? Step.SHIELD : Step.BOOM;
				wait = delay.get();
			}
			case SHIELD -> {
				AnchorUtil.placeShield(mc, anchor, rotate.on());
				step = Step.BOOM;
				wait = delay.get();
			}
			case BOOM -> {
				if (anchor != null && mc.level.getBlockState(anchor).is(Blocks.RESPAWN_ANCHOR)) {
					int slot = Targets.hotbar(p, s -> s.is(Items.TOTEM_OF_UNDYING));
					if (slot < 0) slot = Targets.hotbar(p, s -> s.isEmpty());
					if (slot < 0) slot = Targets.hotbar(p, s -> !s.is(Items.GLOWSTONE));
					if (slot >= 0) {
						p.getInventory().setSelectedSlot(slot);
						mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, hitOn(anchor));
						p.swing(InteractionHand.MAIN_HAND);
						p.getInventory().setSelectedSlot(prev);
					}
				}
				anchor = null;
				step = Step.FIND;
				wait = delay.get() + 2;
			}
		}
	}
}
