package com.fabian.xsetspawn.hooks;

import com.fabian.xsetspawn.XSetSpawn;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Location;
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

        // ---- Cooldown ----
        if (params.equalsIgnoreCase("cooldown")) {
            return String.valueOf(plugin.getCooldownManager().getRemainingTime(player));
        }

        // ---- Delay ----
        if (params.equalsIgnoreCase("delay")) {
            return String.valueOf(plugin.getManagerConfig().delayTime);
        }

        // ---- Is pending teleport? ----
        if (params.equalsIgnoreCase("is_pending")) {
            return String.valueOf(plugin.getDelayManager().isPending(player));
        }

        // ---- Spawn info ----
        if (params.equalsIgnoreCase("spawn_set")) {
            return String.valueOf(plugin.getSpawnManager().isSpawnSet(player.getWorld()));
        }

        // ---- Spawn coordinates ----
        if (params.equalsIgnoreCase("spawn_x") || params.startsWith("spawn_x")) {
            Location loc = plugin.getSpawnManager().getSpawn(player.getWorld());
            return loc != null ? String.valueOf(Math.floor(loc.getX() * 10) / 10) : "N/A";
        }
        if (params.equalsIgnoreCase("spawn_y") || params.startsWith("spawn_y")) {
            Location loc = plugin.getSpawnManager().getSpawn(player.getWorld());
            return loc != null ? String.valueOf(Math.floor(loc.getY() * 10) / 10) : "N/A";
        }
        if (params.equalsIgnoreCase("spawn_z") || params.startsWith("spawn_z")) {
            Location loc = plugin.getSpawnManager().getSpawn(player.getWorld());
            return loc != null ? String.valueOf(Math.floor(loc.getZ() * 10) / 10) : "N/A";
        }
        if (params.equalsIgnoreCase("spawn_world")) {
            Location loc = plugin.getSpawnManager().getSpawn(player.getWorld());
            return loc != null && loc.getWorld() != null ? loc.getWorld().getName() : "N/A";
        }
        if (params.equalsIgnoreCase("spawn_coords")) {
            Location loc = plugin.getSpawnManager().getSpawn(player.getWorld());
            if (loc == null || loc.getWorld() == null) return "N/A";
            return Math.floor(loc.getX() * 10) / 10 + ", " + Math.floor(loc.getY() * 10) / 10 + ", " + Math.floor(loc.getZ() * 10) / 10;
        }

        // ---- Back info ----
        if (params.equalsIgnoreCase("back_available")) {
            return String.valueOf(plugin.getBackManager().hasLocation(player));
        }

        if (params.equalsIgnoreCase("back_remaining")) {
            return String.valueOf(plugin.getBackManager().hasLocation(player) ? "yes" : "no");
        }

        if (params.equalsIgnoreCase("back_world")) {
            Location loc = plugin.getBackManager().getLocation(player);
            return loc != null && loc.getWorld() != null ? loc.getWorld().getName() : "N/A";
        }

        if (params.equalsIgnoreCase("back_x")) {
            Location loc = plugin.getBackManager().getLocation(player);
            return loc != null ? String.valueOf(Math.floor(loc.getX() * 10) / 10) : "N/A";
        }
        if (params.equalsIgnoreCase("back_y")) {
            Location loc = plugin.getBackManager().getLocation(player);
            return loc != null ? String.valueOf(Math.floor(loc.getY() * 10) / 10) : "N/A";
        }
        if (params.equalsIgnoreCase("back_z")) {
            Location loc = plugin.getBackManager().getLocation(player);
            return loc != null ? String.valueOf(Math.floor(loc.getZ() * 10) / 10) : "N/A";
        }

        if (params.equalsIgnoreCase("back_coords")) {
            Location loc = plugin.getBackManager().getLocation(player);
            if (loc == null || loc.getWorld() == null) return "N/A";
            return Math.floor(loc.getX() * 10) / 10 + ", " + Math.floor(loc.getY() * 10) / 10 + ", " + Math.floor(loc.getZ() * 10) / 10;
        }

        // ---- Back expiry (seconds remaining) ----
        if (params.equalsIgnoreCase("back_expiry")) {
            Location loc = plugin.getBackManager().getLocation(player);
            if (loc == null) return "0";
            // Calculate remaining seconds from expires config
            int expiresMinutes = plugin.getManagerConfig().backExpires;
            if (expiresMinutes <= 0) return "never";
            return String.valueOf(expiresMinutes * 60);
        }

        // ---- Named spawns count ----
        if (params.equalsIgnoreCase("named_count")) {
            return String.valueOf(plugin.getSpawnManager().getAllNamedSpawnNames().size());
        }

        // ---- Per-world spawn info (xsetspawn_spawn_world:<name>) ----
        if (params.startsWith("spawn_world_name:")) {
            String worldName = params.substring("spawn_world_name:".length());
            org.bukkit.World world = plugin.getServer().getWorld(worldName);
            if (world == null) return "N/A";
            return String.valueOf(plugin.getSpawnManager().isSpawnSet(world));
        }

        return null; // Placeholder not found
    }
}
