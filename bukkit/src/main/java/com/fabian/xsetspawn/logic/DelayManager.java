package com.fabian.xsetspawn.logic;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.managers.ManagerConfig;
import com.fabian.xsetspawn.utils.HologramUtil;
import com.fabian.xsetspawn.utils.VisualUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import com.fabian.xsetspawn.utils.SchedulerUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DelayManager implements Listener {

    private final XSetSpawn plugin;
    private final Map<UUID, TeleportSession> pendingTeleports = new HashMap<>();

    public DelayManager(XSetSpawn plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void scheduleTeleport(Player player, Location location, int seconds, String successMessage) {
        cancelTeleport(player);

        ManagerConfig config = plugin.getManagerConfig();
        player.sendMessage(plugin.getLanguageManager().getMessage("teleporting-in", seconds));

        if (config.titlesEnabled) {
            String title = plugin.getLanguageManager().getMessageUnprefixed("title-teleporting");
            String subtitle = plugin.getLanguageManager().getMessageUnprefixed("subtitle-teleporting", seconds);
            VisualUtil.sendTitle(player, title, subtitle, config.titleFadeIn, config.titleStay, config.titleFadeOut);
        }

        final BossBar bossBar = createBossBar(player, seconds);

        boolean useHolo = config.hologramEnabled;
        if (useHolo) {
            String holoText = config.hologramText;
            double offset = config.hologramHeightOffset;
            HologramUtil.createHologram(player, player.getLocation(), holoText.replace("{0}", String.valueOf(seconds)), offset);
        }

        SchedulerUtil.TaskWrapper particleTask = null;
        if (config.particlesEnabled) {
            particleTask = SchedulerUtil.runRegionTimer(plugin, player.getLocation(), () -> {
                if (player.isOnline()) {
                    com.fabian.xsetspawn.utils.ParticleUtil.spawnSpiral(player,
                            config.particleType,
                            config.particleAmount);
                }
            }, 0L, 5L);
        }

        final SchedulerUtil.TaskWrapper finalParticleTask = particleTask;
        
        SchedulerUtil.TaskWrapper countdownTask = SchedulerUtil.runRegionTimer(plugin, player.getLocation(), new Runnable() {
            int timeLeft = seconds;
            
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancelTeleport(player);
                    return;
                }
                
                if (timeLeft > 0) {
                    // Play Tick Sound
                    if (config.countdownSoundsEnabled) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
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
                        bossBar.setProgress((double) timeLeft / seconds);
                        bossBar.setTitle(plugin.getLanguageManager().getMessageUnprefixed("actionbar-teleporting", timeLeft).replace("&", "§"));
                    }
                    if (useHolo) {
                        HologramUtil.updateHologram(player, config.hologramText.replace("{0}", String.valueOf(timeLeft)));
                    }
                    timeLeft--;
                } else {
                    cancelTeleport(player);
                    
                    if (player.isOnline()) {
                        // Play Final Sound
                        if (config.countdownSoundsEnabled) {
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
                        }

                        // Economy
                        if (config.economyEnabled) {
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
                                com.fabian.xsetspawn.utils.ParticleUtil.spawnParticle(player.getLocation().add(0, 1, 0),
                                        config.particleType, 20);
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

        pendingTeleports.put(player.getUniqueId(), new TeleportSession(countdownTask, finalParticleTask, bossBar, player));
    }

    private BossBar createBossBar(Player player, int seconds) {
        ManagerConfig config = plugin.getManagerConfig();
        if (!config.bossbarEnabled) return null;
        try {
            BarColor color = BarColor.valueOf(config.bossbarColor);
            BarStyle style = BarStyle.valueOf(config.bossbarStyle);
            BossBar bar = Bukkit.createBossBar(plugin.getLanguageManager().getMessageUnprefixed("actionbar-teleporting", seconds).replace("&", "§"), color, style);
            bar.addPlayer(player);
            return bar;
        } catch (Throwable t) {
            return null;
        }
    }

    public void cancelTeleport(Player player) {
        TeleportSession session = pendingTeleports.remove(player.getUniqueId());
        if (session != null) {
            session.cancel();
        }
    }

    public boolean isPending(Player player) {
        return pendingTeleports.containsKey(player.getUniqueId());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getManagerConfig().delayCancelOnMove) {
            return;
        }

        Player player = event.getPlayer();
        if (isPending(player)) {
            if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                    event.getFrom().getBlockY() != event.getTo().getBlockY() ||
                    event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {

                cancelTeleport(player);
                player.sendMessage(plugin.getLanguageManager().getMessage("teleport-canceled"));
            }
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        cancelTeleport(event.getPlayer());
    }

    private static class TeleportSession {
        private final SchedulerUtil.TaskWrapper teleportTask;
        private final SchedulerUtil.TaskWrapper particleTask;
        private final BossBar bossBar;
        private final Player player;

        public TeleportSession(SchedulerUtil.TaskWrapper teleportTask, SchedulerUtil.TaskWrapper particleTask, BossBar bossBar, Player player) {
            this.teleportTask = teleportTask;
            this.particleTask = particleTask;
            this.bossBar = bossBar;
            this.player = player;
        }

        public void cancel() {
            if (teleportTask != null)
                teleportTask.cancel();
            if (particleTask != null)
                particleTask.cancel();
            if (bossBar != null) {
                try { bossBar.removePlayer(player); bossBar.removeAll(); } catch (Throwable ignored) {}
            }
            HologramUtil.removeHologram(player);
        }
    }
}

