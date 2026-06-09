package com.fabian.xsetspawn.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextUtil {

    private static final MiniMessage MINI_MESSAGE;
    private static final LegacyComponentSerializer LEGACY_SERIALIZER;
    private static final LegacyComponentSerializer SECTION_SERIALIZER;
    private static final net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer PLAIN_SERIALIZER;
    private static final boolean ADVENTURE_AVAILABLE;
    private static final boolean HEX_SUPPORT;

    static {
        boolean adventureCheck = false;
        MiniMessage mm = null;
        LegacyComponentSerializer ampersandSerializer = null;
        LegacyComponentSerializer sectionSerializer = null;
        try {
            Class.forName("net.kyori.adventure.text.Component");
            mm = MiniMessage.miniMessage();
            ampersandSerializer = LegacyComponentSerializer.legacyAmpersand();
            sectionSerializer = LegacyComponentSerializer.legacySection();
            adventureCheck = true;
        } catch (Throwable e) {
            adventureCheck = false;
        }
        ADVENTURE_AVAILABLE = adventureCheck;
        MINI_MESSAGE = mm;
        LEGACY_SERIALIZER = ampersandSerializer;
        SECTION_SERIALIZER = sectionSerializer;
        PLAIN_SERIALIZER = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();

        boolean hex;
        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            int major = Integer.parseInt(version.split("_")[1]);
            hex = major >= 16;
        } catch (Exception e) {
            hex = true; // Modern versions (1.20.5+) or unknown
        }
        HEX_SUPPORT = hex;
    }

    private static final Pattern PER_CHAR_HEX_PATTERN = Pattern
            .compile("&#([0-9A-Fa-f]{6})((?:[&\u00a7][a-zA-Z0-9])*)([^&\u00a7<>])");
    private static final Pattern GRADIENT_PATTERN = Pattern
            .compile("<gradient:((?:#[0-9A-Fa-f]{6}:?)+)>(.*?)</gradient>", Pattern.CASE_INSENSITIVE);

    public static Component format(String input) {
        if (!ADVENTURE_AVAILABLE) {
            return null;
        }
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }

        // To support both & and MiniMessage tags together, we can translate & to MM
        // tags
        // if we detect that the user is trying to use MiniMessage.
        if (input.contains("<") || input.contains("&#")) {
            String processed = input;

            // Normalize legacy hex &#RRGGBB to <#RRGGBB> for MiniMessage
            if (processed.contains("&#")) {
                processed = processed.replaceAll("&#([A-Fa-f0-9]{6})", "<#$1>");
            }

            String mmCompatible = processed
                    .replace("&0", "<black>").replace("&1", "<dark_blue>").replace("&2", "<dark_green>")
                    .replace("&3", "<dark_aqua>").replace("&4", "<dark_red>").replace("&5", "<dark_purple>")
                    .replace("&6", "<gold>").replace("&7", "<gray>").replace("&8", "<dark_gray>")
                    .replace("&9", "<blue>").replace("&a", "<green>").replace("&b", "<aqua>")
                    .replace("&c", "<red>").replace("&d", "<light_purple>").replace("&e", "<yellow>")
                    .replace("&f", "<white>").replace("&k", "<obfuscated>").replace("&l", "<bold>")
                    .replace("&m", "<strikethrough>").replace("&n", "<underlined>").replace("&o", "<italic>")
                    .replace("&r", "<reset>");
            try {
                Component component = MINI_MESSAGE.deserialize(mmCompatible);
                if (!HEX_SUPPORT) {
                    // Downsample component to 16 colors if hex is not supported
                    return LEGACY_SERIALIZER.deserialize(SECTION_SERIALIZER.serialize(component));
                }
                return component;
            } catch (Exception e) {
                // Fallback to legacy if MM fails
            }
        }

        // Fallback/Legacy logic
        input = processGradients(input);
        if (hasPerCharacterHex(input)) {
            return parsePerCharacterHex(input);
        }

        return LEGACY_SERIALIZER.deserialize(input);
    }

    public static String formatToLegacy(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        if (ADVENTURE_AVAILABLE) {
            try {
                Component component = format(input);
                if (component != null) {
                    return SECTION_SERIALIZER.serialize(component);
                }
            } catch (Exception ignored) {
            }
        }

        // Fallback for non-adventure environments or if serialization fails
        input = processGradients(input);
        String withLegacyColors = ChatColor.translateAlternateColorCodes('&', input);
        String withHex = translateHexToLegacy(withLegacyColors);
        if (hasPerCharacterHex(withHex)) {
            return translatePerCharacterHexToLegacy(withHex);
        }
        return withHex;
    }

    private static boolean hasPerCharacterHex(String input) {
        return input != null && PER_CHAR_HEX_PATTERN.matcher(input).find();
    }

    public static String processGradients(String input) {
        if (input == null || !input.contains("<gradient:")) {
            return input;
        }
        Matcher matcher = GRADIENT_PATTERN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String colorString = matcher.group(1);
            String content = matcher.group(2);
            if (colorString.endsWith(":")) {
                colorString = colorString.substring(0, colorString.length() - 1);
            }
            String replacement = applyGradient(content, colorString);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String applyGradient(String content, String colorString) {
        String[] colors = colorString.split(":");
        if (colors.length == 0)
            return content;
        if (colors.length == 1)
            return "&#" + colors[0].substring(1) + content;

        StringBuilder result = new StringBuilder();
        String currentFormatting = "";

        int visibleLength = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '&' && i + 1 < content.length()) {
                char next = content.charAt(i + 1);
                if ("0123456789AaBbCcDdEeFfKkLlMmNnOoRr".indexOf(next) != -1) {
                    i++;
                    continue;
                }
            }
            visibleLength++;
        }

        if (visibleLength == 0)
            return content;

        int charIndex = 0;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '&' && i + 1 < content.length()) {
                char next = content.charAt(i + 1);
                if ("0123456789AaBbCcDdEeFfKkLlMmNnOoRr".indexOf(next) != -1) {
                    char lowerNext = Character.toLowerCase(next);
                    if (lowerNext == 'r') {
                        currentFormatting = "";
                    } else if ("klmno".indexOf(lowerNext) != -1) {
                        currentFormatting += "&" + next;
                    }
                    i++;
                    continue;
                }
            }

            double ratio = visibleLength > 1 ? (double) charIndex / (visibleLength - 1) : 0;
            String hex = interpolateBetweenMultiple(colors, ratio);

            result.append("&#").append(hex).append(currentFormatting).append(c);
            charIndex++;
        }

        return result.toString();
    }

    private static String interpolateBetweenMultiple(String[] colors, double ratio) {
        if (ratio <= 0)
            return colors[0].substring(1);
        if (ratio >= 1)
            return colors[colors.length - 1].substring(1);

        double section = ratio * (colors.length - 1);
        int index = (int) section;
        double localRatio = section - index;

        return interpolate(colors[index], colors[index + 1], localRatio);
    }

    private static String interpolate(String color1, String color2, double ratio) {
        int r1 = Integer.parseInt(color1.substring(1, 3), 16);
        int g1 = Integer.parseInt(color1.substring(3, 5), 16);
        int b1 = Integer.parseInt(color1.substring(5, 7), 16);

        int r2 = Integer.parseInt(color2.substring(1, 3), 16);
        int g2 = Integer.parseInt(color2.substring(3, 5), 16);
        int b2 = Integer.parseInt(color2.substring(5, 7), 16);

        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);

        return String.format("%02x%02x%02x", r, g, b);
    }

    private static Component parsePerCharacterHex(String input) {
        TextComponent.Builder builder = Component.text();
        String processed = ChatColor.translateAlternateColorCodes('&', input);
        Matcher matcher = PER_CHAR_HEX_PATTERN.matcher(processed);
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                builder.append(Component.text(processed.substring(lastEnd, matcher.start())));
            }
            String hex = matcher.group(1);
            String formatting = matcher.group(2);
            String character = matcher.group(3);
            TextColor color = TextColor.fromHexString("#" + hex);
            TextComponent.Builder charBuilder = Component.text();
            if (color != null) {
                if (HEX_SUPPORT) {
                    charBuilder.color(color);
                } else {
                    // Downsample individual characters for old versions
                    charBuilder.color(net.kyori.adventure.text.format.NamedTextColor.nearestTo(color));
                }
            }
            charBuilder.content(character);
            if (formatting != null && !formatting.isEmpty()) {
                String fmt = formatting.replace("&", "").replace("\u00a7", "");
                for (char c : fmt.toCharArray()) {
                    switch (Character.toLowerCase(c)) {
                        case 'l':
                            charBuilder.decoration(TextDecoration.BOLD, true);
                            break;
                        case 'o':
                            charBuilder.decoration(TextDecoration.ITALIC, true);
                            break;
                        case 'n':
                            charBuilder.decoration(TextDecoration.UNDERLINED, true);
                            break;
                        case 'm':
                            charBuilder.decoration(TextDecoration.STRIKETHROUGH, true);
                            break;
                        case 'k':
                            charBuilder.decoration(TextDecoration.OBFUSCATED, true);
                            break;
                    }
                }
            }
            builder.append(charBuilder.build());
            lastEnd = matcher.end();
        }
        if (lastEnd < processed.length()) {
            builder.append(Component.text(processed.substring(lastEnd)));
        }
        return builder.build();
    }

    private static String translatePerCharacterHexToLegacy(String input) {
        StringBuffer sb = new StringBuffer();
        Matcher matcher = PER_CHAR_HEX_PATTERN.matcher(input);
        while (matcher.find()) {
            String hex = matcher.group(1);
            String formatting = matcher.group(2) != null ? matcher.group(2) : "";
            String character = matcher.group(3);
            String legacyHex = "§x";
            for (char c : hex.toCharArray()) {
                legacyHex += "§" + Character.toLowerCase(c);
            }
            String legacyFormatting = formatting.replace("&", "§");
            matcher.appendReplacement(sb, legacyHex + legacyFormatting + character);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String translateHexToLegacy(String input) {
        Pattern pattern = Pattern.compile("&#([0-9A-Fa-f]{6})");
        Matcher matcher = pattern.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            if (HEX_SUPPORT) {
                StringBuilder legacy = new StringBuilder("§x");
                for (char c : hex.toCharArray()) {
                    legacy.append("§").append(Character.toLowerCase(c));
                }
                matcher.appendReplacement(sb, legacy.toString());
            } else {
                matcher.appendReplacement(sb, getNearestLegacyColor(hex));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String getNearestLegacyColor(String hex) {
        int r = Integer.parseInt(hex.substring(0, 2), 16);
        int g = Integer.parseInt(hex.substring(2, 4), 16);
        int b = Integer.parseInt(hex.substring(4, 6), 16);

        // Predefined legacy colors
        int[][] legacyColors = {
                { 0, 0, 0 }, { 0, 0, 170 }, { 0, 170, 0 }, { 0, 170, 170 },
                { 170, 0, 0 }, { 170, 0, 170 }, { 255, 170, 0 }, { 170, 170, 170 },
                { 85, 85, 85 }, { 85, 85, 255 }, { 85, 255, 85 }, { 85, 255, 255 },
                { 255, 85, 85 }, { 255, 85, 255 }, { 255, 255, 85 }, { 255, 255, 255 }
        };
        String[] legacyCodes = {
                "§0", "§1", "§2", "§3", "§4", "§5", "§6", "§7",
                "§8", "§9", "§a", "§b", "§c", "§d", "§e", "§f"
        };

        double minDistance = Double.MAX_VALUE;
        int bestIndex = 0;

        for (int i = 0; i < legacyColors.length; i++) {
            double distance = Math.pow(r - legacyColors[i][0], 2) +
                    Math.pow(g - legacyColors[i][1], 2) +
                    Math.pow(b - legacyColors[i][2], 2);
            if (distance < minDistance) {
                minDistance = distance;
                bestIndex = i;
            }
        }
        return legacyCodes[bestIndex];
    }

    public static void sendMessage(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) {
            return;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(formatToLegacy(message));
            return;
        }
        Player player = (Player) sender;
        if (ADVENTURE_AVAILABLE) {
            Component component = format(message);
            if (component != null) {
                try {
                    player.sendMessage(component);
                    return;
                } catch (Throwable e) {
                    // Fall through to legacy
                }
            }
        }
        player.sendMessage(formatToLegacy(message));
    }

    public static void sendMessage(Player player, String message) {
        sendMessage((CommandSender) player, message);
    }

    public static void sendMessages(CommandSender sender, String... messages) {
        for (String message : messages) {
            sendMessage(sender, message);
        }
    }

    public static void broadcast(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        String legacy = formatToLegacy(message);
        if (ADVENTURE_AVAILABLE) {
            Component component = format(message);
            if (component != null) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    try {
                        player.sendMessage(component);
                    } catch (Throwable e) {
                        player.sendMessage(legacy);
                    }
                }
                Bukkit.getConsoleSender().sendMessage(legacy);
                return;
            }
        }
        Bukkit.broadcastMessage(legacy);
    }

    public static void broadcastLegacy(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        Bukkit.broadcastMessage(formatToLegacy(message));
    }

    public static boolean hasMiniMessage(String input) {
        if (input == null) {
            return false;
        }
        String lower = input.toLowerCase();
        return (input.contains("<") && input.contains(">")) &&
                (lower.contains("<gradient") || lower.contains("<bold") || lower.contains("<b>") ||
                        lower.contains("<italic") || lower.contains("<i>") ||
                        lower.contains("<underlined") || lower.contains("<u>") ||
                        lower.contains("<strikethrough") || lower.contains("<st>") ||
                        lower.contains("<obfuscated") || lower.contains("<obf>") ||
                        lower.contains("<reset") || lower.contains("<font") ||
                        lower.contains("<rainbow") || lower.contains("<click") ||
                        lower.contains("<hover") || lower.contains("<newline") ||
                        lower.contains("<transition") ||
                        lower.matches(".*<#(?:[0-9a-f]{3}){1,2}>.*") || // Hex colors <#fff> or <#ffffff>
                        lower.contains("<red>") || lower.contains("<green>") || lower.contains("<blue>") ||
                        lower.contains("<yellow>") || lower.contains("<white>") || lower.contains("<black>") ||
                        lower.contains("<gold>") || lower.contains("<aqua>") || lower.contains("<gray>") ||
                        lower.contains("<dark_"));
    }

    public static boolean hasHexColors(String input) {
        if (input == null) {
            return false;
        }
        return input.matches(".*&#[0-9A-Fa-f]{6}.*");
    }
}
