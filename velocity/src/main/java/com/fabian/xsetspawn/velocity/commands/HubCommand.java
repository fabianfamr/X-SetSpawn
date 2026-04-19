package com.fabian.xsetspawn.velocity.commands;

import com.fabian.xsetspawn.velocity.XSetSpawnVelocity;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Command handler for /hub, /lobby, /spawn on Velocity proxy.
 * Sends the player to the configured target server.
 */
public class HubCommand implements SimpleCommand {

    private final XSetSpawnVelocity plugin;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public HubCommand(XSetSpawnVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(LEGACY.deserialize(
                    plugin.getPrefix() + plugin.getMsgPlayerOnly()));
            return;
        }

        // Check server info
        String serverName = plugin.getTargetServer();
        Optional<RegisteredServer> targetServer = plugin.getServer().getServer(serverName);

        if (targetServer.isEmpty()) {
            String msg = plugin.getMsgServerNotFound().replace("{server}", serverName);
            player.sendMessage(LEGACY.deserialize(plugin.getPrefix() + msg));
            plugin.getLogger().warn("Server '{}' not found in Velocity configuration!", serverName);
            return;
        }

        boolean alreadyOnServer = player.getCurrentServer().isPresent() 
                && player.getCurrentServer().get().getServerInfo().getName().equalsIgnoreCase(serverName);

        // Check cooldown (ONLY if NOT already on the target server)
        UUID uuid = player.getUniqueId();
        int cooldownTime = plugin.getCooldownSeconds();

        if (cooldownTime > 0 && !alreadyOnServer) {
            Long lastUse = plugin.getCooldowns().get(uuid);
            if (lastUse != null) {
                long elapsed = (System.currentTimeMillis() - lastUse) / 1000;
                if (elapsed < cooldownTime) {
                    long remaining = cooldownTime - elapsed;
                    String msg = plugin.getMsgCooldown().replace("{time}", String.valueOf(remaining));
                    player.sendMessage(LEGACY.deserialize(plugin.getPrefix() + msg));
                    return;
                }
            }
        }

        if (alreadyOnServer) {
            // Already there: Just snap to coordinates (SILENT = false because it's a manual command)
            com.google.common.io.ByteArrayDataOutput out = com.google.common.io.ByteStreams.newDataOutput();
            
            if (plugin.isLobbyCoordsSet()) {
                out.writeUTF("GlobalLobbyTeleport");
                out.writeUTF(plugin.getLobbyWorld());
                out.writeDouble(plugin.getLobbyX());
                out.writeDouble(plugin.getLobbyY());
                out.writeDouble(plugin.getLobbyZ());
                out.writeFloat(plugin.getLobbyYaw());
                out.writeFloat(plugin.getLobbyPitch());
                out.writeBoolean(false); // Silent = false (manual command)
            } else {
                out.writeUTF("TeleportLobby");
                out.writeUTF(uuid.toString());
                out.writeBoolean(false); // Silent = false (manual command)
            }
            
            player.getCurrentServer().get().sendPluginMessage(XSetSpawnVelocity.CHANNEL, out.toByteArray());
            
            // NO connecting message sent when already on the server to avoid spam
            return;
        }

        // --- Jumping to a different server ---

        // Send connecting message if enabled
        if (plugin.isShowConnectingMessage()) {
            String connectMsg = plugin.getMsgConnecting().replace("{server}", serverName);
            player.sendMessage(LEGACY.deserialize(plugin.getPrefix() + connectMsg));
        }

        // Set cooldown
        if (cooldownTime > 0) {
            plugin.getCooldowns().put(uuid, System.currentTimeMillis());
        }

        // Connect the player to the target server
        player.createConnectionRequest(targetServer.get()).connect()
                .thenAccept(result -> {
                    if (!result.isSuccessful()) {
                        String failMsg = plugin.getMsgConnectionFailed()
                                .replace("{server}", serverName);
                        player.sendMessage(LEGACY.deserialize(
                                plugin.getPrefix() + failMsg));
                        // Remove cooldown on failure
                        plugin.getCooldowns().remove(uuid);
                    }
                });
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("xsetspawn.lobby");
    }
}
