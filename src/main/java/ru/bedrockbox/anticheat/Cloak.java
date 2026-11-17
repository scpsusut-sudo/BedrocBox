package ru.bedrockbox.anticheat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.function.BiPredicate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.bukkit.World;

public final class Cloak {
   private final List<Cloak.Zone> zones = new ArrayList<>();
   private final Map<UUID, Set<Integer>> cloaked = new HashMap<>();
   private boolean enabled = true;
   private double revealDistance = 10.0;
   private BlockData fake;
   private BiPredicate<UUID, UUID> allies = UUID::equals;
   private World dugWorld;
   private final Map<Long, Map<Long, UUID>> dug = new HashMap<>();
   private final Map<UUID, Set<Long>> revealedDug = new HashMap<>();
   private static final UUID NOBODY = new UUID(0L, 0L);

   public void setAllies(BiPredicate<UUID, UUID> biPredicate) {
      this.allies = biPredicate;
   }

   private static long ck(int n, int n2) {
      return (long)n << 32 ^ (long)n2 & 4294967295L;
   }

   private static long bk(int n, int n2, int n3) {
      return (long)(n & 67108863) << 38 | (long)(n3 & 67108863) << 12 | (long)(n2 & 4095);
   }

   private static int bx(long l) {
      return (int)(l >> 38);
   }

   private static int by(long l) {
      return (int)(l << 52 >> 52);
   }

   private static int bz(long l) {
      return (int)(l << 26 >> 38);
   }

   public void load(FileConfiguration fileConfiguration) {
      this.enabled = fileConfiguration.getBoolean("anticheat.anti-freecam", true);
      this.revealDistance = Math.max(3.0, fileConfiguration.getDouble("anticheat.anti-freecam-distance", 10.0));
   }

   public void clear() {
      this.zones.clear();
      this.cloaked.clear();
      this.dug.clear();
      this.revealedDug.clear();
      this.dugWorld = null;
   }

   public double revealDistance() {
      return this.revealDistance;
   }

