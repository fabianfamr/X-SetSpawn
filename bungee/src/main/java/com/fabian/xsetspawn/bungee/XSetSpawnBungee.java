package com.fabian.xsetspawn.bungee;

import com.fabian.xsetspawn.bungee.commands.HubCommand;
import com.fabian.xsetspawn.bungee.commands.ReloadCommand;
import com.fabian.xsetspawn.bungee.commands.SetLobbyCommand;
import com.fabian.xsetspawn.bungee.utils.UpdateChecker;
import com.fabian.xsetspawn.bungee.metrics.Metrics;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.event.EventHandler;
import com.google.common.io.ByteStreams;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.scheduler.ScheduledTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * X-SetSpawn BungeeCord Plugin
 * Provides /hub, /lobby, and /spawn commands on the BungeeCord proxy
 * to send players to a configured backend server with multi-lobby random selection.
 */
public class XSetSpawnBungee extends Plugin implements Listener {

    // Plugin messaging channel for Bukkit-Proxy communication
    public static final String CHANNEL = "xsetspawn:main";

    // Expected config-code version. If the user's file has a lower value, it gets rebuilt.
    private static final int EXPECTED_CONFIG_CODE = 9;

    // Config values
    private List<String> lobbyServers = new ArrayList<>(Arrays.asList("lobby"));
    private List<String> aliases = new ArrayList<>(Arrays.asList("hub", "lobby"));
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
    private String prefix = "\u00a78[\u00a7bX-SetSpawn\u00a78]\u00a7r ";
    private String msgConnecting = "\u00a7aSending you to \u00a7e{server}\u00a7a...";
    private String msgCooldown = "\u00a7cPlease wait \u00a7e{time}s \u00a7cbefore using this again.";
    private String msgServerNotFound = "\u00a7cServer '{server}' not found!";
    private String msgPlayerOnly = "\u00a7cThis command can only be used by players.";
    private String msgAlreadyConnected = "\u00a7eYou are already connected to this server!";
    private String msgLobbySet = "\u00a7aGlobal lobby server has been set to: \u00a7e{server}";
    private String msgNoPermission = "\u00a7cYou don't have permission to use this command.";
    private String msgErrorNoServer = "\u00a7cError: Could not determine current server.";
    private String msgConnectionFailed = "\u00a7cCould not connect to \u00a7e{server}\u00a7c. Please try again.";
    private String msgReloadSuccess = "\u00a7aConfiguration and messages reloaded successfully!";
    private String msgReloadHelp = "\u00a7e/xssproxy reload \u00a77- \u00a7fReload the plugin configuration and messages";

    // Cooldowns and pending tasks
    private final Map<UUID, Long> cooldowns = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> pendingTeleports = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> lastGlobalTeleport = new java.util.concurrent.ConcurrentHashMap<>();

    // Metrics
    private Metrics metrics;

    @Override
    public void onEnable() {
        // 1. Read the current config-code from config.yml BEFORE loading anything
        File configFile = new File(getDataFolder(), "config.yml");
        int currentVersion = readConfigCode(configFile.toPath());

        // 2. Load configuration and messages using that master version
        loadConfig(currentVersion);
        loadData();
        loadMessages(currentVersion);

        // Register commands - SetLobby first for priority
        getProxy().getPluginManager().registerCommand(this, new SetLobbyCommand(this));
        getProxy().getPluginManager().registerCommand(this, new ReloadCommand(this));

        for (String alias : aliases) {
            getProxy().getPluginManager().registerCommand(this, new HubCommand(this, alias));
        }

        getLogger().info(translateColors("&b----------------------------------------------"));
        getLogger().info(translateColors("  &3X-SetSpawn &bv" + getDescription().getVersion() + " &aenabled! Enjoy spawning!"));
        getLogger().info(translateColors("  &fLanguage: &e" + language + " &f| Lobbies: &e" + lobbyServers));
        getLogger().info(translateColors("  &fCommands: &e" + aliases + " &fand &b/setlobby, &b/xssproxy"));
        getLogger().info(translateColors("&b----------------------------------------------"));

        // Register plugin messaging channel
        getProxy().registerChannel(CHANNEL);
        getProxy().getPluginManager().registerListener(this, this);

        // Check for updates
        new UpdateChecker(this).checkForUpdates();

        // 3. Initialize metrics
        this.metrics = new Metrics(this, 30831);
    }

