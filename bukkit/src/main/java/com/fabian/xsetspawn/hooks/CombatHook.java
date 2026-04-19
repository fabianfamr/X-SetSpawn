package com.fabian.xsetspawn.hooks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * CombatHook - Detects if a player is in combat using soft integrations.
 *
 * This class does NOT import any optional plugin APIs directly.
 * Instead, it uses metadata (common across all combat plugins) and
 * safe reflection so the plugin compiles without optional dependencies in POM.
 *
 * Supported plugins (optional, loaded at runtime):
 *  - CombatLogX
 *  - PvPManager
 *  - DeluxeCombat
 *  - Any plugin that sets metadata "in_combat" or "CombatTag" on a player
 */
public class CombatHook {

    /**
     * Checks if a player is currently in combat by querying known metadata keys
     * and reflection-based API access for popular combat plugins.
     *
     * @param player The player to check.
     * @return true if in combat, false otherwise.
     */
    public static boolean isInCombat(Player player) {
        // Strategy 1: Check common metadata keys used by combat plugins
        if (hasMetadataFlag(player, "in_combat")
                || hasMetadataFlag(player, "CombatTag")
                || hasMetadataFlag(player, "CombatTagged")
                || hasMetadataFlag(player, "pvpmanager_combat")
                || hasMetadataFlag(player, "deluxecombat_combat")
                || hasMetadataFlag(player, "combatlogx_in_combat")) {
            return true;
        }

        // Strategy 2: Try CombatLogX via reflection
        if (isPluginEnabled("CombatLogX") && checkCombatLogX(player)) {
            return true;
        }

        // Strategy 3: Try PvPManager via reflection
        if (isPluginEnabled("PvPManager") && checkPvPManager(player)) {
            return true;
        }

        return false;
    }

    private static boolean hasMetadataFlag(Player player, String key) {
        if (!player.hasMetadata(key)) return false;
        List<MetadataValue> values = player.getMetadata(key);
        for (MetadataValue value : values) {
            if (value.asBoolean()) return true;
        }
        return false;
    }

    private static boolean checkCombatLogX(Player player) {
        try {
            Object plugin = Bukkit.getPluginManager().getPlugin("CombatLogX");
            if (plugin == null) return false;
            // Reflection: plugin.getCombatManager().isInCombat(player)
            Object combatManager = plugin.getClass().getMethod("getCombatManager").invoke(plugin);
            return (boolean) combatManager.getClass().getMethod("isInCombat", Player.class).invoke(combatManager, player);
        } catch (Throwable ignored) {
            // API mismatch or not available - fallback to metadata only
            return false;
        }
    }

    private static boolean checkPvPManager(Player player) {
        try {
            Object plugin = Bukkit.getPluginManager().getPlugin("PvPManager");
            if (plugin == null) return false;
            // Reflection: plugin.getPlayerHandler().get(player).isInCombat()
            Object handler = plugin.getClass().getMethod("getPlayerHandler").invoke(plugin);
            Object pvpPlayer = handler.getClass().getMethod("get", Player.class).invoke(handler, player);
            return (boolean) pvpPlayer.getClass().getMethod("isInCombat").invoke(pvpPlayer);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isPluginEnabled(String name) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        return plugin != null && plugin.isEnabled();
    }
}

