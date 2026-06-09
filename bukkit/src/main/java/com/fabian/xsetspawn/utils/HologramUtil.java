package com.fabian.xsetspawn.utils;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

public class HologramUtil {

    private static final Map<UUID, ArmorStand> activeHolograms = new ConcurrentHashMap<>();

    public static void createHologram(Player player, Location location, String text, double heightOffset) {
        removeHologram(player);

        Location displayLoc = location.clone().add(0, heightOffset, 0);
        ArmorStand armorStand = (ArmorStand) displayLoc.getWorld().spawnEntity(displayLoc, EntityType.ARMOR_STAND);
        
        armorStand.setVisible(false);
        armorStand.setGravity(false);
        armorStand.setCustomNameVisible(true);
        armorStand.setCustomName(org.bukkit.ChatColor.translateAlternateColorCodes('&', text));
        armorStand.setBasePlate(false);
        
        // Use reflection or newer API to set Marker safely across versions
        try {
            armorStand.setMarker(true);
        } catch (NoSuchMethodError e) {
            // Version < 1.8.8 doesn't have setMarker, safe to ignore
        }

        activeHolograms.put(player.getUniqueId(), armorStand);
    }

    public static void updateHologram(Player player, String newText) {
        ArmorStand armorStand = activeHolograms.get(player.getUniqueId());
        if (armorStand != null && armorStand.isValid()) {
            armorStand.setCustomName(org.bukkit.ChatColor.translateAlternateColorCodes('&', newText));
        }
    }

    public static void removeHologram(Player player) {
        ArmorStand armorStand = activeHolograms.remove(player.getUniqueId());
        if (armorStand != null && armorStand.isValid()) {
            armorStand.remove();
        }
    }

    /**
     * Removes ALL active holograms. Called on plugin disable/reload to prevent orphaned ArmorStands.
     */
    public static void removeAll() {
        for (ArmorStand armorStand : activeHolograms.values()) {
            if (armorStand != null && armorStand.isValid()) {
                armorStand.remove();
            }
        }
        activeHolograms.clear();
    }
}