    @Override
    public void onDisable() {
        if (metrics != null) {
            metrics.shutdown();
        }
        getLogger().info("X-SetSpawn v" + getDescription().getVersion() + " disabled!");
    }

    // =========================================================================
    // Multi-Lobby Methods
    // =========================================================================

    /**
     * Returns a random ONLINE lobby server from the configured list.
     * If only 1 server is online, returns that one directly.
     * If multiple are online, picks a random one.
     */
    public String getRandomLobbyServer() {
        if (lobbyServers.isEmpty()) return null;

        // Filter to only servers that are registered and online in the proxy
        List<String> onlineLobbies = new ArrayList<>();
        for (String name : lobbyServers) {
            ServerInfo info = getProxy().getServerInfo(name);
            if (info != null && !info.getPlayers().isEmpty()) {
                onlineLobbies.add(name);
            }
        }

        // Fallback: if no lobbies have players, try ones that are at least registered
        if (onlineLobbies.isEmpty()) {
            for (String name : lobbyServers) {
                ServerInfo info = getProxy().getServerInfo(name);
                if (info != null) {
                    onlineLobbies.add(name);
                }
            }
        }

        if (onlineLobbies.isEmpty()) return null;
        if (onlineLobbies.size() == 1) return onlineLobbies.get(0);
        return onlineLobbies.get(ThreadLocalRandom.current().nextInt(onlineLobbies.size()));
    }

    /**
     * Checks if the given server name is one of the configured lobby servers.
     */
    public boolean isLobbyServer(String serverName) {
        for (String lobby : lobbyServers) {
            if (lobby.equalsIgnoreCase(serverName)) return true;
        }
        return false;
    }

    /**
     * Gets an unmodifiable view of the lobby servers list.
     */
    public List<String> getLobbyServers() {
        return Collections.unmodifiableList(lobbyServers);
    }

    /**
     * Adds a server to the lobby servers list if not already present.
     * Moves it to the front (primary) position.
     */
    public void addLobbyServer(String serverName) {
        lobbyServers.removeIf(s -> s.equalsIgnoreCase(serverName));
        lobbyServers.add(0, serverName);
        saveLocationData();
    }

    /**
     * Gets the primary (first) lobby server name.
     */
    public String getPrimaryLobby() {
        return lobbyServers.isEmpty() ? null : lobbyServers.get(0);
    }

