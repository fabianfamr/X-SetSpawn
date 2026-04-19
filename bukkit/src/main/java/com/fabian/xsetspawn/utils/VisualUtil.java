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

    static {
        try {
            Player.class.getMethod("sendTitle", String.class, String.class, int.class, int.class, int.class);
        } catch (NoSuchMethodException e) {
            useReflectionForTitles = true;
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
            sendTitleReflection(player, title, subtitle, fadeIn, stay, fadeOut);
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
                        .invoke(null, "{\"text\":\"" + title.replace("&", "§") + "\"}");
                Object titlePacket = getNMSClass("PacketPlayOutTitle").getConstructor(enumTitle.getClass(), getNMSClass("IChatBaseComponent"))
                        .newInstance(enumTitle, titleChat);
                sendPacket(player, titlePacket);
            }

            // Subtitle
            if (subtitle != null) {
                Object enumSubtitle = getNMSClass("PacketPlayOutTitle$EnumTitleAction").getField("SUBTITLE").get(null);
                Object subtitleChat = getNMSClass("IChatBaseComponent$ChatSerializer").getMethod("a", String.class)
                        .invoke(null, "{\"text\":\"" + subtitle.replace("&", "§") + "\"}");
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

    private static void sendPacket(Player player, Object packet) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object playerConnection = handle.getClass().getField("playerConnection").get(handle);
            playerConnection.getClass().getMethod("sendPacket", getNMSClass("Packet")).invoke(playerConnection, packet);
        } catch (Exception ignored) { }
    }

    public static void spawnFirework(Location location, Color color, int power) {
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
    }
}

