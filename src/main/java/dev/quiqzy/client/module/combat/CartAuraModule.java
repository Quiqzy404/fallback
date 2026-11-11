package dev.quiqzy.client.module.combat;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.Place;
import dev.quiqzy.client.util.Targets;

/**
 * Cart Aura (TNT-Minecart-PvP): setzt neben einen Gegner eine Schiene, stellt einen TNT-Minecart drauf und zündet ihn.
 * Gezündet wird durch einen Schlag (schnell) oder mit Feuerzeug. "Auto Ignite" zündet auch schon gesetzte Carts in der Nähe.
 * Braucht Schiene + TNT-Minecart in der Hotbar (Feuerzeug optional). Carts machen viel Schaden – "Safe" setzt Obsidian dazwischen.
 */
public final class CartAuraModule extends Module {
	public final Setting.Int range = add(new Setting.Int("range", "Target Range", 4, 2, 6, " b"));
	public final Setting.Int delay = add(new Setting.Int("delay", "Step Delay", 2, 0, 10, " t"));
	public final Setting.Choice ignite = add(new Setting.Choice("ignite", "Ignite", "Hit", "Hit", "Flint & Steel"));
	public final Setting.Bool autoIgnite = add(new Setting.Bool("auto_ignite", "Auto Ignite Nearby", true));
	public final Setting.Bool safe = add(new Setting.Bool("safe", "Safe (Obsidian)", false));
	public final Setting.Bool rotate = add(new Setting.Bool("rotate", "Rotate", true));
	public final Setting.Bool players = add(new Setting.Bool("players", "Players", true));
	public final Setting.Bool mobs = add(new Setting.Bool("mobs", "Mobs", false));
	public final Setting.Int minHealth = add(new Setting.Int("min_health", "Pause Below Health", 6, 0, 20, " HP"));
	public final Setting.Bool refill = add(new Setting.Bool("refill", "Cart Refill", true));
	public final Setting.Int cartHotbar = add(new Setting.Int("cart_slot", "Cart Slot", 2, 1, 9));
	public final Setting.Bool refillRail = add(new Setting.Bool("refill_rail", "Also Refill Rail", true));
	public final Setting.Int railHotbar = add(new Setting.Int("rail_slot", "Rail Slot", 1, 1, 9));
	public final Setting.Bool totemSwap = add(new Setting.Bool("totem_swap", "Totem Before Boom", true));
	public final Setting.Int totemRange = add(new Setting.Int("totem_range", "Totem Range", 7, 2, 12, " b"));

	private enum Step { FIND, RAIL, CART, SAFE, IGNITE }

	private Step step = Step.FIND;
	private BlockPos rail;
	private int wait;

	public CartAuraModule() {
		super("cart_aura", "Cart Aura", "Places and detonates TNT minecarts on enemies", Category.COMBAT);
	}

	@Override
	protected void onEnable() {
		step = Step.FIND;
		rail = null;
	}

	private void look(LocalPlayer p, Vec3 at) {
		if (!rotate.on()) return;
		float[] a = Targets.angles(p, at);
		p.setYRot(a[0]);
		p.setXRot(a[1]);
	}

	private MinecartTNT cartAt(Minecraft mc, BlockPos pos) {
		AABB box = new AABB(pos).inflate(0.3, 0.8, 0.3);
		for (Entity e : mc.level.entitiesForRendering())
			if (e instanceof MinecartTNT c && c.getBoundingBox().intersects(box)) return c;
		return null;
	}

