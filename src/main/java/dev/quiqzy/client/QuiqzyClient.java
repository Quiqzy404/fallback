package dev.quiqzy.client;

import com.mojang.blaze3d.platform.InputConstants.Type;
import dev.quiqzy.client.config.QuiqzyConfig;
import dev.quiqzy.client.gui.ZeroGui;
import dev.quiqzy.client.gui.engine.EngineTest;
import dev.quiqzy.client.gui.engine.ZeroPipelines;
import dev.quiqzy.client.hud.ZeroHud;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.render.BlockEspRenderer;
import dev.quiqzy.client.render.EspRenderer;
import dev.quiqzy.client.render.SusChunkRenderer;
import dev.quiqzy.client.render.SoundChunkRenderer;
import dev.quiqzy.client.render.SpawnerRenderer;
import dev.quiqzy.client.util.SpawnerTracker;
import dev.quiqzy.client.util.UiSounds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.EndMain;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class QuiqzyClient implements ClientModInitializer {
   public static final String MOD_ID = "quiqzy";
   public static final Logger LOGGER = LoggerFactory.getLogger("ZERO Client");
   public static final Category CATEGORY = Category.register(Identifier.fromNamespaceAndPath("quiqzy", "main"));
   public static final KeyMapping OPEN_GUI = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.quiqzy.open_gui", Type.KEYSYM, 344, CATEGORY));
   public static final KeyMapping ZOOM = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.quiqzy.zoom", Type.KEYSYM, 67, CATEGORY));
   public static final KeyMapping ADD_WAYPOINT = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.quiqzy.add_waypoint", Type.KEYSYM, -1, CATEGORY));
   public static final KeyMapping LAUNCH_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.quiqzy.instant_launch", Type.KEYSYM, -1, CATEGORY));
   public static final KeyMapping INSPECT = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.quiqzy.inspect", Type.KEYSYM, 82, CATEGORY));
   public static final KeyMapping HUD_EDITOR = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.quiqzy.hud_editor", Type.KEYSYM, -1, CATEGORY));

   public void onInitializeClient() {
      ZeroPipelines.init();
      EngineTest.init();
      QuiqzyConfig.load();
      ClientTickEvents.START_CLIENT_TICK.register(ModuleManager::startTick);
      dev.quiqzy.client.util.ReplaceCommand.register();
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)mc -> {
         while (OPEN_GUI.consumeClick()) {
            if (mc.screen == null) {
               openGui(mc);
            }
         }

         while (ADD_WAYPOINT.consumeClick()) {
            if (ModuleManager.WAYPOINTS.isEnabled()) {
               ModuleManager.WAYPOINTS.addHere(mc, "");
               UiSounds.added();
            }
         }

         while (LAUNCH_KEY.consumeClick()) {
            if (ModuleManager.INSTANT_LAUNCH.isEnabled()) {
               ModuleManager.INSTANT_LAUNCH.launch(mc);
            }
         }

         while (INSPECT.consumeClick()) {
            ModuleManager.WEAPON_SKINS.trick();
         }

         while (HUD_EDITOR.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new dev.quiqzy.client.hud.HudEditorScreen());
         }

         SpawnerTracker.tick(mc);
         ModuleManager.tick(mc);
      });
      ClientChunkEvents.CHUNK_LOAD.register(ModuleManager.SUS_CHUNK::onChunkLoad);
      ClientChunkEvents.CHUNK_LOAD.register(ModuleManager.BASE_FINDER::onChunkLoad);
      ClientChunkEvents.CHUNK_UNLOAD.register(ModuleManager.BASE_FINDER::onChunkUnload);
      ClientChunkEvents.CHUNK_UNLOAD.register(ModuleManager.SUS_CHUNK::onChunkUnload);
      ClientChunkEvents.CHUNK_UNLOAD.register(ModuleManager.SOUND_CHUNK::onChunkUnload);
      ClientChunkEvents.CHUNK_LOAD.register(ModuleManager.BLOCK_ESP::onChunkLoad);
      ClientChunkEvents.CHUNK_UNLOAD.register(ModuleManager.BLOCK_ESP::onChunkUnload);
      ClientChunkEvents.CHUNK_LOAD.register(SpawnerTracker::onChunkLoad);
      ClientChunkEvents.CHUNK_UNLOAD.register(SpawnerTracker::onChunkUnload);
      ClientChunkEvents.CHUNK_LOAD.register(ModuleManager.XRAY_RESEARCH::onChunkLoad);
      ClientChunkEvents.CHUNK_UNLOAD.register(ModuleManager.XRAY_RESEARCH::onChunkUnload);
      ClientChunkEvents.CHUNK_LOAD.register(ModuleManager.ACTIVITY_INSPECTOR::onChunkLoad);
      ClientChunkEvents.CHUNK_UNLOAD.register(ModuleManager.ACTIVITY_INSPECTOR::onChunkUnload);
      ClientChunkEvents.CHUNK_LOAD.register(ModuleManager.BASE_RESEARCH::onChunkLoad);
      ClientChunkEvents.CHUNK_UNLOAD.register(ModuleManager.BASE_RESEARCH::onChunkUnload);
      dev.quiqzy.client.render.ActivityInspectorRenderer.init();
      WorldRenderEvents.BEFORE_TRANSLUCENT.register(dev.quiqzy.client.render.ActivityInspectorRenderer::render);
      dev.quiqzy.client.render.BaseFinderResearchRenderer.init();
      WorldRenderEvents.BEFORE_TRANSLUCENT.register(dev.quiqzy.client.render.BaseFinderResearchRenderer::render);
      dev.quiqzy.client.render.XrayResearchRenderer.init();
      WorldRenderEvents.BEFORE_TRANSLUCENT.register(dev.quiqzy.client.render.XrayResearchRenderer::render);
      SusChunkRenderer.init();
      WorldRenderEvents.BEFORE_TRANSLUCENT.register(SusChunkRenderer::render);
      SoundChunkRenderer.init();
      WorldRenderEvents.BEFORE_TRANSLUCENT.register(SoundChunkRenderer::render);
      SpawnerRenderer.init();
      WorldRenderEvents.BEFORE_TRANSLUCENT.register(SpawnerRenderer::render);
      BlockEspRenderer.init();
      WorldRenderEvents.BEFORE_TRANSLUCENT.register(BlockEspRenderer::render);
      EspRenderer.init();
      WorldRenderEvents.BEFORE_TRANSLUCENT.register(EspRenderer::render);
      WorldRenderEvents.BEFORE_TRANSLUCENT.register(dev.quiqzy.client.render.FxRenderer::render);
      WorldRenderEvents.END_MAIN.register((EndMain)context -> ModuleManager.frame(Minecraft.getInstance()));
      HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("quiqzy", "hud"), (g, dt) -> {
         // Eigenes Engine-HUD (gleicher Look wie die GUI) – kein Browser mehr nötig
         ZeroHud.render(g, dt);
      });
      LOGGER.info("ZERO Client loaded - GUI on Right Shift");
   }

   private static void openGui(Minecraft mc) {
      UiSounds.gui(true);
      reopenGui(mc);
   }

   public static void reopenGui(Minecraft mc) {
      mc.setScreen(new ZeroGui());
   }
}
