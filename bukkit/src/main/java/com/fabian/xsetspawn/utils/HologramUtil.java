package com.fabian.xsetspawn.utils;

import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import eu.decentsoftware.holograms.api.DHAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hologram utility with multi-provider support.
 * Priority: FancyHolograms > DecentHolograms > ArmorStand fallback
 */
public class HologramUtil {

    private static boolean fhAvailable = false;
    private static boolean dhAvailable = false;

    private static final Map<UUID, Object> trackedHolograms = new ConcurrentHashMap<>();

    static {
        fhAvailable = Bukkit.getPluginManager().isPluginEnabled("FancyHolograms");
        dhAvailable = Bukkit.getPluginManager().isPluginEnabled("DecentHolograms");
        if (fhAvailable) {
            DebugLogger.debug("HologramUtil", "Hologram provider: FancyHolograms");
        } else if (dhAvailable) {
            DebugLogger.debug("HologramUtil", "Hologram provider: DecentHolograms");
        } else {
            DebugLogger.debug("HologramUtil", "No hologram plugin found, using ArmorStand fallback");
        }
    }

    /**
     * Returns the name of the active hologram provider.
     */
    public static String getProviderName() {
        if (fhAvailable) return "FancyHolograms";
        if (dhAvailable) return "DecentHolograms";
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

        if (fhAvailable) {
            createFH(id, displayLoc, coloredText);
        } else if (dhAvailable) {
            createDH(id, displayLoc, coloredText);
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

        if (fhAvailable) {
            updateFH(id, coloredText);
        } else if (dhAvailable) {
            updateDH(id, coloredText);
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

        if (fhAvailable) {
            removeFH(id);
        } else if (dhAvailable) {
            removeDH(id);
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
        if (fhAvailable) {
            for (UUID uuid : trackedHolograms.keySet()) {
                String id = "xsetspawn_" + uuid.toString().substring(0, 8);
                removeFH(id);
            }
        }

        if (dhAvailable) {
            for (UUID uuid : trackedHolograms.keySet()) {
                String id = "xsetspawn_" + uuid.toString().substring(0, 8);
                removeDH(id);
            }
        }

        // ArmorStand: tracked in map
        for (Map.Entry<UUID, Object> entry : trackedHolograms.entrySet()) {
            try {
                Object obj = entry.getValue();
                if (obj instanceof ArmorStand) {
                    if (((ArmorStand) obj).isValid()) ((ArmorStand) obj).remove();
                }
            } catch (Throwable ignored) {}
        }
        trackedHolograms.clear();
    }

    // ==================== FancyHolograms ====================

    /**
     * Get the FancyHolograms HologramManager via reflection,
     * since the main plugin class is not part of the public API.
     */
    private static HologramManager getFHManager() {
        try {
            Plugin fhPlugin = Bukkit.getPluginManager().getPlugin("FancyHolograms");
            if (fhPlugin == null) return null;
            return (HologramManager) fhPlugin.getClass()
                    .getMethod("getHologramManager")
                    .invoke(fhPlugin);
        } catch (NoClassDefFoundError | Exception e) {
            DebugLogger.debug("HologramUtil", "Failed to get FancyHolograms manager: " + e.getMessage());
            return null;
        }
    }

    private static void createFH(String id, Location loc, String text) {
        try {
            HologramManager manager = getFHManager();
            if (manager == null) return;

            // Remove existing if present from a previous unclosed session
            Optional<Hologram> existingOpt = manager.getHologram(id);
            if (existingOpt.isPresent()) {
                manager.removeHologram(existingOpt.get());
            }

            TextHologramData data = new TextHologramData(id, loc);
            data.setText(Collections.singletonList(text));
            data.setPersistent(false);

            Hologram holo = manager.create(data);
            manager.addHologram(holo);
        } catch (NoClassDefFoundError | Exception e) {
            DebugLogger.debug("HologramUtil", "Failed to create FancyHolograms hologram: " + e.getMessage());
            fhAvailable = false;
        }
    }

    private static void updateFH(String id, String text) {
        try {
            HologramManager manager = getFHManager();
            if (manager == null) return;

            Optional<Hologram> holoOpt = manager.getHologram(id);
            if (holoOpt.isPresent()) {
                Hologram holo = holoOpt.get();
                if (holo.getData() instanceof TextHologramData) {
                    TextHologramData textData = (TextHologramData) holo.getData();
                    textData.setText(Collections.singletonList(text));
                    holo.forceUpdate();
                }
            }
        } catch (NoClassDefFoundError | Exception e) {
            DebugLogger.debug("HologramUtil", "Failed to update FancyHolograms hologram: " + e.getMessage());
        }
    }

    private static void removeFH(String id) {
        try {
            HologramManager manager = getFHManager();
            if (manager == null) return;

            Optional<Hologram> holoOpt = manager.getHologram(id);
            if (holoOpt.isPresent()) {
                manager.removeHologram(holoOpt.get());
            }
        } catch (NoClassDefFoundError | Exception ignored) {}
    }

    // ==================== DecentHolograms ====================

    private static void createDH(String id, Location loc, String text) {
        try {
            DHAPI.removeHologram(id);
            eu.decentsoftware.holograms.api.holograms.Hologram holo = DHAPI.createHologram(id, loc);
            if (holo != null) {
                DHAPI.addHologramLine(holo, text);
            }
        } catch (NoClassDefFoundError | Exception e) {
            DebugLogger.debug("HologramUtil", "Failed to create DecentHolograms hologram: " + e.getMessage());
            dhAvailable = false;
        }
    }

    private static void updateDH(String id, String text) {
        try {
            eu.decentsoftware.holograms.api.holograms.Hologram holo = DHAPI.getHologram(id);
            if (holo != null) {
                DHAPI.setHologramLines(holo, Collections.singletonList(text));
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
