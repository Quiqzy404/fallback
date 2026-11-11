package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;

import dev.quiqzy.client.module.ModuleManager;

/**
 * Sound Chunks - Server-Weg: fängt positionierte Sound-Pakete ab (z. B. Lagerfeuer)
 * und harte Level-Events (Crafter/Brewing/Dispenser), bevor der Client sie abarbeitet.
 */
@Mixin(ClientPacketListener.class)
public abstract class SoundEventSpyMixin {
	@Inject(method = "handleSoundEvent", at = @At("HEAD"), require = 0)
	private void zero$spySound(ClientboundSoundPacket packet, CallbackInfo ci) {
		try {
			ModuleManager.SOUND_CHUNK.onSoundPacket(packet);
		} catch (Throwable ignored) {
		}
	}

	@Inject(method = "handleLevelEvent", at = @At("HEAD"), require = 0)
	private void zero$spyLevelEvent(ClientboundLevelEventPacket packet, CallbackInfo ci) {
		try {
			if (packet.isGlobalEvent()) return; // globale Events haben keine verwertbare Position
			var p = packet.getPos();
			ModuleManager.SOUND_CHUNK.onLevelEvent(packet.getType(), p.getX(), p.getY(), p.getZ());
		} catch (Throwable ignored) {
		}
	}
}
