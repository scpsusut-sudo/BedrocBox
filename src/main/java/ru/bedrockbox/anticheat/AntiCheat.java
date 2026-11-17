package ru.bedrockbox.anticheat;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.BiPredicate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.World;
import ru.bedrockbox.BedrockBoxPlugin;
import ru.bedrockbox.util.Log;

public final class AntiCheat {
   private final BedrockBoxPlugin plugin;
   private boolean espEnabled = true;
   private boolean xrayEnabled = true;
   private double nearDistance = 4.0;
   private double maxDistance = 40.0;
   private final Map<UUID, Set<UUID>> hidden = new HashMap<>();
   private final Cloak cloak = new Cloak();
   private boolean hitCheck = true;
   private double maxReach = 4.8;
   private BiPredicate<UUID, UUID> allies = UUID::equals;
   private static final List<String> HIDDEN = Arrays.asList("emerald_ore", "diamond_ore", "mossy_cobblestone");
   private static final List<String> REPLACE = Arrays.asList(
      "stone", "cobblestone", "deepslate", "andesite", "diorite", "granite", "tuff", "calcite", "dirt", "coarse_dirt", "gravel", "moss_block"
   );

   public Cloak cloak() {
      return this.cloak;
   }

   public void setAllies(BiPredicate<UUID, UUID> biPredicate) {
      this.allies = biPredicate;
      this.cloak.setAllies(biPredicate);
   }

   public AntiCheat(BedrockBoxPlugin bedrockBoxPlugin) {
      this.plugin = bedrockBoxPlugin;
   }

   public void load(FileConfiguration fileConfiguration) {
      this.espEnabled = fileConfiguration.getBoolean("anticheat.anti-esp", true);
      this.xrayEnabled = fileConfiguration.getBoolean("anticheat.anti-xray", true);
      this.nearDistance = Math.max(0.0, fileConfiguration.getDouble("anticheat.anti-esp-distance", 4.0));
      this.maxDistance = Math.max(8.0, fileConfiguration.getDouble("anticheat.anti-tracer-distance", 40.0));
      this.hitCheck = fileConfiguration.getBoolean("anticheat.hit-check", true);
      this.maxReach = Math.max(3.5, fileConfiguration.getDouble("anticheat.max-reach", 4.8));
      this.cloak.load(fileConfiguration);
   }

   public boolean xrayEnabled() {
      return this.xrayEnabled;
   }

   public void update(Collection<Player> players, Collection<Entity> entitys) {
      if (!this.espEnabled) {
         this.revealAll(players);
      } else {
         for (Player player : players) {
            Set list = this.hidden.computeIfAbsent(player.getUniqueId(), x -> new HashSet<>());

            for (Entity entity : entitys) {
               if (!entity.getUniqueId().equals(player.getUniqueId()) && entity.getWorld().getName().equals(player.getWorld().getName())) {
                  if (entity instanceof Player player2) {
                     noGlow(player2);
                  }

                  boolean flag2 = this.allies.test(player.getUniqueId(), entity.getUniqueId()) || this.canSee(player, entity);
                  boolean flag = list.contains(entity.getUniqueId());
                  if (flag2 && flag) {
                     this.show(player, entity);
                     list.remove(entity.getUniqueId());
                  } else if (!flag2 && !flag) {
                     this.hide(player, entity);
                     list.add(entity.getUniqueId());
                  } else if (!flag2) {
                     this.hidePassengers(player, entity);
                  }
               }
            }
         }
      }
   }

   private boolean canSee(Player player, Entity entity) {
      Location location = player.getEyeLocation();
      Location location2 = entity.getLocation();
      double d = location.distance(location2);
      if (d <= this.nearDistance) {
         return true;
      } else if (d > this.maxDistance) {
         return false;
      } else {
         double d2 = entity.getHeight();

         for (double d3 : new double[]{d2 * 0.9, d2 * 0.5, 0.1}) {
            if (clearLine(location, location2.clone().add(0.0, d3, 0.0))) {
               return true;
            }
         }

         return false;
      }
   }

   private static boolean clearLine(Location location, Location location2) {
      Vector vector = location2.toVector().subtract(location.toVector());
      double d = vector.length();
      if (d < 0.01) {
         return true;
      } else {
         World world = location.getWorld();
         RayTraceResult rayTraceResult = world.rayTraceBlocks(location, vector.normalize(), d, FluidCollisionMode.NEVER, true);
         return rayTraceResult == null || rayTraceResult.getHitBlock() == null;
      }
   }

   private void hide(Player player, Entity entity) {
      if (entity instanceof Player player2) {
         player.hidePlayer(this.plugin, player2);
      } else {
         player.hideEntity(this.plugin, entity);
      }

      this.hidePassengers(player, entity);
   }

