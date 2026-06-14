package com.fabian.xsetspawn.commands;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.utils.ColorUtils;
import com.fabian.xsetspawn.utils.DebugLogger;
import com.fabian.xsetspawn.managers.LanguageManager;
import com.fabian.xsetspawn.managers.Permission;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DelSpawnCommand implements TabExecutor {

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
            ColorUtils.sendMessage(sender, languageManager.getMessage("no-permission"));
            return true;
        }

        List<String> allSpawns = plugin.getSpawnManager().getAllNamedSpawnNames();

        if (args.length < 1) {
            if (allSpawns.size() == 1) {
                // Only one spawn exists: delete it without requiring a name
                String onlySpawn = allSpawns.get(0);
                plugin.getSpawnManager().removeNamedSpawn(onlySpawn);
                ColorUtils.sendMessage(sender, languageManager.getMessage("spawn-deleted", onlySpawn));
            } else if (allSpawns.isEmpty()) {
                ColorUtils.sendMessage(sender, languageManager.getMessage("no-spawns-to-delete"));
            } else {
                ColorUtils.sendMessage(sender, languageManager.getMessage("delspawn-usage"));
            }
        } else {
            String name = args[0].toLowerCase();
            if (plugin.getSpawnManager().isNamedSpawnSet(name)) {
                plugin.getSpawnManager().removeNamedSpawn(name);
                ColorUtils.sendMessage(sender, languageManager.getMessage("spawn-deleted", name));
            } else {
                ColorUtils.sendMessage(sender, languageManager.getMessage("spawn-not-found", name));
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
