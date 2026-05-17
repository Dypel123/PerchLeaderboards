package evergreen.leaderboard;

public class TimedTask {

    private final String placeholder;
    private final String description;
    private final double goal;
    private final double threshold;

    public TimedTask(String placeholder, String description, double goal, double threshold) {
        this.placeholder = placeholder;
        this.description = description;
        this.goal = goal;
        this.threshold = threshold;
    }

    public String getPlaceholder() { return placeholder; }
    public String getDescription() { return description; }
    public double getGoal() { return goal; }
    public double getThreshold() { return threshold; }
}
