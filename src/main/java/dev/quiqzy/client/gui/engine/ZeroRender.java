package dev.quiqzy.client.gui.engine;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.quiqzy.client.mixin.BufferBuilderAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

public final class ZeroRender {
   private ZeroRender() {
   }

   public static void rect(GuiGraphics g, float x1, float y1, float x2, float y2, float radius, int color) {
      shape(g, ZeroPipelines.SHAPE, TextureSetup.noTexture(), x1, y1, x2, y2, 0.0F, radius, 0.0F, 0.0F, 0.0F, color, color);
   }

   public static void gradient(GuiGraphics g, float x1, float y1, float x2, float y2, float radius, int from, int to, float angle) {
      shape(g, ZeroPipelines.SHAPE, TextureSetup.noTexture(), x1, y1, x2, y2, 0.0F, radius, 0.0F, 0.0F, (float)Math.toRadians(angle), from, to);
   }

   public static void stroke(GuiGraphics g, float x1, float y1, float x2, float y2, float radius, float width, int color) {
      shape(g, ZeroPipelines.SHAPE, TextureSetup.noTexture(), x1, y1, x2, y2, 0.0F, radius, 0.0F, width, 0.0F, color, color);
   }

   public static void strokeGradient(GuiGraphics g, float x1, float y1, float x2, float y2, float radius, float width, int from, int to, float angle) {
      shape(g, ZeroPipelines.SHAPE, TextureSetup.noTexture(), x1, y1, x2, y2, 0.0F, radius, 0.0F, width, (float)Math.toRadians(angle), from, to);
   }

   public static void circle(GuiGraphics g, float cx, float cy, float radius, int color) {
      rect(g, cx - radius, cy - radius, cx + radius, cy + radius, radius, color);
   }

   public static void glow(GuiGraphics g, float x1, float y1, float x2, float y2, float radius, float blur, int color) {
      shape(g, ZeroPipelines.SHAPE, TextureSetup.noTexture(), x1, y1, x2, y2, blur, radius, blur, 0.0F, 0.0F, color, color);
   }

   public static void glass(GuiGraphics g, float x1, float y1, float x2, float y2, float radius, int from, int to, float angle, float opacity) {
      GpuTextureView blur = ZeroBlur.result();
      float radians = (float)Math.toRadians(angle);
      if (blur == null) {
         shape(g, ZeroPipelines.SHAPE, TextureSetup.noTexture(), x1, y1, x2, y2, 0.0F, radius, 0.0F, 0.0F, radians, alpha(from, opacity), alpha(to, opacity));
      } else {
         GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
         shape(g, ZeroPipelines.GLASS, TextureSetup.singleTexture(blur, sampler), x1, y1, x2, y2, 0.0F, radius, 0.0F, opacity, radians, from, to);
      }
   }

   private static void shape(
      GuiGraphics g, RenderPipeline pipeline, TextureSetup textures, float x1, float y1, float x2, float y2,
      float pad, float radius, float soft, float stroke, float angle, int from, int to
   ) {
      shape(g, pipeline, textures, x1, y1, x2, y2, pad, radius, soft, stroke, angle, from, to, 0.0F, 0.0F, 0.0F, 0.0F);
   }

   /** Radialer Verlauf: Mitte = inner, Rand = outer. */
   public static void radial(GuiGraphics g, float x1, float y1, float x2, float y2, float radius, int inner, int outer) {
      shape(g, ZeroPipelines.SHAPE, TextureSetup.noTexture(), x1, y1, x2, y2, 0.0F, radius, 0.0F, 0.0F, 0.0F, inner, outer, 1.0F, 0.0F, 0.0F, 0.0F);
   }

