import java.util.concurrent.atomic.AtomicLong;

/**
 * SimulationConfig.java
 *
 * Centralized shared state for simulation speed and simulation time.
 *
 * We maintain a shared simulation clock (ms) that advances by intended model durations.
 * Threads may sleep scaled wall time, but metrics should read the simulation clock so they
 * remain stable across different speed factors (e.g., 200× vs 480×).
 */
public class SimulationConfig {
    /** Default until GUI Start or headless launch applies a value and locks. */
    private static volatile int TIME_FACTOR = 480;

    private static volatile boolean speedLocked = false;
    /** Shared simulation clock in milliseconds (monotonic). */
    private static final AtomicLong SIM_TIME_MS = new AtomicLong(0);
    private static volatile long startWallTimeMs = -1;

    /**
     * When true, {@link FireIncidentSubsystem} sleeps between CSV rows according to the Time column
     * (scaled by {@link #getTimeFractionFactor()}). Tests disable this so sends stay instantaneous.
     */
    private static volatile boolean csvTimePacingEnabled = true;

    public static int getTimeFactor() {
        return TIME_FACTOR;
    }

    public static double getTimeFractionFactor() {
        return 1.0 / TIME_FACTOR;
    }

    /**
     * Sets the simulation speed factor. Ignored after {@link #lockSpeed()} (except in tests via
     * {@link #unlockSpeedForTests()}).
     */
    public static void setTimeFactor(int factor) {
        if (speedLocked) {
            return;
        }
        if (factor < 1) {
            factor = 1;
        }
        TIME_FACTOR = factor;
    }

    /** Call once when the run begins so speed cannot change mid-simulation (metrics stay consistent). */
    public static void lockSpeed() {
        speedLocked = true;
    }

    public static boolean isSpeedLocked() {
        return speedLocked;
    }

    /** Resets the lock so unit tests can change speed. Not used in normal application runs. */
    public static void unlockSpeedForTests() {
        speedLocked = false;
    }

    /** Reset simulation clock to 0 ms (call right before starting simulation threads). */
    public static void resetSimClock() {
        SIM_TIME_MS.set(0);
        startWallTimeMs = System.currentTimeMillis();
    }

    /** Current simulated time in ms since last reset. */
    public static long nowSimMs() {
        if (startWallTimeMs == -1) return SIM_TIME_MS.get();
        if (TIME_FACTOR == 1 && !csvTimePacingEnabled) {
            return SIM_TIME_MS.get();
        }
        return (System.currentTimeMillis() - startWallTimeMs) * Math.max(1, TIME_FACTOR);
    }

    /** Advance the simulation clock by the given simulated duration (ms). */
    public static void advanceSimMs(long simulatedMs) {
        if (simulatedMs <= 0) {
            return;
        }
        SIM_TIME_MS.addAndGet(simulatedMs);
    }

    /**
     * Sleep for a given simulated duration.
     * Always advances the simulation clock by {@code simulatedMs}, and sleeps scaled wall time.
     */
    public static void sleepSimulated(long simulatedMs) throws InterruptedException {
        if (simulatedMs <= 0) {
            return;
        }
        advanceSimMs(simulatedMs);
        long wallMs = Math.max(1, Math.round(simulatedMs * getTimeFractionFactor()));
        Thread.sleep(wallMs);
    }

    public static boolean isCsvTimePacingEnabled() {
        return csvTimePacingEnabled;
    }

    public static void setCsvTimePacingEnabled(boolean enabled) {
        csvTimePacingEnabled = enabled;
    }
}
