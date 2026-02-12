package me.perch.leaderboard;

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

    public abstract String getPlaceholder();
    public abstract String getTopName(int position);
    public abstract String getTopValue(int position);
    public abstract void shutdown();
}
