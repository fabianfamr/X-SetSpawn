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
            ColorUtils.sendMessage(sender, languageManager.getMessage("no-permission"));
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
                    ColorUtils.sendMessage(sender, languageManager.getMessage("no-permission"));
                    return true;
                }
                DebugLogger.debug("Command", "Reloading plugin configuration...");
                plugin.getDelayManager().cancelAllPendingTeleports();
                config.reload();
                plugin.getConfigManager().reloadConfiguration();
                plugin.getLanguageManager().reloadLanguage();
                plugin.getSpawnManager().loadCachesAsync(
                        () -> ColorUtils.sendMessage(sender, languageManager.getMessage("config-reloaded")));
                plugin.setupMetrics();
                break;

            case "update":
            case "upd":
                if (!Permission.UPDATE.has(sender)) {
                    ColorUtils.sendMessage(sender, languageManager.getMessage("no-permission"));
                    return true;
                }
                if (plugin.getUpdateChecker() != null) {
                    ColorUtils.sendMessage(sender, languageManager.getMessage("update-checking"));
                    plugin.getUpdateChecker().checkForUpdates(sender);
                } else {
                    ColorUtils.sendMessage(sender, languageManager.getMessage("update-disabled"));
                }
                break;

            case "version":
            case "v":
            case "ver":
                boolean vConsole = !(sender instanceof Player);
                sendLine(sender, "version-header", vConsole);
                sendLine(sender, "version-title", vConsole);
                sendLine(sender, "version-info-version", vConsole, plugin.getDescription().getVersion());
                sendLine(sender, "version-info-author", vConsole, String.join(", ", plugin.getDescription().getAuthors()));
                sendLine(sender, "version-info-platform", vConsole,
                        (plugin.getServer().getName().contains("Folia") ? "Folia" : "Bukkit/Paper"));
                sendLine(sender, "version-footer", vConsole);
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

            case "forcemessages":
            case "fm":
                if (!Permission.FORCEMESSAGES.has(sender)) {
                    ColorUtils.sendMessage(sender, languageManager.getMessage("no-permission"));
                    return true;
                }
                handleForceMessagesCommand(sender, args);
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
            ColorUtils.sendMessage(sender, languageManager.getMessageUnprefixed("locate-usage"));
            ColorUtils.sendMessage(sender, ColorUtils.formatToLegacy("&7Current: &f" + current));
            ColorUtils.sendMessage(sender, ColorUtils.formatToLegacy("&7Available: &f" + String.join(", ", available)));
            return;
        }

        String newLang = args[1].toLowerCase();
        boolean success = languageManager.setLanguage(newLang);

        if (success) {
            ColorUtils.sendMessage(sender, languageManager.getMessage("locate-changed", newLang));
        } else {
            java.util.List<String> available = languageManager.getAvailableLanguages();
            ColorUtils.sendMessage(sender, languageManager.getMessage("locate-not-found", newLang));
            ColorUtils.sendMessage(sender, ColorUtils.formatToLegacy("&7Available: &f" + String.join(", ", available)));
        }
    }

    private void showHelp(CommandSender sender) {
        boolean console = !(sender instanceof Player);
        sendLine(sender, "help-header", console);
        sendLine(sender, "help-spawn", console);
        sendLine(sender, "help-setspawn", console);
        sendLine(sender, "help-delspawn", console);
        sendLine(sender, "help-back", console);
        sendLine(sender, "help-reload", console);
        sendLine(sender, "help-locate", console);
        sendLine(sender, "help-version", console);
        sendLine(sender, "help-update", console);
        sendLine(sender, "help-help", console);
        sendLine(sender, "help-footer", console);
    }

    /**
     * Sends a language message, stripping § color codes when the sender
     * is the console (avoids garbled characters on Windows CP437/CP850).
     */
    private void sendLine(CommandSender sender, String key, boolean strip) {
        String msg = languageManager.getMessageUnprefixed(key);
        sender.sendMessage(strip ? ChatColor.stripColor(msg) : msg);
    }

    /**
     * Overload that accepts format arguments (e.g. version info placeholders).
     */
    private void sendLine(CommandSender sender, String key, boolean strip, Object... args) {
        String msg = languageManager.getMessageUnprefixed(key, args);
        sender.sendMessage(strip ? ChatColor.stripColor(msg) : msg);
    }

    private void handleForceMessagesCommand(CommandSender sender, String[] args) {
        // /xss forcemessages → show current language + usage
        if (args.length < 2) {
            String current = languageManager.getCurrentLanguage();
            java.util.List<String> available = languageManager.getAvailableLanguages();
            ColorUtils.sendMessage(sender, languageManager.getMessageUnprefixed("force-messages-current", current));
            ColorUtils.sendMessage(sender, languageManager.getMessageUnprefixed("force-messages-usage"));
            ColorUtils.sendMessage(sender, languageManager.getMessageUnprefixed("language-list", String.join(", ", available)));
            return;
        }

        String mode = args[1].toLowerCase();

        // Validate mode
        if (!mode.equals("keep") && !mode.equals("new")) {
            ColorUtils.sendMessage(sender, languageManager.getMessage("force-messages-invalid-mode"));
            return;
        }

        // /xss forcemessages <keep|new> → apply to all
        if (args.length < 3 || args[2].equalsIgnoreCase("all")) {
            if (mode.equals("keep")) {
                int count = languageManager.forceReloadAllMessages();
                ColorUtils.sendMessage(sender, languageManager.getMessage("force-messages-all", String.valueOf(count)));
            } else {
                int count = languageManager.forceResetAllMessages();
                ColorUtils.sendMessage(sender, languageManager.getMessage("force-messages-reset-all", String.valueOf(count)));
            }
            return;
        }

        // /xss forcemessages <keep|new> <language>
        String langCode = args[2].toLowerCase();
        java.util.List<String> available = languageManager.getAvailableLanguages();
        boolean langExists = available.stream().anyMatch(l -> l.equalsIgnoreCase(langCode));
        if (!langExists) {
            ColorUtils.sendMessage(sender, languageManager.getMessage("language-not-found", String.join(", ", available)));
            return;
        }

        String current = languageManager.getCurrentLanguage();

        if (mode.equals("keep")) {
            boolean reloaded = languageManager.forceReloadMessages(langCode);
            if (langCode.equalsIgnoreCase(current)) {
                ColorUtils.sendMessage(sender, languageManager.getMessage("force-messages-success", langCode));
            } else {
                ColorUtils.sendMessage(sender, languageManager.getMessage("force-messages-no-changes", langCode));
            }
        } else {
            boolean reset = languageManager.forceResetMessages(langCode);
            if (!reset) {
                ColorUtils.sendMessage(sender, languageManager.getMessage("language-not-found", String.join(", ", available)));
                return;
            }
            if (langCode.equalsIgnoreCase(current)) {
                ColorUtils.sendMessage(sender, languageManager.getMessage("force-messages-reset-success", langCode));
            } else {
                ColorUtils.sendMessage(sender, languageManager.getMessage("force-messages-reset-no-active", langCode));
            }
        }
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
            completions.add("forcemessages");

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

        // Suggest modes for /xss forcemessages <TAB>
        if (args.length == 2 && (args[0].equalsIgnoreCase("forcemessages") || args[0].equalsIgnoreCase("fm"))) {
            List<String> modes = new ArrayList<>();
            modes.add("keep");
            modes.add("new");
            return modes.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .sorted()
                    .collect(Collectors.toList());
        }

        // Suggest "all" + available languages for /xss forcemessages <keep|new> <TAB>
        if (args.length == 3 && (args[0].equalsIgnoreCase("forcemessages") || args[0].equalsIgnoreCase("fm"))) {
            List<String> targets = new ArrayList<>();
            targets.add("all");
            targets.addAll(languageManager.getAvailableLanguages());
            return targets.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .sorted()
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
