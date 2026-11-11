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
import dev.quiqzy.client.module.world.SpawnerNotiModule;
import dev.quiqzy.client.module.world.WaypointsModule;
import dev.quiqzy.client.util.SpawnerTracker;
import dev.quiqzy.client.util.SpawnerTracker.SpawnerEntry;

/**
 * Zeichnet Beacon Beams bis zum Build-Limit und leuchtende Boxen über gefundenen Spawnern.
 */
public final class SpawnerRenderer {
	private static final float EDGE = 0.025f;

	private static final RenderPipeline THROUGH_WALLS = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
			.withLocation(Identifier.fromNamespaceAndPath(QuiqzyClient.MOD_ID, "pipeline/spawner_beam_through_walls"))
			.withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
			.build());

	private static final ByteBufferBuilder ALLOCATOR = new ByteBufferBuilder(RenderType.SMALL_BUFFER_SIZE);
	private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
	private static final Vector3f MODEL_OFFSET = new Vector3f();
	private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
	private static MappableRingBuffer vertexBuffer;

	private SpawnerRenderer() {
	}

	public static void init() {
	}

	public static void render(WorldRenderContext context) {
		SpawnerNotiModule module = ModuleManager.SPAWNER_NOTI;
		if (!module.isEnabled()) return;
		if (!module.beam.on() && !module.box.on()) return;

		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) return;

		String dim = WaypointsModule.dimensionId(mc.level);
		List<SpawnerEntry> spawners = SpawnerTracker.getActiveSpawners(dim);
		if (spawners.isEmpty()) return;

		Vec3 cam = context.worldState().cameraRenderState.pos;
		PoseStack matrices = context.matrices();
		matrices.pushPose();
		matrices.translate(-cam.x, -cam.y, -cam.z);
		Matrix4f m = matrices.last().pose();

		if (module.throughWalls.on()) {
			BufferBuilder buffer = new BufferBuilder(ALLOCATOR, THROUGH_WALLS.getVertexFormatMode(), THROUGH_WALLS.getVertexFormat());
			int quads = emit(buffer, m, module, spawners, player, mc);
			matrices.popPose();
			if (quads > 0) drawThroughWalls(mc, buffer);
			else buffer.build();
		} else {
			VertexConsumer buffer = context.consumers().getBuffer(RenderTypes.debugFilledBox());
			emit(buffer, m, module, spawners, player, mc);
			matrices.popPose();
		}
	}

	private static int emit(VertexConsumer b, Matrix4f m, SpawnerNotiModule module, List<SpawnerEntry> spawners, LocalPlayer player, Minecraft mc) {
		int faces = 0;
		int baseColor = module.beamColor.get();
		float r = (baseColor >> 16 & 0xFF) / 255f;
		float g = (baseColor >> 8 & 0xFF) / 255f;
		float bl = (baseColor & 0xFF) / 255f;
		int coreColor = module.coreColor.get();
		float cr = (coreColor >> 16 & 0xFF) / 255f;
		float cg = (coreColor >> 8 & 0xFF) / 255f;
		float cb = (coreColor & 0xFF) / 255f;

		int fill = ARGB.colorFromFloat(0.08f, r, g, bl);
		int edge = ARGB.colorFromFloat(0.55f, r, g, bl);

		// Pulse: Wellenform 0..1, gesteuert von Speed (Periode) und Strength (Ausschlag)
		final float beamBase = module.beamOpacity.get() / 100f;
		final float coreBase = module.coreOpacity.get() / 100f;
		float wave = 1f;
		float pulseAmt = 0f;
		if (module.pulse.on()) {
			float speed = module.pulseSpeed.get() / 100f;
			float period = Math.max(100f, 2650f / speed); // Periode in ms (Original: ~2,65 s bei 100 %)
			float t = (System.currentTimeMillis() % (long) period) / period;
			wave = 0.5f + 0.5f * (float) Math.sin(t * Math.PI * 2.0);
			pulseAmt = module.pulseStrength.get() / 100f;
		}
		float beamMix = (1f - pulseAmt) + pulseAmt * wave;
		int beamCol = ARGB.colorFromFloat(clamp01(beamBase * beamMix), r, g, bl);
		int coreCol = ARGB.colorFromFloat(clamp01(coreBase * beamMix), cr, cg, cb);

		float halfW = module.beamWidth.get() / 100f * 0.5f;
		float halfC = module.coreWidth.get() / 100f * 0.5f;

		int worldTop = mc.level != null ? mc.level.getMaxY() : 320;
		double maxDistSq = (double) module.range.get() * module.range.get();

		for (SpawnerEntry entry : spawners) {
			BlockPos pos = entry.pos;
			double d2 = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			if (d2 > maxDistSq) continue;

			float x0 = pos.getX();
			float y0 = pos.getY();
			float z0 = pos.getZ();
			float x1 = x0 + 1f;
			float y1 = y0 + 1f;
			float z1 = z0 + 1f;

			// Kasten um den Spawner-Block
			if (module.box.on()) {
				box(b, m, x0, y0, z0, x1, y1, z1, fill);
				faces += 6;
				faces += edges(b, m, x0, y0, z0, x1, y1, z1, edge);
			}

			// Beacon Beam über dem Spawner
			if (module.beam.on() && halfW > 0f) {
				float topY = switch (module.beamTop.get()) {
					case "Player +64" -> (float) Math.min(worldTop, player.getEyeY() + 64);
					case "Fixed" -> Math.min(worldTop, y1 + module.beamHeight.get());
					default -> worldTop;
				};
				if (topY > y1) {
					float cx = x0 + 0.5f;
					float cz = z0 + 0.5f;

					// Hauptstrahl (100 % = exakt 1x1 Block breit)
					box(b, m, cx - halfW, y1, cz - halfW, cx + halfW, topY, cz + halfW, beamCol);
					faces += 6;

					// Heller Kern wie beim echten Beacon-Strahl
					if (module.coreBeam.on() && halfC > 0f) {
						float cw = Math.min(halfC, halfW);
						box(b, m, cx - cw, y1, cz - cw, cx + cw, topY, cz + cw, coreCol);
						faces += 6;
					}
				}
			}
		}

		return faces;
	}

	private static float clamp01(float v) {
		return v < 0f ? 0f : v > 1f ? 1f : v;
	}

	private static int edges(VertexConsumer b, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, int c) {
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

	private static void drawThroughWalls(Minecraft mc, BufferBuilder buffer) {
		MeshData mesh = buffer.buildOrThrow();
		MeshData.DrawState state = mesh.drawState();
		VertexFormat format = state.format();

		int size = state.vertexCount() * format.getVertexSize();
		if (vertexBuffer == null || vertexBuffer.size() < size) {
			if (vertexBuffer != null) vertexBuffer.close();
			vertexBuffer = new MappableRingBuffer(() -> QuiqzyClient.MOD_ID + " spawner beam", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_MAP_WRITE, size);
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
				() -> QuiqzyClient.MOD_ID + " spawner pass",
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
