package com.fabian.xsetspawn.commands;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.utils.DebugLogger;
import com.fabian.xsetspawn.hooks.CombatHook;
import com.fabian.xsetspawn.hooks.VaultHook;
import com.fabian.xsetspawn.managers.CooldownManager;
import com.fabian.xsetspawn.managers.DelayManager;
import com.fabian.xsetspawn.managers.BackManager;
import com.fabian.xsetspawn.managers.LanguageManager;
import com.fabian.xsetspawn.managers.ManagerConfig;
import com.fabian.xsetspawn.managers.Permission;
import com.fabian.xsetspawn.utils.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BackCommand implements CommandExecutor {

    private final XSetSpawn plugin;
    private final BackManager backManager;
    private final LanguageManager languageManager;

    public BackCommand(XSetSpawn plugin) {
        this.plugin = plugin;
        this.backManager = plugin.getBackManager();
        this.languageManager = plugin.getLanguageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        DebugLogger.debug("Command", "/back executed by " + sender.getName());
        if (!(sender instanceof Player)) {
            sender.sendMessage(languageManager.getMessage("player-only"));
            return true;
        }

        Player player = (Player) sender;
        ManagerConfig config = plugin.getManagerConfig();

        // Feature disabled check
        if (!config.backEnabled) {
            player.sendMessage(languageManager.getMessage("command-disabled"));
            return true;
        }

        // Permission check
        if (!Permission.BACK.has(player)) {
            player.sendMessage(languageManager.getMessage("no-permission"));
            return true;
        }

        // Combat check — do not allow /back while in combat
        if (config.combatCheckEnabled && CombatHook.isInCombat(player)) {
            player.sendMessage(languageManager.getMessage("combat-active"));
            return true;
        }

        // Falling check — do not allow /back while falling
        if (config.fallingCheckEnabled && player.getFallDistance() > 0) {
            player.sendMessage(languageManager.getMessage("falling-active"));
            return true;
        }

        // Cooldown check — reuses the same cooldown system as /spawn
        CooldownManager cooldownManager = plugin.getCooldownManager();
        if (config.cooldownEnabled && !Permission.BYPASS_COOLDOWN.has(player)) {
            if (cooldownManager.isOnCooldown(player)) {
                long secondsLeft = cooldownManager.getRemainingTime(player);
                player.sendMessage(languageManager.getMessage("cooldown-active", secondsLeft));
                return true;
            }
        }

        // Economy check — reuses the same economy system as /spawn
        VaultHook vault = plugin.getVaultHook();
        if (config.economyEnabled && vault.isSetup() && !Permission.BYPASS_ECONOMY.has(player)) {
            double cost = config.economyCost;
            if (!vault.hasEnough(player, cost)) {
                player.sendMessage(languageManager.getMessage("not-enough-money", vault.format(cost)));
                return true;
            }
        }

        // Check if there is a valid saved location (also handles expired / unloaded world)
        Location backLocation = backManager.getLocation(player);
        if (backLocation == null) {
            DebugLogger.debug("Command", "No back location for " + player.getName());
            player.sendMessage(languageManager.getMessage("back-no-location"));
            return true;
        }

        // Verify the world is still loaded and accessible
        if (backLocation.getWorld() == null || !plugin.getServer().getWorlds().contains(backLocation.getWorld())) {
            backManager.clearLocation(player);
            player.sendMessage(languageManager.getMessage("back-no-location"));
            return true;
        }

        // Consume the saved location (one-use)
        backManager.clearLocation(player);
        DebugLogger.debug("Command", "Teleporting " + player.getName() + " back to " + backLocation.getWorld().getName());

        // Decide: delay or instant (respects per-command delay)
        int effectiveDelay = config.getDelayForCommand("back");
        if (config.delayEnabled && !Permission.BYPASS_DELAY.has(player) && effectiveDelay > 0) {
            // Delayed teleport with BACK event particles
            plugin.getDelayManager().scheduleTeleport(player, backLocation, effectiveDelay,
                    languageManager.getMessage("back-teleport"),
                    com.fabian.xsetspawn.managers.DelayManager.TeleportEvent.BACK);
        } else {
            // Instant teleport
            SchedulerUtil.teleport(plugin, player, backLocation, () -> {
                if (!player.isOnline()) return;

                player.sendMessage(languageManager.getMessage("back-teleport"));
                plugin.getSpawnManager().playSpawnSound(player, backLocation);

                // Back arrival particles
                if (config.particlesEnabled) {
                    com.fabian.xsetspawn.utils.ParticleUtil.spawnParticle(
                            player.getLocation().add(0, 1, 0),
                            config.particleBackType, config.particleBackAmount);
                }

                // Apply protection
                if (config.protectionEnabled) {
                    int time = config.protectionTime;
                    player.setMetadata("xsetspawn_protection", new org.bukkit.metadata.FixedMetadataValue(plugin, System.currentTimeMillis() + (time * 1000L)));
                }

                // Charge economy after successful teleport
                if (config.economyEnabled && vault.isSetup() && !Permission.BYPASS_ECONOMY.has(player)) {
                    double cost = config.economyCost;
                    vault.withdrawPlayer(player, cost);
                    if (cost > 0) {
                        player.sendMessage(languageManager.getMessage("teleport-cost", vault.format(cost)));
                    }
                }

                // Apply cooldown after successful teleport
                if (config.cooldownEnabled && !Permission.BYPASS_COOLDOWN.has(player)) {
                    plugin.getCooldownManager().setCooldown(player, config.cooldownTime);
                }
            });
        }

        return true;
    }
}
