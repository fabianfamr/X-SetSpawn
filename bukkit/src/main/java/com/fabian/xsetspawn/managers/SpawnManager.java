package com.fabian.xsetspawn.managers;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.managers.storage.SpawnStorage;
import com.fabian.xsetspawn.utils.VisualUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * SpawnManager - Manages spawn locations by delegating to the current StorageManager.
 * Updated for v1.5 to support SQL and MongoDB backends, and premium effects.
 */
public class SpawnManager {

    private final XSetSpawn plugin;
    private final ManagerConfig config;
    private final SpawnStorage storage;

    public SpawnManager(XSetSpawn plugin) {
        this.plugin = plugin;
        this.config = plugin.getManagerConfig();
        this.storage = plugin.getStorageManager().getStorage();
    }

    public void setSpawn(Location location) {
        String id = getSpawnId(location.getWorld());
        storage.save(id, location);
    }

    public void setSpawn(World world, double x, double y, double z, float yaw, float pitch) {
        Location location = new Location(world, x, y, z, yaw, pitch);
        setSpawn(location);
    }

    public Location getSpawn(World world) {
        String id = getSpawnId(world);
        return storage.load(id);
    }

    private String getSpawnId(World world) {
        if (config.perWorld) {
            return "spawn-" + world.getName();
        } else {
            return "spawn";
        }
    }

    public boolean isSpawnSet(World world) {
        return storage.isSet(getSpawnId(world));
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
        storage.save(getNamedSpawnId(name), location);
    }

    public void setNamedSpawn(String name, World world, double x, double y, double z, float yaw, float pitch) {
        setNamedSpawn(name, new Location(world, x, y, z, yaw, pitch));
    }

    public Location getNamedSpawn(String name) {
        return storage.load(getNamedSpawnId(name));
    }

    public boolean isNamedSpawnSet(String name) {
        return storage.isSet(getNamedSpawnId(name));
    }

    public void removeNamedSpawn(String name) {
        storage.remove(getNamedSpawnId(name));
    }

    public void removeSpawn(World world) {
        storage.remove(getSpawnId(world));
    }

    /**
     * Returns display-friendly names of all custom (named) spawns.
     * Strips the 'spawn-custom-' prefix.
     */
    public java.util.List<String> getAllNamedSpawnNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String id : storage.getAllSpawnIds()) {
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
        return storage.getAllSpawnIds();
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
            Color color = resolveColorByName(config.fireworksColor);
            VisualUtil.spawnFirework(location.clone().add(0, 1, 0), color, config.fireworksPower);
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
        return storage.load("first-join-spawn");
    }

    public void setFirstJoinSpawn(Location location) {
        storage.save("first-join-spawn", location);
    }
}

