package com.fabian.xsetspawn.utils;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.managers.ManagerConfig;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * DebugLogger - Static utility for debug logging with two modes:
 * <p>
 * - Config debug (debug: true in config.yml): messages go to CONSOLE only.
 * - Command debug (/xsetspawn debug): messages go to the PLAYER who toggled it.
 * <p>
 * When both are active, only the player receives command-triggered debug messages.
 * Config debug always outputs to console independently.
 */
public final class DebugLogger {

    private static final String PLUGIN_NAME = "X-SetSpawn";
    private static final String PREFIX = "&8[&bDEBUG&8] &f[" + PLUGIN_NAME + "&f]&r &7";

    private DebugLogger() {
        // Static utility class — no instances
    }

    /**
     * Checks if debug mode is active (either via config or via command).
     */
    private static boolean isDebugEnabled() {
        XSetSpawn plugin = XSetSpawn.getInstance();
        if (plugin == null) return false;
        ManagerConfig config = plugin.getManagerConfig();
        if (config == null) return false;
        return config.debugEnabled || config.debugPlayer != null;
    }

    /**
     * Checks if config-based debug is active (console output).
     */
    private static boolean isConfigDebug() {
        XSetSpawn plugin = XSetSpawn.getInstance();
        if (plugin == null) return false;
        ManagerConfig config = plugin.getManagerConfig();
        if (config == null) return false;
        return config.debugEnabled;
    }

    /**
     * Gets the player who enabled debug via command, or null.
     */
    private static Player getDebugPlayer() {
        XSetSpawn plugin = XSetSpawn.getInstance();
        if (plugin == null) return null;
        ManagerConfig config = plugin.getManagerConfig();
        if (config == null || config.debugPlayer == null) return null;
        return Bukkit.getPlayer(config.debugPlayer);
    }

    /**
     * Logs a debug message with the standard debug prefix.
     */
    public static void debug(String message) {
        if (!isDebugEnabled()) return;
        send(message);
    }

    /**
     * Logs a debug message with a category prefix.
     * Output format: [DEBUG] [Category] message
     */
    public static void debug(String category, String message) {
        if (!isDebugEnabled()) return;
        send("[" + category + "] " + message);
    }

    /**
     * Logs a debug message with a category prefix and includes a stack trace.
     */
    public static void debug(String category, String message, Throwable throwable) {
        if (!isDebugEnabled()) return;
        send("[" + category + "] " + message);
        if (throwable != null) {
            throwable.printStackTrace();
        }
    }

    /**
     * Routes the message to the appropriate recipient:
     * - If a player enabled debug via command → send to that player
     * - If debug is enabled via config → send to console
     * - If both → player gets it (config debug still goes to console independently via isConfigDebug)
     */
    private static void send(String message) {
        XSetSpawn plugin = XSetSpawn.getInstance();
        if (plugin == null) return;
        ManagerConfig config = plugin.getManagerConfig();
        if (config == null) return;

        String formatted = ChatColor.translateAlternateColorCodes('&', PREFIX + message);

        // Player debug via command
        if (config.debugPlayer != null) {
            Player debugPlayer = Bukkit.getPlayer(config.debugPlayer);
            if (debugPlayer != null && debugPlayer.isOnline()) {
                debugPlayer.sendMessage(formatted);
                return;
            } else {
                // Player went offline, clean up
                config.debugPlayer = null;
            }
        }

        // Config debug → console only
        if (config.debugEnabled) {
            Bukkit.getConsoleSender().sendMessage(formatted);
        }
    }
}