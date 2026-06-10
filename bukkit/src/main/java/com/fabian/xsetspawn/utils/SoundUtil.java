package com.fabian.xsetspawn.utils;

import org.bukkit.Sound;
import java.util.HashMap;
import java.util.Map;

public class SoundUtil {

    private static final Map<String, String> SOUND_MAP = new HashMap<>();
    private static final Map<String, String> REVERSE_SOUND_MAP = new HashMap<>();

    static {
        // Map common 1.8 sounds to modern 1.9+ names
        SOUND_MAP.put("ENDERMAN_TELEPORT", "ENTITY_ENDERMAN_TELEPORT");
        SOUND_MAP.put("LEVEL_UP", "ENTITY_PLAYER_LEVELUP");
        SOUND_MAP.put("ORB_PICKUP", "ENTITY_EXPERIENCE_ORB_PICKUP");
        SOUND_MAP.put("NOTE_PLING", "BLOCK_NOTE_BLOCK_PLING");
        SOUND_MAP.put("NOTE_PIANO", "BLOCK_NOTE_BLOCK_HARP");
        SOUND_MAP.put("FIREWORK_LAUNCH", "ENTITY_FIREWORK_ROCKET_LAUNCH");
        SOUND_MAP.put("FIREWORK_TWINKLE", "ENTITY_FIREWORK_ROCKET_TWINKLE");
        SOUND_MAP.put("EXPLODE", "ENTITY_GENERIC_EXPLODE");
        SOUND_MAP.put("WITHER_SPAWN", "ENTITY_WITHER_SPAWN");
        SOUND_MAP.put("CHICKEN_EGG_POP", "ENTITY_CHICKEN_EGG");
        SOUND_MAP.put("WOOD_CLICK", "BLOCK_WOODEN_BUTTON_CLICK_ON");
        SOUND_MAP.put("STEP_GRASS", "BLOCK_GRASS_STEP");

        // Build reverse map for O(1) modern→legacy lookups
        for (Map.Entry<String, String> entry : SOUND_MAP.entrySet()) {
            REVERSE_SOUND_MAP.put(entry.getValue(), entry.getKey());
        }
    }

    /**
     * Resolves a sound name by trying the original name and its modern/legacy
     * counterparts.
     */
    public static Sound resolveSound(String soundName) {
        if (soundName == null || soundName.isEmpty())
            return null;

        String upperName = soundName.toUpperCase();

        // 1. Try the name as provided
        try {
            return Sound.valueOf(upperName);
        } catch (IllegalArgumentException ignored) {
        }

        // 2. Try the mapped name (Legacy to Modern)
        String mapped = SOUND_MAP.get(upperName);
        if (mapped != null) {
            try {
                return Sound.valueOf(mapped);
            } catch (IllegalArgumentException ignored) {
            }
        }

        // 3. Try reverse map (Modern to Legacy) - O(1) lookup
        String reversed = REVERSE_SOUND_MAP.get(upperName);
        if (reversed != null) {
            try {
                return Sound.valueOf(reversed);
            } catch (IllegalArgumentException ignored) {
            }
        }

        return null;
    }
}

