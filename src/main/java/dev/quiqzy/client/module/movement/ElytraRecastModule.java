package dev.quiqzy.client.module.movement;

import java.lang.reflect.Field;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.InputUtil;
import dev.quiqzy.client.util.Reflect;

/**
 * Elytra Recast – core logic ported from the original mod "Elytra Recast" by InLieuOfLuna
 * (MIT License, Copyright (c) 2021 Luna, github.com/InLieuOfLuna/elytra-recast), extras like in CatLean / ThunderHack.
 *
 * Core trick (original): the moment gliding stops (you land), the elytra is cast again right away –
 * EVEN ON THE GROUND – and the jump cooldown is cut, so you bounce straight back into flight.
 *
 * - Auto Jump / Auto Walk: holds Jump and Forward for you
 * - as soon as you fall (and are not gliding), the elytra is cast again immediately
 * - no jump cooldown -> you bounce right off the ground
 * - Change Pitch: for the flight physics and for the server you look at "Pitch" (55°) – your camera stays normal.
 *   This is what makes the ground bounce fast. (Done in LivingEntityMixin / LocalPlayerMixin.)
 * - Exploit Strict/Strong: wiggles yaw / pitch a bit (helps on some anti-cheats)
 */
public final class ElytraRecastModule extends Module {
	public final Setting.Bool changePitch = add(new Setting.Bool("change_pitch", "Change Pitch", true));
	public final Setting.Int pitch = add(new Setting.Int("pitch", "Pitch", 55, -90, 90, "°"));
	public final Setting.Bool autoWalk = add(new Setting.Bool("auto_walk", "Auto Walk", true));
	public final Setting.Bool autoJump = add(new Setting.Bool("auto_jump", "Auto Jump", true));
	public final Setting.Int jumpCooldown = add(new Setting.Int("jump_cooldown", "Jump Cooldown", 2, 0, 10, " t"));
	public final Setting.Bool allowBroken = add(new Setting.Bool("allow_broken", "Allow Broken", true));
	public final Setting.Choice exploit = add(new Setting.Choice("exploit", "Exploit", "None", "None", "Strict", "Strong"));

	/** LivingEntity#noJumpDelay – intermediary: class_1309.field_6228 */
	private static final Field NO_JUMP_DELAY = Reflect.field("net.minecraft.class_1309", "field_6228", "I");
	private static final long START = System.currentTimeMillis();

	private float jitter;

	// Original-Logik: Zustand vom letzten aiStep
	private boolean previousElytra;
	private boolean awaitingElytra;

	// gespeicherte echte Rotation, während der Fake-Pitch aktiv ist
	private float savedPitch;
	private float savedYaw;
	private boolean faking;

	public ElytraRecastModule() {
		super("elytra_recast", "Elytra Recast", "Bounces you along with your elytra (like CatLean)", Category.MOVEMENT);
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getWindow() == null) return;
		// Tasten loslassen, außer du drückst sie wirklich
		if (!InputUtil.isPhysicallyDown(mc, mc.options.keyUp)) mc.options.keyUp.setDown(false);
		if (!InputUtil.isPhysicallyDown(mc, mc.options.keyJump)) mc.options.keyJump.setDown(false);
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) return;

		if (autoJump.on()) mc.options.keyJump.setDown(true);
		if (autoWalk.on()) mc.options.keyUp.setDown(true);

		if (!player.isFallFlying() && player.fallDistance > 0 && checkElytra(mc, player)) {
			castElytra(player);
		}

		jitter = 20f * (float) Math.sin((System.currentTimeMillis() - START) / 50.0);
	}

	// ================= Original Elytra Recast (aufgerufen aus LivingEntityMixin) =================

	/** Vor aiStep: Sprung-Cooldown kürzen (Original: PlayerJumpCooldownMixin). */
	public void beforeAiStep(LocalPlayer player) {
		if (!isEnabled() || NO_JUMP_DELAY == null) return;
		try {
			if (NO_JUMP_DELAY.getInt(player) > jumpCooldown.get()) NO_JUMP_DELAY.setInt(player, jumpCooldown.get());
		} catch (Throwable ignored) {
		}
	}

	/** Nach aiStep: hört das Gleiten gerade auf -> sofort neu casten, auch am Boden (Original: PlayerMixin). */
	public void afterAiStep(LocalPlayer player) {
		if (!isEnabled()) return;
		Minecraft mc = Minecraft.getInstance();
		boolean elytra = player.isFallFlying();

		if (awaitingElytra) {
			if (elytra) awaitingElytra = false;
		} else if (!elytra && previousElytra) {
			if (checkElytra(mc, player)) castElytra(player);
			awaitingElytra = checkElytra(mc, player);
		}
		previousElytra = elytra;
	}

	/** Wie das Original: ignoriert, ob du auf dem Boden stehst. */
	private void castElytra(LocalPlayer player) {
		if (player.isInWater() || player.hasEffect(MobEffects.LEVITATION)) return;
		if (!elytraOk(player)) return;
		player.startFallFlying();
		player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
	}

	private boolean checkElytra(Minecraft mc, LocalPlayer player) {
		if (!mc.options.keyJump.isDown()) return false;
		if (player.getAbilities().flying || player.isPassenger() || player.onClimbable()) return false;
		return elytraOk(player);
	}

	private boolean elytraOk(LocalPlayer player) {
		ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
		if (!chest.is(Items.ELYTRA)) return false;
		boolean broken = chest.nextDamageWillBreak();
		return !broken || allowBroken.on();
	}

	// ================= Fake-Rotation (aufgerufen aus den Mixins) =================

	/** Vor Flugphysik / Positions-Paket: Rotation auf den Fake-Pitch stellen. */
	public void beginFakeRotation(LocalPlayer player) {
		if (!isEnabled() || !changePitch.on() || faking) return;
		faking = true;
		savedPitch = player.getXRot();
		savedYaw = player.getYRot();

		float p = pitch.get();
		switch (exploit.get()) {
			case "Strict" -> player.setYRot(savedYaw + jitter);
			case "Strong" -> p = p - Math.abs(jitter / 2f);
			default -> {
			}
		}
		player.setXRot(p);
	}

	/** Danach: echte Rotation zurück (Kamera bleibt normal). */
	public void endFakeRotation(LocalPlayer player) {
		if (!faking) return;
		faking = false;
		player.setXRot(savedPitch);
		player.setYRot(savedYaw);
	}
}
