package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import dev.quiqzy.client.module.ModuleManager;

/**
 * Sound Chunks - Client-Weg: Ofen, Hochofen und Smoker spielen ihre Sounds direkt auf dem Client
 * (animateTick am Block, exakte Blockposition, kein Paket vom Server). Der Client ruft sein eigenes
 * ClientLevel.playLocalSound - deshalb hier und nicht im Mutter-Level abfangen.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelSoundSpyMixin {
	@Inject(method = "playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V",
			at = @At("HEAD"), require = 1)
	private void zero$spyLocalSound(double x, double y, double z, SoundEvent sound, SoundSource source,
			float volume, float pitch, boolean delay, CallbackInfo ci) {
		try {
			ModuleManager.SOUND_CHUNK.onLocalSound(sound, source, x, y, z);
		} catch (Throwable ignored) {
		}
	}
}
