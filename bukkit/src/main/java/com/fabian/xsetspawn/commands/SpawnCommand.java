package com.fabian.xsetspawn.commands;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.utils.DebugLogger;
import com.fabian.xsetspawn.logic.DelayManager;
import com.fabian.xsetspawn.logic.CooldownManager;
import com.fabian.xsetspawn.managers.LanguageManager;
import com.fabian.xsetspawn.managers.ManagerConfig;
import com.fabian.xsetspawn.managers.Permission;
import com.fabian.xsetspawn.managers.SpawnManager;
import com.fabian.xsetspawn.hooks.VaultHook;
import com.fabian.xsetspawn.hooks.CombatHook;
import com.fabian.xsetspawn.utils.SchedulerUtil;
import com.fabian.xsetspawn.utils.ColorUtils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class SpawnCommand implements CommandExecutor, TabCompleter {

    private final XSetSpawn plugin;
    private final SpawnManager spawnManager;
    private final LanguageManager languageManager;
    private final ManagerConfig config;

    public SpawnCommand(XSetSpawn plugin) {
        this.plugin = plugin;
        this.spawnManager = plugin.getSpawnManager();
        this.languageManager = plugin.getLanguageManager();
        this.config = plugin.getManagerConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        DebugLogger.debug("Command", "/spawn executed by " + sender.getName());
        if (!(sender instanceof Player)) {
            sender.sendMessage(languageManager.getMessage("player-only"));
            return true;
        }

        Player player = (Player) sender;

        // 1. Permission Check
        if (!Permission.SPAWN.has(player)) {
            DebugLogger.debug("Command", "Permission denied for /spawn: " + player.getName());
            player.sendMessage(languageManager.getMessage("no-permission"));
            return true;
        }

        // 2. Combat Check
        if (config.combatCheckEnabled && CombatHook.isInCombat(player)) {
            DebugLogger.debug("Command", "Spawn blocked - player in combat: " + player.getName());
            player.sendMessage(languageManager.getMessage("combat-active"));
            return true;
        }

        // 3. Falling Check
        if (config.fallingCheckEnabled && player.getFallDistance() > 0) {
            DebugLogger.debug("Command", "Spawn blocked - player falling: " + player.getName());
            player.sendMessage(languageManager.getMessage("falling-active"));
            return true;
        }

        // 4. Spawn Selection (Named or World)
        World world = player.getWorld();
        Location spawnLocation = null;
        String spawnName = null;
        boolean isNamed = false;

        if (args.length > 0 && config.namedSpawns) {
            spawnName = args[0].toLowerCase();
            if (spawnManager.isNamedSpawnSet(spawnName)) {
                DebugLogger.debug("Command", "Named spawn selected: " + spawnName + " by " + player.getName());
                // Check named spawn permission
                if (!player.hasPermission("xsetspawn.spawn." + spawnName) && !Permission.ADMIN.has(player)) {
                    player.sendMessage(languageManager.getMessage("no-permission"));
                    return true;
                }
                spawnLocation = spawnManager.getNamedSpawn(spawnName);
                isNamed = true;
            }
        }

        // Fallback to generic spawn if no valid named spawn was found
        if (!isNamed) {
            spawnLocation = spawnManager.getSpawn(world);
        }

        if (spawnLocation == null) {
            DebugLogger.debug("Command", "No spawn available for " + player.getName() + " (named=" + isNamed + ", world=" + world.getName() + ")");
            if (isNamed) {
                 player.sendMessage(languageManager.getMessage("spawn-not-found", spawnName));
            } else if (config.perWorld) {
                player.sendMessage(languageManager.getMessage("spawn-not-set-world", world.getName()));
            } else {
                player.sendMessage(languageManager.getMessage("spawn-not-set"));
            }
            return true;
        }

        // 5. Cooldown Check
        CooldownManager cooldownManager = plugin.getCooldownManager();
        if (config.cooldownEnabled && !Permission.BYPASS_COOLDOWN.has(player)) {
            if (cooldownManager.isOnCooldown(player)) {
                long secondsLeft = cooldownManager.getRemainingTime(player);
                DebugLogger.debug("Command", "Spawn blocked - cooldown active for " + player.getName() + " (" + secondsLeft + "s remaining)");
                player.sendMessage(languageManager.getMessage("cooldown-active", secondsLeft));
                return true;
            }
        }

        // 6. Economy Check (Vault)
        VaultHook vault = plugin.getVaultHook();
        if (config.economyEnabled && vault.isSetup() && !Permission.BYPASS_ECONOMY.has(player)) {
            double cost = config.economyCost;
            if (!vault.hasEnough(player, cost)) {
                player.sendMessage(languageManager.getMessage("not-enough-money", vault.format(cost)));
                return true;
            }
        }

        // 7. Teleport Logic (Delay or Instant)
        String successMessage;
        if (isNamed) {
            successMessage = languageManager.getMessage("spawn-teleport-named", spawnName);
        } else if (config.perWorld) {
            successMessage = languageManager.getMessage("spawn-teleport-world", world.getName());
        } else {
            successMessage = languageManager.getMessage("spawn-teleport");
        }

        DelayManager delayManager = plugin.getDelayManager();
        int effectiveDelay = config.getDelayForCommand("spawn");
        if (config.delayEnabled && !Permission.BYPASS_DELAY.has(player) && effectiveDelay > 0) {
            DebugLogger.debug("Command", "Scheduling delayed spawn teleport for " + player.getName() + " (" + effectiveDelay + "s)");
            // Save back-location at the START of the delay (so the player can go back
            // if the delay completes and they get teleported).  If the delay is
            // cancelled (movement), the back-location is cleared by DelayManager.
            plugin.getBackManager().saveLocation(player);
            delayManager.scheduleTeleport(player, spawnLocation, effectiveDelay, successMessage);
        } else {
            // Instant Teleport — save location right before teleporting
            plugin.getBackManager().saveLocation(player);
            
            DebugLogger.debug("Command", "Instant teleport for " + player.getName() + " to " + spawnLocation.getWorld().getName());
            final Location finalLocation = spawnLocation;
            final String finalMessage = successMessage;
            
            SchedulerUtil.teleport(this.plugin, player, finalLocation, () -> {
                spawnManager.playSpawnSound(player, finalLocation);
                player.sendMessage(finalMessage);

                // Charge economy after successful instant teleport
                if (config.economyEnabled && vault.isSetup() && !Permission.BYPASS_ECONOMY.has(player)) {
                    double cost = config.economyCost;
                    vault.withdrawPlayer(player, cost);
                    if (cost > 0) {
                        player.sendMessage(languageManager.getMessage("teleport-cost", vault.format(cost)));
                    }
                }
                
                // Apply cooldown after successful instant teleport
                if (config.cooldownEnabled && !Permission.BYPASS_COOLDOWN.has(player)) {
                    this.plugin.getCooldownManager().setCooldown(player, config.cooldownTime);
                }
            });
        }

        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return java.util.Collections.emptyList();
        Player player = (Player) sender;

        if (args.length == 1 && config.namedSpawns) {
            java.util.List<String> completions = new java.util.ArrayList<>();
            
            for (String spawnName : spawnManager.getAllNamedSpawnNames()) {
                if (Permission.ADMIN.has(player) || player.hasPermission("xsetspawn.spawn." + spawnName.toLowerCase())) {
                    if (spawnName.toLowerCase().startsWith(args[0].toLowerCase())) {
                        completions.add(spawnName);
                    }
                }
            }
            return completions;
        }

        return java.util.Collections.emptyList();
    }
}

