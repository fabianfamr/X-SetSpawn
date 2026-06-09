package com.fabian.xsetspawn.velocity.commands;

import com.fabian.xsetspawn.velocity.XSetSpawnVelocity;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Command handler for /setlobby on Velocity proxy.
 * Sets the current server as the global lobby server.
 */
public class SetLobbyCommand implements SimpleCommand {

    private final XSetSpawnVelocity plugin;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public SetLobbyCommand(XSetSpawnVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!invocation.source().hasPermission("xsetspawn.admin")) {
            invocation.source().sendMessage(LEGACY.deserialize(plugin.getPrefix() + plugin.getMsgNoPermission()));
            return;
        }

        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(LEGACY.deserialize(
                    plugin.getPrefix() + plugin.getMsgPlayerOnly()));
            return;
        }

        if (player.getCurrentServer().isEmpty()) {
            player.sendMessage(LEGACY.deserialize(
                    plugin.getPrefix() + plugin.getMsgErrorNoServer()));
            return;
        }

        String serverName = player.getCurrentServer().get().getServerInfo().getName();

        // 1. Update the target server name
        plugin.updateTargetServer(serverName);

        // 2. Request coordinates from the backend server
        com.google.common.io.ByteArrayDataOutput out = com.google.common.io.ByteStreams.newDataOutput();
        out.writeUTF("RequestLocation");
        player.getCurrentServer().get().sendPluginMessage(XSetSpawnVelocity.CHANNEL, out.toByteArray());

        String msg = plugin.getMsgLobbySet().replace("{server}", serverName);
        player.sendMessage(LEGACY.deserialize(plugin.getPrefix() + msg));

        plugin.getLogger().info("Global lobby server updated to '{}'. Requesting coordinates from backend...", serverName);
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true; // Always visible
    }
}
