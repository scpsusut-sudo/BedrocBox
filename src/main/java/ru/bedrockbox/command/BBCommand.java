package ru.bedrockbox.command;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import ru.bedrockbox.anticheat.NameHider;
import ru.bedrockbox.arena.Arena;
import ru.bedrockbox.arena.Lobby;
import ru.bedrockbox.arena.TallWorld;
import ru.bedrockbox.BedrockBoxPlugin;
import ru.bedrockbox.game.Game;

public final class BBCommand implements CommandExecutor, TabCompleter {
   private static final String[] SUBS = new String[]{
      "join",
      "leave",
      "spectate",
      "shop",
      "info",
      "setup",
      "start",
      "stop",
      "reset",
      "reload",
      "set",
      "names",
      "autojoin",
      "tallworld",
      "lobby",
      "setlobby",
      "team",
      "help"
   };
   private static final int MAX_SIZE = 255;
   private static final String[] SET_KEYS = new String[]{
      "size", "width", "length", "height", "maxplayers", "minplayers", "emeralds", "grace", "beds", "haste", "autostart", "diamonds", "teamsize"
   };
   private final BedrockBoxPlugin plugin;

   public BBCommand(BedrockBoxPlugin bedrockBoxPlugin) {
      this.plugin = bedrockBoxPlugin;
   }

