package com.fabian.xsetspawn.utils;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hologram utility with multi-provider support.
 * Priority: DecentHolograms > HolographicDisplays > ArmorStand fallback
 */
public class HologramUtil {

    private static boolean dhAvailable = false;
    private static boolean hdAvailable = false;

    // Track HD holograms by player UUID (HD requires manual tracking)
    // Fallback ArmorStand holograms also tracked here
    private static final Map<UUID, Object> trackedHolograms = new ConcurrentHashMap<>();

    static {
        dhAvailable = Bukkit.getPluginManager().isPluginEnabled("DecentHolograms");
        hdAvailable = Bukkit.getPluginManager().isPluginEnabled("HolographicDisplays");
        if (dhAvailable) {
            DebugLogger.debug("HologramUtil", "Hologram provider: DecentHolograms");
        } else if (hdAvailable) {
            DebugLogger.debug("HologramUtil", "Hologram provider: HolographicDisplays");
        } else {
            DebugLogger.debug("HologramUtil", "No hologram plugin found, using ArmorStand fallback");
        }
    }

    /**
     * Returns the name of the active hologram provider.
     */
    public static String getProviderName() {
        if (dhAvailable) return "DecentHolograms";
        if (hdAvailable) return "HolographicDisplays";
        return "ArmorStand (built-in)";
    }

    /**
     * Create a hologram above the player's location.
     * The hologram name/ID is based on the player's UUID.
     */
    public static void createHologram(Player player, Location location, String text, double heightOffset) {
        removeHologram(player);

        Location displayLoc = location.clone().add(0, heightOffset, 0);
        String id = "xsetspawn_" + player.getUniqueId().toString().substring(0, 8);
        String coloredText = org.bukkit.ChatColor.translateAlternateColorCodes('&', text);

        if (dhAvailable) {
            createDH(id, displayLoc, coloredText);
        } else if (hdAvailable) {
            createHD(player, displayLoc, coloredText);
        } else {
            createArmorStand(player, displayLoc, coloredText);
        }
    }

    /**
     * Update the text of an existing hologram for the given player.
     */
    public static void updateHologram(Player player, String newText) {
        String id = "xsetspawn_" + player.getUniqueId().toString().substring(0, 8);
        String coloredText = org.bukkit.ChatColor.translateAlternateColorCodes('&', newText);

        if (dhAvailable) {
            updateDH(id, coloredText);
        } else if (hdAvailable) {
            updateHD(player, coloredText);
        } else {
            Object obj = trackedHolograms.get(player.getUniqueId());
            if (obj instanceof ArmorStand) {
                ArmorStand armorStand = (ArmorStand) obj;
                if (armorStand.isValid()) {
                    armorStand.setCustomName(coloredText);
                }
            }
        }
    }

