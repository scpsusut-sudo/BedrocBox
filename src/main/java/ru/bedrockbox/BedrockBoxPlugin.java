package ru.bedrockbox;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.World;
import ru.bedrockbox.anticheat.AntiCheat;
import ru.bedrockbox.anticheat.ChunkListener;
import ru.bedrockbox.arena.Arena;
import ru.bedrockbox.arena.Cinematic;
import ru.bedrockbox.arena.Lobby;
import ru.bedrockbox.arena.TallWorld;
import ru.bedrockbox.command.BBCommand;
import ru.bedrockbox.game.Game;
import ru.bedrockbox.game.GameListener;
import ru.bedrockbox.shop.CustomItems;
import ru.bedrockbox.shop.Shop;

public final class BedrockBoxPlugin extends JavaPlugin {
   private Game game;
   private Shop shop;
   private Shop secretShop;
   private CustomItems items;
   private TallWorld tallWorld;
   private Lobby lobby;
   private Cinematic cinematic;
   private AntiCheat antiCheat;
   private static final int CONFIG_VERSION = 16;

   @Override
   public void onEnable() {
      this.saveDefaultConfig();
      this.migrateConfig();
      this.items = new CustomItems(this);
      this.tallWorld = new TallWorld(this);
      this.lobby = new Lobby(this);
      this.cinematic = new Cinematic(this);
      this.antiCheat = new AntiCheat(this);
      this.tallWorld.world();
      this.shop = new Shop("shop", "§8Торговец коробки");
      this.secretShop = new Shop("secret-shop", "§5§lТайный торговец");
      this.game = new Game(this);
      this.antiCheat.setAllies(this.game::sameTeam);
      this.reloadSettings();
      this.reloadArena();
      this.game.names().clearAll();
      if (this.game.arena() != null && this.lobby.conflicts(this.game.arena())) {
         Bukkit.getScheduler().runTaskLater(this, () -> this.game.rebuild(this.game.arena(), null), 40L);
      } else if (this.game.arena() != null && this.game.wasInterrupted()) {
         this.getLogger().info("Прошлая игра не закончилась, пересобираю коробку.");
         Bukkit.getScheduler().runTaskLater(this, () -> this.game.resetArena(null), 60L);
      }

      Bukkit.getPluginManager().registerEvents(new GameListener(this), this);

      try {
         Class.forName("io.papermc.paper.event.packet.PlayerChunkLoadEvent");
         Bukkit.getPluginManager().registerEvents(new ChunkListener(this), this);
      } catch (Throwable t) {
         this.getLogger().warning("Это не Paper - анти-фрикам будет работать слабее (нет события загрузки чанка).");
      }

      BBCommand bBCommand = new BBCommand(this);
      PluginCommand pluginCommand = this.getCommand("bb");
      if (pluginCommand != null) {
         pluginCommand.setExecutor(bBCommand);
         pluginCommand.setTabCompleter(bBCommand);
      }

      Bukkit.getScheduler().runTaskTimer(this, () -> {
         this.game.enforceEverywhere();
         this.lobby.tick();
      }, 40L, 20L);
      Bukkit.getScheduler().runTaskLater(this, () -> {
         if (this.lobby.enabled()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
               if (player.getGameMode() != GameMode.CREATIVE && !this.game.inGame(player)) {
                  this.lobby.send(player);
               }
            }
         }

         this.game.autoJoinAll();
      }, 60L);
      if (this.game.arena() != null && this.antiCheat.ensureXray(this.game.arena().world, this.game.arena().maxY)) {
         this.getLogger().warning("Анти-X-ray только что включён для мира арены - перезапусти сервер ещё раз.");
      }