   @Override
   public boolean onCommand(CommandSender commandSender, Command command, String str, String[] strings) {
      String str2 = strings.length == 0 ? "help" : strings[0].toLowerCase();
      Game tmp = this.plugin.game();
      boolean flag = commandSender.hasPermission("bedrockbox.admin");
      switch (str2) {
         case "join":
            if (player(commandSender) != null) {
               tmp.join((Player)commandSender);
            }
            break;
         case "leave":
            if (player(commandSender) != null) {
               Player player8 = (Player)commandSender;
               boolean flag3 = tmp.inGame(player8) || tmp.isOutsideSpectator(player8);
               if (tmp.isAutoJoin()) {
                  tmp.setOptOut(player8, true);
               }

               tmp.leave(player8, false);
               if (tmp.isAutoJoin() && flag3) {
                  player8.sendMessage(Game.PREFIX + "§7Автоподключение выключено до след. игры. Вернуться: §e/bb join");
               }
            }
            break;
         case "spectate":
         case "watch":
            if (player(commandSender) != null) {
               tmp.spectate((Player)commandSender);
            }
            break;
         case "shop":
            if (checkAdmin(commandSender, flag)) {
               Player player7 = player(commandSender);
               if (player7 != null) {
                  if (strings.length > 1 && strings[1].equalsIgnoreCase("secret")) {
                     this.plugin.secretShop().open(player7);
                  } else {
                     this.plugin.shop().open(player7);
                  }
               }
            }
            break;
         case "info":
            commandSender.sendMessage(tmp.info());
            break;
         case "setup":
            if (checkAdmin(commandSender, flag)) {
               Player player6 = player(commandSender);
               if (player6 != null) {
                  if (tmp.whyCantRebuild() != null) {
                     player6.sendMessage(Game.PREFIX + tmp.whyCantRebuild());
                  } else {
                     FileConfiguration fileConfiguration3 = this.plugin.getConfig();
                     int n = strings.length > 1 ? parse(strings[1], fileConfiguration3.getInt("arena.size", 41)) : fileConfiguration3.getInt("arena.size", 41);
                     int n3 = strings.length > 2 ? parse(strings[2], fileConfiguration3.getInt("arena.height", 31)) : fileConfiguration3.getInt("arena.height", 31);
                     if (strings.length > 3) {
                        fileConfiguration3.set("arena.length", Integer.valueOf(Math.max(21, Math.min(255, parse(strings[3], n)))));
                     } else if (strings.length > 1) {
                        fileConfiguration3.set("arena.length", Integer.valueOf(0));
                     }

                     n = Math.max(21, Math.min(255, n));
                     World world2 = player6.getWorld();
                     int tmp2 = maxBoxHeight(world2);
                     if (n3 > tmp2) {
                        player6.sendMessage(Game.PREFIX + "§eВ этом мире влезает максимум §f" + tmp2 + "§e в высоту - ставлю " + tmp2 + ".");
                        if (n3 > 380) {
                           player6.sendMessage(Game.PREFIX + "§7Выше 380: §e/bb tallworld");
                        }
                     }

                     n3 = Math.max(15, Math.min(tmp2, n3));
                     int n2 = fitBaseY(world2, player6.getLocation().getBlockY(), n3);
                     if (n2 != player6.getLocation().getBlockY()) {
                        player6.sendMessage(Game.PREFIX + "§7Низ коробки сдвинут на Y=§f" + n2 + "§7, чтобы влезла вся высота.");
                     }

                     fileConfiguration3.set("arena.world", player6.getWorld().getName());
                     fileConfiguration3.set("arena.x", Integer.valueOf(player6.getLocation().getBlockX()));
                     fileConfiguration3.set("arena.y", Integer.valueOf(n2));
                     fileConfiguration3.set("arena.z", Integer.valueOf(player6.getLocation().getBlockZ()));
                     fileConfiguration3.set("arena.size", Integer.valueOf(n));
                     fileConfiguration3.set("arena.height", Integer.valueOf(n3));
                     this.plugin.saveConfig();
                     Arena arena2 = this.plugin.arenaFromConfig();
                     player6.sendMessage(
                        Game.PREFIX + "Строю коробку §e"
                           + n
                           + "x"
                           + n3
                           + "x"
                           + n
                           + "§7, комнат влезет: §e"
                           + arena2.maxRooms()
                           + "§7. Отойди в сторону!"
                     );
                     if (arena2.maxRooms() < fileConfiguration3.getInt("game.max-players", 8)) {
                        player6.sendMessage(
                           Game.PREFIX + "§eДля "
                              + fileConfiguration3.getInt("game.max-players", 8)
                              + " игроков нужен размер §f"
                              + arena2.sizeFor(fileConfiguration3.getInt("game.max-players", 8))
                        );
                     }

                     Location location = new Location(
                        world2, (double)(player6.getLocation().getBlockX() + n / 2) + 4.5, (double)(n2 + 1), (double)player6.getLocation().getBlockZ() + 0.5
                     );
                     world2.getBlockAt(location.getBlockX(), n2, location.getBlockZ()).setType(Material.GLASS, false);
                     player6.teleport(location);
                     tmp.rebuild(arena2, player6);
                  }
               }
            }
            break;
         case "start":
            if (checkAdmin(commandSender, flag)) {
               if (tmp.state() != Game.State.WAITING && tmp.state() != Game.State.STARTING) {
                  commandSender.sendMessage(Game.PREFIX + "§cСейчас нельзя: " + tmp.state());
               } else {
                  tmp.forceStart();
                  commandSender.sendMessage(Game.PREFIX + "§aПринудительный старт.");
               }
            }
            break;
         case "stop":
            if (checkAdmin(commandSender, flag)) {
               Game.State state2 = tmp.state();
               tmp.end(null);

               commandSender.sendMessage(Game.PREFIX + switch (state2) {
                  case RUNNING -> "§eИгра остановлена, коробка восстанавливается.";
                  case STARTING -> "§eТаймер остановлен, очередь распущена.";
                  case RESETTING -> tmp.state() == Game.State.WAITING ? "§eЗависшая стройка сброшена." : "§7Коробка строится, подожди.";
                  default -> "§7Игра и так не идёт.";
               });
            }
            break;
         case "reset":
            if (checkAdmin(commandSender, flag)) {
               if (tmp.whyCantRebuild() != null) {
                  commandSender.sendMessage(Game.PREFIX + tmp.whyCantRebuild());
               } else if (tmp.state() == Game.State.STARTING) {
                  commandSender.sendMessage(Game.PREFIX + "§cИдёт таймер лобби. /bb stop - распустить очередь");
               } else {
                  tmp.resetArena(commandSender instanceof Player player5 ? player5 : null);
                  commandSender.sendMessage(Game.PREFIX + "Перестраиваю коробку...");
               }
            }
            break;
         case "reload":
            if (checkAdmin(commandSender, flag)) {
               this.plugin.reloadConfig();
               this.plugin.reloadSettings();
               if (tmp.state() == Game.State.WAITING) {
                  this.plugin.reloadArena();
               }

               commandSender.sendMessage(Game.PREFIX + "§aКонфиг перезагружен.");
            }
            break;
         case "set":
            if (checkAdmin(commandSender, flag)) {
               if (strings.length < 3) {
                  this.showSettings(commandSender);
               } else {
                  this.set(commandSender, strings[1].toLowerCase(), strings[2]);
               }
            }
            break;
         case "names":
            if (checkAdmin(commandSender, flag)) {
               String str3 = strings.length > 1 ? strings[1].toLowerCase() : "";
               FileConfiguration fileConfiguration2 = this.plugin.getConfig();
               switch (str3) {
                  case "hide":
                     fileConfiguration2.set("game.hide-nametags-everywhere", Boolean.valueOf(true));
                     fileConfiguration2.set("game.hide-nametags", Boolean.valueOf(true));
                     this.plugin.saveConfig();
                     this.plugin.reloadSettings();
                     tmp.enforceEverywhere();
                     commandSender.sendMessage(Game.PREFIX + "§aНики скрыты на всём сервере, всегда.");
                     break;
                  case "show":
                     fileConfiguration2.set("game.hide-nametags-everywhere", Boolean.valueOf(false));
                     this.plugin.saveConfig();
                     this.plugin.reloadSettings();
                     tmp.showEveryone();
                     commandSender.sendMessage(Game.PREFIX + "§eНики снова видны (кроме битвы).");
                     break;
                  default:
                     this.namesInfo(commandSender);
               }
            }
            break;
         case "autojoin":
            if (checkAdmin(commandSender, flag)) {
               FileConfiguration fileConfiguration = this.plugin.getConfig();
               boolean flag2 = strings.length > 1 ? strings[1].equalsIgnoreCase("on") : !fileConfiguration.getBoolean("auto-join.enabled", true);
               fileConfiguration.set("auto-join.enabled", Boolean.valueOf(flag2));
               this.plugin.saveConfig();
               this.plugin.reloadSettings();
               commandSender.sendMessage(Game.PREFIX + "Автоподключение: " + (flag2 ? "§aвключено" : "§cвыключено"));
               if (flag2) {
                  tmp.autoJoinAll();
               }
            }
            break;
         case "team":
         case "teams":
            Player player4 = player(commandSender);
            if (player4 != null) {
               tmp.chooseTeam(player4, strings.length > 1 ? strings[1] : null);
            }
            break;
         case "lobby":
            Player player3 = player(commandSender);
            if (player3 != null) {
               if (!this.plugin.lobby().enabled()) {
                  player3.sendMessage(Game.PREFIX + "§cЛобби выключено.");
               } else if (tmp.inGame(player3) && tmp.state() == Game.State.RUNNING) {
                  player3.sendMessage(Game.PREFIX + "§cСначала §e/bb leave");
               } else {
                  this.plugin.lobby().send(player3);
               }
            }
            break;
         case "setlobby":
            if (checkAdmin(commandSender, flag)) {
               Player player2 = player(commandSender);
               if (player2 != null) {
                  if (strings.length > 1 && strings[1].equalsIgnoreCase("arena")) {
                     Lobby.resetToArena(this.plugin.getConfig());
                     this.plugin.saveConfig();
                     this.plugin.reloadSettings();
                     this.plugin.lobby().follow(tmp.arena());
                     player2.sendMessage(Game.PREFIX + "§aЛобби снова у коробки - оттуда видно, как она строится.");
                  } else {
                     this.plugin.lobby().setHere(player2, this.plugin.getConfig());
                     this.plugin.saveConfig();
                     this.plugin.reloadSettings();
                     player2.sendMessage(Game.PREFIX + "§aЛобби теперь здесь. Вернуть к коробке: §e/bb setlobby arena");
                  }
               }
            }
            break;
         case "tallworld":
            if (checkAdmin(commandSender, flag)) {
               this.tallworld(commandSender, strings);
            }
            break;
         default:
            this.help(commandSender, flag);
      }

      return true;
   }

