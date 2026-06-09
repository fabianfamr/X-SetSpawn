package com.fabian.xsetspawn.bungee.commands;

import com.fabian.xsetspawn.bungee.XSetSpawnBungee;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

/**
 * Command handler for /setlobby on BungeeCord proxy.
 * Sets the current server as the global lobby server.
 */
public class SetLobbyCommand extends Command {

    private final XSetSpawnBungee plugin;

    public SetLobbyCommand(XSetSpawnBungee plugin) {
        super("setlobby", null, "sl");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("xsetspawn.admin")) {
            sender.sendMessage(new TextComponent(plugin.getPrefix() + plugin.getMsgNoPermission()));
            return;
        }

        if (!(sender instanceof ProxiedPlayer)) {
            sender.sendMessage(new TextComponent(plugin.getPrefix() + plugin.getMsgPlayerOnly()));
            return;
        }

        ProxiedPlayer player = (ProxiedPlayer) sender;

        if (player.getServer() == null) {
            player.sendMessage(new TextComponent(plugin.getPrefix() + plugin.getMsgErrorNoServer()));
            return;
        }

        String serverName = player.getServer().getInfo().getName();
        
        // 1. Update the target server name
        plugin.updateTargetServer(serverName);

        // 2. Request coordinates from the backend server
        com.google.common.io.ByteArrayDataOutput out = com.google.common.io.ByteStreams.newDataOutput();
        out.writeUTF("RequestLocation");
        player.getServer().getInfo().sendData(XSetSpawnBungee.CHANNEL, out.toByteArray());

        String msg = plugin.getMsgLobbySet().replace("{server}", serverName);
        player.sendMessage(new net.md_5.bungee.api.chat.TextComponent(plugin.getPrefix() + msg));
        
        plugin.getLogger().info("Global lobby server updated to '" + serverName + "'. Requesting coordinates from backend...");
    }
}
