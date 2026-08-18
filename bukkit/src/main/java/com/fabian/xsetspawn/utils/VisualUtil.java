package com.fabian.xsetspawn.utils;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

public class VisualUtil {

    private static boolean useReflectionForTitles = false;
    private static boolean nmsAvailable = false;

    static {
        try {
            Player.class.getMethod("sendTitle", String.class, String.class, int.class, int.class, int.class);
        } catch (NoSuchMethodException e) {
            useReflectionForTitles = true;
            // Check if NMS versioned packages are available (pre-1.17 only)
            try {
                String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
                Class.forName("net.minecraft.server." + version + ".IChatBaseComponent");
                nmsAvailable = true;
            } catch (Exception ignored) {
                // 1.17+ or non-standard server — NMS reflection won't work
                nmsAvailable = false;
            }
        }
    }

    public static void sendActionBar(Player player, String message) {
        if (message == null || message.isEmpty()) return;
        try {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
        } catch (Exception e) {
            // Fallback for extremely old versions or custom forks
            player.sendMessage(message);
        }
    }

    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (!useReflectionForTitles) {
            try {
                player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
                return;
            } catch (NoSuchMethodError ignored) {
                useReflectionForTitles = true;
            }
        }

        if (useReflectionForTitles) {
            if (nmsAvailable) {
                sendTitleReflection(player, title, subtitle, fadeIn, stay, fadeOut);
            }
            // If NMS is not available (1.17+), titles are simply unsupported on this fork
        }
    }

    private static void sendTitleReflection(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            
            // Times
            Object enumTimes = getNMSClass("PacketPlayOutTitle$EnumTitleAction").getField("TIMES").get(null);
            Object timesPacket = getNMSClass("PacketPlayOutTitle").getConstructor(enumTimes.getClass(), getNMSClass("IChatBaseComponent"), int.class, int.class, int.class)
                    .newInstance(enumTimes, null, fadeIn, stay, fadeOut);
            sendPacket(player, timesPacket);

            // Title
            if (title != null) {
                Object enumTitle = getNMSClass("PacketPlayOutTitle$EnumTitleAction").getField("TITLE").get(null);
                Object titleChat = getNMSClass("IChatBaseComponent$ChatSerializer").getMethod("a", String.class)
                        .invoke(null, "{\"text\":\"" + escapeJson(title) + "\"}");
                Object titlePacket = getNMSClass("PacketPlayOutTitle").getConstructor(enumTitle.getClass(), getNMSClass("IChatBaseComponent"))
                        .newInstance(enumTitle, titleChat);
                sendPacket(player, titlePacket);
            }

            // Subtitle
            if (subtitle != null) {
                Object enumSubtitle = getNMSClass("PacketPlayOutTitle$EnumTitleAction").getField("SUBTITLE").get(null);
                Object subtitleChat = getNMSClass("IChatBaseComponent$ChatSerializer").getMethod("a", String.class)
                        .invoke(null, "{\"text\":\"" + escapeJson(subtitle) + "\"}");
                Object subtitlePacket = getNMSClass("PacketPlayOutTitle").getConstructor(enumSubtitle.getClass(), getNMSClass("IChatBaseComponent"))
                        .newInstance(enumSubtitle, subtitleChat);
                sendPacket(player, subtitlePacket);
            }

        } catch (Exception e) {
            // Fails silently if completely unsupported
        }
    }

    private static Class<?> getNMSClass(String name) throws ClassNotFoundException {
        String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
        return Class.forName("net.minecraft.server." + version + "." + name);
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length());
        for (char c : input.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static void sendPacket(Player player, Object packet) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object playerConnection = handle.getClass().getField("playerConnection").get(handle);
            playerConnection.getClass().getMethod("sendPacket", getNMSClass("Packet")).invoke(playerConnection, packet);
        } catch (Exception ignored) { }
    }

    public static void spawnFirework(org.bukkit.plugin.Plugin plugin, Location location, Color color, int power) {
        if (location.getWorld() == null) return;
        
        Firework fw = location.getWorld().spawn(location, Firework.class);
        FireworkMeta fwm = fw.getFireworkMeta();
        
        fwm.addEffect(FireworkEffect.builder()
                .withColor(color)
                .with(FireworkEffect.Type.BALL)
                .flicker(true)
                .build());
        fwm.setPower(power);
        fw.setFireworkMeta(fwm);
        
        // Schedule cleanup to prevent orphan firework entities
        // Use SchedulerUtil for Folia/Canvas compatibility
        SchedulerUtil.runRegionDelayed(plugin, location, () -> {
            if (fw.isValid()) fw.remove();
        }, (long)(power + 2) * 20L);
    }
}

