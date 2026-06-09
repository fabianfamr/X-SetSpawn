package com.fabian.xsetspawn.commands;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.hooks.CombatHook;
import com.fabian.xsetspawn.hooks.VaultHook;
import com.fabian.xsetspawn.logic.CooldownManager;
import com.fabian.xsetspawn.logic.DelayManager;
import com.fabian.xsetspawn.managers.LanguageManager;
import com.fabian.xsetspawn.managers.ManagerConfig;
import com.fabian.xsetspawn.managers.Permission;
import com.fabian.xsetspawn.managers.SpawnManager;
import com.fabian.xsetspawn.utils.ParticleUtil;
import com.fabian.xsetspawn.utils.SchedulerUtil;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * HubCommand - Handles /hub and /lobby commands on the Bukkit side.
 *
 * Behavior (priority order):
 *   1. If proxy-support is enabled AND a target server is configured,
 *      sends the player to that Bungee/Velocity server via Plugin Messaging.
 *   2. If the player is already on the lobby server (or proxy is off),
 *      teleports them to the named spawn "lobby" or "hub" (if set),
 *      or falls back to the world spawn.
 *   3. Supports: delay, cooldown, economy, combat check, falling check,
 *      per-command warmup, particles on arrival, and back-location saving.
 *
 * Permissions:
 *   xsetspawn.hub          - Base permission (default: true)
 *   xsetspawn.hub.bypass   - Bypass delay/cooldown/economy (default: op)
 */
public class HubCommand implements CommandExecutor, TabCompleter {

    private final XSetSpawn plugin;
    private final SpawnManager spawnManager;
    private final LanguageManager languageManager;
    private final ManagerConfig config;

    public HubCommand(XSetSpawn plugin) {
        this.plugin = plugin;
        this.spawnManager = plugin.getSpawnManager();
        this.languageManager = plugin.getLanguageManager();
        this.config = plugin.getManagerConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(languageManager.getMessage("player-only"));
            return true;
        }

        Player player = (Player) sender;

        // 1. Permission Check
        if (!Permission.HUB.has(player)) {
            player.sendMessage(languageManager.getMessage("no-permission"));
            return true;
        }

        // 2. Feature enabled check
        if (!config.hubEnabled) {
            player.sendMessage(languageManager.getMessage("command-disabled"));
            return true;
        }

        // 3. Combat Check
        if (config.combatCheckEnabled && CombatHook.isInCombat(player)) {
            player.sendMessage(languageManager.getMessage("combat-active"));
            return true;
        }

        // 4. Falling Check
        if (config.fallingCheckEnabled && player.getFallDistance() > 0) {
            player.sendMessage(languageManager.getMessage("falling-active"));
            return true;
        }

        // 5. Cooldown Check
        CooldownManager cooldownManager = plugin.getCooldownManager();
        if (config.cooldownEnabled && !Permission.BYPASS_COOLDOWN.has(player)) {
            if (cooldownManager.isOnCooldown(player)) {
                long secondsLeft = cooldownManager.getRemainingTime(player);
                player.sendMessage(languageManager.getMessage("cooldown-active", secondsLeft));
                return true;
            }
        }

        // 6. Economy Check
        VaultHook vault = plugin.getVaultHook();
        if (config.economyEnabled && vault.isSetup() && !Permission.BYPASS_ECONOMY.has(player)) {
            double cost = config.economyCost;
            if (!vault.hasEnough(player, cost)) {
                player.sendMessage(languageManager.getMessage("not-enough-money", vault.format(cost)));
                return true;
            }
        }

        // 7. Determine target: Proxy or Local
        // --- Proxy path: send player to another server ---
        if (config.proxyEnabled && config.hubProxyEnabled) {
            String targetServer = config.hubProxyServer != null && !config.hubProxyServer.isEmpty()
                    ? config.hubProxyServer : config.proxyServer;

            player.sendMessage(languageManager.getMessage("proxy-connecting", targetServer));
            plugin.getPluginMessageManager().sendToServer(player, targetServer);

            // Apply cooldown even for proxy teleports
            if (config.cooldownEnabled && !Permission.BYPASS_COOLDOWN.has(player)) {
                cooldownManager.setCooldown(player, config.cooldownTime);
            }

            // Charge economy
            if (config.economyEnabled && vault.isSetup() && !Permission.BYPASS_ECONOMY.has(player)) {
                double cost = config.economyCost;
                vault.withdrawPlayer(player, cost);
                if (cost > 0) {
                    player.sendMessage(languageManager.getMessage("teleport-cost", vault.format(cost)));
                }
            }
            return true;
        }

        // --- Local path: teleport to named "lobby"/"hub" spawn or world spawn ---
        Location hubLocation = resolveHubLocation(player);
        if (hubLocation == null) {
            player.sendMessage(languageManager.getMessage("hub-not-set"));
            return true;
        }

        String successMessage = languageManager.getMessage("hub-teleport");

        // 8. Teleport Logic (Delay or Instant)
        DelayManager delayManager = plugin.getDelayManager();
        int effectiveDelay = config.getDelayForCommand("hub");
        if (config.delayEnabled && !Permission.BYPASS_DELAY.has(player) && effectiveDelay > 0) {
            plugin.getBackManager().saveLocation(player);
            delayManager.scheduleTeleport(player, hubLocation, effectiveDelay, successMessage,
                    DelayManager.TeleportEvent.HUB);
        } else {
            // Instant teleport
            plugin.getBackManager().saveLocation(player);

            final Location finalLocation = hubLocation;
            final String finalMessage = successMessage;

            SchedulerUtil.teleport(plugin, player, finalLocation, () -> {
                if (!player.isOnline()) return;

                spawnManager.playSpawnSound(player, finalLocation);
                player.sendMessage(finalMessage);

                // Hub arrival particles
                if (config.particlesEnabled) {
                    ParticleUtil.spawnParticle(player.getLocation().add(0, 1, 0),
                            config.particleHubType, config.particleHubAmount);
                }

                // Protection
                if (config.protectionEnabled) {
                    int time = config.protectionTime;
                    player.setMetadata("xsetspawn_protection",
                            new org.bukkit.metadata.FixedMetadataValue(plugin,
                                    System.currentTimeMillis() + (time * 1000L)));
                }

                // Charge economy
                if (config.economyEnabled && vault.isSetup() && !Permission.BYPASS_ECONOMY.has(player)) {
                    double cost = config.economyCost;
                    vault.withdrawPlayer(player, cost);
                    if (cost > 0) {
                        player.sendMessage(languageManager.getMessage("teleport-cost", vault.format(cost)));
                    }
                }

                // Apply cooldown
                if (config.cooldownEnabled && !Permission.BYPASS_COOLDOWN.has(player)) {
                    plugin.getCooldownManager().setCooldown(player, config.cooldownTime);
                }
            });
        }

        return true;
    }

    /**
     * Resolves the hub location using priority:
     *   1. Named spawn "lobby" (if named-spawns enabled)
     *   2. Named spawn "hub"  (if named-spawns enabled)
     *   3. World spawn (fallback)
     */
    private Location resolveHubLocation(Player player) {
        if (config.namedSpawns) {
            Location lobby = spawnManager.getNamedSpawn("lobby");
            if (lobby != null) return lobby;

            Location hub = spawnManager.getNamedSpawn("hub");
            if (hub != null) return hub;
        }

        // Fallback to world spawn
        return spawnManager.getSpawn(player.getWorld());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
