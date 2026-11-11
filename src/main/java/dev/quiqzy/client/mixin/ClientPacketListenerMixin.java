package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.PositionMoveRotation;

/**
 * Base Finder Research: Server-bestätigte Spieler-Positionen mitschneiden.
 * ClientboundPlayerPositionPacket = Korrektur/Rubberband des Servers -> echte,
 * server-seitig bestätigte Position (Forschungsdaten).
 * require = 0: falls die Methode fehlt, einfach nichts tun statt zu crashen.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@Inject(method = "handleMovePlayer", at = @At("HEAD"), require = 0)
	private void zero$serverPosition(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
		try {
			PositionMoveRotation change = packet.change();
			dev.quiqzy.client.module.ModuleManager.BASE_RESEARCH.onServerPosition(change.position());
		} catch (Throwable ignored) {
		}
	}
}
