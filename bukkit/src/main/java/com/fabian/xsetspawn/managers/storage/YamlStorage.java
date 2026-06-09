package com.fabian.xsetspawn.managers.storage;

import com.fabian.xsetspawn.XSetSpawn;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * YamlStorage - YAML implementation of SpawnStorage.
 * Saves each spawn location to its own file in the 'spawns/' folder.
 */
public class YamlStorage implements SpawnStorage {

    private final XSetSpawn plugin;
    private final File spawnsFolder;

    public YamlStorage(XSetSpawn plugin) {
        this.plugin = plugin;
        plugin.log("&eConnecting to YAML database...");
        this.spawnsFolder = new File(plugin.getDataFolder(), "spawns");
        if (!spawnsFolder.exists()) {
            spawnsFolder.mkdirs();
        }
        migrateFromConfig();
        plugin.log("&aYAML database connected and ready.");
    }

    /**
     * One-time migration of spawns from config.yml to the new spawns/ folder.
     */
    private void migrateFromConfig() {
        java.util.Set<String> processedKeys = new java.util.HashSet<>();
        // 1. Check current config.yml
        FileConfiguration config = plugin.getConfig();
        boolean migrated = migrate(config, "config.yml", processedKeys);

        // 2. Check for backup created by ConfigManager during 1.5 update
        File[] files = plugin.getDataFolder().listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().startsWith("config_backup_") || file.getName().equals("config_old.yml")) {
                    FileConfiguration backupConfig = YamlConfiguration.loadConfiguration(file);
                    if (migrate(backupConfig, file.getName(), processedKeys)) {
                        migrated = true;
                    }
                }
            }
        }

        if (migrated) {
            plugin.log("&aMigration of spawns completed successfully!");
        }
    }

    private boolean migrate(FileConfiguration config, String fileName, java.util.Set<String> processedKeys) {
        boolean migrated = false;
        for (String key : config.getKeys(false)) {
            // Identify spawns: "spawn", "spawn-world", "first-join-spawn"
            if (key.equals("spawn") || key.startsWith("spawn-") || key.equals("first-join-spawn")) {
                // Only attempt migration if it actually holds location data and wasn't already processed
                if (config.contains(key + ".world") && !processedKeys.contains(key) && !isSet(key).join()) {
                    plugin.log("Migrating spawn '&e" + key + "&7' from &e" + fileName + "&7...");
                    processedKeys.add(key); // Mark as processed to prevent redundant loops
                    Location loc = loadFromConfig(config, key);
                    if (loc != null) {
                        save(key, loc).join();
                        migrated = true;
                    } else {
                        plugin.getLogger().warning("Failed to migrate '" + key + "' because the world does not exist or coords are invalid.");
                    }
                }
            }
        }
        return migrated;
    }

    private Location loadFromConfig(FileConfiguration config, String id) {
        if (!config.contains(id)) return null;
        String worldName = config.getString(id + ".world");
        
        // Safety check to avoid IllegalArgumentException: name cannot be null
        if (worldName == null || worldName.isEmpty()) return null;
        
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        return new Location(world,
                config.getDouble(id + ".x"),
                config.getDouble(id + ".y"),
                config.getDouble(id + ".z"),
                (float) config.getDouble(id + ".yaw"),
                (float) config.getDouble(id + ".pitch"));
    }

    @Override
    public CompletableFuture<Void> save(String id, Location location) {
        return CompletableFuture.supplyAsync(() -> {
            File spawnFile = new File(spawnsFolder, id + ".yml");
            FileConfiguration spawnConfig = new YamlConfiguration();

            spawnConfig.set("world", location.getWorld().getName());
            spawnConfig.set("x", location.getX());
            spawnConfig.set("y", location.getY());
            spawnConfig.set("z", location.getZ());
            spawnConfig.set("pitch", location.getPitch());
            spawnConfig.set("yaw", location.getYaw());

            try {
                spawnConfig.save(spawnFile);
            } catch (IOException e) {
                plugin.logError("Could not save spawn file " + id + ".yml: " + e.getMessage());
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Location> load(String id) {
        return CompletableFuture.supplyAsync(() -> {
            File spawnFile = new File(spawnsFolder, id + ".yml");
            if (!spawnFile.exists()) return null;

            FileConfiguration spawnConfig = YamlConfiguration.loadConfiguration(spawnFile);
            String worldName = spawnConfig.getString("world");
            World world = Bukkit.getWorld(worldName);

            if (world == null) {
                plugin.getLogger().warning("World " + worldName + " not found for spawn: " + id);
                return null;
            }

            double x = spawnConfig.getDouble("x");
            double y = spawnConfig.getDouble("y");
            double z = spawnConfig.getDouble("z");
            float pitch = (float) spawnConfig.getDouble("pitch");
            float yaw = (float) spawnConfig.getDouble("yaw");

            return new Location(world, x, y, z, yaw, pitch);
        });
    }

    @Override
    public CompletableFuture<Boolean> isSet(String id) {
        return CompletableFuture.supplyAsync(() -> new File(spawnsFolder, id + ".yml").exists());
    }

    @Override
    public CompletableFuture<Void> remove(String id) {
        return CompletableFuture.supplyAsync(() -> {
            File spawnFile = new File(spawnsFolder, id + ".yml");
            if (spawnFile.exists() && !spawnFile.delete()) {
                plugin.logError("Could not delete spawn file: " + id + ".yml");
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<java.util.List<String>> getAllSpawnIds() {
        return CompletableFuture.supplyAsync(() -> {
            java.util.List<String> ids = new java.util.ArrayList<>();
            File[] files = spawnsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    ids.add(file.getName().replace(".yml", ""));
                }
            }
            return ids;
        });
    }

    @Override
    public CompletableFuture<java.util.Map<String, Location>> loadAll() {
        return CompletableFuture.supplyAsync(() -> {
            java.util.Map<String, Location> map = new java.util.HashMap<>();
            try {
                java.util.List<String> ids = getAllSpawnIds().join();
                for (String id : ids) {
                    Location loc = load(id).join();
                    if (loc != null) {
                        map.put(id, loc);
                    }
                }
            } catch (Exception e) {
                plugin.logError("Error loading Yaml spawns: " + e.getMessage());
            }
            return map;
        });
    }

    @Override
    public void close() {
        // Nothing to close
    }
}

