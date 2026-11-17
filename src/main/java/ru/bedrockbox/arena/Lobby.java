package ru.bedrockbox.arena;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import ru.bedrockbox.BedrockBoxPlugin;
import ru.bedrockbox.util.Log;

public final class Lobby {
   private static final int WORLD_Y = 100;
   private static final int R = 12;
   private static final int GAP = 8;
   private final BedrockBoxPlugin plugin;
   private boolean enabled = true;
   private String mode = "arena";
   private Location spawn;
   private Location platform;

   public Lobby(BedrockBoxPlugin bedrockBoxPlugin) {
      this.plugin = bedrockBoxPlugin;
   }

   public boolean enabled() {
      return this.enabled;
   }

   public void load(FileConfiguration fileConfiguration) {
      this.enabled = fileConfiguration.getBoolean("lobby.enabled", true);
      this.mode = fileConfiguration.getString("lobby.mode", "arena").toLowerCase();
      if (fileConfiguration.contains("lobby.x")) {
         this.mode = "custom";
      }

      this.spawn = null;
      if (this.enabled) {
         if (this.mode.equals("custom")) {
            World world2 = Bukkit.getWorld(fileConfiguration.getString("lobby.world", ""));
            if (world2 != null) {
               this.spawn = new Location(
                  world2, fileConfiguration.getDouble("lobby.x"), fileConfiguration.getDouble("lobby.y"), fileConfiguration.getDouble("lobby.z"), (float)fileConfiguration.getDouble("lobby.yaw"), 0.0F
               );
               this.platform = null;
               return;
            }

            this.plugin.getLogger().warning("Мир своего лобби не найден - ставлю лобби у коробки.");
            this.mode = "arena";
         }

         if (!this.mode.equals("arena") || !this.follow(this.plugin.game() == null ? null : this.plugin.game().arena())) {
            World world3 = Bukkit.getWorld("bb_lobby");
            if (world3 == null) {
               world3 = new WorldCreator("bb_lobby").generator(new Lobby.VoidGenerator()).createWorld();
            }

            quiet(world3);
            Location location = new Location(world3, 0.5, 100.0, 0.5);
            if (world3.getBlockAt(0, 99, 0).getType() == Material.AIR) {
               buildPlatform(location);
            }

            this.platform = location;
            this.spawn = location.clone();
         }
      }
   }

   public boolean follow(Arena arena2) {
      if (this.enabled && this.mode.equals("arena") && arena2 != null) {
         World world2 = arena2.world;
         int n = arena2.maxX + 8 + 12 + 1;
         int n2 = (arena2.minZ + arena2.maxZ) / 2;
         int n3 = arena2.minY + Math.min((arena2.maxY - arena2.minY) / 3, 40) + 2;
         n3 = Math.max(world2.getMinHeight() + 2, Math.min(world2.getMaxHeight() - 4, n3));
         Location location = new Location(world2, (double)n + 0.5, (double)n3, (double)n2 + 0.5);
         if (this.platform != null && sameBlock(this.platform, location)) {
            if (this.spawn == null) {
               this.spawn = location.clone();
               this.spawn.setYaw(90.0F);
            }

            return true;
         } else {
            Location location2 = this.platform;
            if (location2 != null && !location2.getWorld().getName().equals("bb_lobby")) {
               clearPlatform(location2);
            }

            buildPlatform(location);
            this.platform = location;
            Location location3 = location.clone();
            location3.setYaw(90.0F);
            this.spawn = location3;
            quiet(world2);
            if (location2 != null) {
               for (Player player : Bukkit.getOnlinePlayers()) {
                  if (this.near(location2, player.getLocation())) {
                     player.teleport(this.spawn);
                  }
               }
            }

            return true;
         }
      } else {
         return false;
      }
   }

   private static boolean sameBlock(Location location, Location location2) {
      return location.getWorld().getName().equals(location2.getWorld().getName())
         && location.getBlockX() == location2.getBlockX()
         && location.getBlockY() == location2.getBlockY()
         && location.getBlockZ() == location2.getBlockZ();
   }

   private static void quiet(World world) {
      try {
         world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
         world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
         world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
         world.setTime(6000L);
      } catch (Throwable t) {
         Log.ignored(t);
      }
   }

   private static void buildPlatform(Location location) {
      World world = location.getWorld();
      int n = location.getBlockX();
      int n2 = location.getBlockY();
      int n3 = location.getBlockZ();

      for (int n4 = -12; n4 <= 12; n4++) {
         for (int n5 = -12; n5 <= 12; n5++) {
            boolean flag = Math.abs(n4) == 12 || Math.abs(n5) == 12;
            boolean flag2 = Math.max(Math.abs(n4), Math.abs(n5)) % 4 == 0;
            world.getBlockAt(n + n4, n2 - 1, n3 + n5).setType(flag2 ? Material.POLISHED_DEEPSLATE : Material.SMOOTH_STONE, false);
            if (flag) {
               world.getBlockAt(n + n4, n2, n3 + n5).setType(Material.GLASS, false);
               world.getBlockAt(n + n4, n2 + 1, n3 + n5).setType(Material.GLASS, false);
            }
         }
      }

      int[][] arr2 = new int[][]{{11, 11}, {-11, 11}, {11, -11}, {-11, -11}};

      for (int[] arr : arr2) {
         world.getBlockAt(n + arr[0], n2, n3 + arr[1]).setType(Material.LANTERN, false);
      }

      world.getBlockAt(n, n2 - 1, n3).setType(Material.BEDROCK, false);
   }