      this.getLogger().info("BedrockBox включён. Построй арену: /bb setup");
   }

   @Override
   public void onDisable() {
      if (this.cinematic != null) {
         this.cinematic.stopAll();
      }

      if (this.game != null) {
         this.game.shutdown();
      }
   }

   private void migrateConfig() {
      FileConfiguration fileConfiguration = this.getConfig();
      if (fileConfiguration.getInt("config-version", 0) < 16) {
         LinkedHashMap map = new LinkedHashMap();

         for (String str : fileConfiguration.getKeys(true)) {
            if (!fileConfiguration.isConfigurationSection(str)
               && !str.equals("config-version")
               && !str.startsWith("shop.")
               && !str.startsWith("secret-shop.")
               && !str.startsWith("fill.blocks.")) {
               map.put(str, fileConfiguration.get(str));
            }
         }

         File file = new File(this.getDataFolder(), "config.yml");
         File file2 = new File(this.getDataFolder(), "config-old.yml");
         if (file2.exists()) {
            file2.delete();
         }

         file.renameTo(file2);
         this.saveResource("config.yml", true);
         this.reloadConfig();
         FileConfiguration fileConfiguration2 = this.getConfig();

         for (Entry entry : (Iterable<Entry>) map.entrySet()) {
            if (fileConfiguration2.contains((String)entry.getKey()) || ((String)entry.getKey()).startsWith("arena.") || ((String)entry.getKey()).startsWith("lobby.")) {
               fileConfiguration2.set((String)entry.getKey(), entry.getValue());
            }
         }

         fileConfiguration2.set("game.bed-destroy-seconds", Integer.valueOf(0));
         fileConfiguration2.set("game.haste-level", Integer.valueOf(0));
         fileConfiguration2.set("fill.rare-ores.DIAMOND_ORE", Double.valueOf(0.1));
         fileConfiguration2.set("fill.emerald-percent", Integer.valueOf(2));
         fileConfiguration2.set("game.spawn-randomness", Double.valueOf(0.15));
         if (!fileConfiguration2.contains("lobby.x")) {
            fileConfiguration2.set("lobby.mode", "arena");
         }

         this.saveConfig();
         this.getLogger().info("Конфиг обновлён до версии 16 (старый - config-old.yml). Арена и настройки сохранены.");
      }
   }

   public void reloadSettings() {
      this.antiCheat.load(this.getConfig());
      this.lobby.load(this.getConfig());
      this.game.loadSettings(this.getConfig());
      this.shop.load(this.getConfig(), this.getLogger());
      this.secretShop.load(this.getConfig(), this.getLogger());
   }

   public void reloadArena() {
      this.game.setArena(this.arenaFromConfig());
      this.lobby.follow(this.game.arena());
   }

   public Arena arenaFromConfig() {
      FileConfiguration fileConfiguration = this.getConfig();
      String str = fileConfiguration.getString("arena.world", "");
      World world2 = str != null && !str.isEmpty() ? Bukkit.getWorld(str) : null;
      if (world2 == null) {
         return null;
      } else {
         Arena arena2 = new Arena(
            world2,
            fileConfiguration.getInt("arena.x"),
            fileConfiguration.getInt("arena.y"),
            fileConfiguration.getInt("arena.z"),
            fileConfiguration.getInt("arena.size", 41),
            arenaLength(fileConfiguration),
            fileConfiguration.getInt("arena.height", 31)
         );
         arena2.setSpawnRandomness(fileConfiguration.getDouble("game.spawn-randomness", 0.15));
         return arena2;
      }
   }

   public static int arenaLength(FileConfiguration fileConfiguration) {
      int n = fileConfiguration.getInt("arena.length", 0);
      return n > 0 ? n : fileConfiguration.getInt("arena.size", 41);
   }

   public Arena safeArena(Arena arena2, Player player) {
      if (arena2 != null && this.lobby.conflicts(arena2)) {
         World world2 = Lobby.arenaWorld();
         FileConfiguration fileConfiguration = this.getConfig();
         int n = fileConfiguration.getInt("arena.size", 41);
         int n2 = Math.max(15, Math.min(fileConfiguration.getInt("arena.height", 31), world2.getMaxHeight() - world2.getMinHeight() - 6));
         int n3 = Math.max(world2.getMinHeight() + 1, Math.min(world2.getMaxHeight() - n2 - 4, 0));
         fileConfiguration.set("arena.world", world2.getName());
         fileConfiguration.set("arena.x", Integer.valueOf(0));
         fileConfiguration.set("arena.y", Integer.valueOf(n3));
         fileConfiguration.set("arena.z", Integer.valueOf(0));
         fileConfiguration.set("arena.height", Integer.valueOf(n2));
         this.saveConfig();
         Arena arena3 = new Arena(world2, 0, n3, 0, n, arenaLength(fileConfiguration), n2);
         arena3.setSpawnRandomness(fileConfiguration.getDouble("game.spawn-randomness", 0.15));
         String str = Game.PREFIX + "§eКоробка задевала лобби - перенёс её в отдельный мир §fbb_arena§e, чтобы стройка не сносила площадку.";
         this.getLogger().info("Коробка задевала лобби - перенесена в мир bb_arena.");
         if (player != null && player.isOnline()) {
            player.sendMessage(str);
            Location location = new Location(world2, (double)arena3.maxX + 4.5, (double)(n3 + 1), 0.5);
            world2.getBlockAt(location.getBlockX(), n3, location.getBlockZ()).setType(Material.GLASS, false);
            player.teleport(location);
         }

         return arena3;
      } else {
         return arena2;
      }
   }

   public Game game() {
      return this.game;
   }

   public Shop shop() {
      return this.shop;
   }

   public Shop secretShop() {
      return this.secretShop;
   }

   public CustomItems items() {
      return this.items;
   }

   public TallWorld tallWorld() {
      return this.tallWorld;
   }

   public Lobby lobby() {
      return this.lobby;
   }

   public Cinematic cinematic() {
      return this.cinematic;
   }

   public AntiCheat antiCheat() {
      return this.antiCheat;
   }
}
