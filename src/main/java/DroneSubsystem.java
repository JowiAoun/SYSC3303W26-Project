import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;

/**
 * DroneSubsystem.java
 *
 * State-machine-driven drone client.
 * The run() loop switches on currentState; each handler advances the state.
 */
public class DroneSubsystem implements Runnable {
    private final MessageBuffer toScheduler;
    private final MessageBuffer fromScheduler;
    private static final int MAX_CAPACITY_LITERS = 15;
    private static final double TRAVEL_SPEED_MPS = 15.0;
    private static final int METERS_PER_ZONE = 10;
    private static final double DROP_SECONDS_PER_LITER = 0.5;
    private static final int BASE_ZONE_ID = 0;
    private int remainingLiters = MAX_CAPACITY_LITERS;
    private DroneState currentState = DroneState.IDLE;
    private final DatagramSocket socket;
    private final InetAddress schedulerAddr;
    private final int droneId;

    // State-machine context
    private FireEvent currentAssignment = null;
    private int remainingRequired = 0;
    private int currentZoneId = BASE_ZONE_ID;

    // Grid position tracking
    private int currentCol = 0;
    private int currentRow = 0;
    private final List<ZoneDef> zones;

    /**
     * @param droneId unique identifier for this drone
     * @param toScheduler queue used to send messages to Scheduler
     * @param fromScheduler queue used to receive assignments from Scheduler
     */
    public DroneSubsystem(int droneId, MessageBuffer toScheduler,
                          MessageBuffer fromScheduler) throws SocketException, UnknownHostException {
        this.droneId = droneId;
        this.toScheduler = toScheduler;
        this.fromScheduler = fromScheduler;
        this.socket = new DatagramSocket();
        this.schedulerAddr = InetAddress.getByName(SwarmNetwork.LOCALHOST);

        // Load zone geometry for path planning
        this.zones = ZoneLoader.loadZones("./src/main/resources/data/zones.csv", 16, 16);
        ZoneDef baseZone = getZoneById(BASE_ZONE_ID);
        if (baseZone != null) {
            int[] baseCenter = PathPlanner.zoneCenterCell(baseZone);
            this.currentCol = baseCenter[0];
            this.currentRow = baseCenter[1];
        }
    }

    /**
     * Backward-compatible constructor defaulting to drone ID 1.
     */
    public DroneSubsystem(MessageBuffer toScheduler,
                          MessageBuffer fromScheduler) throws SocketException, UnknownHostException {
        this(1, toScheduler, fromScheduler);
    }

    public int getDroneId() { return droneId; }

    public DatagramSocket getSocket() { return socket; }

    public int getPort() { return socket.getPort(); }

    public void closeSocket() { socket.close(); }

    @Override
    public void run() {
        int completed = 0;
        System.out.println("[Drone " + droneId + "] Ready.");

        try {
            // Announce initial readiness and IDLE state.
            sendReadySignal();
            sendStatus(DroneState.IDLE, BASE_ZONE_ID);

            boolean running = true;
            while (running) {
                switch (currentState) {
                    case IDLE:
                        running = handleIdle();
                        break;
                    case EN_ROUTE:
                        handleEnRoute();
                        break;
                    case EXTINGUISHING:
                        handleExtinguishing();
                        if (currentState == DroneState.IDLE) {
                            completed++;
                        }
                        break;
                    case RETURNING:
                        handleReturning();
                        break;
                    case REFILLING:
                        handleRefilling();
                        break;
                    case FAULTED:
                        handleFaulted();
                        break;
                    default:
                        System.out.println("[Drone " + droneId + "] Unknown state: " + currentState);
                        running = false;
                        break;
                }
            }

            System.out.println("[Drone " + droneId + "] Finished. Completed: " + completed);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("[Drone " + droneId + "] Interrupted.", e);
        }
    }

    // ── State handlers ──────────────────────────────────────────────

