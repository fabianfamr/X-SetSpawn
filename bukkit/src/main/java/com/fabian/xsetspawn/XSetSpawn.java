package com.fabian.xsetspawn;

import com.fabian.xsetspawn.commands.AdminCommand;
import com.fabian.xsetspawn.commands.BackCommand;
import com.fabian.xsetspawn.commands.DelSpawnCommand;
import com.fabian.xsetspawn.commands.SetSpawnCommand;
import com.fabian.xsetspawn.commands.SpawnCommand;
import com.fabian.xsetspawn.listeners.CommandListener;
import com.fabian.xsetspawn.listeners.PlayerListener;
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
import com.fabian.xsetspawn.hooks.VaultHook;
import com.fabian.xsetspawn.utils.UpdateChecker;
import com.fabian.xsetspawn.metrics.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import com.fabian.xsetspawn.utils.SchedulerUtil;
import net.byteflux.libby.BukkitLibraryManager;
import net.byteflux.libby.Library;

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

        String storageType = configManager.getConfig().getString("storage.type", "YAML").toUpperCase();

        // Initialize Libby and download database libraries conditionally based on config
        try {
            BukkitLibraryManager libraryManager = new BukkitLibraryManager(this);
            libraryManager.addMavenCentral();
            
            if (storageType.equals("MONGODB") || storageType.equals("MONGO")) {
                Library bson = Library.builder()
                        .groupId("org.mongodb")
                        .artifactId("bson")
                        .version("4.11.1")
                        .build();
                
                Library bsonRecordCodec = Library.builder()
                        .groupId("org.mongodb")
                        .artifactId("bson-record-codec")
                        .version("4.11.1")
                        .build();
                        
                Library mongoCore = Library.builder()
                        .groupId("org.mongodb")
                        .artifactId("mongodb-driver-core")
                        .version("4.11.1")
                        .build();
    
                Library mongoDb = Library.builder()
                        .groupId("org.mongodb")
                        .artifactId("mongodb-driver-sync")
                        .version("4.11.1")
                        .build();
                
                libraryManager.loadLibrary(bson);
                libraryManager.loadLibrary(bsonRecordCodec);
                libraryManager.loadLibrary(mongoCore);
                libraryManager.loadLibrary(mongoDb);
            } else if (storageType.equals("SQL") || storageType.equals("MYSQL") || storageType.equals("MARIADB") || storageType.equals("H2")) {
                Library slf4j = Library.builder()
                        .groupId("org.slf4j")
                        .artifactId("slf4j-api")
                        .version("1.7.32")
                        .build();
                
                Library hikari = Library.builder()
                        .groupId("com.zaxxer")
                        .artifactId("HikariCP")
                        .version("3.4.5") // Java 8 compatible pool
                        .build();
                
                libraryManager.loadLibrary(slf4j);
                libraryManager.loadLibrary(hikari);

                if (storageType.equals("H2")) {
                    Library h2 = Library.builder()
                            .groupId("com.h2database")
                            .artifactId("h2")
                            .version("2.2.224")
                            .build();
                    libraryManager.loadLibrary(h2);
                } else if (storageType.equals("MYSQL") || storageType.equals("SQL")) {
                    Library mysql = Library.builder()
                            .groupId("mysql")
                            .artifactId("mysql-connector-java")
                            .version("8.0.33")
                            .build();
                    libraryManager.loadLibrary(mysql);
                } else if (storageType.equals("MARIADB")) {
                    Library mariadb = Library.builder()
                            .groupId("org.mariadb.jdbc")
                            .artifactId("mariadb-java-client")
                            .version("3.3.3")
                            .build();
                    libraryManager.loadLibrary(mariadb);
                }
            }
        } catch (Exception e) {
            logError("Failed to load runtime database libraries! " + e.getMessage());
        }

        // Initialize remaining managers
        try {
            this.storageManager = new StorageManager(this);
            this.languageManager = new LanguageManager(this);
            this.spawnManager = new SpawnManager(this);
            this.cooldownManager = new CooldownManager();
            this.delayManager = new DelayManager(this);
            this.vaultHook = new VaultHook(this);
            this.backManager = new BackManager(this);
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
        BackCommand backCommand = new BackCommand(this);
        DelSpawnCommand delSpawnCommand = new DelSpawnCommand(this);

        getCommand("setspawn").setExecutor(setSpawnCommand);
        getCommand("setspawn").setTabCompleter(setSpawnCommand);
        getCommand("spawn").setExecutor(spawnCommand);
        getCommand("spawn").setTabCompleter(spawnCommand);
        getCommand("xsetspawn").setExecutor(adminCommand);
        getCommand("xsetspawn").setTabCompleter(adminCommand);
        getCommand("back").setExecutor(backCommand);
        getCommand("delspawn").setExecutor(delSpawnCommand);
        getCommand("delspawn").setTabCompleter(delSpawnCommand);

        // Register dynamic aliases from config.yml
        new AliasManager(this).registerAliases();

        // Register PlaceholderAPI hook
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.fabian.xsetspawn.hooks.PlaceholderAPIExpansion(this).register();
        }

        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        // Register CommandListener manualy if on 1.13+ (where PlayerCommandSendEvent
        // exists)
        try {
            Class<?> eventClass = Class.forName("org.bukkit.event.player.PlayerCommandSendEvent");
            org.bukkit.event.HandlerList handlers = (org.bukkit.event.HandlerList) eventClass
                    .getMethod("getHandlerList")
                    .invoke(null);
            CommandListener listener = new CommandListener(this);

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
        if (metrics != null) {
            metrics.shutdown();
        }

        if (storageManager != null) {
            storageManager.close();
        }

        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&',
                "&8[&bX-SetSpawn&8] &cDisabled version " + getDescription().getVersion() + "! Out."));
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
