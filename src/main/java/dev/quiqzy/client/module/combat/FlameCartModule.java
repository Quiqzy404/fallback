package dev.quiqzy.client.module.combat;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.Place;
import dev.quiqzy.client.util.Targets;

/**
 * Flame Cart: schießt du mit einem Flammen-Bogen, wird die Flugbahn des Pfeils berechnet und am Einschlagpunkt
 * eine Schiene + TNT-Minecart gesetzt – ohne dass du dorthin schaust. Der Flammenpfeil zündet den Cart beim Aufprall.
 * Braucht Schiene + TNT-Minecart in der Hotbar. Platzieren außer Reichweite klappt nur auf Servern ohne Reichweiten-Check.
 */
public final class FlameCartModule extends Module {
	public final Setting.Int maxDist = add(new Setting.Int("max_dist", "Max Distance", 48, 8, 160, " b"));
	public final Setting.Bool anyArrow = add(new Setting.Bool("any_arrow", "Any Arrow (not only flame)", false));
	public final Setting.Bool hitIgnite = add(new Setting.Bool("hit_ignite", "Hit Ignite Fallback", false));
	public final Setting.Bool refill = add(new Setting.Bool("refill", "Cart Refill", true));
	public final Setting.Int cartHotbar = add(new Setting.Int("cart_slot", "Cart Slot", 2, 1, 9));
	public final Setting.Int railHotbar = add(new Setting.Int("rail_slot", "Rail Slot", 1, 1, 9));
	public final Setting.Bool totemSwap = add(new Setting.Bool("totem_swap", "Totem Before Boom", true));
	public final Setting.Int totemRange = add(new Setting.Int("totem_range", "Totem Range", 7, 2, 12, " b"));

	private final Set<Integer> handled = new HashSet<>();
	private final ArrayDeque<int[]> queue = new ArrayDeque<>(); // {x,y,z,phase} phase 0=rail,1=cart
	private int holdRevert = -1;
	private int savedSlot = -1;

	public FlameCartModule() {
		super("flame_cart", "Flame Cart", "Shoot a flame bow, a cart lands where the arrow does", Category.COMBAT);
	}

	@Override
	protected void onEnable() {
		handled.clear();
		queue.clear();
		holdRevert = -1;
		savedSlot = -1;
	}

	private boolean mine(Minecraft mc, AbstractArrow a) {
		Entity owner = a.getOwner();
		if (owner == mc.player) return true;
		return owner == null && a.position().distanceToSqr(mc.player.getEyePosition()) < 9.0;
	}

	/** Pfeil-Flugbahn simulieren (Vanilla: Schwerkraft 0,05, Luftwiderstand 0,99) bis zum Block-Einschlag. */
	private BlockHitResult predict(Minecraft mc, AbstractArrow a) {
		Vec3 pos = a.position();
		Vec3 v = a.getDeltaMovement();
		double budget = maxDist.get();
		for (int i = 0; i < 160 && budget > 0; i++) {
			Vec3 next = pos.add(v);
			BlockHitResult hit = mc.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
			if (hit.getType() == HitResult.Type.BLOCK) return hit;
			budget -= v.length();
			pos = next;
			v = v.scale(0.99).subtract(0, 0.05, 0);
		}
		return null;
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || mc.level == null) return;

		// kurz vor der Explosion: Totem in die Hand (schützt dich vor dem eigenen Cart)
		if (totemSwap.on() && mc.screen == null) {
			double tr = totemRange.get();
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e instanceof net.minecraft.world.entity.vehicle.minecart.MinecartTNT c && c.isPrimed() && Targets.distTo(p, c) <= tr) {
					int t = Targets.hotbar(p, st -> st.is(Items.TOTEM_OF_UNDYING));
					if (t >= 0 && p.getInventory().getSelectedSlot() != t) p.getInventory().setSelectedSlot(t);
					return; // Totem in der Hand halten, solange Gefahr besteht
				}
			}
		}

		if (refill.on()) {
			dev.quiqzy.client.util.Refill.keep(mc, cartHotbar.get() - 1, st -> st.is(Items.TNT_MINECART));
			dev.quiqzy.client.util.Refill.keep(mc, railHotbar.get() - 1,
					st -> st.is(Items.RAIL) || st.is(Items.POWERED_RAIL) || st.is(Items.ACTIVATOR_RAIL) || st.is(Items.DETECTOR_RAIL));
		}

		// 1. neue eigene (Flammen-)Pfeile finden und Einschlag berechnen
		int railSlot = Targets.hotbar(p, s -> s.is(Items.RAIL) || s.is(Items.POWERED_RAIL) || s.is(Items.ACTIVATOR_RAIL) || s.is(Items.DETECTOR_RAIL));
		int cartSlot = Targets.hotbar(p, s -> s.is(Items.TNT_MINECART));
		if (railSlot >= 0 && cartSlot >= 0) {
			for (Entity e : mc.level.entitiesForRendering()) {
				if (!(e instanceof AbstractArrow a) || handled.contains(a.getId())) continue;
				if (!mine(mc, a)) continue;
				if (!anyArrow.on() && !a.isOnFire()) continue;
				handled.add(a.getId());
				BlockHitResult hit = predict(mc, a);
				if (hit == null) continue;
				BlockPos place = hit.getBlockPos().relative(hit.getDirection()); // Luftblock an der Einschlagseite
				if (!Place.canPlace(mc, place)) continue;
				queue.add(new int[]{place.getX(), place.getY(), place.getZ(), 0});
			}
			if (handled.size() > 200) handled.clear();
		}

		// 2. Warteschlange abarbeiten: Schiene, dann Cart (je ein Tick), ohne den Blick zu drehen
		if (!queue.isEmpty()) {
			int[] j = queue.poll();
			BlockPos pos = new BlockPos(j[0], j[1], j[2]);
			if (savedSlot < 0) savedSlot = p.getInventory().getSelectedSlot();
			// Schiene + Cart im selben Tick (Server verarbeitet die Pakete der Reihe nach)
			if (railSlot >= 0 && !mc.level.getBlockState(pos).is(net.minecraft.tags.BlockTags.RAILS)) {
				p.getInventory().setSelectedSlot(railSlot);
				clickOn(mc, p, pos.below());
			}
			if (cartSlot >= 0) {
				p.getInventory().setSelectedSlot(cartSlot);
				clickOn(mc, p, pos);
			}
			holdRevert = 3;
		} else if (holdRevert > 0) {
			if (--holdRevert == 0 && savedSlot >= 0) {
				p.getInventory().setSelectedSlot(savedSlot);
				savedSlot = -1;
			}
		}

		// 3. optional: gesetzte Carts, die der Pfeil verfehlt hat, selbst zünden
		if (hitIgnite.on()) {
			double r = p.entityInteractionRange() + 0.5;
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e instanceof net.minecraft.world.entity.vehicle.minecart.MinecartTNT c && !c.isPrimed() && Targets.distTo(p, c) <= r) {
					mc.gameMode.attack(p, c);
					p.swing(InteractionHand.MAIN_HAND);
					break;
				}
			}
		}
	}

	/** Oben auf den angegebenen Block klicken – ohne Blickdrehung (Reichweite prüft der Server). */
	private void clickOn(Minecraft mc, LocalPlayer p, BlockPos target) {
		Vec3 hit = Vec3.atCenterOf(target).add(0, 0.5, 0);
		mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, target, false));
		p.swing(InteractionHand.MAIN_HAND);
	}
}
