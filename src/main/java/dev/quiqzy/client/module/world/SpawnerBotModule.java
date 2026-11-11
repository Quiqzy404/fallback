package dev.quiqzy.client.module.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.config.QuiqzyConfig;
import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.Targets;

/**
 * Spawner Bot – alles über die GUI, keine Chat-Befehle:
 *  1. Spawner markieren ("Mark spawner": schaut auf den Spawner, den du ansiehst).
 *  2. Alle X Minuten: jeden markierten Spawner öffnen -> auf den Knopf (Standard: Dropper) klicken -> schließen.
 *  3. Danach /order <item> senden -> oberstes passendes Item im Menü anklicken -> alle Items dieser Sorte
 *     aus deinem Inventar ins Menü schieben -> schließen.
 * Spawner müssen in Reichweite sein (du läufst nicht selbst hin).
 */
public final class SpawnerBotModule extends Module {
	public final Setting.Int interval = add(new Setting.Int("interval", "Every", 10, 1, 180, " min"));
	public final Setting.Items button = add(new Setting.Items("button", "Spawner Button", "minecraft:dropper"));
	public final Setting.Items sellItem = add(new Setting.Items("sell_item", "Order Item", "minecraft:bone"));
	public final Setting.Bool order = add(new Setting.Bool("order", "Fill /order After", true));
	public final Setting.Int clickDelay = add(new Setting.Int("click_delay", "Click Delay", 8, 2, 40, " t"));
	/** Bones auf dem Boden einsammeln (hinlaufen) und danach zurück zum Startplatz. */
	public final Setting.Bool collect = add(new Setting.Bool("collect", "Collect Ground Items", true));
	public final Setting.Int collectRadius = add(new Setting.Int("collect_radius", "Collect Radius", 8, 2, 20, " b"));
	public final Setting.Bool look = add(new Setting.Bool("look", "Look At Spawner", true));
	/** Spawner in der Nähe selbst finden und markieren. */
	public final Setting.Bool autoMark = add(new Setting.Bool("auto_mark", "Auto Mark Spawners", true));
	public final Setting.Int markRadius = add(new Setting.Int("mark_radius", "Mark Radius", 5, 2, 16, " b"));
	public final Setting.Waypoints spawners = add(new Setting.Waypoints("spawners", "Spawners"));

	private enum Step { IDLE, OPEN, CLICK_BUTTON, CLOSE, COLLECT, RETURN, ORDER_OPEN, ORDER_CLICK, ORDER_WAIT, DEPOSIT, CLOSE_ALL, FINISH }

	private Step step = Step.IDLE;
	private long nextRun;
	private int wait;
	private int timeout;
	private int lastContainer = -1;
	private int attempts;
	private BlockPos current;
	/** Schließen, bis wirklich kein Menü mehr aufgeht; danach weiter mit "after". */
	private Step after = Step.FINISH;
	private int closeRounds, quietTicks;
	private Vec3 home;
	private int collectTicks, stuckTicks;
	private Vec3 lastPos;
	private final java.util.Set<Integer> skipped = new java.util.HashSet<>();
	private int targetId = -1, targetTicks;
	private int depositTicks, lastDepositSlot = -1, sameSlotTries;
	private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
	public String status = "Idle";

	public SpawnerBotModule() {
		super("spawner_bot", "Spawner Bot", "Empties marked spawners and fills an /order on a timer", Category.WORLD);
	}

	@Override
	protected void onEnable() {
		nextRun = 0;
		step = Step.IDLE;
	}

	@Override
	protected void onDisable() {
		releaseKeys(Minecraft.getInstance());
		step = Step.IDLE;
		status = "Off";
	}

	private static void say(Minecraft mc, String s) {
		if (mc.player != null) mc.player.displayClientMessage(Component.literal("\u00a76Spawner Bot \u00a77» \u00a7f" + s), true);
	}

