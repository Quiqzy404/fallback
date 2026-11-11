package dev.quiqzy.client.module.combat;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.Place;
import dev.quiqzy.client.util.Targets;

/**
 * Triple Tap: eine Taste, drei Kristalle.
 *  1. Sprint-Hit mit dem Totem in der Hand (Anstoß am Boden)
 *  2. Launch: Obsidian neben das Ziel, Kristall drauf, sprengen -> Ziel fliegt hoch
 *  3. Air Hit: in der Luft mit der Axt (neuer Damage-Tick, sobald der Treffer-Schutz vom Kristall vorbei ist)
 *  4. Double Pop: noch zwei Kristalle auf DASSELBE Obsidian, jeweils nach dem Treffer-Schutz -> 2 Pops
 * Timing nach einem echten Clip: Schlag 0,8 s, Launch 1,1 s, Pops 1,3 s und 2,0 s.
 * Leg einen Keybind auf das Modul: drücken = Combo einmal ausführen (schaltet sich danach selbst aus).
 * Braucht Schwert/Axt, Obsidian und End-Kristalle in der Hotbar.
 */
public final class TripleTapModule extends Module {
	public final Setting.Int range = add(new Setting.Int("range", "Range", 4, 2, 6, " b"));
	public final Setting.Int delay = add(new Setting.Int("delay", "Step Delay", 1, 0, 6, " t"));
	public final Setting.Int pops = add(new Setting.Int("pops", "Extra Crystals", 2, 1, 4));
	public final Setting.Bool startHit = add(new Setting.Bool("start_hit", "Sprint Hit (Totem)", true));
	public final Setting.Bool swordHit = add(new Setting.Bool("sword_hit", "Air Axe Hit", true));
	/** Auf das Ende des Treffer-Schutzes (hurtTime) warten, damit jeder Treffer wirklich Schaden macht. */
	public final Setting.Bool waitHurt = add(new Setting.Bool("wait_hurt", "Wait For Damage Tick", true));
	public final Setting.Bool rotate = add(new Setting.Bool("rotate", "Rotate", true));
	public final Setting.Bool players = add(new Setting.Bool("players", "Players", true));
	public final Setting.Bool mobs = add(new Setting.Bool("mobs", "Mobs", false));

	private enum Step { HIT, OBBY, PLACE, BREAK, AIR_HIT, DONE }

	private Step step = Step.DONE;
	/** Selbsttest: Zahl der Luft-Schläge. */
	public int airHits;
	private LivingEntity target;
	private BlockPos base;
	private int wait, crystalsLeft, prevSlot, timeout, crystalsDone;

	public TripleTapModule() {
		super("triple_tap", "Triple Tap", "One key: sprint hit, crystal launch, air axe hit, double pop", Category.COMBAT);
	}

	private static void say(Minecraft mc, String s) {
		if (mc.player != null) mc.player.displayClientMessage(Component.literal("§6Triple Tap §7» §f" + s), true);
	}

