package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import dev.quiqzy.client.module.ModuleManager;

/**
 * Elytra Recast "Change Pitch": during the movement calculation (travel) the player looks at the fake pitch,
 * afterwards the real rotation is restored. Only for you (LocalPlayer).
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	/** Elytra Recast (Original-Logik): vor/nach der Bewegungs-Berechnung. aiStep = Yarn "tickMovement". */
	@Inject(method = "aiStep", at = @At("HEAD"), require = 0)
	private void zero$aiStepStart(CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer player) ModuleManager.ELYTRA_RECAST.beforeAiStep(player);
	}

	@Inject(method = "aiStep", at = @At("TAIL"), require = 0)
	private void zero$aiStepEnd(CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer player) ModuleManager.ELYTRA_RECAST.afterAiStep(player);
	}

	@Inject(method = "travel", at = @At("HEAD"), require = 0)
	private void zero$travelStart(Vec3 input, CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer player) ModuleManager.ELYTRA_RECAST.beginFakeRotation(player);
	}

	@Inject(method = "travel", at = @At("RETURN"), require = 0)
	private void zero$travelEnd(Vec3 input, CallbackInfo ci) {
		if ((Object) this instanceof LocalPlayer player) ModuleManager.ELYTRA_RECAST.endFakeRotation(player);
	}
}
