package com.fabian.xsetspawn.managers;

import com.fabian.xsetspawn.XSetSpawn;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.HashMap;

/**
 * BackManager - Stores players' previous locations in RAM.
 * Used by /back to return to the location before using /spawn.
 * Locations expire after the configured time (back.expires in config.yml).
 */
public class BackManager {

    private final XSetSpawn plugin;

    private static class BackEntry {
        final Location location;
        final long timestamp; // System.currentTimeMillis() when stored

        BackEntry(Location location) {
            this.location = location;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private final Map<UUID, BackEntry> backLocations = new HashMap<>();

    public BackManager(XSetSpawn plugin) {
        this.plugin = plugin;
    }

    /**
     * Saves the player's current location as their "back" position.
     * Called just before teleporting to spawn.
     */
    public void saveLocation(Player player) {
        if (!plugin.getManagerConfig().backEnabled) return;
        backLocations.put(player.getUniqueId(), new BackEntry(player.getLocation().clone()));
    }

    /**
     * Returns the saved back location for a player, or null if not found / expired.
     */
    public Location getLocation(Player player) {
        BackEntry entry = backLocations.get(player.getUniqueId());
        if (entry == null) return null;

        int expiresMinutes = plugin.getManagerConfig().backExpires;
        if (expiresMinutes > 0) {
            long expiredAt = entry.timestamp + (expiresMinutes * 60_000L);
            if (System.currentTimeMillis() > expiredAt) {
                backLocations.remove(player.getUniqueId());
                return null;
            }
        }

        return entry.location;
    }

    /**
     * Removes the saved location for a player (e.g. after using /back).
     */
    public void clearLocation(Player player) {
        backLocations.remove(player.getUniqueId());
    }

    /**
     * Removes the saved location by UUID (used on player quit to prevent memory leaks).
     */
    public void clearLocation(UUID uuid) {
        backLocations.remove(uuid);
    }

    /**
     * Returns true if the player has a valid (non-expired) back location.
     */
    public boolean hasLocation(Player player) {
        return getLocation(player) != null;
    }
}
