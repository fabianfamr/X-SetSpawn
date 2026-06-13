package com.fabian.xsetspawn.listeners;

import com.fabian.xsetspawn.utils.DebugLogger;

import java.util.Collection;

public class CommandListener implements org.bukkit.event.Listener {

    // Only hide our own namespaced commands
    private static final String[] HIDDEN_PREFIXES = {
            "xsetspawn:", "x-setspawn:"
    };

    public CommandListener() {
    }

    /**
     * Handles the PlayerCommandSendEvent via reflection to support 1.8+
     * (This event was added in 1.13)
     */
    public void onCommandSend(org.bukkit.event.Event event) {
        if (!event.getClass().getSimpleName().equals("PlayerCommandSendEvent")) {
            return;
        }

        DebugLogger.debug("Listener", "Filtering own namespaced commands for player");
        try {
            java.lang.reflect.Method getCommandsMethod = event.getClass().getMethod("getCommands");
            Object commandsObj = getCommandsMethod.invoke(event);

            if (commandsObj instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<String> commands = (Collection<String>) commandsObj;

                // Only remove our own namespaced commands
                commands.removeIf(command -> {
                    String lower = command.toLowerCase();
                    for (String prefix : HIDDEN_PREFIXES) {
                        if (lower.startsWith(prefix)) return true;
                    }
                    return false;
                });
            }
        } catch (Exception ignored) {
            // Gracefully ignore errors to prevent console spam on unsupported versions
        }
    }
}

