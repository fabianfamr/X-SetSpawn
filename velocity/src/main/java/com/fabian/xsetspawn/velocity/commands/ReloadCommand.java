package com.fabian.xsetspawn.velocity.commands;

import com.fabian.xsetspawn.velocity.XSetSpawnVelocity;
import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Command handler for /xssproxy on Velocity proxy.
 * Provides plugin management subcommands.
 */
public class ReloadCommand implements SimpleCommand {

    private final XSetSpawnVelocity plugin;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public ReloadCommand(XSetSpawnVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!invocation.source().hasPermission("xsetspawn.admin")) {
                invocation.source().sendMessage(LEGACY.deserialize(plugin.getPrefix() + plugin.getMsgNoPermission()));
                return;
            }
            plugin.reload();
            invocation.source().sendMessage(LEGACY.deserialize(plugin.getPrefix() + plugin.getMsgReloadSuccess()));
        } else {
            invocation.source().sendMessage(LEGACY.deserialize(plugin.getPrefix() + plugin.getMsgReloadHelp()));
        }
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length == 0) {
            return CompletableFuture.completedFuture(List.of("reload"));
        }
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            return invocation.source().hasPermission("xsetspawn.admin");
        }
        return true;
    }
}
