package ru.bedrockbox;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class Cinematic {
   private final Plugin plugin;
   private final Set<UUID> watching = new HashSet<>();
   private final Map<UUID, GameMode> prevMode = new HashMap<>();

   public Cinematic(Plugin plugin2) {
      this.plugin = plugin2;
   }

   public boolean isWatching(Player player) {
      return this.watching.contains(player.getUniqueId());
   }

   private void begin(Player player) {
      if (this.watching.add(player.getUniqueId())) {
         this.prevMode.put(player.getUniqueId(), player.getGameMode());
      }

      player.setGameMode(GameMode.SPECTATOR);
   }

   private void finish(Player player) {
      this.watching.remove(player.getUniqueId());
      GameMode gameMode = this.prevMode.remove(player.getUniqueId());
      if (player.isOnline() && gameMode != null) {
         player.setGameMode(gameMode);
      }
   }

   public void stopAll() {
      for (UUID uUID : new ArrayList<>(this.watching)) {
         Player player = Bukkit.getPlayer(uUID);
         if (player != null) {
            this.finish(player);
         }
      }

      this.watching.clear();
   }

   static Location lookAt(Location location, Location location2) {
      Location location3 = location.clone();
      double d = location2.getX() - location.getX();
      double d2 = location2.getY() - location.getY();
      double d3 = location2.getZ() - location.getZ();
      double d4 = Math.sqrt(d * d + d3 * d3);
      location3.setYaw((float)Math.toDegrees(Math.atan2(-d, d3)));
      location3.setPitch((float)(-Math.toDegrees(Math.atan2(d2, d4))));
      return location3;
   }

   public void orbit(
      Collection<Player> tmp,
      final Location location,
      final double d2,
      final double d3,
      final int n,
      final double d4,
      final BooleanSupplier booleanSupplier,
      final Consumer<Player> consumer
   ) {
      final ArrayList list = new ArrayList();

      for (Player player2 : tmp) {
         if (player2.isOnline() && !this.isWatching(player2)) {
            this.begin(player2);
            list.add(player2);
         }
      }

      if (!list.isEmpty()) {
         final double d5 = Math.random() * Math.PI * 2.0;
         (new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
               boolean tmp = this.t >= n || booleanSupplier != null && booleanSupplier.getAsBoolean() && this.t > 40;
               double d = d5 + (double)this.t * d4 * Math.PI * 2.0 / 20.0;
               Location location2 = location.clone().add(Math.cos(d) * d2, d3, Math.sin(d) * d2);
               Location location3 = Cinematic.lookAt(location2, location);

               for (Player player : (Iterable<Player>) list) {
                  if (player.isOnline()) {
                     if (tmp) {
                        Cinematic.this.finish(player);
                        if (consumer != null) {
                           consumer.accept(player);
                        }
                     } else {
                        player.teleport(location3);
                     }
                  }
               }

               if (tmp) {
                  this.cancel();
               }

               this.t++;
            }
         }).runTaskTimer(this.plugin, 0L, 1L);
      }
   }

   public void fly(final Player player, final Location location, final Location location2, final Location location3, final int n, final Runnable task) {
      if (player.isOnline()) {
         this.begin(player);
         (new BukkitRunnable() {
               int t = 0;

               @Override
               public void run() {
                  if (!player.isOnline()) {
                     Cinematic.this.watching.remove(player.getUniqueId());
                     Cinematic.this.prevMode.remove(player.getUniqueId());
                     this.cancel();
                  } else {
                     double d = Math.min(1.0, (double)this.t / (double)n);
                     double d2 = 1.0 - Math.pow(1.0 - d, 3.0);
                     Location location4 = new Location(
                        location.getWorld(),
                        location.getX() + (location2.getX() - location.getX()) * d2,
                        location.getY() + (location2.getY() - location.getY()) * d2,
                        location.getZ() + (location2.getZ() - location.getZ()) * d2
                     );
                     player.teleport(Cinematic.lookAt(location4, location3));
                     if (this.t >= n) {
                        this.cancel();
                        Cinematic.this.finish(player);
                        if (task != null) {
                           task.run();
                        }
                     }

                     this.t++;
                  }
               }
            })
            .runTaskTimer(this.plugin, 0L, 1L);
      }
   }
}
