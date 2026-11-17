package ru.bedrockbox;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class GameListener implements Listener {
   private final BedrockBoxPlugin plugin;

   public GameListener(BedrockBoxPlugin bedrockBoxPlugin) {
      this.plugin = bedrockBoxPlugin;
   }

   private Game game() {
      return this.plugin.game();
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onBreak(BlockBreakEvent blockBreakEvent) {
      if (this.lobbyProtected(blockBreakEvent.getPlayer())) {
         blockBreakEvent.setCancelled(true);
      } else {
         Game game2 = this.game();
         Arena arena2 = game2.arena();
         Player player = blockBreakEvent.getPlayer();
         Block block = blockBreakEvent.getBlock();
         PlayerData playerData = game2.get(player);
         PlayerData.BedRecord bedRecord = game2.bedAt(block);
         if (bedRecord != null) {
            if (playerData == null || game2.state() != Game.State.RUNNING || !playerData.alive) {
               blockBreakEvent.setCancelled(true);
            } else if (game2.sameTeam(bedRecord.owner, player.getUniqueId())) {
               blockBreakEvent.setCancelled(true);
               player.sendMessage("§8[§6Коробка§8] §7§cСвою кровать ломать нельзя!");
            } else {
               blockBreakEvent.setDropItems(false);
               game2.removeBed(bedRecord, player.getName(), block, false);
            }
         } else if (arena2 != null) {
            if (playerData == null) {
               if (arena2.isInBox(block) && !player.hasPermission("bedrockbox.admin")) {
                  blockBreakEvent.setCancelled(true);
               }
            } else if (game2.state() != Game.State.RUNNING || !arena2.isInside(block)) {
               blockBreakEvent.setCancelled(true);
            } else if (!game2.canMine(player, block, player.getInventory().getItemInMainHand())) {
               blockBreakEvent.setCancelled(true);
            } else {
               ItemStack itemStack = player.getInventory().getItemInMainHand();
               blockBreakEvent.setCancelled(true);
               if (!game2.giveMined(player, block, itemStack)) {
                  return;
               }

               game2.damageTool(player, itemStack);
               block.setType(Material.AIR, true);
               this.plugin.antiCheat().cloak().onDug(block, player.getUniqueId(), game2.alivePlayers(), this.plugin);
               if (CustomItems.shapeOf(itemStack) != null) {
                  this.plugin.items().drill(player, block, game2);
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onPlace(BlockPlaceEvent blockPlaceEvent) {
      if (this.lobbyProtected(blockPlaceEvent.getPlayer())) {
         blockPlaceEvent.setCancelled(true);
      } else {
         Game game2 = this.game();
         PlayerData playerData = game2.get(blockPlaceEvent.getPlayer());
         if (playerData == null && game2.arena() != null && game2.arena().isInBox(blockPlaceEvent.getBlockPlaced()) && !blockPlaceEvent.getPlayer().hasPermission("bedrockbox.admin")) {
            blockPlaceEvent.setCancelled(true);
            return;
         }

         if (playerData != null) {
            Block block = blockPlaceEvent.getBlockPlaced();
            if (game2.state() == Game.State.RUNNING && game2.arena() != null && game2.arena().isInside(block)) {
               if (block.getBlockData() instanceof Bed bed) {
                  if (game2.bedsGone()) {
                     blockPlaceEvent.setCancelled(true);
                     blockPlaceEvent.getPlayer().sendMessage("§8[§6Коробка§8] §7§cКровати больше ставить нельзя!");
                     return;
                  }

                  Block block2 = block.getRelative(bed.getFacing());
                  if (!game2.arena().isInside(block2)) {
                     blockPlaceEvent.setCancelled(true);
                     return;
                  }

                  game2.registerBed(playerData, block, block2, block.getLocation().add(0.5, 0.6, 0.5));
                  blockPlaceEvent.getPlayer().sendMessage("§8[§6Коробка§8] §7§aЗапасная кровать стоит.");
               }
            } else {
               blockPlaceEvent.setCancelled(true);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onEntityExplode(EntityExplodeEvent entityExplodeEvent) {
      this.handleExplosion(entityExplodeEvent.blockList());
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onBlockExplode(BlockExplodeEvent blockExplodeEvent) {
      this.handleExplosion(blockExplodeEvent.blockList());
   }

   private void handleExplosion(List<Block> blocks) {
      Game game2 = this.game();
      Iterator it = blocks.iterator();

      while (it.hasNext()) {
         Block block = (Block)it.next();
         PlayerData.BedRecord bedRecord = game2.bedAt(block);
         if (bedRecord != null) {
            it.remove();
            if (game2.state() == Game.State.RUNNING) {
               game2.removeBed(bedRecord, "динамит", null, false);
            }
         } else if (game2.arena() != null && game2.state() != Game.State.RUNNING && game2.arena().isInBox(block)) {
            it.remove();
         }
      }

      if (game2.state() == Game.State.RUNNING && game2.arena() != null) {
         ArrayList list = new ArrayList();

         for (Block block3 : blocks) {
            if (game2.arena().isInside(block3)) {
               list.add(block3);
            }
         }

         if (!list.isEmpty()) {
            Bukkit.getScheduler().runTask(this.plugin, () -> {
               for (Block block2 : (Iterable<Block>) list) {
                  if (block2.getType() == Material.AIR) {
                     this.plugin.antiCheat().cloak().onDug(block2, null, game2.alivePlayers(), this.plugin);
                  }
               }
            });
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onDamage(EntityDamageByEntityEvent e) {
      if (!(e.getEntity() instanceof Player target)) {
         return;
      }
      Entity damager = e.getDamager();
      Player attacker = null;
      if (damager instanceof Player p) {
         attacker = p;
      } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) {
         attacker = p;
      } else if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player p) {
         attacker = p;
      }
      if (attacker == null) {
         return;
      }
      Game game = this.game();
      if (attacker != target && game.teams() && game.state() == Game.State.RUNNING
            && game.sameTeam(attacker.getUniqueId(), target.getUniqueId())) {
         e.setCancelled(true);
         return;
      }
      if (attacker != target && game.inGame(target) && game.inGame(attacker) && game.isGrace()) {
         e.setCancelled(true);
         attacker.sendMessage("§8[§6Коробка§8] §7§eМирное время, PvP пока выключено.");
         return;
      }
      if (damager instanceof Player && game.inGame(attacker) && game.state() == Game.State.RUNNING
            && !this.plugin.antiCheat().legitHit(attacker, target)) {
         e.setCancelled(true);
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onPistonExtend(BlockPistonExtendEvent blockPistonExtendEvent) {
      if (this.touchesBed(blockPistonExtendEvent.getBlocks())) {
         blockPistonExtendEvent.setCancelled(true);
      }
   }

   @EventHandler(ignoreCancelled = true)
   public void onPistonRetract(BlockPistonRetractEvent blockPistonRetractEvent) {
      if (this.touchesBed(blockPistonRetractEvent.getBlocks())) {
         blockPistonRetractEvent.setCancelled(true);
      }
   }

   private boolean touchesBed(List<Block> blocks) {
      for (Block block : blocks) {
         if (this.game().bedAt(block) != null) {
            return true;
         }
      }

      return false;
   }

   @EventHandler
   public void onDeath(PlayerDeathEvent playerDeathEvent) {
      Player player = playerDeathEvent.getEntity();
      if (this.game().inGame(player)) {
         this.game().onDeath(player, player.getKiller());
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onRespawn(PlayerRespawnEvent playerRespawnEvent) {
      Location location = this.game().onRespawn(playerRespawnEvent.getPlayer());
      if (location == null && this.plugin.lobby().enabled()) {
         location = this.plugin.lobby().spawn();
      }

      if (location != null) {
         playerRespawnEvent.setRespawnLocation(location);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onTeleport(PlayerTeleportEvent playerTeleportEvent) {
      if (this.game().names().isHidden(playerTeleportEvent.getPlayer())) {
         this.game().names().release(playerTeleportEvent.getPlayer());
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onLaunch(ProjectileLaunchEvent projectileLaunchEvent) {
      if (projectileLaunchEvent.getEntity() instanceof EnderPearl enderPearl && enderPearl.getShooter() instanceof Player player && this.game().names().isHidden(player)) {
         this.game().names().release(player);
      }
   }

   private boolean lobbyProtected(Player player) {
      return this.plugin.lobby().isLobby(player.getLocation()) && (!player.hasPermission("bedrockbox.admin") || player.getGameMode() != GameMode.CREATIVE);
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onLobbyDamage(EntityDamageEvent entityDamageEvent) {
      if (entityDamageEvent.getEntity() instanceof Player player && !this.game().inGame(player) && this.plugin.lobby().isLobby(player.getLocation())) {
         entityDamageEvent.setCancelled(true);
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onLobbyHunger(FoodLevelChangeEvent foodLevelChangeEvent) {
      if (this.plugin.lobby().isLobby(foodLevelChangeEvent.getEntity().getLocation())) {
         foodLevelChangeEvent.setCancelled(true);
      }
   }

   @EventHandler
   public void onJoinServer(PlayerJoinEvent playerJoinEvent) {
      Player player = playerJoinEvent.getPlayer();
      Bukkit.getScheduler()
         .runTaskLater(
            this.plugin,
            () -> {
               if (player.isOnline()) {
                  if (!this.game().onReconnect(player)) {
                     if (this.plugin.lobby().enabled()
                        && !this.game().inGame(player)
                        && !this.game().isOutsideSpectator(player)
                        && player.getGameMode() != GameMode.CREATIVE) {
                        this.plugin.lobby().send(player);
                     }

                     this.game().autoJoin(player);
                  }
               }
            },
            10L
         );
   }

   @EventHandler
   public void onWorldChange(PlayerChangedWorldEvent playerChangedWorldEvent) {
      this.game().autoJoin(playerChangedWorldEvent.getPlayer());
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent playerQuitEvent) {
      this.plugin.antiCheat().reveal(playerQuitEvent.getPlayer());
      this.plugin.antiCheat().forget(playerQuitEvent.getPlayer());
      this.game().names().show(playerQuitEvent.getPlayer());
      this.plugin.antiCheat().cloak().reveal(playerQuitEvent.getPlayer());
      if (!this.game().onDisconnect(playerQuitEvent.getPlayer())) {
         if (this.game().inGame(playerQuitEvent.getPlayer()) || this.game().isOutsideSpectator(playerQuitEvent.getPlayer())) {
            this.game().leave(playerQuitEvent.getPlayer(), true);
         }
      }
   }

   @EventHandler
   public void onInteract(PlayerInteractEvent playerInteractEvent) {
      Action action = playerInteractEvent.getAction();
      if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
         ItemStack itemStack = playerInteractEvent.getItem();
         if (itemStack != null && CustomItems.shapeOf(itemStack) == null) {
            if (this.plugin.items().use(playerInteractEvent.getPlayer(), itemStack, this.game())) {
               playerInteractEvent.setCancelled(true);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onTrader(PlayerInteractEntityEvent playerInteractEntityEvent) {
      if (this.game().isTrader(playerInteractEntityEvent.getRightClicked())) {
         playerInteractEntityEvent.setCancelled(true);
         if (playerInteractEntityEvent.getHand() == EquipmentSlot.HAND) {
            Player player = playerInteractEntityEvent.getPlayer();
            PlayerData playerData = this.game().get(player);
            if (playerData != null && playerData.alive && this.game().state() == Game.State.RUNNING) {
               if (this.game().isSecretTrader(playerInteractEntityEvent.getRightClicked())) {
                  this.game().onSecretFound(player);
                  this.plugin.secretShop().open(player);
               } else {
                  this.plugin.shop().open(player);
               }

               player.playSound(player.getLocation(), "entity.villager.trade", 1.0F, 1.0F);
            } else {
               player.sendMessage("§8[§6Коробка§8] §7§7Торговец торгует только с участниками битвы.");
            }
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onFall(EntityDamageEvent entityDamageEvent) {
      if (this.game().isTrader(entityDamageEvent.getEntity())) {
         entityDamageEvent.setCancelled(true);
      } else if (this.game().state() == Game.State.ENDING && entityDamageEvent.getEntity() instanceof Player) {
         entityDamageEvent.setCancelled(true);
      } else {
         if (entityDamageEvent.getCause() == DamageCause.FALL && entityDamageEvent.getEntity() instanceof Player player && this.plugin.items().noFall(player)) {
            entityDamageEvent.setCancelled(true);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onWatcherCommand(PlayerCommandPreprocessEvent playerCommandPreprocessEvent) {
      Player player = playerCommandPreprocessEvent.getPlayer();
      if (this.game().isWatcher(player) && !player.hasPermission("bedrockbox.admin")) {
         String str = playerCommandPreprocessEvent.getMessage().toLowerCase();
         if (!str.startsWith("/bb ") && !str.equals("/bb") && !str.startsWith("/bedrockbox") && !str.startsWith("/korobka")) {
            playerCommandPreprocessEvent.setCancelled(true);
            player.sendMessage("§8[§6Коробка§8] §7§cНаблюдатели не могут использовать команды. §7Выйти: §e/bb leave");
         }
      }
   }

   @EventHandler
   public void onClick(InventoryClickEvent inventoryClickEvent) {
      if (inventoryClickEvent.getInventory().getHolder() instanceof Shop.Holder) {
         inventoryClickEvent.setCancelled(true);
         if (inventoryClickEvent.getWhoClicked() instanceof Player player) {
            int n = inventoryClickEvent.getRawSlot();
            if (n >= 0 && n < inventoryClickEvent.getInventory().getSize()) {
               ((Shop.Holder)inventoryClickEvent.getInventory().getHolder()).shop().buy(player, n);
            }
         }
      }
   }

   @EventHandler
   public void onDrag(InventoryDragEvent inventoryDragEvent) {
      if (inventoryDragEvent.getInventory().getHolder() instanceof Shop.Holder) {
         inventoryDragEvent.setCancelled(true);
      }
   }

   @EventHandler
   public void onSleep(PlayerBedEnterEvent playerBedEnterEvent) {
      if (this.game().inGame(playerBedEnterEvent.getPlayer())) {
         playerBedEnterEvent.setCancelled(true);
      }
   }
}
