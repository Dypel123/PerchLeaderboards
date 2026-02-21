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

        if (args.length == 0) {
            if (sender.hasPermission("perchlb.admin")) {
                sender.sendMessage("/perchlb reload");
                sender.sendMessage("/perchlb info <leaderboard>");
            }
            if (sender.hasPermission("perchlb.top")) {
                sender.sendMessage("/perchlb top <leaderboard> <page>");
            }
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "reload" -> {

                if (!sender.hasPermission("perchlb.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }

                plugin.getLeaderboardManager().reload();
                sender.sendMessage(ChatColor.GREEN + "Reloaded.");
            }

            case "info" -> {

                if (!sender.hasPermission("perchlb.admin")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }

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

                            builder.append(i == activeIndex
                                    ? ChatColor.GREEN
                                    : ChatColor.WHITE);

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

            case "top" -> {

                if (!sender.hasPermission("perchlb.top")) {
                    sender.sendMessage(ChatColor.RED + "No permission.");
                    return true;
                }

                if (args.length < 2 || args.length > 3) {
                    sender.sendMessage("/perchlb top <leaderboard> <page>");
                    return true;
                }

                Leaderboard lb = plugin.getLeaderboardManager()
                        .getLeaderboard(args[1]);

                if (lb == null) {
                    sender.sendMessage(ChatColor.RED + "Leaderboard not found.");
                    return true;
                }

                int page = 1;

                if (args.length == 3) {
                    try {
                        page = Integer.parseInt(args[2]);
                        if (page <= 0) page = 1;
                    } catch (NumberFormatException ignored) {}
                }

                final int perPage = 10;
                final int start = (page - 1) * perPage + 1;
                final int end = start + perPage - 1;

                sender.sendMessage(ChatColor.GOLD + "Top "
                        + lb.getName() + " - Page " + page);

                boolean any = false;

                for (int pos = start; pos <= end; pos++) {

                    String name = lb.getTopName(pos);
                    String value = lb.getTopValue(pos);

                    if (name == null || name.isEmpty()) continue;
                    if (value == null || value.isEmpty()) continue;

                    double numericValue;

                    try {
                        numericValue = Double.parseDouble(value);
                    } catch (NumberFormatException e) {
                        continue;
                    }

                    if (numericValue <= 0) continue;

                    any = true;

                    String formattedValue;

                    try {
                        formattedValue = new java.math.BigDecimal(value)
                                .stripTrailingZeros()
                                .toPlainString();
                    } catch (NumberFormatException e) {
                        formattedValue = value;
                    }

                    sender.sendMessage(
                            ChatColor.GOLD + String.valueOf(pos)
                                    + ChatColor.DARK_GRAY + " • "
                                    + ChatColor.WHITE + name
                                    + ChatColor.GRAY + " (" + formattedValue + ")"
                    );
                }

                if (!any) {
                    sender.sendMessage(ChatColor.GRAY + "No entries on this page.");
                }
            }

            default -> sender.sendMessage(ChatColor.RED + "Unknown subcommand.");
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

            if (sender.hasPermission("perchlb.admin")) {
                list.add("reload");
                list.add("info");
            }

            if (sender.hasPermission("perchlb.top")) {
                list.add("top");
            }
        }

        if (args.length == 2) {

            if ((args[0].equalsIgnoreCase("info")
                    && sender.hasPermission("perchlb.admin"))
                    ||
                    (args[0].equalsIgnoreCase("top")
                            && sender.hasPermission("perchlb.top"))) {

                list.addAll(
                        plugin.getLeaderboardManager()
                                .getLeaderboardNames()
                );
            }
        }

        if (args.length == 3
                && args[0].equalsIgnoreCase("top")
                && sender.hasPermission("perchlb.top")) {

            list.add("1");
            list.add("2");
            list.add("3");
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