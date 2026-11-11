package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.player.Player;

import dev.quiqzy.client.module.ModuleManager;

/**
 * Totem Pops: Event 35 (Totem ausgelöst) wird direkt im ClientPacketListener verarbeitet
 * (nicht über Entity.handleEntityEvent) – deshalb hier am Paket abgreifen.
 */
@Mixin(ClientPacketListener.class)
public abstract class PopMixin {
	@Inject(method = "handleEntityEvent", at = @At("HEAD"), require = 0)
	private void zero$pop(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (packet.getEventId() != 35 || mc.level == null || !mc.isSameThread()) return;
		if (packet.getEntity(mc.level) instanceof Player p) ModuleManager.POP_COUNTER.onPop(p);
	}
}