   /**
    * Bogen/Ring mit runden Enden. Winkel in Grad, 0 = rechts, im Uhrzeigersinn; -90 = oben.
    * sweep 360 = voller Ring. Farbe verläuft von "from" (Anfang) nach "to" (Ende).
    */
   public static void arc(GuiGraphics g, float cx, float cy, float radius, float width, float startDeg, float sweepDeg, int from, int to) {
      if (sweepDeg <= 0.0F) {
         return;
      }
      float r = radius + width * 0.5F;
      shape(g, ZeroPipelines.SHAPE, TextureSetup.noTexture(), cx - r, cy - r, cx + r, cy + r, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, from, to,
         2.0F, (float)Math.toRadians(startDeg), (float)Math.toRadians(Math.min(360.0F, sweepDeg)), width);
   }

   public static void ring(GuiGraphics g, float cx, float cy, float radius, float width, int color) {
      arc(g, cx, cy, radius, width, 0.0F, 360.0F, color, color);
   }

   private static void shape(
      GuiGraphics g, RenderPipeline pipeline, TextureSetup textures, float x1, float y1, float x2, float y2,
      float pad, float radius, float soft, float stroke, float angle, int from, int to,
      float mode, float e1, float e2, float e3
   ) {
      if (!(x2 <= x1) && !(y2 <= y1)) {
         from = dev.quiqzy.client.util.Theme.tint(from);
         to = dev.quiqzy.client.util.Theme.tint(to);
         if (from >>> 24 != 0 || to >>> 24 != 0 || pipeline != ZeroPipelines.SHAPE) {
            radius = Math.min(radius, Math.min(x2 - x1, y2 - y1) / 2.0F);
            Matrix3x2f pose = new Matrix3x2f(g.pose());
            ScreenRectangle scissor = g.scissorStack.peek();
            g.guiRenderState
               .submitGuiElement(
                  new ZeroRender.ShapeState(
                     pipeline,
                     textures,
                     pose,
                     x1,
                     y1,
                     x2,
                     y2,
                     pad,
                     radius,
                     soft,
                     stroke,
                     angle,
                     from,
                     to,
                     mode,
                     e1,
                     e2,
                     e3,
                     scissor,
                     bounds(x1 - pad, y1 - pad, x2 + pad, y2 + pad, pose, scissor)
                  )
               );
         }
      }
   }

   @Nullable
   private static ScreenRectangle bounds(float x1, float y1, float x2, float y2, Matrix3x2fc pose, @Nullable ScreenRectangle scissor) {
      int ix = (int)Math.floor(x1);
      int iy = (int)Math.floor(y1);
      ScreenRectangle r = new ScreenRectangle(ix, iy, (int)Math.ceil(x2) - ix, (int)Math.ceil(y2) - iy).transformMaxBounds(pose);
      return scissor != null ? scissor.intersection(r) : r;
   }

   public static void text(GuiGraphics g, ZeroFont font, String s, float x, float y, float size, int color) {
      text(g, font, s, x, y, size, 0.0F, color, color);
   }

   public static void textRight(GuiGraphics g, ZeroFont font, String s, float right, float y, float size, int color) {
      text(g, font, s, right - font.width(s, size, 0.0F), y, size, 0.0F, color, color);
   }

   public static void textCentered(GuiGraphics g, ZeroFont font, String s, float cx, float y, float size, int color) {
      text(g, font, s, cx - font.width(s, size, 0.0F) / 2.0F, y, size, 0.0F, color, color);
   }

   public static void icon(GuiGraphics g, char icon, float cx, float cy, float size, int color) {
      ZeroFont.Glyph glyph = ZeroFont.ICONS.glyph(icon);
      if (glyph != null) {
         float baseline = cy - (glyph.y0() + glyph.y1()) / 2.0F * size;
         float start = cx - (glyph.x0() + glyph.x1()) / 2.0F * size;
         layout(g, ZeroFont.ICONS, String.valueOf(icon), start, baseline, size, 0.0F, color, color);
      }
   }

   public static void text(GuiGraphics g, ZeroFont font, String s, float x, float y, float size, float tracking, int from, int to) {
      layout(g, font, s, x, y + font.ascent() * size, size, tracking, from, to);
   }

