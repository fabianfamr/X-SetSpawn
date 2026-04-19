package com.fabian.xsetspawn.managers.storage;

import org.bukkit.Location;

/**
 * SpawnStorage - Interface for various storage backends.
 * Allows switching between YAML, SQL, and MongoDB.
 */
public interface SpawnStorage {

    /**
     * Saves a spawn location.
     * @param id The identifier for the spawn (e.g., "global", "world_name", "first-join").
     * @param location The location to save.
     */
    void save(String id, Location location);

    /**
     * Loads a spawn location.
     * @param id The identifier for the spawn.
     * @return The location, or null if not found.
     */
    Location load(String id);

    /**
     * Checks if a spawn location is set.
     * @param id The identifier for the spawn.
     * @return true if set, false otherwise.
     */
    boolean isSet(String id);

    /**
     * Removes a spawn location by its ID.
     * @param id The identifier for the spawn to remove.
     */
    void remove(String id);

    /**
     * Returns a list of all spawn IDs currently saved.
     * Used for TabCompleter and listing Named Spawns.
     * @return List of spawn IDs (e.g. "spawn", "spawn-world", "spawn-custom-vip").
     */
    java.util.List<String> getAllSpawnIds();

    /**
     * Disconnects from the storage backend (if needed).
     */
    void close();
}

