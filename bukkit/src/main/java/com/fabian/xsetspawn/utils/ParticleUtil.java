package com.fabian.xsetspawn.utils;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.lang.reflect.Method;

public class ParticleUtil {

    private static Method spawnParticleMethod = null;

    static {
        try {
            // Check if Particle class exists (1.9+)
            Class<?> particleClass = Class.forName("org.bukkit.Particle");
            spawnParticleMethod = org.bukkit.World.class.getMethod("spawnParticle", particleClass, Location.class,
                    int.class, double.class, double.class, double.class, double.class);
        } catch (Exception ignored) {
        }
    }

    /**
     * Spawns particles at a location.
     * Uses reflection for 1.9+ and playEffect for 1.8.
     */
    public static void spawnParticle(Location loc, String particleName, int amount) {
        if (spawnParticleMethod != null) {
            try {
                // Modern version (1.9+)
                Class<?> particleClass = Class.forName("org.bukkit.Particle");
                Object particle = particleClass.getMethod("valueOf", String.class).invoke(null, particleName);
                spawnParticleMethod.invoke(loc.getWorld(), particle, loc, amount, 0.5, 0.5, 0.5, 0.1);
                return;
            } catch (Exception ignored) {
            }
        }

        // Fallback for 1.8 (Effect API)
        try {
            org.bukkit.Effect effect = null;
            try {
                effect = org.bukkit.Effect.valueOf(particleName);
            } catch (Exception ex) {
                // Common mappings
                if (particleName.equals("VILLAGER_HAPPY")) {
                    try {
                        effect = org.bukkit.Effect.valueOf("HAPPY_VILLAGER");
                    } catch (Exception e) {
                    }
                } else if (particleName.equals("FLAME")) {
                    try {
                        effect = org.bukkit.Effect.valueOf("FLAME");
                    } catch (Exception e) {
                    }
                }
            }

            if (effect != null) {
                loc.getWorld().playEffect(loc, effect, 0);
            }
        } catch (Exception ignored) {
        }
    }

    public static void spawnSpiral(Player player, String particleName, int amount) {
        Location loc = player.getLocation().add(0, 0.1, 0);
        for (double i = 0; i < Math.PI * 2; i += Math.PI / 8) {
            double x = Math.cos(i) * 0.8;
            double z = Math.sin(i) * 0.8;
            spawnParticle(loc.clone().add(x, 0, z), particleName, 1);
        }
    }
}

