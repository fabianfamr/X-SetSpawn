package com.fabian.xsetspawn.managers;

import com.fabian.xsetspawn.XSetSpawn;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class LanguageManager {

    private final XSetSpawn plugin;
    private FileConfiguration languageConfig;
    private String currentLanguage;

    public LanguageManager(XSetSpawn plugin) {
        this.plugin = plugin;
        loadLanguage();
    }

    public void loadLanguage() {
        this.currentLanguage = plugin.getManagerConfig().language;

        // Create languages folder if it doesn't exist
        File languagesFolder = new File(plugin.getDataFolder(), "languages");
        if (!languagesFolder.exists()) {
            languagesFolder.mkdirs();
        }

        // Save default language files
        saveResourceIfNotExists("languages/ES.yml");
        saveResourceIfNotExists("languages/EN.yml");
        saveResourceIfNotExists("languages/JA.yml");
        saveResourceIfNotExists("languages/PT.yml");
        saveResourceIfNotExists("languages/RU.yml");
        saveResourceIfNotExists("languages/CUSTOM.yml");

        // Load the selected language
        File languageFile = new File(plugin.getDataFolder(), "languages/" + currentLanguage + ".yml");
        if (!languageFile.exists()) {
            plugin.getLogger().warning("Language file " + currentLanguage + ".yml not found! Using EN.yml");
            languageFile = new File(plugin.getDataFolder(), "languages/EN.yml");
        }

        this.languageConfig = YamlConfiguration.loadConfiguration(languageFile);

        // Load defaults from resources (to update missing keys)
        InputStream defaultStream = plugin.getResource("languages/" + currentLanguage + ".yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
            languageConfig.setDefaults(defaultConfig);
            languageConfig.options().copyDefaults(true);

            try {
                languageConfig.save(languageFile);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not save language file updates: " + e.getMessage());
            }
        }
    }

    public void reloadLanguage() {
        loadLanguage();
    }

    public String getMessage(String key, Object... args) {
        String message = languageConfig.getString(key);
        String prefix = plugin.getManagerConfig().prefix;

        if (message == null) {
            return getFallbackMessage(key);
        }

        if (args.length > 0) {
            message = MessageFormat.format(message, args);
        }

        return ChatColor.translateAlternateColorCodes('&', prefix + message);
    }

    public String getMessage(Player player, String key, Object... args) {
        String msg = getMessage(key, args);
        if (player != null && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            msg = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, msg);
        }
        return msg;
    }

    public String getMessageUnprefixed(String key, Object... args) {
        String message = languageConfig.getString(key);

        if (message == null) {
            return ChatColor.RED + "Missing message: " + key;
        }

        if (args.length > 0) {
            message = MessageFormat.format(message, args);
        }

        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public String getMessageUnprefixed(Player player, String key, Object... args) {
        String msg = getMessageUnprefixed(key, args);
        if (player != null && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            msg = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, msg);
        }
        return msg;
    }

    private String saveResourceIfNotExists(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
        }
        return resourcePath;
    }

    private String getFallbackMessage(String key) {
        switch (key) {
            case "cooldown-active":
                return ChatColor.translateAlternateColorCodes('&', "&cYou must wait {0} seconds.");
            case "teleporting-in":
                return ChatColor.translateAlternateColorCodes('&', "&aTeleporting in {0} seconds... Don't move!");
            case "proxy-connecting":
                return ChatColor.translateAlternateColorCodes('&', "&aSending you to the &e{0} &aserver...");
            case "proxy-hub-message":
                return ChatColor.translateAlternateColorCodes('&', "&aReturning to the &eMain Hub&a...");
            case "proxy-lobby-message":
                return ChatColor.translateAlternateColorCodes('&', "&aGoing back to the &eLobby&a...");
            case "not-enough-money":
                return ChatColor.translateAlternateColorCodes('&', "&cYou need at least &e${0} &cto teleport!");
            case "teleport-cost":
                return ChatColor.translateAlternateColorCodes('&', "&aYou have paid &e${0} &afor teleporting.");
            case "combat-active":
                return ChatColor.translateAlternateColorCodes('&', "&cYou cannot teleport while in combat!");
            case "falling-active":
                return ChatColor.translateAlternateColorCodes('&', "&cYou cannot teleport while falling!");
            case "spawn-firstjoin-set":
                return ChatColor.translateAlternateColorCodes('&', "&aFirst-Join spawn successfully set!");
            default:
                plugin.getLogger().warning("Missing language key: " + key);
                return ChatColor.RED + "Missing message: " + key;
        }
    }
}

