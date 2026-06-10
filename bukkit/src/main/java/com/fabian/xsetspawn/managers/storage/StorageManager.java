package com.fabian.xsetspawn.managers.storage;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.managers.ManagerConfig;
import com.fabian.xsetspawn.utils.DebugLogger;

/**
 * StorageManager - Responsible for selecting and initializing the storage backend.
 */
public class StorageManager {

    private final XSetSpawn plugin;
    private final SpawnStorage storage;

    public StorageManager(XSetSpawn plugin) {
        this.plugin = plugin;
        this.storage = initializeStorage();
    }

    private SpawnStorage initializeStorage() {
        ManagerConfig config = plugin.getManagerConfig();
        String type = config.storageType.toUpperCase();

        DebugLogger.debug("Storage", "Initializing storage backend: " + type);

        try {
            switch (type) {
                case "SQL":
                case "MYSQL":
                case "MARIADB":
                case "H2":
                    return new SqlStorage(plugin, 
                        type, 
                        config.sqlHost, 
                        config.sqlPort, 
                        config.sqlDatabase, 
                        config.sqlUsername, 
                        config.sqlPassword);
                
                case "MONGODB":
                case "MONGO":
                    return new MongoStorage(plugin, 
                        config.mongoUri, 
                        config.mongoDatabase, 
                        config.mongoCollection);

                case "YAML":
                default:
                    return new YamlStorage(plugin);
            }
        } catch (Exception e) {
            DebugLogger.debug("Storage", "Failed to initialize storage backend '" + type + "', falling back to YAML", e);
            plugin.logError("Failed to initialize storage backend '" + type + "'. Falling back to YAML.");
            e.printStackTrace();
            return new YamlStorage(plugin);
        }
    }

    public SpawnStorage getStorage() {
        return storage;
    }

    public void close() {
        DebugLogger.debug("Storage", "Closing storage backend...");
        if (storage != null) {
            storage.close();
        }
    }
}

