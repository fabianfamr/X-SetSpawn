package com.fabian.xsetspawn.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hologram utility with multi-provider support.
 * Priority: FancyHolograms > DecentHolograms > ArmorStand fallback.
 * All external plugin interactions use reflection to avoid NoClassDefFoundError.
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

    public static String getProviderName() {
        if (fhAvailable) return "FancyHolograms";
        if (dhAvailable) return "DecentHolograms";
        return "ArmorStand (built-in)";
    }

    public static void createHologram(Player player, Location location, String text, double heightOffset) {
        removeHologram(player);

        Location displayLoc = location.clone().add(0, heightOffset, 0);
        String id = "xsetspawn_" + player.getUniqueId().toString().substring(0, 8);
        String coloredText = org.bukkit.ChatColor.translateAlternateColorCodes('&', text);

        if (fhAvailable) {
            Object holo = createFH(id, displayLoc, coloredText);
            if (holo != null) trackedHolograms.put(player.getUniqueId(), holo);
        } else if (dhAvailable) {
            createDH(id, displayLoc, coloredText);
        } else {
            createArmorStand(player, displayLoc, coloredText);
        }
    }

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

    // ==================== FancyHolograms (pure reflection) ====================

    private static Object getFHManager() {
        try {
            Plugin fhPlugin = Bukkit.getPluginManager().getPlugin("FancyHolograms");
            if (fhPlugin == null) return null;
            return fhPlugin.getClass().getMethod("getHologramManager").invoke(fhPlugin);
        } catch (NoClassDefFoundError | Exception e) {
            DebugLogger.debug("HologramUtil", "Failed to get FancyHolograms manager: " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object createFH(String id, Location loc, String text) {
        try {
            Object manager = getFHManager();
            if (manager == null) return null;

            Class<?> textDataClass = Class.forName("de.oliver.fancyholograms.api.data.TextHologramData");
            Object data = textDataClass.getConstructor(String.class, Location.class).newInstance(id, loc);

            Method setText = textDataClass.getMethod("setText", java.util.List.class);
            setText.invoke(data, Collections.singletonList(text));

            Method setPersistent = textDataClass.getMethod("setPersistent", boolean.class);
            setPersistent.invoke(data, false);

            Object holo = manager.getClass().getMethod("create", Class.forName("de.oliver.fancyholograms.api.data.HologramData")).invoke(manager, data);
            manager.getClass().getMethod("addHologram", holo.getClass()).invoke(manager, holo);

            return holo;
        } catch (NoClassDefFoundError | Exception e) {
            DebugLogger.debug("HologramUtil", "Failed to create FancyHolograms hologram: " + e.getMessage());
            fhAvailable = false;
            return null;
        }
    }

    private static void updateFH(String id, String text) {
        try {
            Object manager = getFHManager();
            if (manager == null) return;

            Method getHologram = manager.getClass().getMethod("getHologram", String.class);
            Object optional = getHologram.invoke(manager, id);

            Method isPresent = optional.getClass().getMethod("isPresent");
            if (!(boolean) isPresent.invoke(optional)) return;

            Method get = optional.getClass().getMethod("get");
            Object holo = get.invoke(optional);

            Class<?> textDataClass = Class.forName("de.oliver.fancyholograms.api.data.TextHologramData");
            Object holoData = holo.getClass().getMethod("getData").invoke(holo);

            if (textDataClass.isInstance(holoData)) {
                Method setText = textDataClass.getMethod("setText", java.util.List.class);
                setText.invoke(holoData, Collections.singletonList(text));
                holo.getClass().getMethod("forceUpdate").invoke(holo);
            }
        } catch (NoClassDefFoundError | Exception e) {
            DebugLogger.debug("HologramUtil", "Failed to update FancyHolograms hologram: " + e.getMessage());
        }
    }

    private static void removeFH(String id) {
        try {
            Object manager = getFHManager();
            if (manager == null) return;

            Method getHologram = manager.getClass().getMethod("getHologram", String.class);
            Object optional = getHologram.invoke(manager, id);

            Method isPresent = optional.getClass().getMethod("isPresent");
            if ((boolean) isPresent.invoke(optional)) {
                Method get = optional.getClass().getMethod("get");
                Object holo = get.invoke(optional);
                manager.getClass().getMethod("removeHologram", holo.getClass()).invoke(manager, holo);
            }
        } catch (NoClassDefFoundError | Exception ignored) {}
    }

    // ==================== DecentHolograms (pure reflection) ====================

    private static void createDH(String id, Location loc, String text) {
        try {
            Class<?> dhapiClass = Class.forName("eu.decentsoftware.holograms.api.DHAPI");
            Method removeHologram = dhapiClass.getMethod("removeHologram", String.class);
            removeHologram.invoke(null, id);

            Method createHologram = dhapiClass.getMethod("createHologram", String.class, Location.class);
            Object holo = createHologram.invoke(null, id, loc);

            if (holo != null) {
                Method addLine = dhapiClass.getMethod("addHologramLine", holo.getClass(), String.class);
                addLine.invoke(null, holo, text);
            }
        } catch (NoClassDefFoundError | Exception e) {
            DebugLogger.debug("HologramUtil", "Failed to create DecentHolograms hologram: " + e.getMessage());
            dhAvailable = false;
        }
    }

    private static void updateDH(String id, String text) {
        try {
            Class<?> dhapiClass = Class.forName("eu.decentsoftware.holograms.api.DHAPI");
            Method getHologram = dhapiClass.getMethod("getHologram", String.class);
            Object holo = getHologram.invoke(null, id);

            if (holo != null) {
                Method setLines = dhapiClass.getMethod("setHologramLines", holo.getClass(), java.util.List.class);
                setLines.invoke(null, holo, Collections.singletonList(text));
            }
        } catch (NoClassDefFoundError | Exception e) {
            DebugLogger.debug("HologramUtil", "Failed to update DecentHolograms hologram: " + e.getMessage());
        }
    }

    private static void removeDH(String id) {
        try {
            Class<?> dhapiClass = Class.forName("eu.decentsoftware.holograms.api.DHAPI");
            Method removeHologram = dhapiClass.getMethod("removeHologram", String.class);
            removeHologram.invoke(null, id);
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