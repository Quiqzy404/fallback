package dev.quiqzy.client.render;

import java.util.List;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import org.joml.Vector3f;

import dev.quiqzy.client.QuiqzyClient;

/**
 * Item Glow: haelt das Item eine aufblasbare, einfarbige "Huelle" direkt um das Item-Modell.
 * Die Quads des Items werden von ihrem eigenen Zentrum aus vergroessert und als
 * halbtransparente Geometrie mit exakt derselben Pose wie das Item gesubmitet -
 */
public final class HandGlowRenderer {
	/** Position-Color, kein Depth-Write: das Item bleibt komplett sichtbar, nur der Rand leuchtet. */
	private static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "pipeline/hand_glow"))
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.withDepthWrite(false)
			.withCull(false)
			.build());

	public static final RenderType TYPE = RenderType.create("quiqzy_hand_glow", RenderSetup.builder(PIPELINE).createRenderSetup());

	private HandGlowRenderer() {
	}

	/**
	 * Submits den Glow: jede Modell-Ebene wird mit derselben ItemTransform wie das Item
	 * aufgeblasen (Faktor 1 + inflate) und als einfaerbige Schale gesubmitet.
	 */
	public static void submitGlow(Object stateObj, PoseStack poseStack, SubmitNodeCollector collector,
			int color, int inflatePercent) {
		dev.quiqzy.client.mixin.ItemStackRenderStateAccessor state = (dev.quiqzy.client.mixin.ItemStackRenderStateAccessor) stateObj;
		net.minecraft.world.item.ItemDisplayContext ctx = state.zero$displayContext();
		net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState[] layers = state.zero$layers();
		int count = state.zero$activeLayerCount();
		if (layers == null || count <= 0) return;

		float factor = 1f + inflatePercent / 100f;

		for (int i = 0; i < Math.min(count, layers.length); i++) {
			var layer = layers[i];
			if (layer == null) continue;
			List<BakedQuad> quads = ((dev.quiqzy.client.mixin.LayerRenderStateAccessor) layer).zero$quads();
			if (quads == null || quads.isEmpty()) continue;
			net.minecraft.client.renderer.block.model.ItemTransform transform =
					((dev.quiqzy.client.mixin.LayerRenderStateAccessor) layer).zero$transform();

			poseStack.pushPose();
			if (transform != null) transform.apply(ctx.leftHand(), poseStack.last());
			final float f = factor;
			final List<BakedQuad> q = quads;
			collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> emit(pose, buffer, q, f, color));
			poseStack.popPose();
		}
	}

	/** Quads vom eigenen Zentrum aus aufblasen und als Farbflaeche ausgeben. */
	private static void emit(PoseStack.Pose pose, VertexConsumer buffer, List<BakedQuad> quads, float factor, int color) {
		Vector3f center = new Vector3f();
		Vector3f p = new Vector3f();
		for (BakedQuad quad : quads) {
			center.set(0f);
			for (int k = 0; k < 4; k++) center.add(quad.position(k));
			center.mul(0.25f);
			for (int k = 0; k < 4; k++) {
				p.set(quad.position(k)).sub(center).mul(factor).add(center);
				buffer.addVertex(pose, p.x, p.y, p.z).setColor(color);
			}
		}
	}

	/** ARGB-Farbe aus RGB + Alpha-Prozent. */
	public static int color(int rgb, int opacityPercent) {
		return ARGB.colorFromFloat(opacityPercent / 100f,
				(rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f);
	}
}
