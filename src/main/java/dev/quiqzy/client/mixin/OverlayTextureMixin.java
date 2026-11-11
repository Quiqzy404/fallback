package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;

import dev.quiqzy.client.module.visuals.HitEffectsModule;

/** Merkt sich die Overlay-Textur (rotes Treffer-Aufblinken), damit Hit Effects sie umfärben kann. */
@Mixin(OverlayTexture.class)
public abstract class OverlayTextureMixin {
	@Shadow
	@Final
	private DynamicTexture texture;

	@Inject(method = "<init>", at = @At("RETURN"), require = 0)
	private void zero$keep(CallbackInfo ci) {
		HitEffectsModule.overlayTexture = this.texture;
	}
}