   private static void layout(GuiGraphics g, ZeroFont font, String s, float x, float baseline, float size, float tracking, int from, int to) {
      from = dev.quiqzy.client.util.Theme.tint(from);
      to = dev.quiqzy.client.util.Theme.tint(to);
      if (!s.isEmpty() && from >>> 24 != 0) {
         if (Math.abs(g.pose().m10()) < 1.0E-4F) {
            float scale = Minecraft.getInstance().getWindow().getGuiScale();
            float poseY = g.pose().m11();
            float poseShift = g.pose().m21();
            baseline = (Math.round((baseline * poseY + poseShift) * scale) / scale - poseShift) / poseY;
         }

         float[] quads = new float[s.length() * 16];
         int n = 0;
         int previous = -1;
         float pen = x;
         float minX = Float.MAX_VALUE;
         float maxX = -Float.MAX_VALUE;
         float minY = Float.MAX_VALUE;
         float maxY = -Float.MAX_VALUE;
         int i = 0;

         while (i < s.length()) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            ZeroFont.Glyph glyph = font.glyph(cp);
            if (glyph != null) {
               pen += font.kerning(previous, glyph.index()) * size;
               previous = glyph.index();
               if (glyph.x1() > glyph.x0()) {
                  float x0 = pen + glyph.x0() * size;
                  float x1 = pen + glyph.x1() * size;
                  float y0 = baseline + glyph.y0() * size;
                  float y1 = baseline + glyph.y1() * size;
                  quads[n++] = x0;
                  quads[n++] = y0;
                  quads[n++] = glyph.u0();
                  quads[n++] = glyph.v0();
                  quads[n++] = x0;
                  quads[n++] = y1;
                  quads[n++] = glyph.u0();
                  quads[n++] = glyph.v1();
                  quads[n++] = x1;
                  quads[n++] = y1;
                  quads[n++] = glyph.u1();
                  quads[n++] = glyph.v1();
                  quads[n++] = x1;
                  quads[n++] = y0;
                  quads[n++] = glyph.u1();
                  quads[n++] = glyph.v0();
                  minX = Math.min(minX, x0);
                  maxX = Math.max(maxX, x1);
                  minY = Math.min(minY, y0);
                  maxY = Math.max(maxY, y1);
               }

               pen += glyph.advance() * size + tracking;
            }
         }

         if (n != 0) {
            Matrix3x2f pose = new Matrix3x2f(g.pose());
            ScreenRectangle scissor = g.scissorStack.peek();
            GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, true);
            g.guiRenderState
               .submitGuiElement(
                  new ZeroRender.TextState(
                     TextureSetup.singleTexture(font.view(), sampler),
                     pose,
                     quads,
                     n,
                     minY,
                     maxY,
                     from,
                     to,
                     scissor,
                     bounds(minX, minY, maxX, maxY, pose, scissor)
                  )
               );
         }
      }
   }

   public static int alpha(int color, float factor) {
      int a = Math.max(0, Math.min(255, Math.round((color >>> 24) * factor)));
      return a << 24 | color & 16777215;
   }

   public static int lerp(int a, int b, float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      int aa = a >>> 24;
      int ar = a >> 16 & 0xFF;
      int ag = a >> 8 & 0xFF;
      int ab = a & 0xFF;
      int ba = b >>> 24;
      int br = b >> 16 & 0xFF;
      int bg = b >> 8 & 0xFF;
      int bb = b & 0xFF;
      return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
   }

   private record ShapeState(
      RenderPipeline pipeline,
      TextureSetup textureSetup,
      Matrix3x2f pose,
      float x1,
      float y1,
      float x2,
      float y2,
      float pad,
      float radius,
      float soft,
      float stroke,
      float angle,
      int from,
      int to,
      float mode,
      float e1,
      float e2,
      float e3,
      @Nullable ScreenRectangle scissorArea,
      @Nullable ScreenRectangle bounds
   ) implements GuiElementRenderState {
      @Override
      public void buildVertices(VertexConsumer consumer) {
         float cx = (this.x1 + this.x2) / 2.0F;
         float cy = (this.y1 + this.y2) / 2.0F;
         float hx = (this.x2 - this.x1) / 2.0F;
         float hy = (this.y2 - this.y1) / 2.0F;
         float ex = hx + this.pad;
         float ey = hy + this.pad;
         this.vertex(consumer, cx, cy, -ex, -ey, hx, hy);
         this.vertex(consumer, cx, cy, -ex, ey, hx, hy);
         this.vertex(consumer, cx, cy, ex, ey, hx, hy);
         this.vertex(consumer, cx, cy, ex, -ey, hx, hy);
      }

      private void vertex(VertexConsumer consumer, float cx, float cy, float lx, float ly, float hx, float hy) {
         consumer.addVertexWith2DPose(this.pose, cx + lx, cy + ly).setColor(this.from);
         MemoryUtil.memPutInt(be(consumer, ZeroPipelines.COLOR2), abgr(this.to));
         long local = be(consumer, ZeroPipelines.LOCAL);
         MemoryUtil.memPutFloat(local, lx);
         MemoryUtil.memPutFloat(local + 4L, ly);
         long half = be(consumer, ZeroPipelines.HALF_SIZE);
         MemoryUtil.memPutFloat(half, hx);
         MemoryUtil.memPutFloat(half + 4L, hy);
         long params = be(consumer, ZeroPipelines.SHAPE_PARAMS);
         MemoryUtil.memPutFloat(params, this.radius);
         MemoryUtil.memPutFloat(params + 4L, this.soft);
         MemoryUtil.memPutFloat(params + 8L, this.stroke);
         MemoryUtil.memPutFloat(params + 12L, this.angle);
         long extra = be(consumer, ZeroPipelines.EXTRA);
         MemoryUtil.memPutFloat(extra, this.mode);
         MemoryUtil.memPutFloat(extra + 4L, this.e1);
         MemoryUtil.memPutFloat(extra + 8L, this.e2);
         MemoryUtil.memPutFloat(extra + 12L, this.e3);
      }

      /** beginElement: normal via Accessor-Mixin; beim Injizieren (kein Mixin) per Reflection, Methode nach Signatur gesucht. */
      private static java.lang.reflect.Method BEGIN;

      private static long be(VertexConsumer consumer, VertexFormatElement element) {
         if (consumer instanceof BufferBuilderAccessor acc) return acc.zero$beginElement(element);
         try {
            if (BEGIN == null) {
               for (Class<?> c = consumer.getClass(); c != null && BEGIN == null; c = c.getSuperclass()) {
                  for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                     Class<?>[] p = m.getParameterTypes();
                     if (m.getReturnType() == long.class && p.length == 1 && p[0] == VertexFormatElement.class) {
                        m.setAccessible(true);
                        BEGIN = m;
                        break;
                     }
                  }
               }
            }
            return (long) BEGIN.invoke(consumer, element);
         } catch (Throwable t) {
            throw new RuntimeException("zero begin-element failed", t);
         }
      }

      private static int abgr(int argb) {
         return argb & -16711936 | argb >> 16 & 0xFF | (argb & 0xFF) << 16;
      }
   }

   private record TextState(
      TextureSetup textureSetup,
      Matrix3x2f pose,
      float[] quads,
      int length,
      float top,
      float bottom,
      int from,
      int to,
      @Nullable ScreenRectangle scissorArea,
      @Nullable ScreenRectangle bounds
   ) implements GuiElementRenderState {
      @Override
      public RenderPipeline pipeline() {
         return ZeroPipelines.TEXT;
      }

      @Override
      public void buildVertices(VertexConsumer consumer) {
         float span = Math.max(0.001F, this.bottom - this.top);

         for (int i = 0; i < this.length; i += 4) {
            float y = this.quads[i + 1];
            int color = this.from == this.to ? this.from : ZeroRender.lerp(this.from, this.to, (y - this.top) / span);
            consumer.addVertexWith2DPose(this.pose, this.quads[i], y).setUv(this.quads[i + 2], this.quads[i + 3]).setColor(color);
         }
      }
   }
}
