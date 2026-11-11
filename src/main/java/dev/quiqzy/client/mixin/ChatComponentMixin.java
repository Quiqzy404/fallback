package dev.quiqzy.client.mixin;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

import dev.quiqzy.client.module.ModuleManager;

/** Chat Timestamps: "[19:42] " (grau) vor jede Nachricht. */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@ModifyVariable(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
			at = @At("HEAD"), argsOnly = true, require = 0)
	private Component zero$timestamp(Component msg) {
		if (!ModuleManager.CHAT_TWEAKS.isEnabled() || msg == null) return msg;
		String t = LocalTime.now().format(DateTimeFormatter.ofPattern(ModuleManager.CHAT_TWEAKS.seconds.on() ? "HH:mm:ss" : "HH:mm"));
		return Component.literal("[" + t + "] ").withStyle(ChatFormatting.DARK_GRAY).append(msg);
	}
}