	/** Zündet alle nicht-primed TNT-Carts in Reichweite durch einen Schlag. */
	private boolean igniteNearby(Minecraft mc, LocalPlayer p) {
		double r = p.entityInteractionRange() + 0.5;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof MinecartTNT c) || c.isPrimed()) continue;
			if (Targets.distTo(p, c) > r) continue;
			look(p, c.position().add(0, 0.3, 0));
			mc.gameMode.attack(p, c);
			p.swing(InteractionHand.MAIN_HAND);
			return true;
		}
		return false;
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || mc.level == null) return;
		if (totemSwap.on() && mc.screen == null) {
			double tr = totemRange.get();
			for (var e : mc.level.entitiesForRendering()) {
				if (e instanceof net.minecraft.world.entity.vehicle.minecart.MinecartTNT c && c.isPrimed() && Targets.distTo(p, c) <= tr) {
					int t = Targets.hotbar(p, st -> st.is(Items.TOTEM_OF_UNDYING));
					if (t >= 0 && p.getInventory().getSelectedSlot() != t) p.getInventory().setSelectedSlot(t);
					return;
				}
			}
		}
		if (refill.on()) {
			if (dev.quiqzy.client.util.Refill.keep(mc, cartHotbar.get() - 1, st -> st.is(Items.TNT_MINECART))) return;
			if (refillRail.on() && dev.quiqzy.client.util.Refill.keep(mc, railHotbar.get() - 1,
					st -> st.is(Items.RAIL) || st.is(Items.POWERED_RAIL) || st.is(Items.ACTIVATOR_RAIL) || st.is(Items.DETECTOR_RAIL))) return;
		}
		if (mc.screen != null) return;
		if (p.getHealth() + p.getAbsorptionAmount() < minHealth.get()) return;
		if (wait-- > 0) return;

		if (autoIgnite.on() && igniteNearby(mc, p)) {
			wait = delay.get();
			return;
		}

		int railSlot = Targets.hotbar(p, s -> s.is(Items.RAIL) || s.is(Items.POWERED_RAIL) || s.is(Items.ACTIVATOR_RAIL) || s.is(Items.DETECTOR_RAIL));
		int cartSlot = Targets.hotbar(p, s -> s.is(Items.TNT_MINECART));
		if (cartSlot < 0) return;
		int prev = p.getInventory().getSelectedSlot();

		switch (step) {
			case FIND -> {
				List<LivingEntity> ts = Targets.find(mc, range.get() + 1, 360, players.on(), mobs.on(), mobs.on(), false);
				if (ts.isEmpty()) return;
				LivingEntity target = ts.get(0);
				BlockPos tf = target.blockPosition(), best = null;
				double bd = Double.MAX_VALUE;
				for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
					BlockPos c = tf.relative(d);
					boolean onRail = mc.level.getBlockState(c).is(net.minecraft.tags.BlockTags.RAILS);
					if (!onRail && (!Place.canPlace(mc, c) || !mc.level.getBlockState(c.below()).isSolidRender())) continue;
					if (p.getBoundingBox().intersects(new AABB(c))) continue;
					double dist = p.getEyePosition().distanceTo(Vec3.atCenterOf(c));
					if (dist > p.blockInteractionRange()) continue;
					if (dist < bd) {
						bd = dist;
						best = c;
					}
				}
				if (best == null) return;
				rail = best;
				step = mc.level.getBlockState(best).is(net.minecraft.tags.BlockTags.RAILS) ? Step.CART : Step.RAIL;
				wait = delay.get();
			}
			case RAIL -> {
				if (railSlot < 0) {
					step = Step.FIND;
					rail = null;
					return;
				}
				p.getInventory().setSelectedSlot(railSlot);
				Place.at(mc, rail, rotate.on());
				p.getInventory().setSelectedSlot(prev);
				step = Step.CART;
				wait = delay.get();
			}
			case CART -> {
				if (!mc.level.getBlockState(rail).is(net.minecraft.tags.BlockTags.RAILS)) {
					// Schiene noch nicht bestätigt
					wait = 1;
					return;
				}
				if (cartAt(mc, rail) == null) {
					p.getInventory().setSelectedSlot(cartSlot);
					Vec3 hit = Vec3.atCenterOf(rail).add(0, 0.1, 0);
					look(p, hit);
					mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, Direction.UP, rail, false));
					p.swing(InteractionHand.MAIN_HAND);
					p.getInventory().setSelectedSlot(prev);
				}
				step = safe.on() ? Step.SAFE : Step.IGNITE;
				wait = delay.get();
			}
			case SAFE -> {
				int ob = Targets.hotbar(p, s -> s.is(Items.OBSIDIAN));
				if (ob >= 0) {
					BlockPos between = rail.above();
					double dx = p.getX() - (rail.getX() + 0.5), dz = p.getZ() - (rail.getZ() + 0.5);
					Direction d = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
					BlockPos sp = rail.relative(d);
					if (Place.canPlace(mc, sp) && !p.getBoundingBox().intersects(new AABB(sp))) {
						p.getInventory().setSelectedSlot(ob);
						Place.at(mc, sp, rotate.on());
						p.getInventory().setSelectedSlot(prev);
					}
				}
				step = Step.IGNITE;
				wait = delay.get();
			}
			case IGNITE -> {
				MinecartTNT c = cartAt(mc, rail);
				if (c != null && !c.isPrimed()) {
					look(p, c.position().add(0, 0.3, 0));
					if (ignite.get().equals("Flint & Steel")) {
						int fs = Targets.hotbar(p, s -> s.is(Items.FLINT_AND_STEEL));
						if (fs >= 0) {
							p.getInventory().setSelectedSlot(fs);
							mc.gameMode.interact(p, c, InteractionHand.MAIN_HAND);
							p.getInventory().setSelectedSlot(prev);
						} else {
							mc.gameMode.attack(p, c);
						}
					} else {
						mc.gameMode.attack(p, c);
					}
					p.swing(InteractionHand.MAIN_HAND);
				}
				rail = null;
				step = Step.FIND;
				wait = delay.get() + 2;
			}
		}
	}
}
