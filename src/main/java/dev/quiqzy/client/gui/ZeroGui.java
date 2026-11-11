package dev.quiqzy.client.gui;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.InputConstants.Type;
import dev.quiqzy.client.QuiqzyClient;
import dev.quiqzy.client.config.QuiqzyConfig;
import dev.quiqzy.client.gui.engine.ZeroBlur;
import dev.quiqzy.client.gui.engine.ZeroFont;
import dev.quiqzy.client.gui.engine.ZeroRender;
import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.Setting;
import dev.quiqzy.client.util.UiSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

public final class ZeroGui extends Screen {
   private static final float W = 480.0F;
   private static final float H = 312.0F;
   private static final float SIDEBAR = 122.0F;
   private static final float R = 13.0F;
   private static final float PAD = 14.0F;
   private static final float ROW_H = 38.0F;
   private static final float CARD_H = 62.0F;
   private static final float SET_ROW = 21.0F;
   private static final float SET_SLIDER = 27.0F;
   private static final float SET_POINT = 18.0F;
   private static final int TEXT = -725272;
   private static final int MUTED = -6516598;
   private static final int FAINT = -9476768;
   private static final int ACCENT = -1014209;
   private static final int ACCENT_HI = -19858;
   private static final int ACCENT_DK = -1286372;
   private static final int LINE = 318767103;
   private static final int FIELD = 234881023;
   private static final int FIELD_LINE = 352321535;
   private static final int DANGER = -1748149;
   private static final int[] SWATCHES = new int[]{16777215, 15763007, 16731469, 16765517, 5111688, 5100287, 7240703, 14703871};
   private static ZeroGui.Page page = ZeroGui.Page.MODULES;
   private static Module.Category selected = Module.Category.COMBAT;
   private static ZeroGui.Mode mode = ZeroGui.Mode.ALL;
   private static boolean grid;
   private float k = 1.0F;
   private float vw;
   private float vh;
   private float mx;
   private float my;
   private float px;
   private float py;
   private float a;
   private final List<ZeroGui.Region> regions = new ArrayList<>();
   private final Map<Module, Boolean> expanded = new IdentityHashMap<>();
   private ZeroGui.Field field = ZeroGui.Field.NONE;
   private String search = "";
   private String configName = "";
   private String pickerQuery = "";
   private String hexText = "";
   private Setting.Color hexTarget;
   private Module listening;
   private Module wasListening;
   private Setting.Int dragSlider;
   private float sliderX1;
   private float sliderX2;
   private boolean dragPanel;
   private float dragOffX;
   private float dragOffY;
   private boolean resetArmed;
   private float clipY1 = -1000000.0F;
   private float clipY2 = 1000000.0F;
   private boolean modal;
   private float scroll;
   private float scrollTarget;
   private float scrollMax;
   private Setting<?> picker;
   private float pickerScroll;
   private float pickerScrollTarget;
   private float pickerScrollMax;
   private float pickerA;
   private List<ZeroGui.PickerEntry> pickerAll = List.of();
   private long lastFrame = System.nanoTime();
   private float dt;
   private float time;
   private float open;
   private float contentA = 1.0F;
   private float barY = Float.NaN;
   private float pillX = Float.NaN;
   private float pillW;
   private float viewX = Float.NaN;
   private final Map<Object, Float> hoverA = new HashMap<>();
   private final Map<Object, Float> onA = new HashMap<>();
   private final Map<Object, Float> expandA = new HashMap<>();
   private final Map<Object, Float> gridX = new HashMap<>();
   private final Map<Object, Float> gridY = new HashMap<>();
   /** moduleRow wird gerade in einer Grid-Karte gezeichnet (kein Trennstrich). */
   private boolean inCard;

   public ZeroGui() {
      super(Component.literal("ZERO"));
   }

   private void updateScale() {
      Window window = this.minecraft.getWindow();
      float pixels = Math.max(1.0F, window.getHeight() / 540.0F);
      pixels = Math.min(pixels, Math.min(window.getWidth() / 496.0F, window.getHeight() / 328.0F));
      this.k = window.getGuiScale() / pixels;
      this.vw = this.width * this.k;
      this.vh = this.height * this.k;
   }

   @Override
   protected void init() {
      super.init();
      this.updateScale();
      if (QuiqzyConfig.guiX == Integer.MIN_VALUE || QuiqzyConfig.guiX + 480.0F > this.vw + 200.0F) {
         QuiqzyConfig.guiX = Math.round((this.vw - 480.0F) / 2.0F);
         QuiqzyConfig.guiY = Math.round((this.vh - 312.0F) / 2.0F);
      }

      this.clamp();
   }

   private void clamp() {
      QuiqzyConfig.guiX = Math.round(Math.max(4.0F, Math.min(Math.max(4.0F, this.vw - 480.0F - 4.0F), (float)QuiqzyConfig.guiX)));
      QuiqzyConfig.guiY = Math.round(Math.max(4.0F, Math.min(Math.max(4.0F, this.vh - 312.0F - 4.0F), (float)QuiqzyConfig.guiY)));
   }

   @Override
   public boolean isPauseScreen() {
      return false;
   }

   @Override
   public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {
   }

   @Override
   public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
      long now = System.nanoTime();
      this.dt = Math.min(0.1F, (float)(now - this.lastFrame) / 1.0E9F);
      this.lastFrame = now;
      this.time = this.time + this.dt;
      this.open = Draw.approach(this.open, 1.0F, 14.0F, this.dt);
      this.contentA = Draw.approach(this.contentA, 1.0F, 16.0F, this.dt);
      this.a = Math.min(1.0F, this.open * 1.15F);
      this.updateScale();
      this.clamp();
      if (QuiqzyConfig.guiBlur) {
         ZeroBlur.capture();
      }

      this.px = QuiqzyConfig.guiX;
      this.py = QuiqzyConfig.guiY;
      // 3D-Kippen: Zielneigung aus der Mausposition relativ zur Fenstermitte (-1..1)
      float rawX = mouseX * this.k, rawY = mouseY * this.k;
      float tgtX = 0.0F, tgtY = 0.0F;
      boolean busy = this.dragPanel || this.dragSlider != null || this.colorDrag != 0 || this.picker != null || this.colorSetting != null;
      if (QuiqzyConfig.gui3d && !busy) {
         tgtX = Math.max(-1.0F, Math.min(1.0F, (rawX - (this.px + 240.0F)) / 300.0F));
         tgtY = Math.max(-1.0F, Math.min(1.0F, (rawY - (this.py + 156.0F)) / 220.0F));
      }
      this.tiltX = Draw.approach(this.tiltX, tgtX, 6.0F, this.dt);
      this.tiltY = Draw.approach(this.tiltY, tgtY, 6.0F, this.dt);
      float[] local = this.toPanel(rawX, rawY);
      this.mx = local[0];
      this.my = local[1];
      this.regions.clear();
      this.modal = this.picker != null || this.colorSetting != null;
      ZeroRender.rect(g, 0.0F, 0.0F, this.width, this.height, 0.0F, ZeroRender.alpha(1493172224, this.a));
      g.pose().pushMatrix();
      g.pose().scale(1.0F / this.k);
      float pop = 0.965F + 0.035F * this.open;
      // Schatten wandert gegen die Neigung (wirkt wie Licht von vorne oben)
      if (QuiqzyConfig.gui3d) {
         float sx = -this.tiltX * 14.0F, sy = -this.tiltY * 10.0F + 8.0F;
         ZeroRender.glow(g, this.px + sx, this.py + sy, this.px + 480.0F + sx, this.py + 312.0F + sy, 14.0F, 34.0F, ZeroRender.alpha(0xC0000000, this.a * 0.8F));
      }
      g.pose().mul(this.tiltMatrix(pop));
      this.drawPanel(g);
      this.drawSidebar(g);
      switch (page) {
         case MODULES:
            this.drawModules(g);
            break;
         case SETTINGS:
            this.drawSettings(g);
            break;
         case CONFIGS:
            this.drawConfigs(g);
            break;
         case KEYBINDS:
            this.drawKeybinds(g);
      }

      this.drawGlare(g);
      this.pickerA = Draw.approach(this.pickerA, this.picker != null ? 1.0F : 0.0F, 18.0F, this.dt);
      if (this.picker != null) {
         this.drawPicker(g);
      } else if (this.colorSetting != null) {
         this.drawColorPopover(g, this.px, this.py, this.px + 480.0F, this.py + 312.0F);
      }

