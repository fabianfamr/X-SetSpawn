package com.fabian.xsetspawn.utils;

import com.fabian.xsetspawn.XSetSpawn;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

/**
 * DebugLogger - A static utility class for comprehensive debug logging.
 * Only outputs to console when debug mode is enabled in config.yml.
 */
public final class DebugLogger {

    private static final String PREFIX = "&8[&bX-SetSpawn&8] &b[DEBUG] &7";

    private DebugLogger() {
        // Static utility class — no instances
    }

    /**
     * Checks if debug mode is enabled in the plugin configuration.
     * Handles null gracefully if the plugin or config is not yet initialized.
     */
    private static boolean isDebugEnabled() {
        XSetSpawn plugin = XSetSpawn.getInstance();
        if (plugin == null) return false;
        com.fabian.xsetspawn.managers.ManagerConfig config = plugin.getManagerConfig();
        if (config == null) return false;
        return config.debugEnabled;
    }

    /**
     * Logs a debug message with the standard debug prefix.
     *
     * @param message the message to log
     */
    public static void debug(String message) {
        if (!isDebugEnabled()) return;
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', PREFIX + message));
    }

    /**
     * Logs a debug message with a category prefix.
     * Output format: [DEBUG][Category] message
     *
     * @param category the category label (e.g. "SpawnManager", "Command")
     * @param message  the message to log
     */
    public static void debug(String category, String message) {
        if (!isDebugEnabled()) return;
        Bukkit.getConsoleSender().sendMessage(
                ChatColor.translateAlternateColorCodes('&', PREFIX + "&b[" + category + "&b] &7" + message));
    }

    /**
     * Logs a debug message with a category prefix and includes a stack trace.
     *
     * @param category  the category label
     * @param message   the message to log
     * @param throwable the throwable whose stack trace should be printed
     */
    public static void debug(String category, String message, Throwable throwable) {
        if (!isDebugEnabled()) return;
        Bukkit.getConsoleSender().sendMessage(
                ChatColor.translateAlternateColorCodes('&', PREFIX + "&b[" + category + "&b] &7" + message));
        if (throwable != null) {
            throwable.printStackTrace();
        }
    }
}