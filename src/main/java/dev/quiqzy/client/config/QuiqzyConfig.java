package dev.quiqzy.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.quiqzy.client.QuiqzyClient;
import dev.quiqzy.client.module.Module;
import dev.quiqzy.client.module.ModuleManager;
import dev.quiqzy.client.module.Setting;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;

public final class QuiqzyConfig {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("quiqzy.json");
   private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("quiqzy-configs");
   public static int guiX = Integer.MIN_VALUE;
   public static int guiY = Integer.MIN_VALUE;
   public static String guiMode = "html";
   public static boolean guiBlur = true;
   /** 3D-Kippeffekt: das GUI-Fenster neigt sich zur Maus. */
   public static boolean gui3d = true;

   private QuiqzyConfig() {
   }

   public static void load() {
      if (Files.exists(FILE)) {
         try (Reader reader = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
            JsonObject root = (JsonObject)GSON.fromJson(reader, JsonObject.class);
            if (root != null) {
               applyJson(root);
            }
         } catch (Exception var5) {
            QuiqzyClient.LOGGER.warn("Konnte quiqzy.json nicht laden", var5);
         }
      }
   }

   public static void save() {
      try {
         Files.createDirectories(FILE.getParent());

         try (Writer writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
            GSON.toJson(buildJson(), writer);
         }
      } catch (Exception var5) {
         QuiqzyClient.LOGGER.warn("Konnte quiqzy.json nicht speichern", var5);
      }
   }

