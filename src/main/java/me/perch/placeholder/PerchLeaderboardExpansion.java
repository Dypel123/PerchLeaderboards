package me.perch.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.perch.Leaderboards;
import me.perch.leaderboard.Leaderboard;
import me.perch.leaderboard.TimedLeaderboard;
import org.bukkit.entity.Player;
import java.util.Locale;

public class PerchLeaderboardExpansion extends PlaceholderExpansion {

    private final Leaderboards plugin;

    public PerchLeaderboardExpansion(Leaderboards plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "perchlb";
    }

    @Override
    public String getAuthor() {
        return "Perch";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {

        if (params == null || params.isEmpty()) return "";

        params = params.toLowerCase(Locale.ROOT);

        try {

            // %perchlb_description_<leaderboard>%
            if (params.startsWith("description_")) {

                String name = params.substring("description_".length());
                Leaderboard leaderboard =
                        plugin.getLeaderboardManager().getLeaderboard(name);

                if (leaderboard instanceof TimedLeaderboard timed) {
                    return timed.getCurrentTaskDescription();
                }

                return leaderboard != null
                        ? leaderboard.getDescription()
                        : "";

            }


            // %perchlb_topname_<leaderboard>_<position>%
            if (params.startsWith("topname_")) {

                String[] split = params.split("_");
                if (split.length != 3) return "";

                String name = split[1];
                int position = Integer.parseInt(split[2]);

                Leaderboard leaderboard =
                        plugin.getLeaderboardManager().getLeaderboard(name);

                return leaderboard != null
                        ? leaderboard.getTopName(position)
                        : "";
            }

            // perchlb_topvalue_<leaderboard>_<position>%
            if (params.startsWith("topvalue_")) {

                String[] split = params.split("_");
                if (split.length != 3) return "";

                String name = split[1];
                int position = Integer.parseInt(split[2]);

                Leaderboard leaderboard =
                        plugin.getLeaderboardManager().getLeaderboard(name);

                return leaderboard != null
                        ? leaderboard.getTopValue(position)
                        : "";
            }


            // perchlb_timeuntil_<leaderboard>%
            if (params.startsWith("timeuntil_")) {

                String name = params.substring("timeuntil_".length());
                Leaderboard leaderboard =
                        plugin.getLeaderboardManager().getLeaderboard(name);

                if (leaderboard instanceof TimedLeaderboard timed) {

                    long millis = timed.getTimeUntilResetMillis();
                    if (millis <= 0) return "Resetting...";

                    return formatDuration(millis);
                }

                return "Permanent";
            }

        } catch (Exception ignored) {}

        return "";
    }

    private String formatDuration(long millis) {

        long seconds = millis / 1000;

        long days = seconds / 86400;
        seconds %= 86400;

        long hours = seconds / 3600;
        seconds %= 3600;

        long minutes = seconds / 60;

        return days + " days, " + hours + " hours, " + minutes + " minutes";
    }
}