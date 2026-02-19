package me.perch.leaderboard;

import me.perch.Leaderboards;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class LeaderboardManager {

    private final Leaderboards plugin;
    private final Map<String, Leaderboard> leaderboards = new HashMap<>();

    // spacing between leaderboard update starts (in ticks)
    private static final long STAGGER_TICKS = 10L;

    public LeaderboardManager(Leaderboards plugin) {
        this.plugin = plugin;
    }

    public void loadLeaderboards() {

        leaderboards.values().forEach(Leaderboard::shutdown);
        leaderboards.clear();

        File folder = new File(plugin.getDataFolder(), "leaderboards");
        if (!folder.exists()) folder.mkdirs();

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        Arrays.sort(files, Comparator.comparing(File::getName));

        int index = 0;

        for (File file : files) {

            String name = file.getName().replace(".yml", "").toLowerCase();
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

            String type = config.getString("type", "simple").toLowerCase();

            long staggerDelay = index * STAGGER_TICKS;

            if (type.equals("simple")) {

                if (!config.contains("tasks")) {
                    plugin.getLogger().warning("Simple leaderboard '" + name + "' has no tasks section.");
                    continue;
                }

                List<Map<?, ?>> taskMaps = config.getMapList("tasks");

                if (taskMaps.isEmpty()) {
                    plugin.getLogger().warning("Simple leaderboard '" + name + "' has empty tasks list.");
                    continue;
                }

                Map<?, ?> task = taskMaps.get(0);

                String placeholder = String.valueOf(task.get("placeholder"));
                String taskDescription = String.valueOf(task.get("description"));

                int update = config.getInt("update-interval", 30);
                int save = config.getInt("save-interval", 300);

                leaderboards.put(name,
                        new SimpleLeaderboard(
                                name,
                                taskDescription,
                                placeholder,
                                update,
                                save,
                                staggerDelay
                        )
                );
            }

            if (type.equals("timed")) {

                if (!config.contains("tasks")) {
                    plugin.getLogger().warning("Timed leaderboard '" + name + "' has no tasks section.");
                    continue;
                }

                List<TimedTask> tasks = new ArrayList<>();

                for (Map<?, ?> map : config.getMapList("tasks")) {

                    if (!map.containsKey("placeholder") || !map.containsKey("description")) {
                        plugin.getLogger().warning("Invalid task in leaderboard '" + name + "'");
                        continue;
                    }

                    tasks.add(new TimedTask(
                            map.get("placeholder").toString(),
                            map.get("description").toString()
                    ));
                }

                if (tasks.isEmpty()) {
                    plugin.getLogger().warning("Timed leaderboard '" + name + "' has no valid tasks.");
                    continue;
                }

                Map<Integer, List<String>> rewards = new HashMap<>();

                if (config.contains("rewards")) {

                    for (String key : config.getConfigurationSection("rewards").getKeys(false)) {

                        try {
                            int position = Integer.parseInt(key);
                            List<String> commands = config.getStringList("rewards." + key);

                            if (!commands.isEmpty()) {
                                rewards.put(position, commands);
                            }

                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("Invalid reward position '" + key + "' in leaderboard '" + name + "'");
                        }
                    }
                }

                String cron = config.getString("cron", "0 0 0 1 * ?");
                int update = config.getInt("update-interval", 30);
                int save = config.getInt("save-interval", 300);

                leaderboards.put(name,
                        new TimedLeaderboard(
                                name,
                                tasks,
                                rewards,
                                cron,
                                update,
                                save,
                                staggerDelay
                        )
                );
            }

            index++;
        }
    }

    public Leaderboard getLeaderboard(String name) {
        return leaderboards.get(name.toLowerCase());
    }

    public Collection<Leaderboard> getLeaderboards() {
        return leaderboards.values();
    }

    public Collection<String> getLeaderboardNames() {
        return leaderboards.keySet();
    }

    public void shutdown() {
        leaderboards.values().forEach(Leaderboard::shutdown);
    }

    public void reload() {
        shutdown();
        loadLeaderboards();
    }
}
