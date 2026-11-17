package ru.bedrockbox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Bed.Part;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public final class Game {
   public static final String PREFIX = "§8[§6Коробка§8] §7";
   public static final int MAX_PLAYERS_LIMIT = 16;
   private static final Material[] BED_COLORS = new Material[]{
      Material.RED_BED,
      Material.BLUE_BED,
      Material.LIME_BED,
      Material.YELLOW_BED,
      Material.PURPLE_BED,
      Material.ORANGE_BED,
      Material.CYAN_BED,
      Material.PINK_BED,
      Material.LIGHT_BLUE_BED,
      Material.MAGENTA_BED,
      Material.GREEN_BED,
      Material.BROWN_BED,
      Material.BLACK_BED,
      Material.GRAY_BED,
      Material.LIGHT_GRAY_BED,
      Material.WHITE_BED
   };
   private static final String[] COLOR_CODES = new String[]{"§c", "§9", "§a", "§e", "§5", "§6", "§3", "§d", "§b", "§d", "§2", "§6", "§0", "§8", "§7", "§f"};
   private final BedrockBoxPlugin plugin;
   private Arena arena;
   private Game.State state = Game.State.WAITING;
   private final Map<UUID, PlayerData> players = new LinkedHashMap<>();
   private final Map<String, PlayerData.BedRecord> bedsByBlock = new HashMap<>();
   private final Map<UUID, String> colors = new HashMap<>();
   private final Map<UUID, Location> spectators = new LinkedHashMap<>();
   private final List<Entity> traders = new ArrayList<>();
   private final List<Entity> secretTraders = new ArrayList<>();
   private boolean secretFound;
   private final Set<UUID> offline = new HashSet<>();
   private final Set<UUID> dropped = new HashSet<>();
   private int rejoinSeconds = 120;
   private boolean autoJoin = true;
   private boolean autoSpectate = true;
   private String autoJoinWorld = "";
   private final Set<UUID> optedOut = new HashSet<>();
   private int secretCount = 1;
   private boolean hideCoords = true;
   private Boolean prevReducedDebug;
   private Boolean prevImmediateRespawn;
   public static final String TRADER_NAME = "§6§lТорговец §7(ПКМ)";
   private BukkitRunnable countdownTask;
   private BukkitRunnable tickTask;
   private BukkitRunnable espTask;
   private BossBar bar;
   private int elapsed;
   private int startedWith;
   private int startedTeams;
   private int teamSize = 1;
   private boolean bedsGone;
   private boolean anyPickaxe = true;
   private boolean hideNametags = true;
   private boolean hideEverywhere = false;
   private int autoStart;
   private BossBar lobbyBar;
   private int minPlayers;
   private int maxPlayers;
   private int countdown;
   private int graceSeconds;
   private int bedDestroySeconds;
   private int hasteLevel;
   private final Map<Material, Double> fill = new LinkedHashMap<>();
   private final Map<Material, Material> drops = new HashMap<>();
   private final Set<Material> rareOres = new HashSet<>();
   private final Set<Material> fillerBlocks = new HashSet<>();
   private final Map<Material, Integer> minTier = new HashMap<>();
   private final Map<UUID, Long> tierWarned = new HashMap<>();
   public static final String[] TEAM_NAMES = new String[]{
      "Красные",
      "Синие",
      "Лаймовые",
      "Жёлтые",
      "Фиолетовые",
      "Оранжевые",
      "Бирюзовые",
      "Розовые",
      "Голубые",
      "Пурпурные",
      "Зелёные",
      "Коричневые",
      "Чёрные",
      "Серые",
      "Светлые",
      "Белые"
   };
   private boolean forcedStart;
   private int countdownLeft;
   private int countdownTotal;
   private final NameHider names = new NameHider();
   private final Random toolRandom = new Random();
   private boolean cinematics = true;
   private BossBar buildBar;

   public Game(BedrockBoxPlugin bedrockBoxPlugin) {
      this.plugin = bedrockBoxPlugin;
   }

   public void loadSettings(FileConfiguration fileConfiguration) {
      this.minPlayers = Math.max(1, fileConfiguration.getInt("game.min-players", 2));
      this.maxPlayers = Math.max(1, Math.min(16, fileConfiguration.getInt("game.max-players", 8)));
      this.countdown = Math.max(1, fileConfiguration.getInt("game.countdown", 10));
      this.autoStart = Math.max(5, fileConfiguration.getInt("game.auto-start-seconds", 120));
      this.hideNametags = fileConfiguration.getBoolean("game.hide-nametags", true);
      this.hideEverywhere = fileConfiguration.getBoolean("game.hide-nametags-everywhere", false);
      this.secretCount = Math.max(0, Math.min(8, fileConfiguration.getInt("secret-trader.count", 1)));
      this.autoJoin = fileConfiguration.getBoolean("auto-join.enabled", true);
      this.autoSpectate = fileConfiguration.getBoolean("auto-join.spectate-running", true);
      this.autoJoinWorld = fileConfiguration.getString("auto-join.world", "");
      this.hideCoords = fileConfiguration.getBoolean("game.hide-coordinates", true);
      this.rejoinSeconds = Math.max(0, fileConfiguration.getInt("game.rejoin-seconds", 120));
      this.teamSize = Math.max(1, Math.min(8, fileConfiguration.getInt("game.team-size", 1)));
      this.cinematics = fileConfiguration.getBoolean("game.cinematics", true);
      this.graceSeconds = Math.max(0, fileConfiguration.getInt("game.grace-seconds", 60));
      this.bedDestroySeconds = Math.max(0, fileConfiguration.getInt("game.bed-destroy-seconds", 0));
      this.hasteLevel = Math.max(0, fileConfiguration.getInt("game.haste-level", 2));
      this.fill.clear();
      double d = Math.max(0.0, Math.min(100.0, fileConfiguration.getDouble("fill.emerald-percent", 8.0)));
      double d2 = Math.max(0.0, Math.min(100.0, fileConfiguration.getDouble("fill.stone-percent", 30.0)));
      double d3 = 0.0;
      ConfigurationSection configurationSection = fileConfiguration.getConfigurationSection("fill.rare-ores");
      if (configurationSection != null) {
         for (String str : configurationSection.getKeys(false)) {
            Material material = Material.matchMaterial(str);
            double d4 = Math.max(0.0, Math.min(100.0, configurationSection.getDouble(str)));
            if (material != null && material.isBlock() && d4 > 0.0) {
               this.fill.put(material, d4);
               d3 += d4;
            }
         }
      }

      double d6 = Math.max(0.0, 100.0 - d - d3);
      this.fill.put(Material.EMERALD_ORE, d);
      LinkedHashMap<Material, Double> map = new LinkedHashMap<>();
      ConfigurationSection configurationSection2 = fileConfiguration.getConfigurationSection("fill.blocks");
      if (configurationSection2 != null) {
         for (String str2 : configurationSection2.getKeys(false)) {
            Material material2 = Material.matchMaterial(str2);
            double d5 = Math.max(0.0, configurationSection2.getDouble(str2));
            if (material2 != null && material2.isBlock() && d5 > 0.0) {
               map.merge(material2, d5, Double::sum);
            }
         }
      }

      if (map.isEmpty()) {
         map.put(Material.STONE, d2);
         map.put(Material.COBBLESTONE, 100.0 - d2);
      }

      double d7 = 0.0;

      for (double d8 : (Iterable<Double>) map.values()) {
         d7 += d8;
      }

      for (Entry entry : map.entrySet()) {
         this.fill.merge((Material)entry.getKey(), d6 * (Double)entry.getValue() / d7, Double::sum);
      }

      this.fillerBlocks.clear();
      this.fillerBlocks.addAll(map.keySet());
      this.rareOres.clear();
      if (configurationSection != null) {
         for (String str3 : configurationSection.getKeys(false)) {
            Material material3 = Material.matchMaterial(str3);
            if (material3 != null) {
               this.rareOres.add(material3);
            }
         }
      }

      this.anyPickaxe = fileConfiguration.getBoolean("fill.any-pickaxe", true);
      this.minTier.clear();
      ConfigurationSection configurationSection3 = fileConfiguration.getConfigurationSection("fill.min-pickaxe");
      if (configurationSection3 != null) {
         for (String str4 : configurationSection3.getKeys(false)) {
            Material material4 = Material.matchMaterial(str4);
            Material material5 = Material.matchMaterial(configurationSection3.getString(str4, ""));
            if (material4 != null && material5 != null && tier(material5) > 0) {
               this.minTier.put(material4, tier(material5));
            }
         }
      }

      this.drops.clear();
      ConfigurationSection configurationSection4 = fileConfiguration.getConfigurationSection("drops");
      if (configurationSection4 != null) {
         for (String str5 : configurationSection4.getKeys(false)) {
            Material material7 = Material.matchMaterial(str5);
            Material material6 = Material.matchMaterial(configurationSection4.getString(str5, ""));
            if (material7 != null && material6 != null) {
               this.drops.put(material7, material6);
            }
         }
      }
   }

   public void setArena(Arena arena2) {
      this.arena = arena2;
   }

   public Arena arena() {
      return this.arena;
   }

   public Game.State state() {
      return this.state;
   }

   public Material dropFor(Material material) {
      return this.drops.get(material);
   }

   public boolean anyPickaxe() {
      return this.anyPickaxe;
   }

   public static int tier(Material material) {
      if (material != null && material.name().endsWith("_PICKAXE")) {
         String str = material.name();

         return switch (str) {
            case "WOODEN_PICKAXE", "GOLDEN_PICKAXE" -> 1;
            case "STONE_PICKAXE" -> 2;
            case "IRON_PICKAXE" -> 3;
            case "DIAMOND_PICKAXE" -> 4;
            case "NETHERITE_PICKAXE" -> 5;
            default -> 1;
         };
      } else {
         return 0;
      }
   }

   public boolean canMine(Player player, Block block, ItemStack itemStack) {
      Integer n = this.minTier.get(block.getType());
      if (n == null) {
         return true;
      } else if (tier(itemStack == null ? null : itemStack.getType()) >= n) {
         return true;
      } else {
         long l = System.currentTimeMillis();
         Long l2 = this.tierWarned.get(player.getUniqueId());
         if (l2 == null || l - l2 > 3000L) {
            this.tierWarned.put(player.getUniqueId(), l);
            String[] strings = new String[]{"", "деревянной", "каменной", "железной", "алмазной", "незеритовой"};
            player.sendMessage(
               "§8[§6Коробка§8] §7§cЭту руду можно добыть только §f"
                  + strings[Math.min(5, n)]
                  + " киркой§c или лучше."
                  + (n == 3 ? " §7Железная у торговца." : "")
            );
         }

         return false;
      }
   }

   public int effectiveMax() {
      return this.arena == null ? this.maxPlayers : Math.min(this.maxPlayers, this.arena.maxRooms() * this.teamSize);
   }

   private File dirtyFile() {
      return new File(this.plugin.getDataFolder(), "dirty.flag");
   }

   private void setDirty(boolean flag2) {
      File file = this.dirtyFile();
      try {
         if (flag2) {
            file.getParentFile().mkdirs();
            file.createNewFile();
         } else {
            file.delete();
         }
      } catch (IOException iOException) {
         this.plugin.getLogger().warning("dirty.flag: " + iOException.getMessage());
      }
   }

   public boolean wasInterrupted() {
      return this.dirtyFile().exists();
   }

   private int minNeeded() {
      return Math.max(1, Math.min(this.minPlayers, this.effectiveMax()));
   }

   public int teamSize() {
      return this.teamSize;
   }

   public boolean teams() {
      return this.teamSize > 1;
   }

   public boolean sameTeam(UUID uUID, UUID uUID2) {
      if (uUID == null || uUID2 == null) {
         return false;
      } else if (uUID.equals(uUID2)) {
         return true;
      } else if (this.teamSize <= 1) {
         return false;
      } else {
         PlayerData playerData = this.players.get(uUID);
         PlayerData playerData2 = this.players.get(uUID2);
         return playerData != null && playerData2 != null && playerData.team >= 0 && playerData.team == playerData2.team;
      }
   }

   public String teamName(int n) {
      return COLOR_CODES[n % COLOR_CODES.length] + TEAM_NAMES[n % TEAM_NAMES.length];
   }

   public void chooseTeam(Player player, String str) {
      PlayerData playerData = this.get(player);
      if (!this.teams()) {
         player.sendMessage("§8[§6Коробка§8] §7§7Сейчас режим «каждый сам за себя». Админ: §e/bb set teamsize 2");
      } else if (playerData != null && (this.state == Game.State.WAITING || this.state == Game.State.STARTING)) {
         int n = Math.max(2, (int)Math.ceil((double)Math.max(this.players.size(), 2) / (double)this.teamSize));
         if (str != null) {
            String str2 = str.toLowerCase();

            for (int n4 = 0; n4 < TEAM_NAMES.length; n4++) {
               if (TEAM_NAMES[n4].toLowerCase().startsWith(str2)) {
                  int n5 = 0;

                  for (PlayerData playerData3 : this.players.values()) {
                     if (playerData3.prefTeam == n4 && playerData3 != playerData) {
                        n5++;
                     }
                  }

                  if (n5 >= this.teamSize) {
                     player.sendMessage("§8[§6Коробка§8] §7§cВ команде " + this.teamName(n4) + " §cуже нет мест.");
                     return;
                  }

                  playerData.prefTeam = n4;
                  this.broadcast("§e" + player.getName() + " §7выбрал команду " + this.teamName(n4));
                  return;
               }
            }

            player.sendMessage("§8[§6Коробка§8] §7§cНет такой команды. Список: §e/bb team");
         } else {
            StringBuilder stringBuilder = new StringBuilder("§8[§6Коробка§8] §7§7Команды (по " + this.teamSize + "): ");

            for (int n2 = 0; n2 < Math.min(n + 1, TEAM_NAMES.length); n2++) {
               int n3 = 0;

               for (PlayerData playerData2 : this.players.values()) {
                  if (playerData2.prefTeam == n2) {
                     n3++;
                  }
               }

               stringBuilder.append(this.teamName(n2)).append(" §8(").append(n3).append(") ");
            }

            player.sendMessage(stringBuilder.toString());
            player.sendMessage("§8[§6Коробка§8] §7§7Выбрать: §e/bb team красные");
         }
      } else {
         player.sendMessage("§8[§6Коробка§8] §7§cКоманду можно выбрать только в очереди перед битвой.");
      }
   }

   private List<List<PlayerData>> buildTeams(List<PlayerData> playerDatas) {
      ArrayList<List<PlayerData>> lists = new ArrayList<>();
      if (this.teamSize <= 1) {
         for (PlayerData playerData2 : playerDatas) {
            ArrayList list4 = new ArrayList();
            list4.add(playerData2);
            lists.add(list4);
         }

         return lists;
      } else {
         int n = Math.max(2, Math.min(TEAM_NAMES.length, (int)Math.ceil((double)playerDatas.size() / (double)this.teamSize)));
         if (playerDatas.size() < 2) {
            n = 1;
         }

         for (int n2 = 0; n2 < n; n2++) {
            lists.add(new ArrayList());
         }

         ArrayList list3 = new ArrayList();

         for (PlayerData playerData : playerDatas) {
            if (playerData.prefTeam >= 0 && playerData.prefTeam < n && ((List)lists.get(playerData.prefTeam)).size() < this.teamSize) {
               ((List)lists.get(playerData.prefTeam)).add(playerData);
            } else {
               list3.add(playerData);
            }
         }

         Collections.shuffle(list3);

         for (PlayerData playerData3 : (Iterable<PlayerData>) list3) {
            List list = (List)lists.get(0);

            for (List list2 : lists) {
               if (list2.size() < list.size()) {
                  list = list2;
               }
            }

            list.add(playerData3);
         }

         for (List list5 : lists) {
            if (list5.isEmpty()) {
               List tmp = (List)lists.get(0);

               for (List list6 : lists) {
                  if (list6.size() > tmp.size()) {
                     tmp = list6;
                  }
               }

               if (tmp.size() > 1) {
                  PlayerData playerData4 = (PlayerData)tmp.remove(tmp.size() - 1);
                  playerData4.prefTeam = -1;
                  list5.add(playerData4);
               }
            }
         }

         lists.removeIf(List::isEmpty);
         return lists;
      }
   }

   private boolean colorUsed(List<List<PlayerData>> lists, int n, int n2) {
      for (int n3 = 0; n3 < n; n3++) {
         if (!((List)lists.get(n3)).isEmpty() && ((PlayerData)((List)lists.get(n3)).get(0)).team == n2) {
            return true;
         }
      }

      return false;
   }

   private int firstFreeColor(List<List<PlayerData>> lists, int n) {
      for (int n2 = 0; n2 < BED_COLORS.length; n2++) {
         if (!this.colorUsed(lists, n, n2)) {
            return n2;
         }
      }

      return n % BED_COLORS.length;
   }

   private void releaseBeds(PlayerData playerData) {
      boolean flag = false;

      for (PlayerData playerData2 : this.players.values()) {
         if (playerData2 != playerData && playerData2.team >= 0 && playerData2.team == playerData.team && this.teamSize > 1) {
            flag = true;
         }
      }

      if (flag) {
         playerData.beds = new ArrayList<>();
      } else {
         for (PlayerData.BedRecord bedRecord : new ArrayList<>(playerData.beds)) {
            this.removeBed(bedRecord, null, null, true);
         }
      }
   }

   public int maxPlayersSetting() {
      return this.maxPlayers;
   }

   public boolean isGrace() {
      return this.state == Game.State.RUNNING && this.elapsed < this.graceSeconds;
   }

   public boolean bedsGone() {
      return this.bedsGone;
   }

   public PlayerData get(Player player) {
      return this.players.get(player.getUniqueId());
   }

   public boolean inGame(Player player) {
      return this.players.containsKey(player.getUniqueId());
   }

   private boolean autoEligible(Player player) {
      if (this.autoJoin && this.arena != null) {
         if (this.optedOut.contains(player.getUniqueId())) {
            return false;
         } else if (player.hasPermission("bedrockbox.bypass")) {
            return false;
         } else if (this.inGame(player) || this.spectators.containsKey(player.getUniqueId())) {
            return false;
         } else if (player.getGameMode() == GameMode.CREATIVE) {
            return false;
         } else {
            return this.plugin.lobby().enabled()
               ? this.plugin.lobby().isLobby(player.getLocation())
               : this.autoJoinWorld == null || this.autoJoinWorld.isEmpty() || player.getWorld().getName().equalsIgnoreCase(this.autoJoinWorld);
         }
      } else {
         return false;
      }
   }

   public void autoJoin(Player player) {
      if (this.autoEligible(player)) {
         if (this.state == Game.State.WAITING || this.state == Game.State.STARTING) {
            if (this.players.size() < this.effectiveMax()) {
               this.join(player);
               player.sendMessage("§8[§6Коробка§8] §7§7Ты в очереди. Выйти: §e/bb leave");
            } else if (this.autoSpectate) {
               player.sendMessage("§8[§6Коробка§8] §7§7Мест нет - подожди следующую игру.");
            }
         } else if (this.state == Game.State.RUNNING && this.autoSpectate) {
            this.spectate(player);
         }
      }
   }

   public void autoJoinAll() {
      if (this.autoJoin) {
         for (Player player : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            this.autoJoin(player);
         }
      }
   }

   public void setOptOut(Player player, boolean flag) {
      if (flag) {
         this.optedOut.add(player.getUniqueId());
      } else {
         this.optedOut.remove(player.getUniqueId());
      }
   }

   public boolean isAutoJoin() {
      return this.autoJoin;
   }

   public void join(Player player) {
      this.optedOut.remove(player.getUniqueId());
      if (this.arena == null) {
         player.sendMessage("§8[§6Коробка§8] §7§cАрена не настроена. Админ: §e/bb setup");
      } else if (this.inGame(player)) {
         player.sendMessage("§8[§6Коробка§8] §7Ты уже в игре.");
      } else if (this.state != Game.State.WAITING && this.state != Game.State.STARTING) {
         player.sendMessage("§8[§6Коробка§8] §7§cИгра уже идёт или арена восстанавливается. Подожди.");
      } else if (this.players.size() >= this.effectiveMax()) {
         player.sendMessage("§8[§6Коробка§8] §7§cМест нет (" + this.effectiveMax() + ").");
      } else {
         this.players.put(player.getUniqueId(), new PlayerData(player.getUniqueId(), player.getName(), player.getLocation().clone()));
         this.broadcast("§e" + player.getName() + " §7зашёл в коробку §8(" + this.players.size() + "/" + this.effectiveMax() + ")");
         if (this.lobbyBar != null) {
            this.lobbyBar.addPlayer(player);
         }

         if (this.state == Game.State.WAITING) {
            this.startCountdown(this.autoStart, false);
         } else if (this.players.size() >= this.effectiveMax()) {
            this.shortenCountdown(this.countdown);
         }

         if (this.players.size() < this.minNeeded()) {
            player.sendMessage("§8[§6Коробка§8] §7Нужно минимум §e" + this.minNeeded() + " §7игроков, игра начнётся сама.");
         }
      }
   }

   public boolean onDisconnect(Player player) {
      PlayerData playerData = this.get(player);
      if (playerData != null && this.state == Game.State.RUNNING && playerData.alive && this.rejoinSeconds > 0) {
         this.offline.add(playerData.id);
         if (this.bar != null) {
            this.bar.removePlayer(player);
         }

         this.broadcast("§e" + playerData.name + " §7вылетел - ждём §e" + this.rejoinSeconds + " сек§7.");
         UUID uUID = playerData.id;
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (this.offline.remove(uUID)) {
               PlayerData playerData2 = this.players.remove(uUID);
               if (playerData2 != null) {
                  this.releaseBeds(playerData2);
                  this.dropped.add(uUID);
                  this.broadcast("§e" + playerData2.name + " §7не вернулся и выбыл.");
                  if (this.state == Game.State.RUNNING) {
                     this.checkWin();
                  }
               }
            }
         }, (long)this.rejoinSeconds * 20L);
         return true;
      } else {
         return false;
      }
   }

   public boolean onReconnect(Player player) {
      if (this.dropped.remove(player.getUniqueId())) {
         player.getInventory().clear();
         player.getInventory().setArmorContents(new ItemStack[4]);
         player.sendMessage("§8[§6Коробка§8] §7§7Ты не успел вернуться в битву, выбыл.");
         if (this.arena != null && this.arena.isNear(player.getLocation(), 2)) {
            player.setGameMode(GameMode.SURVIVAL);
            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
         }

         return false;
      } else if (!this.offline.remove(player.getUniqueId())) {
         return false;
      } else {
         PlayerData playerData = this.get(player);
         if (playerData != null && this.state == Game.State.RUNNING) {
            if (this.bar != null) {
               this.bar.addPlayer(player);
            }

            player.setGameMode(GameMode.SURVIVAL);
            if (!this.arena.isInside(player.getLocation().getBlock())) {
               Location location = playerData.hasBed() ? playerData.respawnPoint() : playerData.home;
               if (location != null) {
                  player.teleport(location);
               }
            }

            this.broadcast("§a" + playerData.name + " §7вернулся в битву.");
            Bukkit.getScheduler().runTaskLater(this.plugin, this::enforceNames, 5L);
            return true;
         } else {
            return false;
         }
      }
   }

   public void leave(Player player, boolean flag) {
      if (this.spectators.containsKey(player.getUniqueId())) {
         this.stopSpectating(player, this.spectators.remove(player.getUniqueId()));
         if (!flag) {
            player.sendMessage("§8[§6Коробка§8] §7Ты перестал наблюдать.");
         }
      } else {
         PlayerData playerData = this.players.remove(player.getUniqueId());
         if (playerData == null) {
            if (!flag) {
               player.sendMessage("§8[§6Коробка§8] §7Ты не в игре.");
            }
         } else {
            this.releaseBeds(playerData);
            if (this.state != Game.State.RUNNING && this.state != Game.State.ENDING) {
               player.sendMessage("§8[§6Коробка§8] §7Ты вышел из очереди.");
            } else {
               this.restore(player, playerData);
            }

            this.broadcast("§e" + player.getName() + " §7покинул коробку.");
            if (this.lobbyBar != null) {
               this.lobbyBar.removePlayer(player);
            }

            if (this.state == Game.State.STARTING && this.players.isEmpty()) {
               this.cancelCountdown();
            }

            if (this.state == Game.State.RUNNING) {
               this.checkWin();
            }
         }
      }
   }

   public void forceStart() {
      if (this.state == Game.State.STARTING) {
         this.forcedStart = true;
         this.shortenCountdown(5);
      } else {
         this.startCountdown(5, true);
      }
   }

   public void startCountdown(int n, boolean flag) {
      if (this.state == Game.State.WAITING) {
         if (!this.players.isEmpty()) {
            this.state = Game.State.STARTING;
            this.forcedStart = flag;
            this.countdownLeft = n;
            this.countdownTotal = n;
            this.lobbyBar = Bukkit.createBossBar("§eСтарт через " + fmt(n), BarColor.GREEN, BarStyle.SOLID);

            for (Player player2 : this.online()) {
               this.lobbyBar.addPlayer(player2);
            }

            this.countdownTask = new BukkitRunnable() {
               @Override
               public void run() {
                  if (Game.this.players.isEmpty()) {
                     Game.this.cancelCountdown();
                  } else {
                     int n = Game.this.countdownLeft;
                     if (n <= 0) {
                        if (!Game.this.forcedStart && Game.this.players.size() < Game.this.minNeeded()) {
                           Game.this.broadcast(
                              "§cНе хватает игроков ("
                                 + Game.this.players.size()
                                 + "/"
                                 + Game.this.minNeeded()
                                 + "). Ждём ещё "
                                 + Game.fmt(Game.this.autoStart)
                                 + "."
                           );
                           Game.this.countdownLeft = Game.this.autoStart;
                           Game.this.countdownTotal = Game.this.autoStart;
                        } else {
                           this.cancel();
                           Game.this.countdownTask = null;
                           if (Game.this.lobbyBar != null) {
                              Game.this.lobbyBar.removeAll();
                              Game.this.lobbyBar = null;
                           }

                           Game.this.begin();
                        }
                     } else {
                        if (Game.this.lobbyBar != null) {
                           Game.this.lobbyBar
                              .setTitle(
                                 "§eСтарт через §f"
                                    + Game.fmt(n)
                                    + " §8| §7игроков: §f"
                                    + Game.this.players.size()
                                    + "/"
                                    + Game.this.effectiveMax()
                                    + (Game.this.players.size() < Game.this.minNeeded() ? " §c(нужно " + Game.this.minNeeded() + ")" : "")
                              );
                           Game.this.lobbyBar.setProgress(Game.clamp((double)n / (double)Game.this.countdownTotal));
                        }

                        if (n <= 5 || n == 10 || n == 30 || n == 60 || n == 90) {
                           for (Player player : Game.this.online()) {
                              if (n <= 10) {
                                 player.sendTitle("§6" + n, "§7до начала битвы", 0, 25, 5);
                              }

                              player.playSound(player.getLocation(), "block.note_block.hat", 1.0F, 1.0F);
                           }

                           if (n > 5) {
                              Game.this.broadcast("Битва начнётся через §e" + Game.fmt(n));
                           }
                        }

                        Game.this.countdownLeft--;
                     }
                  }
               }
            };
            this.countdownTask.runTaskTimer(this.plugin, 0L, 20L);
            this.broadcast("Игра начнётся сама через §e" + fmt(n) + "§7. Зовите друзей: §e/bb join");
         }
      }
   }

   private void shortenCountdown(int tmp) {
      if (this.state == Game.State.STARTING && this.countdownLeft > tmp) {
         this.countdownLeft = tmp;
         this.broadcast("§aВсе места заняты - старт через " + tmp + " сек!");
      }
   }

   private void cancelCountdown() {
      if (this.countdownTask != null) {
         this.countdownTask.cancel();
         this.countdownTask = null;
      }

      if (this.lobbyBar != null) {
         this.lobbyBar.removeAll();
         this.lobbyBar = null;
      }

      if (this.state == Game.State.STARTING) {
         this.state = Game.State.WAITING;
      }
   }

   private void begin() {
      this.state = Game.State.RUNNING;
      this.elapsed = 0;
      this.bedsGone = false;
      this.setDirty(true);

      for (PlayerData playerData : new ArrayList<>(this.players.values())) {
         if (Bukkit.getPlayer(playerData.id) == null) {
            this.players.remove(playerData.id);
         }
      }

      this.startedWith = this.players.size();

      try {
         this.prevImmediateRespawn = this.arena.world.getGameRuleValue(GameRule.DO_IMMEDIATE_RESPAWN);
         this.arena.world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
      } catch (Throwable t2) {
      }

      this.plugin.antiCheat().cloak().clear();
      List list4 = this.buildTeams(new ArrayList<>(this.players.values()));
      this.startedTeams = list4.size();
      List list5 = this.arena.rooms(list4.size());
      ArrayList list = new ArrayList();

      for (int n = 0; n < BED_COLORS.length; n++) {
         list.add(n);
      }

      if (!this.teams()) {
         Collections.shuffle(list);
      }

      Collections.shuffle(list5);

      for (int n3 = 0; n3 < list4.size(); n3++) {
         List list2 = (List)list4.get(n3);
         int n2 = this.teams() ? -1 : (Integer)list.get(n3);
         if (this.teams()) {
            for (PlayerData playerData2 : (Iterable<PlayerData>) list2) {
               if (playerData2.prefTeam >= 0) {
                  n2 = playerData2.prefTeam;
                  break;
               }
            }
         }

         if (n2 < 0 || this.colorUsed(list4, n3, n2)) {
            n2 = this.firstFreeColor(list4, n3);
         }

         int[] arr2 = (int[])list5.get(n3);
         this.arena.carveRoom(arr2);
         PlayerData playerData5 = (PlayerData)list2.get(0);
         this.plugin.antiCheat().cloak().add(playerData5.id, this.arena.world, arr2[0] - 2, arr2[1] - 1, arr2[2] - 2, arr2[0] + 2, arr2[1] + 4, arr2[2] + 2);
         Location location = new Location(this.arena.world, (double)arr2[0] + 0.5, (double)arr2[1], (double)arr2[2] + 1.5);
         location.setYaw(180.0F);
         ArrayList list3 = new ArrayList();

         for (PlayerData playerData3 : (Iterable<PlayerData>) list2) {
            playerData3.team = n2;
            playerData3.beds = list3;
         }

         this.placeBed(playerData5, this.arena.world.getBlockAt(arr2[0] - 1, arr2[1], arr2[2]), BlockFace.EAST, BED_COLORS[n2 % BED_COLORS.length], location);
         this.spawnTrader(new Location(this.arena.world, (double)arr2[0] + 1.5, (double)arr2[1], (double)arr2[2] - 1.5));

         for (PlayerData playerData7 : (Iterable<PlayerData>) list2) {
            Player player = Bukkit.getPlayer(playerData7.id);
            this.colors.put(playerData7.id, COLOR_CODES[n2 % COLOR_CODES.length]);
            playerData7.home = location;
            if (player != null) {
               player.teleport(location);
               this.preparePlayer(player, true);
               this.intro(player, location);
               if (this.teams()) {
                  StringBuilder stringBuilder = new StringBuilder();

                  for (PlayerData playerData4 : (Iterable<PlayerData>) list2) {
                     if (playerData4 != playerData7) {
                        stringBuilder.append(playerData4.name).append(" ");
                     }
                  }

                  player.sendTitle(
                     "§6§lКОМАНДА " + this.teamName(n2).toUpperCase(),
                     stringBuilder.length() == 0 ? "§7Ты один в команде" : "§7С тобой: §f" + stringBuilder.toString().trim(),
                     5,
                     70,
                     10
                  );
               } else {
                  player.sendTitle("§6§lБИТВА В КОРОБКЕ", "§7Копай, торгуй, ломай чужие кровати!", 5, 60, 10);
               }
            }
         }
      }

      this.secretFound = false;
      ArrayList list6 = new ArrayList(list5);

      for (int n4 = 0; n4 < this.secretCount; n4++) {
         int[] arr = this.arena.secretSpot(list6);
         this.arena.carveSecret(arr);
         this.plugin.antiCheat().cloak().add(null, this.arena.world, arr[0] - 2, arr[1] - 1, arr[2] - 2, arr[0] + 2, arr[1] + 3, arr[2] + 2);
         this.spawnSecretTrader(new Location(this.arena.world, (double)arr[0] + 0.5, (double)arr[1], (double)arr[2] + 0.5));
         list6.add(arr);
      }

      if (this.hideCoords) {
         try {
            this.prevReducedDebug = this.arena.world.getGameRuleValue(GameRule.REDUCED_DEBUG_INFO);
            this.arena.world.setGameRule(GameRule.REDUCED_DEBUG_INFO, true);
         } catch (Throwable t) {
         }
      }

      this.bar = Bukkit.createBossBar("§eМирное время", BarColor.YELLOW, BarStyle.SOLID);

      for (Player player2 : this.online()) {
         this.bar.addPlayer(player2);
      }

      this.broadcast("§aИгра началась! §7PvP включится через §e" + this.graceSeconds + " §7сек.");
      if (this.teams()) {
         this.broadcast("§7Режим: §eкоманды по " + this.teamSize + "§7, свои не бьются.");

         for (List list7 : (Iterable<List>) list4) {
            StringBuilder stringBuilder2 = new StringBuilder();

            for (PlayerData playerData6 : (Iterable<PlayerData>) list7) {
               stringBuilder2.append(playerData6.name).append(" ");
            }

            this.broadcast(this.teamName(((PlayerData)list7.get(0)).team) + "§7: §f" + stringBuilder2.toString().trim());
         }
      }

      this.broadcast("§7Сломают кровать - больше не возродишься.");
      this.broadcast("§7В комнате торговец, ПКМ по нему.");
      if (this.secretCount > 0) {
         this.broadcast("§5Где-то в камне спрятан тайный торговец.");
      }

      Bukkit.getScheduler().runTaskLater(this.plugin, this::enforceNames, 5L);
      this.tickTask = new BukkitRunnable() {
         @Override
         public void run() {
            Game.this.tick();
         }
      };
      this.tickTask.runTaskTimer(this.plugin, 20L, 20L);
      this.espTask = new BukkitRunnable() {
         @Override
         public void run() {
            Game.this.updateEsp();
         }
      };
      this.espTask.runTaskTimer(this.plugin, 10L, 4L);
      this.checkWin();
   }

   private void tick() {
      if (this.state == Game.State.RUNNING) {
         this.elapsed++;
         if (this.elapsed == this.graceSeconds && this.graceSeconds > 0) {
            this.broadcast("§c§lPvP ВКЛЮЧЕНО!");

            for (Player player : this.online()) {
               player.sendTitle("§4§l⚔ PvP ⚔", "§cМирное время кончилось", 5, 40, 15);
               player.playSound(player.getLocation(), "entity.ender_dragon.growl", 0.6F, 1.0F);
               player.playSound(player.getLocation(), "entity.lightning_bolt.thunder", 0.8F, 0.9F);
            }
         }

         if (this.bedDestroySeconds > 0 && this.elapsed == this.bedDestroySeconds && !this.bedsGone) {
            this.destroyAllBeds();
         }

         if (this.bar != null) {
            if (this.elapsed < this.graceSeconds) {
               this.bar.setTitle("§eМирное время: §f" + fmt(this.graceSeconds - this.elapsed));
               this.bar.setColor(BarColor.YELLOW);
               this.bar.setProgress(clamp((double)(this.graceSeconds - this.elapsed) / (double)this.graceSeconds));
            } else if (this.bedDestroySeconds > 0 && !this.bedsGone) {
               this.bar.setTitle("§cКровати исчезнут через §f" + fmt(this.bedDestroySeconds - this.elapsed) + " §8| §7живых: §f" + this.aliveCount());
               this.bar.setColor(BarColor.RED);
               this.bar.setProgress(clamp((double)(this.bedDestroySeconds - this.elapsed) / (double)this.bedDestroySeconds));
            } else if (this.bedsGone) {
               this.bar.setTitle("§4§lБЕЗ КРОВАТЕЙ §8| §7живых: §f" + this.aliveCount());
               this.bar.setColor(BarColor.PURPLE);
               this.bar.setProgress(1.0);
            } else {
               this.bar.setTitle("§cБитва идёт §8| §7живых: §f" + this.aliveCount() + " §8| §7время: §f" + fmt(this.elapsed));
               this.bar.setColor(BarColor.RED);
               this.bar.setProgress(1.0);
            }

            for (Player player2 : this.online()) {
               this.bar.addPlayer(player2);
            }

            for (Player player3 : this.onlineSpectators()) {
               this.bar.addPlayer(player3);
            }
         }

         this.watchdog();
         this.enforceNames();
      }
   }

   private void watchdog() {
      for (PlayerData playerData : this.players.values()) {
         Player player = Bukkit.getPlayer(playerData.id);
         if (player != null && !player.isDead() && !this.plugin.cinematic().isWatching(player)) {
            if (playerData.alive) {
               if (player.getGameMode() != GameMode.SURVIVAL) {
                  player.setGameMode(GameMode.SURVIVAL);
                  player.sendMessage("§8[§6Коробка§8] §7§cПроверка: живой игрок может быть только в режиме выживания.");
                  this.warnAdmins(player.getName() + " был не в выживании - исправлено.");
               }

               if (!this.arena.isInside(player.getLocation().getBlock())) {
                  Location location = playerData.hasBed() ? playerData.respawnPoint() : playerData.home;
                  this.names.release(player);
                  if (location != null) {
                     player.teleport(location);
                  }

                  player.sendMessage("§8[§6Коробка§8] §7§cПроверка: из коробки выходить нельзя!");
                  this.warnAdmins(player.getName() + " оказался вне коробки - возвращён.");
               }
            } else {
               this.enforceSpectator(player);
            }
         }
      }

      for (Player player2 : this.onlineSpectators()) {
         this.enforceSpectator(player2);
      }
   }

   private void enforceSpectator(Player player) {
      if (!this.plugin.cinematic().isWatching(player)) {
         if (player.getGameMode() != GameMode.SPECTATOR) {
            player.getInventory().clear();
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage("§8[§6Коробка§8] §7§7Ты наблюдатель - режим наблюдателя возвращён.");
         }

         if (!this.arena.isNear(player.getLocation(), 6)) {
            player.teleport(this.arena.center());
            player.sendMessage("§8[§6Коробка§8] §7§7Наблюдатели не улетают дальше коробки.");
         }
      }
   }

   private void warnAdmins(String str) {
      for (Player player : Bukkit.getOnlinePlayers()) {
         if (player.hasPermission("bedrockbox.admin")) {
            player.sendMessage("§8[§6Коробка§8] §7§8[проверка] §7" + str);
         }
      }
   }

   public boolean isOutsideSpectator(Player player) {
      return this.spectators.containsKey(player.getUniqueId());
   }

   public boolean isWatcher(Player player) {
      if (this.spectators.containsKey(player.getUniqueId())) {
         return true;
      } else {
         PlayerData playerData = this.get(player);
         return playerData != null && !playerData.alive;
      }
   }

   public void spectate(Player player) {
      if (this.arena != null && this.state == Game.State.RUNNING) {
         if (!this.inGame(player) && !this.spectators.containsKey(player.getUniqueId())) {
            this.spectators.put(player.getUniqueId(), player.getLocation().clone());
            player.getInventory().clear();
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(this.arena.center());
            if (this.bar != null) {
               this.bar.addPlayer(player);
            }

            player.sendMessage("§8[§6Коробка§8] §7§aТы наблюдаешь. §71-9 - к игрокам, §e/bb leave §7- выйти.");
         } else {
            player.sendMessage("§8[§6Коробка§8] §7Ты уже в коробке.");
         }
      } else {
         player.sendMessage("§8[§6Коробка§8] §7§cСейчас нечего смотреть - игра не идёт.");
      }
   }

   private void stopSpectating(Player player, Location location) {
      this.showName(player);
      if (this.bar != null) {
         this.bar.removePlayer(player);
      }

      player.setGameMode(GameMode.SURVIVAL);
      if (this.plugin.lobby().enabled()) {
         this.plugin.lobby().send(player);
      } else {
         if (location == null || location.getWorld() == null) {
            location = Bukkit.getWorlds().get(0).getSpawnLocation();
         }

         player.teleport(location);
      }
   }

   private List<Player> onlineSpectators() {
      ArrayList list = new ArrayList();

      for (UUID uUID : this.spectators.keySet()) {
         Player player = Bukkit.getPlayer(uUID);
         if (player != null) {
            list.add(player);
         }
      }

      return list;
   }

   public NameHider names() {
      return this.names;
   }

   private void enforceNames() {
      if (this.hideNametags && this.state == Game.State.RUNNING) {
         ArrayList list = new ArrayList();

         for (PlayerData playerData : this.players.values()) {
            Player player = Bukkit.getPlayer(playerData.id);
            if (player != null && playerData.alive && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR) {
               list.add(player);
            }
         }

         ArrayList list2 = new ArrayList<>(this.online());
         list2.addAll(this.onlineSpectators());

         for (Player player2 : (Iterable<Player>) list2) {
            this.names.useGameBoard(player2);
         }

         this.names.enforce(list, list2);
         this.updateSidebar();
      }
   }

   private void updateSidebar() {
      ArrayList list = new ArrayList();
      list.add("§7Живых: §f" + this.aliveCount());
      if (this.teams()) {
         list.add("§7Команд: §f" + this.aliveTeams());
      }

      list.add("§7Время: §f" + fmt(this.elapsed));
      list.add(this.elapsed < this.graceSeconds ? "§7PvP через: §e" + fmt(this.graceSeconds - this.elapsed) : "§7PvP: §cвключено");
      if (this.bedsGone) {
         list.add("§4Без кроватей!");
      } else if (this.bedDestroySeconds > 0) {
         list.add("§7Кровати до: §f" + fmt(this.bedDestroySeconds - this.elapsed));
      } else {
         list.add("§7Кровати: §aне исчезают");
      }

      list.add("§8 ");
      list.add(this.secretFound ? "§5Тайный торговец найден" : "§5Тайный торговец где-то в камне");
      this.names.sidebar(list);
   }

   public List<Player> alivePlayers() {
      ArrayList list = new ArrayList();

      for (PlayerData playerData : this.players.values()) {
         Player player = Bukkit.getPlayer(playerData.id);
         if (player != null && playerData.alive && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR) {
            list.add(player);
         }
      }

      return list;
   }

   public void damageTool(Player player, ItemStack itemStack) {
      if (itemStack != null && itemStack.getType().getMaxDurability() > 0) {
         ItemMeta itemMeta = itemStack.getItemMeta();
         if (itemMeta instanceof Damageable damageable && !itemMeta.isUnbreakable()) {
            int n = 0;

            try {
               Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("unbreaking"));
               if (enchantment != null) {
                  n = itemStack.getEnchantmentLevel(enchantment);
               }
            } catch (Throwable t) {
            }

            if (n > 0 && this.toolRandom.nextInt(n + 1) != 0) {
               return;
            }

            int n2 = damageable.getDamage() + 1;
            if (n2 >= itemStack.getType().getMaxDurability()) {
               player.getInventory().setItemInMainHand(null);
               player.playSound(player.getLocation(), "entity.item.break", 1.0F, 1.0F);
               return;
            }

            damageable.setDamage(n2);
            itemStack.setItemMeta(itemMeta);
            return;
         }
      }
   }

   private void updateEsp() {
      if (this.state == Game.State.RUNNING) {
         ArrayList list = new ArrayList();

         for (PlayerData playerData : this.players.values()) {
            Player player = Bukkit.getPlayer(playerData.id);
            if (player != null && playerData.alive && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR) {
               list.add(player);
            }
         }

         ArrayList list2 = new ArrayList(list);

         for (Entity entity : this.secretTraders) {
            if (entity.isValid()) {
               list2.add(entity);
            }
         }

         for (Entity entity2 : this.traders) {
            if (entity2.isValid()) {
               list2.add(entity2);
            }
         }

         this.plugin.antiCheat().update(list, list2);
         this.plugin.antiCheat().cloak().update(list);
      }
   }

   private void showName(Player player) {
      if (this.hideEverywhere) {
         this.names.release(player);
      } else {
         this.names.show(player);
      }
   }

   public boolean hideEverywhere() {
      return this.hideEverywhere;
   }

   public boolean hideNametags() {
      return this.hideNametags;
   }

   public void enforceEverywhere() {
      if (this.hideEverywhere) {
         ArrayList list = new ArrayList<>(Bukkit.getOnlinePlayers());
         this.names.enforce(list, list, false);
      }
   }

   public void showEveryone() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         if (!this.inGame(player) || this.state != Game.State.RUNNING) {
            this.names.show(player);
         }
      }
   }

   public boolean giveMined(Player player2, Block block, ItemStack itemStack) {
      Material material = this.drops.get(block.getType());
      if (material == null && this.fillerBlocks.contains(block.getType())) {
         material = Material.COBBLESTONE;
      }

      Collection<ItemStack> itemStacks;
      if (material == null) {
         itemStacks = block.getDrops(itemStack);
      } else {
         boolean flag = itemStack != null && itemStack.getType().name().endsWith("_PICKAXE");
         if (!block.isPreferredTool(itemStack) && (!this.anyPickaxe || !flag)) {
            return false;
         }

         itemStacks = new ArrayList<ItemStack>();
         itemStacks.add(new ItemStack(material, 1));
      }

      for (ItemStack itemStack2 : itemStacks) {
         for (ItemStack itemStack3 : player2.getInventory().addItem(new ItemStack[]{itemStack2}).values()) {
            player2.getWorld().dropItem(player2.getLocation(), itemStack3);
         }
      }

      if (material == Material.EMERALD) {
         player2.playSound(player2.getLocation(), "entity.experience_orb.pickup", 0.5F, 1.8F);
      }

      if (this.rareOres.contains(block.getType())) {
         player2.playSound(player2.getLocation(), "entity.player.levelup", 0.8F, 1.6F);
         player2.sendTitle("", "§b§l+1 " + (material == null ? "редкость" : Shop.currencyName(material).replace("алмазов", "алмаз")), 0, 25, 5);
      }

      return true;
   }

   private void spawnTrader(Location location) {
      location.setYaw(0.0F);
      Villager villager = this.arena.world.spawn(location, Villager.class);
      villager.setAI(false);
      villager.setInvulnerable(true);
      villager.setSilent(true);
      villager.setRemoveWhenFarAway(false);
      villager.setCustomName("§6§lТорговец §7(ПКМ)");
      villager.setCustomNameVisible(false);
      this.traders.add(villager);
   }

   public boolean isTrader(Entity entity) {
      for (Entity entity2 : this.traders) {
         if (entity2.getUniqueId().equals(entity.getUniqueId())) {
            return true;
         }
      }

      return this.isSecretTrader(entity);
   }

   public boolean isSecretTrader(Entity entity) {
      for (Entity entity2 : this.secretTraders) {
         if (entity2.getUniqueId().equals(entity.getUniqueId())) {
            return true;
         }
      }

      return false;
   }

   private void spawnSecretTrader(Location location) {
      Villager villager = this.arena.world.spawn(location, Villager.class);
      villager.setAI(false);
      villager.setInvulnerable(true);
      villager.setSilent(true);
      villager.setRemoveWhenFarAway(false);
      villager.setCustomNameVisible(false);
      this.secretTraders.add(villager);
   }

   public void onSecretFound(Player player) {
      if (!this.secretFound) {
         this.secretFound = true;
         this.broadcast("§5§lКто-то нашёл тайного торговца!");
         player.sendTitle("§5§lТАЙНЫЙ ТОРГОВЕЦ", "§7Ты нашёл его первым", 5, 50, 10);
         this.updateSidebar();
      }
   }

   private void removeTraders() {
      for (Entity entity : this.traders) {
         entity.remove();
      }

      this.traders.clear();

      for (Entity entity2 : this.secretTraders) {
         entity2.remove();
      }

      this.secretTraders.clear();
      if (this.prevReducedDebug != null && this.arena != null) {
         try {
            this.arena.world.setGameRule(GameRule.REDUCED_DEBUG_INFO, this.prevReducedDebug);
         } catch (Throwable t) {
         }

         this.prevReducedDebug = null;
      }

      if (this.prevImmediateRespawn != null && this.arena != null) {
         try {
            this.arena.world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, this.prevImmediateRespawn);
         } catch (Throwable t2) {
         }

         this.prevImmediateRespawn = null;
      }

      this.tierWarned.clear();
   }

   public PlayerData byId(UUID uUID) {
      return this.players.get(uUID);
   }

   public PlayerData.BedRecord nearestEnemyBed(Player player) {
      PlayerData.BedRecord bedRecord = null;
      double d = Double.MAX_VALUE;

      for (PlayerData playerData : this.players.values()) {
         if (!this.sameTeam(playerData.id, player.getUniqueId())) {
            for (PlayerData.BedRecord bedRecord2 : playerData.beds) {
               double d2 = bedRecord2.foot.getLocation().distance(player.getLocation());
               if (d2 < d) {
                  d = d2;
                  bedRecord = bedRecord2;
               }
            }
         }
      }

      return bedRecord;
   }

   public static String key(Block block) {
      return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
   }

   public PlayerData.BedRecord bedAt(Block block) {
      return this.bedsByBlock.get(key(block));
   }

   public void placeBed(PlayerData playerData, Block block, BlockFace blockFace, Material material, Location location) {
      Block block2 = block.getRelative(blockFace);
      BlockData blockData = material.createBlockData();
      BlockData blockData2 = material.createBlockData();
      if (blockData instanceof Bed bed) {
         bed.setFacing(blockFace);
         bed.setPart(Part.FOOT);
      }

      if (blockData2 instanceof Bed bed2) {
         bed2.setFacing(blockFace);
         bed2.setPart(Part.HEAD);
      }

      block.setBlockData(blockData, false);
      block2.setBlockData(blockData2, false);
      this.registerBed(playerData, block, block2, location);
   }

   public void registerBed(PlayerData playerData, Block block, Block block2, Location location) {
      PlayerData.BedRecord bedRecord = new PlayerData.BedRecord(playerData.id, block, block2, location);
      playerData.beds.add(bedRecord);
      this.bedsByBlock.put(key(block), bedRecord);
      this.bedsByBlock.put(key(block2), bedRecord);
   }

   public void removeBed(PlayerData.BedRecord bedRecord, String str, Block block, boolean flag) {
      this.bedsByBlock.remove(key(bedRecord.foot));
      this.bedsByBlock.remove(key(bedRecord.head));
      if (block == null || !key(block).equals(key(bedRecord.foot))) {
         bedRecord.foot.setType(Material.AIR, false);
      }

      if (block == null || !key(block).equals(key(bedRecord.head))) {
         bedRecord.head.setType(Material.AIR, false);
      }

      PlayerData playerData = this.players.get(bedRecord.owner);
      if (playerData == null || !playerData.beds.contains(bedRecord)) {
         for (PlayerData playerData2 : this.players.values()) {
            if (playerData2.beds.contains(bedRecord)) {
               playerData = playerData2;
               break;
            }
         }
      }

      if (playerData != null) {
         playerData.beds.remove(bedRecord);
         if (!flag) {
            String str2 = str == null ? "" : " §7(сломал §e" + str + "§7)";
            String str3 = this.teams() && playerData.team >= 0 ? "команды " + this.teamName(playerData.team) : "игрока " + this.name(playerData);
            if (playerData.hasBed()) {
               this.broadcast("§cКровать " + str3 + " §cразрушена" + str2 + "§c, но есть запасная!");
            } else {
               this.broadcast("§c§lКРОВАТЬ " + str3.toUpperCase() + " §c§lРАЗРУШЕНА!" + str2);

               for (PlayerData playerData3 : this.players.values()) {
                  if (playerData3 == playerData || this.teams() && playerData3.team == playerData.team) {
                     Player player = Bukkit.getPlayer(playerData3.id);
                     if (player != null) {
                        player.sendTitle("§c§lКРОВАТЬ РАЗРУШЕНА", "§7Больше не возродишься!", 5, 50, 10);
                        player.playSound(player.getLocation(), "entity.wither.death", 0.7F, 1.2F);
                     }
                  }
               }
            }
         }
      }
   }

   private void destroyAllBeds() {
      this.bedsGone = true;

      for (PlayerData playerData : this.players.values()) {
         for (PlayerData.BedRecord bedRecord : new ArrayList<>(playerData.beds)) {
            this.removeBed(bedRecord, null, null, true);
         }
      }

      for (Player player : this.online()) {
         player.sendTitle("§4§lКРОВАТИ ИСЧЕЗЛИ", "§7Последний бой - без возрождений!", 5, 60, 10);
         player.playSound(player.getLocation(), "entity.lightning_bolt.thunder", 1.0F, 0.8F);
      }

      this.broadcast("§4§lВсе кровати уничтожены! Новые ставить нельзя.");
   }

   public void onDeath(Player player, Player player2) {
      PlayerData playerData = this.get(player);
      if (playerData != null && this.state == Game.State.RUNNING) {
         if (player2 != null) {
            PlayerData playerData2 = this.get(player2);
            if (playerData2 != null) {
               playerData2.kills++;
            }
         }

         if (!playerData.hasBed()) {
            playerData.alive = false;
            this.plugin.antiCheat().reveal(player);
            this.plugin.antiCheat().cloak().reveal(player);
            this.plugin.antiCheat().forget(player);
            this.broadcast("§c☠ " + this.name(playerData) + " §cвыбыл!" + (player2 != null ? " §7Убийца: §e" + player2.getName() : ""));
            this.checkWin();
         }
      }
   }

   public Location onRespawn(Player player) {
      PlayerData playerData = this.get(player);
      if (playerData == null) {
         return null;
      } else if (this.state == Game.State.RUNNING && playerData.alive && playerData.hasBed()) {
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> this.preparePlayer(player, false), 1L);
         Bukkit.getScheduler().runTaskLater(this.plugin, this::enforceNames, 3L);
         return playerData.respawnPoint();
      } else if (this.state == Game.State.RUNNING) {
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            player.getInventory().clear();
            player.setGameMode(GameMode.SPECTATOR);
            player.sendTitle("§c§lТЫ ВЫБЫЛ", "§7Смотри, кто победит", 5, 50, 10);
         }, 1L);
         return this.arena.center();
      } else {
         return playerData.returnLocation;
      }
   }

   private int aliveCount() {
      int n = 0;

      for (PlayerData playerData : this.players.values()) {
         if (playerData.alive) {
            n++;
         }
      }

      return n;
   }

   private int aliveTeams() {
      if (!this.teams()) {
         return this.aliveCount();
      } else {
         HashSet list = new HashSet();

         for (PlayerData playerData : this.players.values()) {
            if (playerData.alive) {
               list.add(playerData.team);
            }
         }

         return list.size();
      }
   }

   public void checkWin() {
      if (this.state == Game.State.RUNNING) {
         int n = this.aliveTeams();
         int n2 = this.teams() ? this.startedTeams : this.startedWith;
         if (n == 0 || n2 > 1 && n == 1) {
            PlayerData playerData = null;

            for (PlayerData playerData2 : this.players.values()) {
               if (playerData2.alive) {
                  playerData = playerData2;
               }
            }

            this.end(playerData);
         }
      }
   }

   public void end(PlayerData playerData) {
      if (this.state != Game.State.ENDING) {
         if (this.state == Game.State.RESETTING) {
            if (this.arena == null || !this.arena.isBuilding()) {
               this.state = Game.State.WAITING;
            }
         } else if (this.state != Game.State.WAITING && this.state != Game.State.STARTING) {
            if (this.countdownTask != null) {
               this.countdownTask.cancel();
               this.countdownTask = null;
            }

            if (this.tickTask != null) {
               this.tickTask.cancel();
               this.tickTask = null;
            }

            if (this.espTask != null) {
               this.espTask.cancel();
               this.espTask = null;
            }

            this.plugin.antiCheat().revealEveryone();

            for (Player player : this.online()) {
               this.plugin.antiCheat().cloak().reveal(player);
            }

            this.plugin.antiCheat().cloak().clear();
            boolean flag = this.state == Game.State.RUNNING;
            this.state = Game.State.ENDING;
            if (this.bar != null) {
               this.bar.removeAll();
               this.bar = null;
            }

            if (playerData != null) {
               String str = playerData.name;
               if (this.teams() && playerData.team >= 0) {
                  StringBuilder stringBuilder = new StringBuilder();

                  for (PlayerData playerData3 : this.players.values()) {
                     if (playerData3.team == playerData.team) {
                        stringBuilder.append(playerData3.name).append(" ");
                     }
                  }

                  this.broadcast("§6§l★ ПОБЕДИЛА КОМАНДА " + this.teamName(playerData.team).toUpperCase() + " §6§l★ §7(" + stringBuilder.toString().trim() + ")");
                  str = TEAM_NAMES[playerData.team % TEAM_NAMES.length];
               } else {
                  this.broadcast("§6§l★ ПОБЕДИЛ " + this.name(playerData) + " §6§l★ §7(убийств: §e" + playerData.kills + "§7)");
               }

               for (Player player4 : this.online()) {
                  player4.sendTitle("§6§lПОБЕДА: §f" + str, "§7Битва в коробке окончена", 5, 80, 20);
                  player4.playSound(player4.getLocation(), "ui.toast.challenge_complete", 1.0F, 1.0F);
               }

               this.celebrate(playerData);
            } else if (flag) {
               this.broadcast("§7Игра окончена без победителя.");
            }

            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
               for (PlayerData playerData2 : new ArrayList<>(this.players.values())) {
                  Player player2 = Bukkit.getPlayer(playerData2.id);
                  if (player2 != null) {
                     this.restore(player2, playerData2);
                  }
               }

               for (Entry entry : new ArrayList<>(this.spectators.entrySet())) {
                  Player player3 = Bukkit.getPlayer((UUID)entry.getKey());
                  if (player3 != null) {
                     this.stopSpectating(player3, (Location)entry.getValue());
                  }
               }

               this.spectators.clear();
               this.dropped.addAll(this.offline);
               this.offline.clear();
               this.removeTraders();
               this.names.clearAll();
               this.players.clear();
               this.bedsByBlock.clear();
               this.colors.clear();
               this.resetArena(null);
            }, flag ? 160L : 1L);
         } else {
            this.cancelCountdown();
            if (!this.players.isEmpty()) {
               this.broadcast("§eОчередь распущена администратором.");
            }

            this.players.clear();
            this.state = Game.State.WAITING;
         }
      }
   }

   public void resetArena(Player player) {
      if (this.arena != null) {
         this.setDirty(true);
         this.state = Game.State.RESETTING;
         this.arena.regenerate(this.plugin, this.fill, x -> {
            if (player != null && player.isOnline()) {
               player.sendMessage("§8[§6Коробка§8] §7Строю коробку... §e" + x + "%");
            }
         }, this::afterBuild);
         this.showBuild();
      }
   }

   private void celebrate(PlayerData playerData) {
      final ArrayList list = new ArrayList();

      for (PlayerData playerData2 : this.players.values()) {
         if (playerData2.alive && (playerData2 == playerData || this.teams() && playerData2.team == playerData.team)) {
            Player player2 = Bukkit.getPlayer(playerData2.id);
            if (player2 != null) {
               list.add(player2);
            }
         }
      }

      (new BukkitRunnable() {
         int n = 0;

         @Override
         public void run() {
            for (Player player : (Iterable<Player>) list) {
               if (player.isOnline()) {
                  Game.firework(player.getLocation().add((Math.random() - 0.5) * 6.0, 3.0, (Math.random() - 0.5) * 6.0));
               }
            }

            if (++this.n >= 6) {
               this.cancel();
            }
         }
      }).runTaskTimer(this.plugin, 0L, 10L);
      if (this.cinematics && this.arena != null) {
         ArrayList list2 = new ArrayList<>(this.online());
         list2.addAll(this.onlineSpectators());
         Location location = this.arena.center();
         double d2 = (double)Math.max(this.arena.maxX - this.arena.minX, this.arena.maxZ - this.arena.minZ);
         double d = (double)(this.arena.maxY - this.arena.minY);
         Bukkit.getScheduler()
            .runTaskLater(
               this.plugin, () -> this.plugin.cinematic().orbit(list2, location, d2 * 0.9 + 18.0, Math.min(d * 0.6 + 6.0, 90.0), 120, 0.08, null, null), 20L
            );
      }
   }

   private static void firework(Location location) {
      Firework firework2 = location.getWorld().spawn(location, Firework.class);
      FireworkMeta fireworkMeta = firework2.getFireworkMeta();
      Color[] colors = new Color[]{Color.ORANGE, Color.YELLOW, Color.AQUA, Color.LIME, Color.FUCHSIA};
      fireworkMeta.addEffect(
         FireworkEffect.builder()
            .withColor(colors[(int)(Math.random() * (double)colors.length)], Color.WHITE)
            .with(Type.BALL_LARGE)
            .withFlicker()
            .withTrail()
            .build()
      );
      fireworkMeta.setPower(1);
      firework2.setFireworkMeta(fireworkMeta);
   }

   private void intro(Player player2, Location location) {
      if (this.cinematics) {
         Location location2 = this.arena.center();
         double d = (double)Math.max(this.arena.maxX - this.arena.minX, this.arena.maxZ - this.arena.minZ);
         double d2 = Math.random() * Math.PI * 2.0;
         Location location3 = location2.clone()
            .add(Math.cos(d2) * (d + 25.0), Math.min((double)this.arena.maxY - location2.getY() + 30.0, 80.0), Math.sin(d2) * (d + 25.0));
         Location location4 = new Location(this.arena.world, location.getX(), (double)(this.arena.maxY + 4), location.getZ());
         this.plugin.cinematic().fly(player2, location3, location4, location, 50, () -> {
            if (player2.isOnline()) {
               player2.teleport(location);
               player2.playSound(player2.getLocation(), "entity.player.levelup", 0.6F, 0.8F);
            }
         });
      }
   }

   private void showBuild() {
      if (this.buildBar != null) {
         this.buildBar.removeAll();
      }

      this.buildBar = Bukkit.createBossBar("§bСтроится коробка...", BarColor.BLUE, BarStyle.SEGMENTED_20);

      for (Player player : Bukkit.getOnlinePlayers()) {
         if (!this.inGame(player)) {
            this.buildBar.addPlayer(player);
         }
      }

      final Arena arena2 = this.arena;
      (new BukkitRunnable() {
         @Override
         public void run() {
            if (Game.this.buildBar == null) {
               this.cancel();
            } else if (!arena2.isBuilding()) {
               Game.this.buildBar.removeAll();
               Game.this.buildBar = null;
               this.cancel();
            } else {
               Game.this.buildBar.setTitle("§bСтроится коробка: §f" + arena2.buildPercent() + "%");
               Game.this.buildBar.setProgress(Game.clamp((double)arena2.buildPercent() / 100.0));

               for (Player player : Bukkit.getOnlinePlayers()) {
                  if (!Game.this.inGame(player)) {
                     Game.this.buildBar.addPlayer(player);
                  }
               }
            }
         }
      }).runTaskTimer(this.plugin, 5L, 10L);
      if (this.cinematics) {
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (arena2.isBuilding()) {
               ArrayList list = new ArrayList();

               for (Player player2 : Bukkit.getOnlinePlayers()) {
                  if (!this.inGame(player2) && player2.getGameMode() != GameMode.CREATIVE && this.plugin.lobby().isLobby(player2.getLocation())) {
                     list.add(player2);
                  }
               }

               if (!list.isEmpty()) {
                  Location location = arena2.center();
                  double d4 = (double)Math.max(arena2.maxX - arena2.minX, arena2.maxZ - arena2.minZ);
                  double d = (double)(arena2.maxY - arena2.minY);
                  double d2 = d4 * 0.9 + 18.0;
                  double d3 = Math.min(d * 0.6 + 6.0, 90.0);
                  this.plugin.cinematic().orbit(list, location, d2, d3, 2400, 0.04, () -> !arena2.isBuilding(), x -> {
                     this.plugin.lobby().send(x);
                     x.sendTitle("§a§lКОРОБКА ГОТОВА", "§7Скоро битва", 5, 40, 10);
                  });
               }
            }
         }, 10L);
      }
   }

   private void afterBuild() {
      this.state = Game.State.WAITING;
      this.setDirty(false);

      try {
         if (this.plugin.antiCheat().ensureXray(this.arena.world, this.arena.maxY)) {
            this.warnAdmins("§eАнти-X-ray включён для мира арены - §lперезапусти сервер§e, чтобы он заработал.");
         }
      } catch (Throwable t) {
         this.plugin.getLogger().warning("Анти-X-ray: " + t);
      }

      this.bedsGone = false;
      Bukkit.broadcastMessage("§8[§6Коробка§8] §7§aКоробка готова к битве! §e/bb join");
      if (!this.players.isEmpty()) {
         this.startCountdown(this.autoStart, false);
      }

      this.optedOut.clear();
      Bukkit.getScheduler().runTaskLater(this.plugin, this::autoJoinAll, 20L);
   }

   public String whyCantRebuild() {
      return switch (this.state) {
         case RUNNING, ENDING -> "§cИдёт битва. Сначала §e/bb stop";
         case RESETTING -> "§eКоробка ещё строится (" + (this.arena == null ? 0 : this.arena.buildPercent()) + "%), подожди пару секунд.";
         default -> null;
      };
   }

   public void rebuild(Arena arena2, Player player) {
      if (this.whyCantRebuild() == null) {
         if (this.state == Game.State.STARTING) {
            this.cancelCountdown();
            this.broadcast("§eАдмин меняет коробку - таймер начнётся заново, когда она будет готова.");
         }

         Arena arena3 = this.plugin.safeArena(arena2, player);
         this.state = Game.State.RESETTING;
         Arena arena4 = this.arena;
         Runnable task = () -> {
            this.arena = arena3;
            this.plugin.lobby().follow(arena3);
            this.resetArena(player);
         };
         if (arena4 == null) {
            task.run();
         } else {
            arena4.wipe(this.plugin, task);
         }
      }
   }

   public void shutdown() {
      if (this.arena != null) {
         this.arena.cancelBuild();
      }

      if (this.countdownTask != null) {
         this.countdownTask.cancel();
      }

      if (this.tickTask != null) {
         this.tickTask.cancel();
      }

      if (this.espTask != null) {
         this.espTask.cancel();
      }

      this.plugin.antiCheat().revealEveryone();
      if (this.bar != null) {
         this.bar.removeAll();
      }

      for (PlayerData playerData : new ArrayList<>(this.players.values())) {
         Player player = Bukkit.getPlayer(playerData.id);
         if (player != null) {
            this.restore(player, playerData);
         }
      }

      for (Entry entry : this.spectators.entrySet()) {
         Player player2 = Bukkit.getPlayer((UUID)entry.getKey());
         if (player2 != null) {
            this.stopSpectating(player2, (Location)entry.getValue());
         }
      }

      this.spectators.clear();
      this.removeTraders();
      this.names.clearAll();
      this.players.clear();
   }

   private void preparePlayer(Player player, boolean flag) {
      player.setGameMode(GameMode.SURVIVAL);
      PlayerInventory playerInventory = player.getInventory();
      playerInventory.clear();
      playerInventory.setArmorContents(new ItemStack[4]);
      player.setHealth(20.0);
      player.setFoodLevel(20);
      player.setFireTicks(0);
      playerInventory.addItem(new ItemStack[]{new ItemStack(Material.WOODEN_PICKAXE, 1)});
      if (flag) {
         playerInventory.addItem(new ItemStack[]{new ItemStack(Material.WOODEN_SWORD, 1)});
         playerInventory.addItem(new ItemStack[]{new ItemStack(Material.TORCH, 16)});
         playerInventory.addItem(new ItemStack[]{new ItemStack(Material.BREAD, 8)});
      } else {
         playerInventory.addItem(new ItemStack[]{new ItemStack(Material.BREAD, 4)});
      }

      if (this.hasteLevel > 0) {
         try {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 72000, this.hasteLevel - 1, false, false));
         } catch (Throwable t) {
         }
      }
   }

   private void restore(Player player, PlayerData playerData) {
      this.showName(player);
      this.plugin.antiCheat().reveal(player);
      this.plugin.antiCheat().forget(player);
      if (this.bar != null) {
         this.bar.removePlayer(player);
      }

      player.getInventory().clear();
      player.getInventory().setArmorContents(new ItemStack[4]);

      try {
         player.removePotionEffect(PotionEffectType.HASTE);
      } catch (Throwable t) {
      }

      player.setGameMode(GameMode.SURVIVAL);
      player.setHealth(20.0);
      player.setFoodLevel(20);
      player.setFireTicks(0);
      if (this.plugin.lobby().enabled()) {
         this.plugin.lobby().send(player);
      } else {
         Location location = playerData.returnLocation;
         if (location == null || location.getWorld() == null) {
            location = Bukkit.getWorlds().get(0).getSpawnLocation();
         }

         player.teleport(location);
      }
   }

   private List<Player> online() {
      ArrayList list = new ArrayList();

      for (UUID uUID : this.players.keySet()) {
         Player player = Bukkit.getPlayer(uUID);
         if (player != null) {
            list.add(player);
         }
      }

      return list;
   }

   public void broadcast(String str) {
      for (Player player : this.online()) {
         player.sendMessage("§8[§6Коробка§8] §7" + str);
      }
   }

   private String name(PlayerData playerData) {
      return this.colors.getOrDefault(playerData.id, "§f") + playerData.name;
   }

   public String info() {
      return "§8[§6Коробка§8] §7Состояние: §e"
         + this.state
         + " §7| игроков: §e"
         + this.players.size()
         + "/"
         + this.effectiveMax()
         + (this.state == Game.State.RUNNING ? " §7| живых: §e" + this.aliveCount() + " §7| время: §e" + fmt(this.elapsed) : "");
   }

   private static String fmt(int n) {
      if (n < 0) {
         n = 0;
      }

      return String.format("%d:%02d", n / 60, n % 60);
   }

   private static double clamp(double d) {
      return Math.max(0.0, Math.min(1.0, d));
   }

   public static enum State {
      WAITING,
      STARTING,
      RUNNING,
      ENDING,
      RESETTING;
   }
}
