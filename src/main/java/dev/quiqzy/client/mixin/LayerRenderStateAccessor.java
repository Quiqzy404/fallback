package dev.quiqzy.client.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/** Zugriff auf die privaten Felder von ItemStackRenderState.LayerRenderState (Item Glow). */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {
	@Accessor("quads")
	List<BakedQuad> zero$quads();

	@Accessor("transform")
	ItemTransform zero$transform();
}
