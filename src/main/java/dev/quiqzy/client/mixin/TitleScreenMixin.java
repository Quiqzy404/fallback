package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.screens.TitleScreen;

import dev.quiqzy.client.gui.engine.ZeroFont;
import dev.quiqzy.client.gui.engine.ZeroRender;
import dev.quiqzy.client.module.ModuleManager;

/**
 * Custom Start Menu: ZERO-Logo statt Minecraft-Logo und ein warmer, animierter Schimmer über dem Panorama.
 * Alles andere (Knöpfe) bleibt Vanilla, damit nichts kaputtgeht.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
	@Inject(method = "render", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/screens/TitleScreen;renderPanorama(Lnet/minecraft/client/gui/GuiGraphics;F)V", shift = At.Shift.AFTER), require = 0)
	private void zero$menuGlow(GuiGraphics g, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!ModuleManager.START_MENU.isEnabled()) return;
		float w = g.guiWidth(), h = g.guiHeight();
		float t = (System.currentTimeMillis() % 100000L) / 1000f;
		// Abdunkeln + warmer Verlauf von unten
		ZeroRender.gradient(g, 0f, 0f, w, h, 0f, 0x50000000, 0xB00A0605, 90f);
		// langsam wandernde Leuchtflecken
		for (int i = 0; i < 3; i++) {
			float cx = w * (0.25f + 0.25f * i) + (float) Math.sin(t * 0.25 + i * 2.1) * w * 0.12f;
			float cy = h * 0.78f + (float) Math.cos(t * 0.3 + i * 1.7) * h * 0.06f;
			float r = h * (0.32f + 0.05f * i);
			ZeroRender.glow(g, cx - r, cy - r * 0.45f, cx + r, cy + r * 0.45f, r * 0.45f, r * 0.6f,
					ZeroRender.alpha(i == 1 ? 0xFFF2B635 : 0xFFF0782C, 0.22f));
		}
	}

	@Redirect(method = "render", require = 0, at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/LogoRenderer;renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V"))
	private void zero$logo(LogoRenderer logo, GuiGraphics g, int width, float alpha) {
		if (!ModuleManager.START_MENU.isEnabled()) {
			logo.renderLogo(g, width, alpha);
			return;
		}
		float size = 44f;
		String txt = "ZERO";
		float tw = ZeroFont.LOGO.width(txt, size, 0f);
		float cx = width / 2f;
		float y = 26f;
		float pulse = 0.65f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 700.0);
		int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
		ZeroRender.glow(g, cx - tw / 2f - 10f, y, cx + tw / 2f + 10f, y + size, 18f, 26f, ZeroRender.alpha(0xFFF0782C, 0.35f * pulse * alpha));
		ZeroRender.text(g, ZeroFont.LOGO, txt, cx - tw / 2f, y, size, 0f,
				(a << 24) | 0xFFC08A, (a << 24) | 0xEC5F1C);
		String sub = "C L I E N T";
		float sw = ZeroFont.BOLD.width(sub, 7f, 0f);
		ZeroRender.text(g, ZeroFont.BOLD, sub, cx - sw / 2f, y + size + 2f, 7f, ZeroRender.alpha(0xFFE8D2C0, 0.8f * alpha));
	}
}
