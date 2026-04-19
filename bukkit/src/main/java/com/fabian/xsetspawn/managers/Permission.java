package com.fabian.xsetspawn.managers;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Permission - Centralized permission management for X-SetSpawn v1.5+.
 *
 * Permissions are NO LONGER configurable in config.yml.
 * They are strictly enforced via plugin.yml and the Permission enum.
 *
 * Key behavior: If a permission is explicitly DENIED (-node), it will
 * be respected even if the player is OP.
 */
public enum Permission {
    ADMIN("admin"),
    SETSPAWN("setspawn"),
    SPAWN("spawn"),
    RELOAD("reload"),
    UPDATE("update"),
    BYPASS_COOLDOWN("bypass.cooldown"),
    BYPASS_DELAY("bypass.delay"),
    BYPASS_ECONOMY("bypass.economy"),
    BACK("back"),
    DELSPAWN("delspawn");

    private final String node;

    Permission(String node) {
        this.node = "xsetspawn." + node;
    }

    public String getNode() {
        return node;
    }

    /**
     * Checks if a sender has this permission.
     *
     * Rules:
     * 1. Console always has permission.
     * 2. If the permission is explicitly NEGATED (-node), it ALWAYS returns false — even for OP.
     * 3. Otherwise, standard Bukkit permission check (inherited from plugin.yml defaults).
     *
     * @param sender The command sender to check.
     * @return true if permission is granted, false otherwise.
     */
    public boolean has(CommandSender sender) {
        if (!(sender instanceof Player)) {
            return true; // Console always allowed
        }

        Player player = (Player) sender;

        // If permission is explicitly set to FALSE (negated), deny — even for OP
        if (player.isPermissionSet(this.node) && !player.hasPermission(this.node)) {
            return false;
        }

        return player.hasPermission(this.node);
    }
}

