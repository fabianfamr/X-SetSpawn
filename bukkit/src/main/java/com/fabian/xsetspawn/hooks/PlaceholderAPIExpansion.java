package com.fabian.xsetspawn.hooks;

import com.fabian.xsetspawn.XSetSpawn;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class PlaceholderAPIExpansion extends PlaceholderExpansion {

    private final XSetSpawn plugin;

    public PlaceholderAPIExpansion(XSetSpawn plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String getIdentifier() {
        return "xsetspawn";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        if (offlinePlayer == null || !offlinePlayer.isOnline()) return null;
        Player player = offlinePlayer.getPlayer();

        if (params.equalsIgnoreCase("cooldown")) {
            return String.valueOf(plugin.getCooldownManager().getRemainingTime(player));
        }

        if (params.equalsIgnoreCase("delay")) {
            return String.valueOf(plugin.getManagerConfig().delayTime);
        }

        if (params.equalsIgnoreCase("is_pending")) {
            return String.valueOf(plugin.getDelayManager().isPending(player));
        }

        if (params.equalsIgnoreCase("spawn_set")) {
            return String.valueOf(plugin.getSpawnManager().isSpawnSet(player.getWorld()));
        }

        return null; // Placeholder not found
    }
}

