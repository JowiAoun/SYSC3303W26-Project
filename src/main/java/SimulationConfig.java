/**
 * SimulationConfig.java
 *
 * Centralized shared state for the simulation speed factor.
 * Readable by all threads (drones, scheduler); writable from the GUI EDT.
 * volatile is sufficient: single writer (GUI), multiple readers.
 */
public class SimulationConfig {
    private static volatile int TIME_FACTOR = 1;

    public static int getTimeFactor() {
        return TIME_FACTOR;
    }

    public static double getTimeFractionFactor() {
        return 1.0 / TIME_FACTOR;
    }

    public static void setTimeFactor(int factor) {
        TIME_FACTOR = factor;
    }
}
