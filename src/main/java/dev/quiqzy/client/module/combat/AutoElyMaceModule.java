package dev.quiqzy.client.module.combat;

import java.util.Random;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.InputUtil;

/**
 * Auto Ely Mace (Wind Burst):
 *   START   -> on enable: jump, open elytra, rockets up to "Start Height" (only once, "Fly Up On Start")
 *   FREE    -> YOU fly (no steering, no rockets). A player comes into "Range" -> DIVE
 *   DIVE    -> aim at the player, fly down with rockets ("Dive Rockets")
 *   HIT     -> "Elytra Swap": tick 0 elytra off + weapon in hand
 *              shield up: tick 1 axe hit (breaks shield), "Swap Delay" later mace hit
 *              no shield: tick 1 mace hit
 *              "Re-equip Delay" ticks after the last hit: elytra back on
 *   RECAST  -> Wind Burst launches you up; at the highest point the elytra is opened again -> DIVE again
 *              (without Wind Burst: as soon as you fall). Target dead/gone -> FREE (you fly).
 *   LAUNCH  -> only if you are on the ground: jump + open elytra.
 *
 * Needs: elytra on, rockets + mace in the hotbar (axe for shields, chestplate or empty slot for the swap).
 */
public final class AutoElyMaceModule extends Module {
	public final Setting.Int range = add(new Setting.Int("range", "Range", 48, 8, 128, " m"));
	public final Setting.Bool flyUpOnStart = add(new Setting.Bool("fly_up_on_start", "Fly Up On Start", true));
	public final Setting.Int startHeight = add(new Setting.Int("start_height", "Start Height", 25, 5, 80, " m"));
	public final Setting.Bool diveRockets = add(new Setting.Bool("dive_rockets", "Dive Rockets", true));
	public final Setting.Int diveRocketDelay = add(new Setting.Int("dive_rocket_delay", "Dive Rocket Delay", 10, 2, 40, " t"));
	public final Setting.Bool breakShield = add(new Setting.Bool("break_shield", "Break Shield", true));
	public final Setting.Int swapDelay = add(new Setting.Int("swap_delay", "Swap Delay", 1, 1, 5, " t"));
	public final Setting.Bool elytraSwap = add(new Setting.Bool("elytra_swap", "Elytra Swap", true));
	public final Setting.Int reequipDelay = add(new Setting.Int("reequip_delay", "Re-equip Delay", 4, 1, 40, " t"));
	public final Setting.Int windBurstWait = add(new Setting.Int("wind_burst_wait", "Wind Burst Wait", 10, 1, 40, " t"));
	public final Setting.Bool predict = add(new Setting.Bool("predict", "Predict Movement", true));
	public final Setting.Bool smoothAim = add(new Setting.Bool("smooth_aim", "Smooth Aim", true));
	public final Setting.Int aimSpeed = add(new Setting.Int("aim_speed", "Aim Speed", 10, 1, 30, ""));
	public final Setting.Bool humanize = add(new Setting.Bool("humanize", "Humanize", true));

	private static final double REACH = 3.0;
	/** Steigwinkel und Raketen-Abstand beim Hochfliegen am Anfang (nicht einstellbar, damit es einfach bleibt). */
	private static final float CLIMB_PITCH = -65f;
	private static final int CLIMB_ROCKET_DELAY = 18;
	private static final int MAX_WAIT = 6;
	private static final float HIT_ANGLE = 25f;
	/** Brustplatz im Spieler-Inventar-Menü (0 Ergebnis, 1–4 Crafting, 5 Kopf, 6 Brust, ...). */
	private static final int CHEST_MENU_SLOT = 6;

	private static final Predicate<ItemStack> ROCKET = s -> s.is(Items.FIREWORK_ROCKET);
	private static final Predicate<ItemStack> MACE = s -> s.is(Items.MACE);
	private static final Predicate<ItemStack> AXE = s -> s.is(ItemTags.AXES);
	private static final Predicate<ItemStack> CHESTPLATE = s -> s.is(ItemTags.CHEST_ARMOR) && !s.is(Items.ELYTRA);

