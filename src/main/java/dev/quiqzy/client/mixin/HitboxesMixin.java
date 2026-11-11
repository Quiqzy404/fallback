package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Entity;

import dev.quiqzy.client.module.ModuleManager;

@Mixin(Entity.class)
public abstract class HitboxesMixin {
	@Inject(method = "getPickRadius", at = @At("RETURN"), cancellable = true, require = 0)
	private void zero$pick(CallbackInfoReturnable<Float> cir) {
		float extra = ModuleManager.HITBOXES.extra((Entity) (Object) this);
		if (extra > 0f) cir.setReturnValue(cir.getReturnValue() + extra);
	}
}