   public void onDug(Block block, UUID uUID, Collection<Player> players, Plugin plugin) {
      if (this.enabled) {
         this.dugWorld = block.getWorld();
         int n = block.getX();
         int n2 = block.getY();
         int n3 = block.getZ();
         long l = bk(n, n2, n3);
         this.dug.computeIfAbsent(ck(n >> 4, n3 >> 4), x -> new HashMap<>()).put(l, uUID == null ? NOBODY : uUID);
         ArrayList list = new ArrayList(players);
         Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (this.fake == null) {
               this.fake = Material.STONE.createBlockData();
            }

            Location location = new Location(this.dugWorld, (double)n, (double)n2, (double)n3);

            for (Player player : (Iterable<Player>) list) {
               if (player.isOnline() && !this.allies.test(player.getUniqueId(), uUID) && player.getWorld().getName().equals(this.dugWorld.getName())) {
                  Set list2 = this.revealedDug.computeIfAbsent(player.getUniqueId(), x -> new HashSet<>());
                  if (dist(player.getLocation(), n, n2, n3) <= this.revealDistance) {
                     list2.add(l);
                  } else {
                     list2.remove(l);
                     player.sendBlockChange(location, this.fake);
                  }
               }
            }
         }, 2L);
      }
   }

   private static double dist(Location location, int n, int n2, int n3) {
      double d = location.getX() - ((double)n + 0.5);
      double d2 = location.getY() - ((double)n2 + 0.5);
      double d3 = location.getZ() - ((double)n3 + 0.5);
      return Math.sqrt(d * d + d2 * d2 + d3 * d3);
   }

   private void updateDug(Player player) {
      if (this.dugWorld != null && !this.dug.isEmpty() && player.getWorld().getName().equals(this.dugWorld.getName())) {
         if (this.fake == null) {
            this.fake = Material.STONE.createBlockData();
         }

         Set list = this.revealedDug.computeIfAbsent(player.getUniqueId(), x -> new HashSet<>());
         Location location = player.getLocation();
         int n = (int)Math.ceil(this.revealDistance / 16.0) + 1;
         int n2 = location.getBlockX() >> 4;
         int n3 = location.getBlockZ() >> 4;

         for (int n4 = n2 - n; n4 <= n2 + n; n4++) {
            for (int n5 = n3 - n; n5 <= n3 + n; n5++) {
               Map map = this.dug.get(ck(n4, n5));
               if (map != null) {
                  for (Entry entry : (Iterable<Entry>) map.entrySet()) {
                     long l = (Long)entry.getKey();
                     if (!this.allies.test(player.getUniqueId(), (UUID)entry.getValue()) && !list.contains(l)) {
                        int n6 = bx(l);
                        int n7 = by(l);
                        int n8 = bz(l);
                        if (dist(location, n6, n7, n8) <= this.revealDistance) {
                           player.sendBlockChange(
                              new Location(this.dugWorld, (double)n6, (double)n7, (double)n8),
                              this.dugWorld.getBlockAt(n6, n7, n8).getBlockData()
                           );
                           list.add(l);
                        }
                     }
                  }
               }
            }
         }

         Iterator it = list.iterator();

         while (it.hasNext()) {
            long l2 = (Long)it.next();
            int n9 = bx(l2);
            int n10 = by(l2);
            int n11 = bz(l2);
            if (dist(location, n9, n10, n11) > this.revealDistance) {
               player.sendBlockChange(new Location(this.dugWorld, (double)n9, (double)n10, (double)n11), this.fake);
               it.remove();
            }
         }
      }
   }

   public void add(UUID uUID, World world, int n, int n2, int n3, int n4, int n5, int n6) {
      this.zones.add(new Cloak.Zone(this.zones.size(), uUID, world, n, n2, n3, n4, n5, n6));
   }

   public void update(Collection<Player> players) {
      if (this.enabled) {
         for (Player player : players) {
            this.updateDug(player);
         }

         if (!this.zones.isEmpty()) {
            for (Player player2 : players) {
               Set list = this.cloaked.computeIfAbsent(player2.getUniqueId(), x -> new HashSet<>());
               Location location = player2.getLocation();

               for (Cloak.Zone zone : this.zones) {
                  if (!this.allies.test(player2.getUniqueId(), zone.owner()) && zone.world().getName().equals(player2.getWorld().getName())) {
                     boolean flag = zone.distTo(location) <= this.revealDistance;
                     if (flag && list.remove(zone.id())) {
                        this.send(player2, zone, false);
                     } else if (!flag && list.add(zone.id())) {
                        this.send(player2, zone, true);
                     }
                  }
               }
            }
         }
      }
   }

   public void onChunkLoad(Player player, int n, int n2) {
      if (this.enabled) {
         Set list = this.cloaked.get(player.getUniqueId());
         if (list != null) {
            for (Cloak.Zone zone : this.zones) {
               if (list.contains(zone.id()) && zone.inChunk(n, n2)) {
                  this.send(player, zone, true);
               }
            }
         }

         Map map = this.dug.get(ck(n, n2));
         if (map != null && this.dugWorld != null && player.getWorld().getName().equals(this.dugWorld.getName())) {
            if (this.fake == null) {
               this.fake = Material.STONE.createBlockData();
            }

            Set list2 = this.revealedDug.getOrDefault(player.getUniqueId(), Collections.emptySet());

            for (Entry entry : (Iterable<Entry>) map.entrySet()) {
               long l = (Long)entry.getKey();
               if (!this.allies.test(player.getUniqueId(), (UUID)entry.getValue()) && !list2.contains(l)) {
                  player.sendBlockChange(new Location(this.dugWorld, (double)bx(l), (double)by(l), (double)bz(l)), this.fake);
               }
            }
         }
      }
   }

   public void reveal(Player player) {
      this.revealedDug.remove(player.getUniqueId());
      if (this.dugWorld != null && player.getWorld().getName().equals(this.dugWorld.getName())) {
         for (Map map : this.dug.values()) {
            for (long l : (Iterable<Long>) map.keySet()) {
               int n = bx(l);
               int n2 = by(l);
               int n3 = bz(l);
               player.sendBlockChange(
                  new Location(this.dugWorld, (double)n, (double)n2, (double)n3), this.dugWorld.getBlockAt(n, n2, n3).getBlockData()
               );
            }
         }
      }

      Set list = this.cloaked.remove(player.getUniqueId());
      if (list != null) {
         for (Cloak.Zone zone : this.zones) {
            if (list.contains(zone.id()) && zone.world().getName().equals(player.getWorld().getName())) {
               this.send(player, zone, false);
            }
         }
      }
   }

   private void send(Player player, Cloak.Zone zone, boolean flag) {
      if (this.fake == null) {
         this.fake = Material.STONE.createBlockData();
      }

      for (int n = zone.x0(); n <= zone.x1(); n++) {
         for (int n2 = zone.y0(); n2 <= zone.y1(); n2++) {
            for (int n3 = zone.z0(); n3 <= zone.z1(); n3++) {
               Location location = new Location(zone.world(), (double)n, (double)n2, (double)n3);
               player.sendBlockChange(location, flag ? this.fake : zone.world().getBlockAt(n, n2, n3).getBlockData());
            }
         }
      }
   }

   private static record Zone(int id, UUID owner, World world, int x0, int y0, int z0, int x1, int y1, int z1) {
      double distTo(Location tmp) {
         double tmp2 = Math.max(Math.max((double)this.x0 - tmp.getX(), 0.0), tmp.getX() - (double)(this.x1 + 1));
         double d = Math.max(Math.max((double)this.y0 - tmp.getY(), 0.0), tmp.getY() - (double)(this.y1 + 1));
         double d2 = Math.max(Math.max((double)this.z0 - tmp.getZ(), 0.0), tmp.getZ() - (double)(this.z1 + 1));
         return Math.sqrt(tmp2 * tmp2 + d * d + d2 * d2);
      }

      boolean inChunk(int tmp, int tmp2) {
         return this.x0 >> 4 <= tmp && tmp <= this.x1 >> 4 && this.z0 >> 4 <= tmp2 && tmp2 <= this.z1 >> 4;
      }
   }
}
