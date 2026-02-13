package me.perch.leaderboard;

import me.clip.placeholderapi.PlaceholderAPI;
import me.perch.Leaderboards;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SimpleLeaderboard extends Leaderboard {

    private static final int CACHE_LIMIT = 30;

    private final String placeholder;
    private final int updateInterval;
    private final int saveInterval;
    private final long startDelay;

    private final Map<UUID, Double> values = new ConcurrentHashMap<>();
    private volatile List<Map.Entry<UUID, Double>> cachedTop = new ArrayList<>();
    private volatile boolean dirty = false;
    private volatile boolean updating = false;

    private final File dataFile;

    private BukkitTask updateTask;
    private BukkitTask saveTask;

    public SimpleLeaderboard(String name,
                             String description,
                             String placeholder,
                             int updateInterval,
                             int saveInterval,
                             long startDelay) {

        super(name, "simple", description);

        this.placeholder = placeholder;
        this.updateInterval = updateInterval;
        this.saveInterval = saveInterval;
        this.startDelay = startDelay;

        this.dataFile = new File(
                Leaderboards.getInstance().getDataFolder(),
                "data/" + name + ".yml"
        );

        load();
        rebuildCache();
        startTasks();
    }

    @Override
    public String getPlaceholder() { return placeholder; }

    @Override
    public String getTopName(int pos) {
        if (pos <= 0 || pos > cachedTop.size()) return "";
        return Bukkit.getOfflinePlayer(
                cachedTop.get(pos - 1).getKey()
        ).getName();
    }

    @Override
    public String getTopValue(int pos) {
        if (pos <= 0 || pos > cachedTop.size()) return "";
        return String.valueOf(cachedTop.get(pos - 1).getValue());
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
                this::saveAsync,
                saveInterval * 20L,
                saveInterval * 20L
        );
    }

    private void updateSync() {

        if (updating) return;
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

            @Override
            public void run() {

                int processed = 0;

                while (index < players.size() && processed < batchSize) {

                    Player player = players.get(index++);
                    processed++;

                    String result =
                            PlaceholderAPI.setPlaceholders(player, placeholder);

                    double value;
                    try {
                        value = Double.parseDouble(result.replace(",", ""));
                    } catch (Exception e) {
                        continue;
                    }

                    UUID uuid = player.getUniqueId();
                    Double old = values.get(uuid);

                    if (old == null || Double.compare(old, value) != 0) {
                        values.put(uuid, value);
                        changed = true;
                    }
                }

                if (index >= players.size()) {

                    if (changed) {
                        rebuildCache();
                        dirty = true;
                    }

                    updating = false;
                    cancel();
                }
            }

        }.runTaskTimer(Leaderboards.getInstance(), 0L, 1L);
    }

    private void rebuildCache() {
        cachedTop = values.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(CACHE_LIMIT)
                .collect(Collectors.toList());
    }

    private void load() {
        if (!dataFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);

        for (String key : config.getKeys(false)) {
            values.put(UUID.fromString(key), config.getDouble(key));
        }
    }

    private void saveAsync() {
        if (!dirty) return;
        dirty = false;

        YamlConfiguration config = new YamlConfiguration();
        values.forEach((u,v) -> config.set(u.toString(), v));

        try { config.save(dataFile); }
        catch (IOException e) { e.printStackTrace(); }
    }

    @Override
    public void shutdown() {

        if (updateTask != null) updateTask.cancel();
        if (saveTask != null) saveTask.cancel();

        saveAsync();
    }
}
