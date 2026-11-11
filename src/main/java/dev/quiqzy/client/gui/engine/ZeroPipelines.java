package dev.quiqzy.client.gui.engine;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Builder;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.mojang.blaze3d.vertex.VertexFormatElement.Type;
import com.mojang.blaze3d.vertex.VertexFormatElement.Usage;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class ZeroPipelines {
   static final VertexFormatElement COLOR2 = element(Type.UBYTE, Usage.COLOR, 4);
   static final VertexFormatElement LOCAL = element(Type.FLOAT, Usage.GENERIC, 2);
   static final VertexFormatElement HALF_SIZE = element(Type.FLOAT, Usage.GENERIC, 2);
   static final VertexFormatElement SHAPE_PARAMS = element(Type.FLOAT, Usage.GENERIC, 4);
   static final VertexFormatElement EXTRA = element(Type.FLOAT, Usage.GENERIC, 4);
   static final VertexFormat SHAPE_FORMAT = VertexFormat.builder()
      .add("Position", VertexFormatElement.POSITION)
      .add("Color", VertexFormatElement.COLOR)
      .add("Color2", COLOR2)
      .add("Local", LOCAL)
      .add("HalfSize", HALF_SIZE)
      .add("Shape", SHAPE_PARAMS)
      .add("Extra", EXTRA)
      .build();
   public static final RenderPipeline SHAPE = gui("zero_shape", "zero_shape", "zero_shape", SHAPE_FORMAT, false);
   public static final RenderPipeline GLASS = gui("zero_glass", "zero_shape", "zero_glass", SHAPE_FORMAT, true);
   public static final RenderPipeline TEXT = gui("zero_text", "zero_text", "zero_text", DefaultVertexFormat.POSITION_TEX_COLOR, true);
   public static final RenderPipeline BLUR_DOWN = blur("zero_blur_down");
   public static final RenderPipeline BLUR_UP = blur("zero_blur_up");

   private ZeroPipelines() {
   }

   public static void init() {
   }

   private static RenderPipeline gui(String name, String vertex, String fragment, VertexFormat format, boolean textured) {
      Builder builder = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
         .withLocation(id("pipeline/" + name))
         .withVertexShader(id("core/" + vertex))
         .withFragmentShader(id("core/" + fragment))
         .withBlend(BlendFunction.TRANSLUCENT)
         .withVertexFormat(format, Mode.QUADS)
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST);
      if (textured) {
         builder.withSampler("Sampler0");
      }

      return RenderPipelines.register(builder.build());
   }

   private static RenderPipeline blur(String name) {
      return RenderPipelines.register(
         RenderPipeline.builder()
            .withLocation(id("pipeline/" + name))
            .withVertexShader("core/screenquad")
            .withFragmentShader(id("core/" + name))
            .withSampler("InSampler")
            .withoutBlend()
            .withDepthWrite(false)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withVertexFormat(DefaultVertexFormat.EMPTY, Mode.TRIANGLES)
            .build()
      );
   }

   private static VertexFormatElement element(Type type, Usage usage, int count) {
      for (int id = 31; id > 0; id--) {
         if (VertexFormatElement.byId(id) == null) {
            return VertexFormatElement.register(id, 0, type, usage, count);
         }
      }

      throw new IllegalStateException("Keine freie Vertex-Element-ID");
   }

   private static Identifier id(String path) {
      return Identifier.fromNamespaceAndPath("quiqzy", path);
   }
}
