package at.yawk.bukkit.lagrestart;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * @author Yawkat
 */
public class LagRestart extends JavaPlugin {
    private float minimumTps = 12;
    private boolean enabled = false;
    private int belowMinimumSeconds = 0;
    private ScheduledExecutorService scheduler;
    
    private volatile long tickIndex;
    private volatile boolean restartScheduled;
    
    private volatile float tps;
    
    @Override
    public void onEnable() {
        scheduler = new ScheduledThreadPoolExecutor(1);
        enabled = true;
        minimumTps = (float) getConfig().getDouble("minimumtps", minimumTps);
        getServer().getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                tickIndex++;
            }
        }, 0, 1);
        scheduler.scheduleAtFixedRate(new Runnable() {
            long lastTickIndex = 0;
            
            @Override
            public void run() {
                if (tickIndex > minimumTps) {
                    tps = tickIndex - lastTickIndex;
                    lastTickIndex = tickIndex;
                    if (tps < minimumTps && !restartScheduled) {
                        belowMinimumSeconds++;
                        if (belowMinimumSeconds > 30) {
                            scheduleRestart();
                        }
                    } else {
                        belowMinimumSeconds = 0;
                    }
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }
    
    private void registerRestartWarning(int seconds, int totalSeconds) {
        final String suffix;
        if (seconds == 0) {
            suffix = "NOW";
        } else if (seconds == 10) {
            suffix = "in 10 seconds";
        } else if (seconds == 30) {
            suffix = "in 30 seconds";
        } else if (seconds == 60) {
            suffix = "in 1 minute";
        } else if (seconds % 60 == 0) {
            suffix = "in " + (seconds / 60) + " minutes";
        } else {
            suffix = null;
        }
        if (suffix != null) {
            final String message = ChatColor.BOLD.toString() + ChatColor.BLACK + "#" + ChatColor.WHITE + "#" + ChatColor.BLACK + "#" + ChatColor.DARK_AQUA + " Restarting " + suffix;
            scheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    getServer().getScheduler().runTask(LagRestart.this, new Runnable() {
                        @Override
                        public void run() {
                            getServer().broadcastMessage(message);
                        }
                    });
                }
            }, totalSeconds - seconds, TimeUnit.SECONDS);
        }
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String lbl, String[] args) {
        if (cmd.testPermission(sender)) {
            if (args.length == 0) {
                final String prefix = ChatColor.GOLD + "| " + ChatColor.GRAY;
                sender.sendMessage(prefix + "TPS: " + ChatColor.DARK_PURPLE + Math.round(tps * 100) / 100F);
                {
                    final StringBuilder bar = new StringBuilder(prefix);
                    bar.append('[');
                    bar.append(ChatColor.GREEN);
                    int border = Math.round(tps);
                    for (int i = 0; i < 20; i++) {
                        if (i == border) {
                            bar.append(ChatColor.RED);
                        }
                        bar.append('#');
                    }
                    bar.append(ChatColor.GRAY);
                    bar.append(']');
                    sender.sendMessage(bar.toString());
                }
                sender.sendMessage(prefix + "Total ticks: " + ChatColor.DARK_PURPLE + tickIndex);
            } else {
                String prefix = ChatColor.GRAY + "LagRestart: " + ChatColor.GOLD;
                if (args.length != 1) {
                    sender.sendMessage(prefix + "Invalid usage");
                } else if (args[0].equalsIgnoreCase("off")) {
                    if (!enabled) {
                        sender.sendMessage(prefix + "Already disabled!");
                    } else {
                        onDisable();
                        sender.sendMessage(prefix + "Disabled");
                    }
                } else if (args[0].equalsIgnoreCase("on")) {
                    if (enabled) {
                        sender.sendMessage(prefix + "Already enabled!");
                    } else {
                        onEnable();
                        sender.sendMessage(prefix + "Enabled");
                    }
                } else if (args[0].equalsIgnoreCase("schedule")) {
                    if (restartScheduled) {
                        sender.sendMessage(prefix + "A restart is already scheduled!");
                    } else {
                        scheduleRestart();
                        sender.sendMessage(prefix + "Scheduled restart.");
                    }
                } else {
                    sender.sendMessage(prefix + "Invalid usage");
                }
            }
        }
        return true;
    }
    
    @Override
    public void onDisable() {
        enabled = false;
        getServer().getScheduler().cancelTasks(this);
        scheduler.shutdownNow();
    }
    
    private void scheduleRestart() {
        restartScheduled = true;
        final int waitingTime = 5 * 60;
        for (int i = 0; i <= waitingTime; i++) {
            registerRestartWarning(i, waitingTime);
        }
        scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                getServer().getScheduler().runTask(LagRestart.this, new Runnable() {
                    @Override
                    public void run() {
                        for (Player player : getServer().getOnlinePlayers()) {
                            player.kickPlayer("Restarting server.");
                        }
                        getServer().shutdown();
                    }
                });
            }
        }, waitingTime, TimeUnit.SECONDS);
    }
}
