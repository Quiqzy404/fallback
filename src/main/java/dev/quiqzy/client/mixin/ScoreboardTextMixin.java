package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;

import dev.quiqzy.client.module.ModuleManager;

/** Text Replace: Titel, Zeilen und Zahlen der Scoreboard-Seitenleiste. */
@Mixin(Gui.class)
public abstract class ScoreboardTextMixin {
	@ModifyArg(method = "displayScoreboardSidebar", require = 0, index = 1,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"))
	private Component zero$replace(Component text) {
		var m = ModuleManager.TEXT_REPLACE;
		return m.scoreboard.on() ? m.apply(text) : text;
	}

	/** Breite mit dem neuen Text berechnen, damit nichts überlappt. */
	@ModifyArg(method = "displayScoreboardSidebar", require = 0, index = 0,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I"))
	private net.minecraft.network.chat.FormattedText zero$width(net.minecraft.network.chat.FormattedText text) {
		var m = ModuleManager.TEXT_REPLACE;
		return m.scoreboard.on() && text instanceof Component c ? m.apply(c) : text;
	}
}
