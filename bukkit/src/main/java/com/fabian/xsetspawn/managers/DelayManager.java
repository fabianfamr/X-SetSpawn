package com.fabian.xsetspawn.managers;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.managers.ManagerConfig;
import com.fabian.xsetspawn.utils.DebugLogger;
import com.fabian.xsetspawn.utils.HologramUtil;
import com.fabian.xsetspawn.utils.VisualUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import com.fabian.xsetspawn.utils.ParticleUtil;
import com.fabian.xsetspawn.utils.SchedulerUtil;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

/**
 * Manages delayed teleportation with countdown, visual effects, and cancellation.
 * Uses reflection for BossBar (1.9+) and Sound enum constants to maintain 1.8.8 compatibility.
 */
public class DelayManager implements Listener {

    private final XSetSpawn plugin;
    private final Map<UUID, TeleportSession> pendingTeleports = new java.util.concurrent.ConcurrentHashMap<>();

    // --- BossBar reflection (lazy, 1.9+ only) ---
    private static volatile Class<?> bossBarClass;
    private static volatile Class<?> barColorClass;
    private static volatile Class<?> barStyleClass;
    private static volatile boolean bossBarAvailable = false;

    static {
        try {
            bossBarClass = Class.forName("org.bukkit.boss.BossBar");
            barColorClass = Class.forName("org.bukkit.boss.BarColor");
            barStyleClass = Class.forName("org.bukkit.boss.BarStyle");
            bossBarAvailable = true;
        } catch (ClassNotFoundException ignored) {
            // Running on 1.8.8 — BossBar API not available
        }
    }

    // Cached reflection methods for BossBar
    private static volatile Method bossBarSetProgress;
    private static volatile Method bossBarSetTitle;
    private static volatile Method bossBarRemovePlayer;
    private static volatile Method bossBarRemoveAll;
    private static volatile Method bossBarAddPlayer;

    private static void ensureBossBarMethods() {
        if (bossBarSetProgress != null) return;
        synchronized (DelayManager.class) {
            if (bossBarSetProgress != null) return;
            try {
                bossBarSetProgress = bossBarClass.getMethod("setProgress", double.class);
                bossBarSetTitle = bossBarClass.getMethod("setTitle", String.class);
                bossBarRemovePlayer = bossBarClass.getMethod("removePlayer", Player.class);
                bossBarRemoveAll = bossBarClass.getMethod("removeAll");
                bossBarAddPlayer = bossBarClass.getMethod("addPlayer", Player.class);
            } catch (NoSuchMethodException ignored) {}
        }
    }

