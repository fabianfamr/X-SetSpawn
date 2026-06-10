package com.fabian.xsetspawn.listeners;

import com.fabian.xsetspawn.XSetSpawn;
import com.fabian.xsetspawn.utils.DebugLogger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Collection;

public class CommandListener implements Listener {

    public CommandListener() {
    }

    /**
     * Handles the PlayerCommandSendEvent via reflection to support 1.8+
     * (This event was added in 1.13)
     */
    @EventHandler
    public void onCommandSend(org.bukkit.event.Event event) {
        if (!event.getClass().getSimpleName().equals("PlayerCommandSendEvent")) {
            return;
        }

        DebugLogger.debug("Listener", "Filtering namespaced commands for player");
        try {
            // Get the collection of commands being sent to the player
            java.lang.reflect.Method getCommandsMethod = event.getClass().getMethod("getCommands");
            Object commandsObj = getCommandsMethod.invoke(event);

            if (commandsObj instanceof Collection) {
                @SuppressWarnings("unchecked")
                Collection<String> commands = (Collection<String>) commandsObj;

                // Remove namespaced commands (containing ':')
                commands.removeIf(command -> command.contains(":"));
            }
        } catch (Exception ignored) {
            // Gracefully ignore errors to prevent console spam on unsupported versions
        }
    }
}

