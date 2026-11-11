package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import dev.quiqzy.client.module.ModuleManager;

/**
 * Jede Blockänderung im Client (Server-Paket oder eigenes Abbauen/Setzen) sofort an Block ESP melden,
 * damit Markierungen ohne Verzögerung erscheinen/verschwinden.
 * require = 0: falls die Methode fehlt, einfach nichts tun statt zu crashen.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@Inject(method = "sendBlockUpdated", at = @At("HEAD"), require = 0)
	private void zero$blockChanged(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
		try {
			ModuleManager.BLOCK_ESP.onBlockChanged(pos, newState);
			dev.quiqzy.client.util.SpawnerTracker.onBlockChanged(pos, newState);
			ModuleManager.XRAY_RESEARCH.onBlockUpdate(pos, oldState, newState);
			ModuleManager.BASE_RESEARCH.onBlockUpdate(pos, newState);
		} catch (Throwable ignored) {
		}
	}
}