    public DelayManager(XSetSpawn plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Event type for particle selection */
    public enum TeleportEvent { SPAWN, BACK }

    /**
     * Original signature kept for backward compatibility — defaults to SPAWN event.
     */
    public void scheduleTeleport(Player player, Location location, int seconds, String successMessage) {
        scheduleTeleport(player, location, seconds, successMessage, TeleportEvent.SPAWN);
    }

    public void scheduleTeleport(Player player, Location location, int seconds, String successMessage, TeleportEvent eventType) {
        cancelTeleport(player);
        DebugLogger.debug("DelayManager", "Scheduling teleport for " + player.getName() + ": " + seconds + "s delay, event=" + eventType);

        ManagerConfig config = plugin.getManagerConfig();
        player.sendMessage(plugin.getLanguageManager().getMessage("teleporting-in", seconds));

        if (config.titlesEnabled) {
            String title = plugin.getLanguageManager().getMessageUnprefixed("title-teleporting");
            String subtitle = plugin.getLanguageManager().getMessageUnprefixed("subtitle-teleporting", seconds);
            VisualUtil.sendTitle(player, title, subtitle, config.titleFadeIn, config.titleStay, config.titleFadeOut);
        }

        final Object bossBar = createBossBar(player, seconds);

        boolean useHolo = config.hologramEnabled;
        if (useHolo) {
            String holoText = config.hologramText;
            double offset = config.hologramHeightOffset;
            HologramUtil.createHologram(player, player.getLocation(), holoText.replace("{0}", String.valueOf(seconds)), offset);
        }

        SchedulerUtil.TaskWrapper particleTask = null;
        if (config.particlesEnabled) {
            String countdownType = config.particleCountdownType;
            int countdownAmount = config.particleCountdownAmount;
            particleTask = SchedulerUtil.runEntityTimer(plugin, player, () -> {
                if (player.isOnline()) {
                    ParticleUtil.spawnSpiral(player, countdownType, countdownAmount);
                }
            }, 0L, 5L);
        }

        final SchedulerUtil.TaskWrapper finalParticleTask = particleTask;

        SchedulerUtil.TaskWrapper countdownTask = SchedulerUtil.runEntityTimer(plugin, player, new Runnable() {
            int timeLeft = seconds;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancelTeleport(player);
                    return;
                }

                if (timeLeft > 0) {
                    // Play Tick Sound (1.9+ name, gracefully degrades on 1.8.8)
                    if (config.countdownSoundsEnabled) {
                        try {
                            player.playSound(player.getLocation(),
                                    org.bukkit.Sound.valueOf("BLOCK_NOTE_BLOCK_HAT"), 1.0f, 1.0f);
                        } catch (Throwable ignored) {
                            // Sound name doesn't exist on this version
                        }
                    }

                    if (config.actionbarEnabled) {
                        String actionMsg = plugin.getLanguageManager().getMessageUnprefixed("actionbar-teleporting", timeLeft);
                        VisualUtil.sendActionBar(player, actionMsg);
                    }
                    if (config.titlesEnabled) {
                        String title = plugin.getLanguageManager().getMessageUnprefixed("title-teleporting");
                        String subtitle = plugin.getLanguageManager().getMessageUnprefixed("subtitle-teleporting", timeLeft);
                        VisualUtil.sendTitle(player, title, subtitle, 0, 30, 0);
                    }
                    if (bossBar != null) {
                        try {
                            bossBarSetProgress.invoke(bossBar, (double) timeLeft / seconds);
                            bossBarSetTitle.invoke(bossBar, plugin.getLanguageManager().getMessageUnprefixed("actionbar-teleporting", timeLeft));
                        } catch (Throwable ignored) {}
                    }
                    if (useHolo) {
                        HologramUtil.updateHologram(player, config.hologramText.replace("{0}", String.valueOf(timeLeft)));
                    }
                    timeLeft--;
                } else {
                    cancelTeleport(player);

                    if (player.isOnline()) {
                        // Play Final Sound (1.9+ name, gracefully degrades on 1.8.8)
                        if (config.countdownSoundsEnabled) {
                            try {
                                player.playSound(player.getLocation(),
                                        org.bukkit.Sound.valueOf("BLOCK_NOTE_BLOCK_PLING"), 1.0f, 2.0f);
                            } catch (Throwable ignored) {
                                // Sound name doesn't exist on this version
                            }
                        }

                        // Economy
                        if (config.economyEnabled && !com.fabian.xsetspawn.managers.Permission.BYPASS_ECONOMY.has(player)) {
                            double cost = config.economyCost;
                            plugin.getVaultHook().withdrawPlayer(player, cost);
                            if (cost > 0) {
                                player.sendMessage(plugin.getLanguageManager().getMessage("teleport-cost", plugin.getVaultHook().format(cost)));
                            }
                        }

                        SchedulerUtil.teleport(plugin, player, location, () -> {
                            if (!player.isOnline()) return;

                            plugin.getSpawnManager().playSpawnSound(player, location);
                            player.sendMessage(successMessage);

                            if (config.protectionEnabled) {
                                int time = config.protectionTime;
                                player.setMetadata("xsetspawn_protection", new org.bukkit.metadata.FixedMetadataValue(plugin, System.currentTimeMillis() + (time * 1000L)));
                            }

                            if (config.titlesEnabled) {
                                String title = plugin.getLanguageManager().getMessageUnprefixed("title-teleported");
                                String subtitle = plugin.getLanguageManager().getMessageUnprefixed("subtitle-teleported");
                                VisualUtil.sendTitle(player, title, subtitle, 10, 40, 10);
                            }

                            if (config.particlesEnabled) {
                                String arriveType, arriveAmountStr;
                                switch (eventType) {
                                    case BACK:
                                        arriveType = config.particleBackType;
                                        arriveAmountStr = String.valueOf(config.particleBackAmount);
                                        break;
                                    default:
                                        arriveType = config.particleSpawnType;
                                        arriveAmountStr = String.valueOf(config.particleSpawnAmount);
                                        break;
                                }
                                ParticleUtil.spawnParticle(player.getLocation().add(0, 1, 0),
                                        arriveType, Integer.parseInt(arriveAmountStr));
                            }

                            // Apply cooldown after successful delayed teleport
                            if (config.cooldownEnabled && !com.fabian.xsetspawn.managers.Permission.BYPASS_COOLDOWN.has(player)) {
                                plugin.getCooldownManager().setCooldown(player, config.cooldownTime);
                            }
                        });
                    }
                }
            }
        }, 0L, 20L);