   public static List<String> listNamed() {
      List<String> out = new ArrayList<>();
      if (Files.isDirectory(DIR)) {
         try (Stream<Path> st = Files.list(DIR)) {
            st.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
               String n = p.getFileName().toString();
               out.add(n.substring(0, n.length() - 5));
            });
         } catch (Exception var6) {
         }
      }

      out.sort(String.CASE_INSENSITIVE_ORDER);
      return out;
   }

   public static void saveNamed(String name) {
      String n = safe(name);
      if (!n.isEmpty()) {
         try {
            Files.createDirectories(DIR);

            try (Writer w = Files.newBufferedWriter(DIR.resolve(n + ".json"), StandardCharsets.UTF_8)) {
               GSON.toJson(buildJson(), w);
            }
         } catch (Exception var7) {
            QuiqzyClient.LOGGER.warn("Konnte Config '{}' nicht speichern", n, var7);
         }
      }
   }

   public static void loadNamed(String name) {
      String n = safe(name);
      Path f = DIR.resolve(n + ".json");
      if (Files.exists(f)) {
         try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            JsonObject root = (JsonObject)GSON.fromJson(r, JsonObject.class);
            if (root != null) {
               applyJson(root);
               save();
            }
         } catch (Exception var8) {
            QuiqzyClient.LOGGER.warn("Konnte Config '{}' nicht laden", n, var8);
         }
      }
   }

   public static void deleteNamed(String name) {
      try {
         Files.deleteIfExists(DIR.resolve(safe(name) + ".json"));
      } catch (Exception var2) {
      }
   }

   public static void resetAll() {
      for (Module module : ModuleManager.all()) {
         for (Setting<?> s : module.settings()) {
            s.reset();
         }

         module.setEnabled(false);
         module.setKeybind(-1);
      }

      save();
   }

   private static String safe(String name) {
      if (name == null) {
         return "";
      } else {
         String s = name.trim().replaceAll("[^A-Za-z0-9 _-]", "").trim();
         return s.length() > 32 ? s.substring(0, 32) : s;
      }
   }

   private static JsonObject buildJson() {
      JsonObject root = new JsonObject();
      JsonObject modules = new JsonObject();

      for (Module module : ModuleManager.all()) {
         JsonObject obj = new JsonObject();
         obj.addProperty("enabled", module.isEnabled());
         obj.addProperty("keybind", module.keybind());
         JsonObject settings = new JsonObject();

         for (Setting<?> s : module.settings()) {
            if (s instanceof Setting.Bool b) {
               settings.addProperty(s.id(), (Boolean)b.get());
            } else if (s instanceof Setting.Int i) {
               settings.addProperty(s.id(), (Number)i.get());
            } else if (s instanceof Setting.Choice c) {
               settings.addProperty(s.id(), (String)c.get());
            } else if (s instanceof Setting.Color col) {
               settings.addProperty(s.id(), col.hex());
            } else if (s instanceof Setting.Blocks bl) {
               JsonArray arr = new JsonArray();
               bl.get().forEach(arr::add);
               settings.add(s.id(), arr);
            } else if (s instanceof Setting.Strings st) {
               JsonArray arr = new JsonArray();
               st.get().forEach(arr::add);
               settings.add(s.id(), arr);
            } else if (s instanceof Setting.ColorMap cm) {
               JsonObject o = new JsonObject();
               cm.get().forEach((k, v) -> o.addProperty(k, String.format("#%06X", v)));
               settings.add(s.id(), o);
            } else if (s instanceof Setting.Items it) {
               JsonArray arr = new JsonArray();
               it.get().forEach(arr::add);
               settings.add(s.id(), arr);
            } else if (s instanceof Setting.Waypoints wp) {
               JsonArray arr = new JsonArray();

               for (Setting.Waypoints.Point pt : wp.get()) {
                  JsonObject o = new JsonObject();
                  o.addProperty("name", pt.name());
                  o.addProperty("x", pt.x());
                  o.addProperty("y", pt.y());
                  o.addProperty("z", pt.z());
                  o.addProperty("dim", pt.dim());
                  arr.add(o);
               }

               settings.add(s.id(), arr);
            }
         }

         obj.add("settings", settings);
         modules.add(module.id(), obj);
      }

      root.add("modules", modules);
      root.addProperty("guiMode", guiMode);
      root.addProperty("guiBlur", guiBlur);
      root.addProperty("gui3d", gui3d);
      return root;
   }

   private static void applyJson(JsonObject root) {
      if (root.has("guiMode")) {
         guiMode = root.get("guiMode").getAsString();
      }

      if (root.has("gui3d")) {
         gui3d = root.get("gui3d").getAsBoolean();
      }

      if (root.has("guiBlur")) {
         guiBlur = root.get("guiBlur").getAsBoolean();
      }

      JsonObject modules = root.getAsJsonObject("modules");
      if (modules != null) {
         for (Module module : ModuleManager.all()) {
            JsonObject obj = modules.getAsJsonObject(module.id());
            if (obj != null) {
               JsonObject settings = obj.getAsJsonObject("settings");
               if (settings != null) {
                  for (Setting<?> s : module.settings()) {
                     JsonElement el = settings.get(s.id());
                     if (el != null) {
                        if (s instanceof Setting.Bool b) {
                           b.set(el.getAsBoolean());
                        } else if (s instanceof Setting.Int i) {
                           i.set(el.getAsInt());
                        } else if (s instanceof Setting.Choice c) {
                           c.set(el.getAsString());
                        } else if (s instanceof Setting.Color col) {
                           col.setHex(el.getAsString());
                        } else if (s instanceof Setting.Blocks bl && el.isJsonArray()) {
                           List<String> ids = new ArrayList<>();
                           el.getAsJsonArray().forEach(ex -> ids.add(ex.getAsString()));
                           bl.set(ids);
                        } else if (s instanceof Setting.Strings st && el.isJsonArray()) {
                           List<String> list = new ArrayList<>();
                           el.getAsJsonArray().forEach(ex -> list.add(ex.getAsString()));
                           st.set(list);
                        } else if (s instanceof Setting.ColorMap cm && el.isJsonObject()) {
                           java.util.Map<String, Integer> map = new java.util.LinkedHashMap<>();
                           for (java.util.Map.Entry<String, JsonElement> en : el.getAsJsonObject().entrySet()) {
                              try {
                                 map.put(en.getKey(), Integer.parseInt(en.getValue().getAsString().replace("#", ""), 16) & 0xFFFFFF);
                              } catch (RuntimeException ignored) {
                              }
                           }
                           cm.set(map);
                        } else if (s instanceof Setting.Items it && el.isJsonArray()) {
                           List<String> ids = new ArrayList<>();
                           el.getAsJsonArray().forEach(ex -> ids.add(ex.getAsString()));
                           it.set(ids);
                        } else if (s instanceof Setting.Waypoints wp && el.isJsonArray()) {
                           List<Setting.Waypoints.Point> pts = new ArrayList<>();

                           for (JsonElement e : el.getAsJsonArray()) {
                              JsonObject o = e.getAsJsonObject();
                              pts.add(
                                 new Setting.Waypoints.Point(
                                    o.get("name").getAsString(),
                                    o.get("x").getAsInt(),
                                    o.get("y").getAsInt(),
                                    o.get("z").getAsInt(),
                                    o.get("dim").getAsString()
                                 )
                              );
                           }

                           wp.set(pts);
                        }
                     }
                  }
               }

               if (obj.has("keybind")) {
                  module.setKeybind(obj.get("keybind").getAsInt());
               }

               if (obj.has("enabled")) {
                  module.setEnabled(obj.get("enabled").getAsBoolean());
               }
            }
         }
      }
   }
}