    /**
     * IDLE — blocks on fromScheduler.get() waiting for the next command.
     * @return false when the drone should exit the run loop (SHUTDOWN received)
     */
    private boolean handleIdle() throws Exception {
        Message reply = receiveMessage();
        switch (reply.getType()) {
            case DRONE_ASSIGNMENT:
                currentAssignment = reply.getEvent();
                remainingRequired = currentAssignment.getRequiredLiters();
                System.out.println("[Drone " + droneId + "] Assigned: " + currentAssignment);

                if (remainingLiters <= 0) {
                    sendStatus(DroneState.RETURNING, BASE_ZONE_ID);
                } else {
                    sendStatus(DroneState.EN_ROUTE, currentAssignment.getZoneId());
                }
                break;

            case DRONE_RETURN_TO_BASE:
                System.out.println("[Drone " + droneId + "] Received Return to Base command.");
                currentAssignment = null;
                sendStatus(DroneState.RETURNING, BASE_ZONE_ID);
                break;

            case SHUTDOWN:
                return false;

            default:
                System.out.println("[Drone " + droneId + "] Unknown message type in IDLE: " + reply.getType());
                break;
        }
        return true;
    }

    /**
     * EN_ROUTE — simulate travel to the fire zone, then begin extinguishing.
     */
    private void handleEnRoute() throws Exception {
        int targetZone = currentAssignment.getZoneId();
        System.out.println("[Drone " + droneId + "] Flying to Zone " + targetZone);
        simulateTravel(targetZone);
        currentZoneId = targetZone;
        sendStatus(DroneState.EXTINGUISHING, targetZone);
    }

    /**
     * EXTINGUISHING — drop water on the fire.
     * Transitions: fire done -> IDLE, tank empty -> RETURNING, otherwise stays EXTINGUISHING.
     */
    private void handleExtinguishing() throws Exception {
        int targetZone = currentAssignment.getZoneId();
        System.out.println("[Drone " + droneId + "] Extinguishing fire at Zone " + targetZone);

        int toDrop = Math.min(remainingLiters, remainingRequired);
        double dropSeconds = toDrop * DROP_SECONDS_PER_LITER;
        long sleepMs = Math.round(dropSeconds * 1000);
        Thread.sleep(sleepMs);

        remainingLiters -= toDrop;
        remainingRequired -= toDrop;
        System.out.println("[Drone " + droneId + "] Dropped " + toDrop + "L. Remaining in tank: "
                + remainingLiters + "L. Fire needs: " + remainingRequired + "L");

        if (remainingRequired <= 0) {
            System.out.println("[Drone " + droneId + "] Fire extinguished. Awaiting next command.");
            sendCompletion(currentAssignment);
            currentAssignment = null;
            sendReadySignal();
            currentZoneId = targetZone;
            sendStatus(DroneState.IDLE, targetZone);
        } else if (remainingLiters <= 0) {
            System.out.println("[Drone " + droneId + "] Tank empty. Returning to base for refill.");
            sendStatus(DroneState.RETURNING, BASE_ZONE_ID);
        }
        // else: stay in EXTINGUISHING, next loop iteration drops more
    }

    /**
     * RETURNING — simulate travel back to base, then refill.
     */
    private void handleReturning() throws Exception {
        simulateTravel(BASE_ZONE_ID);
        currentZoneId = BASE_ZONE_ID;
        sendStatus(DroneState.REFILLING, BASE_ZONE_ID);
    }

    /**
     * REFILLING — refill the tank.
     * If there's an active assignment, go back EN_ROUTE; otherwise go IDLE.
     */
    private void handleRefilling() throws Exception {
        System.out.println("[Drone " + droneId + "] Refilling...");
        Thread.sleep(2000);
        remainingLiters = MAX_CAPACITY_LITERS;
        System.out.println("[Drone " + droneId + "] Refilled. Capacity: " + remainingLiters);

        if (currentAssignment != null) {
            System.out.println("[Drone " + droneId + "] Returning to Zone " + currentAssignment.getZoneId());
            sendStatus(DroneState.EN_ROUTE, currentAssignment.getZoneId());
        } else {
            sendReadySignal();
            sendStatus(DroneState.IDLE, BASE_ZONE_ID);
        }
    }

    /**
     * FAULTED — placeholder for iteration 4.
     */
    private void handleFaulted() throws InterruptedException {
        System.out.println("[Drone " + droneId + "] FAULTED state — awaiting recovery (not yet implemented).");
        Thread.sleep(5000);
    }

    // ── Helpers ─────────────────────────────────────────────────────

    /**
     * Get the currentState of the drone.
     */
    DroneState getCurrentState() {
        return currentState;
    }

    /**
     * Get the currentState of the drone.
     */
    DroneState getCurrentState(DroneState droneState) {
        return currentState;
    }