   private void set(CommandSender commandSender, String str, String str2) {
      Game game2 = this.plugin.game();
      FileConfiguration fileConfiguration = this.plugin.getConfig();

      double d;
      try {
         d = Double.parseDouble(str2.replace(',', '.'));
      } catch (NumberFormatException numberFormatException) {
         commandSender.sendMessage(Game.PREFIX + "§cНужно число.");
         return;
      }

      int n = (int)Math.round(d);
      switch (str) {
         case "size":
         case "width":
         case "length":
         case "height":
            if (game2.whyCantRebuild() != null) {
               commandSender.sendMessage(Game.PREFIX + game2.whyCantRebuild());
               return;
            }

            boolean flag = !str.equals("height");
            Arena arena4 = game2.arena();
            int tmp = arena4 == null ? 4000 : maxBoxHeight(arena4.world);
            int n3 = flag ? clamp(n, 21, 255) : clamp(n, 15, tmp);
            if (n3 != n) {
               commandSender.sendMessage(Game.PREFIX + "§7Допустимо: " + (flag ? "21..255" : "15.." + tmp) + " - ставлю §e" + n3);
               if (!flag && n > tmp) {
                  commandSender.sendMessage(Game.PREFIX + "§7Выше нельзя, нужен §e/bb tallworld");
               }
            }

            if (!flag && arena4 != null) {
               int n4 = fitBaseY(arena4.world, fileConfiguration.getInt("arena.y"), n3);
               if (n4 != fileConfiguration.getInt("arena.y")) {
                  fileConfiguration.set("arena.y", Integer.valueOf(n4));
                  commandSender.sendMessage(Game.PREFIX + "§7Низ коробки сдвинут на Y=§f" + n4 + "§7, чтобы влезла вся высота.");
               }
            }

            byte b = -1;
            switch (str.hashCode()) {
               case -1106363674:
                  if (str.equals("length")) {
                     b = 2;
                  }
                  break;
               case 3530753:
                  if (str.equals("size")) {
                     b = 0;
                  }
                  break;
               case 113126854:
                  if (str.equals("width")) {
                     b = 1;
                  }
            }

            String str3 = switch (b) {
               case 0 -> {
                  fileConfiguration.set("arena.size", Integer.valueOf(n3));
                  fileConfiguration.set("arena.length", Integer.valueOf(0));
                  yield "Ширина и длина";
               }
               case 1 -> {
                  if (fileConfiguration.getInt("arena.length", 0) <= 0) {
                     fileConfiguration.set("arena.length", Integer.valueOf(fileConfiguration.getInt("arena.size", 41)));
                  }

                  fileConfiguration.set("arena.size", Integer.valueOf(n3));
                  yield "Ширина";
               }
               case 2 -> {
                  fileConfiguration.set("arena.length", Integer.valueOf(n3));
                  yield "Длина";
               }
               default -> {
                  fileConfiguration.set("arena.height", Integer.valueOf(n3));
                  yield "Высота";
               }
            };

            this.plugin.saveConfig();
            if (arena4 == null) {
               commandSender.sendMessage(Game.PREFIX + "Сохранено: §e" + str3 + " = " + n3 + " §7(построй коробку: /bb setup)");
               return;
            }

            Arena arena3 = this.plugin.arenaFromConfig();
            commandSender.sendMessage(
               Game.PREFIX + ""
                  + str3
                  + " -> §e"
                  + n3
                  + "§7. Коробка §f"
                  + fileConfiguration.getInt("arena.size", 41)
                  + "×"
                  + BedrockBoxPlugin.arenaLength(fileConfiguration)
                  + "×"
                  + fileConfiguration.getInt("arena.height", 31)
                  + "§7 (ширина×длина×высота). Перестраиваю... §8(влезет баз: "
                  + arena3.maxRooms()
                  + ")"
            );
            game2.rebuild(arena3, commandSender instanceof Player player ? player : null);
            break;
         case "maxplayers":
            int n7 = clamp(n, 1, 16);
            fileConfiguration.set("game.max-players", Integer.valueOf(n7));
            this.save(commandSender, "Макс. игроков", String.valueOf(n7));
            Arena arena2 = game2.arena();
            if (arena2 != null && arena2.maxRooms() < n7) {
               commandSender.sendMessage(
                  Game.PREFIX + "§eВ коробку сейчас влезает только §f"
                     + arena2.maxRooms()
                     + " §eкомнат. Для "
                     + n7
                     + " игроков: §f/bb set size "
                     + arena2.sizeFor(n7)
               );
            }
            break;
         case "minplayers":
            int n6 = clamp(n, 1, 16);
            fileConfiguration.set("game.min-players", Integer.valueOf(n6));
            this.save(commandSender, "Мин. игроков для старта", String.valueOf(n6));
            break;
         case "emeralds":
            double d3 = Math.max(0.0, Math.min(100.0, d));
            fileConfiguration.set("fill.emerald-percent", Double.valueOf(d3));
            this.save(commandSender, "Изумрудной руды", d3 + "%");
            commandSender.sendMessage(Game.PREFIX + "§7Применится после перестройки: §e/bb reset");
            break;
         case "grace":
            fileConfiguration.set("game.grace-seconds", Integer.valueOf(clamp(n, 0, 3600)));
            this.save(commandSender, "Мирное время", clamp(n, 0, 3600) + " сек");
            break;
         case "beds":
            fileConfiguration.set("game.bed-destroy-seconds", Integer.valueOf(clamp(n, 0, 7200)));
            this.save(commandSender, "Кровати исчезнут через", clamp(n, 0, 7200) + " сек (0 = никогда)");
            break;
         case "teamsize":
            int n5 = clamp(n, 1, 8);
            fileConfiguration.set("game.team-size", Integer.valueOf(n5));
            this.save(commandSender, "Размер команды", n5 == 1 ? "1 (каждый сам за себя)" : String.valueOf(n5));
            break;
         case "diamonds":
            double d2 = Math.max(0.0, Math.min(100.0, d));
            fileConfiguration.set("fill.rare-ores.DIAMOND_ORE", Double.valueOf(d2));
            this.save(commandSender, "Алмазной руды", d2 + "%");
            commandSender.sendMessage(Game.PREFIX + "§7Применится после перестройки: §e/bb reset");
            break;
         case "autostart":
            int n2 = clamp(n, 5, 3600);
            fileConfiguration.set("game.auto-start-seconds", Integer.valueOf(n2));
            this.save(commandSender, "Автостарт через", n2 + " сек");
            break;
         case "haste":
            fileConfiguration.set("game.haste-level", Integer.valueOf(clamp(n, 0, 10)));
            this.save(commandSender, "Спешка", String.valueOf(clamp(n, 0, 10)));
            break;
         default:
            this.showSettings(commandSender);
      }
   }

