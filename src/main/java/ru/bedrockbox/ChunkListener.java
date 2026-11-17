package ru.bedrockbox;

import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class ChunkListener implements Listener {
   private final BedrockBoxPlugin plugin;

   public ChunkListener(BedrockBoxPlugin bedrockBoxPlugin) {
      this.plugin = bedrockBoxPlugin;
   }

   @EventHandler
   public void onChunkSent(PlayerChunkLoadEvent playerChunkLoadEvent) {
      Player player = playerChunkLoadEvent.getPlayer();
      int n = playerChunkLoadEvent.getChunk().getX();
      int n2 = playerChunkLoadEvent.getChunk().getZ();
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         if (player.isOnline()) {
            this.plugin.antiCheat().cloak().onChunkLoad(player, n, n2);
         }
      }, 1L);
   }
}
