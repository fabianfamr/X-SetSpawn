package com.fabian.xsetspawn.utils;

import com.fabian.xsetspawn.XSetSpawn;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.TabExecutor;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Utility to register commands dynamically into Bukkit's CommandMap.
 * This allows keeping only the main command in plugin.yml while
 * sub-commands are registered at runtime.
 */
public class CommandRegistrar {

    private final XSetSpawn plugin;

    public CommandRegistrar(XSetSpawn plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers a command dynamically with optional aliases using a TabExecutor.
     */
    public void register(String name, TabExecutor executor, String... aliases) {
        registerDynamic(name, executor, executor, Arrays.asList(aliases));
    }

    /**
     * Registers a command dynamically with optional aliases using a CommandExecutor.
     */
    public void register(String name, CommandExecutor executor, TabCompleter tabCompleter, String... aliases) {
        registerDynamic(name, executor, tabCompleter, Arrays.asList(aliases));
    }

    private void registerDynamic(String name, CommandExecutor executor, TabCompleter tabCompleter,
                                  List<String> aliases) {
        CommandMap commandMap = getCommandMap();
        if (commandMap == null) {
            plugin.getLogger().warning("Could not access CommandMap, skipping /" + name);
            return;
        }

        DynamicPluginCommand cmd = new DynamicPluginCommand(
                name,
                "X-SetSpawn command",
                "/" + name,
                executor,
                tabCompleter,
                aliases
        );

        // Remove existing registration if any
        Command existing = commandMap.getCommand(name.toLowerCase());
        if (existing != null) {
            existing.unregister(commandMap);
            commandMap.getKnownCommands().remove(name.toLowerCase());
        }

        commandMap.register(plugin.getName().toLowerCase(), cmd);
        DebugLogger.debug("CommandRegistrar", "Registered /" + name);
    }

    private CommandMap getCommandMap() {
        try {
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            return (CommandMap) field.get(Bukkit.getServer());
        } catch (Exception e) {
            plugin.getLogger().warning("Could not access CommandMap: " + e.getMessage());
            return null;
        }
    }

    /**
     * A lightweight dynamic command that wraps a CommandExecutor and optional TabCompleter.
     */
    private static class DynamicPluginCommand extends Command {

        private final CommandExecutor executor;
        private final TabCompleter tabCompleter;

        protected DynamicPluginCommand(String name, String description, String usage,
                                        CommandExecutor executor, TabCompleter tabCompleter,
                                        List<String> aliases) {
            super(name, description, usage, aliases);
            this.executor = executor;
            this.tabCompleter = tabCompleter;
            this.setPermissionMessage("\u00a7cYou don't have permission.");
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            return executor.onCommand(sender, this, label, args);
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            if (tabCompleter != null) {
                List<String> completions = tabCompleter.onTabComplete(sender, this, alias, args);
                if (completions != null) return completions;
            }
            return Collections.emptyList();
        }
    }
}