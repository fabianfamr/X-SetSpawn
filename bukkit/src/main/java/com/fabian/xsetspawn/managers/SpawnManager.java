package com.fabian.xsetspawn.managers;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.managers.storage.SpawnStorage;
import com.fabian.xsetspawn.utils.VisualUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * SpawnManager - Manages spawn locations by delegating to the current StorageManager.
 * Updated for v1.5 to support SQL and MongoDB backends, and premium effects.
 */
public class SpawnManager {

    private final XSetSpawn plugin;
    private final ManagerConfig config;
    private final SpawnStorage storage;
    private final Map<String, Location> spawnCache = new java.util.concurrent.ConcurrentHashMap<>();
    private Color cachedFireworkColor = Color.BLUE;

    public SpawnManager(XSetSpawn plugin) {
        this.plugin = plugin;
        this.config = plugin.getManagerConfig();
        this.storage = plugin.getStorageManager().getStorage();
        this.cachedFireworkColor = resolveColorByName(config.fireworksColor);
        // loadCaches() is now called asynchronously by the storage backends once connected.
    }

    /**
     * @deprecated Use {@link #loadCachesAsync()} instead. This method blocks the calling thread.
     */
    @Deprecated
    public void loadCaches() {
        plugin.log("&eWarning: loadCaches() called on main thread. Use loadCachesAsync().");
        spawnCache.clear();
        java.util.Map<String, Location> allSpawns = storage.loadAll().join();
        if (allSpawns != null) {
            spawnCache.putAll(allSpawns);
        }
    }

    public void loadCachesAsync() {
        loadCachesAsync(null);
    }

    public void loadCachesAsync(Runnable callback) {
        storage.loadAll().thenAccept(allSpawns -> {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                if (allSpawns != null) {
                    spawnCache.clear();
                    spawnCache.putAll(allSpawns);
                } else {
                    spawnCache.clear();
                }
                plugin.log("&aLoaded " + spawnCache.size() + " spawns.");
                if (callback != null) {
                    callback.run();
                }
            });
        });
    }

    public void setSpawn(Location location) {
        String id = getSpawnId(location.getWorld());
        spawnCache.put(id, location);
        storage.save(id, location).exceptionally(ex -> {
            plugin.logError("Failed to save spawn " + id + ": " + ex.getMessage());
            return null;
        });
    }

    public void setSpawn(World world, double x, double y, double z, float yaw, float pitch) {
        Location location = new Location(world, x, y, z, yaw, pitch);
        setSpawn(location);
    }

    public Location getSpawn(World world) {
        String id = getSpawnId(world);
        return spawnCache.get(id);
    }

    private String getSpawnId(World world) {
        if (config.perWorld) {
            return "spawn-" + world.getName();
        } else {
            return "spawn";
        }
    }

    public boolean isSpawnSet(World world) {
        return spawnCache.containsKey(getSpawnId(world));
    }

    // ==========================================
    // Named Spawn Methods (Named Spawns feature)
    // ==========================================

    /**
     * Returns the ID key used to store a named spawn.
     */
    private String getNamedSpawnId(String name) {
        return "spawn-custom-" + name.toLowerCase();
    }

    public void setNamedSpawn(String name, Location location) {
        String id = getNamedSpawnId(name);
        spawnCache.put(id, location);
        storage.save(id, location).exceptionally(ex -> {
            plugin.logError("Failed to save named spawn " + id + ": " + ex.getMessage());
            return null;
        });
    }

    public void setNamedSpawn(String name, World world, double x, double y, double z, float yaw, float pitch) {
        setNamedSpawn(name, new Location(world, x, y, z, yaw, pitch));
    }

    public Location getNamedSpawn(String name) {
        return spawnCache.get(getNamedSpawnId(name));
    }

    public boolean isNamedSpawnSet(String name) {
        return spawnCache.containsKey(getNamedSpawnId(name));
    }

    public void removeNamedSpawn(String name) {
        String id = getNamedSpawnId(name);
        spawnCache.remove(id);
        storage.remove(id);
    }

    public void removeSpawn(World world) {
        String id = getSpawnId(world);
        spawnCache.remove(id);
        storage.remove(id);
    }

    /**
     * Returns display-friendly names of all custom (named) spawns.
     * Strips the 'spawn-custom-' prefix.
     */
    public java.util.List<String> getAllNamedSpawnNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String id : spawnCache.keySet()) {
            if (id.startsWith("spawn-custom-")) {
                names.add(id.substring("spawn-custom-".length()));
            }
        }
        return names;
    }

    /**
     * Returns ALL spawn IDs (including defaults) from storage.
     */
    public java.util.List<String> getAllSpawnIds() {
        return new java.util.ArrayList<>(spawnCache.keySet());
    }

    public void playSpawnSound(Player player, Location location) {
        if (!config.soundsEnabled)
            return;

        String soundName = config.spawnSound;
        org.bukkit.Sound sound = com.fabian.xsetspawn.utils.SoundUtil.resolveSound(soundName);

        if (sound != null) {
            player.playSound(location, sound, config.soundVolume, config.soundPitch);
        } else {
            plugin.getLogger().warning("Invalid sound name in config: " + soundName);
        }

        // Fireworks
        if (config.fireworksEnabled) {
            VisualUtil.spawnFirework(plugin, location.clone().add(0, 1, 0), cachedFireworkColor, config.fireworksPower);
        }
    }

    private Color resolveColorByName(String colorName) {
        try {
            switch (colorName.toUpperCase()) {
                case "RED": return Color.RED;
                case "GREEN": return Color.GREEN;
                case "BLUE": return Color.BLUE;
                case "YELLOW": return Color.YELLOW;
                case "WHITE": return Color.WHITE;
                case "PURPLE": return Color.PURPLE;
                case "ORANGE": return Color.ORANGE;
                case "LIME": return Color.LIME;
                default: return Color.BLUE;
            }
        } catch (Exception e) {
            return Color.BLUE;
        }
    }

    public Location getFirstJoinSpawn() {
        return spawnCache.get("first-join-spawn");
    }

    public void setFirstJoinSpawn(Location location) {
        spawnCache.put("first-join-spawn", location);
        storage.save("first-join-spawn", location).exceptionally(ex -> {
            plugin.logError("Failed to save first-join spawn: " + ex.getMessage());
            return null;
        });
    }
}

