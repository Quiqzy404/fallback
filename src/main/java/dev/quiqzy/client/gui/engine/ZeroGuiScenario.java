package dev.quiqzy.client.gui.engine;

import dev.quiqzy.client.QuiqzyClient;
import dev.quiqzy.client.gui.ZeroGui;
import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.ModuleManager;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

final class ZeroGuiScenario {
   private static final float ROW_X = 280.0F;
   private static final float ROW_Y = 169.0F;
   private static final float CHIP_X = 400.0F;

   private ZeroGuiScenario() {
   }

   private static void gui(Minecraft mc, Consumer<ZeroGui> action) {
      if (mc.screen instanceof ZeroGui gui) {
         action.accept(gui);
      } else {
         QuiqzyClient.LOGGER.warn("[Selbsttest] GUI ist nicht offen: {}", mc.screen);
      }
   }

   private static void log(String what, Object value) {
      QuiqzyClient.LOGGER.info("[Selbsttest] {}: {}", what, value);
   }

   static void add(BiConsumer<String, Consumer<Minecraft>> step) {
      if (System.getenv("ZERO_SELFTEST_ONLY") != null) {
         InGameScenario.add(step);
         return;
      }
      step.accept("combat", mc -> {
         ZeroGui gui = new ZeroGui();
         mc.setScreen(gui);
         gui.testShow(Module.Category.COMBAT, false, ModuleManager.STUN_SLAM);
      });
      step.accept("list", mc -> gui(mc, g -> g.testShow(Module.Category.COMBAT, false, null)));
      step.accept("click", mc -> gui(mc, g -> {
         boolean before = ModuleManager.ANCHOR.isEnabled();
         g.testClick(280.0F, 169.0F);
         log("Anchor per Klick", before + " -> " + ModuleManager.ANCHOR.isEnabled());
      }));
      step.accept("bind", mc -> gui(mc, g -> {
         g.testClick(400.0F, 169.0F);
         log("Nach Klick auf Chip", g.testState());
      }));
      step.accept("bound", mc -> gui(mc, g -> {
         g.keyPressed(new KeyEvent(82, 0, 0));
         log("Anchor-Keybind", ModuleManager.ANCHOR.keybind());
      }));
      step.accept("typed", mc -> gui(mc, g -> {
         g.testClick(400.0F, 23.0F);

         for (char c : "stun".toCharArray()) {
            g.charTyped(new CharacterEvent(c, 0));
         }

         log("Nach Tippen", g.testState());
      }));
      step.accept("visuals_grid", mc -> gui(mc, g -> g.testShow(Module.Category.VISUALS, true, null)));
      step.accept("esp_settings", mc -> gui(mc, g -> g.testShow(Module.Category.VISUALS, false, ModuleManager.ENTITY_ESP)));
      step.accept("tracers", mc -> gui(mc, g -> g.testShow(Module.Category.VISUALS, false, ModuleManager.TRACERS)));
      step.accept("world", mc -> gui(mc, g -> g.testShow(Module.Category.WORLD, false, ModuleManager.BLOCK_ESP)));
      step.accept("picker", mc -> gui(mc, g -> g.testPicker(ModuleManager.BLOCK_ESP.blocks)));
      step.accept("color_window", mc -> gui(mc, g -> g.testColor("minecraft:diamond_ore")));
      step.accept("world_grid", mc -> {
         mc.setScreen(new ZeroGui());
         gui(mc, g -> g.testShow(Module.Category.WORLD, true, ModuleManager.BLOCK_ESP));
      });
      step.accept("settings", mc -> {
         mc.setScreen(new ZeroGui());
         gui(mc, g -> g.testPage(1));
      });
      step.accept("configs", mc -> gui(mc, g -> g.testPage(2)));
      step.accept("keybinds", mc -> gui(mc, g -> {
         g.testPage(3);
         log("Keybinds-Seite", g.testState());
      }));
      step.accept("closing", mc -> gui(mc, g -> {
         ModuleManager.ANCHOR.setEnabled(false);
         ModuleManager.ANCHOR.setKeybind(-1);
         log("Vor dem Ende", g.testState());
         log("FPS mit offener GUI", mc.getFps());
      }));
      InGameScenario.add(step);
   }
}
