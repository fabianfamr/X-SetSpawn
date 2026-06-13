package com.fabian.xsetspawn.commands;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.utils.DebugLogger;
import org.bukkit.ChatColor;
import com.fabian.xsetspawn.managers.LanguageManager;
import com.fabian.xsetspawn.managers.ManagerConfig;
import com.fabian.xsetspawn.managers.Permission;
import com.fabian.xsetspawn.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final XSetSpawn plugin;
    private final LanguageManager languageManager;
    private final ManagerConfig config;

    public AdminCommand(XSetSpawn plugin) {
        this.plugin = plugin;
        this.languageManager = plugin.getLanguageManager();
        this.config = plugin.getManagerConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        DebugLogger.debug("Command", "/xsetspawn executed by " + sender.getName() + " with subcommand: " + (args.length > 0 ? args[0] : "none"));
        if (!Permission.ADMIN.has(sender)) {
            sender.sendMessage(languageManager.getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
            case "rl":
                if (!Permission.RELOAD.has(sender)) {
                    sender.sendMessage(languageManager.getMessage("no-permission"));
                    return true;
                }
                DebugLogger.debug("Command", "Reloading plugin configuration...");
                plugin.getDelayManager().cancelAllPendingTeleports();
                config.reload();
                plugin.getConfigManager().reloadConfiguration();
                plugin.getLanguageManager().reloadLanguage();
                plugin.getSpawnManager().loadCachesAsync(
                        () -> sender.sendMessage(languageManager.getMessage("config-reloaded")));
                plugin.setupMetrics();
                break;

            case "update":
            case "upd":
                if (!Permission.UPDATE.has(sender)) {
                    sender.sendMessage(languageManager.getMessage("no-permission"));
                    return true;
                }
                if (plugin.getUpdateChecker() != null) {
                    sender.sendMessage(languageManager.getMessage("update-checking"));
                    plugin.getUpdateChecker().checkForUpdates(sender);
                } else {
                    sender.sendMessage(languageManager.getMessage("update-disabled"));
                }
                break;

            case "version":
            case "v":
            case "ver":
                sender.sendMessage(languageManager.getMessageUnprefixed("version-header"));
                sender.sendMessage(languageManager.getMessageUnprefixed("version-title"));
                sender.sendMessage(languageManager.getMessageUnprefixed("version-info-version",
                        plugin.getDescription().getVersion()));
                sender.sendMessage(languageManager.getMessageUnprefixed("version-info-author",
                        String.join(", ", plugin.getDescription().getAuthors())));
                sender.sendMessage(languageManager.getMessageUnprefixed("version-info-platform",
                        (plugin.getServer().getName().contains("Folia") ? "Folia" : "Bukkit/Paper")));
                sender.sendMessage(languageManager.getMessageUnprefixed("version-footer"));
                break;

            case "setspawn":
                // Bypass ADMIN check for this specific subcommand, relying on SETSPAWN check
                // inside SetSpawnCommand
                String[] setSpawnArgs = new String[args.length - 1];
                System.arraycopy(args, 1, setSpawnArgs, 0, args.length - 1);
                plugin.getCommand("setspawn").getExecutor().onCommand(sender, command, "setspawn", setSpawnArgs);
                break;

            case "delspawn":
            case "removespawn":
            case "ds":
                // Bypass ADMIN check, relying on SETSPAWN check inside DelSpawnCommand
                String[] delSpawnArgs = new String[args.length - 1];
                System.arraycopy(args, 1, delSpawnArgs, 0, args.length - 1);
                plugin.getCommand("delspawn").getExecutor().onCommand(sender, command, "delspawn", delSpawnArgs);
                break;

            case "import":
                new com.fabian.xsetspawn.commands.ImportCommand(plugin).execute(sender,
                        java.util.Arrays.copyOfRange(args, 1, args.length));
                break;

            case "locate":
            case "language":
            case "lang":
                handleLocateCommand(sender, args);
                break;

            case "debug":
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    if (config.debugPlayer != null && config.debugPlayer.equals(player.getUniqueId())) {
                        config.debugPlayer = null;
                        ColorUtils.sendMessage(player, config.prefix + "&7Debug mode: &cdisabled");
                    } else {
                        config.debugPlayer = player.getUniqueId();
                        ColorUtils.sendMessage(player, config.prefix + "&7Debug mode: &aenabled &7(messages sent to you)");
                    }
                } else {
                    boolean currentState = config.debugEnabled;
                    config.debugEnabled = !currentState;
                    plugin.getConfig().set("debug", config.debugEnabled);
                    plugin.saveConfig();
                    ColorUtils.sendMessage(sender, config.prefix + "&7Debug mode: " + (config.debugEnabled ? "&aenabled &7(console)" : "&cdisabled"));
                }
                break;

            case "help":
            case "h":
            case "?":
            default:
                showHelp(sender);
                break;
        }

        return true;
    }

    private void handleLocateCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            String current = languageManager.getCurrentLanguage();
            java.util.List<String> available = languageManager.getAvailableLanguages();
            sender.sendMessage(languageManager.getMessageUnprefixed("locate-usage"));
            sender.sendMessage(ColorUtils.formatToLegacy("&7Current: &f" + current));
            sender.sendMessage(ColorUtils.formatToLegacy("&7Available: &f" + String.join(", ", available)));
            return;
        }

        String newLang = args[1].toLowerCase();
        boolean success = languageManager.setLanguage(newLang);

        if (success) {
            sender.sendMessage(languageManager.getMessage("locate-changed", newLang));
        } else {
            java.util.List<String> available = languageManager.getAvailableLanguages();
            sender.sendMessage(languageManager.getMessage("locate-not-found", newLang));
            sender.sendMessage(ColorUtils.formatToLegacy("&7Available: &f" + String.join(", ", available)));
        }
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(languageManager.getMessageUnprefixed("help-header"));
        sender.sendMessage(languageManager.getMessageUnprefixed("help-spawn"));
        sender.sendMessage(languageManager.getMessageUnprefixed("help-setspawn"));
        sender.sendMessage(languageManager.getMessageUnprefixed("help-delspawn"));
        sender.sendMessage(languageManager.getMessageUnprefixed("help-back"));
        sender.sendMessage(languageManager.getMessageUnprefixed("help-reload"));
        sender.sendMessage(languageManager.getMessageUnprefixed("help-locate"));
        sender.sendMessage(languageManager.getMessageUnprefixed("help-version"));
        sender.sendMessage(languageManager.getMessageUnprefixed("help-update"));
        sender.sendMessage(languageManager.getMessageUnprefixed("help-help"));
        sender.sendMessage(languageManager.getMessageUnprefixed("help-footer"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!Permission.ADMIN.has(sender)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("reload");
            completions.add("locate");
            completions.add("version");
            completions.add("update");
            completions.add("import");
            completions.add("debug");

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .sorted()
                    .collect(Collectors.toList());
        }

        // Suggest import sources for /xss import <TAB>
        if (args.length == 2 && args[0].equalsIgnoreCase("import")) {
            return new com.fabian.xsetspawn.commands.ImportCommand(plugin).getTabCompletions(
                    java.util.Arrays.copyOfRange(args, 1, args.length));
        }

        // Suggest existing named spawn names for /xss delspawn <TAB>
        if (args.length == 2 && args[0].equalsIgnoreCase("delspawn")) {
            List<String> names = plugin.getSpawnManager().getAllNamedSpawnNames();
            return names.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .sorted()
                    .collect(Collectors.toList());
        }

        // Suggest languages for /xss locate <TAB>
        if (args.length == 2 && (args[0].equalsIgnoreCase("locate") || args[0].equalsIgnoreCase("language")
                || args[0].equalsIgnoreCase("lang"))) {
            return languageManager.getAvailableLanguages().stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .sorted()
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
