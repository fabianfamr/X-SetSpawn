package com.fabian.xsetspawn.bungee.commands;

import com.fabian.xsetspawn.bungee.XSetSpawnBungee;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;

/**
 * Command handler for /xssproxy on BungeeCord proxy.
 * Provides plugin management subcommands.
 */
public class ReloadCommand extends Command {

    private final XSetSpawnBungee plugin;

    public ReloadCommand(XSetSpawnBungee plugin) {
        super("xssproxy");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("xsetspawn.admin")) {
                sender.sendMessage(new TextComponent(plugin.getPrefix() + plugin.getMsgNoPermission()));
                return;
            }
            plugin.reload();
            sender.sendMessage(new TextComponent(plugin.getPrefix() + plugin.getMsgReloadSuccess()));
        } else {
            sender.sendMessage(new TextComponent(plugin.getPrefix() + plugin.getMsgReloadHelp()));
        }
    }
}
