package com.fabian.xsetspawn.commands;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.managers.ManagerConfig;
import com.fabian.xsetspawn.managers.Permission;
import com.fabian.xsetspawn.managers.LanguageManager;
import com.fabian.xsetspawn.managers.SpawnManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SetSpawnCommand implements CommandExecutor, TabCompleter {

    private final ManagerConfig config;
    private final LanguageManager languageManager;
    private final SpawnManager spawnManager;
    private final XSetSpawn plugin;

    public SetSpawnCommand(XSetSpawn plugin) {
        this.plugin = plugin;
        this.config = plugin.getManagerConfig();
        this.languageManager = plugin.getLanguageManager();
        this.spawnManager = plugin.getSpawnManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Permission check
        if (!Permission.SETSPAWN.has(sender)) {
            sender.sendMessage(languageManager.getMessage("no-permission"));
            return true;
        }

        // --- /setspawn ---
        // No args: sets the default spawn at player's location
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cUsage: /setspawn <x> <y> <z> [world] or /setspawn <name>");
                return true;
            }
            Player player = (Player) sender;
            spawnManager.setSpawn(player.getLocation());
            sendSetSpawnMessage(player, player.getWorld(), null);
            return true;
        }

        // --- /setspawn firstjoin ---
        if (args.length == 1 && args[0].equalsIgnoreCase("firstjoin")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cOnly players can set the firstjoin spawn without coordinates.");
                return true;
            }
            Player player = (Player) sender;
            spawnManager.setFirstJoinSpawn(player.getLocation());
            sender.sendMessage(languageManager.getMessage("spawn-firstjoin-set"));
            return true;
        }

        // --- Smart Parsing ---
        
        // Case 1: Try /setspawn <x> <y> <z> [world] [yaw] [pitch] — Default spawn at coordinates
        if (args.length >= 3) {
            try {
                // If this fails, it's either a named spawn or invalid usage
                double x = parseCoord(sender, args[0], "X");
                double y = parseCoord(sender, args[1], "Y");
                double z = parseCoord(sender, args[2], "Z");

                World world = null;
                // If arg[3] is not a number, it's likely a world name
                if (args.length >= 4 && !isNumericOrTilde(args[3])) {
                    world = Bukkit.getWorld(args[3]);
                    if (world == null) {
                        sender.sendMessage("§cWorld '" + args[3] + "' not found!");
                        return true;
                    }
                } else {
                    if (sender instanceof Player) {
                        world = ((Player) sender).getWorld();
                    } else {
                        sender.sendMessage("§cConsole must specify a world: /setspawn <x> <y> <z> <world>");
                        return true;
                    }
                }

                float yaw = 0;
                float pitch = 0;
                if (args.length >= 5 && isNumericOrTilde(args[4])) yaw = Float.parseFloat(args[4]);
                if (args.length >= 6 && isNumericOrTilde(args[5])) pitch = Float.parseFloat(args[5]);
                
                spawnManager.setSpawn(world, x, y, z, yaw, pitch);
                sendSetSpawnMessage(sender, world, null);
                return true;

            } catch (NumberFormatException ignored) {
                // Not a default coordinate-based spawn, fall through to named spawns
            }
        }

        // Case 2: /setspawn <name>  (1 arg, not numeric) — Named Spawn at player location
        if (args.length == 1) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cPlayers only can set a named spawn without coordinates.");
                return true;
            }
            if (!config.namedSpawns) {
                sender.sendMessage(languageManager.getMessage("named-spawns-disabled"));
                return true;
            }
            Player player = (Player) sender;
            String name = args[0].toLowerCase();
            spawnManager.setNamedSpawn(name, player.getLocation());
            sendSetSpawnMessage(player, player.getWorld(), name);
            return true;
        }

        // Case 3: /setspawn <name> <x> <y> <z> [world] [yaw] [pitch] — Named Spawn at coordinates
        if (args.length >= 4) {
            try {
                String name = args[0].toLowerCase();
                double x = parseCoord(sender, args[1], "X");
                double y = parseCoord(sender, args[2], "Y");
                double z = parseCoord(sender, args[3], "Z");

                if (!config.namedSpawns) {
                    sender.sendMessage(languageManager.getMessage("named-spawns-disabled"));
                    return true;
                }

                World world = null;
                if (args.length >= 5 && !isNumericOrTilde(args[4])) {
                    world = Bukkit.getWorld(args[4]);
                    if (world == null) {
                        sender.sendMessage("§cWorld '" + args[4] + "' not found!");
                        return true;
                    }
                } else {
                    if (sender instanceof Player) {
                        world = ((Player) sender).getWorld();
                    } else {
                        sender.sendMessage("§cConsole must specify a world when using named spawns with coordinates.");
                        return true;
                    }
                }

                float yaw = 0;
                float pitch = 0;
                if (args.length >= 6 && isNumericOrTilde(args[5])) yaw = Float.parseFloat(args[5]);
                
                spawnManager.setNamedSpawn(name, world, x, y, z, yaw, pitch);
                sendSetSpawnMessage(sender, world, name);
                return true;

            } catch (NumberFormatException ignored) {
                // Not a named coordinate-based spawn
            }
        }

        // Catch-all usage message
        sender.sendMessage("§cUsage: /setspawn | /setspawn <name> | /setspawn <x> <y> <z> | /setspawn <name> <x> <y> <z>");
        return true;
    }

    private boolean isNumericOrTilde(String s) {
        if (s.startsWith("~")) return true;
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private double parseCoord(CommandSender sender, String input, String axis) {
        if (input.startsWith("~") && sender instanceof Player) {
            double current = 0;
            Location loc = ((Player) sender).getLocation();
            if (axis.equals("X")) current = loc.getX();
            else if (axis.equals("Y")) current = loc.getY();
            else if (axis.equals("Z")) current = loc.getZ();
            if (input.length() == 1) return current;
            return current + Double.parseDouble(input.substring(1));
        }
        return Double.parseDouble(input);
    }

    private void sendSetSpawnMessage(CommandSender sender, World world, String spawnName) {
        if (spawnName != null) {
            sender.sendMessage(languageManager.getMessage("spawn-set-named", spawnName));
        } else if (config.perWorld) {
            sender.sendMessage(languageManager.getMessage("spawn-set-world", world.getName()));
        } else {
            sender.sendMessage(languageManager.getMessage("spawn-set"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!Permission.SETSPAWN.has(sender)) return Collections.emptyList();

        if (args.length == 1) {
            java.util.List<String> completions = new java.util.ArrayList<>();
            completions.add("~");
            completions.add("firstjoin");
            // Named spawns are completed from existing names; no literal placeholder needed
            return completions.stream()
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length >= 2 && args.length <= 4) {
            return Collections.singletonList("~");
        }

        if (args.length == 5) {
            return Bukkit.getWorlds().stream()
                    .map(World::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[4].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
