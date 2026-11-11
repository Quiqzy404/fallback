package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import dev.quiqzy.client.module.ModuleManager;

/** Visual FX: Gegner bekommt den leuchtenden Umriss in der Akzentfarbe. */
public final class GlowMixins {
	private GlowMixins() {
	}

	@Mixin(Minecraft.class)
	public abstract static class Glowing {
		@Inject(method = "shouldEntityAppearGlowing", at = @At("RETURN"), cancellable = true, require = 0)
		private void zero$glow(Entity e, CallbackInfoReturnable<Boolean> cir) {
			var m = ModuleManager.VISUAL_FX;
			if (m.targetOutline.on() && m.isTarget(e)) cir.setReturnValue(true);
			var gl = ModuleManager.GLASS;
			if (gl.outline.on() && gl.applies(e)) cir.setReturnValue(true);
		}
	}

	@Mixin(Entity.class)
	public abstract static class TeamColor {
		@Inject(method = "getTeamColor", at = @At("RETURN"), cancellable = true, require = 0)
		private void zero$color(CallbackInfoReturnable<Integer> cir) {
			var gl = ModuleManager.GLASS;
			if (gl.outline.on() && gl.applies((Entity) (Object) this)) {
				cir.setReturnValue(gl.color.get() & 0xFFFFFF);
				return;
			}
			var m = ModuleManager.VISUAL_FX;
			if (m.targetOutline.on() && m.isTarget((Entity) (Object) this)) {
				cir.setReturnValue(ModuleManager.HIT_EFFECTS.palette()[1] & 0xFFFFFF);
			}
		}
	}
}
