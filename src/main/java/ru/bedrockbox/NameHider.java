package ru.bedrockbox;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;

public final class NameHider {
   public static final String TAG = "bb_nametag_hider";
   private static final String TEAM = "bb_hidden";
   private final Map<UUID, ArmorStand> markers = new HashMap<>();
   private final Set<Scoreboard> touchedBoards = new HashSet<>();
   private Scoreboard gameBoard;
   private final Map<UUID, Scoreboard> previousBoards = new HashMap<>();
   private final List<String> sidebarLines = new ArrayList<>();
   private final Set<UUID> tabHidden = new HashSet<>();
   private String tabStatus = "не проверялся";
   public static final String[] NAMETAG_PLUGINS = new String[]{
      "TAB",
      "NametagEdit",
      "CMI",
      "TitleManager",
      "NameTagChanger",
      "Animated-Scoreboard",
      "AnimatedNames",
      "PlaceholderAPI-Nametags",
      "UltimateNametags",
      "NickAPI",
      "TabList"
   };

   public void enforce(Collection<Player> players, Collection<Player> players2) {
      this.enforce(players, players2, true);
   }

   public Scoreboard board() {
      if (this.gameBoard == null) {
         this.gameBoard = Bukkit.getScoreboardManager().getNewScoreboard();
         Team team = this.gameBoard.registerNewTeam("bb_hidden");
         team.setOption(Option.NAME_TAG_VISIBILITY, OptionStatus.NEVER);
         team.setOption(Option.DEATH_MESSAGE_VISIBILITY, OptionStatus.ALWAYS);
         Objective objective = this.gameBoard.registerNewObjective("bb_side", Criteria.DUMMY, "§6§lКОРОБКА");
         objective.setDisplaySlot(DisplaySlot.SIDEBAR);
      }

      return this.gameBoard;
   }

   public void useGameBoard(Player player) {
      Scoreboard scoreboard = this.board();
      Scoreboard scoreboard2 = player.getScoreboard();
      if (scoreboard2 != scoreboard) {
         this.previousBoards.putIfAbsent(player.getUniqueId(), scoreboard2);
         player.setScoreboard(scoreboard);
      }
   }

   public void restoreBoard(Player player) {
      Scoreboard scoreboard = this.previousBoards.remove(player.getUniqueId());
      if (this.gameBoard != null && player.getScoreboard() == this.gameBoard) {
         player.setScoreboard(scoreboard != null ? scoreboard : Bukkit.getScoreboardManager().getMainScoreboard());
      }
   }

   public void sidebar(List<String> strs) {
      Scoreboard scoreboard = this.board();
      Objective objective = scoreboard.getObjective("bb_side");
      if (objective != null) {
         for (String str : this.sidebarLines) {
            if (!strs.contains(str)) {
               scoreboard.resetScores(str);
            }
         }

         this.sidebarLines.clear();
         int n = strs.size();

         for (String str2 : strs) {
            String str3 = str2;

            while (this.sidebarLines.contains(str3)) {
               str3 = str3 + "§r";
            }

            objective.getScore(str3).setScore(n--);
            this.sidebarLines.add(str3);
         }
      }
   }

   public void enforce(Collection<Player> players, Collection<Player> players2, boolean flag) {
      for (Player player : players) {
         this.tab(player, true);
      }

      if (flag) {
         for (Player player3 : players) {
            if (!player3.isDead() && player3.isOnline()) {
               ArmorStand armorStand = this.markers.get(player3.getUniqueId());
               boolean flag2 = armorStand != null && armorStand.isValid() && player3.getPassengers().contains(armorStand);
               if (!flag2) {
                  if (armorStand != null) {
                     armorStand.remove();
                  }

                  this.markers.put(player3.getUniqueId(), this.mount(player3));
               }
            } else {
               this.release(player3);
            }
         }
      }

      HashSet list = new HashSet();

      for (Player player4 : players2) {
         list.add(player4.getScoreboard());
      }

      for (Player player5 : players) {
         list.add(player5.getScoreboard());
      }

      list.add(Bukkit.getScoreboardManager().getMainScoreboard());
      if (this.gameBoard != null) {
         list.add(this.gameBoard);
      }

      for (Scoreboard scoreboard : (Iterable<Scoreboard>) list) {
         try {
            Team team = scoreboard.getTeam("bb_hidden");
            if (team == null) {
               team = scoreboard.registerNewTeam("bb_hidden");
            }

            team.setOption(Option.NAME_TAG_VISIBILITY, OptionStatus.NEVER);

            for (Player player2 : players) {
               if (!team.hasEntry(player2.getName())) {
                  team.addEntry(player2.getName());
               }
            }

            this.touchedBoards.add(scoreboard);
         } catch (Throwable t) {
         }
      }
   }

