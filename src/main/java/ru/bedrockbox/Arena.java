package ru.bedrockbox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntConsumer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class Arena {
   public final World world;
   public final int minX;
   public final int minY;
   public final int minZ;
   public final int maxX;
   public final int maxY;
   public final int maxZ;
   private final Random random = new Random();
   private static final int ROOM_INSET = 4;
   private static final int MIN_ROOM_DIST = 12;
   private final Random placeRandom = new Random();
   private double spawnRandomness = 0.15;
   private int maxRoomsCache = -1;
   private BukkitRunnable buildTask;
   private volatile int buildPercent = 100;
   private static final long BUDGET_NANOS = 12000000L;

   public Arena(World world, int n, int n2, int n3, int n4, int n5) {
      this(world, n, n2, n3, n4, n4, n5);
   }

   public Arena(World world2, int n, int n2, int n3, int n4, int n5, int n6) {
      this.world = world2;
      this.minX = n - n4 / 2 - 1;
      this.maxX = this.minX + n4 + 1;
      this.minZ = n3 - n5 / 2 - 1;
      this.maxZ = this.minZ + n5 + 1;
      this.minY = n2;
      this.maxY = n2 + n6 + 1;
   }

   public boolean isInside(Block block) {
      if (!block.getWorld().getName().equals(this.world.getName())) {
         return false;
      } else {
         int n = block.getX();
         int n2 = block.getY();
         int n3 = block.getZ();
         return n > this.minX && n < this.maxX && n2 > this.minY && n2 < this.maxY && n3 > this.minZ && n3 < this.maxZ;
      }
   }

   public boolean isInBox(Block block) {
      if (!block.getWorld().getName().equals(this.world.getName())) {
         return false;
      } else {
         int n = block.getX();
         int n2 = block.getY();
         int n3 = block.getZ();
         return n >= this.minX && n <= this.maxX && n2 >= this.minY && n2 <= this.maxY && n3 >= this.minZ && n3 <= this.maxZ;
      }
   }

   public boolean isNear(Location location, int n) {
      if (location.getWorld() != null && location.getWorld().getName().equals(this.world.getName())) {
         int n2 = location.getBlockX();
         int n3 = location.getBlockY();
         int n4 = location.getBlockZ();
         return n2 >= this.minX - n
            && n2 <= this.maxX + n
            && n3 >= this.minY - n
            && n3 <= this.maxY + n
            && n4 >= this.minZ - n
            && n4 <= this.maxZ + n;
      } else {
         return false;
      }
   }

   public Location center() {
      return new Location(
         this.world, (double)(this.minX + this.maxX) / 2.0 + 0.5, (double)(this.minY + this.maxY) / 2.0, (double)(this.minZ + this.maxZ) / 2.0 + 0.5
      );
   }

   private List<int[]> candidates(int n) {
      int n2 = this.minX + 4;
      int n3 = this.maxX - 4;
      int n4 = this.minZ + 4;
      int n5 = this.maxZ - 4;
      int n6 = this.minY + 2;
      int n7 = this.maxY - 5;
      ArrayList list = new ArrayList();
      int n8 = Math.max(2, Math.min(n, (n3 - n2) / 6 + 1));
      int n9 = Math.max(2, Math.min(n, (n5 - n4) / 6 + 1));
      int n10 = Math.max(2, Math.min(n, (n7 - n6) / 6 + 1));

      for (int n11 : axis(n2, n3, n8)) {
         for (int n12 : axis(n6, n7, n10)) {
            for (int n13 : axis(n4, n5, n9)) {
               list.add(new int[]{n11, n12, n13});
            }
         }
      }

      return list;
   }

   private static List<Integer> axis(int n, int n2, int n3) {
      ArrayList list = new ArrayList();
      if (n2 < n) {
         list.add((n + n2) / 2);
         return list;
      } else {
         int n4 = n;

         while (n4 < n2) {
            list.add(n4);
            n4 += n3;
         }

         list.add(n2);
         return list;
      }
   }

   private static double dist(int[] arr, int[] arr2) {
      double d = (double)(arr[0] - arr2[0]);
      double d2 = (double)(arr[1] - arr2[1]);
      double d3 = (double)(arr[2] - arr2[2]);
      return Math.sqrt(d * d + d2 * d2 + d3 * d3);
   }

   public void setSpawnRandomness(double d) {
      this.spawnRandomness = Math.max(0.0, Math.min(0.8, d));
   }

   public List<int[]> rooms(int n) {
      return this.rooms(n, 24, 4, this.spawnRandomness);
   }

   private List<int[]> rooms(int n, int n2, int n3) {
      return this.rooms(n, n2, n3, 0.0);
   }

   private List<int[]> rooms(int n, int n2, int n3, double d) {
      int n4 = Math.max(Math.max(this.maxX - this.minX, this.maxZ - this.minZ), this.maxY - this.minY);
      int n5 = Math.max(2, n4 / n2);
      List<int[]> points = this.candidates(n5);
      ArrayList<int[]> points2 = new ArrayList<>();
      if (n <= 0) {
         return points2;
      } else {
         int n6 = this.minX + 4;
         int n7 = this.maxX - 4;
         int n8 = this.minZ + 4;
         int n9 = this.maxZ - 4;
         int n10 = this.minY + 2;
         int n11 = this.maxY - 5;
         if (d > 0.0) {
            int n20 = this.minY + 2;
            int n21 = this.maxY - 5;
            int n22 = Math.max(0, n21 - n20);
            int n23 = Math.max(1, n22 / 6 + 1);
            int n15 = Math.min(n23, Math.max(n, 3));
            int[] arr2 = new int[n15];
            int n16 = 0;
            int n17 = 0;

            for (int n18 = n15 - 1; n16 < n15; n16++) {
               arr2[n16] = n16 % 2 == 0 ? n17++ : n18--;
            }

            ArrayList<List<int[]>> lists = new ArrayList<>();

            for (int n24 = 0; n24 < n15; n24++) {
               int n25 = n20 + n24 * n22 / n15;
               int n19 = n20 + (n24 + 1) * n22 / n15;
               ArrayList<int[]> points3 = new ArrayList<>();

               for (int[] arr3 : (Iterable<int[]>) points) {
                  if (arr3[1] >= n25 && (arr3[1] < n19 || n24 == n15 - 1 && arr3[1] <= n19)) {
                     points3.add(arr3);
                  }
               }

               lists.add(points3.isEmpty() ? points : points3);
            }

            List list = (List)lists.get(arr2[0]);
            int[] arr5 = new int[]{(this.minX + this.maxX) / 2, (this.minY + this.maxY) / 2, (this.minZ + this.maxZ) / 2};
            double d2 = 0.0;

            for (int[] arr6 : (Iterable<int[]>) list) {
               d2 = Math.max(d2, flatDist(arr6, arr5));
            }

            List<int[]> points4 = new ArrayList<>();

            for (int[] arr4 : (Iterable<int[]>) list) {
               if (flatDist(arr4, arr5) >= d2 * 0.75) {
                  points4.add(arr4);
               }
            }

            if (points4.isEmpty()) {
               points4 = list;
            }

            points2.add((int[])((int[])points4.get(this.placeRandom.nextInt(points4.size()))).clone());

            for (int n26 = 1; n26 < n; n26++) {
               points2.add(this.farEnough((List<int[]>)lists.get(arr2[n26 % n15]), points2, d));
            }

            return points2;
         } else {
            int n12 = this.placeRandom.nextInt(8);
            points2.add(new int[]{(n12 & 1) == 0 ? n6 : n7, (n12 & 2) == 0 ? n10 : n11, (n12 & 4) == 0 ? n8 : n9});

            while (points2.size() < n) {
               points2.add(this.farthest(points, points2, -1));
            }

            for (int n13 = 0; n13 < n3 && n > 1; n13++) {
               for (int n14 = 0; n14 < n; n14++) {
                  int[] arr = this.farthest(points, points2, n14);
                  if (minDistTo(arr, points2, n14) > minDistTo((int[])points2.get(n14), points2, n14)) {
                     points2.set(n14, arr);
                  }
               }
            }

            return points2;
         }
      }
   }

   private static double flatDist(int[] arr, int[] arr2) {
      double d = (double)(arr[0] - arr2[0]);
      double d2 = (double)(arr[2] - arr2[2]);
      return Math.sqrt(d * d + d2 * d2);
   }

   private int[] farEnough(List<int[]> points, List<int[]> points2, double d) {
      double d2 = 0.0;

      for (int[] arr : points) {
         d2 = Math.max(d2, minDistTo(arr, points2, -1));
      }

      double d3 = Math.max(Math.min(12.0, d2), d2 * (1.0 - d));
      ArrayList list = new ArrayList();

      for (int[] arr2 : points) {
         if (minDistTo(arr2, points2, -1) >= d3) {
            list.add(arr2);
         }
      }

      return list.isEmpty() ? this.farthest(points, points2, -1) : (int[])((int[])list.get(this.placeRandom.nextInt(list.size()))).clone();
   }

   private int[] farthest(List<int[]> points, List<int[]> points2, int n) {
      int[] arr = (int[])points.get(0);
      double tmp = -1.0;

      for (int[] arr2 : points) {
         double d = minDistTo(arr2, points2, n);
         if (d > tmp) {
            tmp = d;
            arr = arr2;
         }
      }

      return (int[])arr.clone();
   }

   private static double minDistTo(int[] arr, List<int[]> points, int n) {
      double d = Double.MAX_VALUE;

      for (int n2 = 0; n2 < points.size(); n2++) {
         if (n2 != n) {
            d = Math.min(d, dist(arr, (int[])points.get(n2)));
         }
      }

      return d;
   }

   public double minRoomDistance(int n) {
      List list = this.rooms(n, 12, 1);
      double d = Double.MAX_VALUE;

      for (int n2 = 0; n2 < list.size(); n2++) {
         for (int n3 = n2 + 1; n3 < list.size(); n3++) {
            d = Math.min(d, dist((int[])list.get(n2), (int[])list.get(n3)));
         }
      }

      return list.size() < 2 ? Double.MAX_VALUE : d;
   }

   public int maxRooms() {
      if (this.maxRoomsCache > 0) {
         return this.maxRoomsCache;
      } else {
         int n = 1;
         int n2 = 2;

         while (n2 <= 16 && this.minRoomDistance(n2) >= 12.0) {
            n = n2++;
         }

         return this.maxRoomsCache = n;
      }
   }

   public int sizeFor(int n) {
      int n2 = 21;
      int n3 = 255;

      while (n2 < n3) {
         int n4 = (n2 + n3) / 2;
         Arena arena = new Arena(this.world, (this.minX + this.maxX) / 2, this.minY, (this.minZ + this.maxZ) / 2, n4, this.maxY - this.minY - 1);
         if (arena.maxRooms() >= n) {
            n3 = n4;
         } else {
            n2 = n4 + 1;
         }
      }

      return n2;
   }

   public int[] secretSpot(List<int[]> points) {
      List<int[]> points2 = this.candidates(Math.max(2, Math.max(this.maxX - this.minX, this.maxY - this.minY) / 20));
      if (points.isEmpty()) {
         return (int[])((int[])points2.get(this.placeRandom.nextInt(points2.size()))).clone();
      } else {
         points2.sort((x, x2) -> Double.compare(minDistTo(x2, points, -1), minDistTo(x, points, -1)));
         int n = Math.max(1, points2.size() / 10);
         return (int[])((int[])points2.get(this.placeRandom.nextInt(n))).clone();
      }
   }

   public void carveSecret(int[] arr) {
      for (int n = arr[0] - 2; n <= arr[0] + 2; n++) {
         for (int n2 = arr[1] - 1; n2 <= arr[1] + 3; n2++) {
            for (int n3 = arr[2] - 2; n3 <= arr[2] + 2; n3++) {
               boolean flag = Math.abs(n - arr[0]) <= 1 && Math.abs(n3 - arr[2]) <= 1 && n2 >= arr[1] && n2 <= arr[1] + 2;
               this.world.getBlockAt(n, n2, n3).setType(flag ? Material.AIR : Material.MOSSY_COBBLESTONE, false);
            }
         }
      }
   }

   public boolean isBuilding() {
      return this.buildTask != null;
   }

   public int buildPercent() {
      return this.buildPercent;
   }

   public void cancelBuild() {
      if (this.buildTask != null) {
         this.buildTask.cancel();
         this.buildTask = null;
      }

      this.buildPercent = 100;
   }

   private void fillBox(final Plugin plugin, final Arena.Picker picker, final boolean flag, final IntConsumer intConsumer, final Runnable task) {
      this.cancelBuild();
      final int n4 = this.minX >> 4;
      int n6 = this.maxX >> 4;
      final int n7 = this.minZ >> 4;
      int n9 = this.maxZ >> 4;
      final int n10 = n6 - n4 + 1;
      final int n12 = n10 * (n9 - n7 + 1);
      final int n14 = this.maxY - this.minY + 1;
      final long l2 = (long)n12 * (long)n14;
      this.buildPercent = 0;
      this.buildTask = new BukkitRunnable() {
         int chunk = 0;
         int layer = 0;
         int lastReported = 0;
         long slicesDone = 0L;

         @Override
         public void run() {
            try {
               long l = System.nanoTime();

               label65:
               while (this.chunk < n12 && System.nanoTime() - l < 12000000L) {
                  int n = n4 + this.chunk % n10;
                  int n2 = n7 + this.chunk / n10;
                  int n3 = Math.max(Arena.this.minX, n << 4);
                  int n5 = Math.min(Arena.this.maxX, (n << 4) + 15);
                  int n6 = Math.max(Arena.this.minZ, n2 << 4);
                  int n8 = Math.min(Arena.this.maxZ, (n2 << 4) + 15);

                  for (int n9 = 0; n9 < 1 && this.layer < n14; this.chunk++) {
                     int n11 = flag ? Arena.this.maxY - this.layer : Arena.this.minY + this.layer;

                     for (int n13 = n3; n13 <= n5; n13++) {
                        for (int n15 = n6; n15 <= n8; n15++) {
                           Material material = picker.at(n13, n11, n15);
                           Block block = Arena.this.world.getBlockAt(n13, n11, n15);
                           if (block.getType() != material) {
                              block.setType(material, false);
                           }
                        }
                     }

                     n9++;
                     this.layer++;
                     this.slicesDone++;
                     if (this.layer >= n14) {
                        this.layer = 0;
                     }
                     continue label65;
                  }
                  break;
               }

               Arena.this.buildPercent = (int)Math.min(99L, this.slicesDone * 100L / Math.max(1L, l2));
               if (intConsumer != null && Arena.this.buildPercent / 25 > this.lastReported / 25) {
                  this.lastReported = Arena.this.buildPercent;
                  intConsumer.accept(Arena.this.buildPercent);
               }

               if (this.chunk >= n12) {
                  this.finish();
               }
            } catch (Throwable t) {
               plugin.getLogger().warning("Ошибка при постройке коробки: " + t);
               this.finish();
            }
         }

         private void finish() {
            this.cancel();
            Arena.this.buildTask = null;
            Arena.this.buildPercent = 100;
            task.run();
         }
      };
      this.buildTask.runTaskTimer(plugin, 1L, 1L);
   }

   public boolean overlaps(Location location, int n) {
      return location != null && this.isNear(location, n);
   }

   public void wipe(Plugin plugin, Runnable task) {
      this.removeEntities();
      this.fillBox(plugin, (x, x2, x3) -> Material.AIR, true, null, task);
   }

   private void removeEntities() {
      for (Item item : this.world.getEntitiesByClass(Item.class)) {
         if (this.isInBox(item.getLocation().getBlock())) {
            item.remove();
         }
      }

      for (Villager villager : this.world.getEntitiesByClass(Villager.class)) {
         if (this.isInBox(villager.getLocation().getBlock())) {
            villager.remove();
         }
      }
   }

   public void carveRoom(int[] arr) {
      for (int n = arr[0] - 2; n <= arr[0] + 2; n++) {
         for (int n2 = arr[2] - 2; n2 <= arr[2] + 2; n2++) {
            this.world.getBlockAt(n, arr[1] - 1, n2).setType(Material.COBBLESTONE, false);

            for (int n3 = arr[1]; n3 <= arr[1] + 3; n3++) {
               this.world.getBlockAt(n, n3, n2).setType(Material.AIR, false);
            }
         }
      }

      this.world.getBlockAt(arr[0], arr[1] + 4, arr[2]).setType(Material.GLOWSTONE, false);
   }

   public void regenerate(Plugin plugin, Map<Material, Double> map, IntConsumer intConsumer, Runnable task) {
      Material[] materials = map.keySet().toArray(new Material[0]);
      double[] arr = new double[materials.length];
      double d = 0.0;

      for (int n = 0; n < materials.length; n++) {
         d += map.get(materials[n]);
         arr[n] = d;
      }

      double d2 = d;
      this.removeEntities();
      this.fillBox(plugin, (x, x2, x3) -> {
         boolean flag = x == this.minX || x == this.maxX || x3 == this.minZ || x3 == this.maxZ || x2 == this.minY || x2 == this.maxY;
         return flag ? Material.BEDROCK : this.pick(materials, arr, d2);
      }, false, intConsumer, task);
   }

   private Material pick(Material[] materials, double[] arr, double d) {
      if (materials.length == 0) {
         return Material.COBBLESTONE;
      } else {
         double d2 = this.random.nextDouble() * d;

         for (int n = 0; n < materials.length; n++) {
            if (d2 < arr[n]) {
               return materials[n];
            }
         }

         return materials[materials.length - 1];
      }
   }

   private interface Picker {
      Material at(int n, int n2, int n3);
   }
}
