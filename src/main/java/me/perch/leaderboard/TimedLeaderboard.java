package me.perch.leaderboard;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import me.clip.placeholderapi.PlaceholderAPI;
import me.perch.Leaderboards;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TimedLeaderboard extends Leaderboard {

    private static final int CACHE_LIMIT = 30;

    private final List<TimedTask> tasks;
    private int currentTaskIndex = 0;

    private final int updateInterval;
    private final int saveInterval;

    private final Map<UUID, Double> baseline = new ConcurrentHashMap<>();
    private final Map<UUID, Double> values = new ConcurrentHashMap<>();

    private volatile List<Map.Entry<UUID, Double>> cachedTop = new ArrayList<>();
    private volatile boolean dirty = false;
    private volatile boolean updating = false;
    private volatile boolean resetting = false;

    private final File dataFile;
    private long lastReset;

    private final ExecutionTime executionTime;

    public TimedLeaderboard(String name,
                            List<TimedTask> tasks,
                            String cronExpression,
                            int updateInterval,
                            int saveInterval) {

        super(name, "timed", "");

        if (tasks == null || tasks.isEmpty())
            throw new IllegalArgumentException("Timed leaderboard must have at least one task.");

        this.tasks = tasks;
        this.updateInterval = updateInterval;
        this.saveInterval = saveInterval;

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

        Bukkit.getScheduler().runTaskTimer(
                Leaderboards.getInstance(),
                this::updateSync,
                20L,
                updateInterval * 20L
        );

        Bukkit.getScheduler().runTaskTimerAsynchronously(
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
        saveIfDirtyAsync();
    }
}