    /**
     * Remove the hologram for the given player.
     */
    public static void removeHologram(Player player) {
        String id = "xsetspawn_" + player.getUniqueId().toString().substring(0, 8);

        if (dhAvailable) {
            removeDH(id);
        } else if (hdAvailable) {
            removeHD(player);
        } else {
            Object obj = trackedHolograms.remove(player.getUniqueId());
            if (obj instanceof ArmorStand) {
                try {
                    if (((ArmorStand) obj).isValid()) ((ArmorStand) obj).remove();
                } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * Removes ALL active holograms. Called on plugin disable/reload.
     */
    public static void removeAll() {
        // DecentHolograms: remove by name with our prefix
        if (dhAvailable) {
            for (UUID uuid : trackedHolograms.keySet()) {
                String id = "xsetspawn_" + uuid.toString().substring(0, 8);
                removeDH(id);
            }
        }

        // HD and ArmorStand: tracked in map
        for (Map.Entry<UUID, Object> entry : trackedHolograms.entrySet()) {
            try {
                Object obj = entry.getValue();
                if (obj instanceof com.gmail.filoghost.holographicdisplays.api.Hologram) {
                    ((com.gmail.filoghost.holographicdisplays.api.Hologram) obj).delete();
                } else if (obj instanceof ArmorStand) {
                    if (((ArmorStand) obj).isValid()) ((ArmorStand) obj).remove();
                }
            } catch (Throwable ignored) {}
        }
        trackedHolograms.clear();
    }

    // ==================== DecentHolograms ====================

    private static void createDH(String id, Location loc, String text) {
        try {
            // Remove existing if present from a previous unclosed session
            DHAPI.removeHologram(id);

            // Create hologram with one line of text
            Hologram holo = DHAPI.createHologram(id, loc);
            DHAPI.addHologramLine(id, text);
        } catch (NoClassDefFoundError | Exception e) {
            DebugLogger.debug("HologramUtil", "Failed to create DecentHolograms hologram: " + e.getMessage());
            dhAvailable = false;
        }
    }

    private static void updateDH(String id, String text) {
        try {
            Hologram holo = DHAPI.getHologram(id);
            if (holo != null) {
                DHAPI.setHologramLines(id, Collections.singletonList(text));
            }
        } catch (NoClassDefFoundError | Exception e) {
            DebugLogger.debug("HologramUtil", "Failed to update DecentHolograms hologram: " + e.getMessage());
        }
    }

    private static void removeDH(String id) {
        try {
            DHAPI.removeHologram(id);
        } catch (NoClassDefFoundError | Exception ignored) {}
    }

    // ==================== HolographicDisplays ====================

    private static void createHD(Player player, Location loc, String text) {
        try {
            com.gmail.filoghost.holographicdisplays.api.Hologram holo =
                    com.gmail.filoghost.holographicdisplays.api.HologramsAPI.createHologram(
                            Bukkit.getPluginManager().getPlugin("X-SetSpawn"), loc);
            holo.appendTextLine(text);
            trackedHolograms.put(player.getUniqueId(), holo);
        } catch (NoClassDefFoundError | Exception e) {
            DebugLogger.debug("HologramUtil", "Failed to create HolographicDisplays hologram: " + e.getMessage());
            hdAvailable = false;
        }
    }

    private static void updateHD(Player player, String text) {
        try {
            Object obj = trackedHolograms.get(player.getUniqueId());
            if (!(obj instanceof com.gmail.filoghost.holographicdisplays.api.Hologram)) return;

            com.gmail.filoghost.holographicdisplays.api.Hologram holo =
                    (com.gmail.filoghost.holographicdisplays.api.Hologram) obj;
            for (com.gmail.filoghost.holographicdisplays.api.HologramLine line : holo.getLines()) {
                line.removeLine();
            }
            holo.appendTextLine(text);
        } catch (NoClassDefFoundError | Exception e) {
            DebugLogger.debug("HologramUtil", "Failed to update HolographicDisplays hologram: " + e.getMessage());
        }
    }

    private static void removeHD(Player player) {
        try {
            Object obj = trackedHolograms.remove(player.getUniqueId());
            if (obj instanceof com.gmail.filoghost.holographicdisplays.api.Hologram) {
                ((com.gmail.filoghost.holographicdisplays.api.Hologram) obj).delete();
            }
        } catch (NoClassDefFoundError | Exception ignored) {}
    }

    // ==================== ArmorStand Fallback ====================

    private static void createArmorStand(Player player, Location displayLoc, String text) {
        ArmorStand armorStand = (ArmorStand) displayLoc.getWorld().spawnEntity(displayLoc, EntityType.ARMOR_STAND);
        armorStand.setVisible(false);
        armorStand.setGravity(false);
        armorStand.setCustomNameVisible(true);
        armorStand.setCustomName(text);
        armorStand.setBasePlate(false);
        try {
            armorStand.setMarker(true);
        } catch (NoSuchMethodError ignored) {}
        trackedHolograms.put(player.getUniqueId(), armorStand);
    }
}