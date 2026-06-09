package com.fabian.xsetspawn.listeners;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.managers.ManagerConfig;
import com.fabian.xsetspawn.managers.Permission;
import com.fabian.xsetspawn.managers.SpawnManager;
import com.fabian.xsetspawn.utils.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PlayerListener implements Listener {

    private final XSetSpawn plugin;
    private final ManagerConfig config;
    private final SpawnManager spawnManager;

    public PlayerListener(XSetSpawn plugin) {
        this.plugin = plugin;
        this.config = plugin.getManagerConfig();
        this.spawnManager = plugin.getSpawnManager();
    }

    @EventHandler
    public void onFirstJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean teleported = false;

        if (config.teleportOnFirstJoin && !player.hasPlayedBefore()) {
            if (config.firstJoinSpawnEnabled) {
                Location firstJoin = spawnManager.getFirstJoinSpawn();
                if (firstJoin != null) {
                    SchedulerUtil.teleport(player, firstJoin);
                    applyProtection(player);
                    teleported = true;
                }
            }
            if (!teleported) {
                teleported = teleportToSpawnChecked(player);
            }
        }

        if (!teleported && config.teleportOnJoinPermission) {
            SchedulerUtil.runRegionDelayed(plugin, player.getLocation(), () -> {
                if (player.isOnline() && Permission.SPAWN_ON_JOIN.has(player)) {
                    teleportToSpawnChecked(player);
                }
            }, 2L);
        }

        // Notify Proxy that the player is ready/connected (for instant teleport sync)
        plugin.getPluginMessageManager().sendPlayerReady(player);
    }

    @EventHandler
    public void onDeath(PlayerRespawnEvent event) {
        if (config.teleportOnDeath) {
            Player player = event.getPlayer();
            
            // Smart Respawn Check
            if (config.smartRespawn) {
                if (player.getBedSpawnLocation() != null) {
                    // Let the player respawn at their bed naturally
                    return;
                }
            }

            World world = player.getWorld();
            if (spawnManager.isSpawnSet(world)) {
                Location spawn = spawnManager.getSpawn(world);
                if (spawn != null) {
                    // Save back-location (death location) so /back can return here
                    plugin.getBackManager().saveLocation(player);
                    event.setRespawnLocation(spawn);
                    plugin.getSpawnManager().playSpawnSound(player, spawn);
                }
            }
        }
    }

    private boolean teleportToSpawnChecked(Player player) {
        World world = player.getWorld();
        if (spawnManager.isSpawnSet(world)) {
            Location spawn = spawnManager.getSpawn(world);
            if (spawn != null) {
                SchedulerUtil.teleport(player, spawn);
                plugin.getSpawnManager().playSpawnSound(player, spawn);
                applyProtection(player);
                return true;
            }
        }
        return false;
    }

    private void applyProtection(Player player) {
        if (config.protectionEnabled) {
            int time = config.protectionTime;
            player.setMetadata("xsetspawn_protection", new org.bukkit.metadata.FixedMetadataValue(plugin, System.currentTimeMillis() + (time * 1000L)));
        }
    }

    @EventHandler
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!config.protectionEnabled) return;
        if (!(event.getEntity() instanceof Player)) return;
        
        Player player = (Player) event.getEntity();
        if (player.hasMetadata("xsetspawn_protection")) {
            long expireTime = player.getMetadata("xsetspawn_protection").get(0).asLong();
            if (System.currentTimeMillis() < expireTime) {
                event.setCancelled(true);
            } else {
                player.removeMetadata("xsetspawn_protection", plugin);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        plugin.getDelayManager().cancelTeleport(event.getPlayer());
        plugin.getBackManager().clearLocation(uuid);
        plugin.getCooldownManager().removeCooldown(uuid);
    }
}
