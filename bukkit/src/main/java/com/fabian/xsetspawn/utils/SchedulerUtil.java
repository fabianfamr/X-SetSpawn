package com.fabian.xsetspawn.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class SchedulerUtil {

    private static boolean isFolia = false;

    private static Object asyncScheduler;
    private static Method asyncRunDelayedMethod;

    private static Object regionScheduler;
    private static Method regionRunTimerMethod;
    private static Method regionRunDelayedMethod;
    private static Method regionRunMethod;

    private static Method teleportAsyncMethod;

    static {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;

            Object server = Bukkit.getServer();
            
            // AsyncScheduler
            Method getAsyncSchedulerMethod = server.getClass().getMethod("getAsyncScheduler");
            asyncScheduler = getAsyncSchedulerMethod.invoke(server);
            asyncRunDelayedMethod = asyncScheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class, TimeUnit.class);

            // RegionScheduler
            Method getRegionSchedulerMethod = server.getClass().getMethod("getRegionScheduler");
            regionScheduler = getRegionSchedulerMethod.invoke(server);
            regionRunTimerMethod = regionScheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Location.class, Consumer.class, long.class, long.class);
            regionRunDelayedMethod = regionScheduler.getClass().getMethod("runDelayed", Plugin.class, Location.class, Consumer.class, long.class);
            regionRunMethod = regionScheduler.getClass().getMethod("execute", Plugin.class, Location.class, Runnable.class);

            // TeleportAsync (Paper/Folia)
            try {
                teleportAsyncMethod = Class.forName("org.bukkit.entity.Entity").getMethod("teleportAsync", Location.class);
            } catch (Exception e) {
                // Not found, will fall back to sync teleport
            }
            
        } catch (Exception ignored) {
            isFolia = false;
        }
    }

    public interface TaskWrapper {
        void cancel();
    }

    public static void teleport(org.bukkit.entity.Player player, Location location) {
        teleport(null, player, location, null);
    }

    public static void teleport(Plugin plugin, org.bukkit.entity.Player player, Location location, Runnable callback) {
        if (teleportAsyncMethod != null) {
            try {
                Object futureObj = teleportAsyncMethod.invoke(player, location);
                if (futureObj instanceof java.util.concurrent.CompletableFuture) {
                    java.util.concurrent.CompletableFuture<?> future = (java.util.concurrent.CompletableFuture<?>) futureObj;
                    future.thenAccept(result -> {
                        if (callback != null) {
                            if (plugin != null) {
                                runRegion(plugin, location, callback);
                            } else {
                                callback.run();
                            }
                        }
                    });
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        player.teleport(location);
        if (callback != null) {
            callback.run();
        }
    }

    public static TaskWrapper runAsyncDelayed(Plugin plugin, Runnable task, long delayTicks) {
        if (isFolia) {
            try {
                long delayMillis = delayTicks * 50L;
                Consumer<?> consumer = (scheduledTask) -> task.run();
                Object scheduledTaskObj = asyncRunDelayedMethod.invoke(asyncScheduler, plugin, consumer, delayMillis, TimeUnit.MILLISECONDS);
                return createWrapper(scheduledTaskObj);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks);
            return bukkitTask::cancel;
        }
        return () -> {};
    }

    public static TaskWrapper runRegionTimer(Plugin plugin, Location location, Runnable task, long delayTicks, long periodTicks) {
        if (isFolia) {
            try {
                long initialDelay = Math.max(1, delayTicks);
                Consumer<?> consumer = (scheduledTask) -> task.run();
                Object scheduledTaskObj = regionRunTimerMethod.invoke(regionScheduler, plugin, location, consumer, initialDelay, periodTicks);
                return createWrapper(scheduledTaskObj);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
            return bukkitTask::cancel;
        }
        return () -> {};
    }

    public static TaskWrapper runRegionDelayed(Plugin plugin, Location location, Runnable task, long delayTicks) {
        if (isFolia) {
            try {
                Consumer<?> consumer = (scheduledTask) -> task.run();
                Object scheduledTaskObj = regionRunDelayedMethod.invoke(regionScheduler, plugin, location, consumer, delayTicks);
                return createWrapper(scheduledTaskObj);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            return bukkitTask::cancel;
        }
        return () -> {};
    }

    public static void runRegion(Plugin plugin, Location location, Runnable task) {
        if (isFolia) {
            try {
                regionRunMethod.invoke(regionScheduler, plugin, location, task);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private static TaskWrapper createWrapper(Object foliaScheduledTask) {
        return () -> {
            try {
                Method cancelMethod = foliaScheduledTask.getClass().getMethod("cancel");
                cancelMethod.invoke(foliaScheduledTask);
            } catch (Exception ignored) {}
        };
    }
}
