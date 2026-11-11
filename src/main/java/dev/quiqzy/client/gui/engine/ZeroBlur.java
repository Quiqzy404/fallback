package dev.quiqzy.client.gui.engine;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

public final class ZeroBlur {
   private static final int LEVELS = 4;
   private static final GpuTexture[] textures = new GpuTexture[5];
   private static final GpuTextureView[] views = new GpuTextureView[5];
   private static int width;
   private static int height;
   private static boolean ready;

   private ZeroBlur() {
   }

   @Nullable
   public static GpuTextureView result() {
      return ready ? views[0] : null;
   }

   public static void capture() {
      ready = false;
      RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
      GpuTextureView source = main.getColorTextureView();
      if (source != null) {
         ensureSize(main.width, main.height);
         GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
         CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
         GpuTextureView previous = source;

         for (int i = 1; i <= 4; i++) {
            pass(encoder, ZeroPipelines.BLUR_DOWN, previous, views[i], linear);
            previous = views[i];
         }

         for (int i = 3; i >= 0; i--) {
            pass(encoder, ZeroPipelines.BLUR_UP, previous, views[i], linear);
            previous = views[i];
         }

         ready = true;
      }
   }

   private static void pass(CommandEncoder encoder, RenderPipeline pipeline, GpuTextureView from, GpuTextureView to, GpuSampler sampler) {
      try (RenderPass pass = encoder.createRenderPass(() -> "ZERO blur", to, OptionalInt.empty())) {
         pass.setPipeline(pipeline);
         RenderSystem.bindDefaultUniforms(pass);
         pass.bindTexture("InSampler", from, sampler);
         pass.draw(0, 3);
      }
   }

   private static void ensureSize(int w, int h) {
      if (w != width || h != height || textures[0] == null) {
         release();
         width = w;
         height = h;
         GpuDevice device = RenderSystem.getDevice();
         int usage = 12;

         for (int i = 0; i <= 4; i++) {
            int tw = Math.max(1, w >> i);
            int th = Math.max(1, h >> i);
            textures[i] = device.createTexture("ZERO blur " + i, usage, TextureFormat.RGBA8, tw, th, 1, 1);
            views[i] = device.createTextureView(textures[i]);
         }
      }
   }

   public static void release() {
      ready = false;

      for (int i = 0; i <= 4; i++) {
         if (views[i] != null) {
            views[i].close();
         }

         if (textures[i] != null) {
            textures[i].close();
         }

         views[i] = null;
         textures[i] = null;
      }
   }
}
