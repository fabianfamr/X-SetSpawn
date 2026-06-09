package com.fabian.xsetspawn;

import com.fabian.xsetspawn.commands.AdminCommand;
import com.fabian.xsetspawn.commands.BackCommand;
import com.fabian.xsetspawn.commands.DelSpawnCommand;
import com.fabian.xsetspawn.commands.SetSpawnCommand;
import com.fabian.xsetspawn.commands.SpawnCommand;
import com.fabian.xsetspawn.listeners.CommandListener;
import com.fabian.xsetspawn.listeners.PlayerListener;
import com.fabian.xsetspawn.listeners.VoidTeleportListener;
import com.fabian.xsetspawn.logic.CooldownManager;
import com.fabian.xsetspawn.logic.DelayManager;
import com.fabian.xsetspawn.managers.AliasManager;
import com.fabian.xsetspawn.managers.ConfigManager;
import com.fabian.xsetspawn.managers.ManagerConfig;
import com.fabian.xsetspawn.managers.Permission;
import com.fabian.xsetspawn.managers.LanguageManager;
import com.fabian.xsetspawn.managers.SpawnManager;
import com.fabian.xsetspawn.managers.storage.StorageManager;
import com.fabian.xsetspawn.managers.BackManager;
import com.fabian.xsetspawn.managers.PluginMessageManager;
import com.fabian.xsetspawn.managers.DependencyManager;
import com.fabian.xsetspawn.hooks.VaultHook;
import com.fabian.xsetspawn.utils.SchedulerUtil;
import com.fabian.xsetspawn.utils.UpdateChecker;
import com.fabian.xsetspawn.metrics.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public class XSetSpawn extends JavaPlugin implements Listener {

    private static XSetSpawn instance;
    private ConfigManager configManager;
    private ManagerConfig managerConfig;
    private LanguageManager languageManager;
    private SpawnManager spawnManager;
    private CooldownManager cooldownManager;
    private DelayManager delayManager;
    private StorageManager storageManager;
    private VaultHook vaultHook;
    private UpdateChecker updateChecker;
    private Metrics metrics;
    private BackManager backManager;
    private PluginMessageManager pluginMessageManager;
    private DependencyManager dependencyManager;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize config managers first to check storage type
        try {
            this.configManager = new ConfigManager(this);
            this.managerConfig = new ManagerConfig(this);
        } catch (Exception e) {
            logError("Could not initialize config managers: " + e.getMessage());
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize and load dependencies
        this.dependencyManager = new DependencyManager(this);
        this.dependencyManager.loadDependencies();

        // Initialize remaining managers
        try {
            this.storageManager = new StorageManager(this);
            this.languageManager = new LanguageManager(this);
            this.spawnManager = new SpawnManager(this);
            this.spawnManager.loadCachesAsync();
            this.cooldownManager = new CooldownManager();
            this.delayManager = new DelayManager(this);
            this.vaultHook = new VaultHook(this);
            this.backManager = new BackManager(this);
            this.backManager.startCleanupTask();
            this.pluginMessageManager = new PluginMessageManager(this);
        } catch (Exception e) {
            logError("Could not initialize handlers: " + e.getMessage());
            e.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Register commands
        SetSpawnCommand setSpawnCommand = new SetSpawnCommand(this);
        SpawnCommand spawnCommand = new SpawnCommand(this);
        AdminCommand adminCommand = new AdminCommand(this);
        DelSpawnCommand delSpawnCommand = new DelSpawnCommand(this);

        getCommand("setspawn").setExecutor(setSpawnCommand);
        getCommand("setspawn").setTabCompleter(setSpawnCommand);
        getCommand("spawn").setExecutor(spawnCommand);
        getCommand("spawn").setTabCompleter(spawnCommand);
        getCommand("xsetspawn").setExecutor(adminCommand);
        getCommand("xsetspawn").setTabCompleter(adminCommand);
        getCommand("delspawn").setExecutor(delSpawnCommand);
        getCommand("delspawn").setTabCompleter(delSpawnCommand);

        // Only register /back when enabled — when disabled the command is
        // completely unknown to the server (no tab-complete, no "unknown command"
        // confusion, and no fake "no permission" message).
        if (managerConfig.backEnabled) {
            BackCommand backCommand = new BackCommand(this);
            getCommand("back").setExecutor(backCommand);
        } else {
            // Unregister the command so it doesn't show up at all
            try {
                org.bukkit.command.CommandMap commandMap = getServer().getCommandMap();
                org.bukkit.command.KnownCommand known = commandMap.getCommand("back");
                if (known != null) {
                    known.unregister(commandMap);
                    commandMap.getKnownCommands().remove("back");
                    // Also remove aliases
                    commandMap.getKnownCommands().remove("xsetspawn:back");
                }
            } catch (Exception ignored) {
                // Fallback: set a no-op executor so it at least doesn't say "no permission"
                getCommand("back").setExecutor((sender, command, label, args) -> {
                    sender.sendMessage(getLanguageManager().getMessage("command-disabled"));
                    return true;
                });
            }
        }

        // Register dynamic aliases from config.yml
        new AliasManager(this).registerAliases();

        // Register PlaceholderAPI hook
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.fabian.xsetspawn.hooks.PlaceholderAPIExpansion(this).register();
        }

        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        if (managerConfig.voidTeleportEnabled) {
            getServer().getPluginManager().registerEvents(new VoidTeleportListener(this), this);
        }

        // Register CommandListener manualy if on 1.13+ (where PlayerCommandSendEvent
        // exists)
        try {
            Class<?> eventClass = Class.forName("org.bukkit.event.player.PlayerCommandSendEvent");
            org.bukkit.event.HandlerList handlers = (org.bukkit.event.HandlerList) eventClass
                    .getMethod("getHandlerList")
                    .invoke(null);
            CommandListener listener = new CommandListener();

            handlers.register(new org.bukkit.plugin.RegisteredListener(listener, (l, event) -> {
                if (eventClass.isInstance(event)) {
                    ((CommandListener) l).onCommandSend(event);
                }
            }, org.bukkit.event.EventPriority.NORMAL, this, false));
        } catch (Exception e) {
            // Version 1.12 or lower, or unexpected error
        }

        // Check for updates
        if (managerConfig.checkUpdates) {
            this.updateChecker = new UpdateChecker(this);
            SchedulerUtil.runAsyncDelayed(this, () -> {
                updateChecker.checkForUpdates();
            }, 20L * 5); // Check 5 seconds after server start
        }

        // Initialize bStats Metrics
        setupMetrics();

        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&bX-SetSpawn&8] &7----------------------------------------------"));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&bX-SetSpawn&8]   &aEnabled v" + getDescription().getVersion() + "! Enjoy spawning!"));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&bX-SetSpawn&8]   &7Storage: &f" + managerConfig.storageType + " &7| Language: &f" + managerConfig.language));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&bX-SetSpawn&8] &7----------------------------------------------"));
    }

    @Override
    public void onDisable() {
        // Clean up any active holograms (ArmorStands) to prevent orphaned entities
        com.fabian.xsetspawn.utils.HologramUtil.removeAll();

        if (metrics != null) {
            metrics.shutdown();
        }

        if (storageManager != null) {
            storageManager.close();
        }

        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&bX-SetSpawn&8] &7----------------------------------------------"));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&bX-SetSpawn&8]   &cDisabled v" + getDescription().getVersion() + "! Out."));
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&bX-SetSpawn&8] &7----------------------------------------------"));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Notify about updates if available
        if (managerConfig.checkUpdates && updateChecker != null) {
            if (Permission.ADMIN.has(player)) {
                SchedulerUtil.runAsyncDelayed(this, () -> {
                    if (updateChecker.isUpdateAvailable()) {
                        player.sendMessage(languageManager.getMessage("update-available",
                                getDescription().getVersion(),
                                updateChecker.getLatestVersion()));
                        player.sendMessage(languageManager.getMessage("update-download",
                                updateChecker.getDownloadUrl()));
                    }
                }, 20L * 3); // Notify 3 seconds after join
            }
        }
    }

    public static XSetSpawn getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ManagerConfig getManagerConfig() {
        return managerConfig;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public DelayManager getDelayManager() {
        return delayManager;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public VaultHook getVaultHook() {
        return vaultHook;
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    public BackManager getBackManager() {
        return backManager;
    }

    public PluginMessageManager getPluginMessageManager() {
        return pluginMessageManager;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public void setupMetrics() {
        if (managerConfig.metricsEnabled) {
            if (this.metrics == null) {
                int pluginId = 30619;
                this.metrics = new Metrics(this, pluginId);

                // Custom Charts
                metrics.addCustomChart(new Metrics.SimplePie("storage_type", () -> 
                    managerConfig.storageType.toUpperCase()));
                    
                metrics.addCustomChart(new Metrics.SimplePie("update_checker", () -> 
                    managerConfig.checkUpdates ? "Enabled" : "Disabled"));
                    
                metrics.addCustomChart(new Metrics.SimplePie("vault_hook", () -> 
                    vaultHook.isSetup() ? "Hooked" : "Not Found"));
                    
                metrics.addCustomChart(new Metrics.SimplePie("papi_hook", () -> 
                    Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null ? "Hooked" : "Not Found"));

                metrics.addCustomChart(new Metrics.SimplePie("language", () -> 
                    managerConfig.language.toUpperCase()));

                metrics.addCustomChart(new Metrics.SimplePie("per_world_spawn", () -> 
                    managerConfig.perWorld ? "Enabled" : "Disabled"));

                metrics.addCustomChart(new Metrics.SimplePie("economy_enabled", () -> 
                    managerConfig.economyEnabled ? "Enabled" : "Disabled"));
            }
        } else {
            if (this.metrics != null) {
                this.metrics.shutdown();
                this.metrics = null;
            }
        }
    }

    public void log(String message) {
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&bX-SetSpawn&8] &7" + message));
    }

    public void logError(String message) {
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&bX-SetSpawn&8] &c" + message));
    }
}
