package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import dev.quiqzy.client.module.ModuleManager;

/** Animations: Schwunggeschwindigkeit deiner eigenen Hand (nur optisch). */
@Mixin(LivingEntity.class)
public abstract class SwingMixin {
	@Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true, require = 0)
	private void zero$swing(CallbackInfoReturnable<Integer> cir) {
		var m = ModuleManager.ANIMATIONS;
		if (!m.isEnabled() || (Object) this != Minecraft.getInstance().player) return;
		int base = cir.getReturnValue();
		cir.setReturnValue(Math.max(1, Math.round(base * 100f / m.swingSpeed.get())));
	}
}