	@Override
	protected void onEnable() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null) {
			setEnabled(false);
			return;
		}
		// Ziel: was im Fadenkreuz ist, sonst das nächste
		target = null;
		if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le
				&& Targets.valid(p, le, players.on(), mobs.on(), mobs.on())) target = le;
		if (target == null) {
			List<LivingEntity> ts = Targets.find(mc, range.get() + 1, 360, players.on(), mobs.on(), mobs.on(), false);
			if (!ts.isEmpty()) target = ts.get(0);
		}
		if (target == null) {
			say(mc, "No target in range");
			setEnabled(false);
			return;
		}
		if (Targets.hotbar(p, s -> s.is(Items.END_CRYSTAL)) < 0) {
			say(mc, "No end crystals in hotbar");
			setEnabled(false);
			return;
		}
		prevSlot = p.getInventory().getSelectedSlot();
		crystalsLeft = 1 + pops.get();
		base = null;
		crystalsDone = 0;
		step = startHit.on() ? Step.HIT : Step.OBBY;
		wait = 0;
		timeout = 200;
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (step != Step.DONE && mc.player != null) mc.player.getInventory().setSelectedSlot(prevSlot);
		step = Step.DONE;
		target = null;
	}

	private void finish(Minecraft mc, String msg) {
		if (mc.player != null) mc.player.getInventory().setSelectedSlot(prevSlot);
		step = Step.DONE;
		if (msg != null) say(mc, msg);
		setEnabled(false);
	}

	private void look(LocalPlayer p, Vec3 at) {
		if (!rotate.on()) return;
		float[] a = Targets.angles(p, at);
		p.setYRot(a[0]);
		p.setXRot(a[1]);
	}

	/** Basisblock neben dem Ziel: vorhandenes Obsidian/Bedrock unter einer freien Stelle, sonst ein Platz für neues Obsidian. */
	private BlockPos findBase(Minecraft mc, LocalPlayer p) {
		BlockPos feet = target.blockPosition();
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
			for (int dy = -1; dy <= 0; dy++) {
				BlockPos b = feet.relative(d).above(dy);
				var st = mc.level.getBlockState(b);
				boolean ready = st.is(Blocks.OBSIDIAN) || st.is(Blocks.BEDROCK);
				boolean placeable = !ready && dy == 0 && Place.canPlace(mc, b);
				if (!ready && !placeable) continue;
				// über dem Block muss Platz für den Kristall sein
				if (!mc.level.getBlockState(b.above()).isAir() || !mc.level.getBlockState(b.above(2)).isAir()) continue;
				if (p.getBoundingBox().intersects(new AABB(b.above()))) continue;
				double dist = p.getEyePosition().distanceTo(Vec3.atCenterOf(b));
				if (dist > p.blockInteractionRange()) continue;
				double score = dist - (ready ? 2 : 0); // vorhandenes Obsidian bevorzugen
				if (score < bestD) {
					bestD = score;
					best = b;
				}
			}
		}
		return best;
	}

	private EndCrystal crystalAt(Minecraft mc, BlockPos b) {
		AABB box = new AABB(b.above()).inflate(0.3);
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof EndCrystal c && c.getBoundingBox().intersects(box)) return c;
		}
		return null;
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || step == Step.DONE) return;
		if (--timeout <= 0) {
			finish(mc, "Combo timed out");
			return;
		}
		if (target == null || !target.isAlive()) {
			finish(mc, "Target gone");
			return;
		}
		if (wait-- > 0) return;
		switch (step) {
			case HIT -> {
				// Sprint-Hit mit Totem in der Hand (so bleibt der Totem griffbereit)
				int t = Targets.hotbar(p, st -> st.is(Items.TOTEM_OF_UNDYING));
				if (t >= 0) p.getInventory().setSelectedSlot(t);
				if (Targets.distTo(p, target) <= p.entityInteractionRange()) {
					if (!p.isSprinting() && p.getFoodData().getFoodLevel() > 6) p.setSprinting(true);
					look(p, Targets.aimPoint(p, target));
					mc.gameMode.attack(p, target);
					p.swing(InteractionHand.MAIN_HAND);
				}
				step = Step.OBBY;
				wait = delay.get();
			}
			case AIR_HIT -> {
				// in der Luft mit der Axt, sobald der Treffer-Schutz vom Launch-Kristall vorbei ist
				if (waitHurt.on() && target.hurtTime > 0) return;
				int w = Targets.hotbar(p, st -> st.is(net.minecraft.tags.ItemTags.AXES));
				if (w < 0) w = Targets.hotbar(p, st -> AutoWeaponModule.isWeapon(st));
				if (w >= 0) p.getInventory().setSelectedSlot(w);
				if (Targets.distTo(p, target) <= p.entityInteractionRange()) {
					look(p, Targets.aimPoint(p, target));
					mc.gameMode.attack(p, target);
					p.swing(InteractionHand.MAIN_HAND);
					airHits++;
				}
				step = Step.PLACE;
				wait = delay.get();
			}
			case OBBY -> {
				base = findBase(mc, p);
				if (base == null) {
					finish(mc, "No spot for obsidian next to the target");
					return;
				}
				var st = mc.level.getBlockState(base);
				if (!st.is(Blocks.OBSIDIAN) && !st.is(Blocks.BEDROCK)) {
					int ob = Targets.hotbar(p, s -> s.is(Items.OBSIDIAN));
					if (ob < 0) {
						finish(mc, "No obsidian in hotbar");
						return;
					}
					p.getInventory().setSelectedSlot(ob);
					if (!Place.at(mc, base, rotate.on())) {
						finish(mc, "Could not place obsidian");
						return;
					}
				}
				step = Step.PLACE;
				wait = delay.get();
			}
			case PLACE -> {
				var st = mc.level.getBlockState(base);
				if (!st.is(Blocks.OBSIDIAN) && !st.is(Blocks.BEDROCK)) {
					// Server hat das Obsidian noch nicht bestätigt -> kurz warten
					wait = 1;
					return;
				}
				// Pop-Kristalle erst setzen, wenn der Treffer-Schutz vorbei ist (sonst kein neuer Schaden)
				if (crystalsDone > 0 && waitHurt.on() && target.hurtTime > 0) return;
				if (crystalAt(mc, base) == null) {
					int c = Targets.hotbar(p, s -> s.is(Items.END_CRYSTAL));
					if (c < 0) {
						finish(mc, "Out of crystals");
						return;
					}
					p.getInventory().setSelectedSlot(c);
					Vec3 top = Vec3.atCenterOf(base).add(0, 0.5, 0);
					look(p, top);
					mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(top, Direction.UP, base, false));
					p.swing(InteractionHand.MAIN_HAND);
				}
				step = Step.BREAK;
				wait = delay.get();
			}
			case BREAK -> {
				EndCrystal c = crystalAt(mc, base);
				if (c == null) {
					// Kristall noch nicht beim Client angekommen -> kurz warten (max. über timeout)
					wait = 1;
					return;
				}
				look(p, c.position().add(0, 0.5, 0));
				mc.gameMode.attack(p, c);
				p.swing(InteractionHand.MAIN_HAND);
				// ohne Crystal Optimizer selbst entfernen, damit der nächste sofort gesetzt werden kann
				if (!ModuleManager.CRYSTAL_OPTIMIZER.isEnabled() && mc.level != null) mc.level.removeEntity(c.getId(), Entity.RemovalReason.KILLED);
				crystalsLeft--;
				crystalsDone++;
				if (crystalsLeft <= 0) {
					finish(mc, null);
					return;
				}
				step = crystalsDone == 1 && swordHit.on() ? Step.AIR_HIT : Step.PLACE;
				wait = delay.get();
			}
			default -> {
			}
		}
	}
}
