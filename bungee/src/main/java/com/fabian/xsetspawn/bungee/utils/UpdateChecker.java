package com.fabian.xsetspawn.bungee.utils;

import com.fabian.xsetspawn.bungee.XSetSpawnBungee;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {
    private final XSetSpawnBungee plugin;
    private final int resourceId = 132280;

    public UpdateChecker(XSetSpawnBungee plugin) {
        this.plugin = plugin;
    }

    public void checkForUpdates() {
        plugin.getProxy().getScheduler().runAsync(plugin, this::check);
    }

    private void check() {
        try {
            String currentVersion = plugin.getDescription().getVersion();

            URL url = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + resourceId);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Fabian/X-SetSpawn-Bungee/" + currentVersion);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String latestVersion = reader.readLine();
            reader.close();

            if (latestVersion != null && isNewer(currentVersion, latestVersion)) {
                plugin.getLogger().warning("A new update for X-SetSpawn is available!");
                plugin.getLogger().warning("Current version: " + currentVersion + " | Latest version: " + latestVersion);
                plugin.getLogger().warning("Download it here: https://www.spigotmc.org/resources/" + resourceId + "/");
            } else {
                plugin.getLogger().info("X-SetSpawn is up to date!");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not check for updates: " + e.getMessage());
        }
    }

    private boolean isNewer(String current, String latest) {
        String[] currentParts = current.replace("v", "").split("\\.");
        String[] latestParts = latest.replace("v", "").split("\\.");
        int length = Math.max(currentParts.length, latestParts.length);
        for (int i = 0; i < length; i++) {
            int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
            int latestPart = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
            if (latestPart > currentPart) return true;
            if (latestPart < currentPart) return false;
        }
        return false;
    }
}