	private enum State { FREE, LAUNCH, ASCEND, DIVE, AXE_HIT, MACE_HIT, REEQUIP, RECAST }

	private State state = State.FREE;
	private Player target;
	private int rocketTimer;
	private int launchTicks;
	private int phaseDelay;
	private int waitTicks;
	private int swapSlot = -1;
	private boolean elytraOff;
	private int recastTicks;
	/** Hochfliegen am Anfang noch offen? */
	private boolean ascendPending;
	private double baseY;
	/** Hat der Wind Burst uns schon hochgeschleudert? */
	private boolean launched;
	private String lastWarning;

	// ---- weiches Zielen (pro Bild) ----
	private float desiredYaw;
	private float desiredPitch;
	private boolean steering;
	private long lastFrame;
	private final Random rng = new Random();

	public AutoElyMaceModule() {
		super("auto_ely_mace", "Auto Ely Mace", "You fly – a player comes close: dive, shield break, mace slam, Wind Burst up, again", Category.COMBAT);
	}

	@Override
	protected void onEnable() {
		reset();
		Minecraft mc = Minecraft.getInstance();
		if (flyUpOnStart.on() && mc.player != null) {
			ascendPending = true;
			baseY = mc.player.getY();
			launchTicks = 0;
			state = mc.player.isFallFlying() ? State.ASCEND : State.LAUNCH;
		}
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (elytraOff && mc.player != null && mc.gameMode != null) swapChest(mc, mc.player, swapSlot); // Elytra wieder an
		reset();
		releaseJump(mc);
	}

	private void reset() {
		state = State.FREE;
		target = null;
		recastTicks = 0;
		rocketTimer = 0;
		launchTicks = 0;
		phaseDelay = 0;
		waitTicks = 0;
		swapSlot = -1;
		elytraOff = false;
		lastWarning = null;
		steering = false;
		ascendPending = false;
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mc.gameMode == null || mc.screen != null) return;

		if (!elytraOff && !player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) { warn(player, "Auto Ely Mace: no elytra equipped"); return; }
		Inventory inv = player.getInventory();
		if (findSlot(inv, MACE) == -1) { warn(player, "Auto Ely Mace: no mace in hotbar"); return; }
		lastWarning = null;

		// Ziel tot/weg
		if (target != null && !validTarget(player, target)) {
			target = null;
			if (elytraOff) { phaseDelay = 0; state = State.REEQUIP; }
			else if (state == State.DIVE || state == State.AXE_HIT || state == State.MACE_HIT || state == State.RECAST) toFree();
		}

