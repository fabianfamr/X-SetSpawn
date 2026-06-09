package com.fabian.xsetspawn.bungee.commands;

import com.fabian.xsetspawn.bungee.XSetSpawnBungee;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import com.google.common.io.ByteStreams;
import com.google.common.io.ByteArrayDataOutput;

import java.util.UUID;

/**
 * Command handler for /hub, /lobby, /spawn on BungeeCord proxy.
 * Sends the player to the configured target server.
 */
public class HubCommand extends Command {

    private final XSetSpawnBungee plugin;
    private final String targetServerOverride;

    public HubCommand(XSetSpawnBungee plugin, String name, String targetServerOverride) {
        super(name, "xsetspawn.lobby");
        this.plugin = plugin;
        this.targetServerOverride = targetServerOverride;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!hasPermission(sender)) return;
        if (!(sender instanceof ProxiedPlayer)) {
            sender.sendMessage(new TextComponent(plugin.getPrefix() + plugin.getMsgPlayerOnly()));
            return;
        }

        ProxiedPlayer player = (ProxiedPlayer) sender;
        UUID uuid = player.getUniqueId();
        
        // Find the target server (use override if set, otherwise default)
        String serverName = (targetServerOverride != null && !targetServerOverride.isEmpty())
                ? targetServerOverride : plugin.getTargetServer();
        ServerInfo serverInfo = ProxyServer.getInstance().getServerInfo(serverName);

        if (serverInfo == null) {
            String msg = plugin.getMsgServerNotFound().replace("{server}", serverName);
            player.sendMessage(new TextComponent(plugin.getPrefix() + msg));
            plugin.getLogger().warning("Server '" + serverName + "' not found in BungeeCord configuration!");
            return;
        }

        boolean alreadyOnServer = player.getServer() != null 
                && player.getServer().getInfo().getName().equalsIgnoreCase(serverName);

        // Check cooldown (ONLY if NOT already on the target server)
        int cooldownTime = plugin.getCooldownSeconds();
        if (cooldownTime > 0 && !alreadyOnServer) {
            Long lastUse = plugin.getCooldowns().get(uuid);
            if (lastUse != null) {
                long elapsed = (System.currentTimeMillis() - lastUse) / 1000;
                if (elapsed < cooldownTime) {
                    long remaining = cooldownTime - elapsed;
                    String msg = plugin.getMsgCooldown().replace("{time}", String.valueOf(remaining));
                    player.sendMessage(new TextComponent(plugin.getPrefix() + msg));
                    return;
                }
            }
        }

        if (alreadyOnServer) {
            // Show message and snap to coordinates (SILENT = false because it's a manual command)
            String msg = plugin.getMsgAlreadyConnected().replace("{server}", serverName);
            player.sendMessage(new TextComponent(plugin.getPrefix() + msg));

            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            
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
            
            player.getServer().getInfo().sendData(XSetSpawnBungee.CHANNEL, out.toByteArray());
            
            // NO connecting message sent when already on the server to avoid spam
            return;
        }

        // --- Jumping to a different server ---

        // Send connecting message if enabled
        if (plugin.isShowConnectingMessage()) {
            String connectMsg = plugin.getMsgConnecting().replace("{server}", serverName);
            player.sendMessage(new TextComponent(plugin.getPrefix() + connectMsg));
        }

        // Set cooldown
        if (cooldownTime > 0) {
            plugin.getCooldowns().put(uuid, System.currentTimeMillis());
        }

        // Connect the player to the target server
        player.connect(serverInfo);
    }

    @Override
    public boolean hasPermission(CommandSender sender) {
        if (!(sender instanceof ProxiedPlayer)) return true;
        return ((ProxiedPlayer) sender).hasPermission("xsetspawn.lobby");
    }
}
