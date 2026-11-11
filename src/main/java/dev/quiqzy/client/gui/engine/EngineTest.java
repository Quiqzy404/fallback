package dev.quiqzy.client.gui.engine;

import dev.quiqzy.client.QuiqzyClient;
import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;

public final class EngineTest {
   private static final String WORLD = "zero_selftest";
   private static final int SETTLE_TICKS = 25;
   private static Deque<EngineTest.Step> steps;
   private static EngineTest.Step current;
   private static int wait;
   private static boolean worldRequested;
   private static int titleTicks;
   private static String name;

   private EngineTest() {
   }

   public static void init() {
      name = System.getenv("ZERO_SELFTEST");
      if (name != null && !name.isBlank()) {
         ClientTickEvents.END_CLIENT_TICK.register(EngineTest::tick);
      }
   }

   private static void tick(Minecraft mc) {
      // Selbsttest: nicht pausieren, wenn das Fenster den Fokus verliert
      mc.options.pauseOnLostFocus = false;
      if (mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen) {
         mc.setScreen(null);
      }
      if (mc.level != null && mc.player != null) {
         if (steps == null) {
            if (mc.screen == null) {
               String size = System.getenv("ZERO_SELFTEST_SIZE");
               if (size == null || size.isBlank()) {
                  size = "1920x1080";
               }

               String[] wh = size.split("x");
               GLFW.glfwSetWindowSize(mc.getWindow().handle(), Integer.parseInt(wh[0]), Integer.parseInt(wh[1]));
               steps = new ArrayDeque<>();
               ZeroGuiScenario.add(EngineTest::step);
               wait = 60;
            }
         } else if (wait > 0) {
            wait--;
         } else if (current != null) {
            String lbl = current.label.contains("@") ? current.label.substring(0, current.label.indexOf('@')) : current.label;
            String file = name + "_" + lbl + ".png";
            current = null;
            mc.execute(
               () -> Screenshot.grab(
                  new File(mc.gameDirectory.getPath()), file, mc.getMainRenderTarget(), 1, msg -> QuiqzyClient.LOGGER.info("[Selbsttest] {}", msg.getString())
               )
            );
            wait = 3;
         } else {
            current = steps.pollFirst();
            if (current == null) {
               mc.stop();
            } else {
               current.action.accept(mc);
               int at = current.label.indexOf('@');
               wait = at >= 0 ? Integer.parseInt(current.label.substring(at + 1)) : 25;
            }
         }
      } else {
         if (mc.screen instanceof TitleScreen && !worldRequested) {
            // erst das Startmenü fotografieren, dann die Welt öffnen
            if (++titleTicks == 60) {
               mc.execute(() -> Screenshot.grab(new File(mc.gameDirectory.getPath()), name + "_title.png", mc.getMainRenderTarget(), 1,
                  msg -> QuiqzyClient.LOGGER.info("[Selbsttest] {}", msg.getString())));
            }
            if (titleTicks < 70) return;
            worldRequested = true;
            openWorld(mc);
         }
      }
   }

   public static void step(String label, Consumer<Minecraft> action) {
      steps.addLast(new EngineTest.Step(label, action));
   }

   private static void openWorld(Minecraft mc) {
      Screen title = mc.screen;
      if (mc.getLevelSource().levelExists("zero_selftest")) {
         mc.createWorldOpenFlows().openWorld("zero_selftest", () -> mc.setScreen(title));
      } else {
         LevelSettings settings = new LevelSettings(
            "zero_selftest", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(FeatureFlags.DEFAULT_FLAGS), WorldDataConfiguration.DEFAULT
         );
         mc.createWorldOpenFlows()
            .createFreshLevel("zero_selftest", settings, new WorldOptions(20261107L, true, false), WorldPresets::createNormalWorldDimensions, title);
      }
   }

   private record Step(String label, Consumer<Minecraft> action) {
   }
}
