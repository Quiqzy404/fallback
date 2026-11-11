package dev.quiqzy.client.mixin;

import java.util.function.Consumer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;

import dev.quiqzy.client.module.ModuleManager;

/**
 * Base Finder gegen Anti-Xray: greift jede Block-Entity aus dem Chunk-Paket ab, bevor der Client sie
 * (bei verstecktem Block-Zustand) verwirft. So werden Truhen/Shulker/Spawner unter Deepslate erkannt.
 */
@Mixin(ClientPacketListener.class)
public abstract class ChunkBlockEntitySpyMixin {
	@ModifyArg(method = "updateLevelChunk", require = 0, index = 4,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientChunkCache;replaceWithPacketData(IILnet/minecraft/network/FriendlyByteBuf;Ljava/util/Map;Ljava/util/function/Consumer;)Lnet/minecraft/world/level/chunk/LevelChunk;"))
	private Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> zero$spyBlockEntities(Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> original) {
		return out -> original.accept((pos, type, nbt) -> {
			try {
				ModuleManager.BASE_FINDER.onServerBlockEntity(pos, type);
				dev.quiqzy.client.util.SpawnerTracker.onServerBlockEntity(pos, type, nbt);
			} catch (Throwable ignored) {
			}
			out.accept(pos, type, nbt);
		});
	}
}
