package com.fabian.xsetspawn.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;

import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.google.common.io.ByteStreams;
import com.google.common.io.ByteArrayDataInput;
import com.fabian.xsetspawn.velocity.commands.HubCommand;
import com.fabian.xsetspawn.velocity.commands.ReloadCommand;
import com.fabian.xsetspawn.velocity.commands.SetLobbyCommand;
import com.fabian.xsetspawn.velocity.utils.UpdateChecker;
import com.fabian.xsetspawn.velocity.metrics.Metrics;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * X-SetSpawn Velocity Plugin
 * Provides /hub, /lobby, and /spawn commands on the Velocity proxy
 * to send players to a configured backend server.
 */
@Plugin(
    id = "x-setspawn",
    name = "X-SetSpawn",
    version = "2.2",
    description = "Proxy hub/lobby/spawn commands for X-SetSpawn",
    authors = {"Fabian"}
)
public class XSetSpawnVelocity {

    // Plugin messaging channel for Bukkit-Proxy communication
    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.create("xsetspawn", "main");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    // Expected config-code version. If the user's file has a lower value, it gets rebuilt.
    private static final int EXPECTED_CONFIG_CODE = 7;

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Metrics.Factory metricsFactory;
    private Metrics metrics;

    // Config values
    private String targetServer = "lobby";
    private Map<String, String> aliasServers = new LinkedHashMap<>();
    private int cooldownSeconds = 3;
    private int joinTeleportDelay = 0;
    private int switchTeleportDelay = 200;
    private boolean debugEnabled = false;
    private boolean showConnectingMessage = true;
    private boolean connectOnFirstJoin = true;
    private String language = "EN";

    // Global Lobby Location (synced from Bukkit)
    private String lobbyWorld;
    private double lobbyX, lobbyY, lobbyZ;
    private float lobbyYaw, lobbyPitch;
    private boolean lobbyCoordsSet = false;

    // Messages (loaded from messages/<lang>.yml)
    private String prefix = "§8[§bX-SetSpawn§8]§r ";
    private String msgConnecting = "§aSending you to §e{server}§a...";
    private String msgCooldown = "§cPlease wait §e{time}s §cbefore using this again.";
    private String msgServerNotFound = "§cServer '{server}' not found!";
    private String msgPlayerOnly = "§cThis command can only be used by players.";
    private String msgAlreadyConnected = "§eYou are already connected to this server!";
    private String msgConnectionFailed = "§cCould not connect to §e{server}§c. Please try again.";
    private String msgLobbySet = "§aGlobal lobby server has been set to: §e{server}";
    private String msgNoPermission = "§cYou don't have permission to use this command.";
    private String msgErrorNoServer = "§cError: Could not determine current server.";
    private String msgReloadSuccess = "§aConfiguration and messages reloaded successfully!";
    private String msgReloadHelp = "§e/xssproxy reload §7- §fReload the plugin configuration and messages";

    // Cooldowns and pending tasks
    private final Map<UUID, Long> cooldowns = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> pendingTeleports = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> lastGlobalTeleport = new java.util.concurrent.ConcurrentHashMap<>();

    @Inject
    public XSetSpawnVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory, Metrics.Factory metricsFactory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.metricsFactory = metricsFactory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // 1. Read the current config-code from config.yml BEFORE loading anything
        Path configFile = dataDirectory.resolve("config.yml");
        int currentVersion = readConfigCode(configFile);
        
        // 2. Load configuration and messages using that master version
        loadConfig(currentVersion);
        loadData();
        loadMessages(currentVersion);

        // Register plugin messaging channel
        server.getChannelRegistrar().register(CHANNEL);

        // Register commands - SetLobby first for priority
        CommandManager commandManager = server.getCommandManager();
        CommandMeta setLobbyMeta = commandManager.metaBuilder("setlobby").aliases("sl").build();
        commandManager.register(setLobbyMeta, new SetLobbyCommand(this));

        CommandMeta reloadMeta = commandManager.metaBuilder("xssproxy").build();
        commandManager.register(reloadMeta, new ReloadCommand(this));

        for (Map.Entry<String, String> entry : aliasServers.entrySet()) {
            HubCommand cmd = new HubCommand(this, entry.getValue());
            CommandMeta meta = commandManager.metaBuilder(entry.getKey()).build();
            commandManager.register(meta, cmd);
        }

