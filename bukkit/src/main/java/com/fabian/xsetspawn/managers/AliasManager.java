package com.fabian.xsetspawn.managers;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.utils.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * AliasManager - Registers custom command aliases from config.yml at runtime.
 *
 * Admins can define their own aliases for /spawn, /setspawn, and /xsetspawn:
 *
 *   command-aliases:
 *     spawn:    [hub, lobby]
 *     setspawn: []
 *     xsetspawn: [xss2]
 *
 * These are registered into Bukkit's CommandMap at server startup.
 */
public class AliasManager {

    private final XSetSpawn plugin;

    public AliasManager(XSetSpawn plugin) {
        this.plugin = plugin;
    }

    public void registerAliases() {
        DebugLogger.debug("Alias", "Registering command aliases from config.yml");
        List<String> spawnAliases = plugin.getConfig().getStringList("command-aliases.spawn");
        List<String> setSpawnAliases = plugin.getConfig().getStringList("command-aliases.setspawn");
        List<String> adminAliases = plugin.getConfig().getStringList("command-aliases.xsetspawn");

        register("spawn", spawnAliases);
        register("setspawn", setSpawnAliases);
        register("xsetspawn", adminAliases);

        if (!spawnAliases.isEmpty() || !setSpawnAliases.isEmpty() || !adminAliases.isEmpty()) {
            plugin.logInfo("&aCustom command aliases registered from &fconfig.yml&a.");
        }
    }

    private void register(String commandName, List<String> aliases) {
        if (aliases == null || aliases.isEmpty()) return;

        PluginCommand pluginCommand = plugin.getCommand(commandName);
        if (pluginCommand == null) return;

        CommandMap commandMap = getCommandMap();
        if (commandMap == null) return;

        for (String alias : aliases) {
            String cleaned = alias.toLowerCase().trim().replace("/", "");
            if (cleaned.isEmpty()) continue;

            // Warn if the command already exists
            if (commandMap.getCommand(cleaned) != null) {
                plugin.getLogger().warning("Alias /" + cleaned + " for /" + commandName + " overrides an existing server command!");
            }

            // Pass-through command that delegates to the original
            Command proxy = new Command(cleaned) {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    return pluginCommand.execute(sender, label, args);
                }

                @Override
                public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                    try {
                        List<String> completions = pluginCommand.tabComplete(sender, alias, args);
                        return completions != null ? completions : Collections.emptyList();
                    } catch (Exception e) {
                        return Collections.emptyList();
                    }
                }
            };
            proxy.setDescription("Alias for /" + commandName + " (X-SetSpawn)");

            try {
                DebugLogger.debug("Alias", "Registering alias: /" + cleaned + " -> /" + commandName);
                commandMap.register(plugin.getName().toLowerCase(), proxy);
            } catch (Exception e) {
                plugin.getLogger().warning("Could not register alias '" + cleaned + "': " + e.getMessage());
            }
        }
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
}