   private void show(Player player, Entity entity) {
      if (entity instanceof Player player2) {
         player.showPlayer(this.plugin, player2);
      } else {
         player.showEntity(this.plugin, entity);
      }

      for (Entity entity2 : entity.getPassengers()) {
         if (!player.canSee(entity2)) {
            player.showEntity(this.plugin, entity2);
         }
      }
   }

   private void hidePassengers(Player player, Entity entity) {
      for (Entity entity2 : entity.getPassengers()) {
         if (player.canSee(entity2)) {
            player.hideEntity(this.plugin, entity2);
         }
      }
   }

   private static void noGlow(Player player) {
      if (player.isGlowing()) {
         player.setGlowing(false);
      }

      try {
         if (player.hasPotionEffect(PotionEffectType.GLOWING)) {
            player.removePotionEffect(PotionEffectType.GLOWING);
         }
      } catch (Throwable t) {
         Log.ignored(t);
      }
   }

   public boolean legitHit(Player player, Entity entity) {
      if (!this.hitCheck) {
         return true;
      } else {
         Location location = player.getEyeLocation();
         Location location2 = entity.getLocation();
         double d = entity.getHeight();
         double d2 = Math.max(Math.abs(location.getX() - location2.getX()) - 0.3, 0.0);
         double d3 = Math.max(Math.abs(location.getZ() - location2.getZ()) - 0.3, 0.0);
         double d4 = Math.max(Math.max(location2.getY() - location.getY(), 0.0), location.getY() - (location2.getY() + d));
         if (Math.sqrt(d2 * d2 + d4 * d4 + d3 * d3) > this.maxReach) {
            return false;
         } else if (location.distance(location2) <= 1.5) {
            return true;
         } else {
            for (double d5 : new double[]{d * 0.9, d * 0.5, 0.1}) {
               if (clearLine(location, location2.clone().add(0.0, d5, 0.0))) {
                  return true;
               }
            }

            return false;
         }
      }
   }

   public void revealAll(Collection<Player> players) {
      for (Player player : players) {
         this.reveal(player);
      }
   }

   public void reveal(Player player) {
      Set list = this.hidden.remove(player.getUniqueId());
      if (list != null) {
         for (UUID uUID : (Iterable<UUID>) list) {
            Entity entity = Bukkit.getEntity(uUID);
            if (entity != null) {
               this.show(player, entity);
            }
         }
      }
   }

   public void forget(Player player) {
      for (Entry entry : this.hidden.entrySet()) {
         if (((Set)entry.getValue()).remove(player.getUniqueId())) {
            Player player2 = Bukkit.getPlayer((UUID)entry.getKey());
            if (player2 != null) {
               player2.showPlayer(this.plugin, player);
            }
         }
      }
   }

   public void revealEveryone() {
      for (UUID uUID : new ArrayList<>(this.hidden.keySet())) {
         Player player = Bukkit.getPlayer(uUID);
         if (player != null) {
            this.reveal(player);
         } else {
            this.hidden.remove(uUID);
         }
      }
   }

   public boolean ensureXray(World world2, int n) {
      if (this.xrayEnabled && world2 != null) {
         File file = new File(world2.getWorldFolder(), "paper-world.yml");
         YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration(file);
         String str = "anticheat.anti-xray.";
         int n2 = (int)Math.ceil((double)(n + 1) / 16.0) * 16;
         n2 = Math.max(64, Math.min(world2.getMaxHeight(), n2));
         boolean flag = yamlConfiguration.getBoolean(str + "enabled", false)
            && yamlConfiguration.getInt(str + "engine-mode", 0) == 2
            && yamlConfiguration.getInt(str + "max-block-height", 0) >= n2
            && yamlConfiguration.getStringList(str + "hidden-blocks").containsAll(HIDDEN)
            && yamlConfiguration.getStringList(str + "replacement-blocks").containsAll(REPLACE);
         if (flag) {
            return false;
         } else {
            yamlConfiguration.set(str + "enabled", Boolean.valueOf(true));
            yamlConfiguration.set(str + "engine-mode", Integer.valueOf(2));
            yamlConfiguration.set(str + "max-block-height", Integer.valueOf(n2));
            yamlConfiguration.set(str + "update-radius", Integer.valueOf(2));
            yamlConfiguration.set(str + "lava-obscures", Boolean.valueOf(false));
            yamlConfiguration.set(str + "use-permission", Boolean.valueOf(false));
            yamlConfiguration.set(str + "hidden-blocks", HIDDEN);
            yamlConfiguration.set(str + "replacement-blocks", REPLACE);

            try {
               yamlConfiguration.save(file);
               this.plugin.getLogger().info("Анти-X-ray включён для мира " + world2.getName() + " (до Y=" + n2 + "). Перезапусти сервер.");
               return true;
            } catch (Exception e) {
               this.plugin.getLogger().warning("Не удалось записать " + file + ": " + e.getMessage());
               return false;
            }
         }
      } else {
         return false;
      }
   }
}
