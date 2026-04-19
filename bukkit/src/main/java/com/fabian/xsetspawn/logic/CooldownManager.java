package com.fabian.xsetspawn.logic;

import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CooldownManager {

    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public void setCooldown(Player player, int seconds) {
        long expireTime = System.currentTimeMillis() + (seconds * 1000L);
        cooldowns.put(player.getUniqueId(), expireTime);
    }

    public boolean isOnCooldown(Player player) {
        Long expireTime = cooldowns.get(player.getUniqueId());
        return expireTime != null && expireTime > System.currentTimeMillis();
    }

    public long getRemainingTime(Player player) {
        Long expireTime = cooldowns.get(player.getUniqueId());
        if (expireTime == null || expireTime <= System.currentTimeMillis()) {
            return 0;
        }
        return (expireTime - System.currentTimeMillis()) / 1000L;
    }

    public void removeCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
    }
}