	/** Spawner markieren, den du gerade ansiehst. */
	public void markLooked(Minecraft mc) {
		if (!(mc.hitResult instanceof BlockHitResult bhr) || mc.level == null || !mc.level.getBlockState(bhr.getBlockPos()).is(Blocks.SPAWNER)) {
			say(mc, "Look at a spawner first");
			return;
		}
		BlockPos b = bhr.getBlockPos();
		String dim = WaypointsModule.dimensionId(mc.level);
		for (Setting.Waypoints.Point p : spawners.get()) {
			if (p.x() == b.getX() && p.y() == b.getY() && p.z() == b.getZ() && p.dim().equals(dim)) {
				say(mc, "Already marked");
				return;
			}
		}
		spawners.add(new Setting.Waypoints.Point("Spawner " + (spawners.get().size() + 1), b.getX(), b.getY(), b.getZ(), dim));
		QuiqzyConfig.save();
		say(mc, "Spawner marked (" + spawners.get().size() + ")");
	}

	public String debug() {
		return step + " wait=" + wait + " timeout=" + timeout + " queue=" + queue.size() + " next=" + (nextRun - System.currentTimeMillis());
	}

	/** Selbsttest: direkt im offenen Menü abliefern. */
	public void testDeposit() {
		if (!isEnabled()) setEnabled(true);
		nextRun = Long.MAX_VALUE;
		step = Step.DEPOSIT;
		depositTicks = 0;
		lastDepositSlot = -1;
		sameSlotTries = 0;
	}

