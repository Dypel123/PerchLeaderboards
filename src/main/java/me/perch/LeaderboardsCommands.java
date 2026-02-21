package me.perch;

import me.perch.leaderboard.Leaderboard;
import me.perch.leaderboard.TimedLeaderboard;
import me.perch.leaderboard.TimedTask;
import org.bukkit.command.*;
import java.math.BigDecimal;
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
                plugin.getMessages().send(sender, "admin-usage",
                        msg -> msg
                                .replace("{reload}", "/perchlb reload")
                                .replace("{info}", "/perchlb info <leaderboard>")
                );
            }

            if (sender.hasPermission("perchlb.top")) {
                plugin.getMessages().send(sender, "top-usage",
                        msg -> msg.replace("{top}", "/perchlb top <leaderboard> <page>")
                );
            }

            return true;
        }

        switch (args[0].toLowerCase()) {

            case "reload" -> {

                if (!sender.hasPermission("perchlb.admin")) {
                    plugin.getMessages().send(sender, "no-permission");
                    return true;
                }

                plugin.getLeaderboardManager().reload();
                plugin.getMessages().reload();
                plugin.getMessages().send(sender, "reload-success");
            }

            case "info" -> {

                if (!sender.hasPermission("perchlb.admin")) {
                    plugin.getMessages().send(sender, "no-permission");
                    return true;
                }

                if (args.length != 2) {
                    plugin.getMessages().send(sender, "info-usage");
                    return true;
                }

                Leaderboard lb =
                        plugin.getLeaderboardManager()
                                .getLeaderboard(args[1]);

                if (lb == null) {
                    plugin.getMessages().send(sender, "leaderboard-not-found");
                    return true;
                }

                plugin.getMessages().send(sender, "info-header",
                        msg -> msg.replace("{leaderboard}", lb.getName()));

                plugin.getMessages().send(sender, "info-type",
                        msg -> msg.replace("{type}", lb.getType()));

                if (lb instanceof TimedLeaderboard timed) {

                    List<TimedTask> tasks = timed.getTasks();
                    int activeIndex = timed.getCurrentTaskIndex();

                    StringBuilder placeholders = new StringBuilder();

                    for (int i = 0; i < tasks.size(); i++) {

                        TimedTask task = tasks.get(i);

                        if (i == activeIndex) {
                            placeholders.append("<green>")
                                    .append(task.getPlaceholder())
                                    .append("</green>");
                        } else {
                            placeholders.append("<gray>")
                                    .append(task.getPlaceholder())
                                    .append("</gray>");
                        }

                        if (i < tasks.size() - 1) {
                            placeholders.append("<dark_gray>, </dark_gray>");
                        }
                    }

                    final String formattedPlaceholders = placeholders.toString();

                    plugin.getMessages().send(sender, "info-placeholder",
                            msg -> msg.replace("{placeholder}", formattedPlaceholders));

                    plugin.getMessages().send(sender, "info-description",
                            msg -> msg.replace("{description}",
                                    tasks.get(activeIndex).getDescription()));

                    long remaining = timed.getTimeUntilResetMillis();

                    String time = remaining <= 0
                            ? "Soon"
                            : formatTime(remaining);

                    plugin.getMessages().send(sender, "info-reset",
                            msg -> msg.replace("{time}", time));
                } else {

                    plugin.getMessages().send(sender, "info-placeholder",
                            msg -> msg.replace("{placeholder}",
                                    lb.getPlaceholder()));

                    plugin.getMessages().send(sender, "info-description",
                            msg -> msg.replace("{description}",
                                    lb.getDescription()));

                    plugin.getMessages().send(sender, "info-reset",
                            msg -> msg.replace("{time}", "Permanent"));
                }
            }

            case "top" -> {

                if (!sender.hasPermission("perchlb.top")) {
                    plugin.getMessages().send(sender, "no-permission");
                    return true;
                }

                if (args.length < 2 || args.length > 3) {
                    plugin.getMessages().send(sender, "top-usage");
                    return true;
                }

                Leaderboard lb =
                        plugin.getLeaderboardManager()
                                .getLeaderboard(args[1]);

                if (lb == null) {
                    plugin.getMessages().send(sender, "leaderboard-not-found");
                    return true;
                }

                int page = 1;

                if (args.length == 3) {
                    try {
                        page = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ignored) {}
                }

                if (page < 1) page = 1;
                if (page > 3) page = 3;
                final int finalPage = page;

                final int perPage = 10;
                final int start = (page - 1) * perPage + 1;
                final int end = start + perPage - 1;

                plugin.getMessages().send(sender, "top-header",
                        msg -> msg
                                .replace("{leaderboard}", lb.getName())
                                .replace("{page}", String.valueOf(finalPage))
                );

                boolean any = false;

                for (int pos = start; pos <= end; pos++) {

                    final int finalPos = pos;
                    String name = lb.getTopName(pos);
                    String value = lb.getTopValue(pos);

                    if (name == null || name.isEmpty()) continue;
                    if (value == null || value.isEmpty()) continue;

                    double numeric;

                    try {
                        numeric = Double.parseDouble(value);
                    } catch (NumberFormatException e) {
                        continue;
                    }

                    if (numeric <= 0) continue;

                    String formattedValue = new BigDecimal(value)
                            .stripTrailingZeros()
                            .toPlainString();

                    any = true;

                    plugin.getMessages().send(sender, "top-entry",
                            msg -> msg
                                    .replace("{position}", String.valueOf(finalPos))
                                    .replace("{name}", name)
                                    .replace("{score}", formattedValue)
                    );
                }

                if (!any) {
                    plugin.getMessages().send(sender, "no-entries");
                }
            }

            default -> plugin.getMessages().send(sender, "unknown-command");
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