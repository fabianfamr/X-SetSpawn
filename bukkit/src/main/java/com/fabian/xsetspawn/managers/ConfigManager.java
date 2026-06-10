package com.fabian.xsetspawn.managers;

import com.fabian.xsetspawn.XSetSpawn;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;


public class ConfigManager {

    private final XSetSpawn plugin;
    private FileConfiguration config;

    public ConfigManager(XSetSpawn plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();

        // 1.5 Update Logic: Check "code"
        int currentCode = config.getInt("code", 0);
        
        InputStream defaultStream = plugin.getResource("config.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
            int newCode = defaultConfig.getInt("code", 0);
            
            if (currentCode < newCode) {
                plugin.log("&7Found a newer configuration version! &f(&e" + currentCode + " &7-> &a" + newCode + "&f)");
                backupConfig();
                rebuildConfig();
                plugin.reloadConfig();
                this.config = plugin.getConfig();
                
                // Reload ManagerConfig if it exists
                if (plugin.getManagerConfig() != null) {
                    plugin.getManagerConfig().load();
                }
            }
        }
    }

    private void backupConfig() {
        try {
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            File backupFile = new File(plugin.getDataFolder(), "config_old.yml");
            java.nio.file.Files.copy(configFile.toPath(), backupFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            plugin.log("&7A backup of your current config has been created: &e" + backupFile.getName());
        } catch (Exception e) {
            plugin.getLogger().warning("Could not create config backup: " + e.getMessage());
        }
    }

    public void rebuildConfig() {
        try {
            InputStream is = plugin.getResource("config.yml");
            if (is == null) return;
            
            java.util.List<String> lines = new java.util.ArrayList<>();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            
            FileConfiguration currentConfig = this.config;
            java.util.List<String> path = new java.util.ArrayList<>();
            java.util.List<String> outLines = new java.util.ArrayList<>();
            
            for (String line : lines) {
                if (line.trim().isEmpty() || line.trim().startsWith("#") || line.trim().startsWith("-")) {
                    outLines.add(line);
                    continue;
                }
                
                int spaces = 0;
                while (spaces < line.length() && line.charAt(spaces) == ' ') {
                    spaces++;
                }
                
                int level = spaces / 2;
                
                if (line.contains(":")) {
                    String keyPart = line.substring(spaces, line.indexOf(':')).trim();
                    
                    while (path.size() > level) {
                        path.remove(path.size() - 1);
                    }
                    path.add(keyPart);
                    
                    String fullPath = String.join(".", path);
                    
                    if (fullPath.equals("code")) {
                        outLines.add(line); // Keep the updated code from the jar
                    } else if (currentConfig.contains(fullPath) && !currentConfig.isConfigurationSection(fullPath)) {
                        Object val = currentConfig.get(fullPath);
                        String valStr = formatValue(val);
                        outLines.add(line.substring(0, line.indexOf(':') + 1) + " " + valStr);
                    } else {
                        outLines.add(line);
                    }
                } else {
                    outLines.add(line);
                }
            }
            
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            java.nio.file.Files.write(configFile.toPath(), outLines, java.nio.charset.StandardCharsets.UTF_8);

        } catch (Exception e) {
            plugin.getLogger().warning("Could not rebuild config.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String formatValue(Object value) {
        if (value instanceof String) {
            return "\"" + value.toString().replace("\"", "\\\"") + "\"";
        }
        if (value instanceof java.util.List) {
            StringBuilder sb = new StringBuilder("[");
            java.util.List<?> list = (java.util.List<?>) value;
            for (int i = 0; i < list.size(); i++) {
                sb.append(formatValue(list.get(i)));
                if (i < list.size() - 1) sb.append(", ");
            }
            sb.append("]");
            return sb.toString();
        }
        return value.toString();
    }
    
    public void reloadConfiguration() {
        loadConfig();
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public void saveConfig() {
        plugin.saveConfig();
    }

    // --- Backward Compatibility Getters ---
    // These will be removed in future versions once all components use ManagerConfig

    public String getLanguage() {
        return plugin.getManagerConfig().language;
    }

    public boolean isPerWorldEnabled() {
        return plugin.getManagerConfig().perWorld;
    }

    public boolean isUpdateCheckEnabled() {
        return plugin.getManagerConfig().checkUpdates;
    }

    public boolean isTeleportOnFirstJoin() {
        return plugin.getManagerConfig().teleportOnFirstJoin;
    }

    public boolean isTeleportOnDeath() {
        return plugin.getManagerConfig().teleportOnDeath;
    }

    public boolean isSoundEnabled() {
        return plugin.getManagerConfig().soundsEnabled;
    }

    public String getSpawnSound() {
        return plugin.getManagerConfig().spawnSound;
    }

    public float getSoundVolume() {
        return plugin.getManagerConfig().soundVolume;
    }

    public float getSoundPitch() {
        return plugin.getManagerConfig().soundPitch;
    }

    public boolean isCooldownEnabled() {
        return plugin.getManagerConfig().cooldownEnabled;
    }

    public int getCooldownTime() {
        return plugin.getManagerConfig().cooldownTime;
    }

    public boolean isDelayEnabled() {
        return plugin.getManagerConfig().delayEnabled;
    }

    public int getDelayTime() {
        return plugin.getManagerConfig().delayTime;
    }

    public boolean isDelayCancelOnMove() {
        return plugin.getManagerConfig().delayCancelOnMove;
    }

    public boolean isParticlesEnabled() {
        return plugin.getManagerConfig().particlesEnabled;
    }

    public String getParticleType() {
        return plugin.getManagerConfig().particleType;
    }

    public int getParticleAmount() {
        return plugin.getManagerConfig().particleAmount;
    }

    // Deprecated in favor of Permission class
    public boolean hasPermission(org.bukkit.entity.Player player, String node) {
        try {
            return Permission.valueOf(node.toUpperCase().replace("-", "_")).has(player);
        } catch (IllegalArgumentException e) {
            return player.hasPermission("xsetspawn." + node);
        }
    }
}

