package me.perch.leaderboard;

public class TimedTask {

    private final String placeholder;
    private final String description;

    public TimedTask(String placeholder, String description) {
        this.placeholder = placeholder;
        this.description = description;
    }

    public String getPlaceholder() { return placeholder; }
    public String getDescription() { return description; }
}