		switch (state) {
			case FREE -> {
				// du hast die Kontrolle – nur nach Spielern schauen
				steering = false;
				target = findTarget(mc, player);
				if (target != null) {
					player.displayClientMessage(Component.literal("Auto Ely Mace: attacking " + target.getName().getString()), true);
					rocketTimer = 0;
					state = State.DIVE;
				}
			}

			case LAUNCH -> launch(mc, player);

			case ASCEND -> {
				// nur am Anfang: mit Raketen auf "Start Height" hoch
				if (!player.isFallFlying()) { launchTicks = 0; state = State.LAUNCH; break; }
				setDesired(player, steering ? desiredYaw : player.getYRot(), CLIMB_PITCH);
				if (--rocketTimer <= 0) {
					if (useRocket(mc, player)) rocketTimer = vary(CLIMB_ROCKET_DELAY);
					else { warn(player, "Auto Ely Mace: no rockets in hotbar"); rocketTimer = 20; }
				}
				if (player.getY() >= baseY + startHeight.get()) {
					ascendPending = false;
					steering = false; // ab jetzt fliegst du
					state = State.FREE;
				}
			}

			case RECAST -> {
				// Wind Burst schleudert dich hoch -> am höchsten Punkt Elytra wieder auf und runter
				aimAtTarget(player);
				if (player.isFallFlying()) { rocketTimer = 0; state = State.DIVE; break; }
				if (player.onGround()) { launchTicks = 0; state = State.LAUNCH; break; }
				recastTicks++;
				double vy = player.getDeltaMovement().y;
				if (vy > 0.2) launched = true;                                   // Wind Burst ist angekommen
				boolean apex = launched && vy <= 0.05;                           // höchster Punkt erreicht
				boolean noBurst = !launched && recastTicks > windBurstWait.get() && vy < 0; // kein Wind Burst -> beim Fallen
				if (apex || noBurst || recastTicks > 80) {
					if (player.tryToStartFallFlying()) {
						player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
					}
				}
			}

			case DIVE -> {
				if (target == null) { toFree(); break; }
				if (!player.isFallFlying()) {
					// gelandet / Elytra zu -> wieder starten
					recastTicks = 0;
					launched = false;
					launchTicks = 0;
					state = player.onGround() ? State.LAUNCH : State.RECAST;
					break;
				}
				aimAtTarget(player);

				boolean shield = breakShield.on() && target.isBlocking() && findSlot(inv, AXE) != -1;
				// Raketen zwischendurch, sonst schon die richtige Waffe halten (Cooldown erholt sich im Sturz)
				if (diveRockets.on() && player.isFallFlying() && --rocketTimer <= 0 && findSlot(inv, ROCKET) != -1) {
					useRocket(mc, player);
					rocketTimer = vary(diveRocketDelay.get());
				} else {
					select(inv, shield ? AXE : MACE);
				}

				if (distanceToBox(player.getEyePosition(), target.getBoundingBox()) <= REACH + 1.0) {
					// Tick 0: Elytra aus + Waffe in die Hand
					if (elytraSwap.on()) {
						swapSlot = findSwapSlot(inv);
						if (swapSlot != -1) {
							swapChest(mc, player, swapSlot);
							elytraOff = true;
						} else {
							warn(player, "Auto Ely Mace: no chestplate / empty hotbar slot for elytra swap");
						}
					}
					select(inv, shield ? AXE : MACE);
					phaseDelay = 1 + extraTick();
					waitTicks = 0;
					state = shield ? State.AXE_HIT : State.MACE_HIT;
				}
			}

			case AXE_HIT -> {
				aimAtTarget(player);
				if (--phaseDelay > 0 || !inReachOrWaited(player)) break;
				// Tick 1: Axt -> Schild brechen
				select(inv, AXE);
				hit(mc, player);
				phaseDelay = swapDelay.get() + extraTick();
				waitTicks = 0;
				state = State.MACE_HIT;
			}

			case MACE_HIT -> {
				aimAtTarget(player);
				if (--phaseDelay > 0) break;
				select(inv, MACE);
				if (!inReachOrWaited(player)) break;
				hit(mc, player);
				if (elytraOff) {
					phaseDelay = reequipDelay.get() + extraTick();
					state = State.REEQUIP;
				} else {
					afterHit(player);
				}
			}

			case REEQUIP -> {
				if (--phaseDelay > 0) break;
				// "Re-equip Delay" Ticks nach dem letzten Treffer: Elytra wieder an
				swapChest(mc, player, swapSlot);
				elytraOff = false;
				swapSlot = -1;
				afterHit(player);
			}
		}
	}

	private void launch(Minecraft mc, LocalPlayer player) {
		if (player.isFallFlying()) {
			releaseJump(mc);
			rocketTimer = 0;
			state = ascendPending ? State.ASCEND : (target != null ? State.DIVE : State.FREE);
			return;
		}
		if (player.onGround()) {
			if (ascendPending) baseY = player.getY();
			mc.options.keyJump.setDown(true); // abspringen
		} else if (player.tryToStartFallFlying()) {
			player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
		}
		if (++launchTicks > 40) {
			releaseJump(mc);
			launchTicks = 0;
			toFree();
		}
	}

	/** Du hast wieder die Kontrolle. */
	private void toFree() {
		target = null;
		steering = false;
		state = State.FREE;
	}

	/** Nach dem Treffer: Wind Burst abwarten, dann wieder runter auf dasselbe Ziel. */
	private void afterHit(LocalPlayer player) {
		rocketTimer = 0;
		recastTicks = 0;
		launched = false;
		launchTicks = 0;
		state = player.isFallFlying() ? State.DIVE : State.RECAST;
	}

	private void hit(Minecraft mc, LocalPlayer player) {
		if (target != null) mc.gameMode.attack(player, target);
		player.swing(InteractionHand.MAIN_HAND);
	}

	// ================= Ziel =================

	private Player findTarget(Minecraft mc, LocalPlayer self) {
		Player best = null;
		double bestDist = range.get();
		for (AbstractClientPlayer p : mc.level.players()) {
			if (!validTarget(self, p)) continue;
			double d = self.distanceTo(p);
			if (d < bestDist) {
				bestDist = d;
				best = p;
			}
		}
		return best;
	}

	private boolean validTarget(LocalPlayer self, Player p) {
		return p != self && p.isAlive() && !p.isRemoved() && !p.isSpectator() && !p.isCreative()
				&& self.distanceTo(p) <= range.get() + 16;
	}

	/** In Schlagreichweite und (bei Smooth Aim) Fadenkreuz ungefähr aufs Ziel – oder schon MAX_WAIT Ticks gewartet. */
	private boolean inReachOrWaited(LocalPlayer player) {
		if (target == null) return true;
		boolean reach = distanceToBox(player.getEyePosition(), target.getBoundingBox()) <= REACH;
		boolean aimed = !smoothAim.on() || aimError(player) <= HIT_ANGLE;
		if (reach && aimed) return true;
		return ++waitTicks >= MAX_WAIT;
	}

	// ================= Zielen (weich, pro Bild) =================

	private void aimAtTarget(LocalPlayer player) {
		aimAtTarget(player, 1f);
	}

	/** Wohin zielen: Körpermitte (flüssige Zwischenposition) + Vorhalten, wo der Gegner sein wird, wenn du ankommst. */
	private Vec3 aimPoint(LocalPlayer player, float partialTick) {
		Vec3 pos = target.getPosition(partialTick).add(0, target.getBbHeight() * 0.5, 0);
		if (!predict.on()) return pos;
		Vec3 eye = player.getEyePosition(partialTick);
		double dist = eye.distanceTo(pos);
		double mySpeed = Math.max(0.5, player.getDeltaMovement().length()); // Blöcke pro Tick
		double ticksAhead = Math.min(10.0, dist / mySpeed);
		Vec3 vel = target.getDeltaMovement();
		return pos.add(vel.x * ticksAhead, vel.y * ticksAhead * 0.5, vel.z * ticksAhead);
	}

	private void aimAtTarget(LocalPlayer player, float partialTick) {
		if (target == null) return;
		Vec3 point = aimPoint(player, partialTick);
		Vec3 eye = player.getEyePosition(partialTick);
		double dx = point.x - eye.x;
		double dy = point.y - eye.y;
		double dz = point.z - eye.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
		// fast genau über dem Ziel: Richtung nicht ändern (sonst springt sie um 180° -> Kamera spinnt)
		if (horizontal < 1.5) yaw = steering ? desiredYaw : player.getYRot();
		setDesired(player, yaw, Math.max(-90f, Math.min(90f, pitch)));
	}

	/** Nur nach oben/unten schauen, Richtung bleibt. */
	private void lookPitch(LocalPlayer player, float pitch) {
		setDesired(player, steering ? desiredYaw : player.getYRot(), pitch);
	}

	private void setDesired(LocalPlayer player, float yaw, float pitch) {
		desiredYaw = yaw;
		desiredPitch = pitch;
		steering = true;
		if (!smoothAim.on()) {
			// sofort hindrehen – aber den kürzesten Weg und wie eine Maus-Bewegung (sonst dreht die Kamera einmal rum)
			float dyaw = Mth.wrapDegrees(yaw - player.getYRot());
			float dpitch = pitch - player.getXRot();
			player.turn(dyaw / 0.15, dpitch / 0.15);
		}
	}

	@Override
	public void onFrame(Minecraft mc) {
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;

		LocalPlayer player = mc.player;
		if (!steering || !smoothAim.on() || player == null || mc.screen != null) return;

		// Zielpunkt jedes Bild neu (nicht nur jeden Tick), solange angegriffen wird
		if (target != null && state != State.FREE && state != State.LAUNCH) {
			float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
			aimAtTarget(player, partial);
		}

		float dyaw = Mth.wrapDegrees(desiredYaw - player.getYRot());
		float dpitch = desiredPitch - player.getXRot();

		// schnell am Anfang, langsamer am Ende
		float k = 1f - (float) Math.exp(-aimSpeed.get() * dt);
		float stepYaw = dyaw * k;
		float stepPitch = dpitch * k;

		// Höchstgeschwindigkeit (Grad pro Sekunde)
		float max = 540f * dt;
		stepYaw = Math.max(-max, Math.min(max, stepYaw));
		stepPitch = Math.max(-max, Math.min(max, stepPitch));

		// leichtes Hand-Zittern
		if (humanize.on()) {
			double t = now / 1_000_000_000.0;
			stepYaw += (float) (Math.sin(t * 7.3) * 0.6 + Math.sin(t * 13.1) * 0.3) * dt * 4f;
			stepPitch += (float) (Math.sin(t * 5.9) * 0.5 + Math.sin(t * 11.7) * 0.25) * dt * 4f;
		}

		player.turn(stepYaw / 0.15, stepPitch / 0.15);
	}

	private float aimError(LocalPlayer player) {
		if (!steering || !smoothAim.on()) return 0f;
		float dy = Math.abs(Mth.wrapDegrees(desiredYaw - player.getYRot()));
		float dp = Math.abs(desiredPitch - player.getXRot());
		return Math.max(dy, dp);
	}

	/** Humanize: Zeiten um ±20 % streuen. */
	private int vary(int ticks) {
		if (!humanize.on()) return ticks;
		int spread = Math.max(1, Math.round(ticks * 0.2f));
		return Math.max(1, ticks - spread + rng.nextInt(spread * 2 + 1));
	}

	/** Humanize: manchmal 1 Tick später (nie früher). */
	private int extraTick() {
		return humanize.on() && rng.nextInt(3) == 0 ? 1 : 0;
	}

	// ================= Inventar =================

	/** Brustplatz mit einem Hotbar-Slot tauschen (wie Taste 1–9 im Inventar über dem Brustplatz). */
	private static void swapChest(Minecraft mc, LocalPlayer player, int hotbarSlot) {
		if (hotbarSlot < 0 || hotbarSlot > 8) return;
		mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, CHEST_MENU_SLOT, hotbarSlot, ClickType.SWAP, player);
	}

	/** Hotbar-Slot für den Elytra-Tausch: zuerst eine Brustplatte, sonst ein leerer Slot. */
	private static int findSwapSlot(Inventory inv) {
		int plate = findSlot(inv, CHESTPLATE);
		if (plate != -1) return plate;
		return findSlot(inv, ItemStack::isEmpty);
	}

	private static boolean useRocket(Minecraft mc, LocalPlayer player) {
		Inventory inv = player.getInventory();
		int slot = findSlot(inv, ROCKET);
		if (slot == -1) return false;
		inv.setSelectedSlot(slot);
		mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
		return true;
	}

	private static void select(Inventory inv, Predicate<ItemStack> what) {
		int slot = findSlot(inv, what);
		if (slot != -1) inv.setSelectedSlot(slot);
	}

	private static int findSlot(Inventory inv, Predicate<ItemStack> test) {
		for (int i = 0; i < 9; i++) {
			if (test.test(inv.getItem(i))) return i;
		}
		return -1;
	}

	private static double distanceToBox(Vec3 p, AABB box) {
		double x = Math.max(box.minX, Math.min(p.x, box.maxX));
		double y = Math.max(box.minY, Math.min(p.y, box.maxY));
		double z = Math.max(box.minZ, Math.min(p.z, box.maxZ));
		return p.distanceTo(new Vec3(x, y, z));
	}

	private static void releaseJump(Minecraft mc) {
		if (mc.getWindow() == null) return;
		if (!InputUtil.isPhysicallyDown(mc, mc.options.keyJump)) mc.options.keyJump.setDown(false);
	}

	/** Hinweis in der Actionbar (nur einmal pro Problem). */
	private void warn(LocalPlayer player, String msg) {
		if (msg.equals(lastWarning)) return;
		lastWarning = msg;
		player.displayClientMessage(Component.literal(msg), true);
	}
}
