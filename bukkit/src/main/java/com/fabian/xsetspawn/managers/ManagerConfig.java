package com.fabian.xsetspawn.managers;

import com.fabian.xsetspawn.XSetSpawn;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public class ManagerConfig {

    private final XSetSpawn plugin;

    // 1. Language & Updates
    public String language;
    public String prefix;
    public boolean perWorld;
    public boolean checkUpdates;
    public boolean metricsEnabled;
    public boolean debugEnabled;

    // 2. Permissions
    // Managed via Permission enum

    // 3. Teleport Mechanics
    public boolean cooldownEnabled;
    public int cooldownTime;
    public boolean delayEnabled;
    public int delayTime;
    public boolean delayCancelOnMove;
    public boolean combatCheckEnabled;
    public boolean fallingCheckEnabled;

    // 4. Economy
    public boolean economyEnabled;
    public double economyCost;

    // 5. Visual Effects (Particles, Sounds, Titles)
    public boolean particlesEnabled;
    public String particleType;
    public int particleAmount;
    public boolean soundsEnabled;
    public String spawnSound;
    public float soundVolume;
    public float soundPitch;
    public boolean countdownSoundsEnabled;
    public boolean fireworksEnabled;
    public String fireworksColor;
    public int fireworksPower;
    public boolean titlesEnabled;
    public int titleFadeIn;
    public int titleStay;
    public int titleFadeOut;
    public boolean actionbarEnabled;
    public boolean bossbarEnabled;
    public String bossbarColor;
    public String bossbarStyle;

    // 6. Protections & Extra
    public boolean protectionEnabled;
    public int protectionTime;
    public boolean voidTeleportEnabled;
    public int voidTeleportHeight;
    public boolean firstJoinSpawnEnabled;
    public boolean teleportOnFirstJoin;
    public boolean teleportOnDeath;
    public boolean smartRespawn;

    // 7. Holograms (Premium)
    public boolean hologramEnabled;
    public String hologramText;
    public double hologramHeightOffset;

    // 8. Storage Settings
    public String storageType;
    public String sqlHost;
    public int sqlPort;
    public String sqlDatabase;
    public String sqlUsername;
    public String sqlPassword;
    public String mongoUri;
    public String mongoDatabase;
    public String mongoCollection;

    // 9. Proxy Support
    public boolean proxyEnabled;
    public String proxyServer;
    public boolean proxySendOnStop;
    public List<String> proxyAliases;

    // 10. Named Spawns
    public boolean namedSpawns;

    // 11. Back Command
    public boolean backEnabled;
    public int backExpires; // in minutes, 0 = never

    public ManagerConfig(XSetSpawn plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        FileConfiguration config = plugin.getConfig();

        // Language & Updates
        this.language = config.getString("language", "EN");
        this.prefix = config.getString("prefix", "&8[&bX-SetSpawn&8]&r ");
        this.perWorld = config.getBoolean("per-world", false);
        this.checkUpdates = config.getBoolean("check-updates", true);
        this.metricsEnabled = config.getBoolean("metrics", true);
        this.debugEnabled = config.getBoolean("debug", false);

        // Teleport Mechanics
        this.cooldownEnabled = config.getBoolean("cooldown.enabled", true);
        this.cooldownTime = config.getInt("cooldown.time", 10);
        this.delayEnabled = config.getBoolean("delay.enabled", true);
        this.delayTime = config.getInt("delay.time", 3);
        this.delayCancelOnMove = config.getBoolean("delay.cancel-on-move", true);
        this.combatCheckEnabled = config.getBoolean("combat-check.enabled", false);
        this.fallingCheckEnabled = config.getBoolean("falling-check.enabled", true);

        // Economy
        this.economyEnabled = config.getBoolean("economy.enabled", false);
        this.economyCost = config.getDouble("economy.cost", 50.0);

        // Visual Effects
        this.particlesEnabled = config.getBoolean("particles.enabled", true);
        this.particleType = config.getString("particles.type", "VILLAGER_HAPPY");
        this.particleAmount = config.getInt("particles.amount", 5);
        this.soundsEnabled = config.getBoolean("sounds.enabled", true);
        this.spawnSound = config.getString("sounds.spawn-sound", "ENTITY_ENDERMAN_TELEPORT");
        this.soundVolume = (float) config.getDouble("sounds.volume", 1.0);
        this.soundPitch = (float) config.getDouble("sounds.pitch", 1.0);
        this.countdownSoundsEnabled = config.getBoolean("sounds.countdown-sounds", true);
        this.fireworksEnabled = config.getBoolean("fireworks.enabled", false);
        this.fireworksColor = config.getString("fireworks.color", "BLUE");
        this.fireworksPower = config.getInt("fireworks.power", 1);
        this.titlesEnabled = config.getBoolean("titles.enabled", true);
        this.titleFadeIn = config.getInt("titles.fade-in", 10);
        this.titleStay = config.getInt("titles.stay", 40);
        this.titleFadeOut = config.getInt("titles.fade-out", 10);
        this.actionbarEnabled = config.getBoolean("actionbar.enabled", true);
        this.bossbarEnabled = config.getBoolean("bossbar.enabled", false);
        this.bossbarColor = config.getString("bossbar.color", "BLUE");
        this.bossbarStyle = config.getString("bossbar.style", "SOLID");

        // Protections & Extra
        this.protectionEnabled = config.getBoolean("protection.enabled", true);
        this.protectionTime = config.getInt("protection.time", 3);
        this.voidTeleportEnabled = config.getBoolean("void-teleport.enabled", true);
        this.voidTeleportHeight = config.getInt("void-teleport.height", -64);
        this.firstJoinSpawnEnabled = config.getBoolean("first-join-spawn.enabled", true);
        this.teleportOnFirstJoin = config.getBoolean("events.on-first-join", true);
        this.teleportOnDeath = config.getBoolean("events.on-death", true);
        this.smartRespawn = config.getBoolean("events.smart-respawn", true);

        // Holograms
        this.hologramEnabled = config.getBoolean("holograms.enabled", false);
        this.hologramText = config.getString("holograms.text", "&aTeleporting in &e{0} &aseconds...");
        this.hologramHeightOffset = config.getDouble("holograms.height-offset", 2.5);

        // Storage
        this.storageType = config.getString("storage.type", "YAML");
        this.sqlHost = config.getString("storage.sql.host", "localhost");
        this.sqlPort = config.getInt("storage.sql.port", 3306);
        this.sqlDatabase = config.getString("storage.sql.database", "minecraft");
        this.sqlUsername = config.getString("storage.sql.username", "root");
        this.sqlPassword = config.getString("storage.sql.password", "");
        this.mongoUri = config.getString("storage.mongodb.uri", "mongodb://localhost:27017");
        this.mongoDatabase = config.getString("storage.mongodb.database", "minecraft");
        this.mongoCollection = config.getString("storage.mongodb.collection", "xsetspawn_spawns");

        // Proxy
        this.proxyEnabled = config.getBoolean("proxy-support.enabled", false);
        this.proxyServer = config.getString("proxy-support.server", "lobby");
        this.proxySendOnStop = config.getBoolean("proxy-support.send-on-stop", true);
        this.proxyAliases = config.getStringList("proxy-support.aliases");

        // Named Spawns
        this.namedSpawns = config.getBoolean("named-spawns", true);

        // Back Command
        this.backEnabled = config.getBoolean("back.enabled", true);
        this.backExpires = config.getInt("back.expires", 60);
    }

    public void reload() {
        load();
    }
}

