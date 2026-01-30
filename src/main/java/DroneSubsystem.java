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

    @Override
    public void run() {
        int completed = 0;
        System.out.println("[Drone] Ready.");

        try {
            // Announce initial readiness to the Scheduler.
            announceReady();
            while (true) {
                // Wait for the next message from the Scheduler.
                Message reply = readNextMessage();
                // Stop cleanly when a shutdown message is received.
                if (isShutdownMessage(reply)) {
                    break;
                }
                // Handle assignments and report completion.
                if (isAssignmentMessage(reply)) {
                    handleAssignment(reply);
                    completed++;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("[Drone] Interrupted.", e);
        }

        System.out.println("[Drone] Finished. Completed: " + completed);
    }

    /**
     * Announce readiness to the Scheduler.
     */
    void announceReady() throws InterruptedException {
        toScheduler.put(Message.droneReady());
    }

    /**
     * Reads one message from the Scheduler.
     */
    Message readNextMessage() throws InterruptedException {
        return fromScheduler.get();
    }

    /**
     * Checks if a message is a shutdown signal.
     */
    boolean isShutdownMessage(Message reply) {
        return reply.getType() == Message.Type.SHUTDOWN;
    }

    /**
     * Checks if a message is an assignment.
     */
    boolean isAssignmentMessage(Message reply) {
        return reply.getType() == Message.Type.DRONE_ASSIGNMENT;
    }

    /**
     * Handle an assignment from the Scheduler.
     */
    void handleAssignment(Message reply) throws InterruptedException {
        FireEvent event = reply.getEvent();
        System.out.println("[Drone] Assigned: " + event);

        simulateService(event);
        reportCompletion(event);
        System.out.println("[Drone] Ready.");
        announceReady();
    }

    /**
     * Reports completion of an event to the Scheduler.
     */
    void reportCompletion(FireEvent event) throws InterruptedException {
        toScheduler.put(Message.droneCompleted(event));
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