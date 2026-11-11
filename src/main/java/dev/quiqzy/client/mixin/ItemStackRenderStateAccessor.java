package dev.quiqzy.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;

/** Zugriff auf die package-privaten Felder von ItemStackRenderState (Item Glow). */
@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {
	@Accessor("displayContext")
	ItemDisplayContext zero$displayContext();

	@Accessor("activeLayerCount")
	int zero$activeLayerCount();

	@Accessor("layers")
	ItemStackRenderState.LayerRenderState[] zero$layers();
}
