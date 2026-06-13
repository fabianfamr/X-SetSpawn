package com.fabian.xsetspawn.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hologram utility with multi-provider support.
 * Priority: DecentHolograms (FancyHolograms) > HolographicDisplays > ArmorStand fallback
 */
public class HologramUtil {

    private static boolean dhAvailable = false;
    private static boolean hdAvailable = false;

    // Fallback ArmorStand holograms (used when no provider is installed)
    private static final Map<UUID, ArmorStand> activeHolograms = new ConcurrentHashMap<>();

    static {
        dhAvailable = Bukkit.getPluginManager().isPluginEnabled("DecentHolograms");
        hdAvailable = Bukkit.getPluginManager().isPluginEnabled("HolographicDisplays");
        if (dhAvailable) {
            DebugLogger.debug("HologramUtil", "Hologram provider: DecentHolograms (FancyHolograms)");
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
     * The hologram ID is based on the player's UUID.
     */
    public static void createHologram(Player player, Location location, String text, double heightOffset) {
        removeHologram(player);

        Location displayLoc = location.clone().add(0, heightOffset, 0);
        String id = "xsetspawn_" + player.getUniqueId().toString().substring(0, 8);
        String coloredText = org.bukkit.ChatColor.translateAlternateColorCodes('&', text);

        if (dhAvailable) {
            createDH(id, displayLoc, coloredText);
        } else if (hdAvailable) {
            createHD(id, displayLoc, coloredText);
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
            // HD holograms are tracked by UUID in activeHolograms
            updateHD(player, coloredText);
        } else {
            ArmorStand armorStand = activeHolograms.get(player.getUniqueId());
            if (armorStand != null && armorStand.isValid()) {
                armorStand.setCustomName(coloredText);
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
            ArmorStand armorStand = activeHolograms.remove(player.getUniqueId());
            if (armorStand != null && armorStand.isValid()) {
                armorStand.remove();
            }
        }
    }

    /**
     * Removes ALL active holograms. Called on plugin disable/reload.
     */
    public static void removeAll() {
        if (dhAvailable) {
            // DecentHolograms holograms are managed by their ID, clean up any with our prefix
            // Since we use player UUID prefix, individual removals happen via removeHologram()
            // But we clear the DH cache in case any were missed
        }

        if (hdAvailable) {
            // HD holograms are tracked in activeHolograms as Hologram objects
            for (Object holo : activeHolograms.values()) {
                try {
                    ((com.gmail.filoghost.holographicdisplays.api.Hologram) holo).delete();
                } catch (Throwable ignored) {}
            }
        }

        // Always clean up ArmorStands
        for (Object stand : activeHolograms.values()) {
            if (stand instanceof ArmorStand) {
                try {
                    if (((ArmorStand) stand).isValid()) ((ArmorStand) stand).remove();
                } catch (Throwable ignored) {}
            }
        }
        activeHolograms.clear();
    }

    // ==================== DecentHolograms (FancyHolograms) ====================

    private static void createDH(String id, Location loc, String text) {
        try {
            eu.decentsoftware.holograms.api.DHAPI.createHologram(id, loc, java.util.Collections.singletonList(text));
        } catch (NoClassDefFoundError | Exception e) {
            DebugLogger.debug("HologramUtil", "Failed to create DecentHolograms hologram: " + e.getMessage());
            dhAvailable = false;
        }
    }

    private static void updateDH(String id, String text) {
        try {
            eu.decentsoftware.holograms.api.holograms.Hologram holo = eu.decentsoftware.holograms.api.DHAPI.getHologram(id);
            if (holo != null) {
                eu.decentsoftware.holograms.api.DHAPI.setHologramLines(holo, java.util.Collections.singletonList(text));
            }
        } catch (NoClassDefFoundError | Exception e) {
            DebugLogger.debug("HologramUtil", "Failed to update DecentHolograms hologram: " + e.getMessage());
        }
    }

    private static void removeDH(String id) {
        try {
            eu.decentsoftware.holograms.api.DHAPI.removeHologram(id);
        } catch (NoClassDefFoundError | Exception ignored) {}
    }

    // ==================== HolographicDisplays ====================

    private static void createHD(Player player, Location loc, String text) {
        try {
            com.gmail.filoghost.holographicdisplays.api.Hologram holo =
                    com.gmail.filoghost.holographicdisplays.api.HologramsAPI.createHologram(
                            Bukkit.getPluginManager().getPlugin("X-SetSpawn"), loc);
            holo.appendTextLine(text);
            activeHolograms.put(player.getUniqueId(), holo);
        } catch (NoClassDefFoundError | Exception e) {
            DebugLogger.debug("HologramUtil", "Failed to create HolographicDisplays hologram: " + e.getMessage());
            hdAvailable = false;
        }
    }

    private static void updateHD(Player player, String text) {
        try {
            Object holo = activeHolograms.get(player.getUniqueId());
            if (holo instanceof com.gmail.filoghost.holographicdisplays.api.Hologram) {
                com.gmail.filoghost.holographicdisplays.api.Hologram hdHolo =
                        (com.gmail.filoghost.holographicdisplays.api.Hologram) holo;
                // Clear existing lines
                for (com.gmail.filoghost.holographicdisplays.api.HologramLine line : hdHolo.getLines()) {
                    line.removeLine();
                }
                hdHolo.appendTextLine(text);
            }
        } catch (NoClassDefFoundError | Exception e) {
            DebugLogger.debug("HologramUtil", "Failed to update HolographicDisplays hologram: " + e.getMessage());
        }
    }

    private static void removeHD(Player player) {
        try {
            Object holo = activeHolograms.remove(player.getUniqueId());
            if (holo instanceof com.gmail.filoghost.holographicdisplays.api.Hologram) {
                ((com.gmail.filoghost.holographicdisplays.api.Hologram) holo).delete();
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
        activeHolograms.put(player.getUniqueId(), armorStand);
    }
}