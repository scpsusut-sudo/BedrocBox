package ru.bedrockbox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

public final class CustomItems {
   public static final String DRILL = "drill";
   public static final String THROW_TNT = "throw_tnt";
   public static final String TRACKER = "tracker";
   public static final String FEATHER = "jump_feather";
   public static final String MEDKIT = "medkit";
   private static NamespacedKey KEY;
   private final BedrockBoxPlugin plugin;
   private final Map<UUID, Long> featherCooldown = new HashMap<>();
   private final Map<UUID, Long> noFallUntil = new HashMap<>();
   private static final int[][] SHAPE = new int[][]{{-1, 0}, {1, 0}, {0, 1}, {0, 2}};

   public CustomItems(BedrockBoxPlugin bedrockBoxPlugin) {
      this.plugin = bedrockBoxPlugin;
      KEY = new NamespacedKey(bedrockBoxPlugin, "item");
   }

   public static ItemStack create(String str, int n) {
      return switch (str) {
         case "drill" -> make(Material.DIAMOND_PICKAXE, n, str, "§b§lБур", "§7Ломает сразу 5 блоков фигурой:", "§7  ▮", "§7  ▮", "§7▮▮▮");
         case "throw_tnt" -> make(
         Material.FIRE_CHARGE, n, str, "§c§lМетательный динамит", "§7ПКМ - бросить зажжённый динамит", "§8Не работает в мирное время"
      );
         case "tracker" -> make(Material.COMPASS, n, str, "§e§lОхотник за кроватями", "§7ПКМ - указывает на ближайшую", "§7чужую кровать");
         case "jump_feather" -> make(Material.FEATHER, n, str, "§f§lПеро прыжка", "§7ПКМ - рывок вперёд и вверх", "§7Перезарядка 5 сек");
         case "medkit" -> make(Material.GLISTERING_MELON_SLICE, n, str, "§a§lАптечка", "§7ПКМ - восстановить 4 сердца");
         default -> null;
      };
   }

   private static ItemStack make(Material material, int n, String str, String str2, String... x) {
      ItemStack itemStack = new ItemStack(material, Math.max(1, n));
      ItemMeta itemMeta = itemStack.getItemMeta();
      if (itemMeta != null) {
         itemMeta.setDisplayName(str2);
         itemMeta.setLore(new ArrayList<>(Arrays.asList(x)));
         itemMeta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, str);
         itemStack.setItemMeta(itemMeta);
      }

      return itemStack;
   }

   public static String idOf(ItemStack itemStack) {
      if (itemStack != null && KEY != null && itemStack.hasItemMeta()) {
         ItemMeta itemMeta = itemStack.getItemMeta();
         return itemMeta == null ? null : itemMeta.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
      } else {
         return null;
      }
   }

   public boolean use(Player player2, ItemStack itemStack, Game game) {
      String str = idOf(itemStack);
      if (str == null) {
         return false;
      } else if (game.state() == Game.State.RUNNING && game.inGame(player2) && game.get(player2).alive) {
         switch (str) {
            case "throw_tnt":
               if (game.isGrace()) {
                  player2.sendMessage("§8[§6Коробка§8] §7§eВ мирное время динамит не бросить.");
                  return true;
               }

               Location location = player2.getEyeLocation();
               TNTPrimed tNTPrimed = player2.getWorld().spawn(location.clone().add(location.getDirection().multiply(0.8)), TNTPrimed.class);
               tNTPrimed.setFuseTicks(40);
               tNTPrimed.setSource(player2);
               tNTPrimed.setVelocity(location.getDirection().multiply(1.3));
               consume(itemStack);
               player2.playSound(player2.getLocation(), "entity.tnt.primed", 1.0F, 1.0F);
               break;
            case "tracker":
               PlayerData.BedRecord bedRecord = game.nearestEnemyBed(player2);
               if (bedRecord == null) {
                  player2.sendMessage("§8[§6Коробка§8] §7§7Чужих кроватей не осталось.");
                  return true;
               }

               player2.setCompassTarget(bedRecord.foot.getLocation());
               player2.sendMessage("§8[§6Коробка§8] §7§eСтрелка ведёт к чужой кровати.");
               break;
            case "jump_feather":
               long tmp = System.currentTimeMillis();
               Long l = this.featherCooldown.get(player2.getUniqueId());
               if (l != null && l > tmp) {
                  player2.sendMessage("§8[§6Коробка§8] §7§7Перо перезаряжается: §e" + ((l - tmp) / 1000L + 1L) + " сек");
                  return true;
               }

               this.featherCooldown.put(player2.getUniqueId(), tmp + 5000L);
               this.noFallUntil.put(player2.getUniqueId(), tmp + 4000L);
               Vector vector = player2.getEyeLocation().getDirection().multiply(1.4);
               vector.setY(Math.max(0.6, vector.getY()));
               player2.setVelocity(vector);
               player2.playSound(player2.getLocation(), "entity.ender_dragon.flap", 0.8F, 1.4F);
               break;
            case "medkit":
               if (player2.getHealth() >= 20.0) {
                  player2.sendMessage("§8[§6Коробка§8] §7§7Ты и так здоров.");
                  return true;
               }

               player2.setHealth(Math.min(20.0, player2.getHealth() + 8.0));
               consume(itemStack);
               player2.playSound(player2.getLocation(), "entity.player.levelup", 0.6F, 1.6F);
               break;
            default:
               return false;
         }

         return true;
      } else {
         return true;
      }
   }

   public boolean noFall(Player player) {
      Long l = this.noFallUntil.get(player.getUniqueId());
      return l != null && l > System.currentTimeMillis();
   }

   private static void consume(ItemStack itemStack) {
      itemStack.setAmount(itemStack.getAmount() - 1);
   }

   public static int[][] shapeOf(ItemStack itemStack) {
      String str = idOf(itemStack);
      return "drill".equals(str) ? SHAPE : null;
   }

   public void drill(Player player, Block block, Game game) {
      int[][] arr = shapeOf(player.getInventory().getItemInMainHand());
      if (arr != null) {
         float f = player.getLocation().getPitch();
         float f2 = (player.getLocation().getYaw() % 360.0F + 360.0F) % 360.0F;
         ArrayList list = new ArrayList();

         for (int[] arr2 : arr) {
            int n = arr2[0];
            int n2 = arr2[1];
            int n3 = block.getX();
            int n4 = block.getY();
            int n5 = block.getZ();
            if (!(f > 50.0F) && !(f < -50.0F)) {
               if ((!(f2 > 45.0F) || !(f2 < 135.0F)) && (!(f2 > 225.0F) || !(f2 < 315.0F))) {
                  n3 += n;
                  n4 += n2;
               } else {
                  n5 += n;
                  n4 += n2;
               }
            } else {
               n3 += n;
               n5 += n2;
            }

            list.add(block.getWorld().getBlockAt(n3, n4, n5));
         }

         ItemStack itemStack = player.getInventory().getItemInMainHand();

         for (Block block2 : (Iterable<Block>) list) {
            Material material = block2.getType();
            if (material != Material.AIR
               && material != Material.BEDROCK
               && !material.name().endsWith("_BED")
               && material != Material.GLOWSTONE
               && game.arena().isInside(block2)
               && game.bedAt(block2) == null
               && game.canMine(player, block2, itemStack)) {
               if (game.giveMined(player, block2, itemStack)) {
                  block2.setType(Material.AIR, true);
                  this.plugin.antiCheat().cloak().onDug(block2, player.getUniqueId(), game.alivePlayers(), this.plugin);
               }
            }
         }
      }
   }
}
