package com.fabian.xsetspawn.hooks;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.utils.DebugLogger;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultHook {

    private final XSetSpawn plugin;
    private Economy econ = null;
    private boolean setup = false;

    public VaultHook(XSetSpawn plugin) {
        this.plugin = plugin;
        setupEconomy();
    }

    private void setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            DebugLogger.debug("Vault", "Vault plugin not found");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            DebugLogger.debug("Vault", "Vault found but no Economy provider registered");
            return;
        }
        econ = rsp.getProvider();
        setup = econ != null;
        DebugLogger.debug("Vault", "Economy hook " + (setup ? "successfully setup" : "failed (provider was null)"));
    }

    public boolean isSetup() {
        return setup;
    }

    public boolean hasEnough(Player player, double amount) {
        if (!setup) return true; // If vault isn't setup, let them pass
        return econ.has(player, amount);
    }

    public void withdrawPlayer(Player player, double amount) {
        if (!setup || amount <= 0) return;
        econ.withdrawPlayer(player, amount);
    }

    public String format(double amount) {
        if (!setup) return String.valueOf(amount);
        return econ.format(amount);
    }
}