   private static void clearPlatform(Location location) {
      World world = location.getWorld();
      int n = location.getBlockX();
      int n2 = location.getBlockY();
      int n3 = location.getBlockZ();

      for (int n4 = -12; n4 <= 12; n4++) {
         for (int n5 = -12; n5 <= 12; n5++) {
            for (int n6 = -1; n6 <= 1; n6++) {
               world.getBlockAt(n + n4, n2 + n6, n3 + n5).setType(Material.AIR, false);
            }
         }
      }
   }

   private boolean near(Location tmp, Location location) {
      if (tmp != null && location != null && location.getWorld() != null) {
         return !location.getWorld().getName().equals(tmp.getWorld().getName())
            ? false
            : Math.abs(location.getX() - tmp.getX()) <= 18.0
               && Math.abs(location.getZ() - tmp.getZ()) <= 18.0
               && location.getY() > tmp.getY() - 40.0
               && location.getY() < tmp.getY() + 30.0;
      } else {
         return false;
      }
   }

   public boolean isLobby(Location location) {
      if (!this.enabled || this.spawn == null || location == null) {
         return false;
      } else if (this.mode.equals("custom")) {
         return this.near(this.spawn, location);
      } else {
         return this.platform != null && this.platform.getWorld().getName().equals("bb_lobby")
            ? location.getWorld() != null && location.getWorld().getName().equals("bb_lobby")
            : this.near(this.platform, location);
      }
   }

   public Location spawn() {
      return this.spawn == null ? null : this.spawn.clone();
   }

   public void send(Player player) {
      if (this.enabled && this.spawn != null) {
         if (player.getGameMode() != GameMode.CREATIVE) {
            player.setGameMode(GameMode.ADVENTURE);
         }

         player.setHealth(20.0);
         player.setFoodLevel(20);
         player.setFireTicks(0);
         player.teleport(this.spawn);
      }
   }

   public static World arenaWorld() {
      World world = Bukkit.getWorld("bb_arena");
      if (world == null) {
         world = new WorldCreator("bb_arena").generator(new Lobby.VoidGenerator()).createWorld();
      }

      quiet(world);
      return world;
   }

   public boolean conflicts(Arena arena2) {
      if (!this.enabled || this.spawn == null || arena2 == null) {
         return false;
      } else if (this.mode.equals("arena")) {
         return false;
      } else {
         return this.platform != null && this.platform.getWorld().getName().equals("bb_lobby")
            ? arena2.world.getName().equals("bb_lobby")
            : arena2.world.getName().equals(this.spawn.getWorld().getName()) && arena2.overlaps(this.spawn, 32);
      }
   }

   public void tick() {
      if (this.enabled && this.spawn != null) {
         if (this.platform != null
            && this.platform.getWorld().getBlockAt(this.platform.getBlockX(), this.platform.getBlockY() - 1, this.platform.getBlockZ()).getType()
               == Material.AIR) {
            buildPlatform(this.platform);
            this.plugin.getLogger().info("Площадка лобби была снесена - построил заново.");
         }

         Location location = this.platform != null ? this.platform : this.spawn;

         for (Player player : Bukkit.getOnlinePlayers()) {
            Location location2 = player.getLocation();
            if (location2.getWorld().getName().equals(location.getWorld().getName())) {
               boolean flag = Math.abs(location2.getX() - location.getX()) <= 18.0 && Math.abs(location2.getZ() - location.getZ()) <= 18.0;
               if (flag && location2.getY() < location.getY() - 30.0 && !this.plugin.game().inGame(player) && !this.plugin.cinematic().isWatching(player)) {
                  player.teleport(this.spawn);
               }
            }
         }
      }
   }

   public void setHere(Player player, FileConfiguration fileConfiguration) {
      Location location = player.getLocation();
      fileConfiguration.set("lobby.enabled", Boolean.valueOf(true));
      fileConfiguration.set("lobby.mode", "custom");
      fileConfiguration.set("lobby.world", location.getWorld().getName());
      fileConfiguration.set("lobby.x", Double.valueOf(location.getX()));
      fileConfiguration.set("lobby.y", Double.valueOf(location.getY()));
      fileConfiguration.set("lobby.z", Double.valueOf(location.getZ()));
      fileConfiguration.set("lobby.yaw", Double.valueOf((double)location.getYaw()));
   }

   public static void resetToArena(FileConfiguration fileConfiguration) {
      fileConfiguration.set("lobby.mode", "arena");
      fileConfiguration.set("lobby.x", null);
      fileConfiguration.set("lobby.y", null);
      fileConfiguration.set("lobby.z", null);
      fileConfiguration.set("lobby.yaw", null);
   }

   public static final class VoidGenerator extends ChunkGenerator {
   }
}