   private void save(CommandSender commandSender, String str, String str2) {
      this.plugin.saveConfig();
      this.plugin.reloadSettings();
      commandSender.sendMessage(Game.PREFIX + str + " -> §e" + str2);
   }

   private void showSettings(CommandSender commandSender) {
      FileConfiguration fileConfiguration = this.plugin.getConfig();
      Arena arena2 = this.plugin.game().arena();
      commandSender.sendMessage("§6§l== Настройки коробки ==");
      commandSender.sendMessage("§e/bb set width §f" + fileConfiguration.getInt("arena.size", 41) + " §7- ширина (21..255)");
      commandSender.sendMessage("§e/bb set length §f" + BedrockBoxPlugin.arenaLength(fileConfiguration) + " §7- длина (21..255)");
      commandSender.sendMessage("§e/bb set size §7N - ширина и длина сразу (квадрат)");
      commandSender.sendMessage("§e/bb set height §f" + fileConfiguration.getInt("arena.height", 31) + " §7- высота (до предела мира, /bb tallworld - до 2000)");
      commandSender.sendMessage(
         "§e/bb set maxplayers §f"
            + fileConfiguration.getInt("game.max-players", 8)
            + " §7- макс. игроков (до 16)"
            + (arena2 != null ? " §8| влезает комнат: " + arena2.maxRooms() : "")
      );
      commandSender.sendMessage("§e/bb set minplayers §f" + fileConfiguration.getInt("game.min-players", 2) + " §7- сколько нужно для старта");
      commandSender.sendMessage("§e/bb set emeralds §f" + fileConfiguration.getDouble("fill.emerald-percent", 2.0) + "% §7- изумрудной руды");
      commandSender.sendMessage("§e/bb set teamsize §f" + fileConfiguration.getInt("game.team-size", 1) + " §7- игроков в команде (1 = каждый сам за себя)");
      commandSender.sendMessage("§e/bb set autostart §f" + fileConfiguration.getInt("game.auto-start-seconds", 120) + " §7- игра стартует сама через, сек");
      commandSender.sendMessage("§e/bb set diamonds §f" + fileConfiguration.getDouble("fill.rare-ores.DIAMOND_ORE", 0.1) + "% §7- редкой алмазной руды");
      commandSender.sendMessage("§e/bb set grace §f" + fileConfiguration.getInt("game.grace-seconds", 60) + " §7- мирное время, сек");
      commandSender.sendMessage("§e/bb set beds §f" + fileConfiguration.getInt("game.bed-destroy-seconds", 0) + " §7- когда исчезнут кровати, сек");
      commandSender.sendMessage("§e/bb set haste §f" + fileConfiguration.getInt("game.haste-level", 0) + " §7- уровень спешки");
   }

