package me.perch.leaderboard;

public class TimedTask {

    private final String placeholder;
    private final String description;
    private final double goal;

    public TimedTask(String placeholder, String description, double goal) {
        this.placeholder = placeholder;
        this.description = description;
        this.goal = goal;
    }

    public String getPlaceholder() { return placeholder; }
    public String getDescription() { return description; }
    public double getGoal() { return goal; }
}
