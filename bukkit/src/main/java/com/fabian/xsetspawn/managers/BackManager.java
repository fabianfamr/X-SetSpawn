package com.fabian.xsetspawn.managers;

import com.fabian.xsetspawn.XSetSpawn;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BackManager - Stores players' previous locations in RAM.
 * Used by /back to return to the location before using /spawn or dying.
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

    private final Map<UUID, BackEntry> backLocations = new ConcurrentHashMap<>();

    public BackManager(XSetSpawn plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts a periodic cleanup task to remove expired back locations from memory.
     */
    public void startCleanupTask() {
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            int expiresMinutes = plugin.getManagerConfig().backExpires;
            if (expiresMinutes <= 0) return;
            backLocations.entrySet().removeIf(entry -> {
                long expiredAt = entry.getValue().timestamp + (expiresMinutes * 60_000L);
                return now > expiredAt;
            });
        }, 20 * 60L, 20 * 60L); // Every 60 seconds
    }

    /**
     * Saves the player's current location as their "back" position.
     * Called just before teleporting the player via /spawn or on death respawn.
     *
     * @param player    the player being teleported
     * @param overwrite if true, always overwrite; if false, keep existing entry
     */
    public void saveLocation(Player player, boolean overwrite) {
        if (!plugin.getManagerConfig().backEnabled) return;
        if (player == null || player.getLocation() == null) return;
        UUID uuid = player.getUniqueId();
        if (!overwrite && backLocations.containsKey(uuid)) return;
        backLocations.put(uuid, new BackEntry(player.getLocation().clone()));
    }

    /**
     * Convenience — always overwrite.
     */
    public void saveLocation(Player player) {
        saveLocation(player, true);
    }

    /**
     * Returns the saved back location for a player, or null if not found / expired / world unloaded.
     * Verifies that the destination world is still loaded and safe.
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

        Location loc = entry.location;
        World world = loc.getWorld();
        if (world == null || !Bukkit.getWorlds().contains(world)) {
            // World was unloaded — remove stale entry
            backLocations.remove(player.getUniqueId());
            return null;
        }

        // Verify the chunk is loaded so the teleport won't fail silently
        if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            world.loadChunk(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
        }

        return loc;
    }

    /**
     * Removes the saved location for a player (e.g. after using /back).
     */
    public void clearLocation(Player player) {
        if (player != null) backLocations.remove(player.getUniqueId());
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