	/** Alle Spawner im Mark Radius markieren; gibt die Zahl der neuen zurück. */
	public int markAll(Minecraft mc, boolean announce) {
		if (mc.player == null || mc.level == null) return 0;
		String dim = WaypointsModule.dimensionId(mc.level);
		java.util.Set<Long> have = new java.util.HashSet<>();
		for (Setting.Waypoints.Point pt : spawners.get()) if (pt.dim().equals(dim)) have.add(BlockPos.asLong(pt.x(), pt.y(), pt.z()));
		BlockPos c = mc.player.blockPosition();
		int r = markRadius.get(), added = 0;
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
			m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
			if (!mc.level.getBlockState(m).is(Blocks.SPAWNER) || have.contains(m.asLong())) continue;
			spawners.add(new Setting.Waypoints.Point("Spawner " + (spawners.get().size() + 1), m.getX(), m.getY(), m.getZ(), dim));
			have.add(m.asLong());
			added++;
		}
		if (added > 0) QuiqzyConfig.save();
		if (announce) say(mc, added > 0 ? added + " spawner(s) marked (" + spawners.get().size() + " total)" : "No new spawners nearby");
		return added;
	}

	/** Markierte Spawner, die es nicht mehr gibt (abgebaut), aus der Liste nehmen – nur wenn der Chunk geladen ist. */
	private void pruneMissing(Minecraft mc) {
		String dim = WaypointsModule.dimensionId(mc.level);
		List<Setting.Waypoints.Point> keep = new ArrayList<>();
		boolean changed = false;
		for (Setting.Waypoints.Point pt : spawners.get()) {
			BlockPos b = new BlockPos(pt.x(), pt.y(), pt.z());
			if (pt.dim().equals(dim) && mc.level.isLoaded(b) && mc.player.blockPosition().distManhattan(b) < 48
					&& !mc.level.getBlockState(b).is(Blocks.SPAWNER)) {
				changed = true;
				continue;
			}
			keep.add(pt);
		}
		if (changed) {
			spawners.set(keep);
			QuiqzyConfig.save();
		}
	}

	private int scanTimer;

	public void runNow() {
		if (!isEnabled()) setEnabled(true);
		nextRun = 0;
		step = Step.IDLE;
	}

	private static Item item(Setting.Items s) {
		List<String> l = s.get();
		if (l.isEmpty()) return null;
		return BuiltInRegistries.ITEM.getValue(Identifier.parse(l.get(0)));
	}

	private static boolean isPlayerSlot(Slot s) {
		return s.container instanceof Inventory;
	}

	private boolean newContainer(LocalPlayer p) {
		AbstractContainerMenu m = p.containerMenu;
		return m != p.inventoryMenu && m.containerId != lastContainer;
	}

	private void click(Minecraft mc, int slot, ClickType type) {
		mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId, slot, 0, type, mc.player);
	}

	private Step afterSpawners() {
		if (collect.on()) {
			collectTicks = 0;
			home = null;
			return Step.COLLECT;
		}
		return order.on() ? Step.ORDER_OPEN : Step.FINISH;
	}

	private void releaseKeys(Minecraft mc) {
		if (mc.options == null) return;
		mc.options.keyUp.setDown(false);
		mc.options.keyJump.setDown(false);
		mc.options.keyShift.setDown(false);
	}

	/** Eine Richtung laufen; springt, wenn man hängen bleibt. */
	private void walkTo(Minecraft mc, LocalPlayer p, Vec3 to) {
		double dx = to.x - p.getX(), dz = to.z - p.getZ();
		p.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
		mc.options.keyUp.setDown(true);
		Vec3 now = p.position();
		if (lastPos != null && now.distanceToSqr(lastPos) < 0.0004) stuckTicks++;
		else stuckTicks = 0;
		lastPos = now;
		mc.options.keyJump.setDown(stuckTicks > 6 && p.onGround());
	}

	private net.minecraft.world.entity.item.ItemEntity nearestDrop(Minecraft mc, LocalPlayer p) {
		Item sell = item(sellItem);
		if (sell == null) return null;
		Vec3 c = home != null ? home : p.position();
		double r = collectRadius.get(), best = Double.MAX_VALUE;
		net.minecraft.world.entity.item.ItemEntity out = null;
		for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof net.minecraft.world.entity.item.ItemEntity ie) || !ie.getItem().is(sell) || skipped.contains(e.getId())) continue;
			if (Math.abs(e.getY() - c.y) > 3 || e.position().distanceTo(c) > r) continue;
			double d = e.distanceToSqr(p);
			if (d < best) {
				best = d;
				out = ie;
			}
		}
		return out;
	}

	private boolean inventoryFull(LocalPlayer p) {
		Item sell = item(sellItem);
		var inv = p.getInventory();
		for (int i = 0; i < 36; i++) {
			ItemStack st = inv.getItem(i);
			if (st.isEmpty() || (st.is(sell) && st.getCount() < st.getMaxStackSize())) return false;
		}
		return true;
	}

	private void closeAll(Step next) {
		after = next;
		closeRounds = 0;
		quietTicks = 0;
		step = Step.CLOSE_ALL;
	}

	private void done(Minecraft mc, String msg) {
		if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
		nextRun = System.currentTimeMillis() + interval.get() * 60_000L;
		step = Step.IDLE;
		say(mc, msg);
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null || mc.gameMode == null || mc.level == null) return;
		long now = System.currentTimeMillis();
		if (autoMark.on() && ++scanTimer >= 40) {
			scanTimer = 0;
			int n = markAll(mc, false);
			if (n > 0) say(mc, n + " new spawner(s) marked automatically");
			pruneMissing(mc);
		}
		if (step == Step.IDLE) {
			long left = Math.max(0, nextRun - now);
			status = "Next run in " + (left / 60000) + ":" + String.format("%02d", (left / 1000) % 60);
			if (now < nextRun || mc.screen != null) return;
			String dim = WaypointsModule.dimensionId(mc.level);
			queue.clear();
			for (Setting.Waypoints.Point pt : spawners.get()) if (pt.dim().equals(dim)) queue.add(new BlockPos(pt.x(), pt.y(), pt.z()));
			current = null;
			attempts = 0;
			step = queue.isEmpty() ? afterSpawners() : Step.OPEN;
			wait = 0;
			return;
		}
		if (wait > 0) {
			wait--;
			return;
		}
		switch (step) {
			case OPEN -> {
				if (p.containerMenu != p.inventoryMenu) {
					// altes Menü noch offen -> erst sauber schließen
					closeAll(Step.OPEN);
					return;
				}
				if (current == null || attempts >= 3) {
					current = queue.poll();
					attempts = 0;
				}
				if (current == null) {
					step = afterSpawners();
					return;
				}
				BlockPos pos = current;
				Vec3 c = Vec3.atCenterOf(pos);
				if (p.getEyePosition().distanceTo(c) > p.blockInteractionRange() + 0.5) {
					say(mc, "Spawner at " + pos.toShortString() + " is out of reach – skipped");
					current = null;
					return;
				}
				if (look.on()) {
					float[] a = Targets.angles(p, c);
					p.setYRot(a[0]);
					p.setXRot(a[1]);
				}
				attempts++;
				lastContainer = p.containerMenu.containerId;
				status = "Opening spawner " + pos.toShortString() + (attempts > 1 ? " (try " + attempts + ")" : "");
				mc.gameMode.useItemOn(p, InteractionHand.MAIN_HAND, new BlockHitResult(c, Direction.UP, pos, false));
				p.swing(InteractionHand.MAIN_HAND);
				step = Step.CLICK_BUTTON;
				timeout = 40;
				wait = 2;
			}
			case CLICK_BUTTON -> {
				if (!newContainer(p)) {
					if (--timeout <= 0) step = Step.OPEN; // nicht aufgegangen -> nochmal versuchen (max. 3x)
					return;
				}
				// warten, bis der Server den Inhalt geschickt hat
				Item btn = item(button);
				Slot hit = null;
				for (Slot sl : p.containerMenu.slots) {
					if (!isPlayerSlot(sl) && btn != null && sl.getItem().is(btn)) {
						hit = sl;
						break;
					}
				}
				if (hit == null) {
					if (--timeout <= 0) {
						say(mc, "No button found in spawner menu");
						current = null;
						closeAll(Step.OPEN);
					}
					return;
				}
				click(mc, hit.index, ClickType.PICKUP);
				current = null; // erledigt
				step = Step.CLOSE;
				wait = clickDelay.get();
			}
			case CLOSE -> closeAll(Step.OPEN);
			case CLOSE_ALL -> {
				if (p.containerMenu != p.inventoryMenu) {
					if (closeRounds >= 6) {
						done(mc, "Could not leave the menu");
						return;
					}
					p.closeContainer(); // = Esc
					closeRounds++;
					quietTicks = 0;
					wait = clickDelay.get();
					return;
				}
				// kurz abwarten, ob der Server direkt ein neues Menü öffnet
				if (++quietTicks < Math.max(10, clickDelay.get() + 4)) return;
				step = after;
			}
			case COLLECT -> {
				if (home == null) {
					home = p.position();
					skipped.clear();
					targetId = -1;
					status = "Waiting for drops to land";
					wait = 30; // gedroppte Items erst fallen lassen
					return;
				}
				var drop = nearestDrop(mc, p);
				if (drop == null || inventoryFull(p) || ++collectTicks > 400) {
					step = Step.RETURN;
					collectTicks = 0;
					return;
				}
				if (drop.getId() != targetId) {
					targetId = drop.getId();
					targetTicks = 0;
				}
				if (++targetTicks > 40 && stuckTicks > 10) {
					skipped.add(drop.getId()); // nicht erreichbar -> überspringen
					return;
				}
				status = "Collecting items (" + (int) drop.distanceTo(p) + "m)";
				walkTo(mc, p, drop.position());
			}
			case RETURN -> {
				double dx = home.x - p.getX(), dz = home.z - p.getZ();
				if (dx * dx + dz * dz < 0.3 * 0.3 || ++collectTicks > 300) {
					releaseKeys(mc);
					p.setDeltaMovement(0, p.getDeltaMovement().y, 0);
					step = order.on() ? Step.ORDER_OPEN : Step.FINISH;
					wait = clickDelay.get();
					return;
				}
				status = "Walking back";
				walkTo(mc, p, home);
				// kurz vor dem Ziel langsamer werden (schleichen), damit er nicht übers Ziel läuft
				mc.options.keyShift.setDown(dx * dx + dz * dz < 1.2);
				if (dx * dx + dz * dz < 0.3 * 0.3) mc.options.keyShift.setDown(false);
			}
			case ORDER_OPEN -> {
				mc.options.keyShift.setDown(false);
				Item sell = item(sellItem);
				if (sell == null) {
					done(mc, "No order item set");
					return;
				}
				lastContainer = p.containerMenu.containerId;
				status = "Opening /order";
				p.connection.sendCommand("order " + BuiltInRegistries.ITEM.getKey(sell).getPath());
				step = Step.ORDER_CLICK;
				timeout = 80;
				wait = clickDelay.get();
			}
			case ORDER_CLICK -> {
				if (!newContainer(p)) {
					if (--timeout <= 0) done(mc, "/order menu did not open");
					return;
				}
				Item sell = item(sellItem);
				Slot first = null;
				for (Slot sl : p.containerMenu.slots) {
					if (!isPlayerSlot(sl) && sl.getItem().is(sell)) {
						first = sl; // Slots von oben links nach unten rechts -> erster = oberster
						break;
					}
				}
				if (first == null) {
					if (--timeout <= 0) done(mc, "No order for this item found");
					return;
				}
				lastContainer = p.containerMenu.containerId;
				click(mc, first.index, ClickType.PICKUP);
				step = Step.ORDER_WAIT;
				timeout = 40;
				wait = clickDelay.get();
			}
			case ORDER_WAIT -> {
				if (newContainer(p) || --timeout <= 0) {
					step = Step.DEPOSIT;
					depositTicks = 0;
					lastDepositSlot = -1;
					sameSlotTries = 0;
					wait = clickDelay.get();
				}
			}
			case DEPOSIT -> {
				// erst warten, bis der Server den Menü-Inhalt geschickt hat
				if (p.containerMenu == p.inventoryMenu) {
					done(mc, "Delivery menu closed too early");
					return;
				}
				if (p.containerMenu.getStateId() == 0 && ++depositTicks < 40) return;
				Item sell = item(sellItem);
				Slot next = null;
				int left = 0;
				for (Slot sl : p.containerMenu.slots) {
					if (isPlayerSlot(sl) && sl.getItem().is(sell)) {
						left += sl.getItem().getCount();
						if (next == null) next = sl;
					}
				}
				boolean menuFull = true;
				for (Slot sl : p.containerMenu.slots) if (!isPlayerSlot(sl) && sl.getItem().isEmpty()) { menuFull = false; break; }
				if (next == null || menuFull || ++depositTicks > 400) {
					status = "Delivered – closing";
					wait = clickDelay.get();
					closeAll(Step.FINISH); // Esc liefert ab
					return;
				}
				// gleicher Stapel 3x nicht weggegangen -> per Aufnehmen + Ablegen versuchen
				if (next.index == lastDepositSlot && ++sameSlotTries >= 3) {
					int free = -1;
					for (Slot sl : p.containerMenu.slots) if (!isPlayerSlot(sl) && sl.getItem().isEmpty()) { free = sl.index; break; }
					if (free >= 0) {
						click(mc, next.index, ClickType.PICKUP);
						click(mc, free, ClickType.PICKUP);
					}
					sameSlotTries = 0;
				} else {
					if (next.index != lastDepositSlot) sameSlotTries = 0;
					click(mc, next.index, ClickType.QUICK_MOVE);
				}
				lastDepositSlot = next.index;
				status = "Delivering (" + left + " left)";
				wait = 1; // ein Stapel pro Tick, damit der Server nichts verwirft
			}
			case FINISH -> done(mc, "Done – next run in " + interval.get() + " min");
			default -> step = Step.IDLE;
		}
	}
}