    /**
     * Set the currentState of the drone.
     */
    void setCurrentState(DroneState droneState) {
        currentState = droneState;
    }

    /**
     * Announce readiness to the Scheduler.
     */
    void sendReadySignal() throws Exception {
        // toScheduler.put(Message.droneReady());
        SwarmNetwork.sendMessage(socket, schedulerAddr, SwarmNetwork.SCHEDULER_PORT, Message.droneReady(), "[Drone " + droneId + "]", "sent Ready", "to Scheduler");
    }

    /**
     * Send an update that the drone is IDLE at base.
     */
    void sendUserIdlingUpdate() throws Exception {
        sendStatus(DroneState.IDLE, BASE_ZONE_ID);
    }

    /**
     * Send an update that the drone is IDLE at a specific zone.
     */
    void sendUserIdlingUpdate(int zoneId) throws Exception {
        sendStatus(DroneState.IDLE, zoneId);
    }

    /**
     * Reads one message from the Scheduler.
     */
    Message receiveMessage() throws Exception {
        // return fromScheduler.get();
        return SwarmNetwork.receiveMessage(socket);
    }

    /**
     * Checks if a message is a shutdown signal.
     */
    boolean isShutdown(Message reply) {
        return reply.getType() == Message.Type.SHUTDOWN;
    }

    /**
     * Checks if a message is an assignment.
     */
    boolean isAssignment(Message reply) {
        return reply.getType() == Message.Type.DRONE_ASSIGNMENT;
    }

    /**
     * Checks if a message is a return to base command.
     */
    boolean isReturnToBase(Message reply) {
        return reply.getType() == Message.Type.DRONE_RETURN_TO_BASE;
    }

    /**
     * Handle an assignment from the Scheduler.
     */
    void processAssignment(Message reply) throws Exception {
        FireEvent event = reply.getEvent();
        System.out.println("[Drone " + droneId + "] Assigned: " + event);

        simulateService(event);
        sendCompletion(event);

        // Return to IDLE state at CURRENT ZONE
        System.out.println("[Drone " + droneId + "] Task complete. Returning to Ready state at Zone " + event.getZoneId());
        // We stay at the current zone. The scheduler will decide whether to send us more work or Return to Base.
        sendReadySignal();
        sendUserIdlingUpdate(event.getZoneId());
    }

    /**
     * Reports completion of an event to the Scheduler.
     */
    void sendCompletion(FireEvent event) throws Exception {
        // toScheduler.put(Message.droneCompleted(event));
        SwarmNetwork.sendMessage(socket, schedulerAddr, SwarmNetwork.SCHEDULER_PORT, Message.droneCompleted(event), "[Drone " + droneId + "]", "sent Completion", "to Scheduler");
    }

    /**
     * Checks if a refill is needed and handles the refill process.
     */
    private void checkAndRefillIfNeeded() throws Exception {
        if (remainingLiters <= 0) {
            System.out.println("[Drone " + droneId + "] Tank empty. Returning to base for refill.");

            // Return to base
            sendStatus(DroneState.RETURNING, BASE_ZONE_ID);
            simulateTravel(BASE_ZONE_ID);

            // Refill
            System.out.println("[Drone " + droneId + "] Refilling...");
            sendStatus(DroneState.REFILLING, BASE_ZONE_ID);
            Thread.sleep(2000); // Simulate refill time
            remainingLiters = MAX_CAPACITY_LITERS;
            System.out.println("[Drone " + droneId + "] Refilled. Capacity: " + remainingLiters);
        }
    }

