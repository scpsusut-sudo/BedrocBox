package ru.bedrockbox;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

public final class TallWorld {
   public static final String KEY = "bedrockbox:arena";
   public static final int MIN_Y = -1024;
   public static final int HEIGHT = 2048;
   private static final String PACK = "bedrockbox_tall";
   private final BedrockBoxPlugin plugin;

   public TallWorld(BedrockBoxPlugin bedrockBoxPlugin) {
      this.plugin = bedrockBoxPlugin;
   }

   public World world() {
      NamespacedKey namespacedKey = NamespacedKey.fromString("bedrockbox:arena");
      World world2 = namespacedKey == null ? null : Bukkit.getWorld(namespacedKey);
      if (world2 != null) {
         prepare(world2);
      }

      return world2;
   }

   private File packDir() {
      File file = Bukkit.getWorlds().get(0).getWorldFolder();
      return new File(new File(file, "datapacks"), "bedrockbox_tall");
   }

   public boolean installed() {
      return new File(this.packDir(), "pack.mcmeta").exists();
   }

   public void install() throws IOException {
      File file = this.packDir();
      write(
         new File(file, "pack.mcmeta"),
         "{\n  \"pack\": {\n    \"pack_format\": 48,\n    \"supported_formats\": { \"min_inclusive\": 48, \"max_inclusive\": 99 },\n    \"description\": \"BedrockBox: high arena world (2048 blocks)\"\n  }\n}\n"
      );
      write(
         new File(file, "data/bedrockbox/dimension_type/tall.json"),
         "{\n  \"ultrawarm\": false,\n  \"natural\": true,\n  \"piglin_safe\": false,\n  \"respawn_anchor_works\": false,\n  \"bed_works\": true,\n  \"has_raids\": false,\n  \"has_skylight\": true,\n  \"has_ceiling\": false,\n  \"coordinate_scale\": 1.0,\n  \"ambient_light\": 0.0,\n  \"fixed_time\": 6000,\n  \"logical_height\": %d,\n  \"effects\": \"minecraft:overworld\",\n  \"infiniburn\": \"#minecraft:infiniburn_overworld\",\n  \"min_y\": %d,\n  \"height\": %d,\n  \"monster_spawn_light_level\": 0,\n  \"monster_spawn_block_light_limit\": 0\n}\n"
            .formatted(2048, -1024, 2048)
      );
      write(
         new File(file, "data/bedrockbox/dimension/arena.json"),
         "{\n  \"type\": \"bedrockbox:tall\",\n  \"generator\": {\n    \"type\": \"minecraft:flat\",\n    \"settings\": {\n      \"biome\": \"minecraft:the_void\",\n      \"layers\": [],\n      \"features\": false,\n      \"lakes\": false\n    }\n  }\n}\n"
      );
   }

   private static void write(File file, String str) throws IOException {
      file.getParentFile().mkdirs();
      Files.writeString(file.toPath(), str, StandardCharsets.UTF_8);
   }

   public static void prepare(World world) {
      try {
         world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
         world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
         world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
      } catch (Throwable t) {
      }
   }

   public static Location platform(World world, int n, int n2) {
      int n3 = world.getMinHeight() + 2;

      for (int n4 = -2; n4 <= 2; n4++) {
         for (int n5 = -2; n5 <= 2; n5++) {
            world.getBlockAt(n + n4, n3 - 1, n2 + n5).setType(Material.GLASS, false);
         }
      }

      return new Location(world, (double)n + 0.5, (double)n3, (double)n2 + 0.5);
   }
}
