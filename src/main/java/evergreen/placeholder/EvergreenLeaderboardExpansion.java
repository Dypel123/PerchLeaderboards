package evergreen.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import evergreen.Leaderboards;
import evergreen.leaderboard.Leaderboard;
import evergreen.leaderboard.TimedLeaderboard;
import evergreen.leaderboard.CommunityLeaderboard;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

public class EvergreenLeaderboardExpansion extends PlaceholderExpansion {

    private final Leaderboards plugin;

    public EvergreenLeaderboardExpansion(Leaderboards plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NonNull String getIdentifier() {
        return "evergreenlb";
    }

    @Override
    public @NonNull String getAuthor() {
        return "Evergreen";
    }

    @Override
    public @NonNull String getVersion() {
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

            // %evergreenlb_description_<leaderboard>%
            if (params.startsWith("description_")) {

                String name = params.substring("description_".length());
                Leaderboard leaderboard =
                        plugin.getLeaderboardManager().getLeaderboard(name);

                if (leaderboard instanceof TimedLeaderboard timed) {
                    return timed.getCurrentTaskDescription();
                }

                if (leaderboard instanceof CommunityLeaderboard community) {
                    return community.getCurrentTaskDescription();
                }

                return leaderboard != null
                        ? leaderboard.getDescription()
                        : "";

            }


            // %evergreenlb_topname_<leaderboard>_<position>%
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

            // %evergreenlb_topvalueraw_<leaderboard>_<position>%
            if (params.startsWith("topvalueraw_")) {

                String[] split = params.split("_");
                if (split.length != 3) return "";

                String name = split[1];
                int position = Integer.parseInt(split[2]);

                Leaderboard leaderboard =
                        plugin.getLeaderboardManager().getLeaderboard(name);

                if (leaderboard == null) return "";

                String raw = leaderboard.getTopValue(position);
                if (raw == null || raw.isEmpty()) return "";

                try {
                    double value = Double.parseDouble(raw);
                    return format(value);
                } catch (NumberFormatException e) {
                    return raw;
                }
            }

            // %evergreenlb_topvalue_<leaderboard>_<position>%
            if (params.startsWith("topvalue_")) {

                String[] split = params.split("_");
                if (split.length != 3) return "";

                String name = split[1];
                int position = Integer.parseInt(split[2]);

                Leaderboard leaderboard =
                        plugin.getLeaderboardManager().getLeaderboard(name);

                if (leaderboard == null) return "";

                String raw = leaderboard.getTopValue(position);
                if (raw == null || raw.isEmpty()) return "";

                try {
                    double value = Double.parseDouble(raw);

                    return format(value);

                } catch (NumberFormatException e) {
                    return raw;
                }
            }


            // %evergreenlb_timeuntil_<leaderboard>%
            if (params.startsWith("timeuntil_")) {

                String name = params.substring("timeuntil_".length());
                Leaderboard leaderboard =
                        plugin.getLeaderboardManager().getLeaderboard(name);

                if (leaderboard instanceof TimedLeaderboard timed) {

                    long millis = timed.getTimeUntilResetMillis();
                    if (millis <= 0) return "Resetting...";

                    return formatDuration(millis);
                }

                if (leaderboard instanceof CommunityLeaderboard community) {

                    long millis = community.getTimeUntilResetMillis();
                    if (millis <= 0) return "Resetting...";

                    return formatDuration(millis);
                }

                return "Permanent";
            }

            // %evergreenlb_goal_<leaderboard>%
            if (params.startsWith("goal_")) {

                String name = params.substring("goal_".length());
                Leaderboard leaderboard =
                        plugin.getLeaderboardManager().getLeaderboard(name);

                if (leaderboard instanceof CommunityLeaderboard community) {

                    double goal = community.getCurrentGoal();

                    if (goal == Math.floor(goal)) {
                        return String.valueOf((long) goal);
                    }

                    return String.valueOf(goal);
                }

                return "";
            }

            // %evergreenlb_progress_<leaderboard>%
            if (params.startsWith("progress_")) {

                String name = params.substring("progress_".length());
                Leaderboard leaderboard =
                        plugin.getLeaderboardManager().getLeaderboard(name);

                if (leaderboard instanceof CommunityLeaderboard community) {

                    double progress = community.getCurrentProgress();

                    if (progress == Math.floor(progress)) {
                        return String.valueOf((long) progress);
                    }

                    return String.valueOf(progress);
                }

                return "";
            }

            // %evergreenlb_threshold_<leaderboard>%
            if (params.startsWith("threshold_")) {

                String name = params.substring("threshold_".length());
                Leaderboard leaderboard =
                        plugin.getLeaderboardManager().getLeaderboard(name);

                if (leaderboard instanceof CommunityLeaderboard community) {

                    double threshold = community.getCurrentThreshold();

                    if (threshold == Math.floor(threshold)) {
                        return String.valueOf((long) threshold);
                    }

                    return String.valueOf(threshold);
                }

                return "";
            }

            // %evergreenlb_thresholdmark_<leaderboard>%
            if (params.startsWith("thresholdmark_")) {

                if (player == null) return "";

                String name = params.substring("thresholdmark_".length());
                Leaderboard leaderboard =
                        plugin.getLeaderboardManager().getLeaderboard(name);

                if (leaderboard instanceof CommunityLeaderboard community) {

                    double value = community.getPlayerValue(player.getUniqueId());
                    double threshold = community.getCurrentThreshold();

                    return value >= threshold ? "&e✔&r" : "&c✘&r";
                }

                return "";
            }

            // %evergreenlb_playervalue_<leaderboard>%
            if (params.startsWith("playervalue_")) {

                if (player == null) return "";

                String name = params.substring("playervalue_".length());
                Leaderboard leaderboard =
                        plugin.getLeaderboardManager().getLeaderboard(name);

                if (leaderboard == null) return "";

                double value = leaderboard.getPlayerValue(player.getUniqueId());

                return format(value);
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

    private String format(double value) {
        return java.math.BigDecimal.valueOf(value)
                .stripTrailingZeros()
                .toPlainString();
    }
}