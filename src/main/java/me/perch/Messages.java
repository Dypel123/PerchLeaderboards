package me.perch;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Messages {

    private final Leaderboards plugin;
    private File file;
    private YamlConfiguration config;

    public Messages(Leaderboards plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        file = new File(plugin.getDataFolder(), "messages.yml");

        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(file);
    }

    public void reload() {
        load();
    }

    public void send(CommandSender sender, String path) {
        send(sender, path, null);
    }

    public void send(CommandSender sender, String path, Replacer replacer) {

        String raw = config.getString(path, "<red>Missing message: " + path + "</red>");

        if (replacer != null) {
            raw = replacer.apply(raw);
        }

        sender.sendMessage(parse(raw));
    }

    private static final Pattern HEX_PATTERN =
            Pattern.compile("&#([A-Fa-f0-9]{6})");

    private Component parse(String message) {

        if (message == null || message.isEmpty()) {
            return Component.empty();
        }

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String color = matcher.group(1);
            matcher.appendReplacement(buffer, "<#" + color + ">");
        }
        matcher.appendTail(buffer);

        message = buffer.toString();

        if (message.contains("<") && message.contains(">")) {
            return MiniMessage.miniMessage().deserialize(message);
        }

        return LegacyComponentSerializer.legacyAmpersand().deserialize(message);
    }

    public interface Replacer {
        String apply(String input);
    }
}