package com.fabian.xsetspawn.commands;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.managers.BackManager;
import com.fabian.xsetspawn.managers.LanguageManager;
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
        if (!(sender instanceof Player)) {
            sender.sendMessage(languageManager.getMessage("player-only"));
            return true;
        }

        Player player = (Player) sender;

        // Feature disabled check
        if (!plugin.getManagerConfig().backEnabled) {
            player.sendMessage(languageManager.getMessage("no-permission"));
            return true;
        }

        // Permission check
        if (!Permission.BACK.has(player)) {
            player.sendMessage(languageManager.getMessage("no-permission"));
            return true;
        }

        // Check if there is a saved location
        Location backLocation = backManager.getLocation(player);
        if (backLocation == null) {
            player.sendMessage(languageManager.getMessage("back-no-location"));
            return true;
        }

        // Teleport and clear the saved location (one-use)
        backManager.clearLocation(player);
        SchedulerUtil.teleport(plugin, player, backLocation, () -> {
            player.sendMessage(languageManager.getMessage("back-teleport"));
            plugin.getSpawnManager().playSpawnSound(player, backLocation);
            if (plugin.getManagerConfig().protectionEnabled) {
                int time = plugin.getManagerConfig().protectionTime;
                player.setMetadata("xsetspawn_protection", new org.bukkit.metadata.FixedMetadataValue(plugin, System.currentTimeMillis() + (time * 1000L)));
            }
        });

        return true;
    }
}
