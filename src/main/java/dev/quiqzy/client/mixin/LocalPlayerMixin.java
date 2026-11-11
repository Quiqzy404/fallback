package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.player.LocalPlayer;

import dev.quiqzy.client.module.ModuleManager;

/**
 * Elytra Recast "Change Pitch": the movement packet to the server contains the fake pitch,
 * your camera keeps the real one.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Inject(method = "sendPosition", at = @At("HEAD"), require = 0)
	private void zero$sendStart(CallbackInfo ci) {
		ModuleManager.ELYTRA_RECAST.beginFakeRotation((LocalPlayer) (Object) this);
	}

	@Inject(method = "sendPosition", at = @At("RETURN"), require = 0)
	private void zero$sendEnd(CallbackInfo ci) {
		ModuleManager.ELYTRA_RECAST.endFakeRotation((LocalPlayer) (Object) this);
	}
}
