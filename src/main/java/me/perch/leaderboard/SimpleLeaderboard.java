package me.perch.leaderboard;

import me.clip.placeholderapi.PlaceholderAPI;
import me.perch.Leaderboards;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

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

    private final Map<UUID, Double> values = new ConcurrentHashMap<>();
    private volatile List<Map.Entry<UUID, Double>> cachedTop = new ArrayList<>();
    private volatile boolean dirty = false;

    private final File dataFile;

    public SimpleLeaderboard(String name, String description,
                             String placeholder, int updateInterval,
                             int saveInterval) {

        super(name, "simple", description);

        this.placeholder = placeholder;
        this.updateInterval = updateInterval;
        this.saveInterval = saveInterval;

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

        Bukkit.getScheduler().runTaskTimer(
                Leaderboards.getInstance(),
                this::updateSync,
                20L,
                updateInterval * 20L
        );

        Bukkit.getScheduler().runTaskTimerAsynchronously(
                Leaderboards.getInstance(),
                this::saveAsync,
                saveInterval * 20L,
                saveInterval * 20L
        );
    }

    private void updateSync() {

        boolean changed = false;

        for (Player p : Bukkit.getOnlinePlayers()) {

            String result = PlaceholderAPI.setPlaceholders(p, placeholder);

            try {
                double value = Double.parseDouble(result);
                UUID uuid = p.getUniqueId();

                if (!values.containsKey(uuid) ||
                        values.get(uuid) != value) {

                    values.put(uuid, value);
                    changed = true;
                }
            } catch (Exception ignored) {}
        }

        if (changed) {
            rebuildCache();
            dirty = true;
        }
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
    public void shutdown() { saveAsync(); }
}
