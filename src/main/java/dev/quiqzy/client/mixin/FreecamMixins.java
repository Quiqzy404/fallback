package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.visuals.FreecamModule;

/** Freecam: Kamera-Position/-Drehung ersetzen, Maus dreht nur die Kamera, Mausrad = Tempo. */
public final class FreecamMixins {
	private FreecamMixins() {
	}

	@Mixin(Camera.class)
	public abstract static class CameraMixin {
		@Shadow
		protected abstract void setPosition(double x, double y, double z);

		@Shadow
		protected abstract void setRotation(float yaw, float pitch);

		@Inject(method = "setup", at = @At("RETURN"), require = 0)
		private void zero$freecam(Level level, Entity entity, boolean detached, boolean mirror, float partialTick, CallbackInfo ci) {
			FreecamModule m = ModuleManager.FREECAM;
			if (!m.active()) return;
			setRotation(m.yaw, m.pitch);
			setPosition(m.x, m.y, m.z);
		}

		/** "Losgelöst" -> dein Spieler wird von außen gezeichnet. */
		@Inject(method = "isDetached", at = @At("RETURN"), cancellable = true, require = 0)
		private void zero$detached(CallbackInfoReturnable<Boolean> cir) {
			if (ModuleManager.FREECAM.active() && ModuleManager.FREECAM.outsideBody()) cir.setReturnValue(true);
		}
	}

	@Mixin(Entity.class)
	public abstract static class TurnMixin {
		@Inject(method = "turn", at = @At("HEAD"), cancellable = true, require = 0)
		private void zero$turn(double dx, double dy, CallbackInfo ci) {
			FreecamModule m = ModuleManager.FREECAM;
			if (m.active() && (Object) this == Minecraft.getInstance().player) {
				m.turn(dx, dy);
				ci.cancel();
			}
		}
	}

	@Mixin(MouseHandler.class)
	public abstract static class ScrollMixin {
		@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 0)
		private void zero$scroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
			FreecamModule m = ModuleManager.FREECAM;
			if (m.active() && Minecraft.getInstance().screen == null && yOffset != 0) {
				m.scroll(yOffset);
				ci.cancel();
			}
		}
	}
}
