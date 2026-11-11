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
import dev.quiqzy.client.module.world.XrayResearchModule;
import dev.quiqzy.client.module.world.XrayResearchModule.ChunkData;
import dev.quiqzy.client.module.world.XrayResearchModule.Prediction;

/**
 * Research Overlay für Xray Research:
 * - analysierte Chunks (tatsächliche Daten) als Box + Kanten, Farbe nach Confidence oder Akzent
 * - statistische Vorhersagen (noch nicht gescannte Chunks) gedämpft nur als Kanten
 * - optionale Boxen um seltene Erze (Diamond/Emerald/Debris) und um beobachtete Reveals
 */
public final class XrayResearchRenderer {
	private static final float EDGE = 0.025f;

	// ---------------- Pipeline ohne Tiefentest (durch Blöcke sichtbar)
	private static final RenderPipeline THROUGH_WALLS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "pipeline/xray_research_through_walls"))
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.build());

	private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
	private static MappableRingBuffer vertexBuffer;

	private XrayResearchRenderer() {
	}

	/** Lädt die Klasse früh, damit die Pipeline beim Start registriert wird. */
	public static void init() {
	}

	public static void render(WorldRenderContext context) {
		XrayResearchModule module = ModuleManager.XRAY_RESEARCH;
		if (!module.isEnabled()) return;
		if (!module.overlay.on() && !module.rareBoxes.on() && !module.revealBoxes.on()) return;

		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) return;

		List<ChunkData> chunks = module.overlay.on() ? module.highlights() : List.of();
		List<ChunkData> all = module.rareBoxes.on() || module.revealBoxes.on() ? module.allData() : List.of();
		List<Prediction> preds = module.overlay.on() && module.predicted.on() ? module.predictions() : List.of();
		if (chunks.isEmpty() && preds.isEmpty() && all.isEmpty()) return;

		Vec3 cam = context.worldState().cameraRenderState.pos;
		PoseStack matrices = context.matrices();
		matrices.pushPose();
		matrices.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = matrices.last().pose();

		if (module.throughWalls.on()) {
			BufferBuilder buffer = new BufferBuilder(ALLOCATOR, THROUGH_WALLS.getVertexFormatMode(), THROUGH_WALLS.getVertexFormat());
			int quads = emit(buffer, m, module, chunks, all, preds);
			matrices.popPose();
			if (quads > 0) drawThroughWalls(mc, buffer);
			else buffer.build(); // leeren Builder sauber abschließen
		} else {
			VertexConsumer buffer = context.consumers().getBuffer(RenderTypes.debugFilledBox());
			emit(buffer, m, module, chunks, all, preds);
			matrices.popPose();
		}
	}

	/** Baut alle Formen. Gibt die Anzahl der Flächen zurück. */
	private static int emit(VertexConsumer b, Matrix4f m, XrayResearchModule module, List<ChunkData> chunks, List<ChunkData> all, List<Prediction> preds) {
		int faces = 0;
		float op = module.overlayOpacity.get() / 100f;
		float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 500.0);
		boolean fill = !module.overlayMode.get().equals("Edges");
		boolean edges = !module.overlayMode.get().equals("Fill");
		int accent = module.accentColor.get();

		// Analysierte Chunks (tatsächliche Daten)
		for (ChunkData d : chunks) {
			float x0 = d.pos.getMinBlockX();
			float z0 = d.pos.getMinBlockZ();
			float x1 = x0 + 16f;
			float z1 = z0 + 16f;
			float y0 = d.minY == Integer.MAX_VALUE ? 0f : d.minY - 0.5f;
			float y1 = d.maxY == Integer.MIN_VALUE ? 1f : d.maxY + 1.5f;

			int col;
			if (module.colorMode.get().equals("Confidence")) {
				float c = d.confidence / 100f;
				col = lerp(0xFFE06A52, 0xFF7BE6A8, c);
			} else {
				col = accent;
			}
			int fillC = ARGB.colorFromFloat(0.10f * op, (col >> 16 & 0xFF) / 255f, (col >> 8 & 0xFF) / 255f, (col & 0xFF) / 255f);
			int edgeC = ARGB.colorFromFloat((0.55f + 0.30f * pulse) * op, (col >> 16 & 0xFF) / 255f, (col >> 8 & 0xFF) / 255f, (col & 0xFF) / 255f);

			if (fill) {
				box(b, m, x0, y0, z0, x1, y1, z1, fillC);
				faces += 6;
			}
			if (edges) {
				faces += edgesOf(b, m, x0, y0, z0, x1, y1, z1, edgeC);
			}
		}

		// Seltene Erze + Reveals in ALLEN analysierten Chunks (auch unter dem Schwellwert,
		// wichtig auf Servern mit Anti-Xray, wo kaum Chunk-Dichten zustande kommen)
		if (module.rareBoxes.on() || module.revealBoxes.on()) {
			int rareC = ARGB.colorFromFloat(0.75f * op, 0.30f, 0.92f, 0.86f); // Diamond-Ton
			int revC = ARGB.colorFromFloat(0.85f * op, 0.95f, 0.78f, 0.25f);
			for (ChunkData d : all) {
				if (module.rareBoxes.on()) {
					for (BlockPos p : d.rarePos) {
						box(b, m, p.getX() + 0.15f, p.getY() + 0.15f, p.getZ() + 0.15f,
								p.getX() + 0.85f, p.getY() + 0.85f, p.getZ() + 0.85f, rareC);
						faces += 6;
					}
				}
				if (module.revealBoxes.on() && !d.reveals.isEmpty()) {
					for (BlockPos p : d.reveals) {
						box(b, m, p.getX() + 0.05f, p.getY() + 0.05f, p.getZ() + 0.05f,
								p.getX() + 0.95f, p.getY() + 0.95f, p.getZ() + 0.95f, revC);
						faces += 6;
					}
				}
			}
		}

		// Statistische Vorhersagen (nicht gescannt): gedämpfte Kanten mit Puls
		for (Prediction p : preds) {
			if (!edges) continue;
			float x0 = p.pos().getMinBlockX();
			float z0 = p.pos().getMinBlockZ();
			float x1 = x0 + 16f;
			float z1 = z0 + 16f;
			float c = p.confidence() / 100f;
			int col = lerp(0xFF9A6A8F, 0xFFA78BFA, c);
			int edgeC = ARGB.colorFromFloat((0.18f + 0.16f * pulse) * op, (col >> 16 & 0xFF) / 255f, (col >> 8 & 0xFF) / 255f, (col & 0xFF) / 255f);
			faces += edgesOf(b, m, x0, 0f, z0, x1, 1f, z1, edgeC);
		}

		return faces;
	}

	/** 12 Kanten eines Kastens als dünne Balken. */
	private static int edgesOf(VertexConsumer b, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int c) {
		float e = EDGE;
		box(b, m, x0, y0, z0, x1, y0 + e, z0 + e, c);
		box(b, m, x0, y0, z1 - e, x1, y0 + e, z1, c);
		box(b, m, x0, y0, z0, x0 + e, y0 + e, z1, c);
		box(b, m, x1 - e, y0, z0, x1, y0 + e, z1, c);
		box(b, m, x0, y1 - e, z0, x1, y1, z0 + e, c);
		box(b, m, x0, y1 - e, z1 - e, x1, y1, z1, c);
		box(b, m, x0, y1 - e, z0, x0 + e, y1, z1, c);
		box(b, m, x1 - e, y1 - e, z0, x1, y1, z1, c);
		box(b, m, x0, y0, z0, x0 + e, y1, z0 + e, c);
		box(b, m, x1 - e, y0, z0, x1, y1, z0 + e, c);
		box(b, m, x0, y0, z1 - e, x0 + e, y1, z1, c);
		box(b, m, x1 - e, y0, z1 - e, x1, y1, z1, c);
		return 12 * 6;
	}

	/** Gefüllte Box (6 Flächen à 4 Ecken). */
	private static void box(VertexConsumer b, Matrix4f m, float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int c) {
		b.addVertex(m, minX, minY, maxZ).setColor(c);
		b.addVertex(m, maxX, minY, maxZ).setColor(c);
		b.addVertex(m, maxX, maxY, maxZ).setColor(c);
		b.addVertex(m, minX, maxY, maxZ).setColor(c);
		b.addVertex(m, maxX, minY, minZ).setColor(c);
		b.addVertex(m, minX, minY, minZ).setColor(c);
		b.addVertex(m, minX, maxY, minZ).setColor(c);
		b.addVertex(m, maxX, maxY, minZ).setColor(c);
		b.addVertex(m, minX, minY, minZ).setColor(c);
		b.addVertex(m, minX, minY, maxZ).setColor(c);
		b.addVertex(m, minX, maxY, maxZ).setColor(c);
		b.addVertex(m, minX, maxY, minZ).setColor(c);
		b.addVertex(m, maxX, minY, maxZ).setColor(c);
		b.addVertex(m, maxX, minY, minZ).setColor(c);
		b.addVertex(m, maxX, maxY, minZ).setColor(c);
		b.addVertex(m, maxX, maxY, maxZ).setColor(c);
		b.addVertex(m, minX, maxY, maxZ).setColor(c);
		b.addVertex(m, maxX, maxY, maxZ).setColor(c);
		b.addVertex(m, maxX, maxY, minZ).setColor(c);
		b.addVertex(m, minX, maxY, minZ).setColor(c);
		b.addVertex(m, minX, minY, minZ).setColor(c);
		b.addVertex(m, maxX, minY, minZ).setColor(c);
		b.addVertex(m, maxX, minY, maxZ).setColor(c);
		b.addVertex(m, minX, minY, maxZ).setColor(c);
	}

	/** Farbe zwischen a und b interpolieren. */
	private static int lerp(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
		int br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
		return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
	}

	// ---------------- Zeichnen ohne Tiefentest (wie im Fabric-Docs-Beispiel 1.21.11)

	private static void drawThroughWalls(Minecraft mc, BufferBuilder buffer) {
		MeshData mesh = buffer.buildOrThrow();
		MeshData.DrawState state = mesh.drawState();
		VertexFormat format = state.format();

		int size = state.vertexCount() * format.getVertexSize();
		if (vertexBuffer == null || vertexBuffer.size() < size) {
			if (vertexBuffer != null) vertexBuffer.close();
			vertexBuffer = new MappableRingBuffer(() -> QuiqzyClient.MOD_ID + " xray research", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
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
				() -> QuiqzyClient.MOD_ID + " xray research pass",
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