        server.getConsoleCommandSource().sendMessage(LEGACY.deserialize("&b----------------------------------------------"));
        server.getConsoleCommandSource().sendMessage(LEGACY.deserialize("  &3X-SetSpawn &bv2.2 &aenabled! Enjoy spawning!"));
        server.getConsoleCommandSource().sendMessage(LEGACY.deserialize("  &fLanguage: &e" + language + " &f| Lobby: &e" + targetServer));
        server.getConsoleCommandSource().sendMessage(LEGACY.deserialize("  &fCommands: &e" + aliasServers.keySet() + " &fand &b/setlobby, &b/xssproxy"));
        server.getConsoleCommandSource().sendMessage(LEGACY.deserialize("&b----------------------------------------------"));

        // Check for updates
        new UpdateChecker(this).checkForUpdates();

        // 3. Initialize metrics
        this.metrics = metricsFactory.make(this, 30833);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (this.metrics != null) {
            this.metrics.shutdown();
        }
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) return;

        // Message from backend server
        if (event.getSource() instanceof ServerConnection) {
            ServerConnection connection = (ServerConnection) event.getSource();
            try {
                ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
                String subChannel = in.readUTF();
                if (subChannel.equals("SetLobbyServer")) {
                    String serverName = connection.getServerInfo().getName();
                    if (!this.targetServer.equals(serverName)) {
                        updateTargetServer(serverName);
                        logger.info("Target server dynamically updated to '{}' by backend server.", serverName);
                    }
                } else if (subChannel.equals("PlayerReady")) {
                    // Backend says player finished loading terrain, send coords immediately
                    if (event.getTarget() instanceof Player) {
                        Player player = (Player) event.getTarget();
                        player.getCurrentServer().ifPresent(serverConn -> {
                            if (serverConn.getServerInfo().getName().equalsIgnoreCase(targetServer)) {
                                
                                // Cancel fallback task
                                ScheduledTask task = pendingTeleports.remove(player.getUniqueId());
                                if (task != null) {
                                    task.cancel();
                                }
                                
                                sendGlobalLobbyTeleport(player, true);
                                if (debugEnabled) {
                                    logger.info("Received PlayerReady for {} - Snipping to spawn instantly.", player.getUsername());
                                }
                            }
                        });
                    }
                } else if (subChannel.equals("LocationResponse")) {
                     // Update the global lobby coordinates
                    this.lobbyWorld = in.readUTF();
                    this.lobbyX = in.readDouble();
                    this.lobbyY = in.readDouble();
                    this.lobbyZ = in.readDouble();
                    this.lobbyYaw = in.readFloat();
                    this.lobbyPitch = in.readFloat();
                    this.lobbyCoordsSet = true;
                    
                    saveLocationData();
                    logger.info("Global lobby coordinates updated and saved.");
                }
            } catch (Exception ignored) {}
        }
    }

    @Subscribe
    public void onServerConnected(com.velocitypowered.api.event.player.ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        if (!player.getCurrentServer().isPresent()) return;

        String serverName = player.getCurrentServer().get().getServerInfo().getName();

        // Auto-connect to target server on first join
        if (event.getPreviousServer() == null && connectOnFirstJoin && !serverName.equalsIgnoreCase(targetServer)) {
            server.getServer(targetServer).ifPresent(target -> {
                player.createConnectionRequest(target).connect();
                if (debugEnabled) {
                    logger.info("Auto-connecting {} to {} on first join.", player.getUsername(), targetServer);
                }
            });
            return;
        }

        if (!lobbyCoordsSet) return;

        if (serverName.equalsIgnoreCase(targetServer)) {
            // Determine delay: initial join vs server switch
            boolean isJoin = (event.getPreviousServer() == null);
            int delay = isJoin ? joinTeleportDelay : switchTeleportDelay;

            ScheduledTask oldTask = pendingTeleports.remove(player.getUniqueId());
            if (oldTask != null) {
                oldTask.cancel();
            }

            // Use configured delay as fallback (if Bukkit doesn't send PlayerReady)
            ScheduledTask task = server.getScheduler().buildTask(this, () -> {
                pendingTeleports.remove(player.getUniqueId());
                sendGlobalLobbyTeleport(player, isJoin);
            }).delay(delay, java.util.concurrent.TimeUnit.MILLISECONDS).schedule();

            pendingTeleports.put(player.getUniqueId(), task);
        }
    }

    @Subscribe
    public void onPlayerDisconnect(com.velocitypowered.api.event.connection.DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ScheduledTask task = pendingTeleports.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        cooldowns.remove(uuid);
        lastGlobalTeleport.remove(uuid);
    }

    private void sendGlobalLobbyTeleport(Player player, boolean silent) {
        if (!lobbyCoordsSet || lobbyWorld == null) return;
        
        // Anti-Double-Teleport Lock (500ms)
        long now = System.currentTimeMillis();
        long last = lastGlobalTeleport.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 500) {
            if (debugEnabled) {
                logger.info("Ignored redundant GlobalLobbyTeleport for {} (Last was {}ms ago)", player.getUsername(), (now - last));
            }
            return;
        }
        lastGlobalTeleport.put(player.getUniqueId(), now);

        player.getCurrentServer().ifPresent(serverConn -> {
            if (debugEnabled) {
                logger.info("Sending GlobalLobbyTeleport for player '{}' (Silent: {})", player.getUsername(), silent);
            }

            com.google.common.io.ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("GlobalLobbyTeleport");
            out.writeUTF(lobbyWorld);
            out.writeDouble(lobbyX);
            out.writeDouble(lobbyY);
            out.writeDouble(lobbyZ);
            out.writeFloat(lobbyYaw);
            out.writeFloat(lobbyPitch);
            out.writeBoolean(silent);

            serverConn.sendPluginMessage(CHANNEL, out.toByteArray());
        });
    }

    private void saveLocationData() {
        server.getScheduler().buildTask(this, () -> {
            try {
                Path dataFile = dataDirectory.resolve("data.yml");
                List<String> lines = new ArrayList<>();
                lines.add("target-server: \"" + targetServer + "\"");
                if (lobbyCoordsSet) {
                    lines.add("world: \"" + lobbyWorld + "\"");
                    lines.add("x: " + lobbyX);
                    lines.add("y: " + lobbyY);
                    lines.add("z: " + lobbyZ);
                    lines.add("yaw: " + lobbyYaw);
                    lines.add("pitch: " + lobbyPitch);
                }
                Files.write(dataFile, lines);
            } catch (IOException e) {
                logger.warn("Could not save data.yml: {}", e.getMessage());
            }
        }).schedule();
    }

    public void updateTargetServer(String newServer) {
        this.targetServer = newServer;
        saveLocationData();
    }

    private void loadData() {
        try {
            Path dataFile = dataDirectory.resolve("data.yml");
            if (Files.exists(dataFile)) {
                List<String> lines = Files.readAllLines(dataFile);
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    
                    String[] parts = trimmed.split(":", 2);
                    if (parts.length < 2) continue;
                    
                    String key = parts[0].trim();
                    String value = parts[1].trim().replaceAll("[\"']", "");
                    
                    switch (key) {
                        case "target-server": this.targetServer = value; break;
                        case "world": this.lobbyWorld = value; this.lobbyCoordsSet = true; break;
                        case "x": try { this.lobbyX = Double.parseDouble(value); } catch (Exception ignored) {} break;
                        case "y": try { this.lobbyY = Double.parseDouble(value); } catch (Exception ignored) {} break;
                        case "z": try { this.lobbyZ = Double.parseDouble(value); } catch (Exception ignored) {} break;
                        case "yaw": try { this.lobbyYaw = Float.parseFloat(value); } catch (Exception ignored) {} break;
                        case "pitch": try { this.lobbyPitch = Float.parseFloat(value); } catch (Exception ignored) {} break;
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to load data.yml: {}", e.getMessage());
        }
    }

    // =========================================================================
    // Configuration Loading
    // =========================================================================

    /**
     * Loads config.yml from the plugin data directory.
     * Supports config-code auto-update.
     */
    private void loadConfig(int currentCode) {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }

            Path configFile = dataDirectory.resolve("config.yml");

            if (!Files.exists(configFile)) {
                copyResource("/config.yml", configFile);
                logger.info("Created default config.yml");
            } else {
                // Check if update is needed
                if (currentCode < EXPECTED_CONFIG_CODE) {
                    Path backup = dataDirectory.resolve("config.yml.old");
                    Files.move(configFile, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    copyResource("/config.yml", configFile);
                    logger.info("Config outdated (code {} < {}). Backed up old config and created new one.",
                            currentCode, EXPECTED_CONFIG_CODE);
                }
            }

            parseConfig(configFile);

        } catch (IOException e) {
            logger.error("Failed to load config.yml, using defaults: {}", e.getMessage());
        }
    }

    /**
     * Loads messages from messages/<language>.yml.
     * Updates message files if config.yml's code is outdated.
     */
    private void loadMessages(int currentCode) {
        try {
            Path messagesDir = dataDirectory.resolve("messages");
            if (!Files.exists(messagesDir)) {
                Files.createDirectories(messagesDir);
            }

            // Always copy all default language files if they don't exist
            copyMessageFileIfMissing(messagesDir, "EN.yml");
            copyMessageFileIfMissing(messagesDir, "ES.yml");
            copyMessageFileIfMissing(messagesDir, "RU.yml");
            copyMessageFileIfMissing(messagesDir, "PT.yml");
            copyMessageFileIfMissing(messagesDir, "JA.yml");

            // Load the configured language
            Path langFile = messagesDir.resolve(language + ".yml");
            if (!Files.exists(langFile)) {
                logger.warn("Language file '{}' not found, falling back to EN.yml", language);
                langFile = messagesDir.resolve("EN.yml");
            }

            // Check if update is needed based on master config-code
            if (currentCode < EXPECTED_CONFIG_CODE) {
                String fileName = langFile.getFileName().toString();
                Path backup = messagesDir.resolve(fileName + ".old");
                Files.move(langFile, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                copyResource("/messages/" + fileName, langFile);
                logger.info("Messages file '{}' updated due to config-code change ({} -> {}).",
                        fileName, currentCode, EXPECTED_CONFIG_CODE);
            }

            parseMessages(langFile);

        } catch (IOException e) {
            logger.error("Failed to load messages, using defaults: {}", e.getMessage());
        }
    }

    private void copyMessageFileIfMissing(Path messagesDir, String fileName) throws IOException {
        Path target = messagesDir.resolve(fileName);
        if (!Files.exists(target)) {
            copyResource("/messages/" + fileName, target);
        }
    }

    private void copyResource(String resourcePath, Path target) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in != null) {
                Files.copy(in, target);
            }
        }
    }

    /**
     * Reads the config-code value from a YAML file.
     * Returns 0 if not found (treated as outdated).
     */
    private int readConfigCode(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                String trimmed = line.trim();
                // Match both "config-code: X" and "# config-code: X"
                if (trimmed.startsWith("config-code:") || trimmed.startsWith("# config-code:")) {
                    String[] parts = trimmed.split(":", 2);
                    if (parts.length > 1) {
                        return Integer.parseInt(parts[1].trim());
                    }
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    // =========================================================================
    // Simple YAML Parsers
    // =========================================================================

    /**
     * Parses config.yml for settings (server, aliases, cooldown, language).
     */
    private void parseConfig(Path configFile) throws IOException {
        List<String> lines = Files.readAllLines(configFile);
        Map<String, String> aliasMap = new LinkedHashMap<>();
        boolean inAliases = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            if (trimmed.startsWith("- ") && inAliases) {
                String value = trimmed.substring(2).trim().replaceAll("[\"']", "");
                if (!value.isEmpty()) {
                    if (value.contains(":")) {
                        String[] aliasParts = value.split(":", 2);
                        aliasMap.put(aliasParts[0].trim(), aliasParts[1].trim());
                    } else {
                        aliasMap.put(value, targetServer);
                    }
                }
                continue;
            } else if (!trimmed.startsWith("- ")) {
                inAliases = false;
            }

            if (trimmed.contains(":")) {
                String[] parts = trimmed.split(":", 2);
                String key = parts[0].trim();
                String value = parts.length > 1 ? parts[1].trim().replaceAll("[\"']", "") : "";

                switch (key) {
                    case "target-server":
                        this.targetServer = value;
                        break;
                    case "cooldown":
                        try { this.cooldownSeconds = Integer.parseInt(value); } catch (NumberFormatException ignored) {}
                        break;
                    case "teleport-delay-join":
                        try { this.joinTeleportDelay = Integer.parseInt(value); } catch (NumberFormatException ignored) {}
                        break;
                    case "teleport-delay-switch":
                        try { this.switchTeleportDelay = Integer.parseInt(value); } catch (NumberFormatException ignored) {}
                        break;
                    case "debug":
                        this.debugEnabled = value.equalsIgnoreCase("true");
                        break;
                    case "show-connecting-message":
                        this.showConnectingMessage = value.equalsIgnoreCase("true");
                        break;
                    case "connect-on-first-join":
                        this.connectOnFirstJoin = value.equalsIgnoreCase("true");
                        break;
                    case "prefix":
                        this.prefix = translateColors(value);
                        break;
                    case "language":
                        this.language = value;
                        break;
                    case "aliases":
                        inAliases = true;
                        aliasMap.clear();
                        break;
                }
            }
        }

        if (!aliasMap.isEmpty()) {
            this.aliasServers = aliasMap;
        }
    }

    /**
     * Parses a messages YAML file for all message keys.
     */
    private void parseMessages(Path messagesFile) throws IOException {
        List<String> lines = Files.readAllLines(messagesFile);

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            if (trimmed.contains(":")) {
                String[] parts = trimmed.split(":", 2);
                String key = parts[0].trim();
                String value = parts.length > 1 ? parts[1].trim() : "";

                // Remove surrounding quotes
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }

                value = translateColors(value);

                switch (key) {
                    case "prefix":            /* Ignored, moved to config.yml */ break;
                    case "connecting":        this.msgConnecting = value; break;
                    case "cooldown":          this.msgCooldown = value; break;
                    case "server-not-found":  this.msgServerNotFound = value; break;
                    case "player-only":       this.msgPlayerOnly = value; break;
                    case "already-connected": this.msgAlreadyConnected = value; break;
                    case "connection-failed": this.msgConnectionFailed = value; break;
                    case "lobby-set":         this.msgLobbySet = value; break;
                    case "no-permission":    this.msgNoPermission = value; break;
                    case "error-no-server":  this.msgErrorNoServer = value; break;
                    case "reload-success":   this.msgReloadSuccess = value; break;
                    case "reload-help":       this.msgReloadHelp = value; break;
                }
            }
        }
    }

    /**
     * Translates '&' color codes to '§' for legacy color support.
     */
    private String translateColors(String input) {
        if (input == null) return "";
        return input.replace('&', '§');
    }

    // =========================================================================
    // Public Accessors
    // =========================================================================

    public ProxyServer getServer() { return server; }
    public Logger getLogger() { return logger; }
    public String getTargetServer() { return targetServer; }
    public int getCooldownSeconds() { return cooldownSeconds; }
    public String getPrefix() { return prefix; }
    public boolean isShowConnectingMessage() { return showConnectingMessage; }
    public String getMsgConnecting() { return msgConnecting; }
    public String getMsgCooldown() { return msgCooldown; }
    public String getMsgServerNotFound() { return msgServerNotFound; }
    public String getMsgPlayerOnly() { return msgPlayerOnly; }
    public String getMsgAlreadyConnected() { return msgAlreadyConnected; }
    public String getMsgConnectionFailed() { return msgConnectionFailed; }
    public String getMsgLobbySet() { return msgLobbySet; }
    public String getMsgNoPermission() { return msgNoPermission; }
    public String getMsgErrorNoServer() { return msgErrorNoServer; }
    public String getMsgReloadSuccess() { return msgReloadSuccess; }
    public String getMsgReloadHelp() { return msgReloadHelp; }
    public Map<UUID, Long> getCooldowns() { return cooldowns; }
    public Map<String, String> getAliasServers() { return aliasServers; }

    public boolean isLobbyCoordsSet() { return lobbyCoordsSet; }
    public String getLobbyWorld() { return lobbyWorld; }
    public double getLobbyX() { return lobbyX; }
    public double getLobbyY() { return lobbyY; }
    public double getLobbyZ() { return lobbyZ; }
    public float getLobbyYaw() { return lobbyYaw; }
    public float getLobbyPitch() { return lobbyPitch; }

    // =========================================================================
    // Reload
    // =========================================================================

    /**
     * Reloads configuration, data, and messages without restarting the plugin.
     */
    public void reload() {
        try {
            Path configFile = dataDirectory.resolve("config.yml");
            parseConfig(configFile);

            loadData();

            Path messagesDir = dataDirectory.resolve("messages");
            if (!Files.exists(messagesDir)) {
                Files.createDirectories(messagesDir);
            }
            Path langFile = messagesDir.resolve(language + ".yml");
            if (!Files.exists(langFile)) {
                langFile = messagesDir.resolve("EN.yml");
            }
            parseMessages(langFile);

            logger.info("Configuration and messages reloaded.");
        } catch (Exception e) {
            logger.warn("Failed to reload: {}", e.getMessage());
        }
    }
}