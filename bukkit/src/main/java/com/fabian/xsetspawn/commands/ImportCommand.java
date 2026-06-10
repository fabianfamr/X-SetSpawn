package com.fabian.xsetspawn.commands;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.utils.DebugLogger;
import com.fabian.xsetspawn.managers.LanguageManager;
import com.fabian.xsetspawn.managers.Permission;
import com.fabian.xsetspawn.managers.SpawnManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Handles /xss import <source> — imports spawn locations from other popular plugins.
 * Supported sources: Essentials, CMI, SpawnPlus, SpawnX, SpawnControl, DeluxeSpawn, JellySpawn
 */
public class ImportCommand {

    private final XSetSpawn plugin;
    private final LanguageManager lang;

    // Source names for tab completion
    public static final List<String> SOURCES = Collections.unmodifiableList(new ArrayList<String>() {{
        add("essentials");
        add("cmi");
        add("spawnplus");
        add("spawnx");
        add("spawncontrol");
        add("deluxespawn");
        add("jellyspawn");
    }});

    public ImportCommand(XSetSpawn plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    /**
     * Execute the import command.
     */
    public boolean execute(CommandSender sender, String[] args) {
        String source = args.length > 0 ? args[0] : "none";
        DebugLogger.debug("Command", "/xss import " + source + " executed by " + sender.getName());
        if (args.length < 1) {
            showUsage(sender);
            return true;
        }

        String source = args[0].toLowerCase();
        boolean force = args.length > 1 && args[1].equalsIgnoreCase("--force");

        // Check if source is already imported (unless --force)
        if (!force && plugin.getConfig().getBoolean("imported." + source, false)) {
            sender.sendMessage(lang.getMessage("import-already-done", source));
            sender.sendMessage(lang.getMessage("import-force-hint"));
            return true;
        }

        int imported = 0;
        switch (source) {
            case "essentials":
                imported = importFromEssentials(sender);
                break;
            case "cmi":
                imported = importFromCMI(sender);
                break;
            case "spawnplus":
                imported = importFromSpawnPlus(sender);
                break;
            case "spawnx":
                imported = importFromSpawnX(sender);
                break;
            case "spawncontrol":
                imported = importFromSpawnControl(sender);
                break;
            case "deluxespawn":
                imported = importFromDeluxeSpawn(sender);
                break;
            case "jellyspawn":
                imported = importFromJellySpawn(sender);
                break;
            default:
                sender.sendMessage(lang.getMessage("import-source-unknown", source));
                showAvailable(sender);
                return true;
        }

        if (imported > 0) {
            DebugLogger.debug("Command", "Imported " + imported + " spawns from " + source);
            // Mark as imported
            plugin.getConfig().set("imported." + source, true);
            plugin.saveConfig();
            sender.sendMessage(lang.getMessage("import-success", imported, source));
            // Reload spawn cache
            plugin.getSpawnManager().loadCachesAsync(() ->
                sender.sendMessage(lang.getMessage("import-reloaded")));
        } else {
            sender.sendMessage(lang.getMessage("import-no-spawns", source));
        }

        return true;
    }

    public List<String> getTabCompletions(String[] args) {
        if (args.length == 1) {
            List<String> matches = new ArrayList<>();
            for (String s : SOURCES) {
                if (s.startsWith(args[0].toLowerCase())) matches.add(s);
            }
            return matches;
        }
        if (args.length == 2) {
            List<String> flags = new ArrayList<>();
            flags.add("--force");
            return flags;
        }
        return Collections.emptyList();
    }

    // ================================================================
    //  Source: EssentialsX / Essentials
    //  File: plugins/Essentials/spawn.yml
    // ================================================================
    private int importFromEssentials(CommandSender sender) {
        File file = new File("plugins/Essentials/spawn.yml");
        if (!file.exists()) {
            sender.sendMessage(lang.getMessage("import-file-not-found", "Essentials", file.getPath()));
            return 0;
        }
        try {
            YamlConfiguration yc = YamlConfiguration.loadConfiguration(file);
            Location spawn = parseEssentialsLocation(yc);
            if (spawn == null || spawn.getWorld() == null) {
                sender.sendMessage(lang.getMessage("import-parse-error", "Essentials"));
                return 0;
            }
            plugin.getSpawnManager().setSpawn(spawn);
            return 1;
        } catch (Exception e) {
            sender.sendMessage(lang.getMessage("import-error", "Essentials", e.getMessage()));
            return 0;
        }
    }

    private Location parseEssentialsLocation(YamlConfiguration yc) {
        String worldName = yc.getString("spawn.world");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        double x = yc.getDouble("spawn.x", 0);
        double y = yc.getDouble("spawn.y", 64);
        double z = yc.getDouble("spawn.z", 0);
        float yaw = (float) yc.getDouble("spawn.yaw", 0);
        float pitch = (float) yc.getDouble("spawn.pitch", 0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    // ================================================================
    //  Source: CMI
    //  File: plugins/CMI/config.yml  →  section: Spawn.Locations
    // ================================================================
    private int importFromCMI(CommandSender sender) {
        File file = new File("plugins/CMI/config.yml");
        if (!file.exists()) {
            sender.sendMessage(lang.getMessage("import-file-not-found", "CMI", file.getPath()));
            return 0;
        }
        try {
            YamlConfiguration yc = YamlConfiguration.loadConfiguration(file);
            if (!yc.isConfigurationSection("Spawn.Locations")) {
                sender.sendMessage(lang.getMessage("import-no-spawns", "CMI"));
                return 0;
            }
            Set<String> keys = yc.getConfigurationSection("Spawn.Locations").getKeys(false);
            int count = 0;
            for (String key : keys) {
                Location loc = parseCMISection(yc, "Spawn.Locations." + key);
                if (loc != null && loc.getWorld() != null) {
                    if (key.equalsIgnoreCase("default") || key.equalsIgnoreCase("spawn")) {
                        plugin.getSpawnManager().setSpawn(loc);
                    } else {
                        plugin.getSpawnManager().setNamedSpawn(key, loc);
                    }
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            sender.sendMessage(lang.getMessage("import-error", "CMI", e.getMessage()));
            return 0;
        }
    }

    private Location parseCMISection(YamlConfiguration yc, String path) {
        String worldName = yc.getString(path + ".World");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        double x = yc.getDouble(path + ".X", 0);
        double y = yc.getDouble(path + ".Y", 64);
        double z = yc.getDouble(path + ".Z", 0);
        float yaw = (float) yc.getDouble(path + ".Yaw", 0);
        float pitch = (float) yc.getDouble(path + ".Pitch", 0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    // ================================================================
    //  Source: SpawnPlus
    //  File: plugins/SpawnPlus/spawns.yml
    // ================================================================
    private int importFromSpawnPlus(CommandSender sender) {
        File file = new File("plugins/SpawnPlus/spawns.yml");
        if (!file.exists()) {
            sender.sendMessage(lang.getMessage("import-file-not-found", "SpawnPlus", file.getPath()));
            return 0;
        }
        try {
            YamlConfiguration yc = YamlConfiguration.loadConfiguration(file);
            Set<String> keys = yc.getKeys(false);
            int count = 0;
            for (String key : keys) {
                Location loc = parseSimpleLoc(yc, key);
                if (loc != null && loc.getWorld() != null) {
                    if (key.equalsIgnoreCase("default") || key.equalsIgnoreCase("spawn")) {
                        plugin.getSpawnManager().setSpawn(loc);
                    } else {
                        plugin.getSpawnManager().setNamedSpawn(key, loc);
                    }
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            sender.sendMessage(lang.getMessage("import-error", "SpawnPlus", e.getMessage()));
            return 0;
        }
    }

    // ================================================================
    //  Source: SpawnX
    //  File: plugins/SpawnX/spawns.yml
    // ================================================================
    private int importFromSpawnX(CommandSender sender) {
        File file = new File("plugins/SpawnX/spawns.yml");
        if (!file.exists()) {
            sender.sendMessage(lang.getMessage("import-file-not-found", "SpawnX", file.getPath()));
            return 0;
        }
        try {
            YamlConfiguration yc = YamlConfiguration.loadConfiguration(file);
            Set<String> keys = yc.getKeys(false);
            int count = 0;
            for (String key : keys) {
                Location loc = parseSimpleLoc(yc, key);
                if (loc != null && loc.getWorld() != null) {
                    if (key.equalsIgnoreCase("default") || key.equalsIgnoreCase("spawn")) {
                        plugin.getSpawnManager().setSpawn(loc);
                    } else {
                        plugin.getSpawnManager().setNamedSpawn(key, loc);
                    }
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            sender.sendMessage(lang.getMessage("import-error", "SpawnX", e.getMessage()));
            return 0;
        }
    }

    // ================================================================
    //  Source: SpawnControl
    //  File: plugins/SpawnControl/spawns.yml
    // ================================================================
    private int importFromSpawnControl(CommandSender sender) {
        File file = new File("plugins/SpawnControl/spawns.yml");
        if (!file.exists()) {
            sender.sendMessage(lang.getMessage("import-file-not-found", "SpawnControl", file.getPath()));
            return 0;
        }
        try {
            YamlConfiguration yc = YamlConfiguration.loadConfiguration(file);
            Set<String> keys = yc.getKeys(false);
            int count = 0;
            for (String key : keys) {
                Location loc = parseSimpleLoc(yc, key);
                if (loc != null && loc.getWorld() != null) {
                    if (key.equalsIgnoreCase("default") || key.equalsIgnoreCase("spawn")) {
                        plugin.getSpawnManager().setSpawn(loc);
                    } else {
                        plugin.getSpawnManager().setNamedSpawn(key, loc);
                    }
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            sender.sendMessage(lang.getMessage("import-error", "SpawnControl", e.getMessage()));
            return 0;
        }
    }

    // ================================================================
    //  Source: DeluxeSpawn
    //  File: plugins/DeluxeSpawn/spawns.yml
    // ================================================================
    private int importFromDeluxeSpawn(CommandSender sender) {
        File file = new File("plugins/DeluxeSpawn/spawns.yml");
        if (!file.exists()) {
            sender.sendMessage(lang.getMessage("import-file-not-found", "DeluxeSpawn", file.getPath()));
            return 0;
        }
        try {
            YamlConfiguration yc = YamlConfiguration.loadConfiguration(file);
            Set<String> keys = yc.getKeys(false);
            int count = 0;
            for (String key : keys) {
                Location loc = parseSimpleLoc(yc, key);
                if (loc != null && loc.getWorld() != null) {
                    if (key.equalsIgnoreCase("default") || key.equalsIgnoreCase("spawn")) {
                        plugin.getSpawnManager().setSpawn(loc);
                    } else {
                        plugin.getSpawnManager().setNamedSpawn(key, loc);
                    }
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            sender.sendMessage(lang.getMessage("import-error", "DeluxeSpawn", e.getMessage()));
            return 0;
        }
    }

    // ================================================================
    //  Source: JellySpawn
    //  File: plugins/JellySpawn/spawns.yml
    // ================================================================
    private int importFromJellySpawn(CommandSender sender) {
        File file = new File("plugins/JellySpawn/spawns.yml");
        if (!file.exists()) {
            sender.sendMessage(lang.getMessage("import-file-not-found", "JellySpawn", file.getPath()));
            return 0;
        }
        try {
            YamlConfiguration yc = YamlConfiguration.loadConfiguration(file);
            Set<String> keys = yc.getKeys(false);
            int count = 0;
            for (String key : keys) {
                Location loc = parseSimpleLoc(yc, key);
                if (loc != null && loc.getWorld() != null) {
                    if (key.equalsIgnoreCase("default") || key.equalsIgnoreCase("spawn")) {
                        plugin.getSpawnManager().setSpawn(loc);
                    } else {
                        plugin.getSpawnManager().setNamedSpawn(key, loc);
                    }
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            sender.sendMessage(lang.getMessage("import-error", "JellySpawn", e.getMessage()));
            return 0;
        }
    }

    // ================================================================
    //  Generic location parser (works for most YAML-based plugins)
    //  Expected format:
    //    world: <name>
    //    x: <double>  (or X)
    //    y: <double>  (or Y)
    //    z: <double>  (or Z)
    //    yaw: <float>  (or Yaw)
    //    pitch: <float> (or Pitch)
    // ================================================================
    private Location parseSimpleLoc(YamlConfiguration yc, String section) {
        String worldName = yc.getString(section + ".world");
        if (worldName == null) worldName = yc.getString(section + ".World");
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = getDoubleCase(yc, section + ".x", section + ".X");
        double y = getDoubleCase(yc, section + ".y", section + ".Y");
        double z = getDoubleCase(yc, section + ".z", section + ".Z");
        float yaw = (float) getDoubleCase(yc, section + ".yaw", section + ".Yaw");
        float pitch = (float) getDoubleCase(yc, section + ".pitch", section + ".Pitch");

        if (x == 0 && y == 0 && z == 0) return null; // Probably empty/invalid
        return new Location(world, x, y, z, yaw, pitch);
    }

    private double getDoubleCase(YamlConfiguration yc, String key1, String key2) {
        double val = yc.getDouble(key1, Double.MIN_VALUE);
        if (val == Double.MIN_VALUE) val = yc.getDouble(key2, 0);
        return val;
    }

    private void showUsage(CommandSender sender) {
        sender.sendMessage(lang.getMessageUnprefixed("import-usage"));
        showAvailable(sender);
    }

    private void showAvailable(CommandSender sender) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SOURCES.size(); i++) {
            sb.append(SOURCES.get(i));
            if (i < SOURCES.size() - 1) sb.append(", ");
        }
        sender.sendMessage(lang.getMessageUnprefixed("import-available").replace("{0}", sb.toString()));
    }
}
