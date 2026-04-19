package com.fabian.xsetspawn.utils;

import org.bukkit.Bukkit;

/**
 * ServerSupport - Detects server software type and Minecraft version.
 *
 * Handles two versioning schemes:
 *   - Legacy: 1.21.1, 1.21.11 (pre-2026)
 *   - New:    26.1, 26.2 (Mojang's year-based scheme, from March 2026 onwards)
 *
 * Compatible with Paper, Purpur, Folia, and vanilla Bukkit/Spigot.
 * Tested range: 1.13 - 26.1+
 */
public class ServerSupport {

    private static final boolean IS_PAPER  = isClassPresent("com.destroystokyo.paper.PaperConfig")
                                          || isClassPresent("io.papermc.paper.configuration.Configuration");
    private static final boolean IS_FOLIA  = isClassPresent("io.papermc.paper.threadedregions.RegionizedServer");
    private static final boolean IS_PURPUR = isClassPresent("org.purpurmc.purpur.PurpurConfig");

    /** true if running Paper or any Paper fork (Purpur, Folia, etc.) */
    public static boolean isPaper()  { return IS_PAPER; }

    /** true if running Folia (region-threaded Paper fork) */
    public static boolean isFolia()  { return IS_FOLIA; }

    /** true if running Purpur */
    public static boolean isPurpur() { return IS_PURPUR; }

    /**
     * Returns the raw Minecraft version string from Bukkit.
     * Examples: "1.21.1", "1.21.11", "26.1"
     */
    public static String getMinecraftVersion() {
        // getBukkitVersion() returns e.g. "1.21.1-R0.1-SNAPSHOT" or "26.1-R0.1-SNAPSHOT"
        return Bukkit.getServer().getBukkitVersion().split("-")[0];
    }

    /**
     * Returns true if the current version uses the new year-based scheme (26.x, 27.x, ...).
     * Mojang adopted this from March 2026 onwards.
     */
    public static boolean isNewVersionScheme() {
        String[] parts = getMinecraftVersion().split("\\.");
        // New scheme: first segment >= 26, and it's NOT a Minecraft 1.x version
        int first = parseIntSafe(parts[0]);
        return first >= 26;
    }

    /**
     * Checks if the current Minecraft version is at least the given legacy version (1.X.Y).
     * Servers running the new versioning scheme (26.1+) satisfy ALL legacy version checks.
     *
     * @param major always 1 for legacy (e.g., 1)
     * @param minor e.g., 21
     */
    public static boolean isAtLeast(int major, int minor) {
        return isAtLeast(major, minor, 0);
    }

    /**
     * Checks if the current Minecraft version is at least the given version.
     *
     * Handles both version schemes:
     *   - Legacy 1.X.Y: isAtLeast(1, 21, 1)
     *   - New YY.N:     isAtLeast(26, 1)  → use major=26, minor=1, patch=0
     *
     * @param major Major version (1 for legacy, 26+ for new scheme)
     * @param minor Minor version (e.g., 21 or 1)
     * @param patch Patch version (e.g., 1; use 0 to ignore)
     */
    public static boolean isAtLeast(int major, int minor, int patch) {
        // New scheme servers (26.1+) are always "newer" than any legacy check
        if (isNewVersionScheme() && major == 1) {
            return true;
        }

        String[] parts = getMinecraftVersion().split("\\.");
        int curMajor = parseIntSafe(parts[0]);
        int curMinor = parts.length >= 2 ? parseIntSafe(parts[1]) : 0;
        int curPatch = parts.length >= 3 ? parseIntSafe(parts[2]) : 0;

        if (curMajor != major) return curMajor > major;
        if (curMinor != minor) return curMinor > minor;
        return curPatch >= patch;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
