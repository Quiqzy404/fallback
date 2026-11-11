package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;

import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.visuals.AmbienceModule;

/**
 * Ambience: client world only (never the singleplayer server world).
 * require = 0: if a method is not found, Ambience simply does nothing instead of crashing.
 */
@Mixin(Level.class)
public abstract class LevelMixin {
	@Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true, require = 0)
	private void zero$ambienceTime(CallbackInfoReturnable<Long> cir) {
		AmbienceModule m = ModuleManager.AMBIENCE;
		if (m.isEnabled() && (Object) this instanceof ClientLevel) {
			cir.setReturnValue(m.dayTime());
		}
	}

	@Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true, require = 0)
	private void zero$ambienceRain(float partialTick, CallbackInfoReturnable<Float> cir) {
		AmbienceModule m = ModuleManager.AMBIENCE;
		if (m.isEnabled() && m.clearWeather.on() && (Object) this instanceof ClientLevel) {
			cir.setReturnValue(0f);
		}
	}

	@Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true, require = 0)
	private void zero$ambienceThunder(float partialTick, CallbackInfoReturnable<Float> cir) {
		AmbienceModule m = ModuleManager.AMBIENCE;
		if (m.isEnabled() && m.clearWeather.on() && (Object) this instanceof ClientLevel) {
			cir.setReturnValue(0f);
		}
	}
}
