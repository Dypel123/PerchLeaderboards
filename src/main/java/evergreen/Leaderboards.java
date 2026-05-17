package evergreen;

import evergreen.leaderboard.LeaderboardManager;
import evergreen.placeholder.EvergreenLeaderboardExpansion;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class Leaderboards extends JavaPlugin {

    private static Leaderboards instance;
    private LeaderboardManager leaderboardManager;
    private Messages messages;

    @Override
    public void onEnable() {

        instance = this;

        messages = new Messages(this);
        leaderboardManager = new LeaderboardManager(this);
        leaderboardManager.loadLeaderboards();

        getCommand("evergreenlb").setExecutor(new LeaderboardsCommands(this));
        getCommand("evergreenlb").setTabCompleter(new LeaderboardsCommands(this));

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new EvergreenLeaderboardExpansion(this).register();
        }

        getLogger().info("EvergreenLeaderboards enabled.");
    }

    @Override
    public void onDisable() {

        if (leaderboardManager != null) {
            leaderboardManager.shutdown();
        }

        Bukkit.getScheduler().cancelTasks(this);
    }



    public static Leaderboards getInstance() {
        return instance;
    }

    public LeaderboardManager getLeaderboardManager() {
        return leaderboardManager;
    }

    public Messages getMessages() { return messages; }
}