   private static int clamp(int n, int n2, int n3) {
      return Math.max(n2, Math.min(n3, n));
   }

   static int maxBoxHeight(World world) {
      return Math.max(15, world.getMaxHeight() - world.getMinHeight() - 6);
   }

   static int fitBaseY(World world, int n, int n2) {
      int n3 = world.getMinHeight() + 1;
      int n4 = world.getMaxHeight() - n2 - 4;
      return Math.max(n3, Math.min(n4, n));
   }

   private void tallworld(CommandSender commandSender, String[] strings) {
      TallWorld tallWorld2 = this.plugin.tallWorld();
      String str = strings.length > 1 ? strings[1].toLowerCase() : "";
      World world2 = tallWorld2.world();
      if (str.equals("tp")) {
         Player player2 = player(commandSender);
         if (player2 != null) {
            if (world2 == null) {
               commandSender.sendMessage(Game.PREFIX + "§cВысокого мира ещё нет. Сначала §e/bb tallworld§c и перезапуск сервера.");
            } else {
               player2.teleport(TallWorld.platform(world2, 0, 0));
               commandSender.sendMessage(Game.PREFIX + "§aТы в высоком мире (Y от " + world2.getMinHeight() + " до " + world2.getMaxHeight() + ").");
               commandSender.sendMessage(Game.PREFIX + "§7Теперь: §e/bb setup 41 999 §7- коробка 41x999x41.");
            }
         }
      } else if (world2 != null) {
         commandSender.sendMessage(Game.PREFIX + "§aВысокий мир готов: §f" + world2.getName() + " §7(высота " + (world2.getMaxHeight() - world2.getMinHeight()) + ")");
         commandSender.sendMessage(Game.PREFIX + "§e/bb tallworld tp §7- перейти туда, потом §e/bb setup 41 999");
      } else if (tallWorld2.installed()) {
         commandSender.sendMessage(Game.PREFIX + "§eДатапак уже стоит, нужен полный рестарт (не /reload).");
         commandSender.sendMessage(Game.PREFIX + "§7Мира нет после рестарта - кинь консоль.");
      } else {
         try {
            tallWorld2.install();
            commandSender.sendMessage(Game.PREFIX + "§aДатапак высокого мира установлен.");
            commandSender.sendMessage(Game.PREFIX + "§eПерезапусти сервер, потом §f/bb tallworld tp §eи §f/bb setup 41 999");
         } catch (Exception e2) {
            commandSender.sendMessage(Game.PREFIX + "§cНе получилось записать датапак: " + e2.getMessage());
         }
      }
   }

