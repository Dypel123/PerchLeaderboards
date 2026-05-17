package evergreen.leaderboard;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import me.clip.placeholderapi.PlaceholderAPI;
import evergreen.Leaderboards;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CommunityLeaderboard extends Leaderboard {

    private static final int CACHE_LIMIT = 30;

    private final List<TimedTask> tasks;
    private final List<String> rewardCommands;

    private int currentTaskIndex = 0;

    private final int updateInterval;
    private final int saveInterval;
    private final long startDelay;

    private final Map<UUID, Double> baseline = new ConcurrentHashMap<>();
    private final Map<UUID, Double> values = new ConcurrentHashMap<>();

    private volatile List<Map.Entry<UUID, Double>> cachedTop = new ArrayList<>();
    private volatile boolean dirty = false;
    private volatile boolean updating = false;
    private volatile boolean resetting = false;

    private final File dataFile;
    private long lastReset;

    private final ExecutionTime executionTime;

    private BukkitTask updateTask;
    private BukkitTask saveTask;

    public CommunityLeaderboard(String name,
                                List<TimedTask> tasks,
                                List<String> rewardCommands,
                                String cronExpression,
                                int updateInterval,
                                int saveInterval,
                                long startDelay) {

        super(name, "community", "");

        if (tasks == null || tasks.isEmpty())
            throw new IllegalArgumentException("Community leaderboard must have at least one task.");

        this.tasks = tasks;
        this.rewardCommands = rewardCommands != null ? rewardCommands : new ArrayList<>();
        this.updateInterval = updateInterval;
        this.saveInterval = saveInterval;
        this.startDelay = startDelay;

        this.dataFile = new File(
                Leaderboards.getInstance().getDataFolder(),
                "data/" + name + ".yml"
        );

        CronParser parser = new CronParser(
                CronDefinitionBuilder.instanceDefinitionFor(CronType.QUARTZ)
        );
        Cron cron = parser.parse(cronExpression);
        executionTime = ExecutionTime.forCron(cron);

        load();
        rebuildCache();
        startTasks();
    }

    @Override
    public String getPlaceholder() {
        return tasks.get(currentTaskIndex).getPlaceholder();
    }

    public String getCurrentTaskDescription() {
        return tasks.get(currentTaskIndex).getDescription();
    }

    public double getCurrentGoal() {
        return tasks.get(currentTaskIndex).getGoal();
    }

    public double getCurrentThreshold() { return tasks.get(currentTaskIndex).getThreshold(); }

    public double getCurrentProgress() {
        return values.values()
                .stream()
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    @Override
    public double getPlayerValue(UUID uuid) {
        return values.getOrDefault(uuid, 0.0);
    }

    public List<TimedTask> getTasks() {
        return tasks;
    }

    public int getCurrentTaskIndex() {
        return currentTaskIndex;
    }

    public long getTimeUntilResetMillis() {

        ZonedDateTime now = ZonedDateTime.now();

        Optional<ZonedDateTime> next =
                executionTime.nextExecution(
                        ZonedDateTime.ofInstant(
                                new Date(lastReset).toInstant(),
                                now.getZone()
                        )
                );

        if (next.isEmpty()) return -1;

        return next.get().toInstant().toEpochMilli()
                - System.currentTimeMillis();
    }

    @Override
    public String getTopName(int position) {
        if (position <= 0 || position > cachedTop.size()) return "";
        UUID uuid = cachedTop.get(position - 1).getKey();
        return Optional.ofNullable(
                Bukkit.getOfflinePlayer(uuid).getName()
        ).orElse("");
    }

    @Override
    public String getTopValue(int position) {
        if (position <= 0 || position > cachedTop.size()) return "";
        return String.valueOf(
                cachedTop.get(position - 1).getValue()
        );
    }

    private void startTasks() {

        updateTask = Bukkit.getScheduler().runTaskTimer(
                Leaderboards.getInstance(),
                this::updateSync,
                20L + startDelay,
                updateInterval * 20L
        );

        saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                Leaderboards.getInstance(),
                this::saveIfDirtyAsync,
                saveInterval * 20L,
                saveInterval * 20L
        );
    }

    private void updateSync() {

        if (updating || resetting) return;
        updating = true;

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            updating = false;
            return;
        }

        final int batchSize = 10;

        new BukkitRunnable() {

            int index = 0;
            boolean changed = false;
            String activePlaceholder = getPlaceholder();

            @Override
            public void run() {

                int processed = 0;

                while (index < players.size() && processed < batchSize) {

                    Player player = players.get(index++);
                    processed++;

                    if (player.hasPermission("evergreen.leaderboards.ignore")) {
                        continue;
                    }

                    String result =
                            PlaceholderAPI.setPlaceholders(player, activePlaceholder);

                    double current;
                    try {
                        current = Double.parseDouble(result.replace(",", ""));
                    } catch (Exception e) {
                        continue;
                    }

                    UUID uuid = player.getUniqueId();

                    baseline.putIfAbsent(uuid, current);
                    double earned = current - baseline.get(uuid);

                    Double old = values.get(uuid);

                    if (old == null || Double.compare(old, earned) != 0) {
                        values.put(uuid, earned);
                        changed = true;
                    }
                }

                if (index >= players.size()) {

                    if (changed) {
                        rebuildCache();
                        dirty = true;
                    }

                    updating = false;
                    checkReset();
                    cancel();
                }
            }

        }.runTaskTimer(Leaderboards.getInstance(), 0L, 1L);
    }

    private void checkReset() {

        if (getTimeUntilResetMillis() > 0) return;
        if (resetting) return;

        resetting = true;

        distributeCommunityRewards();
        broadcastResetMessage();

        currentTaskIndex++;
        if (currentTaskIndex >= tasks.size()) {
            currentTaskIndex = 0;
        }

        lastReset = System.currentTimeMillis();

        baseline.clear();
        values.clear();
        cachedTop = new ArrayList<>();

        dirty = true;
        resetting = false;
    }

    private void distributeCommunityRewards() {

        if (rewardCommands.isEmpty()) return;
        if (values.isEmpty()) return;

        double total = values.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        double goal = getCurrentGoal();
        if (total < goal) return;

        double threshold = getCurrentThreshold();

        for (Map.Entry<UUID, Double> entry : values.entrySet()) {

            double playerScore = entry.getValue();
            if (playerScore < threshold) continue;

            UUID uuid = entry.getKey();
            String playerName = Bukkit.getOfflinePlayer(uuid).getName();

            if (playerName == null) continue;

            for (String command : rewardCommands) {

                String parsed = command
                        .replace("{player}", playerName)
                        .replace("{score}", String.valueOf(playerScore))
                        .replace("{total}", String.valueOf(total))
                        .replace("{goal}", String.valueOf(goal));

                Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        parsed
                );
            }
        }
    }

    private void rebuildCache() {

        cachedTop = values.entrySet()
                .stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(CACHE_LIMIT)
                .collect(Collectors.toList());
    }

    private void load() {

        if (!dataFile.exists()) {
            lastReset = System.currentTimeMillis();
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);

        lastReset = config.getLong("last-reset", System.currentTimeMillis());
        currentTaskIndex = config.getInt("current-task-index", 0);

        if (config.contains("baseline")) {
            for (String key : config.getConfigurationSection("baseline").getKeys(false)) {
                baseline.put(
                        UUID.fromString(key),
                        config.getDouble("baseline." + key)
                );
            }
        }

        if (config.contains("values")) {
            for (String key : config.getConfigurationSection("values").getKeys(false)) {
                values.put(
                        UUID.fromString(key),
                        config.getDouble("values." + key)
                );
            }
        }
    }

    private void saveIfDirtyAsync() {

        if (!dirty) return;
        dirty = false;

        Map<UUID, Double> baselineSnapshot = new HashMap<>(baseline);
        Map<UUID, Double> valuesSnapshot = new HashMap<>(values);

        YamlConfiguration config = new YamlConfiguration();

        config.set("last-reset", lastReset);
        config.set("current-task-index", currentTaskIndex);

        baselineSnapshot.forEach((uuid, value) ->
                config.set("baseline." + uuid.toString(), value));

        valuesSnapshot.forEach((uuid, value) ->
                config.set("values." + uuid.toString(), value));

        try {
            config.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void shutdown() {

        if (updateTask != null) updateTask.cancel();
        if (saveTask != null) saveTask.cancel();

        saveIfDirtyAsync();
    }
}