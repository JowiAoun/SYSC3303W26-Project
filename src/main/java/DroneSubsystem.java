/**
 * DroneSubsystem.java
 *
 * Represents the drone client for Iteration 1.
 * It repeatedly asks the Scheduler for work and reports completion.
 */
public class DroneSubsystem implements Runnable {
    private final MessageBuffer toScheduler;
    private final MessageBuffer fromScheduler;

    /**
     * @param toScheduler queue used to send messages to Scheduler
     * @param fromScheduler queue used to receive assignments from Scheduler
     */
    public DroneSubsystem(MessageBuffer toScheduler,
                          MessageBuffer fromScheduler) {
        this.toScheduler = toScheduler;
        this.fromScheduler = fromScheduler;
    }

    /**
     * Main loop:
     * - announce readiness
     * - accept an assignment
     * - simulate servicing
     * - report completion
     */
    @Override
    public void run() {
        int completed = 0;
        System.out.println("[Drone] Ready.");

        try {
            toScheduler.put(Message.droneReady());
            while (true) {
                Message reply = fromScheduler.get();

                if (reply.getType() == Message.Type.SHUTDOWN) {
                    break;
                }

                if (reply.getType() == Message.Type.DRONE_ASSIGNMENT) {
                    FireEvent event = reply.getEvent();
                    System.out.println("[Drone] Assigned: " + event);

                    simulateService(event);
                    toScheduler.put(Message.droneCompleted(event));
                    completed++;
                    System.out.println("[Drone] Ready.");
                    toScheduler.put(Message.droneReady());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("[Drone] Interrupted.", e);
        }

        System.out.println("[Drone] Finished. Completed: " + completed);
    }

    /**
     * Simulate service time based on severity (placeholder timing).
     */
    private void simulateService(FireEvent event) throws InterruptedException {
        int sleepMs;
        switch (event.getSeverity()) {
            case LOW:
                sleepMs = 300;
                break;
            case MODERATE:
                sleepMs = 600;
                break;
            case HIGH:
                sleepMs = 900;
                break;
            default:
                sleepMs = 400;
        }
        Thread.sleep(sleepMs);
    }
}
