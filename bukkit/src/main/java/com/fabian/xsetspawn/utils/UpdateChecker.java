package com.fabian.xsetspawn.utils;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.managers.LanguageManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateChecker {

    private final XSetSpawn plugin;
    private final int resourceId;
    private String latestVersion;
    private boolean updateAvailable;

    public UpdateChecker(XSetSpawn plugin) {
        this.plugin = plugin;
        this.resourceId = 132280; // Spigot Resource ID for X-SetSpawn
        this.updateAvailable = false;
    }

    public void checkForUpdates() {
        checkForUpdates(null);
    }

    public void checkForUpdates(CommandSender sender) {
        SchedulerUtil.runAsyncDelayed(plugin, () -> {
            try {
                String currentVersion = plugin.getDescription().getVersion();
                
                // Spigot API for resource versions
                URL url = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + resourceId);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "Fabian/X-SetSpawn/" + currentVersion);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                String version;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    version = reader.readLine();
                }

                this.latestVersion = version;
                LanguageManager lang = plugin.getLanguageManager();

                if (latestVersion != null && isNewer(currentVersion, latestVersion)) {
                    this.updateAvailable = true;

                    if (sender != null) {
                        sender.sendMessage(lang.getMessage("update-available", currentVersion, latestVersion));
                        sender.sendMessage(lang.getMessage("update-download", getDownloadUrl()));
                    } else {
                        Bukkit.getConsoleSender()
                                .sendMessage(lang.getMessage("update-available", currentVersion, latestVersion));
                        Bukkit.getConsoleSender().sendMessage(lang.getMessage("update-download", getDownloadUrl()));
                    }
                } else {
                    if (sender != null) {
                        sender.sendMessage(lang.getMessage("update-current"));
                    } else {
                        Bukkit.getConsoleSender().sendMessage(lang.getMessage("update-current"));
                    }
                }

            } catch (Exception e) {
                if (sender != null) {
                    sender.sendMessage(plugin.getLanguageManager().getMessage("update-error"));
                } else {
                    Bukkit.getConsoleSender().sendMessage(plugin.getLanguageManager().getMessage("update-error"));
                }
            }
        }, 0L);
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getDownloadUrl() {
        return "https://www.spigotmc.org/resources/" + resourceId + "/";
    }

    private boolean isNewer(String current, String latest) {
        try {
            String[] currentParts = current.replace("v", "").split("[\\.-]");
            String[] latestParts = latest.replace("v", "").split("[\\.-]");
            int length = Math.max(currentParts.length, latestParts.length);
            for (int i = 0; i < length; i++) {
                int currentPart = i < currentParts.length ? parseVersionPart(currentParts[i]) : 0;
                int latestPart = i < latestParts.length ? parseVersionPart(latestParts[i]) : 0;
                if (latestPart > currentPart)
                    return true;
                if (latestPart < currentPart)
                    return false;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}