   private void namesInfo(CommandSender commandSender) {
      Game game2 = this.plugin.game();
      commandSender.sendMessage("§6§l== Ники: диагностика ==");
      commandSender.sendMessage("§7Версия BedrockBox: §f" + this.plugin.getDescription().getVersion());
      commandSender.sendMessage("§7Скрывать в битве: " + (game2.hideNametags() ? "§aда" : "§cнет") + " §8| §7Везде всегда: " + (game2.hideEverywhere() ? "§aда" : "§cнет"));
      StringBuilder stringBuilder = new StringBuilder();

      for (String str : NameHider.NAMETAG_PLUGINS) {
         if (Bukkit.getPluginManager().getPlugin(str) != null) {
            stringBuilder.append(str).append(" ");
         }
      }

      commandSender.sendMessage("§7Плагины на ники: " + (stringBuilder.length() == 0 ? "§aне найдено" : "§c" + stringBuilder.toString().trim()));
      if (commandSender instanceof Player player) {
         boolean flag = player.getScoreboard() == Bukkit.getScoreboardManager().getMainScoreboard();
         commandSender.sendMessage("§7Твой скорборд: " + (flag ? "§fосновной" : "§eсвой от другого плагина"));
         this.plugin.game().names().tab(player, this.plugin.game().names().isHidden(player) || game2.hideEverywhere());
      }

      commandSender.sendMessage("§7TAB: §f" + game2.names().tabStatus());
      commandSender.sendMessage("§e/bb names hide §7- скрыть ники всем и всегда");
      commandSender.sendMessage("§e/bb names show §7- вернуть (скрываются только в битве)");
      commandSender.sendMessage("§8Скинь скрин этого сообщения, если ники всё ещё видно.");
   }

