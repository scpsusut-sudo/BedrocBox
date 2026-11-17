package ru.bedrockbox.shop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.Map.Entry;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionType;
import org.bukkit.Registry;
import ru.bedrockbox.game.Game;

public final class Shop {
   private final String section;
   private final String title;
   private final Map<Integer, Shop.Offer> offers = new HashMap<>();

   public Shop(String str, String str2) {
      this.section = str;
      this.title = str2;
   }

   public void load(FileConfiguration fileConfiguration, Logger logger) {
      this.offers.clear();
      ConfigurationSection configurationSection = fileConfiguration.getConfigurationSection(this.section);
      if (configurationSection != null) {
         for (String str : configurationSection.getKeys(false)) {
            ConfigurationSection configurationSection2 = configurationSection.getConfigurationSection(str);
            if (configurationSection2 != null) {
               Material material2 = Material.matchMaterial(configurationSection2.getString("price.material", "COBBLESTONE"));
               String str2 = configurationSection2.getString("custom");
               ItemStack itemStack;
               if (str2 != null) {
                  itemStack = CustomItems.create(str2, configurationSection2.getInt("amount", 1));
               } else {
                  Material material3 = Material.matchMaterial(configurationSection2.getString("material", ""));
                  itemStack = material3 == null ? null : new ItemStack(material3, Math.max(1, configurationSection2.getInt("amount", 1)));
               }

               if (itemStack != null && material2 != null) {
                  ItemMeta itemMeta = str2 != null ? null : itemStack.getItemMeta();
                  if (itemMeta != null) {
                     String str3 = configurationSection2.getString("name");
                     if (str3 != null) {
                        itemMeta.setDisplayName(str3);
                     }

                     ConfigurationSection configurationSection3 = configurationSection2.getConfigurationSection("enchants");
                     if (configurationSection3 != null) {
                        for (String str4 : configurationSection3.getKeys(false)) {
                           Enchantment enchantment = findEnchant(str4);
                           if (enchantment != null) {
                              itemMeta.addEnchant(enchantment, configurationSection3.getInt(str4, 1), true);
                           } else {
                              logger.warning("Магазин: неизвестные чары '" + str4 + "'");
                           }
                        }
                     }

                     String str5 = configurationSection2.getString("potion");
                     if (str5 != null && itemMeta instanceof PotionMeta potionMeta) {
                        try {
                           potionMeta.setBasePotionType(PotionType.valueOf(str5.toUpperCase()));
                        } catch (IllegalArgumentException illegalArgumentException) {
                           logger.warning("Магазин: неизвестное зелье '" + str5 + "' в '" + str + "'");
                        }
                     }

                     itemStack.setItemMeta(itemMeta);
                  }

                  this.offers.put(configurationSection2.getInt("slot", this.offers.size()), new Shop.Offer(itemStack, material2, Math.max(1, configurationSection2.getInt("price.amount", 1))));
               } else {
                  logger.warning("Магазин: неизвестный предмет в '" + str + "'");
               }
            }
         }
      }
   }

   private static Enchantment findEnchant(String str) {
      try {
         return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(str.toLowerCase()));
      } catch (Throwable t) {
         return null;
      }
   }

   public void open(Player player) {
      Shop.Holder holder = new Shop.Holder();
      Inventory inventory2 = Bukkit.createInventory(holder, 54, this.title);
      holder.inventory = inventory2;
      holder.shop = this;

      for (Entry entry : this.offers.entrySet()) {
         int n = (Integer)entry.getKey();
         if (n >= 0 && n < 54) {
            Shop.Offer offer = (Shop.Offer)entry.getValue();
            ItemStack itemStack = offer.item().clone();
            ItemMeta itemMeta = itemStack.getItemMeta();
            if (itemMeta != null) {
               ArrayList list = new ArrayList();
               list.add("§7Цена: §e" + offer.price() + " " + currencyName(offer.priceMaterial()));
               list.add("§aНажми, чтобы купить");
               itemMeta.setLore(list);
               itemStack.setItemMeta(itemMeta);
            }

            inventory2.setItem(n, itemStack);
         }
      }

      player.openInventory(inventory2);
   }

   public void buy(Player player, int n) {
      Shop.Offer offer = this.offers.get(n);
      if (offer != null) {
         ItemStack itemStack = new ItemStack(offer.priceMaterial(), 1);
         if (!player.getInventory().containsAtLeast(itemStack, offer.price())) {
            player.sendMessage(Game.PREFIX + "§cНе хватает: нужно §e" + offer.price() + " " + currencyName(offer.priceMaterial()));
         } else {
            player.getInventory().removeItem(new ItemStack[]{new ItemStack(offer.priceMaterial(), offer.price())});
            HashMap map = player.getInventory().addItem(new ItemStack[]{offer.item().clone()});

            for (ItemStack itemStack2 : (Iterable<ItemStack>) map.values()) {
               player.getWorld().dropItemNaturally(player.getLocation(), itemStack2);
            }

            player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1.0F, 1.2F);
         }
      }
   }

   public static String currencyName(Material tmp) {
      return switch (tmp) {
         case COBBLESTONE -> "булыжника";
         case IRON_INGOT -> "железа";
         case GOLD_INGOT -> "золота";
         case DIAMOND -> "алмазов";
         case EMERALD -> "изумрудов";
         case COAL -> "угля";
         default -> tmp.name().toLowerCase();
      };
   }

   public static final class Holder implements InventoryHolder {
      private Inventory inventory;
      private Shop shop;

      public Shop shop() {
         return this.shop;
      }

      @Override
      public Inventory getInventory() {
         return this.inventory;
      }
   }

   private static record Offer(ItemStack item, Material priceMaterial, int price) {
   }
}
