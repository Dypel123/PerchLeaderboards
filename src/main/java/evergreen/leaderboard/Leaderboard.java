package evergreen.leaderboard;

import evergreen.Leaderboards;
import org.bukkit.Bukkit;

import java.util.UUID;

public abstract class Leaderboard {

    protected final String name;
    protected final String type;
    protected final String description;

    public Leaderboard(String name, String type, String description) {
        this.name = name.toLowerCase();
        this.type = type.toLowerCase();
        this.description = description;
    }

    public String getName() { return name; }
    public String getType() { return type; }
    public String getDescription() { return description; }

    public abstract double getPlayerValue(UUID uuid);

    public abstract String getPlaceholder();
    public abstract String getTopName(int position);
    public abstract String getTopValue(int position);
    public abstract void shutdown();

    protected void broadcastResetMessage() {

        Leaderboards plugin = Leaderboards.getInstance();

        Bukkit.getOnlinePlayers().forEach(player ->
                plugin.getMessages().send(
                        player,
                        "leaderboard-reset",
                        msg -> msg.replace("{leaderboard}", name)
                )
        );
    }
}
