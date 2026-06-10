package com.fabian.xsetspawn.commands;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.utils.DebugLogger;
import com.fabian.xsetspawn.managers.LanguageManager;
import com.fabian.xsetspawn.managers.Permission;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DelSpawnCommand implements CommandExecutor, TabCompleter {

    private final XSetSpawn plugin;
    private final LanguageManager languageManager;

    public DelSpawnCommand(XSetSpawn plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        DebugLogger.debug("Command", "/delspawn executed by " + sender.getName() + " with args: " + java.util.Arrays.toString(args));
        if (!Permission.DELSPAWN.has(sender)) {
            sender.sendMessage(languageManager.getMessage("no-permission"));
            return true;
        }

        List<String> allSpawns = plugin.getSpawnManager().getAllNamedSpawnNames();

        if (args.length < 1) {
            if (allSpawns.size() == 1) {
                // Only one spawn exists: delete it without requiring a name
                String onlySpawn = allSpawns.get(0);
                plugin.getSpawnManager().removeNamedSpawn(onlySpawn);
                sender.sendMessage(languageManager.getMessage("spawn-deleted", onlySpawn));
            } else if (allSpawns.isEmpty()) {
                sender.sendMessage(languageManager.getMessage("no-spawns-to-delete"));
            } else {
                sender.sendMessage(languageManager.getMessage("delspawn-usage"));
            }
        } else {
            String name = args[0].toLowerCase();
            if (plugin.getSpawnManager().isNamedSpawnSet(name)) {
                plugin.getSpawnManager().removeNamedSpawn(name);
                sender.sendMessage(languageManager.getMessage("spawn-deleted", name));
            } else {
                sender.sendMessage(languageManager.getMessage("spawn-not-found", name));
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!Permission.DELSPAWN.has(sender)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> names = plugin.getSpawnManager().getAllNamedSpawnNames();
            return names.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .sorted()
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
