package dev.quiqzy.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Block an eine Stelle setzen: sucht einen festen Nachbarn und klickt auf dessen Seite. */
public final class Place {
	private Place() {
	}

	public static boolean canPlace(Minecraft mc, BlockPos pos) {
		return mc.level.getBlockState(pos).canBeReplaced() && mc.level.isUnobstructed(
				net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState(), pos, net.minecraft.world.phys.shapes.CollisionContext.empty());
	}

	/** Setzt den Block mit dem Item im aktuellen Slot. rotate = Blick kurz zum Klickpunkt drehen (manche Server prüfen das). */
	public static boolean at(Minecraft mc, BlockPos pos, boolean rotate) {
		LocalPlayer p = mc.player;
		if (!canPlace(mc, pos)) return false;
		for (Direction d : Direction.values()) {
			BlockPos n = pos.relative(d);
			var st = mc.level.getBlockState(n);
			if (st.isAir() || st.canBeReplaced()) continue;
			Direction face = d.getOpposite();
			Vec3 hit = Vec3.atCenterOf(n).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
			if (p.getEyePosition().distanceTo(hit) > p.blockInteractionRange()) continue;
			float yaw = p.getYRot(), pitch = p.getXRot();
			if (rotate) {
				float[] a = Targets.angles(p, hit);
				p.setYRot(a[0]);
				p.setXRot(a[1]);
			}
			InteractionResult r = mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, n, false));
			if (rotate) {
				p.setYRot(yaw);
				p.setXRot(pitch);
			}
			if (r.consumesAction()) {
				p.swing(InteractionHand.MAIN_HAND);
				return true;
			}
		}
		return false;
	}
}