        pendingTeleports.put(player.getUniqueId(), new TeleportSession(countdownTask, finalParticleTask, bossBar, player.getUniqueId()));
    }

    /**
     * Creates a BossBar via reflection. Returns null on 1.8.8 or when disabled.
     */
    private Object createBossBar(Player player, int seconds) {
        ManagerConfig config = plugin.getManagerConfig();
        if (!config.bossbarEnabled || !bossBarAvailable) return null;
        try {
            ensureBossBarMethods();
            Object color = Enum.valueOf((Class<Enum>) barColorClass, config.bossbarColor);
            Object style = Enum.valueOf((Class<Enum>) barStyleClass, config.bossbarStyle);
            Object bar = Bukkit.class.getMethod("createBossBar", String.class, barColorClass, barStyleClass)
                    .invoke(Bukkit, plugin.getLanguageManager().getMessageUnprefixed("actionbar-teleporting", seconds), color, style);
            bossBarAddPlayer.invoke(bar, player);
            return bar;
        } catch (Throwable t) {
            return null;
        }
    }

    public void cancelTeleport(Player player) {
        TeleportSession session = pendingTeleports.remove(player.getUniqueId());
        if (session != null) {
            DebugLogger.debug("DelayManager", "Cancelled pending teleport for " + player.getName());
            session.cancel();
        }
    }

    public boolean isPending(Player player) {
        return pendingTeleports.containsKey(player.getUniqueId());
    }

    /**
     * Cancel all pending teleports and clean up their resources (bossbars, holograms, particles).
     * Should be called on reload to prevent stale visual effects.
     */
    public void cancelAllPendingTeleports() {
        for (Map.Entry<UUID, TeleportSession> entry : pendingTeleports.entrySet()) {
            entry.getValue().cancel();
            DebugLogger.debug("DelayManager", "Cancelled pending teleport for UUID " + entry.getKey() + " during reload");
        }
        pendingTeleports.clear();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getManagerConfig().delayCancelOnMove || pendingTeleports.isEmpty()) {
            return;
        }

        Player player = event.getPlayer();
        if (isPending(player)) {
            if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                    event.getFrom().getBlockY() != event.getTo().getBlockY() ||
                    event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {

                cancelTeleport(player);
                DebugLogger.debug("DelayManager", "Teleport cancelled by movement for " + player.getName());
                // Clear the back-location that was saved when the delay started,
                // since the teleport never actually happened.
                plugin.getBackManager().clearLocation(player);
                player.sendMessage(plugin.getLanguageManager().getMessage("teleport-canceled"));
            }
        }
    }



    private class TeleportSession {
        private final SchedulerUtil.TaskWrapper teleportTask;
        private final SchedulerUtil.TaskWrapper particleTask;
        private final Object bossBar; // Held as Object — accessed via reflection
        private final UUID playerUuid;

        public TeleportSession(SchedulerUtil.TaskWrapper teleportTask, SchedulerUtil.TaskWrapper particleTask, Object bossBar, UUID playerUuid) {
            this.teleportTask = teleportTask;
            this.particleTask = particleTask;
            this.bossBar = bossBar;
            this.playerUuid = playerUuid;
        }

        public void cancel() {
            if (teleportTask != null)
                teleportTask.cancel();
            if (particleTask != null)
                particleTask.cancel();

            Player player = Bukkit.getPlayer(playerUuid);
            if (player != null && player.isOnline()) {
                SchedulerUtil.runEntity(plugin, player, () -> {
                    removeBossBar(player);
                    HologramUtil.removeHologram(player);
                });
            } else {
                removeBossBar(null);
            }
        }

        private void removeBossBar(Player player) {
            if (bossBar != null) {
                try {
                    if (player != null) bossBarRemovePlayer.invoke(bossBar, player);
                    bossBarRemoveAll.invoke(bossBar);
                } catch (Throwable ignored) {}
            }
        }
    }
}