    /**
     * Simulate service time based on severity.
     */
    private void simulateService(FireEvent event) throws Exception {
        int remainingRequired = event.getRequiredLiters();
        int targetZone = event.getZoneId();

         // Check if we need to refill BEFORE heading out (if empty)
        checkAndRefillIfNeeded();

        // 1. Travel to Zone
        System.out.println("[Drone " + droneId + "] Flying to Zone " + targetZone);
        sendStatus(DroneState.EN_ROUTE, targetZone);
        simulateTravel(targetZone);

        // 2. Extinguish
        while (remainingRequired > 0) {
            checkAndRefillIfNeeded();

            // If we had to return to base, we need to travel back to the fire
            if (remainingLiters == MAX_CAPACITY_LITERS) {
                 System.out.println("[Drone " + droneId + "] Returning to Zone " + targetZone);
                 sendStatus(DroneState.EN_ROUTE, targetZone);
                 simulateTravel(targetZone);
            }

            System.out.println("[Drone " + droneId + "] Extinguishing fire at Zone " + targetZone);
            sendStatus(DroneState.EXTINGUISHING, targetZone);

            int toDrop = Math.min(remainingLiters, remainingRequired);
            double dropSeconds = toDrop * DROP_SECONDS_PER_LITER;
            long sleepMs = Math.round(dropSeconds * 1000);

            Thread.sleep(sleepMs);

            remainingLiters -= toDrop;
            remainingRequired -= toDrop;
            System.out.println("[Drone " + droneId + "] Dropped " + toDrop + "L. Remaining in tank: " + remainingLiters + "L. Fire needs: " + remainingRequired + "L");

            // Update status after drop
            sendStatus(DroneState.EXTINGUISHING, targetZone);
        }

        System.out.println("[Drone " + droneId + "] Fire extinguished. Awaiting next command.");
    }

    /**
     * Single transition point: updates currentState and sends status to Scheduler.
     */
    private void sendStatus(DroneState state, int zoneId) throws Exception {
        this.currentState = state;
        SwarmNetwork.sendMessage(socket, schedulerAddr, SwarmNetwork.SCHEDULER_PORT,
                Message.droneStatus(new DroneStatus(droneId, state, zoneId, remainingLiters, currentCol, currentRow)),
                "[Drone " + droneId + "]", "sent Drone Status", "to Scheduler");
    }

    /** Time per cell step in ms, derived from zone size and travel speed. */
    private static final long CELL_TRAVEL_MS = Math.round((METERS_PER_ZONE / TRAVEL_SPEED_MPS) * 1000);

    /**
     * Simulate cell-by-cell travel to the target zone center using Bresenham path.
     */
    private void simulateTravel(int targetZoneId) throws Exception {
        ZoneDef targetZone = getZoneById(targetZoneId);
        if (targetZone == null) {
            // Fallback: fixed sleep if zone not found
            Thread.sleep(2000);
            return;
        }
        int[] targetCenter = PathPlanner.zoneCenterCell(targetZone);
        List<int[]> path = PathPlanner.computePath(currentCol, currentRow, targetCenter[0], targetCenter[1]);

        // Skip first cell (current position), traverse remaining cells
        for (int i = 1; i < path.size(); i++) {
            Thread.sleep(CELL_TRAVEL_MS);
            currentCol = path.get(i)[0];
            currentRow = path.get(i)[1];
            int cellZoneId = findZoneForCell(currentCol, currentRow);
            // Send intermediate status update so GUI can track movement
            sendStatus(currentState, cellZoneId);
        }
        currentZoneId = targetZoneId;
    }

    /**
     * Look up which zone a given cell belongs to.
     */
    private int findZoneForCell(int col, int row) {
        for (ZoneDef z : zones) {
            if (col >= z.startCol && col < z.startCol + z.widthCols &&
                    row >= z.startRow && row < z.startRow + z.heightRows) {
                return z.id;
            }
        }
        return 0; // fallback to base
    }

    /**
     * Look up a zone by ID from the loaded zone list.
     */
    private ZoneDef getZoneById(int id) {
        for (ZoneDef z : zones) {
            if (z.id == id) return z;
        }
        return null;
    }

    /**
     * Handles the explicit Return To Base command from Scheduler.
     */
    private void handleReturnToBase() throws Exception {
        System.out.println("[Drone " + droneId + "] Received Return to Base command.");

        // Travel to base
        sendStatus(DroneState.RETURNING, BASE_ZONE_ID);
        simulateTravel(BASE_ZONE_ID);

        // Refill
        System.out.println("[Drone " + droneId + "] Refilling...");
        sendStatus(DroneState.REFILLING, BASE_ZONE_ID);
        Thread.sleep(2000); // Simulate refill time
        remainingLiters = MAX_CAPACITY_LITERS;
        System.out.println("[Drone " + droneId + "] Refilled. Capacity: " + remainingLiters);

        // Back to IDLE at Base
        sendReadySignal();
        sendStatus(DroneState.IDLE, BASE_ZONE_ID);
    }
}
