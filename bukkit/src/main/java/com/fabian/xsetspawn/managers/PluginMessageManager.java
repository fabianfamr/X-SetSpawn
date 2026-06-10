package com.fabian.xsetspawn.managers;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.utils.DebugLogger;
import com.fabian.xsetspawn.utils.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import java.util.UUID;

public class PluginMessageManager implements PluginMessageListener {
    private final XSetSpawn plugin;
    public static final String CHANNEL = "xsetspawn:main";

    public PluginMessageManager(XSetSpawn plugin) {
        this.plugin = plugin;
        DebugLogger.debug("PluginMessage", "Registering plugin message channels: " + CHANNEL);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(CHANNEL) || message == null || message.length < 2) return;

        DebugLogger.debug("PluginMessage", "Received plugin message on channel: " + channel);
        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(message);
            String subChannel = in.readUTF();

            if (subChannel.equals("TeleportLobby")) {
                DebugLogger.debug("PluginMessage", "SubChannel TeleportLobby for player: " + player.getName());
                String uuidString = in.readUTF();
                UUID uuid = UUID.fromString(uuidString);
                Player target = Bukkit.getPlayer(uuid);

                if (target != null && target.isOnline()) {
                    // Try to get default spawn, or named 'lobby' / 'hub' if per-world is strictly isolated
                    Location spawnLocation = plugin.getSpawnManager().getSpawn(target.getWorld());
                    
                    if (spawnLocation == null && plugin.getManagerConfig().namedSpawns) {
                         // fallback to typical named spawns if default world spawn isn't set
                         if (plugin.getSpawnManager().isNamedSpawnSet("lobby")) {
                             spawnLocation = plugin.getSpawnManager().getNamedSpawn("lobby");
                         } else if (plugin.getSpawnManager().isNamedSpawnSet("hub")) {
                             spawnLocation = plugin.getSpawnManager().getNamedSpawn("hub");
                         }
                    }

                    if (spawnLocation != null) {
                        final Location finalLoc = spawnLocation;
                        boolean silent = false;
                        try { silent = in.readBoolean(); } catch (IllegalStateException ignored) {} catch (Exception ignored) {}
                        
                        boolean finalSilent = silent;
                        SchedulerUtil.runEntity(plugin, target, () -> {
                            plugin.getBackManager().saveLocation(target);
                            SchedulerUtil.teleport(plugin, target, finalLoc, () -> {
                                plugin.getSpawnManager().playSpawnSound(target, finalLoc);
                                if (!finalSilent) {
                                    target.sendMessage(plugin.getLanguageManager().getMessage("spawn-teleport"));
                                }
                            });
                        });
                    }
                }
            } else if (subChannel.equals("RequestLocation")) {
                DebugLogger.debug("PluginMessage", "SubChannel RequestLocation from proxy for: " + player.getName());
                // Proxy is asking for player coordinates to set the global lobby
                Location loc = player.getLocation();
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("LocationResponse");
                out.writeUTF(loc.getWorld().getName());
                out.writeDouble(loc.getX());
                out.writeDouble(loc.getY());
                out.writeDouble(loc.getZ());
                out.writeFloat(loc.getYaw());
                out.writeFloat(loc.getPitch());
                player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());

            } else if (subChannel.equals("GlobalLobbyTeleport")) {
                // Incoming forced teleport from Proxy
                DebugLogger.debug("PluginMessage", "SubChannel GlobalLobbyTeleport for " + player.getName() + " to " + worldName);
                String worldName = in.readUTF();
                double x = in.readDouble();
                double y = in.readDouble();
                double z = in.readDouble();
                float yaw = in.readFloat();
                float pitch = in.readFloat();
                boolean silent = false;
                try { silent = in.readBoolean(); } catch (IllegalStateException ignored) {} catch (Exception ignored) {} // Support old versions

                org.bukkit.World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    Location loc = new Location(world, x, y, z, yaw, pitch);
                    
                    // Teleport immediately (SchedulerUtil.runEntity ensures Folia compatibility)
                    boolean finalSilent = silent;
                    SchedulerUtil.runEntity(plugin, player, () -> {
                        if (player.isOnline()) {
                            DebugLogger.debug("PluginMessage", "Executing GlobalLobbyTeleport for " + player.getName() + " to " + worldName + " (" + (int)x + ", " + (int)y + ", " + (int)z + ") [Silent: " + finalSilent + "]");

                            // Bypass double waits: Cancel any pending Bukkit-side teleport delays
                            plugin.getDelayManager().cancelTeleport(player);
                            
                            plugin.getBackManager().saveLocation(player);
                            SchedulerUtil.teleport(plugin, player, loc, () -> {
                                plugin.getSpawnManager().playSpawnSound(player, loc);
                                if (!finalSilent) {
                                    player.sendMessage(plugin.getLanguageManager().getMessage("spawn-teleport"));
                                }
                            });
                        }
                    });
                } else {
                    DebugLogger.debug("PluginMessage", "GlobalLobbyTeleport failed: world '" + worldName + "' not found");
                    plugin.logError("GlobalLobbyTeleport failed: World '" + worldName + "' not found on this server!");
                }
            }
        } catch (Exception e) {
            DebugLogger.debug("PluginMessage", "Error decoding plugin message", e);
            String errorMsg = e.getMessage() != null ? e.getMessage() : "No message (NullPointerException or EOF)";
            plugin.logError("Error decoding plugin message on channel '" + channel + "': " + e.getClass().getSimpleName() + " - " + errorMsg);
            
            // Log stack trace only if it's not a common EOF
            if (!(e instanceof java.io.EOFException)) {
                e.printStackTrace();
            }
        }
    }

    public void sendLobbySync(Player sender) {
        if (sender == null) return;
        DebugLogger.debug("PluginMessage", "Sending lobby sync for " + sender.getName());
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("SetLobbyServer");
            sender.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
        } catch (Exception e) {
            plugin.logError("Failed to send SetLobbyServer message to proxy: " + e.getMessage());
        }
    }

    public void sendPlayerReady(Player player) {
        if (player == null) return;
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("PlayerReady");
            player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
        } catch (Exception e) {
            // Silently fail as the channel might not be registered yet on very early join
        }
    }
}
