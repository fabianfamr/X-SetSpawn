package com.fabian.xsetspawn.managers;

import com.fabian.xsetspawn.XSetSpawn;
import net.byteflux.libby.BukkitLibraryManager;
import net.byteflux.libby.Library;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DependencyManager {

    private final XSetSpawn plugin;
    private final BukkitLibraryManager libraryManager;

    public DependencyManager(XSetSpawn plugin) {
        this.plugin = plugin;
        try {
            Path xapiPath = Paths.get(plugin.getDataFolder().getParent(), "X-API");
            Files.createDirectories(xapiPath);
            this.libraryManager = new BukkitLibraryManager(plugin, xapiPath);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not create X-API directory, using default: " + e.getMessage());
            this.libraryManager = new BukkitLibraryManager(plugin);
        }
        this.libraryManager.addMavenCentral();
        this.libraryManager.addSonatype();
        this.libraryManager.addRepository("https://repo.papermc.io/repository/maven-public/");
    }

    public void loadDependencies() {
        String storageType = plugin.getConfigManager().getConfig().getString("storage.type", "YAML").toUpperCase();

        try {
            plugin.getLogger().info("Loading runtime dependencies via X-API...");
            loadAdventureDependencies();

            if (storageType.equals("MONGODB") || storageType.equals("MONGO")) {
                loadMongoDependencies();
            } else if (storageType.equals("SQL") || storageType.equals("MYSQL") || storageType.equals("MARIADB") || storageType.equals("H2")) {
                loadSqlDependencies(storageType);
            }
            plugin.getLogger().info("All dependencies loaded successfully!");
        } catch (Exception e) {
            plugin.logError("Failed to load runtime database libraries! " + e.getMessage());
        }
    }

    private void loadAdventureDependencies() {
        Library adventureApi = Library.builder()
                .groupId("net.kyori")
                .artifactId("adventure-api")
                .version("4.14.0")
                .build();

        Library miniMessage = Library.builder()
                .groupId("net.kyori")
                .artifactId("adventure-text-minimessage")
                .version("4.14.0")
                .build();

        Library legacySerializer = Library.builder()
                .groupId("net.kyori")
                .artifactId("adventure-text-serializer-legacy")
                .version("4.14.0")
                .build();

        Library plainSerializer = Library.builder()
                .groupId("net.kyori")
                .artifactId("adventure-text-serializer-plain")
                .version("4.14.0")
                .build();

        Library key = Library.builder()
                .groupId("net.kyori")
                .artifactId("adventure-key")
                .version("4.14.0")
                .build();

        Library examinationApi = Library.builder()
                .groupId("net.kyori")
                .artifactId("examination-api")
                .version("1.3.0")
                .build();

        Library examinationString = Library.builder()
                .groupId("net.kyori")
                .artifactId("examination-string")
                .version("1.3.0")
                .build();

        libraryManager.loadLibrary(adventureApi);
        libraryManager.loadLibrary(miniMessage);
        libraryManager.loadLibrary(legacySerializer);
        libraryManager.loadLibrary(plainSerializer);
        libraryManager.loadLibrary(key);
        libraryManager.loadLibrary(examinationApi);
        libraryManager.loadLibrary(examinationString);
    }

    private void loadMongoDependencies() {
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
    }

    private void loadSqlDependencies(String storageType) {
        Library slf4j = Library.builder()
                .groupId("org.slf4j")
                .artifactId("slf4j-api")
                .version("1.7.32")
                .build();

        Library hikari = Library.builder()
                .groupId("com.zaxxer")
                .artifactId("HikariCP")
                .version("3.4.5")
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
}