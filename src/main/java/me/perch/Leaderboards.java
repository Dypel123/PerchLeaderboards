package me.perch;

import me.perch.leaderboard.LeaderboardManager;
import me.perch.placeholder.PerchLeaderboardExpansion;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class Leaderboards extends JavaPlugin {

    private static Leaderboards instance;
    private LeaderboardManager leaderboardManager;

    @Override
    public void onEnable() {

        instance = this;


        leaderboardManager = new LeaderboardManager(this);
        leaderboardManager.loadLeaderboards();

        getCommand("perchlb").setExecutor(new LeaderboardsCommands(this));
        getCommand("perchlb").setTabCompleter(new LeaderboardsCommands(this));

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PerchLeaderboardExpansion(this).register();
        }

        getLogger().info("PerchLeaderboards enabled.");
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
}