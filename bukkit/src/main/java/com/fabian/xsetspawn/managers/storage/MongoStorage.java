package com.fabian.xsetspawn.managers.storage;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.utils.DebugLogger;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.mongodb.client.model.Filters.eq;

/**
 * MongoStorage - MongoDB implementation of SpawnStorage.
 * Saves spawn locations to a MongoDB collection.
 */
public class MongoStorage implements SpawnStorage {

    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> collection;
    private final XSetSpawn plugin;
    private volatile boolean ready = false;

    private static boolean logsSilenced = false;

    private void silenceMongoLogs() {
        if (logsSilenced) return;
        
        // Disable JUL logs explicitly for all sub-loggers (Fallback if Log4j2 isn't handling it natively)
        java.util.logging.Logger.getLogger("org.mongodb.driver").setLevel(java.util.logging.Level.SEVERE);
        java.util.logging.Logger.getLogger("org.mongodb.driver.client").setLevel(java.util.logging.Level.SEVERE);
        java.util.logging.Logger.getLogger("org.mongodb.driver.cluster").setLevel(java.util.logging.Level.SEVERE);
        java.util.logging.Logger.getLogger("org.mongodb.driver.connection").setLevel(java.util.logging.Level.SEVERE);

        // Disable Log4j2 logs (Paper/Spigot 1.8.8+) via Reflection
        try {
            Class<?> levelClass = Class.forName("org.apache.logging.log4j.Level");
            Object offLevel = levelClass.getField("OFF").get(null);

            Class<?> configuratorClass = Class.forName("org.apache.logging.log4j.core.config.Configurator");
            java.lang.reflect.Method setLevelMethod = configuratorClass.getMethod("setLevel", String.class, levelClass);

            setLevelMethod.invoke(null, "org.mongodb.driver", offLevel);
            setLevelMethod.invoke(null, "org.mongodb.driver.client", offLevel);
            setLevelMethod.invoke(null, "org.mongodb.driver.cluster", offLevel);
            setLevelMethod.invoke(null, "org.mongodb.driver.connection", offLevel);
        } catch (Exception ignore) {}
        
        logsSilenced = true;
    }

    public MongoStorage(XSetSpawn plugin, String uri, String dbName, String collectionName) {
        this.plugin = plugin;
        silenceMongoLogs();
        DebugLogger.debug("Storage", "Initializing MongoDB storage (db=" + dbName + ", collection=" + collectionName + ")...");

        // Fix for Minecraft PluginClassLoader isolation issues with MongoDB ServiceLoader
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        com.fabian.xsetspawn.utils.SchedulerUtil.runAsync(plugin, () -> {
            try {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
                plugin.logInfo("Connecting to MONGODB database...");
                this.mongoClient = MongoClients.create(uri);
                this.database = mongoClient.getDatabase(dbName);
                this.collection = database.getCollection(collectionName);
                
                // Ping to verify connection and auth immediately, instead of lazy connecting.
                this.database.runCommand(new Document("ping", 1));
                
                DebugLogger.debug("Storage", "MongoDB connected and ready");
                plugin.logInfo("MONGODB database connected and ready.");
                ready = true;

                // Trigger cache load once connected
                if (plugin.getSpawnManager() != null) {
                    plugin.getSpawnManager().loadCachesAsync();
                }
            } catch (Exception e) {
                DebugLogger.debug("Storage", "Failed to initialize MongoDB connection", e);
                plugin.logError("Failed to initialize MongoDB connection: " + e.getMessage());
                e.printStackTrace();
            } finally {
                Thread.currentThread().setContextClassLoader(originalLoader);
            }
        });
    }

    @Override
    public CompletableFuture<Void> save(String id, Location location) {
        DebugLogger.debug("Storage", "MongoDB save: " + id);
        return CompletableFuture.supplyAsync(() -> {
            if (!ready || collection == null) return null;
            try {
                Document doc = new Document("_id", id)
                        .append("world", location.getWorld().getName())
                        .append("x", location.getX())
                        .append("y", location.getY())
                        .append("z", location.getZ())
                        .append("yaw", (double) location.getYaw())
                        .append("pitch", (double) location.getPitch());

                collection.replaceOne(eq("_id", id), doc, new ReplaceOptions().upsert(true));
            } catch (Exception e) {
                plugin.logError("Error saving to MongoDB: " + e.getMessage());
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<Location> load(String id) {
        DebugLogger.debug("Storage", "MongoDB load: " + id);
        return CompletableFuture.supplyAsync(() -> {
            if (!ready || collection == null) return null;
            try {
                Document doc = collection.find(eq("_id", id)).first();
                if (doc == null) return null;

                String worldName = doc.getString("world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) return null;

                return new Location(world,
                        doc.getDouble("x"),
                        doc.getDouble("y"),
                        doc.getDouble("z"),
                        doc.getDouble("yaw").floatValue(),
                        doc.getDouble("pitch").floatValue());
            } catch (Exception e) {
                plugin.logError("Error loading from MongoDB: " + e.getMessage());
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> isSet(String id) {
        return CompletableFuture.supplyAsync(() -> {
            if (!ready || collection == null) return false;
            try {
                return collection.find(eq("_id", id)).first() != null;
            } catch (Exception e) {
                return false;
            }
        });
    }

    @Override
    public CompletableFuture<Void> remove(String id) {
        return CompletableFuture.supplyAsync(() -> {
            if (!ready || collection == null) return null;
            try {
                collection.deleteOne(eq("_id", id));
            } catch (Exception e) {
                plugin.logError("Error removing from MongoDB: " + e.getMessage());
            }
            return null;
        });
    }

    @Override
    public CompletableFuture<List<String>> getAllSpawnIds() {
        return CompletableFuture.supplyAsync(() -> {
            List<String> ids = new ArrayList<>();
            if (!ready || collection == null) return ids;
            try (MongoCursor<Document> cursor = collection.find().iterator()) {
                while (cursor.hasNext()) {
                    Document doc = cursor.next();
                    ids.add(doc.getString("_id"));
                }
            } catch (Exception e) {
                plugin.logError("Error fetching from MongoDB: " + e.getMessage());
            }
            return ids;
        });
    }

    @Override
    public CompletableFuture<java.util.Map<String, Location>> loadAll() {
        DebugLogger.debug("Storage", "MongoDB loadAll: loading all spawns...");
        return CompletableFuture.supplyAsync(() -> {
            java.util.Map<String, Location> map = new java.util.HashMap<>();
            if (!ready || collection == null) return map;
            try (MongoCursor<Document> cursor = collection.find().iterator()) {
                while (cursor.hasNext()) {
                    Document doc = cursor.next();
                    String worldName = doc.getString("world");
                    World world = Bukkit.getWorld(worldName);
                    if (world != null) {
                        Location loc = new Location(world,
                                doc.getDouble("x"),
                                doc.getDouble("y"),
                                doc.getDouble("z"),
                                doc.getDouble("yaw").floatValue(),
                                doc.getDouble("pitch").floatValue());
                        map.put(doc.getString("_id"), loc);
                    }
                }
            } catch (Exception e) {
                plugin.logError("Error loading all from MongoDB: " + e.getMessage());
            }
            return map;
        });
    }

    @Override
    public void close() {
        DebugLogger.debug("Storage", "Closing MongoDB connection...");
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
}

