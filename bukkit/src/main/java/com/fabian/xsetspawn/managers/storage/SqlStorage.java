package com.fabian.xsetspawn.managers.storage;

import com.fabian.xsetspawn.XSetSpawn;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.sql.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SqlStorage - SQL implementation of SpawnStorage.
 * Supports H2 (local) and MySQL/MariaDB (remote).
 */
public class SqlStorage implements SpawnStorage {

    private final XSetSpawn plugin;
    private HikariDataSource dataSource;
    private final String tableName = "xsetspawn_spawns";
    /** true = H2 embedded, false = MySQL/MariaDB */
    private boolean isH2;

    public SqlStorage(XSetSpawn plugin, String type, String host, int port, String database, String username, String password) {
        this.plugin = plugin;
        this.isH2 = type.equalsIgnoreCase("H2");
        setupDataSource(type, host, port, database, username, password);
        createTable();
    }

    private void silenceHikariLogs() {
        // Disable JUL logs
        Logger.getLogger("com.zaxxer.hikari").setLevel(Level.OFF);
        Logger.getLogger("com.zaxxer.hikari.HikariDataSource").setLevel(Level.OFF);
        Logger.getLogger("com.zaxxer.hikari.pool.HikariPool").setLevel(Level.OFF);
        Logger.getLogger("com.zaxxer.hikari.pool.PoolBase").setLevel(Level.OFF);

        // Disable Log4j2 logs (Paper/Spigot 1.8.8+) via Reflection to avoid hard dependency missing
        try {
            Class<?> levelClass = Class.forName("org.apache.logging.log4j.Level");
            Object offLevel = levelClass.getField("OFF").get(null);

            Class<?> configuratorClass = Class.forName("org.apache.logging.log4j.core.config.Configurator");
            java.lang.reflect.Method setLevelMethod = configuratorClass.getMethod("setLevel", String.class, levelClass);

            setLevelMethod.invoke(null, "com.zaxxer.hikari", offLevel);
            setLevelMethod.invoke(null, "com.zaxxer.hikari.HikariDataSource", offLevel);
            setLevelMethod.invoke(null, "com.zaxxer.hikari.pool.HikariPool", offLevel);
            setLevelMethod.invoke(null, "com.zaxxer.hikari.pool.PoolBase", offLevel);
        } catch (Exception ignore) {}
    }

    private void setupDataSource(String type, String host, int port, String database, String username, String password) {
        silenceHikariLogs();

        plugin.log("&eConnecting to " + type + " database...");

        HikariConfig config = new HikariConfig();

        if (isH2) {
            config.setJdbcUrl("jdbc:h2:./plugins/X-SetSpawn/storage");
            config.setDriverClassName("org.h2.Driver");
        } else {
            // MySQL/MariaDB
            String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true", host, port, database);
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(username);
            config.setPassword(password);
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        }

        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(30000);
        config.setPoolName("XSetSpawnPool");

        this.dataSource = new HikariDataSource(config);
        plugin.log("&a" + type + " database connected and ready.");
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                "id VARCHAR(64) PRIMARY KEY, " +
                "world VARCHAR(64) NOT NULL, " +
                "x DOUBLE NOT NULL, " +
                "y DOUBLE NOT NULL, " +
                "z DOUBLE NOT NULL, " +
                "yaw FLOAT NOT NULL, " +
                "pitch FLOAT NOT NULL" +
                ");";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.logError("Could not create SQL table: " + e.getMessage());
        }
    }

    @Override
    public void save(String id, Location location) {
        try (Connection conn = dataSource.getConnection()) {
            if (isH2) {
                // H2 uses MERGE INTO ... KEY (id) syntax for upsert
                String sql = "MERGE INTO " + tableName + " (id, world, x, y, z, yaw, pitch) KEY (id) VALUES (?, ?, ?, ?, ?, ?, ?);";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, id);
                    pstmt.setString(2, location.getWorld().getName());
                    pstmt.setDouble(3, location.getX());
                    pstmt.setDouble(4, location.getY());
                    pstmt.setDouble(5, location.getZ());
                    pstmt.setFloat(6, location.getYaw());
                    pstmt.setFloat(7, location.getPitch());
                    pstmt.executeUpdate();
                }
            } else {
                // MySQL / MariaDB: INSERT ... ON DUPLICATE KEY UPDATE
                String sql = "INSERT INTO " + tableName + " (id, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?) " +
                             "ON DUPLICATE KEY UPDATE world=?, x=?, y=?, z=?, yaw=?, pitch=?;";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, id);
                    pstmt.setString(2, location.getWorld().getName());
                    pstmt.setDouble(3, location.getX());
                    pstmt.setDouble(4, location.getY());
                    pstmt.setDouble(5, location.getZ());
                    pstmt.setFloat(6, location.getYaw());
                    pstmt.setFloat(7, location.getPitch());
                    // Duplicate key values for UPDATE
                    pstmt.setString(8, location.getWorld().getName());
                    pstmt.setDouble(9, location.getX());
                    pstmt.setDouble(10, location.getY());
                    pstmt.setDouble(11, location.getZ());
                    pstmt.setFloat(12, location.getYaw());
                    pstmt.setFloat(13, location.getPitch());
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not save spawn to SQL: " + e.getMessage());
        }
    }


    @Override
    public Location load(String id) {
        String sql = "SELECT * FROM " + tableName + " WHERE id = ?;";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String worldName = rs.getString("world");
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) return null;

                    return new Location(world, 
                            rs.getDouble("x"), 
                            rs.getDouble("y"), 
                            rs.getDouble("z"), 
                            rs.getFloat("yaw"), 
                            rs.getFloat("pitch"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not load spawn from SQL: " + e.getMessage());
        }
        return null;
    }

    @Override
    public boolean isSet(String id) {
        String sql = "SELECT 1 FROM " + tableName + " WHERE id = ?;";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void remove(String id) {
        String sql = "DELETE FROM " + tableName + " WHERE id = ?;";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not remove spawn from SQL: " + e.getMessage());
        }
    }

    @Override
    public java.util.List<String> getAllSpawnIds() {
        java.util.List<String> ids = new java.util.ArrayList<>();
        String sql = "SELECT id FROM " + tableName + ";";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getString("id"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not list spawn IDs from SQL: " + e.getMessage());
        }
        return ids;
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            silenceHikariLogs();
            dataSource.close();
        }
    }
}

