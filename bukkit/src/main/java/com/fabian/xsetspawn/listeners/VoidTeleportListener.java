package com.fabian.xsetspawn.listeners;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.managers.ManagerConfig;
import com.fabian.xsetspawn.managers.SpawnManager;
import com.fabian.xsetspawn.utils.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class VoidTeleportListener implements Listener {

    private final XSetSpawn plugin;
    private final ManagerConfig config;
    private final SpawnManager spawnManager;

    public VoidTeleportListener(XSetSpawn plugin) {
        this.plugin = plugin;
        this.config = plugin.getManagerConfig();
        this.spawnManager = plugin.getSpawnManager();
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!config.voidTeleportEnabled) return;
        // Optimization: skip head-only movements (no Y block change)
        if (event.getTo() == null || event.getFrom().getBlockY() == event.getTo().getBlockY()) return;
        
        Player player = event.getPlayer();
        int voidY = config.voidTeleportHeight;
        
        if (event.getTo().getY() < voidY) {
            World world = player.getWorld();
            Location spawn = spawnManager.getSpawn(world);
            if (spawn != null) {
                player.setFallDistance(0);
                SchedulerUtil.teleport(player, spawn);
                plugin.getSpawnManager().playSpawnSound(player, spawn);
                applyProtection(player);
            }
        }
    }

    private void applyProtection(Player player) {
        if (config.protectionEnabled) {
            int time = config.protectionTime;
            player.setMetadata("xsetspawn_protection", new org.bukkit.metadata.FixedMetadataValue(plugin, System.currentTimeMillis() + (time * 1000L)));
        }
    }
}
