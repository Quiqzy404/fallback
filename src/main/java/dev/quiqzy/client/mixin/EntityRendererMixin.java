package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;

import dev.quiqzy.client.module.ModuleManager;

/** Nametags: Vanilla-Namen ausblenden, wenn unsere eigenen Nametags an sind. */
public final class EntityRendererMixin {
	private EntityRendererMixin() {
	}

	@Mixin(EntityRenderer.class)
	public abstract static class Base {
		@Inject(method = "submitNameTag", at = @At("HEAD"), cancellable = true, require = 0)
		private void zero$hideName(EntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState cam, CallbackInfo ci) {
			if (ModuleManager.NAMETAGS.isEnabled()) ci.cancel();
		}
	}

	@Mixin(AvatarRenderer.class)
	public abstract static class Avatar {
		@Inject(method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
				at = @At("HEAD"), cancellable = true, require = 0)
		private void zero$hidePlayerName(AvatarRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState cam, CallbackInfo ci) {
			if (ModuleManager.NAMETAGS.isEnabled() && ModuleManager.NAMETAGS.players.on()) ci.cancel();
		}
	}
}
