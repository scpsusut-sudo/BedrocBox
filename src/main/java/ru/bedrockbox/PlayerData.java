package ru.bedrockbox;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.block.Block;

public final class PlayerData {
   public final UUID id;
   public final String name;
   public final Location returnLocation;
   public List<PlayerData.BedRecord> beds = new ArrayList<>();
   public int team = -1;
   public int prefTeam = -1;
   public boolean alive = true;
   public Location home;
   public int kills = 0;

   public PlayerData(UUID uUID, String str, Location location) {
      this.id = uUID;
      this.name = str;
      this.returnLocation = location;
   }

   public boolean hasBed() {
      return !this.beds.isEmpty();
   }

   public Location respawnPoint() {
      return this.beds.isEmpty() ? null : this.beds.get(this.beds.size() - 1).spawn;
   }

   public static final class BedRecord {
      public final UUID owner;
      public final Block foot;
      public final Block head;
      public final Location spawn;

      public BedRecord(UUID uUID, Block block, Block block2, Location location) {
         this.owner = uUID;
         this.foot = block;
         this.head = block2;
         this.spawn = location;
      }
   }
}
