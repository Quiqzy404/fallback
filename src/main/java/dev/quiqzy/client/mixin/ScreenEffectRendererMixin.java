package dev.quiqzy.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.visuals.LowFireModule;

/** Low Fire: die Flammen am Bildschirm tiefer setzen (und etwas kleiner), damit man mehr sieht. */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
	@Inject(method = "renderFire", at = @At("HEAD"), require = 0)
	private static void zero$lowFireStart(PoseStack pose, MultiBufferSource source, TextureAtlasSprite sprite, CallbackInfo ci) {
		pose.pushPose();
		LowFireModule m = ModuleManager.LOW_FIRE;
		if (m.isEnabled()) {
			float h = m.height.get() / 100f;
			// tiefer setzen und flach stauchen -> nur ein schmaler, weicher Flammenrand unten
			pose.translate(0f, -0.95f * (1f - h), 0f);
			pose.scale(1f, 0.25f + 0.75f * h, 1f);
		}
	}

	@Inject(method = "renderFire", at = @At("RETURN"), require = 0)
	private static void zero$lowFireEnd(PoseStack pose, MultiBufferSource source, TextureAtlasSprite sprite, CallbackInfo ci) {
		pose.popPose();
	}
}