   private ArmorStand mount(Player player) {
      ArmorStand armorStand = player.getWorld().spawn(player.getLocation(), ArmorStand.class, x -> {
         x.setMarker(true);
         x.setInvisible(true);
         x.setSmall(true);
         x.setGravity(false);
         x.setInvulnerable(true);
         x.setSilent(true);
         x.setPersistent(false);
         x.setCustomNameVisible(false);
         x.addScoreboardTag("bb_nametag_hider");
      });
      player.addPassenger(armorStand);
      return armorStand;
   }

   public void release(Player player) {
      ArmorStand armorStand = this.markers.remove(player.getUniqueId());
      if (armorStand != null) {
         player.removePassenger(armorStand);
         armorStand.remove();
      }
   }

   public void show(Player player) {
      this.release(player);
      this.tab(player, false);
      this.restoreBoard(player);

      for (Scoreboard scoreboard : this.touchedBoards) {
         try {
            Team team = scoreboard.getTeam("bb_hidden");
            if (team != null) {
               team.removeEntry(player.getName());
            }
         } catch (Throwable t2) {
         }
      }

      try {
         Team team2 = player.getScoreboard().getTeam("bb_hidden");
         if (team2 != null) {
            team2.removeEntry(player.getName());
         }
      } catch (Throwable t) {
      }
   }

   public boolean tab(Player player, boolean flag) {
      if (flag == this.tabHidden.contains(player.getUniqueId()) && flag) {
         return true;
      } else if (Bukkit.getPluginManager().getPlugin("TAB") == null) {
         this.tabStatus = "TAB не установлен";
         return false;
      } else {
         try {
            Class cls = Class.forName("me.neznamy.tab.api.TabAPI");
            Object obj = cls.getMethod("getInstance").invoke(null);
            Object obj2 = call(obj, "getPlayer", player.getUniqueId());
            if (obj2 == null) {
               this.tabStatus = "TAB ещё не загрузил игрока";
               return false;
            } else {
               Object obj3 = call(obj, "getNameTagManager");
               if (obj3 == null) {
                  this.tabStatus = "в TAB выключены ники (scoreboard-teams) - значит, ники рисует не TAB";
                  return false;
               } else {
                  call(obj3, flag ? "hideNameTag" : "showNameTag", obj2);
                  if (flag) {
                     this.tabHidden.add(player.getUniqueId());
                  } else {
                     this.tabHidden.remove(player.getUniqueId());
                  }

                  this.tabStatus = "TAB найден, ники прячутся через него";
                  return true;
               }
            }
         } catch (Throwable t) {
            this.tabStatus = "ошибка TAB API: " + t.getClass().getSimpleName() + " " + t.getMessage();
            return false;
         }
      }
   }

   private static Object call(Object obj, String str, Object... x) throws Exception {
      for (Method method : obj.getClass().getMethods()) {
         if (method.getName().equals(str) && method.getParameterCount() == x.length) {
            boolean flag = true;
            Class[] classs = method.getParameterTypes();

            for (int n = 0; n < x.length; n++) {
               if (x[n] != null && !classs[n].isInstance(x[n])) {
                  flag = false;
                  break;
               }
            }

            if (flag) {
               method.setAccessible(true);
               return method.invoke(obj, x);
            }
         }
      }

      throw new NoSuchMethodException(str);
   }

   public String tabStatus() {
      return this.tabStatus;
   }

   public boolean isMarker(Entity entity) {
      return entity.getScoreboardTags().contains("bb_nametag_hider");
   }

   public boolean isHidden(Player player) {
      return this.markers.containsKey(player.getUniqueId());
   }

   public void clearAll() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         this.restoreBoard(player);
      }

      if (this.gameBoard != null) {
         for (String str : this.sidebarLines) {
            this.gameBoard.resetScores(str);
         }
      }

      this.sidebarLines.clear();

      for (ArmorStand armorStand2 : this.markers.values()) {
         armorStand2.remove();
      }

      this.markers.clear();

      for (World world : Bukkit.getWorlds()) {
         for (ArmorStand armorStand : world.getEntitiesByClass(ArmorStand.class)) {
            if (armorStand.getScoreboardTags().contains("bb_nametag_hider")) {
               armorStand.remove();
            }
         }
      }
   }
}
