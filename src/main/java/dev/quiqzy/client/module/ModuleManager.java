package dev.quiqzy.client.module;

import dev.quiqzy.client.config.QuiqzyConfig;
import dev.quiqzy.client.module.combat.AnchorModule;
import dev.quiqzy.client.module.combat.AutoElyMaceModule;
import dev.quiqzy.client.module.combat.InstantLaunchModule;
import dev.quiqzy.client.module.combat.StunSlamModule;
import dev.quiqzy.client.module.misc.HudModule;
import dev.quiqzy.client.module.misc.KeystrokesModule;
import dev.quiqzy.client.module.misc.SoundsModule;
import dev.quiqzy.client.module.movement.ElytraRecastModule;
import dev.quiqzy.client.module.player.AutoEatModule;
import dev.quiqzy.client.module.player.AutoToolModule;
import dev.quiqzy.client.module.player.AutoTotemModule;
import dev.quiqzy.client.module.player.DurabilityWarningModule;
import dev.quiqzy.client.module.player.FastXpModule;
import dev.quiqzy.client.module.visuals.AmbienceModule;
import dev.quiqzy.client.module.visuals.AvatarModule;
import dev.quiqzy.client.module.visuals.HitEffectsModule;
import dev.quiqzy.client.module.visuals.WeaponSkinsModule;
import dev.quiqzy.client.module.visuals.EmbersModule;
import dev.quiqzy.client.module.visuals.LowFireModule;
import dev.quiqzy.client.module.misc.TargetHudModule;
import dev.quiqzy.client.module.misc.ModuleListModule;
import dev.quiqzy.client.module.misc.WatermarkModule;
import dev.quiqzy.client.module.misc.ArmorHudModule;
import dev.quiqzy.client.module.misc.PotionHudModule;
import dev.quiqzy.client.module.visuals.NoHurtCamModule;
import dev.quiqzy.client.module.visuals.NametagsModule;
import dev.quiqzy.client.module.visuals.NoFogModule;
import dev.quiqzy.client.module.misc.StartMenuModule;
import dev.quiqzy.client.module.misc.LowHpWarningModule;
import dev.quiqzy.client.module.misc.SessionStatsModule;
import dev.quiqzy.client.module.misc.ItemCounterModule;
import dev.quiqzy.client.module.misc.PopCounterModule;
import dev.quiqzy.client.module.misc.ChatTweaksModule;
import dev.quiqzy.client.module.misc.AccentModule;
import dev.quiqzy.client.module.misc.StatusBarModule;
import dev.quiqzy.client.module.misc.RadarModule;
import dev.quiqzy.client.module.misc.TextReplaceModule;
import dev.quiqzy.client.module.misc.SpotifyModule;
import dev.quiqzy.client.module.visuals.FreecamModule;
import dev.quiqzy.client.module.visuals.VisualFxModule;
import dev.quiqzy.client.module.world.BaseFinderModule;
import dev.quiqzy.client.module.combat.KillAuraModule;
import dev.quiqzy.client.module.combat.TriggerBotModule;
import dev.quiqzy.client.module.combat.AimAssistModule;
import dev.quiqzy.client.module.combat.AutoClickerModule;
import dev.quiqzy.client.module.combat.VelocityModule;
import dev.quiqzy.client.module.combat.HitboxesModule;
import dev.quiqzy.client.module.combat.CriticalsModule;
import dev.quiqzy.client.module.combat.WTapModule;
import dev.quiqzy.client.module.combat.AutoWeaponModule;
import dev.quiqzy.client.module.combat.AutoGapModule;
import dev.quiqzy.client.module.combat.AutoPotModule;
import dev.quiqzy.client.module.combat.AutoArmorModule;
import dev.quiqzy.client.module.combat.CrystalAuraModule;
import dev.quiqzy.client.module.world.SpawnerBotModule;
import dev.quiqzy.client.module.combat.CrystalOptimizerModule;
import dev.quiqzy.client.module.combat.SurroundModule;
import dev.quiqzy.client.module.combat.AutoDoubleHandModule;
import dev.quiqzy.client.module.combat.KeyPearlModule;
import dev.quiqzy.client.module.combat.HoleEspModule;
import dev.quiqzy.client.module.combat.AnchorAuraModule;
import dev.quiqzy.client.module.combat.HitCrystalModule;
import dev.quiqzy.client.module.combat.CartAuraModule;
import dev.quiqzy.client.module.combat.FlameCartModule;
import dev.quiqzy.client.module.combat.AutoJumpResetModule;
import dev.quiqzy.client.module.combat.ShieldBreakerModule;
import dev.quiqzy.client.module.combat.AutoShieldModule;
import dev.quiqzy.client.module.combat.AutoPearlModule;
import dev.quiqzy.client.module.combat.AutoClutchModule;
import dev.quiqzy.client.module.combat.AutoDrainModule;
import dev.quiqzy.client.module.combat.FastBowModule;
import dev.quiqzy.client.module.combat.AutoSprintModule;
import dev.quiqzy.client.module.visuals.TrajectoriesModule;
import dev.quiqzy.client.module.combat.TripleTapModule;
import dev.quiqzy.client.module.visuals.GlassModule;
import dev.quiqzy.client.module.visuals.XRayModule;
import dev.quiqzy.client.module.visuals.CyberspaceModule;
import dev.quiqzy.client.module.visuals.RainFxModule;
import dev.quiqzy.client.module.visuals.AnimationsModule;
import dev.quiqzy.client.module.visuals.CrosshairModule;
import dev.quiqzy.client.module.world.DeathWaypointModule;
import dev.quiqzy.client.module.visuals.Esp2DModule;
import dev.quiqzy.client.module.visuals.CustomFovModule;
import dev.quiqzy.client.module.player.ToggleSprintModule;
import dev.quiqzy.client.module.player.AutoRespawnModule;
import dev.quiqzy.client.module.visuals.EntityEspModule;
import dev.quiqzy.client.module.visuals.FullbrightModule;
import dev.quiqzy.client.module.visuals.HandShaderModule;
import dev.quiqzy.client.module.visuals.NoOverlayModule;
import dev.quiqzy.client.module.visuals.TracersModule;
import dev.quiqzy.client.module.visuals.ZoomModule;
import dev.quiqzy.client.module.world.BlockEspModule;
import dev.quiqzy.client.module.world.SusChunkModule;
import dev.quiqzy.client.module.world.XrayResearchModule;
import dev.quiqzy.client.module.world.ActivityInspectorModule;
import dev.quiqzy.client.module.world.BaseFinderResearchModule;
import dev.quiqzy.client.module.world.SoundChunkModule;
import dev.quiqzy.client.module.world.WaypointsModule;
import dev.quiqzy.client.module.world.BaseRadarModule;
import dev.quiqzy.client.module.world.SpawnerNotiModule;
import dev.quiqzy.client.module.visuals.SpawnerNametagsModule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class ModuleManager {
   private static final List<Module> MODULES = new ArrayList<>();
   public static final StunSlamModule STUN_SLAM = register(new StunSlamModule());
   public static final InstantLaunchModule INSTANT_LAUNCH = register(new InstantLaunchModule());
   public static final AnchorModule ANCHOR = register(new AnchorModule());
   public static final AutoElyMaceModule AUTO_ELY_MACE = register(new AutoElyMaceModule());
   public static final FastXpModule FAST_XP = register(new FastXpModule());
   public static final AutoToolModule AUTO_TOOL = register(new AutoToolModule());
   public static final AutoEatModule AUTO_EAT = register(new AutoEatModule());
   public static final AutoTotemModule AUTO_TOTEM = register(new AutoTotemModule());
   public static final DurabilityWarningModule DURABILITY_WARNING = register(new DurabilityWarningModule());
   public static final AmbienceModule AMBIENCE = register(new AmbienceModule());
   public static final FullbrightModule FULLBRIGHT = register(new FullbrightModule());
   public static final HandShaderModule HAND_SHADER = register(new HandShaderModule());
   public static final EntityEspModule ENTITY_ESP = register(new EntityEspModule());
   public static final TracersModule TRACERS = register(new TracersModule());
   public static final ZoomModule ZOOM = register(new ZoomModule());
   public static final NoOverlayModule NO_OVERLAY = register(new NoOverlayModule());
   public static final AvatarModule AVATAR = register(new AvatarModule());
   public static final HitEffectsModule HIT_EFFECTS = register(new HitEffectsModule());
   public static final WeaponSkinsModule WEAPON_SKINS = register(new WeaponSkinsModule());
   public static final EmbersModule EMBERS = register(new EmbersModule());
   public static final LowFireModule LOW_FIRE = register(new LowFireModule());
   public static final TargetHudModule TARGET_HUD = register(new TargetHudModule());
   public static final NoHurtCamModule NO_HURT_CAM = register(new NoHurtCamModule());
   public static final NametagsModule NAMETAGS = register(new NametagsModule());
   public static final NoFogModule NO_FOG = register(new NoFogModule());
   public static final StartMenuModule START_MENU = register(new StartMenuModule());
   public static final CrosshairModule CROSSHAIR = register(new CrosshairModule());
   public static final DeathWaypointModule DEATH_WAYPOINT = register(new DeathWaypointModule());
   public static final LowHpWarningModule LOW_HP = register(new LowHpWarningModule());
   public static final AnimationsModule ANIMATIONS = register(new AnimationsModule());
   public static final SessionStatsModule SESSION_STATS = register(new SessionStatsModule());
   public static final ItemCounterModule ITEM_COUNTER = register(new ItemCounterModule());
   public static final PopCounterModule POP_COUNTER = register(new PopCounterModule());
   public static final ChatTweaksModule CHAT_TWEAKS = register(new ChatTweaksModule());
   public static final AccentModule ACCENT = register(new AccentModule());
   public static final StatusBarModule STATUS_BAR = register(new StatusBarModule());
   public static final RadarModule RADAR = register(new RadarModule());
   public static final TextReplaceModule TEXT_REPLACE = register(new TextReplaceModule());
   public static final SpotifyModule SPOTIFY = register(new SpotifyModule());
   public static final FreecamModule FREECAM = register(new FreecamModule());
   public static final VisualFxModule VISUAL_FX = register(new VisualFxModule());
   public static final BaseFinderModule BASE_FINDER = register(new BaseFinderModule());
   public static final GlassModule GLASS = register(new GlassModule());
   public static final XRayModule XRAY = register(new XRayModule());
   public static final CyberspaceModule CYBERSPACE = register(new CyberspaceModule());
   public static final RainFxModule RAIN_FX = register(new RainFxModule());
   public static final KillAuraModule KILL_AURA = register(new KillAuraModule());
   public static final TriggerBotModule TRIGGER_BOT = register(new TriggerBotModule());
   public static final AimAssistModule AIM_ASSIST = register(new AimAssistModule());
   public static final AutoClickerModule AUTO_CLICKER = register(new AutoClickerModule());
   public static final VelocityModule VELOCITY = register(new VelocityModule());
   public static final HitboxesModule HITBOXES = register(new HitboxesModule());
   public static final CriticalsModule CRITICALS = register(new CriticalsModule());
   public static final WTapModule W_TAP = register(new WTapModule());
   public static final AutoWeaponModule AUTO_WEAPON = register(new AutoWeaponModule());
   public static final AutoGapModule AUTO_GAP = register(new AutoGapModule());
   public static final AutoPotModule AUTO_POT = register(new AutoPotModule());
   public static final AutoArmorModule AUTO_ARMOR = register(new AutoArmorModule());
   public static final CrystalAuraModule CRYSTAL_AURA = register(new CrystalAuraModule());
   public static final SpawnerBotModule SPAWNER_BOT = register(new SpawnerBotModule());
   public static final CrystalOptimizerModule CRYSTAL_OPTIMIZER = register(new CrystalOptimizerModule());
   public static final SurroundModule SURROUND = register(new SurroundModule());
   public static final AutoDoubleHandModule AUTO_DOUBLE_HAND = register(new AutoDoubleHandModule());
   public static final KeyPearlModule KEY_PEARL = register(new KeyPearlModule());
   public static final HoleEspModule HOLE_ESP = register(new HoleEspModule());
   public static final AnchorAuraModule ANCHOR_AURA = register(new AnchorAuraModule());
   public static final HitCrystalModule HIT_CRYSTAL = register(new HitCrystalModule());
   public static final CartAuraModule CART_AURA = register(new CartAuraModule());
   public static final FlameCartModule FLAME_CART = register(new FlameCartModule());
   public static final AutoJumpResetModule AUTO_JUMP_RESET = register(new AutoJumpResetModule());
   public static final ShieldBreakerModule SHIELD_BREAKER = register(new ShieldBreakerModule());
   public static final AutoShieldModule AUTO_SHIELD = register(new AutoShieldModule());
   public static final AutoPearlModule AUTO_PEARL = register(new AutoPearlModule());
   public static final AutoClutchModule AUTO_CLUTCH = register(new AutoClutchModule());
   public static final AutoDrainModule AUTO_DRAIN = register(new AutoDrainModule());
   public static final FastBowModule FAST_BOW = register(new FastBowModule());
   public static final AutoSprintModule AUTO_SPRINT = register(new AutoSprintModule());
   public static final TrajectoriesModule TRAJECTORIES = register(new TrajectoriesModule());
   public static final TripleTapModule TRIPLE_TAP = register(new TripleTapModule());
   public static final Esp2DModule ESP_2D = register(new Esp2DModule());
   public static final CustomFovModule CUSTOM_FOV = register(new CustomFovModule());
   public static final ToggleSprintModule TOGGLE_SPRINT = register(new ToggleSprintModule());
   public static final AutoRespawnModule AUTO_RESPAWN = register(new AutoRespawnModule());
   public static final ModuleListModule MODULE_LIST = register(new ModuleListModule());
   public static final WatermarkModule WATERMARK = register(new WatermarkModule());
   public static final ArmorHudModule ARMOR_HUD = register(new ArmorHudModule());
   public static final PotionHudModule POTION_HUD = register(new PotionHudModule());
   public static final SusChunkModule SUS_CHUNK = register(new SusChunkModule());
   public static final SoundChunkModule SOUND_CHUNK = register(new SoundChunkModule());
   public static final BlockEspModule BLOCK_ESP = register(new BlockEspModule());
   public static final WaypointsModule WAYPOINTS = register(new WaypointsModule());
   public static final ElytraRecastModule ELYTRA_RECAST = register(new ElytraRecastModule());
   public static final SpawnerNametagsModule SPAWNER_NAMETAGS = register(new SpawnerNametagsModule());
   public static final SpawnerNotiModule SPAWNER_NOTI = register(new SpawnerNotiModule());
   public static final XrayResearchModule XRAY_RESEARCH = register(new XrayResearchModule());
   public static final ActivityInspectorModule ACTIVITY_INSPECTOR = register(new ActivityInspectorModule());
   public static final BaseFinderResearchModule BASE_RESEARCH = register(new BaseFinderResearchModule());
   public static final BaseRadarModule BASE_RADAR = register(new BaseRadarModule());
   public static final HudModule HUD = register(new HudModule());
   public static final KeystrokesModule KEYSTROKES = register(new KeystrokesModule());
   public static final SoundsModule SOUNDS = register(new SoundsModule());
   private static final Map<Module, Boolean> KEY_WAS_DOWN = new HashMap<>();

   private ModuleManager() {
   }

   private static <M extends Module> M register(M module) {
      MODULES.add(module);
      return module;
   }

   public static List<Module> all() {
      return Collections.unmodifiableList(MODULES);
   }

   public static List<Module> byCategory(Module.Category category) {
      List<Module> list = new ArrayList<>();

      for (Module m : MODULES) {
         if (m.category() == category) {
            list.add(m);
         }
      }

      return list;
   }

   public static void startTick(Minecraft mc) {
      for (Module m : MODULES) {
         if (m.isEnabled()) {
            m.onStartTick(mc);
         }
      }
   }

   public static void frame(Minecraft mc) {
      for (Module m : MODULES) {
         if (m.isEnabled()) {
            m.onFrame(mc);
         }
      }
   }

   public static void tick(Minecraft mc) {
      for (Module m : MODULES) {
         int key = m.keybind();
         if (key >= 0) {
            boolean down = mc.screen == null && GLFW.glfwGetKey(mc.getWindow().handle(), key) == 1;
            if (down && !KEY_WAS_DOWN.getOrDefault(m, false)) {
               if (m == INSTANT_LAUNCH) {
                  if (m.isEnabled()) {
                     INSTANT_LAUNCH.launch(mc);
                  }
               } else {
                  m.toggle();
                  QuiqzyConfig.save();
               }
            }

            KEY_WAS_DOWN.put(m, down);
         }
      }

      for (Module mx : MODULES) {
         if (mx.isEnabled()) {
            mx.onTick(mc);
         }
      }
   }
}
