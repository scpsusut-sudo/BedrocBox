package ru.bedrockbox.util;

import java.util.logging.Level;
import org.bukkit.Bukkit;

public final class Log {
   private Log() {
   }

   public static void ignored(Throwable t) {
      Bukkit.getLogger().log(Level.FINE, "ignored: " + t, t);
   }
}
