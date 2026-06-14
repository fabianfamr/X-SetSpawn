package com.fabian.xsetspawn.managers;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.utils.ColorUtils;
import com.fabian.xsetspawn.utils.DebugLogger;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Set;
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

        try {
            this.languageConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(new FileInputStream(languageFile), StandardCharsets.UTF_8));
        } catch (java.io.FileNotFoundException e) {
            this.languageConfig = YamlConfiguration.loadConfiguration(languageFile);
        }

        InputStream defaultStream = plugin.getResource("messages/" + currentLanguage + ".yml");
        if (defaultStream == null && !currentLanguage.equals("en")) {
            defaultStream = plugin.getResource("messages/en.yml");
        }
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
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

    // ==========================================
    //       Force Messages Methods
    // ==========================================

    /**
     * Adds missing keys from the JAR default to an existing language file on disk.
     * If the file is the currently active language, it is also reloaded into memory.
     *
     * @param langCode the language code (e.g. "en", "es")
     * @return true if the file was updated and/or reloaded, false if the language was not found
     */
    public boolean forceReloadMessages(String langCode) {
        String lang = langCode.toLowerCase();
        File langFile = new File(plugin.getDataFolder(), "messages/" + lang + ".yml");
        if (!langFile.exists()) return false;

        // Load existing file on disk
        YamlConfiguration diskConfig;
        try {
            diskConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(new FileInputStream(langFile), StandardCharsets.UTF_8));
        } catch (java.io.FileNotFoundException e) {
            diskConfig = YamlConfiguration.loadConfiguration(langFile);
        }

        // Load defaults from JAR
        InputStream jarStream = plugin.getResource("messages/" + lang + ".yml");
        if (jarStream == null) return false;
        YamlConfiguration jarConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(jarStream, StandardCharsets.UTF_8));

        // Copy missing keys from JAR defaults to disk config
        boolean changed = false;
        Set<String> jarKeys = jarConfig.getKeys(true);
        for (String key : jarKeys) {
            if (!diskConfig.contains(key)) {
                diskConfig.set(key, jarConfig.get(key));
                changed = true;
            }
        }

        if (changed) {
            try {
                diskConfig.save(langFile);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not save updated language file " + lang + ".yml: " + e.getMessage());
            }
        }

        // Reload if this is the active language
        if (lang.equals(this.currentLanguage)) {
            loadLanguage();
            return true;
        }
        return changed;
    }

    /**
     * Adds missing keys to ALL language files on disk. Reloads if the active language was updated.
     *
     * @return the number of files updated
     */
    public int forceReloadAllMessages() {
        int count = 0;
        for (String lang : getAvailableLanguages()) {
            File langFile = new File(plugin.getDataFolder(), "messages/" + lang + ".yml");
            if (!langFile.exists()) continue;

            InputStream jarStream = plugin.getResource("messages/" + lang + ".yml");
            if (jarStream == null) continue;
            YamlConfiguration jarConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(jarStream, StandardCharsets.UTF_8));

            YamlConfiguration diskConfig;
            try {
                diskConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(new FileInputStream(langFile), StandardCharsets.UTF_8));
            } catch (java.io.FileNotFoundException e) {
                diskConfig = YamlConfiguration.loadConfiguration(langFile);
            }
            boolean changed = false;
            Set<String> jarKeys = jarConfig.getKeys(true);
            for (String key : jarKeys) {
                if (!diskConfig.contains(key)) {
                    diskConfig.set(key, jarConfig.get(key));
                    changed = true;
                }
            }

            if (changed) {
                try {
                    diskConfig.save(langFile);
                    count++;
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not save updated language file " + lang + ".yml: " + e.getMessage());
                }
            }
        }
        // Reload active language
        loadLanguage();
        return count;
    }

    /**
     * Deletes an existing language file and extracts a fresh copy from the JAR.
     * If the file is the currently active language, it is also reloaded into memory.
     *
     * @param langCode the language code (e.g. "en", "es")
     * @return true if the file was regenerated, false if no JAR default exists
     */
    public boolean forceResetMessages(String langCode) {
        String lang = langCode.toLowerCase();
        String resourcePath = "messages/" + lang + ".yml";

        // Check if the JAR contains this resource
        InputStream jarStream = plugin.getResource(resourcePath);
        if (jarStream == null) return false;

        File langFile = new File(plugin.getDataFolder(), resourcePath);

        // Delete existing file
        if (langFile.exists()) {
            langFile.delete();
        }

        // Extract fresh copy from JAR
        plugin.saveResource(resourcePath, true);

        // Reload if this is the active language
        if (lang.equals(this.currentLanguage)) {
            loadLanguage();
            return true;
        }
        return true;
    }

    /**
     * Regenerates ALL language files that have a JAR default.
     *
     * @return the number of files regenerated
     */
    public int forceResetAllMessages() {
        int count = 0;
        // Get languages that exist on disk
        java.util.List<String> diskLangs = getAvailableLanguages();
        for (String lang : diskLangs) {
            String resourcePath = "messages/" + lang + ".yml";
            InputStream jarStream = plugin.getResource(resourcePath);
            if (jarStream == null) continue;

            File langFile = new File(plugin.getDataFolder(), resourcePath);
            if (langFile.exists()) {
                langFile.delete();
            }
            plugin.saveResource(resourcePath, true);
            count++;
        }
        // Reload active language
        loadLanguage();
        return count;
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
