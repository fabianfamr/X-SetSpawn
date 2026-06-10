package com.fabian.xsetspawn.utils;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.lang.reflect.Method;

public class ParticleUtil {

    private static Method spawnParticleMethod = null;
    private static Method particleValueOfMethod = null;

    // Cached particle enum value to avoid repeated reflection lookups
    private static Object cachedParticle = null;
    private static String cachedParticleName = null;

    static {
        try {
            // Check if Particle class exists (1.9+)
            Class<?> particleClass = Class.forName("org.bukkit.Particle");
            spawnParticleMethod = org.bukkit.World.class.getMethod("spawnParticle", particleClass, Location.class,
                    int.class, double.class, double.class, double.class, double.class);
            particleValueOfMethod = particleClass.getMethod("valueOf", String.class);
        } catch (Exception ignored) {
        }
    }

    /**
     * Resolves and caches the particle enum object from its string name.
     */
    private static Object resolveParticle(String particleName) {
        if (particleValueOfMethod == null) return null;
        if (particleName.equals(cachedParticleName) && cachedParticle != null) {
            return cachedParticle;
        }
        try {
            cachedParticle = particleValueOfMethod.invoke(null, particleName);
            cachedParticleName = particleName;
            return cachedParticle;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Spawns particles at a location.
     * Uses cached reflection for 1.9+ and playEffect for 1.8.
     */
    public static void spawnParticle(Location loc, String particleName, int amount) {
        if (spawnParticleMethod != null) {
            Object particle = resolveParticle(particleName);
            if (particle != null) {
                try {
                    spawnParticleMethod.invoke(loc.getWorld(), particle, loc, amount, 0.5, 0.5, 0.5, 0.1);
                    return;
                } catch (Exception ignored) {
                }
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
        Location base = player.getLocation().add(0, 0.1, 0);
        // Reuse a single Location object to reduce GC pressure
        Location temp = base.clone();
        for (double i = 0; i < Math.PI * 2; i += Math.PI / 8) {
            double x = Math.cos(i) * 0.8;
            double z = Math.sin(i) * 0.8;
            temp.setX(base.getX() + x);
            temp.setZ(base.getZ() + z);
            spawnParticle(temp, particleName, 1);
        }
    }
}


