package dev.quiqzy.client.render;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

import dev.quiqzy.client.QuiqzyClient;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.world.SusChunkModule;
import dev.quiqzy.client.module.world.SusChunkModule.ChunkResult;

/**
 * Zeichnet die Sus-Chunk-Markierung:
 * - der ganze Chunk-Bereich (auf Höhe der Cluster) als leicht gefüllter Kasten mit leuchtenden Kanten
 * - ein dünner Strahl aus der Mitte nach oben mit einem kleinen Würfel oben drauf
 * - optional kleine Boxen um jeden Cluster
 *
 * Mit "Durch Blöcke" wird eine eigene Render-Pipeline ohne Tiefentest benutzt
 * (nach dem offiziellen Fabric-Docs-Beispiel für 1.21.11), dann sieht man alles durch Wände.
 */
public final class SusChunkRenderer {
	private static final float EDGE = 0.028f;
	private static final float BEAM = 0.045f;

	// ---------------- Pipeline ohne Tiefentest (durch Blöcke sichtbar)
	private static final RenderPipeline THROUGH_WALLS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "pipeline/sus_chunk_through_walls"))
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.build());

	private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
	private static MappableRingBuffer vertexBuffer;

	private SusChunkRenderer() {
	}

	/** Lädt die Klasse früh, damit die Pipeline beim Start registriert wird. */
	public static void init() {
	}

	public static void render(WorldRenderContext context) {
		SusChunkModule module = ModuleManager.SUS_CHUNK;
		if (!module.isEnabled()) return;
		if (!module.beams.on() && !module.clusterBoxes.on()) return;

		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) return;

		List<ChunkResult> sus = module.susChunks();
		if (sus.isEmpty()) return;

		Vec3 cam = context.worldState().cameraRenderState.pos;
		PoseStack matrices = context.matrices();
		matrices.pushPose();
		matrices.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = matrices.last().pose();

		if (module.throughWalls.on()) {
			BufferBuilder buffer = new BufferBuilder(ALLOCATOR, THROUGH_WALLS.getVertexFormatMode(), THROUGH_WALLS.getVertexFormat());
			int quads = emit(buffer, m, module, sus, player, mc);
			matrices.popPose();
			if (quads > 0) drawThroughWalls(mc, buffer);
			else buffer.build(); // leeren Builder sauber abschließen
		} else {
			VertexConsumer buffer = context.consumers().getBuffer(RenderTypes.debugFilledBox());
			emit(buffer, m, module, sus, player, mc);
			matrices.popPose();
		}
	}

	/** Baut alle Formen. Gibt die Anzahl der Flächen zurück. */
	private static int emit(VertexConsumer b, Matrix4f m, SusChunkModule module, List<ChunkResult> sus, LocalPlayer player, Minecraft mc) {
		int faces = 0;

		float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 420.0);
		// ZERO-Orange (passt zum GUI), weich pulsierend
		int fill = ARGB.colorFromFloat(0.05f + 0.05f * pulse, 1.0f, 0.55f, 0.18f);
		int edge = ARGB.colorFromFloat(0.65f + 0.30f * pulse, 1.0f, 0.62f, 0.25f);
		int beam = ARGB.colorFromFloat(0.30f + 0.25f * pulse, 1.0f, 0.70f, 0.35f);
		int cube = ARGB.colorFromFloat(0.90f, 1.0f, 0.85f, 0.60f);
		int cluster = ARGB.colorFromFloat(0.40f + 0.35f * pulse, 1.0f, 0.78f, 0.40f);

		double eyeY = player.getEyeY();
		int worldTop = mc.level != null ? mc.level.getMaxY() : 320;
		float bob = (float) Math.sin(System.currentTimeMillis() / 600.0) * 0.25f;

		for (ChunkResult res : sus) {
			float x0 = res.pos().getMinBlockX();
			float z0 = res.pos().getMinBlockZ();
			float x1 = x0 + 16f;
			float z1 = z0 + 16f;
			float y0 = res.minY() - 0.5f;
			float y1 = res.maxY() + 1.5f;

			if (module.beams.on()) {
				// Chunk-Kasten: weiche Füllung + leuchtende Kanten
				box(b, m, x0, y0, z0, x1, y1, z1, fill);
				faces += 6;
				faces += edges(b, m, x0, y0, z0, x1, y1, z1, edge);

				// Strahl aus der Mitte nach oben + kleiner Würfel
				float cx = x0 + 8f;
				float cz = z0 + 8f;
				float top = (float) Math.min(worldTop, Math.max(y1 + 6, eyeY + 18));
				box(b, m, cx - BEAM, y1, cz - BEAM, cx + BEAM, top, cz + BEAM, beam);
				float cy = top + 0.6f + bob;
				box(b, m, cx - 0.25f, cy - 0.25f, cz - 0.25f, cx + 0.25f, cy + 0.25f, cz + 0.25f, cube);
				faces += 12;
			}

			if (module.clusterBoxes.on()) {
				for (BlockPos p : res.clusters()) {
					float px = p.getX();
					float py = p.getY();
					float pz = p.getZ();
					box(b, m, px + 0.2f, py + 0.05f, pz + 0.2f, px + 0.8f, py + 0.85f, pz + 0.8f, cluster);
					faces += 6;
				}
			}
		}
		return faces;
	}

	/** 12 Kanten eines Kastens als dünne Balken. */
	private static int edges(VertexConsumer b, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int c) {
		float e = EDGE;
		// unten
		box(b, m, x0, y0, z0, x1, y0 + e, z0 + e, c);
		box(b, m, x0, y0, z1 - e, x1, y0 + e, z1, c);
		box(b, m, x0, y0, z0, x0 + e, y0 + e, z1, c);
		box(b, m, x1 - e, y0, z0, x1, y0 + e, z1, c);
		// oben
		box(b, m, x0, y1 - e, z0, x1, y1, z0 + e, c);
		box(b, m, x0, y1 - e, z1 - e, x1, y1, z1, c);
		box(b, m, x0, y1 - e, z0, x0 + e, y1, z1, c);
		box(b, m, x1 - e, y1 - e, z0, x1, y1, z1, c);
		// senkrecht
		box(b, m, x0, y0, z0, x0 + e, y1, z0 + e, c);
		box(b, m, x1 - e, y0, z0, x1, y1, z0 + e, c);
		box(b, m, x0, y0, z1 - e, x0 + e, y1, z1, c);
		box(b, m, x1 - e, y0, z1 - e, x1, y1, z1, c);
		return 12 * 6;
	}

	/** Gefüllte Box (6 Flächen à 4 Ecken). */
	private static void box(VertexConsumer b, Matrix4f m, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int c) {
		// Front
		b.addVertex(m, minX, minY, maxZ).setColor(c);
		b.addVertex(m, maxX, minY, maxZ).setColor(c);
		b.addVertex(m, maxX, maxY, maxZ).setColor(c);
		b.addVertex(m, minX, maxY, maxZ).setColor(c);
		// Back
		b.addVertex(m, maxX, minY, minZ).setColor(c);
		b.addVertex(m, minX, minY, minZ).setColor(c);
		b.addVertex(m, minX, maxY, minZ).setColor(c);
		b.addVertex(m, maxX, maxY, minZ).setColor(c);
		// Left
		b.addVertex(m, minX, minY, minZ).setColor(c);
		b.addVertex(m, minX, minY, maxZ).setColor(c);
		b.addVertex(m, minX, maxY, maxZ).setColor(c);
		b.addVertex(m, minX, maxY, minZ).setColor(c);
		// Right
		b.addVertex(m, maxX, minY, maxZ).setColor(c);
		b.addVertex(m, maxX, minY, minZ).setColor(c);
		b.addVertex(m, maxX, maxY, minZ).setColor(c);
		b.addVertex(m, maxX, maxY, maxZ).setColor(c);
		// Top
		b.addVertex(m, minX, maxY, maxZ).setColor(c);
		b.addVertex(m, maxX, maxY, maxZ).setColor(c);
		b.addVertex(m, maxX, maxY, minZ).setColor(c);
		b.addVertex(m, minX, maxY, minZ).setColor(c);
		// Bottom
		b.addVertex(m, minX, minY, minZ).setColor(c);
		b.addVertex(m, maxX, minY, minZ).setColor(c);
		b.addVertex(m, maxX, minY, maxZ).setColor(c);
		b.addVertex(m, minX, minY, maxZ).setColor(c);
	}

	// ---------------- Zeichnen ohne Tiefentest (wie im Fabric-Docs-Beispiel 1.21.11)

	private static void drawThroughWalls(Minecraft mc, BufferBuilder buffer) {
		MeshData mesh = buffer.buildOrThrow();
		MeshData.DrawState state = mesh.drawState();
		VertexFormat format = state.format();

		int size = state.vertexCount() * format.getVertexSize();
		if (vertexBuffer == null || vertexBuffer.size() < size) {
			if (vertexBuffer != null) vertexBuffer.close();
			vertexBuffer = new MappableRingBuffer(() -> QuiqzyClient.MOD_ID + " sus chunk", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
		}

		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		try (GpuBuffer.MappedView view = encoder.mapBuffer(vertexBuffer.currentBuffer().slice(0, mesh.vertexBuffer().remaining()), false, true)) {
			MemoryUtil.memCopy(mesh.vertexBuffer(), view.data());
		}
		GpuBuffer vertices = vertexBuffer.currentBuffer();

		GpuBuffer indices;
		VertexFormat.IndexType indexType;
		if (THROUGH_WALLS.getVertexFormatMode() == VertexFormat.Mode.QUADS) {
			mesh.sortQuads(ALLOCATOR, RenderSystem.getProjectionType().vertexSorting());
			indices = THROUGH_WALLS.getVertexFormat().uploadImmediateIndexBuffer(mesh.indexBuffer());
			indexType = mesh.drawState().indexType();
		} else {
			RenderSystem.AutoStorageIndexBuffer shapes = RenderSystem.getSequentialBuffer(THROUGH_WALLS.getVertexFormatMode());
			indices = shapes.getBuffer(state.indexCount());
			indexType = shapes.type();
		}

		GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
				.writeTransform(RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
				() -> QuiqzyClient.MOD_ID + " sus chunk pass",
				mc.getMainRenderTarget().getColorTextureView(), OptionalInt.empty(),
				mc.getMainRenderTarget().getDepthTextureView(), OptionalDouble.empty())) {
			pass.setPipeline(THROUGH_WALLS);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transforms);
			pass.setVertexBuffer(0, vertices);
			pass.setIndexBuffer(indices, indexType);
			pass.drawIndexed(0, 0, state.indexCount(), 1);
		}

		mesh.close();
		vertexBuffer.rotate();
	}
}
