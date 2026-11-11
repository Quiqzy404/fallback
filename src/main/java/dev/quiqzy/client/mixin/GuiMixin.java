package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

import dev.quiqzy.client.module.ModuleManager;

/** Custom Crosshair: Vanilla-Fadenkreuz ausblenden, wenn unseres an ist. */
@Mixin(Gui.class)
public abstract class GuiMixin {
	/** Potion HUD an -> die großen Vanilla-Effekt-Icons oben rechts ausblenden (sonst doppelt). */
	@Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true, require = 0)
	private void zero$effects(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
		if (ModuleManager.POTION_HUD.isEnabled()) ci.cancel();
	}

	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
	private void zero$crosshair(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
		if (ModuleManager.CROSSHAIR.isEnabled()) ci.cancel();
	}
}