      g.pose().popMatrix();
   }

   private float tiltX;
   private float tiltY;

   /**
    * Pseudo-3D: das Fenster neigt sich zur Maus. Die Mausseite wird etwas breiter/höher gestaucht (Verkürzung),
    * dazu eine leichte Scherung, die sich wie eine Drehung um die Mittelachse liest.
    */
   private org.joml.Matrix3x2f tiltMatrix(float pop) {
      float cx = this.px + 240.0F, cy = this.py + 156.0F + (1.0F - this.open) * 8.0F;
      float tx = this.tiltX, ty = this.tiltY;
      float sx = pop * (1.0F - 0.035F * Math.abs(tx));
      float sy = pop * (1.0F - 0.035F * Math.abs(ty));
      float shy = 0.045F * tx * ty;          // Ecken kippen gemeinsam
      float shx = -0.035F * tx * ty;
      org.joml.Matrix3x2f m = new org.joml.Matrix3x2f();
      m.translate(cx, cy);
      m.mul(new org.joml.Matrix3x2f(sx, shy, shx, sy, 0.0F, 0.0F));
      // Mausseite kommt minimal nach vorne: kleine Verschiebung zur Maus
      m.translate(tx * 5.0F, ty * 4.0F);
      m.translate(-(this.px + 240.0F), -(this.py + 156.0F));
      return m;
   }

   /** Bildschirm-(virtuelle) Koordinaten -> Koordinaten im gekippten Fenster (für Hover/Klicks). */
   private float[] toPanel(float x, float y) {
      float pop = 0.965F + 0.035F * this.open;
      org.joml.Matrix3x2f inv = this.tiltMatrix(pop).invert(new org.joml.Matrix3x2f());
      org.joml.Vector2f v = inv.transformPosition(new org.joml.Vector2f(x, y));
      return new float[]{v.x, v.y};
   }

   /** Lichtreflex, der der Maus über die Glasfläche folgt. */
   private void drawGlare(GuiGraphics g) {
      if (!QuiqzyConfig.gui3d) {
         return;
      }
      float gx = this.px + 240.0F + this.tiltX * 260.0F, gy = this.py + 156.0F + this.tiltY * 180.0F;
      float r = 170.0F;
      g.enableScissor((int)this.px, (int)this.py, (int)(this.px + 480.0F), (int)(this.py + 312.0F));
      ZeroRender.radial(g, gx - r, gy - r, gx + r, gy + r, r, this.c(ZeroRender.alpha(0xFFFFE2C4, 0.07F * (0.4F + 0.6F * Math.max(Math.abs(this.tiltX), Math.abs(this.tiltY))))), 0x00000000);
      g.disableScissor();
      // Kante, die zur Maus zeigt, leuchtet etwas
      ZeroRender.strokeGradient(g, this.px, this.py, this.px + 480.0F, this.py + 312.0F, 13.0F, 0.9F,
         this.c(ZeroRender.alpha(0xFFFFB26E, 0.35F * Math.max(Math.abs(this.tiltX), Math.abs(this.tiltY)))), 0x00000000,
         (float)Math.toDegrees(Math.atan2(this.tiltY, this.tiltX)));
   }

   private void drawPanel(GuiGraphics g) {
      float x2 = this.px + 480.0F;
      float y2 = this.py + 312.0F;
      float pulse = 0.5F + 0.5F * (float)Math.sin(this.time * 1.6F);
      ZeroRender.glow(g, this.px, this.py + 4.0F, x2, y2 + 4.0F, 13.0F, 26.0F, this.c(-1610612736));
      ZeroRender.glow(g, this.px, this.py, x2, y2, 13.0F, 34.0F, this.c(ZeroRender.alpha(-1286372, 0.1F + 0.05F * pulse)));
      ZeroRender.glass(g, this.px, this.py, x2, y2, 13.0F, -535491319, -301267195, 75.0F, this.a);
      ZeroRender.gradient(g, this.px, this.py, x2, y2, 13.0F, this.c(384857132), 15758380, 40.0F);
      g.enableScissor(Math.round(this.px), Math.round(this.py), Math.round(this.px + 122.0F), Math.round(y2));
      ZeroRender.rect(g, this.px, this.py, x2, y2, 13.0F, this.c(771751936));
      g.disableScissor();
      ZeroRender.rect(g, this.px + 122.0F, this.py + 10.0F, this.px + 122.0F + 0.5F, y2 - 10.0F, 0.0F, this.c(318767103));
      ZeroRender.strokeGradient(g, this.px, this.py, x2, y2, 13.0F, 0.75F, this.c(956291518), this.c(285212671), 80.0F);
   }

   private void drawSidebar(GuiGraphics g) {
      ZeroRender.text(g, ZeroFont.LOGO, "ZERO", this.px + 17.0F, this.py + 10.0F, 25.0F, 0.3F, this.c(-12381), this.c(-1018836));
      float y = this.py + 49.0F;
      ZeroRender.text(g, ZeroFont.BOLD, "MODULES", this.px + 17.0F, y, 6.0F, 0.9F, this.c(-9476768), this.c(-9476768));
      y += 11.0F;
      float activeY = Float.NaN;

      for (Module.Category cat : Module.Category.values()) {
         boolean on = page == ZeroGui.Page.MODULES && selected == cat && this.search.isEmpty();
         if (on) {
            activeY = y;
         }

         long enabled = ModuleManager.byCategory(cat).stream().filter(Module::isEnabled).count();
         this.sideItem(g, cat, categoryIcon(cat), cat.label, enabled, y, on, () -> {
            page = ZeroGui.Page.MODULES;
            selected = cat;
            this.clearSearch();
            this.switchContent();
         });
         y += 22.0F;
      }

      y += 9.0F;
      ZeroRender.text(g, ZeroFont.BOLD, "GENERAL", this.px + 17.0F, y, 6.0F, 0.9F, this.c(-9476768), this.c(-9476768));
      y += 11.0F;
      Object[][] general = new Object[][]{
         {ZeroGui.Page.SETTINGS, '\ue158', "Settings"}, {ZeroGui.Page.CONFIGS, '\ue151', "Configs"}, {ZeroGui.Page.KEYBINDS, '\ue284', "Keybinds"}
      };

      for (Object[] item : general) {
         ZeroGui.Page target = (ZeroGui.Page)item[0];
         boolean on = page == target;
         if (on) {
            activeY = y;
         }

         this.sideItem(g, target, (Character)item[1], (String)item[2], 0L, y, on, () -> {
            page = target;
            this.clearSearch();
            this.switchContent();
         });
         y += 22.0F;
      }

      if (!Float.isNaN(activeY)) {
         this.barY = Float.isNaN(this.barY) ? activeY : Draw.approach(this.barY, activeY, 20.0F, this.dt);
         float x1 = this.px + 9.0F;
         ZeroRender.gradient(g, x1, this.barY, this.px + 122.0F - 9.0F, this.barY + 20.0F, 7.0F, this.c(821064748), this.c(116421676), 0.0F);
         ZeroRender.glow(g, x1 + 0.5F, this.barY + 5.0F, x1 + 2.5F, this.barY + 15.0F, 1.0F, 5.0F, this.c(-1594857697));
         ZeroRender.gradient(g, x1 + 0.5F, this.barY + 5.0F, x1 + 2.5F, this.barY + 15.0F, 1.0F, this.c(-19858), this.c(-1286372), 90.0F);
      }
   }

   private void sideItem(GuiGraphics g, Object key, char icon, String label, long count, float y, boolean on, Runnable click) {
      float x1 = this.px + 9.0F;
      float x2 = this.px + 122.0F - 9.0F;
      boolean hover = this.hot(x1, y, x2, y + 20.0F);
      float hv = this.anim(this.hoverA, key, hover ? 1.0F : 0.0F, 18.0F);
      if (hv > 0.01F && !on) {
         ZeroRender.rect(g, x1, y, x2, y + 20.0F, 7.0F, this.c(ZeroRender.alpha(268435455, hv)));
      }

      int color = this.c(on ? -19858 : ZeroRender.lerp(-6516598, -725272, hv));
      ZeroRender.icon(g, icon, x1 + 13.0F, y + 10.0F, 10.0F, color);
      ZeroRender.text(g, on ? ZeroFont.BOLD : ZeroFont.REGULAR, label, x1 + 24.0F, y + 5.0F, 8.5F, color);
      if (count > 0L) {
         ZeroRender.textRight(g, ZeroFont.BOLD, String.valueOf(count), x2 - 8.0F, y + 5.5F, 7.5F, this.c(-1014209));
      }

      this.click(x1, y, x2, y + 20.0F, () -> {
         UiSounds.click();
         click.run();
      });
   }

   private static char categoryIcon(Module.Category cat) {
      return switch (cat) {
         case COMBAT -> '\ue2b4';
         case MOVEMENT -> '\ue3bd';
         case VISUALS -> '\ue0be';
         case HUD -> '';
         case PLAYER -> '\ue19f';
         case WORLD -> '\ue0eb';
         case MISC -> '\ue0ba';
      };
   }

   private void drawHeader(GuiGraphics g, String accent, String rest, String sub) {
      float x = this.px + 122.0F + 14.0F;
      ZeroRender.text(g, ZeroFont.BOLD, accent, x, this.py + 12.0F, 15.0F, 0.0F, this.c(-19858), this.c(-1014209));
      ZeroRender.text(g, ZeroFont.BOLD, rest, x + ZeroFont.BOLD.width(accent, 15.0F, 0.0F) + 4.0F, this.py + 12.0F, 15.0F, this.c(-725272));
      ZeroRender.text(g, ZeroFont.REGULAR, sub, x, this.py + 30.5F, 7.8F, this.c(-6516598));
      this.regions.add(0, new ZeroGui.Region(this.px, this.py, this.px + 480.0F, this.py + 44.0F, () -> {
         this.dragPanel = true;
         this.dragOffX = this.mx - QuiqzyConfig.guiX;
         this.dragOffY = this.my - QuiqzyConfig.guiY;
      }, null));
   }

   private List<Module> visibleModules() {
      List<Module> list = new ArrayList<>();
      String q = this.search.trim().toLowerCase(Locale.ROOT);

      for (Module m : q.isEmpty() ? ModuleManager.byCategory(selected) : ModuleManager.all()) {
         if (mode.has(m) && (q.isEmpty() || m.name().toLowerCase(Locale.ROOT).contains(q) || m.description().toLowerCase(Locale.ROOT).contains(q))) {
            list.add(m);
         }
      }

      return list;
   }

   private void drawModules(GuiGraphics g) {
      List<Module> mods = this.visibleModules();
      long on = mods.stream().filter(Module::isEnabled).count();
      String sub = mods.size() + (mods.size() == 1 ? " module" : " modules") + "  ·  " + on + " enabled";
      if (this.search.isEmpty()) {
         this.drawHeader(g, selected.label, "Modules", sub);
      } else {
         this.drawHeader(g, "Search", "Results", sub);
      }

      float x2 = this.px + 480.0F - 14.0F;
      this.textField(g, ZeroGui.Field.SEARCH, this.search, "Search modules", '\ue155', x2 - 124.0F, this.py + 13.0F, x2, this.py + 33.0F);
      this.drawPills(g, this.px + 122.0F + 14.0F, this.py + 46.0F);
      this.drawViewSwitch(g, x2 - 44.0F, this.py + 46.0F);
      float top = this.py + 70.0F;
      float bottom = this.py + 312.0F - 8.0F;
      float x1 = this.px + 122.0F + 14.0F;
      this.listBegin(g, top, bottom);
      float y = top + 1.0F - this.scroll + (1.0F - this.contentA) * 7.0F;
      float start = y;
      if (mods.isEmpty()) {
         ZeroRender.textCentered(g, ZeroFont.REGULAR, "No modules found", (x1 + x2) / 2.0F, top + 40.0F, 9.0F, this.c(-9476768));
      } else if (grid) {
         float gap = 8.0F;
         float w = (x2 - x1 - gap) / 2.0F;
         int column = 0;
         // Zielposition jeder Kachel (relativ zum Listenanfang) -> Kacheln gleiten weich dorthin
         float rel = 0.0F;

         for (Module m : mods) {
            boolean wide = this.expandA.getOrDefault(m, 0.0F) > 0.01F || this.expanded.getOrDefault(m, false);
            if (wide) {
               if (column == 1) {
                  rel += 62.0F + gap;
               }

               column = 0;
               float cx = this.slide(this.gridX, m, 0.0F);
               float cy = this.slide(this.gridY, m, rel);
               float h = this.wideCard(g, m, x1 + cx, start + cy, x1, x2, w);
               rel += h + gap;
            } else {
               float tx = column * (w + gap);
               float cx = this.slide(this.gridX, m, tx);
               float cy = this.slide(this.gridY, m, rel);
               this.moduleCard(g, m, x1 + cx, start + cy, w);
               if (++column == 2) {
                  column = 0;
                  rel += 62.0F + gap;
               }
            }
         }

         if (column == 1) {
            rel += 62.0F + gap;
         }

         y = start + rel;
      } else {
         for (Module mx : mods) {
            y = this.moduleRow(g, mx, x1, y, x2);
         }
      }

      this.listEnd(g, y - start, top, bottom);
   }

   private void drawPills(GuiGraphics g, float x, float y) {
      float h = 17.0F;
      float cx = x;
      float targetX = x;
      float targetW = 0.0F;
      float[] widths = new float[ZeroGui.Mode.values().length];

      for (ZeroGui.Mode m : ZeroGui.Mode.values()) {
         widths[m.ordinal()] = ZeroFont.BOLD.width(m.label, 7.5F, 0.0F) + 18.0F;
         if (m == mode) {
            targetX = cx;
            targetW = widths[m.ordinal()];
         }

         cx += widths[m.ordinal()] + 3.0F;
      }

      this.pillX = Float.isNaN(this.pillX) ? targetX : Draw.approach(this.pillX, targetX, 22.0F, this.dt);
      this.pillW = this.pillW == 0.0F ? targetW : Draw.approach(this.pillW, targetW, 22.0F, this.dt);
      ZeroRender.glow(g, this.pillX, y, this.pillX + this.pillW, y + h, 8.5F, 7.0F, this.c(1626367775));
      ZeroRender.gradient(g, this.pillX, y, this.pillX + this.pillW, y + h, 8.5F, this.c(-19858), this.c(-1286372), 20.0F);
      cx = x;

      for (ZeroGui.Mode m : ZeroGui.Mode.values()) {
         float w = widths[m.ordinal()];
         boolean hover = this.hot(cx, y, cx + w, y + h);
         int color = m == mode ? -14807290 : (hover ? -725272 : -6516598);
         ZeroRender.textCentered(g, ZeroFont.BOLD, m.label, cx + w / 2.0F, y + 4.2F, 7.5F, this.c(color));
         this.click(cx, y, cx + w, y + h, () -> {
            mode = m;
            UiSounds.click();
            this.switchContent();
         });
         cx += w + 3.0F;
      }
   }

   private void drawViewSwitch(GuiGraphics g, float x, float y) {
      float h = 17.0F;
      float w = 22.0F;
      ZeroRender.rect(g, x, y, x + 44.0F, y + h, 6.5F, this.c(234881023));
      float target = grid ? x + w : x;
      this.viewX = Float.isNaN(this.viewX) ? target : Draw.approach(this.viewX, target, 22.0F, this.dt);
      ZeroRender.rect(g, this.viewX + 1.5F, y + 1.5F, this.viewX + w - 1.5F, y + h - 1.5F, 5.0F, this.c(955282476));
      ZeroRender.icon(g, '\ue1d9', x + w / 2.0F, y + h / 2.0F, 10.0F, this.c(grid ? -6516598 : -19858));
      ZeroRender.icon(g, '\ue105', x + w * 1.5F, y + h / 2.0F, 10.0F, this.c(grid ? -19858 : -6516598));
      this.click(x, y, x + w, y + h, () -> this.setGrid(false));
      this.click(x + w, y, x + w * 2.0F, y + h, () -> this.setGrid(true));
   }

   private void setGrid(boolean value) {
      grid = value;
      UiSounds.click();
      this.switchContent();
   }

   /** Weiches Hingleiten einer Kachel-Koordinate (erster Frame: direkt am Ziel). */
   private float slide(Map<Object, Float> map, Object key, float target) {
      Float cur = map.get(key);
      float v = cur == null ? target : Draw.approach(cur, target, 16.0F, this.dt);
      map.put(key, v);
      return v;
   }

   private static float ease(float t) {
      t = Math.max(0.0F, Math.min(1.0F, t));
      float u = 1.0F - t;
      return 1.0F - u * u * u;
   }

   /**
    * Geoeffnete Kachel im Grid-Modus: waechst von Kachelgroesse weich auf volle Breite,
    * die Kachel blendet aus, die Zeile mit Settings blendet ein. Gibt die Hoehe zurueck.
    */
   private float wideCard(GuiGraphics g, Module m, float x, float y, float x1, float x2, float tileW) {
      float ex = this.expandA.getOrDefault(m, 0.0F);
      float e = ease(ex);
      float full = x2 - x1;
      float cw = tileW + (full - tileW) * e;
      float cx = x + (x1 - x) * e;
      float rowH = 38.0F + settingsHeight(m) * ex + 6.0F;
      float ch = 62.0F + (rowH - 62.0F) * e;
      float onv = this.onA.getOrDefault(m, m.isEnabled() ? 1.0F : 0.0F);
      float oldA = this.a;
      // Karten-Hintergrund (gleicher Look wie die Kachel)
      this.a = oldA * e;
      ZeroRender.rect(g, cx, y, cx + cw, y + ch, 9.0F, this.c(318767103));
      if (onv > 0.01F) {
         ZeroRender.gradient(g, cx, y, cx + cw, y + ch, 9.0F, this.c(ZeroRender.alpha(686847020, onv)), 15758380, 50.0F);
      }
      ZeroRender.stroke(g, cx, y, cx + cw, y + ch, 9.0F, 0.5F, this.c(ZeroRender.lerp(352321535, 1894811199, onv)));
      this.a = oldA;
      // Kachel blendet aus
      if (e < 0.98F) {
         this.a = oldA * (1.0F - e) * (1.0F - e);
         this.moduleCard(g, m, cx, y, cw);
         this.a = oldA;
      }
      // Zeile + Settings blenden ein
      float ra = Math.max(0.0F, (e - 0.25F) / 0.75F);
      this.a = oldA * ra;
      this.inCard = true;
      this.moduleRow(g, m, cx + 9.0F, y + 2.0F, cx + cw - 9.0F);
      this.inCard = false;
      this.a = oldA;
      return ch;
   }

   /** Weicher, pulsierender Orange-Glow (wie beim Keybind-Setzen). */
   private void pulseGlow(GuiGraphics g, float x1, float y1, float x2, float y2, float r, float blur, float strength) {
      if (strength <= 0.01F) {
         return;
      }
      float pulse = 0.55F + 0.45F * (float)Math.sin(this.time * 5.0F);
      ZeroRender.glow(g, x1, y1, x2, y2, r, blur, this.c(ZeroRender.alpha(1894803231, strength * pulse)));
   }

   private float moduleRow(GuiGraphics g, Module m, float x1, float y, float x2) {
      boolean open = this.expanded.getOrDefault(m, false);
      float ex = this.anim(this.expandA, m, open ? 1.0F : 0.0F, 15.0F);
      boolean hover = this.hot(x1 - 5.0F, y, x2 + 5.0F, y + 38.0F);
      float hv = this.anim(this.hoverA, m, hover ? 1.0F : 0.0F, 18.0F);
      float onv = this.anim(this.onA, m, m.isEnabled() ? 1.0F : 0.0F, 15.0F);
      if (hv > 0.01F) {
         ZeroRender.rect(g, x1 - 5.0F, y + 1.0F, x2 + 5.0F, y + 38.0F - 1.0F, 8.0F, this.c(ZeroRender.alpha(234881023, hv)));
      }

      if (onv > 0.01F) {
         ZeroRender.gradient(g, x1 - 5.0F, y + 1.0F, x2 + 5.0F, y + 38.0F - 1.0F, 8.0F, this.c(ZeroRender.alpha(552629292, onv)), 15758380, 0.0F);
      }

      this.click(x1 - 5.0F, y, x2 + 5.0F, y + 38.0F, () -> this.toggleModule(m), () -> this.expand(m));
      float textX = x1 + 4.0F;
      float right = x2 - 33.0F;
      right -= this.moduleControls(g, m, right, y + 19.0F, ex);
      float textW = right - textX - 8.0F;
      ZeroRender.text(g, ZeroFont.BOLD, m.name(), textX, y + 7.5F, 9.5F, this.c(ZeroRender.lerp(-725272, -19858, onv * 0.9F)));
      ZeroRender.text(g, ZeroFont.REGULAR, fit(ZeroFont.REGULAR, m.description(), 7.6F, textW), textX, y + 21.0F, 7.6F, this.c(-6516598));
      this.toggle(g, x2 - 29.0F, y + 19.0F - 8.0F, 29.0F, 16.0F, onv);
      float h = 38.0F;
      if (ex > 0.01F) {
         float full = settingsHeight(m);
         float shown = full * ex;
         float clipTop = Math.max(y + 38.0F, this.clipY1);
         float clipBottom = Math.min(y + 38.0F + shown, this.clipY2);
         if (clipBottom > clipTop) {
            float oldY1 = this.clipY1;
            float oldY2 = this.clipY2;
            this.clipY1 = clipTop;
            this.clipY2 = clipBottom;
            g.enableScissor(Math.round(this.px + 122.0F), (int)Math.floor(clipTop), Math.round(this.px + 480.0F), (int)Math.ceil(clipBottom));
            this.drawSettingsOf(g, m, x1 + 4.0F, y + 38.0F, x2 - 2.0F);
            g.disableScissor();
            this.clipY1 = oldY1;
            this.clipY2 = oldY2;
         }

         h += shown;
      }

      if (!this.inCard) {
         ZeroRender.rect(g, x1, y + h + 0.5F, x2, y + h + 1.0F, 0.0F, this.c(318767103));
      }
      return y + h + 1.5F;
   }

   private float moduleControls(GuiGraphics g, Module m, float right, float cy, float ex) {
      float used = 16.0F;
      if (hasSettings(m)) {
         float ax = right - 7.0F;
         boolean hover = this.hot(ax - 7.0F, cy - 9.0F, ax + 7.0F, cy + 9.0F);
         g.pose().pushMatrix();
         g.pose().translate(ax, cy);
         g.pose().rotate(ex * (float) Math.PI / 2.0F);
         ZeroRender.icon(g, '\ue073', 0.0F, 0.0F, 10.0F, this.c(!hover && !(ex > 0.5F) ? -9476768 : -725272));
         g.pose().popMatrix();
         this.click(ax - 7.0F, cy - 9.0F, ax + 7.0F, cy + 9.0F, () -> this.expand(m));
      }

      return used + (this.keyChip(g, m, right - used, cy) + 3.0F);
   }

   private float keyChip(GuiGraphics g, Module m, float right, float cy) {
      boolean active = this.listening == m;
      String label = active ? "Press key" : keyName(m.keybind());
      float w = ZeroFont.BOLD.width(label, 7.0F, 0.0F) + 12.0F;
      float x1 = right - w;
      float h = 15.0F;
      boolean hover = this.hot(x1, cy - h / 2.0F, right, cy + h / 2.0F);
      float pulse = 0.5F + 0.5F * (float)Math.sin(this.time * 7.0F);
      if (active) {
         ZeroRender.glow(g, x1, cy - h / 2.0F, right, cy + h / 2.0F, 5.0F, 6.0F, this.c(ZeroRender.alpha(1894803231, pulse)));
      } else {
         this.pulseGlow(g, x1, cy - h / 2.0F, right, cy + h / 2.0F, 5.0F, 5.0F, 0.45F * this.anim(this.hoverA, "key:" + m.id(), hover ? 1.0F : 0.0F, 18.0F));
      }

      ZeroRender.rect(g, x1, cy - h / 2.0F, right, cy + h / 2.0F, 5.0F, this.c(active ? 1089500204 : (hover ? 419430399 : 234881023)));
      ZeroRender.stroke(g, x1, cy - h / 2.0F, right, cy + h / 2.0F, 5.0F, 0.5F, this.c(active ? -1594849729 : 352321535));
      ZeroRender.textCentered(g, ZeroFont.BOLD, label, x1 + w / 2.0F, cy - 3.7F, 7.0F, this.c(active ? -19858 : (m.keybind() > 0 ? -725272 : -6516598)));
      this.click(x1, cy - h / 2.0F, right, cy + h / 2.0F, () -> {
         this.listening = this.wasListening == m ? null : m;
         this.field = ZeroGui.Field.NONE;
         UiSounds.click();
      }, () -> {
         m.setKeybind(-1);
         this.listening = null;
         QuiqzyConfig.save();
         UiSounds.removed();
      });
      return w;
   }

   private void moduleCard(GuiGraphics g, Module m, float x, float y, float w) {
      float x2 = x + w;
      float y2 = y + 62.0F;
      boolean hover = this.hot(x, y, x2, y2);
      float hv = this.anim(this.hoverA, m, hover ? 1.0F : 0.0F, 18.0F);
      float onv = this.anim(this.onA, m, m.isEnabled() ? 1.0F : 0.0F, 15.0F);
      ZeroRender.rect(g, x, y, x2, y2, 9.0F, this.c(ZeroRender.lerp(184549375, 318767103, hv)));
      if (onv > 0.01F) {
         ZeroRender.gradient(g, x, y, x2, y2, 9.0F, this.c(ZeroRender.alpha(686847020, onv)), 15758380, 50.0F);
      }

      ZeroRender.stroke(g, x, y, x2, y2, 9.0F, 0.5F, this.c(ZeroRender.lerp(352321535, 1894811199, onv)));
      ZeroRender.text(
         g, ZeroFont.BOLD, fit(ZeroFont.BOLD, m.name(), 9.5F, w - 16.0F), x + 8.0F, y + 8.0F, 9.5F, this.c(ZeroRender.lerp(-725272, -19858, onv * 0.9F))
      );
      ZeroRender.text(g, ZeroFont.REGULAR, fit(ZeroFont.REGULAR, m.description(), 7.4F, w - 16.0F), x + 8.0F, y + 21.0F, 7.4F, this.c(-6516598));
      this.toggle(g, x2 - 34.0F, y2 - 21.0F, 26.0F, 14.0F, onv);
      this.click(x, y, x2, y2, () -> this.toggleModule(m), () -> this.expand(m));
      float right = x2 - 40.0F;
      this.moduleControls(g, m, right, y2 - 14.0F, this.expandA.getOrDefault(m, 0.0F));
   }

   private void toggle(GuiGraphics g, float x, float y, float w, float h, float onv) {
      float x2 = x + w;
      float y2 = y + h;
      float r = h / 2.0F;
      if (onv > 0.01F) {
         float pulse = 0.5F + 0.5F * (float)Math.sin(this.time * 2.4F);
         ZeroRender.glow(g, x, y, x2, y2, r, 7.0F, this.c(ZeroRender.alpha(-1594857697, onv * (0.75F + 0.25F * pulse))));
      }

      ZeroRender.rect(g, x, y, x2, y2, r, this.c(-14016487));
      if (onv > 0.01F) {
         ZeroRender.gradient(g, x, y, x2, y2, r, this.c(ZeroRender.alpha(-19858, onv)), this.c(ZeroRender.alpha(-1286372, onv)), 15.0F);
      }

      ZeroRender.stroke(g, x, y, x2, y2, r, 0.5F, this.c(419430399));
      float knob = r - 2.0F;
      float cx = x + 2.0F + knob + (w - 4.0F - knob * 2.0F) * onv;
      ZeroRender.glow(g, cx - knob, y + 2.5F, cx + knob, y2 - 1.5F, knob, 1.6F, this.c(1879048192));
      ZeroRender.gradient(g, cx - knob, y + 2.0F, cx + knob, y2 - 2.0F, knob, this.c(-1), this.c(-1186081), 90.0F);
   }

   private void toggleModule(Module m) {
      m.toggle();
      QuiqzyConfig.save();
   }

   private void expand(Module m) {
      if (hasSettings(m)) {
         this.expanded.put(m, !this.expanded.getOrDefault(m, false));
         UiSounds.click();
      }
   }

   private static boolean hasSettings(Module m) {
      return !m.settings().isEmpty();
   }

   private static float settingHeight(Setting<?> s) {
      if (s == ModuleManager.TEXT_REPLACE.rules) {
         int n = dev.quiqzy.client.module.misc.TextReplaceModule.serverLines(net.minecraft.client.Minecraft.getInstance(), new net.minecraft.network.chat.Component[1]).size();
         return n == 0 ? 40.0F : 52.0F + n * 15.0F;
      }
      if (s instanceof Setting.ColorMap || s instanceof Setting.Strings) {
         return 0.0F;
      }
      if (s == ModuleManager.BLOCK_ESP.preset) {
         return 58.0F;
      }
      if (s instanceof Setting.Int) {
         return 27.0F;
      } else {
         return s instanceof Setting.Waypoints w ? 21.0F + ((List)w.get()).size() * 18.0F : 21.0F;
      }
   }

   private static float settingsHeight(Module m) {
      float h = 6.0F;

      for (Setting<?> s : m.settings()) {
         h += settingHeight(s);
      }

      return h;
   }

   private void drawSettingsOf(GuiGraphics g, Module m, float x1, float y, float x2) {
      y += 2.0F;

      for (Setting<?> s : m.settings()) {
         if (s instanceof Setting.Bool b) {
            this.boolRow(g, b.name(), b.on(), b, x1, y, x2, () -> {
               b.toggle();
               QuiqzyConfig.save();
               UiSounds.click();
            });
         } else if (s instanceof Setting.Int i) {
            this.sliderRow(g, i, x1, y, x2);
         } else if (s instanceof Setting.Choice c && c == ModuleManager.BLOCK_ESP.preset) {
            this.presetRow(g, c, x1, y, x2);
         } else if (s instanceof Setting.Choice c) {
            this.choiceRow(g, c, x1, y, x2);
         } else if (s instanceof Setting.Color c) {
            this.colorRow(g, c, x1, y, x2);
         } else if (s instanceof Setting.Blocks b) {
            this.pickerRow(g, b, ((List)b.get()).size(), "blocks", x1, y, x2);
         } else if (s instanceof Setting.Items i) {
            this.pickerRow(g, i, ((List)i.get()).size(), "items", x1, y, x2);
         } else if (s instanceof Setting.Waypoints w) {
            this.waypointRows(g, w, x1, y, x2);
         } else if (s == ModuleManager.TEXT_REPLACE.rules) {
            this.scoreboardEditor(g, x1, y, x2);
         }

         y += settingHeight(s);
      }
   }

   private void label(GuiGraphics g, String text, float x, float y) {
      ZeroRender.text(g, ZeroFont.REGULAR, text, x, y, 8.0F, this.c(ZeroRender.lerp(-6516598, -725272, 0.35F)));
   }

   private void boolRow(GuiGraphics g, String name, boolean on, Object key, float x1, float y, float x2, Runnable click) {
      this.label(g, name, x1, y + 5.5F);
      this.toggle(g, x2 - 25.0F, y + 3.5F, 25.0F, 14.0F, this.anim(this.onA, key, on ? 1.0F : 0.0F, 15.0F));
      this.click(x1, y, x2, y + 21.0F, click);
   }

   private void sliderRow(GuiGraphics g, Setting.Int s, float x1, float y, float x2) {
      boolean active = this.dragSlider == s;
      this.label(g, s.name(), x1, y + 2.0F);
      String value = s.get() + (s.unit().isEmpty() ? "" : " " + s.unit());
      ZeroRender.textRight(g, ZeroFont.BOLD, value, x2, y + 2.0F, 8.0F, this.c(-19858));
      float sy = y + 16.5F;
      float fill = x1 + (x2 - x1) * this.anim(this.onA, s, s.progress(), 22.0F);
      ZeroRender.rect(g, x1, sy, x2, sy + 3.5F, 1.75F, this.c(-14016487));
      ZeroRender.glow(g, x1, sy, Math.max(x1 + 3.0F, fill), sy + 3.5F, 1.75F, 4.0F, this.c(1626367775));
      ZeroRender.gradient(g, x1, sy, Math.max(x1 + 3.5F, fill), sy + 3.5F, 1.75F, this.c(-1286372), this.c(-19858), 0.0F);
      float kr = active ? 5.0F : 4.0F;
      boolean sHover = this.hot(x1 - 4.0F, y + 10.0F, x2 + 4.0F, y + 27.0F);
      float sg = this.anim(this.hoverA, s, active ? 1.0F : (sHover ? 0.5F : 0.0F), 18.0F);
      this.pulseGlow(g, fill - kr - 2.0F, sy + 1.75F - kr - 2.0F, fill + kr + 2.0F, sy + 1.75F + kr + 2.0F, kr + 2.0F, 6.0F, sg);
      ZeroRender.glow(g, fill - kr, sy + 1.75F - kr + 0.6F, fill + kr, sy + 1.75F + kr + 0.6F, kr, 1.8F, this.c(1879048192));
      ZeroRender.circle(g, fill, sy + 1.75F, kr, this.c(-1));
      this.click(x1 - 4.0F, y + 10.0F, x2 + 4.0F, y + 27.0F, () -> {
         this.dragSlider = s;
         this.sliderX1 = x1;
         this.sliderX2 = x2;
         this.updateSlider();
      });
   }

   private void choiceRow(GuiGraphics g, Setting.Choice s, float x1, float y, float x2) {
      this.label(g, s.name(), x1, y + 5.5F);
      float size = 7.2F;
      float total = 0.0F;

      for (String o : s.options()) {
         total += ZeroFont.BOLD.width(o, size, 0.0F) + 12.0F + 2.0F;
      }

      float labelW = ZeroFont.REGULAR.width(s.name(), 8.0F, 0.0F);
      float h = 15.0F;
      float ry = y + 3.0F;
      if (total < x2 - x1 - labelW - 10.0F) {
         float cx = x2 - total + 2.0F;

         for (String o : s.options()) {
            float w = ZeroFont.BOLD.width(o, size, 0.0F) + 12.0F;
            boolean on = o.equals(s.get());
            boolean hover = this.hot(cx, ry, cx + w, ry + h);
            if (on) {
               this.pulseGlow(g, cx, ry, cx + w, ry + h, 5.0F, 5.0F, 0.6F);
               ZeroRender.gradient(g, cx, ry, cx + w, ry + h, 5.0F, this.c(1626375743), this.c(1089232668), 20.0F);
            } else {
               ZeroRender.rect(g, cx, ry, cx + w, ry + h, 5.0F, this.c(hover ? 419430399 : 234881023));
            }

            ZeroRender.textCentered(g, ZeroFont.BOLD, o, cx + w / 2.0F, ry + 3.8F, size, this.c(on ? -19858 : -6516598));
            this.click(cx, ry, cx + w, ry + h, () -> this.selectChoice(s, o));
            cx += w + 2.0F;
         }
      } else {
         float w = ZeroFont.BOLD.width((String)s.get(), size, 0.0F) + 22.0F;
         float cx = x2 - w;
         boolean hover = this.hot(cx, ry, x2, ry + h);
         this.pulseGlow(g, cx, ry, x2, ry + h, 5.0F, 5.0F, 0.5F * this.anim(this.hoverA, s, hover ? 1.0F : 0.0F, 18.0F));
         ZeroRender.rect(g, cx, ry, x2, ry + h, 5.0F, this.c(hover ? 419430399 : 234881023));
         ZeroRender.text(g, ZeroFont.BOLD, (String)s.get(), cx + 7.0F, ry + 3.8F, size, this.c(-19858));
         ZeroRender.icon(g, '\ue073', x2 - 7.0F, ry + h / 2.0F, 8.0F, this.c(-6516598));
         this.click(cx, ry, x2, ry + h, () -> {
            s.cycle();
            this.afterChoice(s);
         }, () -> {
            int i = s.options().indexOf(s.get());
            s.set(s.options().get((i + s.options().size() - 1) % s.options().size()));
            this.afterChoice(s);
         });
      }
   }

   /** Block-ESP-Presets als Chips: das gewählte leuchtet orange mit Verlauf + Puls (wie beim Keybind). */
   private void presetRow(GuiGraphics g, Setting.Choice s, float x1, float y, float x2) {
      this.label(g, s.name(), x1, y + 3.0F);
      List<String> opts = s.options().subList(1, s.options().size());
      float gap = 4.0F;
      float w = (x2 - x1 - gap * 2.0F) / 3.0F;
      float h = 16.0F;
      for (int i = 0; i < opts.size(); i++) {
         String o = opts.get(i);
         float cx = x1 + (i % 3) * (w + gap);
         float cy = y + 16.0F + (i / 3) * (h + 4.0F);
         boolean on = o.equals(ModuleManager.BLOCK_ESP.lastPreset);
         boolean hover = this.hot(cx, cy, cx + w, cy + h);
         float av = this.anim(this.onA, "preset:" + o, on ? 1.0F : 0.0F, 14.0F);
         float hv = this.anim(this.hoverA, "preset:" + o, hover ? 1.0F : 0.0F, 18.0F);
         if (av > 0.01F) {
            this.pulseGlow(g, cx, cy, cx + w, cy + h, 6.0F, 7.0F, av);
         }
         ZeroRender.rect(g, cx, cy, cx + w, cy + h, 6.0F, this.c(ZeroRender.lerp(234881023, 419430399, hv)));
         if (av > 0.01F) {
            // Verlauf läuft langsam durch (wie die Kategorie-Pille)
            float ang = 20.0F + 25.0F * (float)Math.sin(this.time * 1.6F + i);
            ZeroRender.gradient(g, cx, cy, cx + w, cy + h, 6.0F, this.c(ZeroRender.alpha(-19858, av)), this.c(ZeroRender.alpha(-1286372, av)), ang);
         }
         ZeroRender.stroke(g, cx, cy, cx + w, cy + h, 6.0F, 0.5F, this.c(ZeroRender.lerp(352321535, -1594849729, Math.max(av, hv * 0.5F))));
         int tc = ZeroRender.lerp(ZeroRender.lerp(-6516598, -725272, hv), -14807290, av);
         ZeroRender.textCentered(g, ZeroFont.BOLD, o, cx + w / 2.0F, cy + 4.3F, 7.2F, this.c(tc));
         this.click(cx, cy, cx + w, cy + h, () -> this.selectChoice(s, o));
      }
   }

   private void selectChoice(Setting.Choice s, String option) {
      s.set(option);
      this.afterChoice(s);
   }

   private void afterChoice(Setting.Choice s) {
      if (s == ModuleManager.BLOCK_ESP.preset) {
         ModuleManager.BLOCK_ESP.applyPreset();
      }

      QuiqzyConfig.save();
      UiSounds.click();
   }

   private void colorRow(GuiGraphics g, Setting.Color s, float x1, float y, float x2) {
      this.label(g, s.name(), x1, y + 5.5F);
      float cy = y + 10.5F;
      boolean editing = this.field == ZeroGui.Field.HEX && this.hexTarget == s;
      String hex = editing ? this.hexText : s.hex();
      float w = 44.0F;
      float fx = x2 - w;
      if (editing) {
         this.pulseGlow(g, fx, cy - 7.5F, x2, cy + 7.5F, 5.0F, 6.0F, 1.0F);
      }
      ZeroRender.rect(g, fx, cy - 7.5F, x2, cy + 7.5F, 5.0F, this.c(234881023));
      ZeroRender.stroke(g, fx, cy - 7.5F, x2, cy + 7.5F, 5.0F, 0.5F, this.c(editing ? -1594849729 : 352321535));
      ZeroRender.text(g, ZeroFont.REGULAR, hex + this.caret(editing), fx + 6.0F, cy - 3.8F, 7.2F, this.c(-725272));
      this.click(fx, cy - 7.5F, x2, cy + 7.5F, () -> {
         this.field = ZeroGui.Field.HEX;
         this.hexTarget = s;
         this.hexText = s.hex();
      });
      float cx = fx - 12.0F;

      for (int i = SWATCHES.length - 1; i >= 0; i--) {
         int rgb = SWATCHES[i];
         boolean on = (Integer)s.get() == rgb;
         if (on) {
            ZeroRender.stroke(g, cx - 6.0F, cy - 6.0F, cx + 6.0F, cy + 6.0F, 6.0F, 1.0F, this.c(-1));
         }

         ZeroRender.circle(g, cx, cy, 4.2F, this.c(0xFF000000 | rgb));
         this.click(cx - 6.0F, cy - 7.0F, cx + 6.0F, cy + 7.0F, () -> {
            s.set(rgb);
            QuiqzyConfig.save();
            UiSounds.click();
         });
         cx -= 13.0F;
      }

      boolean bigHover = this.hot(cx - 7.0F, cy - 7.0F, cx + 7.0F, cy + 7.0F);
      if (bigHover) {
         this.pulseGlow(g, cx - 7.0F, cy - 7.0F, cx + 7.0F, cy + 7.0F, 7.0F, 4.0F, 0.8F);
      }
      ZeroRender.circle(g, cx, cy, bigHover ? 6.0F : 5.0F, this.c(0xFF000000 | (Integer)s.get()));
      ZeroRender.stroke(g, cx - 6.5F, cy - 6.5F, cx + 6.5F, cy + 6.5F, 6.5F, 0.6F, this.c(bigHover ? -19858 : 419430399));
      this.click(cx - 7.0F, cy - 7.0F, cx + 7.0F, cy + 7.0F, () -> this.openColorSetting(s));
   }

   private void pickerRow(GuiGraphics g, Setting<?> s, int count, String noun, float x1, float y, float x2) {
      this.label(g, s.name(), x1, y + 5.5F);
      String text = count + " " + noun;
      float w = ZeroFont.BOLD.width(text, 7.2F, 0.0F) + 26.0F;
      float bx = x2 - w;
      float ry = y + 3.0F;
      float h = 15.0F;
      boolean hover = this.hot(bx, ry, x2, ry + h);
      ZeroRender.rect(g, bx, ry, x2, ry + h, 5.0F, this.c(hover ? 419430399 : 234881023));
      ZeroRender.stroke(g, bx, ry, x2, ry + h, 5.0F, 0.5F, this.c(352321535));
      ZeroRender.text(g, ZeroFont.BOLD, text, bx + 7.0F, ry + 3.8F, 7.2F, this.c(-19858));
      ZeroRender.icon(g, '\ue141', x2 - 8.0F, ry + h / 2.0F, 8.0F, this.c(-6516598));
      this.click(bx, ry, x2, ry + h, () -> this.openPicker(s));
   }

   private void waypointRows(GuiGraphics g, Setting.Waypoints w, float x1, float y, float x2) {
      boolean spawnerList = w == ModuleManager.SPAWNER_BOT.spawners;
      this.label(g, spawnerList ? ModuleManager.SPAWNER_BOT.status : w.name(), x1, y + 5.5F);
      if (spawnerList) {
         this.button(g, "Mark all", '\ue1d9', x2 - 186.0F, y + 3.0F, x2 - 136.0F, y + 18.0F, false, () -> {
            ModuleManager.SPAWNER_BOT.markAll(this.minecraft, true);
            UiSounds.added();
         });
         this.button(g, "Run now", '\ue1d9', x2 - 132.0F, y + 3.0F, x2 - 72.0F, y + 18.0F, false, () -> {
            ModuleManager.SPAWNER_BOT.runNow();
            UiSounds.click();
         });
         this.button(g, "Mark spawner", '\ue115', x2 - 68.0F, y + 3.0F, x2, y + 18.0F, false, () -> {
            ModuleManager.SPAWNER_BOT.markLooked(this.minecraft);
            UiSounds.added();
         });
      } else {
         this.button(g, "Add here", '\ue115', x2 - 58.0F, y + 3.0F, x2, y + 18.0F, false, () -> {
            ModuleManager.WAYPOINTS.addHere(this.minecraft, "");
            UiSounds.added();
         });
      }
      y += 21.0F;
      List<Setting.Waypoints.Point> points = (List<Setting.Waypoints.Point>)w.get();

      String here = dev.quiqzy.client.module.world.WaypointsModule.dimensionId(this.minecraft.level);
      for (int i = 0; i < points.size(); i++) {
         Setting.Waypoints.Point p = points.get(i);
         int rgb = dev.quiqzy.client.module.world.WaypointsModule.colorOf(p.name());
         boolean rowHover = this.hot(x1 - 3.0F, y + 1.0F, x2 + 3.0F, y + 17.0F);
         float rh = this.anim(this.hoverA, "wp:" + i, rowHover ? 1.0F : 0.0F, 18.0F);
         ZeroRender.rect(g, x1 - 3.0F, y + 1.0F, x2 + 3.0F, y + 17.0F, 6.0F, this.c(ZeroRender.lerp(184549375, 352321535, rh)));
         ZeroRender.glow(g, x1 + 1.5F, y + 5.5F, x1 + 8.5F, y + 12.5F, 3.5F, 3.0F, this.c(ZeroRender.alpha(0xFF000000 | rgb, 0.55F)));
         ZeroRender.circle(g, x1 + 5.0F, y + 9.0F, 3.0F, this.c(0xFF000000 | rgb));
         ZeroRender.text(g, ZeroFont.BOLD, fit(ZeroFont.BOLD, p.name(), 7.6F, (x2 - x1) * 0.4F), x1 + 13.0F, y + 5.0F, 7.6F, this.c(-725272));
         String info;
         if (this.minecraft.player != null && p.dim().equals(here)) {
            double dx = p.x() + 0.5 - this.minecraft.player.getX();
            double dy = p.y() - this.minecraft.player.getY();
            double dz = p.z() + 0.5 - this.minecraft.player.getZ();
            info = (int)Math.sqrt(dx * dx + dy * dy + dz * dz) + "m  ·  " + p.x() + ", " + p.y() + ", " + p.z();
         } else {
            info = p.dim() + "  ·  " + p.x() + ", " + p.y() + ", " + p.z();
         }
         ZeroRender.textRight(g, ZeroFont.REGULAR, info, x2 - 18.0F, y + 5.0F, 7.2F, this.c(-6516598));
         int index = i;
         boolean hover = this.hot(x2 - 14.0F, y, x2, y + 18.0F);
         ZeroRender.icon(g, '\ue18e', x2 - 7.0F, y + 9.0F, 8.5F, this.c(hover ? -1748149 : -9476768));
         this.click(x2 - 14.0F, y, x2, y + 18.0F, () -> {
            w.remove(index);
            QuiqzyConfig.save();
            UiSounds.removed();
         });
         y += 18.0F;
      }
   }

   // ---- Scoreboard-Editor (Text Replace)
   private String replaceFrom;
   private String replaceText = "";

   private void commitReplace() {
      if (this.replaceFrom != null) {
         ModuleManager.TEXT_REPLACE.setRule(this.replaceFrom, this.replaceText.trim());
         if (!ModuleManager.TEXT_REPLACE.isEnabled()) {
            ModuleManager.TEXT_REPLACE.setEnabled(true);
         }
         QuiqzyConfig.save();
         UiSounds.added();
      }
      this.replaceFrom = null;
      this.field = ZeroGui.Field.NONE;
   }

   private void scoreboardEditor(GuiGraphics g, float x1, float y, float x2) {
      var mod = ModuleManager.TEXT_REPLACE;
      net.minecraft.network.chat.Component[] title = new net.minecraft.network.chat.Component[1];
      var lines = dev.quiqzy.client.module.misc.TextReplaceModule.serverLines(this.minecraft, title);
      if (lines.isEmpty()) {
         ZeroRender.rect(g, x1, y + 3.0F, x2, y + 35.0F, 7.0F, this.c(184549375));
         ZeroRender.textCentered(g, ZeroFont.REGULAR, "No scoreboard here – join a server with a sidebar", (x1 + x2) / 2.0F, y + 15.0F, 7.6F, this.c(-6516598));
         return;
      }
      float top = y + 3.0F;
      float bottom = top + 42.0F + lines.size() * 15.0F;
      // Karte im Scoreboard-Look
      ZeroRender.rect(g, x1, top, x2, bottom, 8.0F, this.c(-871494134));
      ZeroRender.stroke(g, x1, top, x2, bottom, 8.0F, 0.6F, this.c(352321535));
      ZeroRender.text(g, ZeroFont.BOLD, "LIVE PREVIEW", x1 + 8.0F, top + 5.0F, 5.6F, this.c(-19858));
      ZeroRender.textRight(g, ZeroFont.REGULAR, "click a line to edit  ·  right-click = original", x2 - 8.0F, top + 5.0F, 5.6F, this.c(-9476768));
      float k = 1.0F / this.k;
      // Titel mit Minecraft-Schrift (Originalfarben)
      var font = this.minecraft.font;
      net.minecraft.network.chat.Component t = mod.apply(title[0]);
      this.mcText(g, t, (x1 + x2) / 2.0F - font.width(t) * 0.55F, top + 15.0F, 1.1F, true);
      float ly = top + 30.0F;
      for (var line : lines) {
         boolean editing = this.field == ZeroGui.Field.REPLACE && line.plain().equals(this.replaceFrom);
         boolean custom = mod.ruleFor(line.plain()) != null;
         boolean hover = this.hot(x1 + 4.0F, ly, x2 - 4.0F, ly + 14.0F);
         float hv = this.anim(this.hoverA, "sb:" + line.plain(), hover || editing ? 1.0F : 0.0F, 18.0F);
         if (hv > 0.01F) {
            ZeroRender.rect(g, x1 + 4.0F, ly, x2 - 4.0F, ly + 14.0F, 5.0F, this.c(ZeroRender.alpha(352321535, hv)));
         }
         if (custom) {
            ZeroRender.rect(g, x1 + 5.0F, ly + 3.0F, x1 + 6.5F, ly + 11.0F, 0.7F, this.c(-19858));
         }
         if (editing) {
            this.pulseGlow(g, x1 + 4.0F, ly, x2 - 4.0F, ly + 14.0F, 5.0F, 5.0F, 0.8F);
            ZeroRender.stroke(g, x1 + 4.0F, ly, x2 - 4.0F, ly + 14.0F, 5.0F, 0.6F, this.c(-1594849729));
            ZeroRender.text(g, ZeroFont.REGULAR, fitEnd(this.replaceText, 7.6F, x2 - x1 - 50.0F) + this.caret(true), x1 + 10.0F, ly + 3.2F, 7.6F, this.c(-725272));
         } else {
            net.minecraft.network.chat.Component shown = mod.apply(line.text());
            this.mcText(g, shown, x1 + 10.0F, ly + 3.0F, 1.0F, true);
         }
         this.mcText(g, net.minecraft.network.chat.Component.literal("\u00a7c" + line.score()), x2 - 10.0F - font.width(line.score()) * 1.0F, ly + 3.0F, 1.0F, true);
         String plain = line.plain();
         this.click(x1 + 4.0F, ly, x2 - 4.0F, ly + 14.0F, () -> {
            this.replaceFrom = plain;
            String cur = mod.ruleFor(plain);
            this.replaceText = (cur != null ? cur : plain).replace('§', '&');
            this.field = ZeroGui.Field.REPLACE;
            UiSounds.click();
         }, () -> {
            mod.setRule(plain, null);
            QuiqzyConfig.save();
            UiSounds.removed();
         });
         ly += 15.0F;
      }
      ZeroRender.text(g, ZeroFont.REGULAR, "Colours: &a green  &6 gold  &c red  &b aqua  &l bold  ·  only you see this",
         x1 + 8.0F, bottom - 10.0F, 5.6F, this.c(-9476768));
   }

   /** Minecraft-Text (mit Originalfarben) in GUI-Koordinaten zeichnen. */
   private void mcText(GuiGraphics g, net.minecraft.network.chat.Component text, float x, float y, float scale, boolean shadow) {
      g.pose().pushMatrix();
      g.pose().translate(x, y);
      g.pose().scale(scale * 0.9F, scale * 0.9F);
      int a = Math.max(4, Math.round(255 * this.a));
      g.drawString(this.minecraft.font, text, 0, 0, (a << 24) | 0xFFFFFF, shadow);
      g.pose().popMatrix();
   }

   private void updateSlider() {
      if (this.dragSlider != null && !(this.sliderX2 <= this.sliderX1)) {
         this.dragSlider.setProgress((this.mx - this.sliderX1) / (this.sliderX2 - this.sliderX1));
      }
   }

   private void drawSettings(GuiGraphics g) {
      this.drawHeader(g, "Client", "Settings", "Look and maintenance");
      float x1 = this.px + 122.0F + 14.0F;
      float x2 = this.px + 480.0F - 14.0F;
      float y = this.py + 56.0F + (1.0F - this.contentA) * 7.0F;
      this.boolRow(g, "Background blur", QuiqzyConfig.guiBlur, "blur", x1, y, x2, () -> {
         QuiqzyConfig.guiBlur = !QuiqzyConfig.guiBlur;
         QuiqzyConfig.save();
         UiSounds.click();
      });
      y += 25.0F;
      this.boolRow(g, "3D tilt (window follows mouse)", QuiqzyConfig.gui3d, "gui3d", x1, y, x2, () -> {
         QuiqzyConfig.gui3d = !QuiqzyConfig.gui3d;
         QuiqzyConfig.save();
         UiSounds.click();
      });
      y += 25.0F;
      this.label(g, "Move HUD elements", x1, y + 5.5F);
      this.button(g, "Edit HUD", '\ue105', x2 - 64.0F, y + 2.0F, x2, y + 18.0F, false, () -> {
         UiSounds.click();
         this.minecraft.setScreen(new dev.quiqzy.client.hud.HudEditorScreen());
      });
      y += 25.0F;
      this.label(g, "Window position", x1, y + 5.5F);
      this.button(g, "Center", '\ue14c', x2 - 56.0F, y + 2.0F, x2, y + 18.0F, false, () -> {
         QuiqzyConfig.guiX = Math.round((this.vw - 480.0F) / 2.0F);
         QuiqzyConfig.guiY = Math.round((this.vh - 312.0F) / 2.0F);
         QuiqzyConfig.save();
         UiSounds.click();
      });
      y += 25.0F;
      this.label(g, this.resetArmed ? "Click again to reset everything" : "Reset all modules, settings and keybinds", x1, y + 5.5F);
      this.button(g, this.resetArmed ? "Confirm" : "Reset", '\ue18e', x2 - 62.0F, y + 2.0F, x2, y + 18.0F, true, () -> {
         if (this.resetArmed) {
            QuiqzyConfig.resetAll();
            UiSounds.removed();
         } else {
            UiSounds.click();
         }

         this.resetArmed = !this.resetArmed;
      });
   }

   private void drawConfigs(GuiGraphics g) {
      List<String> names = QuiqzyConfig.listNamed();
      this.drawHeader(g, "Saved", "Configs", names.size() + " saved  ·  save and load profiles");
      float x1 = this.px + 122.0F + 14.0F;
      float x2 = this.px + 480.0F - 14.0F;
      float y = this.py + 48.0F + (1.0F - this.contentA) * 7.0F;
      this.textField(g, ZeroGui.Field.CONFIG_NAME, this.configName, "Config name", '\ue151', x1, y, x2 - 62.0F, y + 20.0F);
      this.button(g, "Save", '\ue070', x2 - 56.0F, y, x2, y + 20.0F, false, this::saveConfig);
      float top = this.py + 76.0F;
      float bottom = this.py + 312.0F - 8.0F;
      this.listBegin(g, top, bottom);
      float ly = top - this.scroll;
      float start = ly;
      if (names.isEmpty()) {
         ZeroRender.textCentered(g, ZeroFont.REGULAR, "No saved configs yet", (x1 + x2) / 2.0F, top + 30.0F, 9.0F, this.c(-9476768));
      }

      for (String name : names) {
         boolean hover = this.hot(x1 - 5.0F, ly, x2 + 5.0F, ly + 28.0F);
         float hv = this.anim(this.hoverA, "cfg:" + name, hover ? 1.0F : 0.0F, 18.0F);
         if (hv > 0.01F) {
            ZeroRender.rect(g, x1 - 5.0F, ly + 1.0F, x2 + 5.0F, ly + 27.0F, 7.0F, this.c(ZeroRender.alpha(234881023, hv)));
         }

         ZeroRender.text(g, ZeroFont.BOLD, name, x1 + 3.0F, ly + 9.0F, 9.0F, this.c(-725272));
         this.button(g, "Load", '\ue19e', x2 - 76.0F, ly + 6.0F, x2 - 24.0F, ly + 22.0F, false, () -> {
            QuiqzyConfig.loadNamed(name);
            UiSounds.click();
         });
         this.button(g, "", '\ue18e', x2 - 20.0F, ly + 6.0F, x2, ly + 22.0F, true, () -> {
            QuiqzyConfig.deleteNamed(name);
            UiSounds.removed();
         });
         ly += 28.0F;
         ZeroRender.rect(g, x1, ly + 0.5F, x2, ly + 1.0F, 0.0F, this.c(318767103));
         ly++;
      }

      this.listEnd(g, ly - start, top, bottom);
   }

   private void saveConfig() {
      if (!this.configName.isBlank()) {
         QuiqzyConfig.saveNamed(this.configName.trim());
         this.configName = "";
         this.field = ZeroGui.Field.NONE;
         UiSounds.added();
      }
   }

   private void drawKeybinds(GuiGraphics g) {
      long bound = ModuleManager.all().stream().filter(mx -> mx.keybind() > 0).count();
      this.drawHeader(g, "Module", "Keybinds", bound + " bound  ·  click to bind, right click to clear");
      float x1 = this.px + 122.0F + 14.0F;
      float x2 = this.px + 480.0F - 14.0F;
      float top = this.py + 50.0F;
      float bottom = this.py + 312.0F - 8.0F;
      this.listBegin(g, top, bottom);
      float y = top - this.scroll + (1.0F - this.contentA) * 7.0F;
      float start = y;

      for (Module m : ModuleManager.all()) {
         if (y + 24.0F > top - 30.0F && y < bottom + 30.0F) {
            ZeroRender.icon(g, categoryIcon(m.category()), x1 + 7.0F, y + 12.0F, 9.5F, this.c(-9476768));
            ZeroRender.text(g, ZeroFont.BOLD, m.name(), x1 + 19.0F, y + 6.5F, 8.8F, this.c(-725272));
            this.keyChip(g, m, x2, y + 12.0F);
            ZeroRender.rect(g, x1, y + 24.0F, x2, y + 24.5F, 0.0F, this.c(318767103));
         }

         y += 25.0F;
      }

      this.listEnd(g, y - start, top, bottom);
   }

   private void openPicker(Setting<?> s) {
      this.picker = s;
      this.pickerQuery = "";
      this.pickerScroll = this.pickerScrollTarget = 0.0F;
      this.field = ZeroGui.Field.PICKER_SEARCH;
      List<ZeroGui.PickerEntry> all = new ArrayList<>();
      if (s instanceof Setting.Blocks) {
         for (Block block : BuiltInRegistries.BLOCK) {
            Item item = block.asItem();
            if (item != Items.AIR) {
               all.add(new ZeroGui.PickerEntry(BuiltInRegistries.BLOCK.getKey(block).toString(), block.getName().getString(), new ItemStack(item)));
            }
         }
      } else {
         for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
               ItemStack stack = new ItemStack(item);
               all.add(new ZeroGui.PickerEntry(BuiltInRegistries.ITEM.getKey(item).toString(), stack.getHoverName().getString(), stack));
            }
         }
      }

      List<ZeroGui.PickerEntry> sel = new ArrayList<>();
      List<ZeroGui.PickerEntry> rest = new ArrayList<>();
      for (ZeroGui.PickerEntry e : all) {
         (this.pickerHas(e.id) ? sel : rest).add(e);
      }
      sel.addAll(rest);
      this.pickerAll = sel;
      this.colorTarget = null;
      UiSounds.click();
   }

   /** Block, dessen ESP-Farbe gerade eingestellt wird (Rechtsklick im Block-Menü). */
   private ZeroGui.PickerEntry colorTarget;
   private float colorX;
   private float colorY;
   private float colorA;
   private static final int[] PALETTE = new int[]{
      0xFF4D5E, 0xFF8A3D, 0xFFD23D, 0x9BEA4D, 0x3DF07A, 0x3DE0C8, 0x4DE8FF, 0x4D9BFF,
      0x7B6BFF, 0xC27BFF, 0xFF6BD5, 0xFFFFFF, 0xB0B0B8, 0xD9733F, 0xE0B49A, 0x8A5CFF
   };

   private void openColor(ZeroGui.PickerEntry e, float x, float y) {
      if (!this.pickerHas(e.id)) {
         this.pickerToggle(e.id); // Rechtsklick auf einen nicht gewählten Block wählt ihn gleich mit aus
      }
      this.colorTarget = e;
      this.colorX = x;
      this.colorY = y;
      this.colorA = 0.0F;
      this.colorDrag = 0;
      this.loadHsv(ModuleManager.BLOCK_ESP.colorFor(e.id));
      UiSounds.click();
   }

   /** Normale Farb-Einstellung, die gerade im großen Farbfenster bearbeitet wird. */
   private Setting.Color colorSetting;

   private void openColorSetting(Setting.Color s) {
      this.colorSetting = s;
      this.colorTarget = null;
      this.colorA = 0.0F;
      this.colorDrag = 0;
      this.loadHsv((Integer)s.get());
      UiSounds.click();
   }

   private boolean colorOpen() {
      return this.colorTarget != null || this.colorSetting != null;
   }

   private void closeColor() {
      this.colorTarget = null;
      this.colorSetting = null;
   }

   private void setBlockColor(int rgb) {
      if (this.colorSetting != null) {
         this.colorSetting.set(rgb);
         QuiqzyConfig.save();
         return;
      }
      if (this.colorTarget != null) {
         ModuleManager.BLOCK_ESP.blockColors.put(this.colorTarget.id, rgb);
         QuiqzyConfig.save();
         UiSounds.click();
      }
   }

   // ---- Farbwähler-Fenster (Rechtsklick auf Block im Block-ESP-Menü)
   private float cH;
   private float cS;
   private float cV;
   /** 0 = nichts, 1 = Farbfeld wird gezogen, 2 = Hue-Balken wird gezogen */
   private int colorDrag;

   private int hsvRgb() {
      return net.minecraft.util.Mth.hsvToRgb(this.cH, this.cS, this.cV) & 0xFFFFFF;
   }

   private void loadHsv(int rgb) {
      float r = (rgb >> 16 & 255) / 255.0F;
      float gg = (rgb >> 8 & 255) / 255.0F;
      float b = (rgb & 255) / 255.0F;
      float max = Math.max(r, Math.max(gg, b));
      float min = Math.min(r, Math.min(gg, b));
      float d = max - min;
      float h = 0.0F;
      if (d > 0.0F) {
         if (max == r) {
            h = ((gg - b) / d) % 6.0F;
         } else if (max == gg) {
            h = (b - r) / d + 2.0F;
         } else {
            h = (r - gg) / d + 4.0F;
         }
         h /= 6.0F;
         if (h < 0.0F) {
            h += 1.0F;
         }
      }
      this.cH = h;
      this.cS = max == 0.0F ? 0.0F : d / max;
      this.cV = max;
   }

   private void commitColor() {
      this.setBlockColor(this.hsvRgb());
   }

   private void drawColorPopover(GuiGraphics g, float bx1, float by1, float bx2, float by2) {
      this.colorA = Draw.approach(this.colorA, 1.0F, 16.0F, this.dt);
      float e = ease(this.colorA);
      Setting.Color cs = this.colorSetting;
      String id = cs != null ? null : this.colorTarget.id;
      boolean custom = cs != null || ModuleManager.BLOCK_ESP.blockColors.color(id) != null;

      // Ziehen: solange die linke Maustaste gehalten wird live anpassen, beim Loslassen speichern
      if (this.colorDrag != 0) {
         boolean down = org.lwjgl.glfw.GLFW.glfwGetMouseButton(this.minecraft.getWindow().handle(), 0) == 1;
         if (down) {
            this.updateColorDrag();
         } else {
            this.colorDrag = 0;
            this.commitColor();
         }
      }
      int current = this.hsvRgb();

      float w = 262.0F;
      float h = 236.0F;
      float x = this.px + (480.0F - w) / 2.0F;
      float y = this.py + (312.0F - h) / 2.0F + (1.0F - e) * 8.0F;
      float x2 = x + w;
      float y2 = y + h;
      float oldA = this.a;
      this.a = oldA * e;

      // Hintergrund abdunkeln, Klick daneben schließt
      ZeroRender.rect(g, this.px, this.py, this.px + 480.0F, this.py + 312.0F, 13.0F, this.c(ZeroRender.alpha(-1610612736, e * 0.8F)));
      this.regions.add(new ZeroGui.Region(0.0F, 0.0F, this.vw, this.vh, this::closeColor, this::closeColor));
      this.regions.add(new ZeroGui.Region(x, y, x2, y2, () -> {}, () -> {}));

      // Fenster
      ZeroRender.glow(g, x, y + 4.0F, x2, y2 + 4.0F, 12.0F, 22.0F, this.c(-1342177280));
      ZeroRender.glow(g, x, y, x2, y2, 12.0F, 16.0F, this.c(ZeroRender.alpha(0xFF000000 | current, 0.18F)));
      ZeroRender.rect(g, x, y, x2, y2, 12.0F, this.c(-132903671));
      ZeroRender.gradient(g, x, y, x2, y + 60.0F, 12.0F, this.c(ZeroRender.alpha(0xFF000000 | current, 0.10F)), this.c(0x00000000), 90.0F);
      ZeroRender.strokeGradient(g, x, y, x2, y2, 12.0F, 0.75F, this.c(1090509246), this.c(318767103), 80.0F);

      // Kopf: Block-Icon + Name + Schließen
      ZeroRender.rect(g, x + 12.0F, y + 10.0F, x + 34.0F, y + 32.0F, 7.0F, this.c(234881023));
      if (cs == null) {
         g.pose().pushMatrix();
         g.pose().translate(x + 15.0F, y + 13.0F);
         g.renderFakeItem(this.colorTarget.stack, 0, 0);
         g.pose().popMatrix();
      } else {
         ZeroRender.circle(g, x + 23.0F, y + 21.0F, 6.5F, this.c(0xFF000000 | current));
      }
      String title = cs != null ? cs.name() : this.colorTarget.name;
      ZeroRender.text(g, ZeroFont.BOLD, fit(ZeroFont.BOLD, title, 10.0F, w - 90.0F), x + 42.0F, y + 11.0F, 10.0F, this.c(-725272));
      ZeroRender.text(g, ZeroFont.REGULAR, cs != null ? "Colour" : "Block ESP color", x + 42.0F, y + 24.0F, 7.4F, this.c(-6516598));
      boolean hoverClose = this.hot(x2 - 26.0F, y + 10.0F, x2 - 10.0F, y + 26.0F);
      ZeroRender.icon(g, '', x2 - 18.0F, y + 18.0F, 10.0F, this.c(hoverClose ? -725272 : -6516598));
      this.click(x2 - 26.0F, y + 10.0F, x2 - 10.0F, y + 26.0F, this::closeColor);

      // Farbfeld: links weiß -> rechts Hue, oben hell -> unten schwarz
      float sx1 = x + 12.0F;
      float sy1 = y + 42.0F;
      float sx2 = x2 - 12.0F;
      float sy2 = sy1 + 112.0F;
      int hue = 0xFF000000 | (net.minecraft.util.Mth.hsvToRgb(this.cH, 1.0F, 1.0F) & 0xFFFFFF);
      ZeroRender.rect(g, sx1, sy1, sx2, sy2, 7.0F, this.c(hue));
      ZeroRender.gradient(g, sx1, sy1, sx2, sy2, 7.0F, this.c(0xFFFFFFFF), this.c(0x00FFFFFF), 0.0F);
      ZeroRender.gradient(g, sx1, sy1, sx2, sy2, 7.0F, this.c(0x00000000), this.c(0xFF000000), 90.0F);
      ZeroRender.stroke(g, sx1, sy1, sx2, sy2, 7.0F, 0.6F, this.c(352321535));
      float kx = sx1 + this.cS * (sx2 - sx1);
      float ky = sy1 + (1.0F - this.cV) * (sy2 - sy1);
      float kr = this.colorDrag == 1 ? 6.5F : 5.5F;
      ZeroRender.glow(g, kx - kr, ky - kr + 1.0F, kx + kr, ky + kr + 1.0F, kr, 3.0F, this.c(-1610612736));
      ZeroRender.circle(g, kx, ky, kr, this.c(-1));
      ZeroRender.circle(g, kx, ky, kr - 1.8F, this.c(0xFF000000 | current));
      this.click(sx1 - 4.0F, sy1 - 4.0F, sx2 + 4.0F, sy2 + 4.0F, () -> {
         this.colorDrag = 1;
         this.updateColorDrag();
      });

      // Hue-Balken
      ZeroRender.text(g, ZeroFont.REGULAR, "Hue", sx1, sy2 + 8.0F, 7.6F, this.c(-6516598));
      float hy1 = sy2 + 20.0F;
      float hy2 = hy1 + 10.0F;
      int[] stops = new int[]{0xFF0000, 0xFFFF00, 0x00FF00, 0x00FFFF, 0x0000FF, 0xFF00FF, 0xFF0000};
      float segW = (sx2 - sx1) / 6.0F;
      // Enden zuerst (abgerundet), die Mitte drüber
      ZeroRender.gradient(g, sx1, hy1, sx1 + segW + 6.0F, hy2, 5.0F, this.c(0xFF000000 | stops[0]), this.c(0xFF000000 | stops[1]), 0.0F);
      ZeroRender.gradient(g, sx2 - segW - 6.0F, hy1, sx2, hy2, 5.0F, this.c(0xFF000000 | stops[5]), this.c(0xFF000000 | stops[6]), 0.0F);
      for (int i = 1; i < 5; i++) {
         float a1 = sx1 + i * segW;
         ZeroRender.gradient(g, a1 - 0.3F, hy1, a1 + segW + 0.3F, hy2, 0.0F, this.c(0xFF000000 | stops[i]), this.c(0xFF000000 | stops[i + 1]), 0.0F);
      }
      ZeroRender.stroke(g, sx1, hy1, sx2, hy2, 5.0F, 0.6F, this.c(352321535));
      float hx = sx1 + this.cH * (sx2 - sx1);
      float hr = this.colorDrag == 2 ? 7.5F : 6.5F;
      ZeroRender.glow(g, hx - 3.5F, hy1 - 3.0F, hx + 3.5F, hy2 + 3.0F, 3.5F, 3.0F, this.c(-1610612736));
      ZeroRender.rect(g, hx - 3.0F, (hy1 + hy2) / 2.0F - hr, hx + 3.0F, (hy1 + hy2) / 2.0F + hr, 3.0F, this.c(-1));
      ZeroRender.rect(g, hx - 1.6F, (hy1 + hy2) / 2.0F - hr + 1.6F, hx + 1.6F, (hy1 + hy2) / 2.0F + hr - 1.6F, 1.6F, this.c(hue));
      this.click(sx1 - 4.0F, hy1 - 5.0F, sx2 + 4.0F, hy2 + 5.0F, () -> {
         this.colorDrag = 2;
         this.updateColorDrag();
      });

      // Unten: Vorschau + Hex, Schnellfarben, Reset / Done
      float by = hy2 + 12.0F;
      ZeroRender.glow(g, sx1, by, sx1 + 22.0F, by + 22.0F, 7.0F, 6.0F, this.c(ZeroRender.alpha(0xFF000000 | current, 0.55F)));
      ZeroRender.rect(g, sx1, by, sx1 + 22.0F, by + 22.0F, 7.0F, this.c(0xFF000000 | current));
      ZeroRender.stroke(g, sx1, by, sx1 + 22.0F, by + 22.0F, 7.0F, 0.6F, this.c(419430399));
      ZeroRender.text(g, ZeroFont.BOLD, String.format("#%06X", current), sx1 + 29.0F, by + 3.0F, 8.4F, this.c(-725272));
      ZeroRender.text(g, ZeroFont.REGULAR, custom ? "Custom" : "Default", sx1 + 29.0F, by + 13.5F, 7.0F, this.c(custom ? -19858 : -6516598));
      // Schnellfarben: eigene Zeile rechts neben "Hue" (rechtsbündig)
      for (int i = 0; i < 8; i++) {
         int rgb = PALETTE[i];
         float cx = sx2 - 4.0F - (7 - i) * 12.0F;
         float cy = sy2 + 11.5F;
         boolean hv = this.hot(cx - 5.0F, cy - 5.0F, cx + 5.0F, cy + 5.0F);
         if (hv) {
            ZeroRender.glow(g, cx - 5.0F, cy - 5.0F, cx + 5.0F, cy + 5.0F, 5.0F, 3.0F, this.c(ZeroRender.alpha(0xFF000000 | rgb, 0.6F)));
         }
         ZeroRender.circle(g, cx, cy, hv ? 4.4F : 3.8F, this.c(0xFF000000 | rgb));
         this.click(cx - 5.0F, cy - 5.0F, cx + 5.0F, cy + 5.0F, () -> {
            this.loadHsv(rgb);
            this.commitColor();
         });
      }
      this.button(g, "Reset", '', sx2 - 98.0F, by + 3.0F, sx2 - 52.0F, by + 19.0F, false, () -> {
         if (cs != null) {
            cs.reset();
            this.loadHsv((Integer)cs.get());
         } else {
            ModuleManager.BLOCK_ESP.blockColors.remove(id);
            this.loadHsv(ModuleManager.BLOCK_ESP.colorFor(id));
         }
         QuiqzyConfig.save();
         UiSounds.removed();
      });
      // Done: orange Knopf mit Puls-Glow
      boolean hoverDone = this.hot(sx2 - 46.0F, by + 3.0F, sx2, by + 19.0F);
      this.pulseGlow(g, sx2 - 46.0F, by + 3.0F, sx2, by + 19.0F, 6.0F, 6.0F, hoverDone ? 1.0F : 0.55F);
      ZeroRender.gradient(g, sx2 - 46.0F, by + 3.0F, sx2, by + 19.0F, 6.0F, this.c(-19858), this.c(-1286372), 20.0F);
      ZeroRender.textCentered(g, ZeroFont.BOLD, "Done", sx2 - 23.0F, by + 7.4F, 7.6F, this.c(-14807290));
      this.click(sx2 - 46.0F, by + 3.0F, sx2, by + 19.0F, () -> {
         this.commitColor();
         this.closeColor();
      });

      this.a = oldA;
      this.colorBox = new float[]{sx1, sy1, sx2, sy2, hy1, hy2};
   }

   /** Feld/Balken-Grenzen aus dem letzten Frame (fürs Ziehen). */
   private float[] colorBox;

   private void updateColorDrag() {
      if (this.colorBox == null) {
         return;
      }
      float sx1 = this.colorBox[0];
      float sy1 = this.colorBox[1];
      float sx2 = this.colorBox[2];
      float sy2 = this.colorBox[3];
      if (this.colorDrag == 1) {
         this.cS = Math.max(0.0F, Math.min(1.0F, (this.mx - sx1) / (sx2 - sx1)));
         this.cV = 1.0F - Math.max(0.0F, Math.min(1.0F, (this.my - sy1) / (sy2 - sy1)));
      } else if (this.colorDrag == 2) {
         this.cH = Math.max(0.0F, Math.min(0.999F, (this.mx - sx1) / (sx2 - sx1)));
      }
   }

   private void closePicker() {
      if (this.colorTarget != null) {
         this.colorTarget = null;
         return;
      }
      this.picker = null;
      this.field = ZeroGui.Field.NONE;
      UiSounds.click();
   }

   private boolean pickerHas(String id) {
      return this.picker instanceof Setting.Blocks b ? b.contains(id) : this.picker instanceof Setting.Items i && i.contains(id);
   }

   private void pickerToggle(String id) {
      boolean had = this.pickerHas(id);
      if (this.picker instanceof Setting.Blocks b) {
         if (had) {
            b.remove(id);
         } else {
            b.add(id);
         }
      } else if (this.picker instanceof Setting.Items i) {
         if (had) {
            i.remove(id);
         } else {
            i.add(id);
         }
      }

      QuiqzyConfig.save();
      if (had) {
         UiSounds.removed();
      } else {
         UiSounds.added();
      }
   }

   private void drawPicker(GuiGraphics g) {
      this.regions.clear();
      this.modal = false;
      ZeroRender.rect(g, this.px, this.py, this.px + 480.0F, this.py + 312.0F, 13.0F, this.c(ZeroRender.alpha(-1610612736, this.pickerA)));
      float w = 320.0F;
      float h = 250.0F;
      float x1 = this.px + (480.0F - w) / 2.0F;
      float y1 = this.py + (312.0F - h) / 2.0F + (1.0F - this.pickerA) * 6.0F;
      float x2 = x1 + w;
      float y2 = y1 + h;
      ZeroRender.glow(g, x1, y1 + 3.0F, x2, y2 + 3.0F, 11.0F, 18.0F, this.c(-1342177280));
      ZeroRender.rect(g, x1, y1, x2, y2, 11.0F, this.c(-132903671));
      ZeroRender.strokeGradient(g, x1, y1, x2, y2, 11.0F, 0.75F, this.c(1090509246), this.c(318767103), 80.0F);
      this.regions.add(new ZeroGui.Region(0.0F, 0.0F, this.vw, this.vh, this::closePicker, null));
      this.regions.add(new ZeroGui.Region(x1, y1, x2, y2, () -> {}, null));
      ZeroRender.text(g, ZeroFont.BOLD, this.picker.name(), x1 + 12.0F, y1 + 11.0F, 11.0F, this.c(-725272));
      boolean hoverClose = this.hot(x2 - 22.0F, y1 + 7.0F, x2 - 8.0F, y1 + 21.0F);
      ZeroRender.icon(g, '\ue1b2', x2 - 15.0F, y1 + 14.0F, 10.0F, this.c(hoverClose ? -725272 : -6516598));
      this.click(x2 - 22.0F, y1 + 7.0F, x2 - 8.0F, y1 + 21.0F, this::closePicker);
      this.textField(g, ZeroGui.Field.PICKER_SEARCH, this.pickerQuery, "Search", '\ue155', x1 + 12.0F, y1 + 28.0F, x2 - 12.0F, y1 + 47.0F);
      String q = this.pickerQuery.trim().toLowerCase(Locale.ROOT);
      // Reihenfolge steht seit dem Öffnen fest -> ein angeklickter Block bleibt an seinem Platz
      List<ZeroGui.PickerEntry> selectedFirst = new ArrayList<>();

      for (ZeroGui.PickerEntry e : this.pickerAll) {
         if (q.isEmpty() || e.id.contains(q) || e.name.toLowerCase(Locale.ROOT).contains(q)) {
            selectedFirst.add(e);
         }
      }
      boolean espPicker = this.picker == ModuleManager.BLOCK_ESP.blocks;
      float cell = 24.0F;
      float gridX = x1 + 12.0F;
      int columns = (int)((w - 24.0F) / cell);
      float top = y1 + 53.0F;
      float bottom = y2 - 22.0F;
      this.pickerScroll = Draw.approach(this.pickerScroll, this.pickerScrollTarget, 18.0F, this.dt);
      this.pickerScrollMax = Math.max(0.0F, (float)Math.ceil((double)selectedFirst.size() / columns) * cell - (bottom - top));
      this.pickerScrollTarget = Math.max(0.0F, Math.min(this.pickerScrollMax, this.pickerScrollTarget));
      float oldY1 = this.clipY1;
      float oldY2 = this.clipY2;
      this.clipY1 = top;
      this.clipY2 = bottom;
      g.enableScissor(Math.round(x1), Math.round(top), Math.round(x2), Math.round(bottom));
      String hovered = null;

      for (int i = 0; i < selectedFirst.size(); i++) {
         float cx = gridX + i % columns * cell;
         float cy = top + i / columns * cell - this.pickerScroll;
         if (!(cy + cell < top) && !(cy > bottom)) {
            ZeroGui.PickerEntry ex = selectedFirst.get(i);
            boolean on = this.pickerHas(ex.id);
            boolean hover = this.hot(cx, cy, cx + cell, cy + cell);
            if (hover) {
               hovered = ex.name;
            }

            float sv = this.anim(this.onA, "pk:" + ex.id, on ? 1.0F : 0.0F, 16.0F);
            if (hover && sv < 0.99F) {
               ZeroRender.rect(g, cx + 1.0F, cy + 1.0F, cx + cell - 1.0F, cy + cell - 1.0F, 6.0F, this.c(352321535));
            }
            if (sv > 0.01F) {
               this.pulseGlow(g, cx + 1.0F, cy + 1.0F, cx + cell - 1.0F, cy + cell - 1.0F, 6.0F, 5.0F, 0.55F * sv);
               ZeroRender.gradient(g, cx + 1.0F, cy + 1.0F, cx + cell - 1.0F, cy + cell - 1.0F, 6.0F,
                  this.c(ZeroRender.alpha(1358929727, sv)), this.c(ZeroRender.alpha(822058268, sv)), 35.0F);
               ZeroRender.stroke(g, cx + 1.0F, cy + 1.0F, cx + cell - 1.0F, cy + cell - 1.0F, 6.0F, 0.75F, this.c(ZeroRender.alpha(-1057978817, sv)));
            }

            g.pose().pushMatrix();
            g.pose().translate(cx + 4.0F, cy + 4.0F);
            g.renderFakeItem(ex.stack, 0, 0);
            g.pose().popMatrix();
            if (espPicker && on) {
               // kleine Farbmarke: so wird der Block im ESP gezeichnet
               int rgb = ModuleManager.BLOCK_ESP.colorFor(ex.id);
               ZeroRender.glow(g, cx + cell - 8.5F, cy + 2.5F, cx + cell - 2.5F, cy + 8.5F, 3.0F, 2.5F, this.c(ZeroRender.alpha(0xFF000000 | rgb, 0.6F)));
               ZeroRender.circle(g, cx + cell - 5.5F, cy + 5.5F, 2.6F, this.c(0xFF000000 | rgb));
            }
            float fcx = cx;
            float fcy = cy;
            this.click(cx, cy, cx + cell, cy + cell, () -> this.pickerToggle(ex.id), espPicker ? () -> this.openColor(ex, fcx, fcy) : null);
         }
      }

      g.disableScissor();
      this.clipY1 = oldY1;
      this.clipY2 = oldY2;
      long count = this.pickerAll.stream().filter(exx -> this.pickerHas(exx.id)).count();
      if (this.colorTarget != null) {
         this.drawColorPopover(g, x1, y1, x2, y2);
      }
      ZeroRender.text(
         g,
         ZeroFont.REGULAR,
         hovered != null ? hovered : count + " selected  ·  click to add or remove" + (espPicker ? "  ·  right-click = color" : ""),
         x1 + 12.0F,
         y2 - 16.0F,
         7.6F,
         this.c(hovered != null ? -725272 : -6516598)
      );
   }

   private void listBegin(GuiGraphics g, float top, float bottom) {
      this.scroll = Draw.approach(this.scroll, this.scrollTarget, 18.0F, this.dt);
      this.clipY1 = top;
      this.clipY2 = bottom;
      g.enableScissor(Math.round(this.px + 122.0F + 1.0F), Math.round(top), Math.round(this.px + 480.0F), Math.round(bottom));
   }

   private void listEnd(GuiGraphics g, float contentHeight, float top, float bottom) {
      g.disableScissor();
      this.clipY1 = -1000000.0F;
      this.clipY2 = 1000000.0F;
      float view = bottom - top;
      this.scrollMax = Math.max(0.0F, contentHeight - view);
      this.scrollTarget = Math.max(0.0F, Math.min(this.scrollMax, this.scrollTarget));
      if (this.scrollMax > 0.0F) {
         float x = this.px + 480.0F - 5.5F;
         float barH = Math.max(18.0F, view * view / contentHeight);
         float y = top + (view - barH) * (this.scroll / this.scrollMax);
         ZeroRender.rect(g, x, y, x + 2.5F, y + barH, 1.25F, this.c(822083583));
      }
   }

   private void textField(GuiGraphics g, ZeroGui.Field id, String value, String placeholder, char icon, float x1, float y1, float x2, float y2) {
      boolean focused = this.field == id;
      float f = this.anim(this.hoverA, id, focused ? 1.0F : 0.0F, 18.0F);
      float h = y2 - y1;
      if (f > 0.01F) {
         ZeroRender.glow(g, x1, y1, x2, y2, 7.0F, 5.0F, this.c(ZeroRender.alpha(1357932319, f)));
      }

      ZeroRender.rect(g, x1, y1, x2, y2, 7.0F, this.c(1291845632));
      ZeroRender.rect(g, x1, y1, x2, y2, 7.0F, this.c(234881023));
      ZeroRender.stroke(g, x1, y1, x2, y2, 7.0F, 0.6F, this.c(ZeroRender.lerp(352321535, -1326414273, f)));
      ZeroRender.icon(g, icon, x1 + 11.0F, y1 + h / 2.0F, 9.5F, this.c(ZeroRender.lerp(-6516598, -19858, f)));
      float tx = x1 + 21.0F;
      float ty = y1 + h / 2.0F - 4.1F;
      if (value.isEmpty() && !focused) {
         ZeroRender.text(g, ZeroFont.REGULAR, placeholder, tx, ty, 8.0F, this.c(-9476768));
      } else {
         ZeroRender.text(g, ZeroFont.REGULAR, fitEnd(value, 8.0F, x2 - tx - 8.0F) + this.caret(focused), tx, ty, 8.0F, this.c(-725272));
      }

      this.click(x1, y1, x2, y2, () -> this.field = id);
   }

   private String caret(boolean focused) {
      return focused && this.time % 1.0F < 0.55F ? "|" : "";
   }

   private void button(GuiGraphics g, String label, char icon, float x1, float y1, float x2, float y2, boolean danger, Runnable action) {
      boolean hover = this.hot(x1, y1, x2, y2);
      float hv = this.anim(this.hoverA, "btn:" + label + icon + (int)y1, hover ? 1.0F : 0.0F, 18.0F);
      int base = danger ? -1748149 : -1014209;
      ZeroRender.rect(g, x1, y1, x2, y2, 6.0F, this.c(ZeroRender.alpha(base, 0.13F + 0.14F * hv)));
      ZeroRender.stroke(g, x1, y1, x2, y2, 6.0F, 0.6F, this.c(ZeroRender.alpha(base, 0.4F + 0.3F * hv)));
      int color = danger ? -25964 : -19858;
      float cy = (y1 + y2) / 2.0F;
      if (label.isEmpty()) {
         ZeroRender.icon(g, icon, (x1 + x2) / 2.0F, cy, 9.0F, this.c(color));
      } else {
         float w = ZeroFont.BOLD.width(label, 7.6F, 0.0F) + 12.0F;
         float x = (x1 + x2) / 2.0F - w / 2.0F;
         ZeroRender.icon(g, icon, x + 4.0F, cy, 8.5F, this.c(color));
         ZeroRender.text(g, ZeroFont.BOLD, label, x + 12.0F, cy - 4.0F, 7.6F, this.c(color));
      }

      this.click(x1, y1, x2, y2, action);
   }

   private void switchContent() {
      this.gridX.clear();
      this.gridY.clear();
      this.contentA = 0.0F;
      this.scroll = this.scrollTarget = 0.0F;
      this.resetArmed = false;
   }

   private void clearSearch() {
      this.search = "";
      if (this.field == ZeroGui.Field.SEARCH) {
         this.field = ZeroGui.Field.NONE;
      }
   }

   private int c(int color) {
      return ZeroRender.alpha(color, this.a);
   }

   private boolean hot(float x1, float y1, float x2, float y2) {
      if (this.modal) {
         return false;
      } else {
         return !(this.my < this.clipY1) && !(this.my >= this.clipY2) ? this.mx >= x1 && this.mx < x2 && this.my >= y1 && this.my < y2 : false;
      }
   }

   private void click(float x1, float y1, float x2, float y2, Runnable left) {
      this.click(x1, y1, x2, y2, left, null);
   }

   private void click(float x1, float y1, float x2, float y2, Runnable left, Runnable right) {
      y1 = Math.max(y1, this.clipY1);
      y2 = Math.min(y2, this.clipY2);
      if (y2 > y1) {
         this.regions.add(new ZeroGui.Region(x1, y1, x2, y2, left, right));
      }
   }

   private float anim(Map<Object, Float> map, Object key, float target, float speed) {
      float v = Draw.approach(map.getOrDefault(key, target), target, speed, this.dt);
      map.put(key, v);
      return v;
   }

   private static String fit(ZeroFont font, String s, float size, float maxWidth) {
      if (font.width(s, size, 0.0F) <= maxWidth) {
         return s;
      } else {
         String cut = s;

         while (!cut.isEmpty() && font.width(cut + "…", size, 0.0F) > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
         }

         return cut.trim() + "…";
      }
   }

   private static String fitEnd(String s, float size, float maxWidth) {
      String cut = s;

      while (!cut.isEmpty() && ZeroFont.REGULAR.width(cut, size, 0.0F) > maxWidth) {
         cut = cut.substring(1);
      }

      return cut;
   }

   private static String keyName(int key) {
      if (key <= 0) {
         return "None";
      } else {
         try {
            return Type.KEYSYM.getOrCreate(key).getDisplayName().getString().toUpperCase(Locale.ROOT);
         } catch (Exception var2) {
            return "KEY";
         }
      }
   }

   @Override
   public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
      float[] lp = this.toPanel((float)event.x() * this.k, (float)event.y() * this.k);
      this.mx = lp[0];
      this.my = lp[1];
      boolean left = event.button() == 0;
      boolean right = event.button() == 1;
      if (!left && !right) {
         return super.mouseClicked(event, dbl);
      } else {
         this.wasListening = this.listening;
         ZeroGui.Field wasField = this.field;
         if (this.field == ZeroGui.Field.HEX) {
            this.commitHex();
         }

         if (this.field != ZeroGui.Field.PICKER_SEARCH) {
            this.field = ZeroGui.Field.NONE;
         }

         this.listening = null;

         for (int i = this.regions.size() - 1; i >= 0; i--) {
            ZeroGui.Region r = this.regions.get(i);
            if (!(this.mx < r.x1) && !(this.mx >= r.x2) && !(this.my < r.y1) && !(this.my >= r.y2)) {
               Runnable action = left ? r.left : r.right;
               if (action != null) {
                  action.run();
                  return true;
               }

               if (!left) {
                  return true;
               }
            }
         }

         this.field = wasField == ZeroGui.Field.PICKER_SEARCH ? wasField : ZeroGui.Field.NONE;
         return super.mouseClicked(event, dbl);
      }
   }

   @Override
   public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
      float[] lp2 = this.toPanel((float)event.x() * this.k, (float)event.y() * this.k);
      this.mx = lp2[0];
      this.my = lp2[1];
      if (this.dragSlider != null) {
         this.updateSlider();
         return true;
      } else if (this.dragPanel) {
         QuiqzyConfig.guiX = Math.round(this.mx - this.dragOffX);
         QuiqzyConfig.guiY = Math.round(this.my - this.dragOffY);
         this.clamp();
         return true;
      } else {
         return super.mouseDragged(event, dx, dy);
      }
   }

   @Override
   public boolean mouseReleased(MouseButtonEvent event) {
      if (this.dragSlider != null) {
         if (this.dragSlider == ModuleManager.SUS_CHUNK.radius) {
            ModuleManager.SUS_CHUNK.requestRescan();
         }

         this.dragSlider = null;
         QuiqzyConfig.save();
         return true;
      } else if (this.dragPanel) {
         this.dragPanel = false;
         QuiqzyConfig.save();
         return true;
      } else {
         return super.mouseReleased(event);
      }
   }

   @Override
   public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
      if (this.picker != null) {
         this.pickerScrollTarget = Math.max(0.0F, Math.min(this.pickerScrollMax, this.pickerScrollTarget - (float)vertical * 28.0F));
      } else {
         this.scrollTarget = Math.max(0.0F, Math.min(this.scrollMax, this.scrollTarget - (float)vertical * 30.0F));
      }

      return true;
   }

   @Override
   public boolean keyPressed(KeyEvent event) {
      int key = event.key();
      if (this.listening == null) {
         if (this.field != ZeroGui.Field.NONE) {
            switch (key) {
               case 256:
                  if (this.picker != null) {
                     this.closePicker();
                  } else {
                     this.field = ZeroGui.Field.NONE;
                  }
                  break;
               case 257:
               case 335:
                  if (this.field == ZeroGui.Field.REPLACE) {
                     this.commitReplace();
                  } else if (this.field == ZeroGui.Field.CONFIG_NAME) {
                     this.saveConfig();
                  } else if (this.field == ZeroGui.Field.HEX) {
                     this.commitHex();
                  }

                  if (this.field != ZeroGui.Field.PICKER_SEARCH) {
                     this.field = ZeroGui.Field.NONE;
                  }
                  break;
               case 259:
                  this.editField(s -> s.isEmpty() ? s : s.substring(0, s.length() - 1));
                  break;
               default:
                  return true;
            }

            return true;
         } else if (this.colorSetting != null && key == 256) {
            this.closeColor();
            return true;
         } else if (this.picker != null && key == 256) {
            this.closePicker();
            return true;
         } else if (key == KeyBindingHelper.getBoundKeyOf(QuiqzyClient.OPEN_GUI).getValue()) {
            this.onClose();
            return true;
         } else {
            return super.keyPressed(event);
         }
      } else {
         if (key == 261 || key == 259) {
            this.listening.setKeybind(-1);
         } else if (key != 256) {
            this.listening.setKeybind(key);
         }

         this.listening = null;
         QuiqzyConfig.save();
         UiSounds.click();
         return true;
      }
   }

   @Override
   public boolean charTyped(CharacterEvent event) {
      if (this.field != ZeroGui.Field.NONE && event.isAllowedChatCharacter()) {
         String typed = event.codepointAsString();
         int max = this.field == ZeroGui.Field.REPLACE ? 60 : 32;
         this.editField(s -> s.length() < max ? s + typed : s);
         return true;
      } else {
         return super.charTyped(event);
      }
   }

   private void editField(UnaryOperator<String> edit) {
      switch (this.field) {
         case SEARCH:
            this.search = edit.apply(this.search);
            this.scroll = this.scrollTarget = 0.0F;
            break;
         case CONFIG_NAME:
            this.configName = edit.apply(this.configName);
            break;
         case PICKER_SEARCH:
            this.pickerQuery = edit.apply(this.pickerQuery);
            this.pickerScroll = this.pickerScrollTarget = 0.0F;
            break;
         case HEX:
            this.hexText = edit.apply(this.hexText);
            break;
         case REPLACE:
            this.replaceText = edit.apply(this.replaceText);
      }
   }

   private void commitHex() {
      if (this.hexTarget != null) {
         this.hexTarget.setHex(this.hexText);
         QuiqzyConfig.save();
      }

      this.hexTarget = null;
   }

   @Override
   public void onClose() {
      UiSounds.gui(false);
      super.onClose();
   }

   @Override
   public void removed() {
      ZeroBlur.release();
      QuiqzyConfig.save();
      super.removed();
   }

   public void testShow(Module.Category category, boolean gridView, Module expand) {
      page = ZeroGui.Page.MODULES;
      selected = category;
      grid = gridView;
      mode = ZeroGui.Mode.ALL;
      this.search = "";
      this.scroll = this.scrollTarget = 0.0F;
      this.expanded.clear();
      if (expand != null) {
         this.expanded.put(expand, true);
      }
   }

   public void testPage(int index) {
      page = ZeroGui.Page.values()[index];
      this.switchContent();
   }

   public void testPicker(Setting<?> setting) {
      this.openPicker(setting);
   }

   /** Selbsttest: Farbfenster fuer eine normale Farb-Einstellung oeffnen. */
   public void testColorSetting(Setting.Color c) {
      this.openColorSetting(c);
   }

   /** Selbsttest: Zeile Nr. index des aktuellen Scoreboards bearbeiten. */
   public void testReplaceLine(int index, String text, boolean commit) {
      var lines = dev.quiqzy.client.module.misc.TextReplaceModule.serverLines(this.minecraft, new net.minecraft.network.chat.Component[1]);
      if (index < lines.size()) this.testReplace(lines.get(index).plain(), text, commit);
   }

   public void testReplace(String from, String text, boolean commit) {
      this.replaceFrom = from;
      this.replaceText = text;
      this.field = ZeroGui.Field.REPLACE;
      if (commit) {
         this.commitReplace();
      }
   }

   public String testColorState() {
      return "colorSetting=" + (this.colorSetting == null ? "-" : this.colorSetting.name()) + " picker=" + (this.picker != null) + " a=" + this.colorA;
   }

   /** Selbsttest: Farbfenster fuer einen Block oeffnen. */
   public void testColor(String id) {
      for (ZeroGui.PickerEntry e : this.pickerAll) {
         if (e.id.equals(id)) {
            this.openColor(e, this.px + 200.0F, this.py + 120.0F);
            return;
         }
      }
   }

   public void testSearch(String text) {
      this.search = text;
   }

   public void testClick(float x, float y) {
      this.mouseClicked(new MouseButtonEvent((this.px + x) / this.k, (this.py + y) / this.k, new MouseButtonInfo(0, 0)), false);
   }

   public String testState() {
      return "page="
         + page
         + " selected="
         + selected
         + " grid="
         + grid
         + " mode="
         + mode
         + " search='"
         + this.search
         + "' listening="
         + (this.listening != null ? this.listening.name() : "-")
         + " field="
         + this.field
         + " picker="
         + (this.picker != null);
   }

   private static enum Field {
      NONE,
      SEARCH,
      CONFIG_NAME,
      PICKER_SEARCH,
      HEX,
      REPLACE;
   }

   private static enum Mode {
      ALL("All"),
      CRYSTAL("Crystal"),
      MACE("Mace"),
      SPEAR("Spear");

      final String label;

      private Mode(String label) {
         this.label = label;
      }

      boolean has(Module m) {
         return switch (this) {
            case ALL -> true;
            case CRYSTAL -> m == ModuleManager.ANCHOR || m == ModuleManager.AUTO_TOTEM || m == ModuleManager.FAST_XP
               || m == ModuleManager.CRYSTAL_AURA || m == ModuleManager.CRYSTAL_OPTIMIZER || m == ModuleManager.SURROUND
               || m == ModuleManager.AUTO_DOUBLE_HAND || m == ModuleManager.KEY_PEARL || m == ModuleManager.HOLE_ESP
               || m == ModuleManager.ANCHOR_AURA || m == ModuleManager.HIT_CRYSTAL || m == ModuleManager.TRIPLE_TAP;
            case MACE -> m == ModuleManager.STUN_SLAM || m == ModuleManager.AUTO_ELY_MACE || m == ModuleManager.ELYTRA_RECAST;
            case SPEAR -> m == ModuleManager.INSTANT_LAUNCH;
         };
      }
   }

   private static enum Page {
      MODULES,
      SETTINGS,
      CONFIGS,
      KEYBINDS;
   }

   private record PickerEntry(String id, String name, ItemStack stack) {
   }

   private record Region(float x1, float y1, float x2, float y2, Runnable left, Runnable right) {
   }
}