   private void help(CommandSender commandSender, boolean flag) {
      commandSender.sendMessage("§6§l== Битва в бедроковой коробке ==");
      commandSender.sendMessage("§e/bb join §7- зайти в игру");
      commandSender.sendMessage("§e/bb leave §7- выйти");
      commandSender.sendMessage("§e/bb spectate §7- смотреть идущую битву");
      commandSender.sendMessage("§e/bb team [цвет] §7- выбрать команду (в командном режиме)");
      commandSender.sendMessage("§e/bb lobby §7- в лобби");
      commandSender.sendMessage("§7Магазин - ПКМ по §6торговцу§7 в своей комнате");
      commandSender.sendMessage("§e/bb info §7- состояние игры");
      if (flag) {
         commandSender.sendMessage("§c/bb setup [размер] [высота] §7- построить коробку здесь");
         commandSender.sendMessage("§c/bb set §7- настройки карты и игроков");
         commandSender.sendMessage("§c/bb setlobby §7- сделать лобби там, где стоишь");
         commandSender.sendMessage("§c/bb tallworld [tp] §7- высокий мир");
         commandSender.sendMessage("§c/bb autojoin [on|off] §7- все игроки сами попадают в игру");
         commandSender.sendMessage("§c/bb names [hide|show] §7- ники: диагностика / скрыть везде");
         commandSender.sendMessage("§c/bb shop [secret] §7- открыть магазин / тайный магазин (тест)");
         commandSender.sendMessage("§c/bb start §7- старт сразу §8| §c/bb stop §7- остановить");
         commandSender.sendMessage("§c/bb reset §7- перестроить коробку §8| §c/bb reload §7- перечитать конфиг");
      }
   }

   private static Player player(CommandSender commandSender) {
      if (commandSender instanceof Player) {
         return (Player)commandSender;
      } else {
         commandSender.sendMessage("Только для игроков.");
         return null;
      }
   }

   private static boolean checkAdmin(CommandSender commandSender, boolean flag) {
      if (!flag) {
         commandSender.sendMessage(Game.PREFIX + "§cНет прав.");
      }

      return flag;
   }

   private static int parse(String str, int n) {
      try {
         return Integer.parseInt(str);
      } catch (NumberFormatException numberFormatException) {
         return n;
      }
   }

   @Override
   public List<String> onTabComplete(CommandSender commandSender, Command command, String str, String[] strings) {
      ArrayList list = new ArrayList();
      if (strings.length == 1) {
         for (String str2 : SUBS) {
            if (str2.startsWith(strings[0].toLowerCase())) {
               list.add(str2);
            }
         }
      } else if (strings.length == 2 && strings[0].equalsIgnoreCase("team")) {
         for (String str6 : Game.TEAM_NAMES) {
            if (str6.toLowerCase().startsWith(strings[1].toLowerCase())) {
               list.add(str6.toLowerCase());
            }
         }
      } else if (strings.length == 2 && strings[0].equalsIgnoreCase("tallworld")) {
         list.add("tp");
      } else if (strings.length == 2 && strings[0].equalsIgnoreCase("autojoin")) {
         list.add("on");
         list.add("off");
      } else if (strings.length == 2 && strings[0].equalsIgnoreCase("names")) {
         for (String str5 : new String[]{"hide", "show"}) {
            if (str5.startsWith(strings[1].toLowerCase())) {
               list.add(str5);
            }
         }
      } else if (strings.length == 2 && strings[0].equalsIgnoreCase("set")) {
         for (String str4 : SET_KEYS) {
            if (str4.startsWith(strings[1].toLowerCase())) {
               list.add(str4);
            }
         }
      } else if (strings.length == 3 && strings[0].equalsIgnoreCase("set")) {
         String str3 = strings[1].toLowerCase();
         switch (str3) {
            case "size":
            case "width":
            case "length":
               list.add("41");
               list.add("69");
               list.add("101");
               list.add("199");
               break;
            case "height":
               list.add("31");
               list.add("101");
               list.add("300");
               list.add("999");
               break;
            case "maxplayers":
            case "minplayers":
               list.add("2");
               list.add("4");
               list.add("8");
               list.add("12");
               list.add("16");
               break;
            case "teamsize":
               list.add("1");
               list.add("2");
               list.add("3");
               list.add("4");
               break;
            case "autostart":
               list.add("60");
               list.add("120");
               list.add("180");
               break;
            case "diamonds":
               list.add("0.2");
               list.add("0.4");
               list.add("1");
               break;
            case "emeralds":
               list.add("3");
               list.add("8");
               list.add("15");
               list.add("25");
         }
      }

      return list;
   }
}