    // =========================================================================
    // Event Handlers
    // =========================================================================

    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getTag().equals(CHANNEL)) return;

        if (event.getSender() instanceof Server) {
            Server server = (Server) event.getSender();
            try {
                ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
                String subChannel = in.readUTF();

                if (subChannel.equals("SetLobbyServer")) {
                    String serverName = server.getInfo().getName();
                    if (!isLobbyServer(serverName)) {
                        addLobbyServer(serverName);
                        getLogger().info("Lobby server dynamically added: '" + serverName + "' by backend server.");
                    }
                } else if (subChannel.equals("PlayerReady")) {
                    // Backend says player finished loading terrain, send coords immediately
                    if (event.getReceiver() instanceof ProxiedPlayer) {
                        ProxiedPlayer player = (ProxiedPlayer) event.getReceiver();
                        if (player.getServer() != null && isLobbyServer(player.getServer().getInfo().getName())) {
                            
                            // Cancel fallback task if it exists
                            ScheduledTask task = pendingTeleports.remove(player.getUniqueId());
                            if (task != null) {
                                task.cancel();
                            }
                            
                            sendGlobalLobbyTeleport(player, true);
                            if (debugEnabled) {
                                getLogger().info("Received PlayerReady for " + player.getName() + " - Snipping to spawn instantly.");
                            }
                        }
                    }
                } else if (subChannel.equals("LocationResponse")) {
                    // Update the global lobby coordinates
                    String world = in.readUTF();
                    double x = in.readDouble();
                    double y = in.readDouble();
                    double z = in.readDouble();
                    float yaw = in.readFloat();
                    float pitch = in.readFloat();
                    
                    this.lobbyWorld = world;
                    this.lobbyX = x;
                    this.lobbyY = y;
                    this.lobbyZ = z;
                    this.lobbyYaw = yaw;
                    this.lobbyPitch = pitch;
                    this.lobbyCoordsSet = true;
                    
                    saveLocationData();
                    getLogger().info("Global lobby coordinates updated and saved.");
                }
            } catch (Exception ignored) {}
        }
    }

    @EventHandler
    public void onServerSwitch(ServerSwitchEvent event) {
        ProxiedPlayer player = event.getPlayer();
        if (player.getServer() == null) return;
        
        String serverName = player.getServer().getInfo().getName();
        
        // Auto-connect to a random lobby on first proxy join
        if (event.getFrom() == null && connectOnFirstJoin && !isLobbyServer(serverName)) {
            String lobbyName = getRandomLobbyServer();
            if (lobbyName != null) {
                ServerInfo target = getProxy().getServerInfo(lobbyName);
                if (target != null) {
                    player.connect(target);
                    if (debugEnabled) {
                        getLogger().info("Auto-connecting " + player.getName() + " to " + lobbyName + " on first join.");
                    }
                }
            }
            return;
        }
        
        if (!lobbyCoordsSet) return;
        
        // Trigger on ANY lobby server
        if (isLobbyServer(serverName)) {
            // Determine delay: initial join vs server switch
            int delay = (event.getFrom() == null) ? joinTeleportDelay : switchTeleportDelay;
            
            // Cancel any old task to prevent memory leaks and zombie teleports
            ScheduledTask oldTask = pendingTeleports.remove(player.getUniqueId());
            if (oldTask != null) {
                oldTask.cancel();
            }

            // Use configured delay to ensure the channel is fully open and backend is ready (Fallback)
            ScheduledTask task = getProxy().getScheduler().schedule(this, () -> {
                pendingTeleports.remove(player.getUniqueId());
                boolean isJoin = (event.getFrom() == null);
                sendGlobalLobbyTeleport(player, isJoin);
            }, delay, java.util.concurrent.TimeUnit.MILLISECONDS);
            
            pendingTeleports.put(player.getUniqueId(), task);
        }
    }

    @EventHandler
    public void onPlayerDisconnect(net.md_5.bungee.api.event.PlayerDisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ScheduledTask task = pendingTeleports.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        cooldowns.remove(uuid);
        lastGlobalTeleport.remove(uuid);
    }

    private void sendGlobalLobbyTeleport(ProxiedPlayer player, boolean silent) {
        if (!lobbyCoordsSet || player.getServer() == null) return;
        
        // Anti-Double-Teleport Lock (500ms)
        long now = System.currentTimeMillis();
        long last = lastGlobalTeleport.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 500) {
            if (debugEnabled) {
                getLogger().info("Ignored redundant GlobalLobbyTeleport for " + player.getName() + " (Last was " + (now - last) + "ms ago)");
            }
            return;
        }
        lastGlobalTeleport.put(player.getUniqueId(), now);

        if (debugEnabled) {
            getLogger().info("Sending GlobalLobbyTeleport for player '" + player.getName() + "' (Silent: " + silent + ")");
        }

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("GlobalLobbyTeleport");
        out.writeUTF(lobbyWorld);
        out.writeDouble(lobbyX);
        out.writeDouble(lobbyY);
        out.writeDouble(lobbyZ);
        out.writeFloat(lobbyYaw);
        out.writeFloat(lobbyPitch);
        out.writeBoolean(silent);

        player.getServer().sendData(CHANNEL, out.toByteArray());
    }

    private void saveLocationData() {
        getProxy().getScheduler().runAsync(this, () -> {
            try {
                File dataFile = new File(getDataFolder(), "data.yml");
                List<String> lines = new ArrayList<>();
                lines.add("lobby-servers:");
                for (String s : lobbyServers) {
                    lines.add("  - \"" + s + "\"");
                }
                if (lobbyCoordsSet) {
                    lines.add("world: \"" + lobbyWorld + "\"");
                    lines.add("x: " + lobbyX);
                    lines.add("y: " + lobbyY);
                    lines.add("z: " + lobbyZ);
                    lines.add("yaw: " + lobbyYaw);
                    lines.add("pitch: " + lobbyPitch);
                }
                Files.write(dataFile.toPath(), lines);
            } catch (IOException e) {
                getLogger().warning("Could not save data.yml: " + e.getMessage());
            }
        });
    }

    private void loadData() {
        try {
            File dataFile = new File(getDataFolder(), "data.yml");
            if (dataFile.exists()) {
                List<String> lines = Files.readAllLines(dataFile.toPath());
                boolean inLobbyServers = false;
                for (String line : lines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    String[] parts = trimmed.split(":", 2);
                    if (parts.length < 2) continue;
                    
                    String key = parts[0].trim();
                    String value = parts[1].trim().replaceAll("[\"']", "");
                    
                    if (key.equals("lobby-servers")) {
                        inLobbyServers = true;
                        continue;
                    }
                    if (inLobbyServers) {
                        if (trimmed.startsWith("- ")) {
                            String serverName = trimmed.substring(2).trim().replaceAll("[\"']", "");
                            if (!serverName.isEmpty() && !lobbyServers.contains(serverName)) {
                                lobbyServers.add(serverName);
                            }
                            continue;
                        } else {
                            inLobbyServers = false;
                        }
                    }

                    switch (key) {
                        case "target-server":
                            // Legacy support: if old data.yml has target-server, add as lobby
                            if (!lobbyServers.contains(value)) {
                                lobbyServers.add(0, value);
                            }
                            break;
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
            getLogger().warning("Failed to load data.yml: " + e.getMessage());
        }
    }

    // =========================================================================
    // Configuration Loading
    // =========================================================================

    /**
     * Loads config.yml from the plugin data folder.
     * Supports config-code auto-update.
     */
    private void loadConfig(int currentCode) {
        try {
            if (!getDataFolder().exists()) {
                getDataFolder().mkdirs();
            }

            File configFile = new File(getDataFolder(), "config.yml");
            Path configPath = configFile.toPath();

            if (!configFile.exists()) {
                copyResource("config.yml", configPath);
                getLogger().info("Created default config.yml");
            } else {
                // Check if update is needed
                if (currentCode < EXPECTED_CONFIG_CODE) {
                    Path backup = new File(getDataFolder(), "config.yml.old").toPath();
                    Files.move(configPath, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    copyResource("config.yml", configPath);
                    getLogger().info("Config outdated (code " + currentCode + " < " + EXPECTED_CONFIG_CODE + "). Backed up old config and created new one.");
                }
            }

            parseConfig(configPath);

        } catch (IOException e) {
            getLogger().warning("Failed to load config.yml, using defaults: " + e.getMessage());
        }
    }

    /**
     * Loads messages from messages/<language>.yml.
     * Updates message files if config.yml's code is outdated.
     */
    private void loadMessages(int currentCode) {
        try {
            File messagesDirBase = new File(getDataFolder(), "messages");
            if (!messagesDirBase.exists()) {
                messagesDirBase.mkdirs();
            }

            // Always copy all default language files if they don't exist
            copyMessageFileIfMissing(messagesDirBase, "EN.yml");
            copyMessageFileIfMissing(messagesDirBase, "ES.yml");
            copyMessageFileIfMissing(messagesDirBase, "RU.yml");
            copyMessageFileIfMissing(messagesDirBase, "PT.yml");
            copyMessageFileIfMissing(messagesDirBase, "JA.yml");

            // Load the configured language
            File langFileBase = new File(messagesDirBase, language + ".yml");
            if (!langFileBase.exists()) {
                getLogger().warning("Language file '" + language + "' not found, falling back to EN.yml");
                langFileBase = new File(messagesDirBase, "EN.yml");
            }
            Path langFile = langFileBase.toPath();

            // Check if update is needed based on master config-code
            if (currentCode < EXPECTED_CONFIG_CODE) {
                String fileName = langFile.getFileName().toString();
                Path backup = new File(messagesDirBase, fileName + ".old").toPath();
                Files.move(langFile, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                copyResource("messages/" + fileName, langFile);
                getLogger().info("Messages file '" + fileName + "' updated due to config-code change (" + currentCode + " -> " + EXPECTED_CONFIG_CODE + ").");
            }

            parseMessages(langFile);

        } catch (IOException e) {
            getLogger().warning("Failed to load messages, using defaults: " + e.getMessage());
        }
    }

    private void copyMessageFileIfMissing(File messagesDir, String fileName) throws IOException {
        File targetBase = new File(messagesDir, fileName);
        if (!targetBase.exists()) {
            copyResource("messages/" + fileName, targetBase.toPath());
        }
    }

    private void copyResource(String resourcePath, Path target) throws IOException {
        try (InputStream in = getResourceAsStream(resourcePath)) {
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
     * Parses config.yml for settings (lobby-servers, aliases, cooldown, language).
     */
    private void parseConfig(Path configFile) throws IOException {
        List<String> lines = Files.readAllLines(configFile);
        List<String> lobbyList = new ArrayList<>();
        List<String> aliasList = new ArrayList<>();
        boolean inLobbyServers = false;
        boolean inAliases = false;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            // Handle list items (lobby-servers and aliases)
            if (trimmed.startsWith("- ") && inLobbyServers) {
                String value = trimmed.substring(2).trim().replaceAll("[\"']", "");
                if (!value.isEmpty()) {
                    lobbyList.add(value);
                }
                continue;
            }
            if (trimmed.startsWith("- ") && inAliases) {
                String value = trimmed.substring(2).trim().replaceAll("[\"']", "");
                if (!value.isEmpty()) {
                    aliasList.add(value);
                }
                continue;
            }
            if (!trimmed.startsWith("- ")) {
                inLobbyServers = false;
                inAliases = false;
            }

            if (trimmed.contains(":")) {
                String[] parts = trimmed.split(":", 2);
                String key = parts[0].trim();
                String value = parts.length > 1 ? parts[1].trim().replaceAll("[\"']", "") : "";

                switch (key) {
                    case "lobby-servers":
                        inLobbyServers = true;
                        lobbyList.clear();
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
                        aliasList.clear();
                        break;
                    // Legacy key: ignore but don't crash
                    case "target-server":
                        break;
                }
            }
        }

        if (!lobbyList.isEmpty()) {
            this.lobbyServers = lobbyList;
        }
        if (!aliasList.isEmpty()) {
            this.aliases = aliasList;
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
     * Translates '&' color codes to Minecraft color codes.
     */
    private String translateColors(String input) {
        if (input == null) return "";
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    // =========================================================================
    // Public Accessors
    // =========================================================================

    public int getCooldownSeconds() { return cooldownSeconds; }
    public String getPrefix() { return prefix; }
    public boolean isShowConnectingMessage() { return showConnectingMessage; }
    public String getMsgConnecting() { return msgConnecting; }
    public String getMsgCooldown() { return msgCooldown; }
    public String getMsgServerNotFound() { return msgServerNotFound; }
    public String getMsgPlayerOnly() { return msgPlayerOnly; }
    public String getMsgAlreadyConnected() { return msgAlreadyConnected; }
    public String getMsgLobbySet() { return msgLobbySet; }
    public String getMsgNoPermission() { return msgNoPermission; }
    public String getMsgErrorNoServer() { return msgErrorNoServer; }
    public String getMsgConnectionFailed() { return msgConnectionFailed; }
    public String getMsgReloadSuccess() { return msgReloadSuccess; }
    public String getMsgReloadHelp() { return msgReloadHelp; }
    public Map<UUID, Long> getCooldowns() { return cooldowns; }

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
            File configFile = new File(getDataFolder(), "config.yml");
            parseConfig(configFile.toPath());

            loadData();

            File messagesDirBase = new File(getDataFolder(), "messages");
            if (!messagesDirBase.exists()) {
                messagesDirBase.mkdirs();
            }
            File langFileBase = new File(messagesDirBase, language + ".yml");
            if (!langFileBase.exists()) {
                langFileBase = new File(messagesDirBase, "EN.yml");
            }
            parseMessages(langFileBase.toPath());

            getLogger().info("Configuration and messages reloaded.");
        } catch (Exception e) {
            getLogger().warning("Failed to reload: " + e.getMessage());
        }
    }
}
