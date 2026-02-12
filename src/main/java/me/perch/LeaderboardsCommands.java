package me.perch;

import me.perch.leaderboard.Leaderboard;
import me.perch.leaderboard.TimedLeaderboard;
import me.perch.leaderboard.TimedTask;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class LeaderboardsCommands implements CommandExecutor, TabCompleter {

    private final Leaderboards plugin;

    public LeaderboardsCommands(Leaderboards plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (!sender.hasPermission("perchlb.admin")) {
            sender.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("/perchlb reload");
            sender.sendMessage("/perchlb info <leaderboard>");
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "reload" -> {
                plugin.getLeaderboardManager().reload();
                sender.sendMessage(ChatColor.GREEN + "Reloaded.");
            }

            case "info" -> {

                if (args.length != 2) {
                    sender.sendMessage("/perchlb info <leaderboard>");
                    return true;
                }

                Leaderboard lb =
                        plugin.getLeaderboardManager()
                                .getLeaderboard(args[1]);

                if (lb == null) {
                    sender.sendMessage(ChatColor.RED + "Not found.");
                    return true;
                }

                sender.sendMessage(ChatColor.GOLD + lb.getName());

                sender.sendMessage(ChatColor.YELLOW + "Type: "
                        + ChatColor.WHITE + lb.getType());

                if (lb instanceof TimedLeaderboard timed) {

                    List<TimedTask> tasks = timed.getTasks();

                    // === SINGLE TASK ===
                    if (tasks.size() == 1) {

                        sender.sendMessage(ChatColor.YELLOW + "Placeholder: "
                                + ChatColor.WHITE + tasks.get(0).getPlaceholder());

                        sender.sendMessage(ChatColor.YELLOW + "Description: "
                                + ChatColor.WHITE + tasks.get(0).getDescription());

                    }
                    // === MULTIPLE TASKS ===
                    else {

                        StringBuilder builder = new StringBuilder();

                        int activeIndex = timed.getCurrentTaskIndex();

                        for (int i = 0; i < tasks.size(); i++) {

                            if (i == activeIndex) {
                                builder.append(ChatColor.GREEN);
                            } else {
                                builder.append(ChatColor.WHITE);
                            }

                            builder.append(tasks.get(i).getPlaceholder());

                            if (i < tasks.size() - 1) {
                                builder.append(ChatColor.GRAY).append(", ");
                            }
                        }

                        sender.sendMessage(ChatColor.YELLOW + "Placeholders: "
                                + builder);

                        sender.sendMessage(ChatColor.YELLOW + "Description: "
                                + ChatColor.WHITE
                                + tasks.get(activeIndex).getDescription());
                    }

                    long remaining = timed.getTimeUntilResetMillis();

                    if (remaining <= 0) {
                        sender.sendMessage(ChatColor.YELLOW + "Resets in: "
                                + ChatColor.WHITE + "Soon");
                    } else {
                        sender.sendMessage(ChatColor.YELLOW + "Resets in: "
                                + ChatColor.WHITE + formatTime(remaining));
                    }

                } else {

                    // SIMPLE LEADERBOARD
                    sender.sendMessage(ChatColor.YELLOW + "Placeholder: "
                            + ChatColor.WHITE + lb.getPlaceholder());

                    sender.sendMessage(ChatColor.YELLOW + "Description: "
                            + ChatColor.WHITE + lb.getDescription());

                    sender.sendMessage(ChatColor.YELLOW + "Resets in: "
                            + ChatColor.WHITE + "Permanent");
                }
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command command,
                                      String alias,
                                      String[] args) {

        List<String> list = new ArrayList<>();

        if (args.length == 1) {
            list.add("reload");
            list.add("info");
        }

        if (args.length == 2 &&
                args[0].equalsIgnoreCase("info")) {

            list.addAll(
                    plugin.getLeaderboardManager()
                            .getLeaderboardNames()
            );
        }

        return list;
    }

    private String formatTime(long millis) {

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);

        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);

        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);

        return days + " days, "
                + hours + " hours, "
                + minutes + " minutes";
    }
}
