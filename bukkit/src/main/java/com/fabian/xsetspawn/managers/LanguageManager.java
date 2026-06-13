package com.fabian.xsetspawn.managers;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.utils.ColorUtils;
import com.fabian.xsetspawn.utils.DebugLogger;
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
    private boolean hasPAPI = false;

    public LanguageManager(XSetSpawn plugin) {
        this.plugin = plugin;
        migrateOldLanguages();
        loadLanguage();
    }

    private void migrateOldLanguages() {
        File oldFolder = new File(plugin.getDataFolder(), "languages");
        File newFolder = new File(plugin.getDataFolder(), "messages");
        if (!oldFolder.exists()) return;
        if (!newFolder.exists()) {
            newFolder.mkdirs();
        }
        File[] oldFiles = oldFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (oldFiles != null) {
            for (File f : oldFiles) {
                File dest = new File(newFolder, f.getName().toLowerCase());
                try {
                    java.nio.file.Files.copy(f.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not migrate " + f.getName() + " to messages/: " + e.getMessage());
                }
            }
        }
        deleteDirectory(oldFolder);
        plugin.logInfo("&eMigrated old &flanguages/&e folder to &fmessages/&e");
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDirectory(f);
                }
                f.delete();
            }
        }
        dir.delete();
    }

    public void loadLanguage() {
        this.currentLanguage = plugin.getManagerConfig().language.toLowerCase();
        DebugLogger.debug("Language", "Loading language: " + this.currentLanguage);

        File messagesFolder = new File(plugin.getDataFolder(), "messages");
        if (!messagesFolder.exists()) {
            messagesFolder.mkdirs();
        }

        saveResourceIfNotExists("messages/en.yml");
        saveResourceIfNotExists("messages/es.yml");
        saveResourceIfNotExists("messages/ja.yml");
        saveResourceIfNotExists("messages/pt.yml");
        saveResourceIfNotExists("messages/ru.yml");
        saveResourceIfNotExists("messages/custom.yml");

        File languageFile = new File(plugin.getDataFolder(), "messages/" + currentLanguage + ".yml");
        if (!languageFile.exists()) {
            DebugLogger.debug("Language", "Language file " + currentLanguage + ".yml not found, falling back to en.yml");
            plugin.getLogger().warning("Language file " + currentLanguage + ".yml not found! Using en.yml");
            languageFile = new File(plugin.getDataFolder(), "messages/en.yml");
            this.currentLanguage = "en";
        }

        this.languageConfig = YamlConfiguration.loadConfiguration(languageFile);

        InputStream defaultStream = plugin.getResource("messages/" + currentLanguage + ".yml");
        if (defaultStream == null && !currentLanguage.equals("en")) {
            defaultStream = plugin.getResource("messages/en.yml");
        }
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

        this.hasPAPI = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    public void reloadLanguage() {
        DebugLogger.debug("Language", "Reloading language...");
        loadLanguage();
    }

    /**
     * Get available language files
     */
    public java.util.List<String> getAvailableLanguages() {
        java.util.List<String> langs = new java.util.ArrayList<>();
        File messagesFolder = new File(plugin.getDataFolder(), "messages");
        if (messagesFolder.exists() && messagesFolder.isDirectory()) {
            File[] files = messagesFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File f : files) {
                    String name = f.getName();
                    langs.add(name.replace(".yml", ""));
                }
            }
        }
        return langs;
    }

    /**
     * Change the language
     */
    public boolean setLanguage(String lang) {
        DebugLogger.debug("Language", "Changing language to: " + lang);
        String newLang = lang.toLowerCase();
        java.util.List<String> available = getAvailableLanguages();

        // Convert available languages to lowercase for comparison
        java.util.List<String> availableLower = new java.util.ArrayList<>();
        for (String l : available) {
            availableLower.add(l.toLowerCase());
        }

        if (!availableLower.contains(newLang)) {
            return false;
        }

        // Update config
        plugin.getConfig().set("language", newLang);
        plugin.saveConfig();

        // Reload messages and config cache
        plugin.getManagerConfig().reload();
        loadLanguage();

        return true;
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public String getMessage(String key, Object... args) {
        String message = languageConfig.getString(key);
        if (message == null) {
            DebugLogger.debug("Language", "Missing language key: " + key);
        }
        String prefix = plugin.getManagerConfig().prefix;

        if (message == null) {
            return getFallbackMessage(key);
        }

        if (args.length > 0) {
            message = MessageFormat.format(message, args);
        }

        return ColorUtils.formatToLegacy(prefix + message);
    }

    public String getMessage(Player player, String key, Object... args) {
        String msg = getMessage(key, args);
        if (player != null && hasPAPI) {
            msg = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, msg);
        }
        return msg;
    }

    public String getMessageUnprefixed(String key, Object... args) {
        String message = languageConfig.getString(key);

        if (message == null) {
            return ColorUtils.formatToLegacy("&cMissing message: " + key);
        }

        if (args.length > 0) {
            message = MessageFormat.format(message, args);
        }

        return ColorUtils.formatToLegacy(message);
    }

    public String getMessageUnprefixed(Player player, String key, Object... args) {
        String msg = getMessageUnprefixed(key, args);
        if (player != null && hasPAPI) {
            msg = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, msg);
        }
        return msg;
    }

    public net.kyori.adventure.text.Component getMessageComponent(Player player, String key, Object... args) {
        String message = languageConfig.getString(key);
        String prefix = plugin.getManagerConfig().prefix;

        if (message == null) {
            return net.kyori.adventure.text.Component.text(getFallbackMessage(key));
        }

        if (args.length > 0) {
            message = MessageFormat.format(message, args);
        }

        String fullMessage = prefix + message;

        if (player != null && hasPAPI) {
            fullMessage = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, fullMessage);
        }

        return ColorUtils.format(fullMessage);
    }

    public net.kyori.adventure.text.Component getMessageComponentUnprefixed(Player player, String key, Object... args) {
        String message = languageConfig.getString(key);

        if (message == null) {
            return ColorUtils.format("&cMissing message: " + key);
        }

        if (args.length > 0) {
            message = MessageFormat.format(message, args);
        }

        if (player != null && hasPAPI) {
            message = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, message);
        }

        return ColorUtils.format(message);
    }

    private String saveResourceIfNotExists(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
        }
        return resourcePath;
    }

    private String getFallbackMessage(String key) {
        String message;
        switch (key) {
            case "cooldown-active":
                message = "&cYou must wait {0} seconds.";
                break;
            case "teleporting-in":
                message = "&aTeleporting in {0} seconds... Don't move!";
                break;
            case "proxy-connecting":
                message = "&aSending you to the &e{0} &aserver...";
                break;
            case "proxy-hub-message":
                message = "&aReturning to the &eMain Hub&a...";
                break;
            case "proxy-lobby-message":
                message = "&aGoing back to the &eLobby&a...";
                break;
            case "not-enough-money":
                message = "&cYou need at least &e${0} &cto teleport!";
                break;
            case "teleport-cost":
                message = "&aYou have paid &e${0} &afor teleporting.";
                break;
            case "combat-active":
                message = "&cYou cannot teleport while in combat!";
                break;
            case "falling-active":
                message = "&cYou cannot teleport while falling!";
                break;
            case "spawn-firstjoin-set":
                message = "&aFirst-Join spawn successfully set!";
                break;
            default:
                plugin.getLogger().warning("Missing language key: " + key);
                message = "&cMissing message: " + key;
                break;
        }
        return ColorUtils.formatToLegacy(message);
    }
}
